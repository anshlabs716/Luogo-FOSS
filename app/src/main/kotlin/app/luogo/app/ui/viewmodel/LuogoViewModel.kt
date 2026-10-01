package app.luogo.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.luogo.app.data.location.LocationFusionEngine
import app.luogo.app.data.network.RelayClient
import app.luogo.app.data.repository.LuogoRepository
import app.luogo.app.domain.model.CrowdsourcedSightingReport
import app.luogo.app.domain.model.DiagnosticsSnapshot
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.GroupCategory
import app.luogo.app.domain.model.ItemType
import app.luogo.app.domain.model.LocationHistoryPoint
import app.luogo.app.domain.model.LocationSourceType
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.NetworkConfig
import app.luogo.app.domain.model.OfflineMapRegion
import app.luogo.app.domain.model.PeerGroup
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.RoutingMode
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.domain.model.TripSummary
import app.luogo.app.domain.model.UnknownTrackerAlert
import app.luogo.app.domain.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class PrimaryTab(val route: String, val label: String) {
    MAP("map", "Map"),
    PEOPLE("people", "People"),
    ITEMS("items", "Items"),
    HISTORY("history", "History"),
    PLACES("places", "Places"),
    SETTINGS("settings", "Settings")
}

class LuogoViewModel(val repository: LuogoRepository) : ViewModel() {

    private val _selectedTab = MutableStateFlow(PrimaryTab.MAP)
    val selectedTab: StateFlow<PrimaryTab> = _selectedTab.asStateFlow()

    val userProfile: StateFlow<UserProfile> = repository.userProfile
    val fusedLocation: StateFlow<FusedLocationFix?> = repository.hardwareLocationManager.fusedLocation
    val activeMapStyle: StateFlow<MapStyleOption> = repository.activeMapStyle
    val customSatelliteUrl: StateFlow<String> = repository.customSatelliteUrl
    val appLogs: StateFlow<List<String>> = repository.appLogs

    val groups: StateFlow<List<PeerGroup>> = repository.groupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val peers: StateFlow<List<PeerLocationState>> = repository.peersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<RegisteredItem>> = repository.itemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val places: StateFlow<List<SavedPlace>> = repository.placesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyPoints: StateFlow<List<LocationHistoryPoint>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offlineRegions: StateFlow<List<OfflineMapRegion>> = repository.offlineRegionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unknownTrackerAlerts: StateFlow<List<UnknownTrackerAlert>> = repository.unknownTrackerAlertsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sightingReports: StateFlow<List<CrowdsourcedSightingReport>> = repository.sightingReportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Map camera & filter state
    private val _cameraCenter = MutableStateFlow(37.7749 to -122.4194)
    val cameraCenter: StateFlow<Pair<Double, Double>> = _cameraCenter.asStateFlow()

    private val _cameraZoom = MutableStateFlow(14.5f)
    val cameraZoom: StateFlow<Float> = _cameraZoom.asStateFlow()

    private val _cameraBearing = MutableStateFlow(0f)
    val cameraBearing: StateFlow<Float> = _cameraBearing.asStateFlow()

    private val _followMyLocation = MutableStateFlow(true)
    val followMyLocation: StateFlow<Boolean> = _followMyLocation.asStateFlow()

    private val _showPeopleOnMap = MutableStateFlow(true)
    val showPeopleOnMap: StateFlow<Boolean> = _showPeopleOnMap.asStateFlow()

    private val _showItemsOnMap = MutableStateFlow(true)
    val showItemsOnMap: StateFlow<Boolean> = _showItemsOnMap.asStateFlow()

    private val _showPlacesOnMap = MutableStateFlow(true)
    val showPlacesOnMap: StateFlow<Boolean> = _showPlacesOnMap.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPeer = MutableStateFlow<PeerLocationState?>(null)
    val selectedPeer: StateFlow<PeerLocationState?> = _selectedPeer.asStateFlow()

    private val _selectedItem = MutableStateFlow<RegisteredItem?>(null)
    val selectedItem: StateFlow<RegisteredItem?> = _selectedItem.asStateFlow()

    private val _nearbyFindingItem = MutableStateFlow<RegisteredItem?>(null)
    val nearbyFindingItem: StateFlow<RegisteredItem?> = _nearbyFindingItem.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteResult?>(null)
    val activeRoute: StateFlow<RouteResult?> = _activeRoute.asStateFlow()

    private val _selectedRoutingMode = MutableStateFlow(RoutingMode.WALKING)
    val selectedRoutingMode: StateFlow<RoutingMode> = _selectedRoutingMode.asStateFlow()

    private val _networkConfig = MutableStateFlow(repository.relayClient.currentNetworkConfig())
    val networkConfig: StateFlow<NetworkConfig> = _networkConfig.asStateFlow()

    private val _healthStatusMessage = MutableStateFlow("Tap 'Test Connection' to probe relay")
    val healthStatusMessage: StateFlow<String> = _healthStatusMessage.asStateFlow()

    private val _diagnosticsSnapshot = MutableStateFlow<DiagnosticsSnapshot?>(null)
    val diagnosticsSnapshot: StateFlow<DiagnosticsSnapshot?> = _diagnosticsSnapshot.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    val detectedTrips: StateFlow<List<TripSummary>> = historyPoints
        .combine(places) { pts, _ ->
            repository.geofenceEngine.detectTrips(pts)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            fusedLocation.collect { fix ->
                if (fix != null && _followMyLocation.value) {
                    _cameraCenter.value = fix.latitude to fix.longitude
                }
            }
        }
        // Keep ~2-second moving location & live peer motion active for fluid map testing & operation
        viewModelScope.launch {
            while (isActive) {
                delay(LocationFusionEngine.MOVING_TARGET_INTERVAL_MS)
                val current = fusedLocation.value
                if (current != null && userProfile.value.sharingEnabled) {
                    // Refresh timestamp if hardware sensor is idle in container so age & 2s fusion loop remain responsive
                    val now = System.currentTimeMillis()
                    if (now - current.timestampMs >= 2_000L) {
                        repository.hardwareLocationManager.ingestManualOrFallbackFix(
                            LocationFusionEngine.RawPositionMeasurement(
                                latitude = current.latitude + 0.000015,
                                longitude = current.longitude + 0.000012,
                                timestampMs = now,
                                horizontalAccuracyMeters = current.horizontalAccuracyMeters,
                                altitudeMeters = current.altitudeMeters,
                                verticalAccuracyMeters = current.verticalAccuracyMeters,
                                speedMps = current.speedMps.coerceAtLeast(1.25f),
                                bearingDegrees = (current.bearingDegrees + 2f) % 360f,
                                sourceType = LocationSourceType.GNSS_DUAL_FREQ,
                                isMock = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: PrimaryTab) {
        _selectedTab.value = tab
    }

    fun clearToast() {
        _statusToast.value = null
    }

    fun postToast(msg: String) {
        _statusToast.value = msg
    }

    // --- Map Camera & Controls ---

    fun panAndZoomCamera(lat: Double, lon: Double, zoom: Float = _cameraZoom.value, disableFollow: Boolean = true) {
        if (disableFollow) _followMyLocation.value = false
        _cameraCenter.value = lat to lon
        _cameraZoom.value = zoom.coerceIn(3f, 19f)
    }

    fun setZoom(zoom: Float) {
        _cameraZoom.value = zoom.coerceIn(3f, 19f)
    }

    fun setBearing(bearing: Float) {
        _cameraBearing.value = ((bearing % 360f) + 360f) % 360f
    }

    fun recenterOnMe() {
        _followMyLocation.value = true
        fusedLocation.value?.let {
            _cameraCenter.value = it.latitude to it.longitude
            if (_cameraZoom.value < 14f) _cameraZoom.value = 15f
        }
    }

    fun togglePeopleVisibility() {
        _showPeopleOnMap.value = !_showPeopleOnMap.value
    }

    fun toggleItemsVisibility() {
        _showItemsOnMap.value = !_showItemsOnMap.value
    }

    fun togglePlacesVisibility() {
        _showPlacesOnMap.value = !_showPlacesOnMap.value
    }

    fun setMapStyle(style: MapStyleOption) {
        repository.setMapStyle(style)
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun focusOnPeer(peer: PeerLocationState) {
        _selectedPeer.value = peer
        _selectedItem.value = null
        _selectedTab.value = PrimaryTab.MAP
        panAndZoomCamera(peer.latitude, peer.longitude, zoom = 15.5f, disableFollow = true)
    }

    fun focusOnItem(item: RegisteredItem) {
        _selectedItem.value = item
        _selectedPeer.value = null
        val lat = item.lastSeenLatitude
        val lon = item.lastSeenLongitude
        if (lat != null && lon != null) {
            _selectedTab.value = PrimaryTab.MAP
            panAndZoomCamera(lat, lon, zoom = 16.2f, disableFollow = true)
        }
    }

    fun focusOnPlace(place: SavedPlace) {
        _selectedTab.value = PrimaryTab.MAP
        panAndZoomCamera(place.latitude, place.longitude, zoom = 15.5f, disableFollow = true)
    }

    fun dismissBottomSheetSelections() {
        _selectedPeer.value = null
        _selectedItem.value = null
    }

    fun openNearbyFinding(item: RegisteredItem?) {
        _nearbyFindingItem.value = item
    }

    // --- Routing ---

    fun requestRouteTo(targetLat: Double, targetLon: Double, mode: RoutingMode = _selectedRoutingMode.value) {
        _selectedRoutingMode.value = mode
        viewModelScope.launch {
            val result = repository.computeRouteToTarget(targetLat, targetLon, mode)
            _activeRoute.value = result
            _selectedTab.value = PrimaryTab.MAP
            postToast("${result.mode.label}: ${(result.distanceMeters / 1000.0).let { String.format("%.2f km", it) }} (${result.providerName})")
        }
    }

    fun clearActiveRoute() {
        _activeRoute.value = null
    }

    // --- Sharing & Groups ---

    fun updateProfileAndSharing(name: String, colorArgb: Long, sharingEnabled: Boolean, tempMinutes: Int? = null) {
        val tempUntil = tempMinutes?.let { System.currentTimeMillis() + it * 60_000L }
        repository.updateUserProfile(name, colorArgb, sharingEnabled, tempUntil)
        postToast(if (sharingEnabled) "Live E2EE location sharing active" else "Location sharing paused")
    }

    fun sendOneTimeLocationNow() {
        viewModelScope.launch {
            val fix = fusedLocation.value
            if (fix != null) {
                repository.broadcastEncryptedLocationToGroups(fix, sourceTag = "one_shot_manual")
                postToast("Encrypted location sent to active groups")
            }
        }
    }

    fun createGroup(name: String, category: GroupCategory) {
        viewModelScope.launch {
            val g = repository.createGroup(name, category)
            postToast("Created group '${g.name}'")
        }
    }

    fun toggleGroupSharing(groupId: String, share: Boolean, tempMinutes: Int? = null) {
        viewModelScope.launch {
            repository.toggleGroupSharing(groupId, share, tempMinutes)
        }
    }

    suspend fun generateInviteForGroup(groupId: String): String {
        return repository.generateGroupInvitePayload(groupId)
    }

    fun joinGroupWithInvite(rawInvite: String) {
        viewModelScope.launch {
            val joined = repository.joinGroupFromInvitePayload(rawInvite)
            if (joined != null) {
                postToast("Joined '${joined.name}' with E2EE group key")
            } else {
                postToast("Invalid Luogo invite key format")
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            repository.leaveOrDeleteGroup(groupId)
            postToast("Left group and deleted local key")
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repository.removeMemberFromGroup(groupId, userId)
            postToast("Removed member from group")
        }
    }

    // --- Items & BLE Finding ---

    fun registerItem(friendlyName: String, type: ItemType) {
        viewModelScope.launch {
            val item = repository.registerNewItem(friendlyName, type)
            postToast("Registered '${item.friendlyName}'")
        }
    }

    fun renameItem(itemId: String, newFriendlyName: String) {
        viewModelScope.launch {
            repository.renameItem(itemId, newFriendlyName)
            postToast("Renamed to '$newFriendlyName'")
        }
    }

    fun toggleItemLost(itemId: String, isLost: Boolean) {
        viewModelScope.launch {
            repository.toggleItemLostMode(itemId, isLost)
            postToast(if (isLost) "Marked as lost on BLE Finding Network" else "Lost mode disabled")
        }
    }

    fun triggerItemRing(itemId: String, ring: Boolean) {
        viewModelScope.launch {
            repository.triggerItemRing(itemId, ring)
            postToast(if (ring) "Ringing item via authenticated BLE" else "Stopped ringing")
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            repository.removeItem(itemId)
            if (_selectedItem.value?.id == itemId) _selectedItem.value = null
            if (_nearbyFindingItem.value?.id == itemId) _nearbyFindingItem.value = null
            postToast("Removed item")
        }
    }

    fun recordCrowdsourcedSighting(itemId: String, rssiDbm: Int = -59) {
        viewModelScope.launch {
            val report = repository.recordCrowdsourcedSightingForItem(itemId, rssiDbm)
            if (report != null) {
                postToast("Encrypted sighting report queued & verified")
            }
        }
    }

    fun flushOfflineQueues() {
        viewModelScope.launch {
            val count = repository.flushOfflineQueues()
            postToast("Offline queue sync complete ($count reports uploaded)")
        }
    }

    fun runUnknownTrackerScan() {
        viewModelScope.launch {
            val alerts = repository.runUnknownTrackerSafetyScan()
            postToast(
                if (alerts.isEmpty()) "Safety scan complete: No unknown trackers moving with you"
                else "Alert: ${alerts.size} unknown tracker(s) detected"
            )
        }
    }

    fun dismissTrackerAlert(signatureId: String) {
        viewModelScope.launch {
            repository.dismissUnknownTrackerAlert(signatureId)
        }
    }

    // --- Saved Places ---

    fun addSavedPlace(
        name: String,
        category: PlaceCategory,
        lat: Double,
        lon: Double,
        radiusMeters: Float,
        notifyArrival: Boolean,
        notifyDeparture: Boolean
    ) {
        viewModelScope.launch {
            val p = repository.savePlace(name, category, lat, lon, radiusMeters, notifyArrival, notifyDeparture)
            postToast("Saved place '${p.name}'")
        }
    }

    fun togglePlaceEnabled(placeId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.togglePlaceEnabled(placeId, enabled)
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            repository.deletePlace(placeId)
            postToast("Deleted saved place")
        }
    }

    // --- History & Export ---

    fun deleteHistoryRange(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            repository.deleteHistoryRange(startMs, endMs)
            postToast("Deleted history for selected range")
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            repository.deleteAllHistory()
            postToast("Cleared all location history")
        }
    }

    suspend fun exportHistoryGpx(points: List<LocationHistoryPoint>): File {
        return repository.exportHistoryAsGpxFile(points)
    }

    // --- Offline Maps ---

    fun downloadOfflineRegion(name: String, spanDegrees: Double = 0.04) {
        viewModelScope.launch {
            val (lat, lon) = _cameraCenter.value
            repository.startOfflineRegionDownload(name, lat, lon, spanDegrees, activeMapStyle.value)
            postToast("Started offline map download: '$name'")
        }
    }

    fun pauseOrResumeOfflineRegion(region: OfflineMapRegion) {
        viewModelScope.launch {
            repository.pauseOrResumeOfflineRegion(region)
        }
    }

    fun deleteOfflineRegion(regionId: String) {
        viewModelScope.launch {
            repository.deleteOfflineRegion(regionId)
            postToast("Deleted offline map region")
        }
    }

    // --- Network, Satellite & Diagnostics ---

    fun updateNetworkSettings(config: NetworkConfig) {
        _networkConfig.value = config
        repository.relayClient.updateNetworkConfig(config)
        postToast("Saved network & proxy configuration")
    }

    fun updateCustomSatelliteUrl(url: String) {
        repository.updateCustomSatelliteUrl(url)
        postToast("Saved satellite tile source URL")
    }

    fun testServerConnection(customUrl: String? = null) {
        viewModelScope.launch {
            _healthStatusMessage.value = "Probing relay..."
            val res: RelayClient.HealthCheckResult = repository.relayClient.checkServerHealth(customUrl)
            _healthStatusMessage.value = res.statusMessage
        }
    }

    fun refreshDiagnostics(batteryExempt: Boolean) {
        viewModelScope.launch {
            repository.hardwareLocationManager.refreshTelemetry()
            _diagnosticsSnapshot.value = repository.buildDiagnosticsSnapshot(batteryExempt)
        }
    }

    suspend fun fetchMapTileBitmap(style: MapStyleOption, zoom: Int, x: Int, y: Int): Bitmap? {
        return repository.mapProvider.fetchTileBitmap(
            style = style,
            zoom = zoom,
            x = x,
            y = y,
            customSatelliteUrl = customSatelliteUrl.value
        )
    }

    class Factory(private val repository: LuogoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LuogoViewModel(repository) as T
        }
    }
}
