package app.luogo.app.ui.map

import app.luogo.app.data.location.GeoMath
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.SavedPlace
import kotlin.math.roundToInt

/**
 * Converts domain models into MapLibre GeoJSON.
 *
 * Marker payloads carry exactly what the layers need and nothing more. Accuracy circles are
 * emitted at their true radius, so a 100 m fix can never be drawn as a 3 m dot.
 */
object MapGeoJson {

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")

    private fun feature(
        geometry: String,
        properties: String
    ): String = """{"type":"Feature","geometry":$geometry,"properties":$properties}"""

    private fun point(lon: Double, lat: Double): String =
        """{"type":"Point","coordinates":[${r(lon)},${r(lat)}]}"""

    fun collection(features: List<String>): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private fun r(value: Double): String {
        // Six decimals is about 11 cm at the equator: well below any real accuracy figure
        // and keeps the payload small.
        val scaled = (value * 1_000_000.0).roundToInt() / 1_000_000.0
        return scaled.toString()
    }

    // ------------------------------------------------------------------ me

    /**
     * The user's own position.
     *
     * Accuracy is written in metres and drawn to scale, so the visible circle always means
     * what the number next to it says.
     */
    fun me(fix: FusedLocationFix?, colorArgb: Long): String =
        collection(meFeatures(fix, colorArgb))

    fun meFeatures(fix: FusedLocationFix?, colorArgb: Long): List<String> {
        if (fix == null) return emptyList()
        val accuracy = fix.horizontalAccuracyMeters.coerceAtLeast(1f)
        return listOf(
            feature(
                geometry = point(fix.longitude, fix.latitude),
                properties = """
                    {"id":"__me__","label":"${escape(fix.sourceSummary)}",
                     "accuracy":${r(accuracy.toDouble())},
                     "color":"${hexColor(colorArgb)}"}
                """.trimIndent()
            )
        )
    }

    // -------------------------------------------------------------- people

    fun people(peers: List<PeerLocationState>, nowMs: Long): String =
        collection(peopleFeatures(peers, nowMs))

    fun peopleFeatures(peers: List<PeerLocationState>, nowMs: Long): List<String> {
        val features = peers.mapNotNull { peer ->
            // A fix older than the stale threshold is drawn faded so live and stale
            // positions are distinguishable at a glance.
            val stale = peer.isStale(nowMs)
            feature(
                geometry = point(peer.longitude, peer.latitude),
                properties = """
                    {"id":"${escape(peer.userId)}","label":"${escape(peer.displayName)}",
                     "color":"${hexColor(peer.colorArgb)}",
                     "accuracy":${r(peer.accuracyMeters.toDouble().coerceAtLeast(1.0))},
                     "stale":$stale}
                """.trimIndent()
            )
        }
        return features
    }

    // --------------------------------------------------------------- items

    fun items(items: List<RegisteredItem>, nowMs: Long): String =
        collection(itemFeatures(items, nowMs))

    fun itemFeatures(items: List<RegisteredItem>, nowMs: Long): List<String> {
        val features = items.mapNotNull { item ->
            val lat = item.lastSeenLatitude
            val lon = item.lastSeenLongitude
            // No position means no marker. We never fall back to the user's own location or
            // a default coordinate, which would invent a position for the item.
            if (lat == null || lon == null) return@mapNotNull null
            val presence = item.presenceStatus(nowMs)
            val stale = presence != ItemPresenceStatus.LIVE
            feature(
                geometry = point(lon, lat),
                properties = """
                    {"id":"${escape(item.id)}","label":"${escape(item.friendlyName)}",
                     "color":"${hexColor(presenceColor(presence))}","stale":$stale}
                """.trimIndent()
            )
        }
        return features
    }

    private fun presenceColor(presence: ItemPresenceStatus): Long = when (presence) {
        ItemPresenceStatus.LIVE -> 0xFF00BFA5
        ItemPresenceStatus.RECENTLY_SEEN -> 0xFFFFB300
        ItemPresenceStatus.STALE -> 0xFF8D6E63
        ItemPresenceStatus.UNKNOWN -> 0xFF78909C
    }

    // -------------------------------------------------------------- places

    /** Geofence circles are emitted as real polygons, sized to the configured radius. */
    fun places(places: List<SavedPlace>): String = collection(placeFeatures(places))

    fun placeFeatures(places: List<SavedPlace>): List<String> {
        val features = places.filter { it.enabled }.map { place ->
            feature(
                geometry = circlePolygon(
                    centerLat = place.latitude,
                    centerLon = place.longitude,
                    radiusMeters = place.radiusMeters.toDouble()
                ),
                properties = """{"id":"${escape(place.id)}","label":"${escape(place.name)}"}"""
            )
        }
        return features
    }

    /**
     * Approximates a circle with a geodesic polygon.
     *
     * 64 segments is well under a pixel at any usable zoom, so the drawn boundary matches the
     * configured radius to within the rendering precision.
     */
    fun circlePolygon(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        segments: Int = 64
    ): String {
        val ring = StringBuilder()
        ring.append('[')
        for (i in 0..segments) {
            val bearing = (i * 360.0 / segments).toFloat()
            val (lat, lon) = GeoMath.offsetMeters(centerLat, centerLon, radiusMeters, bearing)
            if (i > 0) ring.append(',')
            ring.append('[').append(r(lon)).append(',').append(r(lat)).append(']')
        }
        ring.append(']')
        return """{"type":"Polygon","coordinates":[$ring]}"""
    }

    // --------------------------------------------------------------- route

    fun route(route: RouteResult?): String = collection(routeFeatures(route))

    fun routeFeatures(route: RouteResult?): List<String> {
        val polyline = route?.polyline ?: return emptyList()
        if (polyline.size < 2) return emptyList()
        val coords = polyline.joinToString(",") { (lat, lon) -> "[${r(lon)},${r(lat)}]" }
        return listOf(
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
        )
    }

    /** `#RRGGBB`, which is what the style expressions expect. */
    fun hexColor(argb: Long): String = "#%06X".format(argb.toInt() and 0xFFFFFF)
}