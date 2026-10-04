package app.luogo.app.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.luogo.app.data.location.GeoMath
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.OfflineMapRegion
import app.luogo.app.domain.model.OfflineRegionStatus
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.RoutingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Map data access: raster tiles, offline region storage, geocoding and routing.
 *
 * Provider policy:
 *  - Basemap and satellite sources are OpenStreetMap-derived or Esri. Google tile services
 *    are never used, so the app carries no Google Maps dependency of any kind.
 *  - Every style declares its attribution, and the UI must display it.
 *  - Routing is pluggable. If no routing backend is configured, [computeRoute] returns a
 *    result with `isLiveBackendRoute = false` and an explicit notice rather than a fake route.
 */
class MapAndRoutingProvider(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "map_tiles").apply { mkdirs() }
    private val offlineDir = File(appContext.filesDir, "offline_maps").apply { mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val memoryCache = object : LinkedHashMap<String, Bitmap>(
        MEMORY_CACHE_ENTRIES, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MEMORY_CACHE_ENTRIES
    }

    // ------------------------------------------------------------------ tiles

    /**
     * Fetches one raster tile, preferring the offline store, then the memory cache, then disk,
     * then the network.
     *
     * Returns null when the tile genuinely cannot be obtained. Callers must show a loading or
     * unavailable state; they must never substitute a drawn placeholder, because a fabricated
     * map is worse than an honest blank one.
     */
    suspend fun fetchTileBitmap(
        style: MapStyleOption,
        zoom: Int,
        x: Int,
        y: Int,
        customSatelliteUrl: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (zoom !in MIN_ZOOM..MAX_ZOOM) return@withContext null
        if (x !in 0 until (1 shl zoom)) return@withContext null
        val maxIndex = (1 shl zoom) - 1
        if (y !in 0..maxIndex) return@withContext null

        val key = cacheKey(style, zoom, x, y, customSatelliteUrl)

        synchronized(memoryCache) { memoryCache[key] }?.let { return@withContext it }

        val offline = File(offlineDir, tileFileName(style, zoom, x, y))
        if (offline.exists() && offline.length() > 0) {
            return@withContext decode(offline.readBytes(), key)
        }

        val disk = File(cacheDir, tileFileName(style, zoom, x, y))
        if (disk.exists() && disk.length() > 0 && disk.lastModified() > System.currentTimeMillis() - DISK_CACHE_TTL_MS) {
            val bytes = disk.readBytes()
            decode(bytes, key)?.let { return@withContext it }
        }

        val url = tileUrl(style, zoom, x, y, customSatelliteUrl) ?: return@withContext null
        val bytes = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        }.getOrNull() ?: return@withContext null

        runCatching { disk.writeBytes(bytes) }
        decode(bytes, key)
    }

    private fun decode(bytes: ByteArray, key: String): Bitmap? {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return null
        synchronized(memoryCache) { memoryCache[key] = bitmap }
        return bitmap
    }

    /**
     * Builds the tile URL for a style.
     *
     * `customSatelliteUrl` lets a self-hoster point at their own imagery service; its template
     * must still contain the usual {z}/{x}/{y} placeholders.
     */
    fun tileUrl(
        style: MapStyleOption,
        zoom: Int,
        x: Int,
        y: Int,
        customSatelliteUrl: String? = null
    ): String? {
        val template = if (style == MapStyleOption.SATELLITE_IMAGERY && !customSatelliteUrl.isNullOrBlank()) {
            customSatelliteUrl
        } else {
            style.tileUrlTemplate
        }
        if (!template.contains("{z}") || !template.contains("{x}") || !template.contains("{y}")) return null
        return template
            .replace("{z}", zoom.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }

    fun attributionFor(style: MapStyleOption, customSatelliteUrl: String? = null): String {
        val usingCustom = style == MapStyleOption.SATELLITE_IMAGERY && !customSatelliteUrl.isNullOrBlank()
        return if (usingCustom) "Imagery: user-configured source" else style.attribution
    }

    /** True when a custom satellite template is well formed and therefore usable. */
    fun isValidCustomSatelliteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        return url.contains("{z}") && url.contains("{x}") && url.contains("{y}")
    }

    // --------------------------------------------------------- offline regions

    /** Enumerates the tiles a region covers, so download size can be shown before starting. */
    fun tilesForRegion(region: OfflineMapRegion): List<Triple<Int, Int, Int>> {
        val tiles = ArrayList<Triple<Int, Int, Int>>()
        for (zoom in region.minZoom..region.maxZoom) {
            val minX = GeoMath.lonToTileX(region.minLon, zoom)
            val maxX = GeoMath.lonToTileX(region.maxLon, zoom)
            val minY = GeoMath.latToTileY(region.maxLat, zoom)
            val maxY = GeoMath.latToTileY(region.minLat, zoom)
            for (x in minOf(minX, maxX)..maxOf(minX, maxX)) {
                for (y in minOf(minY, maxY)..maxOf(minY, maxY)) {
                    tiles.add(Triple(zoom, x, y))
                }
            }
        }
        return tiles
    }

    /**
     * Downloads the tiles for a region into the offline store.
     *
     * Reports progress through [onProgress] so the UI can show a real percentage, and honours
     * [shouldContinue] so the user can cancel or pause.
     */
    suspend fun downloadRegion(
        region: OfflineMapRegion,
        customSatelliteUrl: String? = null,
        onProgress: (downloaded: Int, total: Int) -> Unit,
        shouldContinue: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        val tiles = tilesForRegion(region)
        val total = tiles.size
        var downloaded = 0
        var bytes = 0L
        for ((zoom, x, y) in tiles) {
            if (!shouldContinue()) return@withContext
            val target = File(offlineDir, tileFileNameForStyle(region.styleId, zoom, x, y))
            if (!target.exists()) {
                val url = tileUrlForStyleId(region.styleId, zoom, x, y, customSatelliteUrl)
                if (url != null) {
                    val body = runCatching {
                        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            if (response.isSuccessful) response.body?.bytes() else null
                        }
                    }.getOrNull()
                    if (body != null) {
                        runCatching { target.writeBytes(body) }
                        bytes += body.size
                    }
                }
            }
            downloaded++
            onProgress(downloaded, total)
        }
        bytes
    }

    suspend fun deleteRegionFiles(region: OfflineMapRegion) = withContext(Dispatchers.IO) {
        for ((zoom, x, y) in tilesForRegion(region)) {
            runCatching { File(offlineDir, tileFileNameForStyle(region.styleId, zoom, x, y)).delete() }
        }
    }

    fun storageUsageBytes(): Long =
        (offlineDir.listFiles()?.sumOf { it.length() } ?: 0L) +
            (cacheDir.listFiles()?.sumOf { it.length() } ?: 0L)

    fun clearTileCache() {
        synchronized(memoryCache) { memoryCache.clear() }
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    private fun tileUrlForStyleId(styleId: String, zoom: Int, x: Int, y: Int, custom: String?): String? {
        val style = MapStyleOption.entries.firstOrNull { it.id == styleId } ?: return null
        return tileUrl(style, zoom, x, y, custom)
    }

    // ---------------------------------------------------------------- routing

    /**
     * Computes a route.
     *
     * With a reachable routing backend this is a live route. Without one it returns an
     * explicitly unavailable result: straight-line distance and a straight-line time
     * estimate, flagged `isLiveBackendRoute = false`, with a notice the UI must show.
     * It never invents turn-by-turn instructions.
     */
    suspend fun computeRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: RoutingMode,
        backendUrl: String? = null
    ): RouteResult = withContext(Dispatchers.IO) {
        val straightLineMeters = GeoMath.distanceMeters(fromLat, fromLon, toLat, toLon)
        val fallbackEstimate = RouteResult(
            mode = mode,
            providerName = "Offline estimate (no routing backend)",
            isLiveBackendRoute = false,
            distanceMeters = straightLineMeters,
            durationSeconds = (straightLineMeters / mode.speedEstimateMps).toLong(),
            polyline = listOf(fromLat to fromLon, toLat to toLon),
            instructions = emptyList(),
            statusNotice = "Routing is unavailable. Showing straight-line distance only. " +
                "Configure a routing backend in Settings to get turn-by-turn directions."
        )
        if (backendUrl.isNullOrBlank()) return@withContext fallbackEstimate

        val url = buildString {
            append(backendUrl.trimEnd('/'))
            append("/route/v1/")
            append(profileFor(mode)).append('/')
            append(fromLon).append(',').append(fromLat).append(';')
            append(toLon).append(',').append(toLat)
            append("?overview=full&geometries=geojson&steps=false")
        }
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext fallbackEstimate

        parseOsrmRoute(body, mode, fallbackEstimate)
    }

    private fun parseOsrmRoute(json: String, mode: RoutingMode, fallback: RouteResult): RouteResult {
        return try {
            val routes = Regex("\"routes\"\\s*:\\s*\\[").find(json) ?: return fallback
            if (routes.range.first == 0) return fallback
            val geometry = Regex("\"coordinates\"\\s*:\\s*(\\[[^\\]]*(?:\\][^\\]]*)*\\])")
                .find(json)?.groupValues?.getOrNull(1) ?: return fallback
            val distance = Regex("\"distance\"\\s*:\\s*([0-9.]+)").find(json)
                ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return fallback
            val duration = Regex("\"duration\"\\s*:\\s*([0-9.]+)").find(json)
                ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return fallback

            val polyline = Regex("\\[(-?[0-9.]+),(-?[0-9.]+)\\]")
                .findAll(geometry)
                .map { (it.groupValues[2].toDouble() to it.groupValues[1].toDouble()) }
                .toList()
            if (polyline.size < 2) return fallback

            RouteResult(
                mode = mode,
                providerName = "OSRM ($backendLabel)",
                isLiveBackendRoute = true,
                distanceMeters = distance,
                durationSeconds = duration.toLong(),
                polyline = polyline,
                instructions = emptyList()
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private val backendLabel: String get() = "self-hosted"

    private fun profileFor(mode: RoutingMode): String = when (mode) {
        RoutingMode.WALKING -> "foot"
        RoutingMode.CYCLING -> "bike"
        RoutingMode.DRIVING -> "driving"
    }

    // ------------------------------------------------------------- geocoding

    /**
     * Forward geocoding. Returns an empty list when no geocoding backend is configured,
     * rather than returning fabricated matches.
     */
    suspend fun geocode(query: String, backendUrl: String? = null): List<GeocodeResult> =
        withContext(Dispatchers.IO) {
            if (backendUrl.isNullOrBlank()) return@withContext emptyList()
            val url = buildString {
                append(backendUrl.trimEnd('/'))
                append("/search?format=jsonv2&q=")
                append(java.net.URLEncoder.encode(query, "UTF-8"))
                append("&limit=8")
            }
            val body = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull() ?: return@withContext emptyList()

            Regex("\"lat\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"lon\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(body)
                .mapNotNull { m ->
                    val lat = m.groupValues[1].toDoubleOrNull()
                    val lon = m.groupValues[2].toDoubleOrNull()
                    if (lat == null || lon == null) null else GeocodeResult(query, lat, lon)
                }
                .toList()
        }

    data class GeocodeResult(val label: String, val latitude: Double, val longitude: Double)

    // ---------------------------------------------------------------- helpers

    private fun cacheKey(style: MapStyleOption, zoom: Int, x: Int, y: Int, custom: String?): String =
        if (style == MapStyleOption.SATELLITE_IMAGERY && !custom.isNullOrBlank()) {
            "custom:$custom:$zoom:$x:$y"
        } else {
            "${style.id}:$zoom:$x:$y"
        }

    private fun tileFileName(style: MapStyleOption, zoom: Int, x: Int, y: Int): String =
        tileFileNameForStyle(style.id, zoom, x, y)

    private fun tileFileNameForStyle(styleId: String, zoom: Int, x: Int, y: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$styleId/$zoom/$x/$y".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$zoom-$digest.png"
    }

    companion object {
        const val MIN_ZOOM = 3
        const val MAX_ZOOM = 18
        private const val MEMORY_CACHE_ENTRIES = 220
        private const val DISK_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Used by the region manager to size a download before the user commits to it. */
        fun estimateRegionBytes(tileCount: Int): Long = tileCount * AVERAGE_TILE_BYTES

        private const val AVERAGE_TILE_BYTES = 20_000L

        fun statusFor(progressPercent: Int, paused: Boolean): OfflineRegionStatus = when {
            paused -> OfflineRegionStatus.PAUSED
            progressPercent >= 100 -> OfflineRegionStatus.READY
            else -> OfflineRegionStatus.DOWNLOADING
        }
    }
}