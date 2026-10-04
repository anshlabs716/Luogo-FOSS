package app.luogo.app.data.geofence

import app.luogo.app.data.location.GeoMath
import app.luogo.app.domain.model.ActivityState
import app.luogo.app.domain.model.LocationHistoryPoint
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.domain.model.TripSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Geofence evaluation, trip detection and history maths.
 *
 * Pure Kotlin, no Android dependency, so every rule here is directly unit-testable.
 *
 * Geofencing uses hysteresis: a place is entered inside its radius, but only left once the
 * device is a margin *outside* it. Without that margin, a phone sitting exactly on the
 * boundary produces a burst of arrival/departure notifications.
 */
class GeofenceAndHistoryEngine {

    enum class GeofenceTransitionType { ARRIVED, DEPARTED }

    data class GeofenceEvent(
        val transitionType: GeofenceTransitionType,
        val placeId: String,
        val placeName: String,
        val placeCategory: PlaceCategory,
        val subjectName: String,
        val timestampMs: Long,
        val distanceMeters: Double
    )

    /**
     * Evaluates every place against one position.
     *
     * Returns the places with their `currentlyInside` flag updated, plus the transitions that
     * fired. Accuracy is taken into account so that a very coarse fix cannot flip a geofence.
     */
    fun evaluateGeofences(
        places: List<SavedPlace>,
        subjectName: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float
    ): Pair<List<SavedPlace>, List<GeofenceEvent>> {
        val updated = ArrayList<SavedPlace>(places.size)
        val events = ArrayList<GeofenceEvent>()

        for (place in places) {
            if (!place.enabled) {
                updated.add(place)
                continue
            }

            val distance = GeoMath.distanceMeters(
                place.latitude, place.longitude, latitude, longitude
            )
            // Enter inside the radius, leave only outside radius + margin.
            val enterRadius = place.radiusMeters.toDouble()
            val exitRadius = enterRadius + exitMarginFor(place.radiusMeters)
            val wasInside = place.currentlyInside
            val isInsideNow = if (wasInside) distance <= exitRadius else distance <= enterRadius

            if (isInsideNow == wasInside) {
                updated.add(place)
                continue
            }

            // A fix far coarser than the geofence is not trustworthy enough to flip state.
            if (accuracyMeters > MAX_ACCURACY_FOR_TRANSITION_METERS) {
                updated.add(place)
                continue
            }

            val transition = if (isInsideNow) {
                GeofenceTransitionType.ARRIVED
            } else {
                GeofenceTransitionType.DEPARTED
            }

            if (transition == GeofenceTransitionType.ARRIVED && !place.notifyOnArrival) {
                updated.add(place.copy(currentlyInside = true, lastTransitionMs = System.currentTimeMillis()))
                continue
            }
            if (transition == GeofenceTransitionType.DEPARTED && !place.notifyOnDeparture) {
                updated.add(place.copy(currentlyInside = false, lastTransitionMs = System.currentTimeMillis()))
                continue
            }

            events.add(
                GeofenceEvent(
                    transitionType = transition,
                    placeId = place.id,
                    placeName = place.name,
                    placeCategory = place.category,
                    subjectName = subjectName,
                    timestampMs = System.currentTimeMillis(),
                    distanceMeters = distance
                )
            )
            updated.add(
                place.copy(
                    currentlyInside = isInsideNow,
                    lastTransitionMs = System.currentTimeMillis()
                )
            )
        }

        return updated to events
    }

    /** Extra distance required to leave, scaled to the geofence size. */
    private fun exitMarginFor(radiusMeters: Float): Double =
        (radiusMeters.toDouble() * EXIT_MARGIN_FRACTION)
            .coerceIn(MIN_EXIT_MARGIN_METERS, MAX_EXIT_MARGIN_METERS)

    // --------------------------------------------------------------- history

    /** Summed great-circle distance across a chronologically ordered track. */
    fun calculateTotalDistanceMeters(points: List<LocationHistoryPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += GeoMath.distanceMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return total
    }

    /**
     * Splits a track into trips. A gap longer than [TRIP_GAP_MS] ends the current trip, which
     * is what separates "drove home" from "was at work all afternoon then drove home".
     */
    fun detectTrips(
        points: List<LocationHistoryPoint>,
        tripGapMs: Long = TRIP_GAP_MS
    ): List<TripSummary> {
        if (points.isEmpty()) return emptyList()
        val ordered = points.sortedBy { it.timestampMs }
        val trips = ArrayList<TripSummary>()

        var bucket = ArrayList<LocationHistoryPoint>()
        for (point in ordered) {
            val previous = bucket.lastOrNull()
            if (previous != null && point.timestampMs - previous.timestampMs > tripGapMs) {
                trips.add(summarise(bucket))
                bucket = ArrayList()
            }
            bucket.add(point)
        }
        if (bucket.isNotEmpty()) trips.add(summarise(bucket))

        return trips.filter { it.pointCount >= MIN_TRIP_POINTS }
    }

    private fun summarise(points: List<LocationHistoryPoint>): TripSummary {
        val first = points.first()
        val last = points.last()
        val distance = calculateTotalDistanceMeters(points)
        val durationMs = (last.timestampMs - first.timestampMs).coerceAtLeast(0L)
        val durationSeconds = durationMs / 1000.0

        val maxSpeed = points.maxOfOrNull { it.speedMps } ?: 0f
        val avgSpeed = if (durationSeconds > 0) (distance / durationSeconds).toFloat() else 0f

        return TripSummary(
            tripId = "trip-${first.subjectId}-${first.timestampMs}",
            subjectId = first.subjectId,
            subjectName = first.subjectName,
            dateKey = dateKey(first.timestampMs),
            startTimeMs = first.timestampMs,
            endTimeMs = last.timestampMs,
            distanceMeters = distance,
            averageSpeedMps = avgSpeed,
            maxSpeedMps = maxSpeed,
            primaryActivity = dominantActivity(points),
            startLat = first.latitude,
            startLon = first.longitude,
            endLat = last.latitude,
            endLon = last.longitude,
            pointCount = points.size
        )
    }

    /** The activity that covers most of the trip's duration. */
    private fun dominantActivity(points: List<LocationHistoryPoint>): ActivityState {
        val buckets = HashMap<ActivityState, Long>()
        for ((index, point) in points.withIndex()) {
            val spanMs = if (index + 1 < points.size) {
                (points[index + 1].timestampMs - point.timestampMs).coerceAtLeast(0L)
            } else {
                0L
            }
            buckets[point.activityState] = (buckets[point.activityState] ?: 0L) + spanMs
        }
        // STATIONARY never wins by default: a parked trip is not interesting, but we still
        // report it when it is genuinely the only thing that happened.
        return buckets.maxByOrNull { it.value }?.key ?: ActivityState.UNKNOWN
    }

    private fun dateKey(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(timestampMs))

    /**
     * Minutes spent inside each saved place, keyed by place name.
     *
     * Time is credited to the gap from a point to the next one, and the final point is
     * credited up to [TAIL_ATTRIBUTION_MINUTES]. Without that tail, the final visit always
     * looks shorter than it was, because there is no following point to measure against.
     */
    fun calculateTimeSpentAtPlacesMinutes(
        points: List<LocationHistoryPoint>,
        places: List<SavedPlace>,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, Int> {
        if (points.size < 2 || places.isEmpty()) return emptyMap()
        val ordered = points.sortedBy { it.timestampMs }
        val minutes = LinkedHashMap<String, Int>()

        fun credit(place: SavedPlace, durationMs: Long) {
            if (durationMs <= 0) return
            val add = (durationMs / 60_000L).toInt()
            if (add > 0) minutes[place.name] = (minutes[place.name] ?: 0) + add
        }

        for (i in 0 until ordered.size) {
            val point = ordered[i]
            val next = ordered.getOrNull(i + 1)
            // The tail is capped so a forgotten phone does not bill an entire night to "Home".
            val spanMs = if (next != null) {
                (next.timestampMs - point.timestampMs).coerceAtLeast(0L)
            } else {
                (nowMs - point.timestampMs).coerceIn(0L, TAIL_ATTRIBUTION_MINUTES * 60_000L)
            }
            for (place in places) {
                if (!place.enabled) continue
                val distance = GeoMath.distanceMeters(
                    place.latitude, place.longitude, point.latitude, point.longitude
                )
                if (distance <= place.radiusMeters) credit(place, spanMs)
            }
        }
        return minutes.filterValues { it > 0 }
    }

    /**
     * The point at [fraction] through a track, for route playback.
     *
     * Returns null for an empty track. Interpolates position between the two neighbouring
     * points so playback glides rather than stepping, while accuracy, speed and source stay
     * those of the earlier measurement, because those describe a real fix and not a blend.
     */
    fun samplePlaybackPoint(
        points: List<LocationHistoryPoint>,
        fraction: Float
    ): LocationHistoryPoint? {
        if (points.isEmpty()) return null
        val ordered = points.sortedBy { it.timestampMs }
        if (ordered.size == 1) return ordered.first()
        val clamped = fraction.coerceIn(0f, 1f)
        val exact = clamped * (ordered.size - 1)
        val index = exact.toInt().coerceIn(0, ordered.size - 2)
        val t = (exact - index).toDouble()
        val a = ordered[index]
        val b = ordered[index + 1]
        return a.copy(
            latitude = a.latitude + (b.latitude - a.latitude) * t,
            longitude = a.longitude + (b.longitude - a.longitude) * t,
            timestampMs = (a.timestampMs + (b.timestampMs - a.timestampMs) * t).toLong()
        )
    }

    // ----------------------------------------------------------------- export

    /** GPX 1.1 export. Accuracy is written as the standard `<hdop>` element. */
    fun exportToGpx(points: List<LocationHistoryPoint>): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"Luogo-FOSS\" ")
        builder.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        builder.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        builder.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
        builder.append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

        for (point in points.sortedBy { it.timestampMs }) {
            builder.append("  <trkpt lat=\"")
            builder.append(formatCoordinate(point.latitude))
            builder.append("\" lon=\"")
            builder.append(formatCoordinate(point.longitude))
            builder.append("\">\n")
            point.altitudeMeters?.let {
                builder.append("    <ele>")
                builder.append(String.format(Locale.US, "%.2f", it))
                builder.append("</ele>\n")
            }
            builder.append("    <time>")
            builder.append(isoTimestamp(point.timestampMs))
            builder.append("</time>\n")
            builder.append("    <hdop>")
            builder.append(String.format(Locale.US, "%.1f", point.accuracyMeters))
            builder.append("</hdop>\n")
            builder.append("    <name>")
            builder.append(escapeXml(point.subjectName))
            builder.append("</name>\n")
            builder.append("    <desc>")
            builder.append(escapeXml("${point.activityState.label} · ${point.sourceSummary}"))
            builder.append("</desc>\n")
            builder.append("  </trkpt>\n")
        }

        builder.append("</gpx>\n")
        return builder.toString()
    }

    private fun formatCoordinate(value: Double): String =
        // Trim trailing zeros but keep enough precision to be unambiguous.
        String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    private fun isoTimestamp(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestampMs))

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    companion object {
        private const val EXIT_MARGIN_FRACTION = 0.25
        private const val MIN_EXIT_MARGIN_METERS = 15.0
        private const val MAX_EXIT_MARGIN_METERS = 120.0

        /** A fix coarser than this cannot be trusted to flip a geofence. */
        private const val MAX_ACCURACY_FOR_TRANSITION_METERS = 150f

        private const val TAIL_ATTRIBUTION_MINUTES = 30L
        const val TRIP_GAP_MS = 10 * 60_000L
        private const val MIN_TRIP_POINTS = 2
    }
}