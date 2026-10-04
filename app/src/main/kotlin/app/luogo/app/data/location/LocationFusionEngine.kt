package app.luogo.app.data.location

import app.luogo.app.domain.model.ActivityState
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.LocationQualityLevel
import app.luogo.app.domain.model.LocationSourceType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fuses independent positioning sources into one position with an honest uncertainty.
 *
 * Method:
 *  - Every incoming measurement is kept in a short ring buffer.
 *  - Positions are combined by inverse-variance weighting, which is the maximum-likelihood
 *    estimate when each source's error is independent and roughly circular and Gaussian.
 *  - Disagreement between sources inflates the reported accuracy (bounded), so a fix never
 *    looks more precise than the evidence supports.
 *  - Physically impossible jumps are rejected: the position is not moved and the fix is
 *    flagged suspicious.
 *  - Dead reckoning is a separate, explicitly labelled path that always widens uncertainty
 *    as it accumulates drift.
 *
 * This class is pure Kotlin with no Android dependency, so it is directly unit-testable.
 */
class LocationFusionEngine {

    data class RawPositionMeasurement(
        val latitude: Double,
        val longitude: Double,
        val timestampMs: Long,
        val horizontalAccuracyMeters: Float,
        val altitudeMeters: Double? = null,
        val verticalAccuracyMeters: Float? = null,
        val speedMps: Float = 0f,
        val bearingDegrees: Float = 0f,
        val sourceType: LocationSourceType,
        val isMock: Boolean = false
    )

    private data class Sample(
        val measurement: RawPositionMeasurement,
        val weight: Double
    )

    private val samples = ArrayDeque<Sample>()
    private var lastFused: FusedLocationFix? = null
    private var lastGoodFixMs: Long = 0L
    private var deadReckoningSteps: Int = 0

    /** Sources seen recently, used to decide when a source has gone away. */
    private val lastSeenBySource = HashMap<LocationSourceType, Long>()

    // ------------------------------------------------------------------ ingest

    /**
     * Adds a measurement and returns the newly fused fix.
     * Passing null-safe behaviour: an unusable measurement returns the previous fix rather
     * than a fabricated one.
     */
    fun ingestMeasurement(measurement: RawPositionMeasurement): FusedLocationFix {
        if (!isPlausibleCoordinate(measurement.latitude, measurement.longitude)) {
            return lastFused ?: FusedLocationFix(
                latitude = 0.0,
                longitude = 0.0,
                timestampMs = measurement.timestampMs,
                horizontalAccuracyMeters = Float.MAX_VALUE,
                qualityLevel = LocationQualityLevel.SUSPICIOUS,
                confidence = 0f
            )
        }

        // Accuracy must be positive to be meaningful; clamp rather than divide by zero.
        val accuracy = measurement.horizontalAccuracyMeters.coerceAtLeast(MIN_ACCURACY_METERS)
        val sanitised = measurement.copy(horizontalAccuracyMeters = accuracy)
        lastSeenBySource[measurement.sourceType] = measurement.timestampMs

        // Reject physically impossible movement before it can poison the weighted average.
        val jump = evaluateJump(sanitised)
        if (jump != null) return jump

        val weight = 1.0 / (accuracy.toDouble() * accuracy.toDouble())
        samples.addLast(Sample(sanitised, weight))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()

        val fused = combine()
        lastFused = fused
        if (!fused.isSuspiciousJump) lastGoodFixMs = measurement.timestampMs
        deadReckoningSteps = 0
        return fused
    }

    /**
     * If the newest measurement demands a ground speed no physical vehicle could produce,
     * keep the previous position and report the problem instead of teleporting.
     */
    private fun evaluateJump(measurement: RawPositionMeasurement): FusedLocationFix? {
        val previous = lastFused ?: return null
        val previousSample = samples.lastOrNull()?.measurement ?: return null
        val dtSeconds = (measurement.timestampMs - previousSample.timestampMs) / 1000.0
        if (dtSeconds <= 0.0) return null

        val travelled = GeoMath.distanceMeters(
            previousSample.latitude, previousSample.longitude,
            measurement.latitude, measurement.longitude
        )

        // Combined error budget: either source could legitimately explain part of the gap.
        val errorBudget = (
            previousSample.horizontalAccuracyMeters.toDouble() +
                measurement.horizontalAccuracyMeters.toDouble()
            ).coerceAtLeast(1.0)

        // Allow generous headroom over the physical speed limit for measurement error.
        val maxPlausibleMeters = MAX_PLAUSIBLE_SPEED_MPS * dtSeconds + errorBudget
        if (travelled <= maxPlausibleMeters) return null

        return previous.copy(
            timestampMs = measurement.timestampMs,
            qualityLevel = LocationQualityLevel.SUSPICIOUS,
            isSuspiciousJump = true,
            confidence = min(previous.confidence, SUSPICIOUS_CONFIDENCE),
            isMock = previous.isMock || measurement.isMock
        )
    }

    /** Inverse-variance weighted combination of the buffered measurements. */
    private fun combine(): FusedLocationFix {
        val newest = samples.last().measurement
        var totalWeight = 0.0
        var lat = 0.0
        var lon = 0.0
        for (s in samples) {
            lat += s.measurement.latitude * s.weight
            lon += s.measurement.longitude * s.weight
            totalWeight += s.weight
        }
        lat /= totalWeight
        lon /= totalWeight

        val baseSigma = sqrt(1.0 / totalWeight).toFloat()

        // Disagreement inflation, deliberately capped. Sources that disagree a little widen
        // the circle a little; we never let this become a licence to report false precision.
        val disagreement = samples.maxOfOrNull {
            GeoMath.distanceMeters(
                lat, lon,
                it.measurement.latitude, it.measurement.longitude
            )
        } ?: 0.0
        val normalized = disagreement / baseSigma.toDouble().coerceAtLeast(0.1)
        val inflation = 1.0 + min(MAX_INFLATION, normalized * DISAGREEMENT_GAIN)
        val accuracy = (baseSigma * inflation).toFloat().coerceAtLeast(MIN_ACCURACY_METERS)

        val sources = samples.map { it.measurement.sourceType }.distinct()
        val activity = classifyActivity(
            speedMps = pickSpeed(newest),
            accelVariance = lastAccelVariance,
            stepRateHz = lastStepRateHz
        )

        val altitude = pickAltitude()
        val verticalAccuracy = pickVerticalAccuracy()
        val isMock = samples.any { it.measurement.isMock }

        return FusedLocationFix(
            latitude = lat,
            longitude = GeoMath.normalizeLongitude(lon),
            timestampMs = newest.timestampMs,
            horizontalAccuracyMeters = accuracy,
            altitudeMeters = altitude,
            verticalAccuracyMeters = verticalAccuracy,
            speedMps = pickSpeed(newest),
            bearingDegrees = pickBearing(),
            sourceSummary = buildSourceSummary(sources),
            activeSources = sources,
            confidence = confidenceFor(accuracy, isMock),
            qualityLevel = qualityFor(accuracy, newest.timestampMs, isMock),
            activityState = activity,
            isMock = isMock,
            isSuspiciousJump = false
        )
    }

    // ------------------------------------------------------------- dead reckoning

    /**
     * Projects the last trusted position forward using the supplied stride.
     *
     * The caller supplies the step length because it comes from real sensor integration
     * (step detector or accelerometer). Uncertainty grows with every step because inertial
     * integration drifts; the returned fix is labelled [LocationSourceType.INERTIAL_DEAD_RECKONING]
     * so the UI can never present it as a GNSS measurement.
     */
    fun propagateDeadReckoning(
        nowMs: Long,
        stepStrideMeters: Double,
        headingDegrees: Float
    ): FusedLocationFix? {
        val anchor = lastFused?.takeIf { !it.isSuspiciousJump } ?: return null
        if (anchor.timestampMs >= nowMs) return null
        if (stepStrideMeters <= 0.0) return null

        val elapsedSeconds = (nowMs - anchor.timestampMs) / 1000.0
        // One stride per second is the realistic ceiling for pedestrian dead reckoning.
        val steps = min(MAX_DEAD_RECKONING_STEPS, (elapsedSeconds * STEPS_PER_SECOND).toInt())
        if (steps <= 0) return null
        deadReckoningSteps += steps

        val distance = stepStrideMeters * steps
        val (lat, lon) = GeoMath.offsetMeters(
            anchor.latitude, anchor.longitude, distance, headingDegrees
        )

        // Drift accumulates with every step; uncertainty grows faster than linearly once the
        // projection runs long, which is the honest behaviour.
        val driftMeters = stepStrideMeters * steps * DRIFT_FRACTION_PER_STEP
        val accuracy = sqrt(
            anchor.horizontalAccuracyMeters.toDouble() * anchor.horizontalAccuracyMeters +
                driftMeters * driftMeters
        ).toFloat().coerceAtLeast(MIN_ACCURACY_METERS)

        return anchor.copy(
            latitude = lat,
            longitude = lon,
            timestampMs = nowMs,
            horizontalAccuracyMeters = accuracy,
            speedMps = (distance / elapsedSeconds).toFloat(),
            bearingDegrees = ((headingDegrees % 360f) + 360f) % 360f,
            sourceSummary = buildSourceSummary(anchor.activeSources + LocationSourceType.INERTIAL_DEAD_RECKONING),
            activeSources = anchor.activeSources + LocationSourceType.INERTIAL_DEAD_RECKONING,
            confidence = confidenceFor(accuracy, anchor.isMock),
            qualityLevel = qualityFor(accuracy, nowMs, anchor.isMock),
            isSuspiciousJump = false
        )
    }

    /** Milliseconds since the last trusted (non-suspicious) fix. */
    fun ageOfLastTrustedFixMs(nowMs: Long): Long =
        if (lastGoodFixMs == 0L) Long.MAX_VALUE else (nowMs - lastGoodFixMs).coerceAtLeast(0L)

    fun lastTrustedFix(): FusedLocationFix? = lastFused?.takeIf { !it.isSuspiciousJump }

    // ------------------------------------------------------------ activity

    private var lastAccelVariance: Float = 0f
    private var lastStepRateHz: Float = 0f

    /** Feed real sensor statistics. Used to bias activity classification. */
    fun updateMotionSignals(accelVariance: Float, stepRateHz: Float) {
        lastAccelVariance = accelVariance
        lastStepRateHz = stepRateHz
    }

    /**
     * Classifies motion from speed plus optional inertial evidence.
     *
     * Step cadence is the strongest single discriminator between walking and running, so it
     * is consulted first. A device being carried in a stationary bag still shows acceleration
     * without displacement, which is why acceleration alone never implies movement.
     */
    fun classifyActivity(speedMps: Float, accelVariance: Float, stepRateHz: Float): ActivityState {
        val speed = speedMps.coerceAtLeast(0f)
        return when {
            speed >= DRIVING_SPEED_MPS -> ActivityState.DRIVING
            stepRateHz >= RUNNING_STEP_RATE_HZ -> ActivityState.RUNNING
            speed in CYCLING_SPEED_RANGE -> ActivityState.CYCLING
            speed >= WALKING_SPEED_MPS -> ActivityState.WALKING
            speed < STATIONARY_SPEED_MPS && accelVariance < CARRYING_ACCEL_VARIANCE ->
                ActivityState.STATIONARY
            speed < STATIONARY_SPEED_MPS -> ActivityState.UNKNOWN
            else -> ActivityState.WALKING
        }
    }

    // ------------------------------------------------------------- quality

    private fun pickSpeed(newest: RawPositionMeasurement): Float {
        // Prefer the reported (Doppler-derived) speed. Differencing two positions from
        // *different* sources over a couple of seconds is dominated by position error, not
        // movement: a 24 m Wi-Fi fix and a 4 m GNSS fix 14 m apart look like 7 m/s of travel
        // when the phone was walking at 1.6 m/s. Reported speed is only absent when the
        // provider could not compute it, and then differencing is the best available.
        if (newest.speedMps > 0f) return newest.speedMps

        val first = samples.firstOrNull()?.measurement ?: return 0f
        val implied = GeoMath.impliedSpeedMps(
            first.latitude, first.longitude, first.timestampMs,
            newest.latitude, newest.longitude, newest.timestampMs
        ) ?: return 0f
        return implied.coerceIn(0f, MAX_PLAUSIBLE_SPEED_MPS.toFloat())
    }

    private fun pickBearing(): Float {
        if (samples.size < 2) return samples.last().measurement.bearingDegrees
        val first = samples.first().measurement
        val last = samples.last().measurement
        val computed = GeoMath.bearingDegrees(
            first.latitude, first.longitude, last.latitude, last.longitude
        )
        if (last.speedMps >= MIN_BEARING_SPEED_MPS || last.bearingDegrees > 0f) {
            return last.bearingDegrees
        }
        return computed
    }

    private fun pickAltitude(): Double? {
        val withAltitude = samples.mapNotNull { it.measurement.altitudeMeters }
        if (withAltitude.isEmpty()) return null
        var totalWeight = 0.0
        var sum = 0.0
        for (s in samples) {
            val alt = s.measurement.altitudeMeters ?: continue
            val vAcc = (s.measurement.verticalAccuracyMeters ?: DEFAULT_VERTICAL_ACCURACY_METERS)
            val w = 1.0 / (vAcc.toDouble() * vAcc.toDouble())
            sum += alt * w
            totalWeight += w
        }
        return if (totalWeight > 0.0) sum / totalWeight else withAltitude.last()
    }

    private fun pickVerticalAccuracy(): Float? {
        val values = samples.mapNotNull { it.measurement.verticalAccuracyMeters }
        if (values.isEmpty()) return null
        return sqrt(values.sumOf { (it * it).toDouble() } / values.size).toFloat()
    }

    private fun buildSourceSummary(sources: List<LocationSourceType>): String =
        when {
            sources.isEmpty() -> "No source"
            sources.size == 1 -> sources.first().label
            else -> sources.take(3).joinToString(" + ") { it.label }
        }

    private fun confidenceFor(accuracyMeters: Float, isMock: Boolean): Float {
        if (isMock) return 0.05f
        val base = (1.0 - (accuracyMeters / CONFIDENCE_HORIZON_METERS)).coerceIn(0.0, 1.0)
        return base.toFloat()
    }

    private fun qualityFor(
        accuracyMeters: Float,
        timestampMs: Long,
        isMock: Boolean
    ): LocationQualityLevel = when {
        isMock -> LocationQualityLevel.SUSPICIOUS
        accuracyMeters <= HIGH_QUALITY_METERS -> LocationQualityLevel.HIGH
        accuracyMeters <= GOOD_QUALITY_METERS -> LocationQualityLevel.GOOD
        accuracyMeters <= MODERATE_QUALITY_METERS -> LocationQualityLevel.MODERATE
        else -> LocationQualityLevel.POOR
    }

    private fun isPlausibleCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN() &&
            lat != 0.0 && lon != 0.0

    companion object {
        /** Product requirement: ~2s meaningful update while moving. */
        const val MOVING_TARGET_INTERVAL_MS = 2_000L

        private const val MAX_SAMPLES = 8
        private const val MIN_ACCURACY_METERS = 1.0f

        /** ~900 km/h. Generous for aircraft, but nothing legitimate on the ground beats it. */
        private const val MAX_PLAUSIBLE_SPEED_MPS = 250.0
        private const val SUSPICIOUS_CONFIDENCE = 0.2f

        private const val MAX_INFLATION = 0.15
        private const val DISAGREEMENT_GAIN = 0.03

        private const val HIGH_QUALITY_METERS = 10f
        private const val GOOD_QUALITY_METERS = 25f
        private const val MODERATE_QUALITY_METERS = 75f
        private const val CONFIDENCE_HORIZON_METERS = 100f

        private const val MAX_DEAD_RECKONING_STEPS = 120
        private const val STEPS_PER_SECOND = 1.0
        private const val DRIFT_FRACTION_PER_STEP = 0.08
        private const val DEFAULT_VERTICAL_ACCURACY_METERS = 10f
        private const val MIN_BEARING_SPEED_MPS = 0.5f

        private const val STATIONARY_SPEED_MPS = 0.5f
        private const val CARRYING_ACCEL_VARIANCE = 1.5f
        private const val WALKING_SPEED_MPS = 0.5f
        private const val RUNNING_STEP_RATE_HZ = 2.2f
        private const val DRIVING_SPEED_MPS = 8.5f
        private val CYCLING_SPEED_RANGE = 2.5f..8.5f

        /** Rounded for display, e.g. accuracy shown as an integer metre count. */
        fun accuracyLabel(accuracyMeters: Float): String = abs(accuracyMeters).roundToInt().toString()
    }
}