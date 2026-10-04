package app.luogo.app.data.location

import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns discrete trusted fixes into a smooth render-time pose.
 *
 * The four frequencies in this app are deliberately separate:
 *   raw sensor rate  -> fused fix rate (~2s while moving) -> network sync rate -> render rate
 *
 * This class owns only the last one. It interpolates *between* two trusted fixes so the
 * marker glides at frame rate without inventing measurements or touching the network.
 * After a fix's arrival time passes, the pose is held at that fix: the measurement said
 * "you are here", so continuing to move the marker past it would be a lie.
 *
 * Pure Kotlin, no Android dependency, unit-testable.
 */
class SmoothMarkerInterpolator(
    /** When false the pose stops at the latest fix instead of coasting along heading. */
    private val allowExtrapolation: Boolean = false
) {

    data class MarkerPose(
        val latitude: Double,
        val longitude: Double,
        val bearingDegrees: Float,
        val accuracyMeters: Float,
        val speedMps: Float
    )

    private data class Track(
        val from: RawPose,
        val to: RawPose
    )

    private data class RawPose(
        val latitude: Double,
        val longitude: Double,
        val bearingDegrees: Float,
        val accuracyMeters: Float,
        val speedMps: Float,
        val timestampMs: Long
    )

    private val tracks = HashMap<String, Track>()

    /**
     * Records a new trusted fix. The previous fix (if any) becomes the interpolation start.
     */
    fun onNewTrustedFix(
        id: String,
        latitude: Double,
        longitude: Double,
        bearingDegrees: Float,
        accuracyMeters: Float,
        speedMps: Float,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val incoming = RawPose(
            latitude = latitude,
            longitude = longitude,
            bearingDegrees = normalizeBearing(bearingDegrees),
            accuracyMeters = accuracyMeters.coerceAtLeast(0f),
            speedMps = speedMps.coerceAtLeast(0f),
            timestampMs = nowMs
        )
        val existing = tracks[id]
        tracks[id] = if (existing == null || incoming.timestampMs <= existing.to.timestampMs) {
            Track(existing?.from ?: incoming, incoming)
        } else {
            Track(existing.to, incoming)
        }
    }

    /**
     * Samples the render pose for [id] at [nowMs], or null if this id has no fixes.
     */
    fun sample(id: String, nowMs: Long = System.currentTimeMillis()): MarkerPose? {
        val track = tracks[id] ?: return null
        val from = track.from
        val to = track.to

        // Before the previous fix's timestamp we are still rendering the older position.
        if (nowMs <= from.timestampMs) {
            return MarkerPose(
                from.latitude, from.longitude, from.bearingDegrees,
                from.accuracyMeters, from.speedMps
            )
        }

        val durationMs = (to.timestampMs - from.timestampMs).coerceAtLeast(1L)
        val linearT = ((nowMs - from.timestampMs).toDouble() / durationMs).coerceIn(0.0, 1.0)

        // Ease-out so the marker decelerates into the new fix instead of stopping dead.
        val t = easeOutCubic(linearT)

        val lat = from.latitude + (to.latitude - from.latitude) * t
        val lon = from.longitude + (to.longitude - from.longitude) * t

        // Accuracy tightens toward the new fix, but never below it.
        val accuracy = from.accuracyMeters + (to.accuracyMeters - from.accuracyMeters) * linearT

        val speed = from.speedMps + (to.speedMps - from.speedMps) * t
        val bearing = interpolateBearing(from.bearingDegrees, to.bearingDegrees, t)

        var outLat = lat
        var outLon = lon
        var outBearing = bearing

        if (allowExtrapolation && linearT >= 1.0 && to.speedMps > EXTRAPOLATION_MIN_SPEED_MPS) {
            // Coast for at most a fraction of the interval that produced the fix, so a lost
            // fix degrades into "position unknown soon" rather than a confident wrong answer.
            val coastMs = (nowMs - to.timestampMs).coerceAtLeast(0L)
            val maxCoastMs = (durationMs * EXTRAPOLATION_FRACTION).toLong().coerceAtLeast(1L)
            val coastT = (coastMs.toDouble() / maxCoastMs).coerceIn(0.0, 1.0)
            val coastMeters = to.speedMps * (maxCoastMs / 1000.0) * coastT
            val (cLat, cLon) = GeoMath.offsetMeters(
                to.latitude, to.longitude, coastMeters, to.bearingDegrees
            )
            outLat = cLat
            outLon = cLon
            outBearing = to.bearingDegrees
        }

        return MarkerPose(outLat, outLon, outBearing, accuracy.toFloat().coerceAtLeast(0f), speed.toFloat())
    }

    /** Drops a track, e.g. when a person leaves a group or an item is deleted. */
    fun forget(id: String) {
        tracks.remove(id)
    }

    /** Keeps at most [maxTracks] entries so long sessions cannot grow without bound. */
    fun trimTo(maxTracks: Int) {
        if (tracks.size <= maxTracks) return
        val keep = tracks.entries.sortedByDescending { it.value.to.timestampMs }.take(maxTracks)
        tracks.clear()
        tracks.putAll(keep.associate { it.key to it.value })
    }

    fun trackedIds(): Set<String> = tracks.keys.toSet()

    private fun easeOutCubic(t: Double): Double {
        val inv = 1.0 - t
        return 1.0 - (inv * inv * inv)
    }

    private fun interpolateBearing(from: Float, to: Float, t: Double): Float {
        var delta = to - from
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return normalizeBearing(from + (delta * t).toFloat())
    }

    private fun normalizeBearing(bearing: Float): Float = ((bearing % 360f) + 360f) % 360f

    companion object {
        private const val EXTRAPOLATION_MIN_SPEED_MPS = 1.0f
        private const val EXTRAPOLATION_FRACTION = 0.5

        /** Bearing in radians, for callers drawing a heading cone. */
        fun bearingToRadians(bearingDegrees: Float): Double =
            Math.toRadians(bearingDegrees.toDouble())

        fun headingVector(bearingDegrees: Float): Pair<Double, Double> {
            val rad = bearingToRadians(bearingDegrees)
            return cos(rad) to sin(rad)
        }
    }
}