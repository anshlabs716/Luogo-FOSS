package app.luogo.app.data.location

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Shared geodesy helpers. Everything here works on a spherical earth, which is accurate
 * enough for consumer location sharing (worst-case error well under the accuracy figures
 * we already report) and avoids pulling in a geodesy dependency.
 */
object GeoMath {

    const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance in meters. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from true north in [0,360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)).toFloat() + 360f) % 360f
    }

    /** Offset a coordinate by a distance along a bearing. Used by dead reckoning. */
    fun offsetMeters(
        latitude: Double,
        longitude: Double,
        distanceMeters: Double,
        bearingDegrees: Float
    ): Pair<Double, Double> {
        val angular = distanceMeters / EARTH_RADIUS_METERS
        val bearing = Math.toRadians(bearingDegrees.toDouble())
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = asin(
            (sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing)).coerceIn(-1.0, 1.0)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2)
        )
        return Math.toDegrees(lat2) to normalizeLongitude(Math.toDegrees(lon2))
    }

    fun normalizeLongitude(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0

    /**
     * Ground speed in m/s implied by two fixes. Returns null when the time delta is
     * non-positive, because a speed derived from a zero or negative interval is meaningless.
     */
    fun impliedSpeedMps(
        lat1: Double,
        lon1: Double,
        timestamp1Ms: Long,
        lat2: Double,
        lon2: Double,
        timestamp2Ms: Long
    ): Float? {
        val dtSeconds = (timestamp2Ms - timestamp1Ms) / 1000.0
        if (dtSeconds <= 0.0) return null
        return (distanceMeters(lat1, lon1, lat2, lon2) / dtSeconds).toFloat()
    }

    fun metersToLatitudeDegrees(meters: Double): Double =
        meters / (EARTH_RADIUS_METERS * PI / 180.0)

    fun metersToLongitudeDegrees(meters: Double, atLatitude: Double): Double =
        meters / (EARTH_RADIUS_METERS * cos(Math.toRadians(atLatitude)) * PI / 180.0)

    /** Slippy-map tile helpers, shared by the tile cache and the offline region manager. */
    fun lonToTileX(lon: Double, zoom: Int): Int =
        (((normalizeLongitude(lon) + 180.0) / 360.0) * 2.0.pow(zoom)).toInt()

    fun latToTileY(lat: Double, zoom: Int): Int {
        val clamped = lat.coerceIn(-85.0511, 85.0511)
        val rad = Math.toRadians(clamped)
        return ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * 2.0.pow(zoom)).toInt()
    }

    fun tileXToLon(x: Int, zoom: Int): Double =
        x / 2.0.pow(zoom) * 360.0 - 180.0

    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = PI - 2.0 * PI * y / 2.0.pow(zoom)
        return Math.toDegrees(atan2(0.5 * (Math.exp(n) - Math.exp(-n)), 1.0)).coerceIn(-85.0511, 85.0511)
    }
}