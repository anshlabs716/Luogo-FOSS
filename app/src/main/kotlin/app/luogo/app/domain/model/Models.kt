package app.luogo.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

enum class LocationSourceType(val label: String) {
    GNSS_DUAL_FREQ("GNSS (L1+L5)"),
    GNSS_STANDARD("GNSS"),
    WIFI_RTT("Wi-Fi RTT"),
    WIFI_NETWORK("Wi-Fi"),
    CELLULAR("Cellular"),
    BLE_RANGING("BLE"),
    UWB_RANGING("UWB"),
    INERTIAL_DEAD_RECKONING("Inertial DR"),
    BAROMETRIC_ALTITUDE("Barometer"),
    IP_FALLBACK("Network Fallback")
}

enum class LocationQualityLevel(val label: String) {
    HIGH("High Precision"),
    GOOD("Good"),
    MODERATE("Moderate"),
    POOR("Low Accuracy"),
    STALE("Stale"),
    SUSPICIOUS("Suspicious Jump")
}

enum class ActivityState(val label: String, val targetUpdateIntervalMs: Long) {
    STATIONARY("Stationary", 15_000L),
    WALKING("Walking", 2_000L),
    RUNNING("Running", 1_800L),
    CYCLING("Cycling", 1_800L),
    DRIVING("Driving", 1_500L),
    // Labelled "Unknown" rather than "Moving". This is the fallback when the reported
    // activity cannot be parsed, so calling it "Moving" asserted something untrue, and
    // rendered as the contradiction "Moving · 0.0 m/s" next to a stationary peer.
    UNKNOWN("Unknown", 2_000L)
}

@Serializable
data class FusedLocationFix(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val horizontalAccuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val sourceSummary: String = "GNSS + Wi-Fi",
    val activeSources: List<LocationSourceType> = listOf(LocationSourceType.GNSS_STANDARD),
    val confidence: Float = 0.9f,
    val qualityLevel: LocationQualityLevel = LocationQualityLevel.GOOD,
    val activityState: ActivityState = ActivityState.WALKING,
    val isMock: Boolean = false,
    val isSuspiciousJump: Boolean = false
) {
    fun ageMs(nowMs: Long = System.currentTimeMillis()): Long =
        (nowMs - timestampMs).coerceAtLeast(0L)

    fun isStale(nowMs: Long = System.currentTimeMillis(), thresholdMs: Long = 60_000L): Boolean =
        ageMs(nowMs) > thresholdMs

    fun formattedQualityBanner(nowMs: Long = System.currentTimeMillis()): String {
        val ageSeconds = ageMs(nowMs) / 1000.0
        val ageStr = if (ageSeconds < 60.0) {
            String.format("%.1f s ago", ageSeconds)
        } else {
            "${(ageSeconds / 60).toInt()} min ago"
        }
        return "${horizontalAccuracyMeters.toInt()} m accuracy · $sourceSummary · Updated $ageStr"
    }
}

enum class GroupCategory(val label: String) {
    FRIENDS("Friends"),
    FAMILY("Family"),
    CUSTOM("Custom")
}

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val colorArgb: Long,
    val publicKeyBase64: String,
    val sharingEnabled: Boolean = true,
    val temporarySharingUntilMs: Long? = null
)

@Serializable
data class PeerGroup(
    val id: String,
    val name: String,
    val category: GroupCategory = GroupCategory.CUSTOM,
    val ownerId: String,
    val createdAtMs: Long,
    val memberCount: Int = 1,
    val shareMyLocation: Boolean = true,
    val temporarySharingUntilMs: Long? = null
)

@Serializable
data class PeerLocationState(
    val userId: String,
    val groupId: String,
    val displayName: String,
    val colorArgb: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val activityState: ActivityState = ActivityState.WALKING,
    val sourceSummary: String = "GNSS + Wi-Fi",
    val timestampMs: Long,
    val isOnline: Boolean = true,
    val sharingPaused: Boolean = false
) {
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        (nowMs - timestampMs) > 120_000L
}

enum class ItemType(val label: String) {
    EARBUDS("Earbuds"),
    PHONE("Phone"),
    WATCH("Watch"),
    LAPTOP("Laptop"),
    KEYS("Keys"),
    BAG("Bag"),
    BIKE("Bike"),
    CUSTOM_DEVICE("Custom Device"),
    CUSTOM_ITEM("Custom Item")
}

enum class ItemPresenceStatus(val label: String) {
    LIVE("LIVE"),
    RECENTLY_SEEN("RECENTLY SEEN"),
    STALE("STALE"),
    UNKNOWN("UNKNOWN")
}

@Serializable
data class RegisteredItem(
    val id: String,
    val friendlyName: String,
    val itemType: ItemType,
    val ownerId: String,
    val createdAtMs: Long,
    val ephemeralIdentitySeedBase64: String,
    val currentRotatingBleIdHex: String,
    val rotationEpoch: Long,
    val lastSeenLatitude: Double? = null,
    val lastSeenLongitude: Double? = null,
    val lastSeenAccuracyMeters: Float? = null,
    val lastSeenTimestampMs: Long? = null,
    val lastSeenSource: String = "BLE Finding Network",
    val batteryPercent: Int? = null,
    val rssiDbm: Int? = null,
    val uwbDistanceMeters: Float? = null,
    val uwbAzimuthDegrees: Float? = null,
    val isLostMode: Boolean = false,
    val isRinging: Boolean = false
) {
    fun presenceStatus(nowMs: Long = System.currentTimeMillis()): ItemPresenceStatus {
        val ts = lastSeenTimestampMs ?: return ItemPresenceStatus.UNKNOWN
        val age = abs(nowMs - ts)
        return when {
            age <= 15_000L -> ItemPresenceStatus.LIVE
            age <= 15 * 60_000L -> ItemPresenceStatus.RECENTLY_SEEN
            else -> ItemPresenceStatus.STALE
        }
    }

    fun signalQualityDescription(): String {
        val rssi = rssiDbm ?: return "Out of direct BLE range"
        return when {
            rssi >= -55 -> "Very Strong ($rssi dBm · Immediate vicinity)"
            rssi >= -70 -> "Strong ($rssi dBm · Nearby)"
            rssi >= -82 -> "Moderate ($rssi dBm · Approaching)"
            else -> "Weak ($rssi dBm · Edge of BLE range)"
        }
    }
}

@Serializable
data class CrowdsourcedSightingReport(
    val reportId: String,
    val rotatingBleIdHex: String,
    val timestampMs: Long,
    val helperLatitude: Double,
    val helperLongitude: Double,
    val helperAccuracyMeters: Float,
    val rssiDbm: Int,
    val encryptedPayloadBase64: String,
    val authTagBase64: String,
    val protocolVersion: Int = 1,
    val isUploaded: Boolean = false,
    val retryCount: Int = 0
)

enum class PlaceCategory(val label: String) {
    HOME("Home"),
    SCHOOL("School"),
    WORK("Work"),
    CUSTOM("Custom")
}

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 120f,
    val enabled: Boolean = true,
    val notifyOnArrival: Boolean = true,
    val notifyOnDeparture: Boolean = true,
    val allowedGroupIdsCsv: String = "",
    val currentlyInside: Boolean = false,
    val lastTransitionMs: Long? = null
)

@Serializable
data class LocationHistoryPoint(
    val id: Long = 0,
    val subjectId: String,
    val subjectName: String,
    val isItem: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val activityState: ActivityState = ActivityState.WALKING,
    val sourceSummary: String = "GNSS",
    val timestampMs: Long,
    val tripId: String? = null
)

@Serializable
data class TripSummary(
    val tripId: String,
    val subjectId: String,
    val subjectName: String,
    val dateKey: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val distanceMeters: Double,
    val averageSpeedMps: Float,
    val maxSpeedMps: Float,
    val primaryActivity: ActivityState,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val pointCount: Int
)

enum class OfflineRegionStatus(val label: String) {
    READY("Downloaded"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    UPDATE_AVAILABLE("Update Available")
}

@Serializable
data class OfflineMapRegion(
    val id: String,
    val name: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val minZoom: Int = 10,
    val maxZoom: Int = 16,
    val styleId: String = "STANDARD_OSM",
    val status: OfflineRegionStatus = OfflineRegionStatus.READY,
    val progressPercent: Int = 100,
    val downloadedTiles: Int = 240,
    val totalTiles: Int = 240,
    val sizeBytes: Long = 4_820_000L,
    val updatedAtMs: Long = System.currentTimeMillis()
)

enum class MapStyleOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val attribution: String,
    val tileUrlTemplate: String
) {
    STANDARD_OSM(
        id = "STANDARD_OSM",
        title = "Standard Vector / OSM",
        subtitle = "OpenStreetMap & Protomaps streets",
        attribution = "© OpenStreetMap contributors · Protomaps",
        tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    ),
    PROTOMAPS_DARK(
        id = "PROTOMAPS_DARK",
        title = "Dark Night Mode",
        subtitle = "High-contrast dark cartography",
        attribution = "© OpenStreetMap contributors · CartoDB Dark Matter",
        tileUrlTemplate = "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
    ),
    TERRAIN_TOPO(
        id = "TERRAIN_TOPO",
        title = "Terrain & Elevation",
        subtitle = "Topographic contours and hillshade",
        attribution = "© OpenStreetMap contributors · OpenTopoMap (CC-BY-SA)",
        tileUrlTemplate = "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    ),
    SATELLITE_IMAGERY(
        id = "SATELLITE_IMAGERY",
        title = "Satellite Imagery",
        subtitle = "High-resolution orthoimagery + hybrid labels",
        attribution = "Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics, USDA, USGS",
        tileUrlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    )
}

enum class RoutingMode(val label: String, val speedEstimateMps: Double) {
    WALKING("Walking", 1.4),
    CYCLING("Cycling", 4.5),
    DRIVING("Driving", 13.5)
}

@Serializable
data class RouteResult(
    val mode: RoutingMode,
    val providerName: String,
    val isLiveBackendRoute: Boolean,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val polyline: List<Pair<Double, Double>>,
    val instructions: List<String>,
    val statusNotice: String? = null
)

@Serializable
data class UnknownTrackerAlert(
    val trackerSignatureId: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val sightingCount: Int,
    val distinctLocationsCount: Int,
    val latestRssiDbm: Int,
    val riskLevel: String,
    val sampleRotatingIdsCsv: String,
    val isDismissed: Boolean = false
)

@Serializable
data class NetworkConfig(
    val relayUrl: String = "https://relay.luogo.app",
    val useTorOrbot: Boolean = false,
    val socks5Enabled: Boolean = false,
    val socks5Host: String = "127.0.0.1",
    val socks5Port: Int = 9050,
    val retentionDays: Int = 30
)

data class DiagnosticsSnapshot(
    val availableSources: List<LocationSourceType>,
    val currentSourceSummary: String,
    val currentAccuracyMeters: Float?,
    val gnssSatellitesInFix: Int,
    val gnssDualFrequencySupported: Boolean,
    val wifiEnabled: Boolean,
    val wifiRttSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val bleScanSupported: Boolean,
    val uwbSupported: Boolean,
    val accelerometerAvailable: Boolean,
    val gyroscopeAvailable: Boolean,
    val magnetometerAvailable: Boolean,
    val barometerAvailable: Boolean,
    val networkConnected: Boolean,
    val ipv6Supported: Boolean,
    val proxySummary: String,
    val relayUrl: String,
    val relayReachable: Boolean,
    val webSocketConnected: Boolean,
    val pendingEncryptedReportsCount: Int,
    val lastSyncTimestampMs: Long?,
    val averageSyncIntervalSeconds: Double?,
    val syncIntervalsBySource: Map<String, Double>,
    val backgroundTrackingEnabled: Boolean,
    val batteryOptimizationExempt: Boolean
)
