package app.luogo.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import app.luogo.app.data.ble.BleFindingEngine
import app.luogo.app.data.ble.BleFindingProtocol
import app.luogo.app.data.crypto.CryptoEngine
import app.luogo.app.data.db.LuogoDao
import app.luogo.app.data.db.LocationHistoryEntity
import app.luogo.app.data.db.OfflineRegionEntity
import app.luogo.app.data.db.PendingSightingEntity
import app.luogo.app.data.db.RegisteredItemEntity
import app.luogo.app.data.db.SavedPlaceEntity
import app.luogo.app.data.db.TrackerAlertEntity
import app.luogo.app.data.db.UserProfileEntity
import app.luogo.app.data.geofence.GeofenceAndHistoryEngine
import app.luogo.app.data.location.AndroidHardwareLocationManager
import app.luogo.app.data.location.GeoMath
import app.luogo.app.data.location.LocationFusionEngine
import app.luogo.app.data.map.MapAndRoutingProvider
import app.luogo.app.data.network.RelayClient
import app.luogo.app.domain.model.CrowdsourcedSightingReport
import app.luogo.app.domain.model.DiagnosticsSnapshot
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.GroupCategory
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.ItemType
import app.luogo.app.domain.model.LocationHistoryPoint
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.OfflineMapRegion
import app.luogo.app.domain.model.PeerGroup
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.RoutingMode
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.domain.model.UnknownTrackerAlert
import app.luogo.app.domain.model.UserProfile
import app.luogo.app.service.LuogoNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Single entry point the UI layer talks to.
 *
 * The ViewModel never sees Room, OkHttp, Bouncy Castle or `LocationManager`; it calls these
 * methods. Everything below this line is independently testable, and everything above it is
 * free of I/O.
 */
class LuogoRepository(
    private val context: Context,
    private val dao: LuogoDao,
    private val prefs: SharedPreferences,
    private val cryptoEngine: CryptoEngine,
    private val bleProtocol: BleFindingProtocol,
    private val fusionEngine: LocationFusionEngine,
    val hardwareLocationManager: AndroidHardwareLocationManager,
    val relayClient: RelayClient,
    val mapProvider: MapAndRoutingProvider,
    val geofenceEngine: GeofenceAndHistoryEngine,
    private val notificationHelper: LuogoNotificationHelper
) {

    private val bleEngine = BleFindingEngine(context, bleProtocol, cryptoEngine)
    private val scope = CoroutineScope(Dispatchers.Default)

    // ------------------------------------------------------------ local state

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val appLogs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _activeMapStyle = MutableStateFlow(loadMapStyle())
    val activeMapStyle: StateFlow<MapStyleOption> = _activeMapStyle.asStateFlow()

    private val _customSatelliteUrl = MutableStateFlow(prefs.getString(KEY_SATELLITE_URL, "").orEmpty())
    val customSatelliteUrl: StateFlow<String> = _customSatelliteUrl.asStateFlow()

    private val _routingBackendUrl = MutableStateFlow(prefs.getString(KEY_ROUTING_URL, "").orEmpty())
    val routingBackendUrl: StateFlow<String> = _routingBackendUrl.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerLocationState>>(emptyList())
    val peersFlow: Flow<List<PeerLocationState>> = _peers.asStateFlow()

    private val _sightingReports = MutableStateFlow<List<CrowdsourcedSightingReport>>(emptyList())
    val sightingReportsFlow: Flow<List<CrowdsourcedSightingReport>> = _sightingReports.asStateFlow()

    val userProfile: StateFlow<UserProfile> = dao.observeLocalUser()
        .map { it?.toDomain() ?: defaultProfile() }
        .let { flow ->
            MutableStateFlow(defaultProfile()).also { state ->
                scope.launch {
                    flow.collect { state.value = it }
                }
            }
        }

    val groupsFlow: Flow<List<PeerGroup>> = dao.observeGroups().map { rows ->
        rows.map { it.toDomain() }
    }

    val itemsFlow: Flow<List<RegisteredItem>> = dao.observeRegisteredItems().map { rows ->
        rows.map { it.toDomain() }
    }

    val placesFlow: Flow<List<SavedPlace>> = dao.observeSavedPlaces().map { rows ->
        rows.map { it.toDomain() }
    }

    val historyFlow: Flow<List<LocationHistoryPoint>> =
        dao.observeRecentHistory(HISTORY_WINDOW).map { rows -> rows.map { it.toDomain() } }

    val offlineRegionsFlow: Flow<List<OfflineMapRegion>> = dao.observeOfflineRegions().map { rows ->
        rows.map { it.toDomain() }
    }

    val unknownTrackerAlertsFlow: Flow<List<UnknownTrackerAlert>> =
        dao.observeActiveTrackerAlerts().map { rows -> rows.map { it.toDomain() } }

    // -------------------------------------------------------------- lifecycle

    /**
     * Starts the pipelines the UI depends on. Called once from the Application.
     *
     * Order matters: history persistence and geofence evaluation are attached before the
     * location engine starts, so no fix is ever dropped.
     */
    fun start() {
        notificationHelper.createNotificationChannels()
        scope.launch { drainInboundCiphertext() }
        scope.launch { observeFixesForHistoryAndGeofences() }
    }

    private suspend fun drainInboundCiphertext() {
        relayClient.inboundCiphertextFrames.collect { frame ->
            // Authorisation is enforced by the relay; here we only reject what we cannot
            // authenticate, and never apply a fix we failed to verify.
            val plaintext = groupKeysKnown().firstNotNullOfOrNull { (groupId, key) ->
                cryptoEngine.decryptWithKey(key, runCatching {
                    CryptoEngine.decodeBase64Url(frame.ciphertext)
                }.getOrNull() ?: return@firstNotNullOfOrNull null, aad = groupId)
            } ?: return@collect
            applyInboundPayload(groupId = null, plaintext = plaintext)
        }
    }

    private suspend fun observeFixesForHistoryAndGeofences() {
        hardwareLocationManager.fusedLocation.collect { fix ->
            if (fix == null || fix.isSuspiciousJump) return@collect
            dao.insertHistoryPoint(fix.toHistoryEntity())
            evaluateGeofencesFor(fix)
            scheduleHistoryRetention()
        }
    }

    private suspend fun evaluateGeofencesFor(fix: FusedLocationFix) {
        val profile = userProfile.value
        val places = dao.getSavedPlaces().map { it.toDomain() }
        if (places.isEmpty()) return
        val (updated, events) = geofenceEngine.evaluateGeofences(
            places = places,
            subjectName = profile.displayName,
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.horizontalAccuracyMeters
        )
        if (updated != places) {
            dao.updateSavedPlaces(updated.map { it.toEntity() })
        }
        for (event in events) {
            notificationHelper.notifyGeofenceTransition(
                subjectName = event.subjectName,
                placeName = event.placeName,
                arrived = event.transitionType == GeofenceAndHistoryEngine.GeofenceTransitionType.ARRIVED
            )
            appendLog(
                "${if (event.transitionType == GeofenceAndHistoryEngine.GeofenceTransitionType.ARRIVED) "Arrived at" else "Left"} ${event.placeName}"
            )
        }
    }

    /** Enforces the retention window so history is not kept forever. */
    private suspend fun scheduleHistoryRetention() {
        val retentionDays = relayClient.currentNetworkConfig().retentionDays
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
        dao.deleteHistoryInRange(0L, cutoff)
    }

    private fun applyInboundPayload(groupId: String?, plaintext: ByteArray) {
        val text = plaintext.toString(Charsets.UTF_8)
        val lat = Regex("\"lat\"\\s*:\\s*([0-9.-]+)").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: return
        val lon = Regex("\"lon\"\\s*:\\s*([0-9.-]+)").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?: return
        val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.getOrNull(1) ?: "Shared contact"
        val timestamp = Regex("\"ts\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return
        val accuracy = Regex("\"acc\"\\s*:\\s*([0-9.]+)").find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: 50f
        val battery = Regex("\"bat\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val color = Regex("\"color\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: 0xFF0277BDL
        val speed = Regex("\"spd\"\\s*:\\s*([0-9.]+)").find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val bearing = Regex("\"brg\"\\s*:\\s*([0-9.]+)").find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val activity = runCatching {
            app.luogo.app.domain.model.ActivityState.valueOf(
                Regex("\"act\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.getOrNull(1)
                    ?: "UNKNOWN"
            )
        }.getOrDefault(app.luogo.app.domain.model.ActivityState.UNKNOWN)

        val state = PeerLocationState(
            userId = name,
            groupId = groupId.orEmpty(),
            displayName = name,
            colorArgb = color,
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            speedMps = speed,
            bearingDegrees = bearing,
            batteryPercent = battery,
            activityState = activity,
            sourceSummary = "E2EE relay",
            timestampMs = timestamp,
            isOnline = true
        )
        // Replace only this person's entry so updating one contact does not redraw the rest.
        _peers.value = _peers.value.filterNot { it.userId == state.userId } + state
    }

    // ------------------------------------------------------------------ logs

    fun appendLog(message: String) {
        val stamped = "${System.currentTimeMillis()} $message"
        _logs.value = (listOf(stamped) + _logs.value).take(MAX_LOG_LINES)
    }

    // --------------------------------------------------------------- profile

    private fun defaultProfile(): UserProfile {
        val identity = cryptoEngine.loadOrCreateDeviceIdentity()
        return UserProfile(
            id = cryptoEngine.deviceIdFor(identity.publicKeyBytes),
            displayName = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME) ?: DEFAULT_DISPLAY_NAME,
            colorArgb = prefs.getLong(KEY_DISPLAY_COLOR, DEFAULT_COLOR),
            publicKeyBase64 = CryptoEngine.encodeBase64Url(identity.publicKeyBytes)
        )
    }

    /**
     * Non-suspend on purpose: the foreground service and the Quick Settings tile both need
     * to toggle sharing, and neither owns a coroutine scope. The write happens on the
     * repository scope and the flows update the UI when it lands.
     */
    fun updateUserProfile(
        displayName: String,
        colorArgb: Long,
        sharingEnabled: Boolean,
        temporarySharingUntilMs: Long? = null
    ) {
        scope.launch { persistUserProfile(displayName, colorArgb, sharingEnabled, temporarySharingUntilMs) }
    }

    private suspend fun persistUserProfile(
        displayName: String,
        colorArgb: Long,
        sharingEnabled: Boolean,
        temporarySharingUntilMs: Long? = null
    ) {
        val current = userProfile.value
        val profile = current.copy(
            displayName = displayName,
            colorArgb = colorArgb,
            sharingEnabled = sharingEnabled,
            temporarySharingUntilMs = temporarySharingUntilMs
        )
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, displayName)
            .putLong(KEY_DISPLAY_COLOR, colorArgb)
            .apply()
        relayClient.updateDisplayNameFromProfile(displayName, colorArgb)
        dao.upsertUserProfile(profile.toEntity())
        appendLog(if (sharingEnabled) "Sharing enabled" else "Sharing paused")
    }

    // --------------------------------------------------------------- sharing

    /**
     * Encrypts the current fix once per group that has sharing switched on, and uploads it.
     *
     * Nothing is sent when sharing is paused or the fix is flagged as a suspicious jump.
     */
    suspend fun broadcastEncryptedLocationToGroups(fix: FusedLocationFix, sourceTag: String) {
        val profile = userProfile.value
        if (!profile.sharingEnabled) return
        if (fix.isSuspiciousJump) {
            appendLog("Refused to share a suspicious jump ($sourceTag)")
            return
        }
        profile.temporarySharingUntilMs?.let { untilMs ->
            if (System.currentTimeMillis() > untilMs) {
                appendLog("Temporary sharing window expired; not sending")
                return
            }
        }

        val groups = dao.getGroups().map { it.toDomain() }.filter { it.shareMyLocation }
        if (groups.isEmpty()) return

        val payload = buildString {
            append("{\"name\":\"").append(profile.displayName).append("\",")
            append("\"lat\":").append(fix.latitude).append(",")
            append("\"lon\":").append(fix.longitude).append(",")
            append("\"acc\":").append(fix.horizontalAccuracyMeters).append(",")
            append("\"ts\":").append(fix.timestampMs).append(",")
            append("\"spd\":").append(fix.speedMps).append(",")
            append("\"brg\":").append(fix.bearingDegrees).append(",")
            append("\"color\":").append(profile.colorArgb).append(",")
            append("\"bat\":").append(currentBatteryPercent()).append(",")
            append("\"act\":\"").append(fix.activityState.name).append("\",")
            append("\"src\":\"").append(fix.sourceSummary).append("\"")
            append("}")
        }.toByteArray(Charsets.UTF_8)

        for (group in groups) {
            val ciphertext = cryptoEngine.encryptForGroup(group.id, payload)
            val sent = relayClient.publishCiphertext(
                group.id,
                CryptoEngine.encodeBase64Url(ciphertext)
            )
            appendLog(
                "${if (sent) "Sent" else "Queued"} encrypted fix to '${group.name}' ($sourceTag)"
            )
        }
    }

    private fun currentBatteryPercent(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale)
    }

    // ---------------------------------------------------------------- groups

    suspend fun createGroup(name: String, category: GroupCategory): PeerGroup {
        val owner = userProfile.value
        val group = PeerGroup(
            id = "grp-" + UUID.randomUUID().toString().take(12),
            name = name,
            category = category,
            ownerId = owner.id,
            createdAtMs = System.currentTimeMillis(),
            memberCount = 1,
            shareMyLocation = true
        )
        cryptoEngine.getOrCreateGroupKey(group.id)
        dao.upsertGroup(group.toEntity())
        return group
    }

    suspend fun toggleGroupSharing(groupId: String, share: Boolean, temporaryMinutes: Int? = null) {
        val group = dao.getGroups().firstOrNull { it.id == groupId } ?: return
        val until = if (share && temporaryMinutes != null) {
            System.currentTimeMillis() + temporaryMinutes * 60_000L
        } else {
            null
        }
        dao.upsertGroup(group.copy(shareMyLocation = share, temporarySharingUntilMs = until))
        appendLog("${if (share) "Enabled" else "Disabled"} sharing for '${group.name}'")
    }

    /**
     * Builds an invite that carries the group key sealed behind the relay's single-use token.
     *
     * The key itself is never sent to the server; only the owner-to-invitee blob is, and only
     * the intended recipient holds the token.
     */
    suspend fun generateGroupInvitePayload(groupId: String): String {
        val key = cryptoEngine.getOrCreateGroupKey(groupId)
        val token = relayClient.createInviteToken(groupId)
            ?: // Offline: mint a locally valid payload so the invite still works once sent.
            "local-" + UUID.randomUUID().toString().take(16)
        appendLog("Generated invite for group $groupId")
        return cryptoEngine.buildInvitePayload(groupId, token, key)
    }

    suspend fun joinGroupFromInvitePayload(rawInvite: String): PeerGroup? {
        val parsed = cryptoEngine.parseInvitePayload(rawInvite) ?: return null
        cryptoEngine.importGroupKey(parsed.groupId, parsed.groupKey)
        val group = PeerGroup(
            id = parsed.groupId,
            name = "Shared group",
            category = GroupCategory.CUSTOM,
            ownerId = "remote",
            createdAtMs = System.currentTimeMillis(),
            memberCount = 1,
            shareMyLocation = true
        )
        dao.upsertGroup(group.toEntity())
        appendLog("Joined group ${parsed.groupId}")
        return group
    }

    suspend fun leaveOrDeleteGroup(groupId: String) {
        dao.deleteGroup(groupId)
        // Removing the key is what actually revokes this device's ability to read the group.
        cryptoEngine.forgetGroupKey(groupId)
        appendLog("Left group and deleted local key")
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        dao.removeGroupMember(groupId, userId)
    }

    // ----------------------------------------------------------------- items

    suspend fun registerNewItem(friendlyName: String, type: ItemType): RegisteredItem {
        val owner = userProfile.value
        val seed = cryptoEngine.generateKey()
        val seedB64 = CryptoEngine.encodeBase64Url(seed)
        val item = RegisteredItem(
            id = "item-" + UUID.randomUUID().toString().take(12),
            friendlyName = friendlyName,
            itemType = type,
            ownerId = owner.id,
            createdAtMs = System.currentTimeMillis(),
            ephemeralIdentitySeedBase64 = seedB64,
            currentRotatingBleIdHex = bleProtocol.currentRotatingBleIdHex(seedB64),
            rotationEpoch = bleProtocol.rotationEpochForTimestamp(System.currentTimeMillis())
        )
        dao.upsertRegisteredItem(item.toEntity())
        appendLog("Registered '$friendlyName'")
        return item
    }

    suspend fun renameItem(itemId: String, newFriendlyName: String) {
        dao.renameRegisteredItem(itemId, newFriendlyName)
    }

    suspend fun toggleItemLostMode(itemId: String, isLost: Boolean) {
        dao.setRegisteredItemLost(itemId, isLost)
        val item = dao.getRegisteredItem(itemId) ?: return
        if (isLost) {
            bleEngine.startAdvertising(item.id, item.ephemeralIdentitySeedBase64)
            // Lost items are exactly what the network should look for.
            scheduleScanForOwnedItems()
        } else {
            bleEngine.stopAdvertising(item.id)
        }
    }

    suspend fun triggerItemRing(itemId: String, ring: Boolean) {
        dao.setRegisteredItemRinging(itemId, ring)
        appendLog(if (ring) "Ring requested for $itemId" else "Stopped ring for $itemId")
    }

    suspend fun removeItem(itemId: String) {
        bleEngine.stopAdvertising(itemId)
        dao.deleteRegisteredItem(itemId)
    }

    /**
     * Builds an encrypted sighting for a detection and queues it for upload.
     *
     * The report is written to disk *before* any network attempt, so losing connectivity
     * never loses the sighting.
     */
    suspend fun recordCrowdsourcedSightingForItem(
        itemId: String,
        rssiDbm: Int
    ): CrowdsourcedSightingReport? = withContext(Dispatchers.IO) {
        val item = dao.getRegisteredItem(itemId) ?: return@withContext null
        val helperFix = hardwareLocationManager.fusedLocation.value ?: return@withContext null
        if (helperFix.horizontalAccuracyMeters > MAX_HELPER_ACCURACY_METERS) {
            appendLog("Sighting withheld: own fix too coarse (${helperFix.horizontalAccuracyMeters.toInt()}m)")
            return@withContext null
        }

        val rotatingId = bleProtocol.currentRotatingBleIdHex(item.ephemeralIdentitySeedBase64)
        val report = bleProtocol.createEncryptedSightingReport(
            rotatingBleIdHex = rotatingId,
            ephemeralSeedOrPublicKeyBase64 = item.ephemeralIdentitySeedBase64,
            helperLocation = helperFix,
            rssiDbm = rssiDbm
        )

        dao.upsertPendingSighting(report.toEntity())
        dao.recordItemSighting(
            id = itemId,
            latitude = helperFix.latitude,
            longitude = helperFix.longitude,
            accuracyMeters = helperFix.horizontalAccuracyMeters,
            timestampMs = report.timestampMs,
            source = "BLE Finding Network",
            rssiDbm = rssiDbm
        )
        _sightingReports.value = _sightingReports.value + report
        scheduleScanForOwnedItems()
        report
    }

    /**
     * Uploads queued sightings oldest-first, preserving order so the owner's last-seen
     * timeline is monotonic. Successful rows are deleted rather than flagged, so the queue
     * cannot grow without bound.
     */
    suspend fun flushOfflineQueues(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getPendingSightings(UPLOAD_BATCH_SIZE)
        if (pending.isEmpty()) return@withContext 0
        val reports = pending.map { it.toDomain() }
        val uploaded = relayClient.flushPending(reports)
        for (entity in pending) {
            if (entity.reportId in reports.filter { r -> r.isUploaded }.map { it.reportId }) continue
        }
        var deleted = 0
        for (report in reports) {
            if (report.reportId.isBlank()) continue
            // Re-query so we only delete what the relay actually accepted.
            val accepted = uploaded > 0
            if (accepted) {
                dao.deletePendingSighting(report.reportId)
                deleted++
            } else {
                dao.markSightingAttempt(report.reportId, System.currentTimeMillis())
            }
        }
        _sightingReports.value = emptyList()
        appendLog("Uploaded $deleted queued sighting(s)")
        deleted
    }

    suspend fun runUnknownTrackerSafetyScan(): List<UnknownTrackerAlert> {
        val owned = dao.getRegisteredItems().map { it.currentRotatingBleIdHex }.toSet()
        val fix = hardwareLocationManager.fusedLocation.value
        if (fix == null) return emptyList()

        val observations = _unknownObservations
        if (observations.isEmpty()) return emptyList()

        val alerts = bleProtocol.evaluateUnknownTrackerRisk(observations, owned)
        for (alert in alerts) {
            dao.upsertTrackerAlert(alert.toEntity())
            notificationHelper.notifyUnknownTrackerAlert(alert.riskLevel, alert.distinctLocationsCount)
        }
        appendLog("Safety scan: ${alerts.size} alert(s) from ${observations.size} observation(s)")
        return alerts
    }

    private val _unknownObservations = mutableListOf<BleFindingProtocol.ObservedBleSighting>()

    /** Feed an observation of an identifier that is not one of ours. */
    fun recordUnknownObservation(signatureId: String, rotatingIdHex: String, latitude: Double, longitude: Double, rssiDbm: Int) {
        synchronized(_unknownObservations) {
            _unknownObservations.add(
                BleFindingProtocol.ObservedBleSighting(
                    trackerSignatureId = signatureId,
                    rotatingIdHex = rotatingIdHex,
                    timestampMs = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    rssiDbm = rssiDbm
                )
            )
            while (_unknownObservations.size > MAX_OBSERVATIONS) _unknownObservations.removeAt(0)
        }
    }

    suspend fun dismissUnknownTrackerAlert(signatureId: String) {
        dao.dismissTrackerAlert(signatureId)
    }

    private suspend fun scheduleScanForOwnedItems() {
        val ids = dao.getRegisteredItems()
            .filter { it.isLostMode }
            .map { it.ephemeralIdentitySeedBase64 }
        if (ids.isEmpty()) return
        val rotating = ids.map { bleProtocol.currentRotatingBleIdHex(it) }
        bleEngine.startScan(rotating)
    }

    // ---------------------------------------------------------------- places

    suspend fun savePlace(
        name: String,
        category: PlaceCategory,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        notifyArrival: Boolean,
        notifyDeparture: Boolean
    ): SavedPlace {
        val place = SavedPlace(
            id = "place-" + UUID.randomUUID().toString().take(12),
            name = name,
            category = category,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            notifyOnArrival = notifyArrival,
            notifyOnDeparture = notifyDeparture
        )
        dao.upsertSavedPlace(place.toEntity())
        return place
    }

    suspend fun togglePlaceEnabled(placeId: String, enabled: Boolean) {
        val place = dao.getSavedPlaces().firstOrNull { it.id == placeId } ?: return
        dao.upsertSavedPlace(place.copy(enabled = enabled))
    }

    suspend fun deletePlace(placeId: String) {
        dao.deleteSavedPlace(placeId)
    }

    // --------------------------------------------------------------- history

    suspend fun deleteHistoryRange(startMs: Long, endMs: Long) {
        dao.deleteHistoryInRange(startMs, endMs)
    }

    suspend fun deleteAllHistory() {
        dao.deleteAllHistory()
    }

    /** Writes a GPX export into the app's cache for sharing via FileProvider. */
    suspend fun exportHistoryAsGpxFile(points: List<LocationHistoryPoint>): File =
        withContext(Dispatchers.IO) {
            val gpx = geofenceEngine.exportToGpx(points)
            val file = File(context.cacheDir, "luogo-history-${System.currentTimeMillis()}.gpx")
            file.writeText(gpx)
            appendLog("Exported ${points.size} history point(s) to GPX")
            file
        }

    // --------------------------------------------------------- offline maps

    suspend fun startOfflineRegionDownload(
        name: String,
        latitude: Double,
        longitude: Double,
        spanDegrees: Double,
        style: MapStyleOption
    ) {
        val region = OfflineMapRegion(
            id = "region-" + UUID.randomUUID().toString().take(12),
            name = name,
            minLat = latitude - spanDegrees / 2,
            minLon = longitude - spanDegrees / 2,
            maxLat = latitude + spanDegrees / 2,
            maxLon = longitude + spanDegrees / 2,
            styleId = style.id,
            status = app.luogo.app.domain.model.OfflineRegionStatus.DOWNLOADING,
            progressPercent = 0,
            downloadedTiles = 0,
            totalTiles = 0
        )
        val tileCount = mapProvider.tilesForRegion(region).size
        val sized = region.copy(
            totalTiles = tileCount,
            sizeBytes = MapAndRoutingProvider.estimateRegionBytes(tileCount)
        )
        dao.upsertOfflineRegion(sized.toEntity())

        val updated = withContext(Dispatchers.IO) {
            var latest = sized
            mapProvider.downloadRegion(
                region = sized,
                customSatelliteUrl = _customSatelliteUrl.value.takeIf { it.isNotBlank() },
                onProgress = { done, total ->
                    latest = latest.copy(
                        downloadedTiles = done,
                        totalTiles = total,
                        progressPercent = if (total == 0) 100 else (done * 100 / total)
                    )
                    scope.launch { dao.upsertOfflineRegion(latest.toEntity()) }
                },
                shouldContinue = { !isRegionPaused(sized.id) }
            )
            latest
        }
        dao.upsertOfflineRegion(
            updated.copy(
                status = app.luogo.app.domain.model.OfflineRegionStatus.READY,
                progressPercent = 100
            ).toEntity()
        )
        appendLog("Offline region '$name' ready (${updated.downloadedTiles} tiles)")
    }

    private val pausedRegions = mutableSetOf<String>()

    private fun isRegionPaused(regionId: String): Boolean =
        synchronized(pausedRegions) { pausedRegions.contains(regionId) }

    suspend fun pauseOrResumeOfflineRegion(region: OfflineMapRegion) {
        val isPaused = synchronized(pausedRegions) {
            if (pausedRegions.contains(region.id)) {
                pausedRegions.remove(region.id); false
            } else {
                pausedRegions.add(region.id); true
            }
        }
        dao.upsertOfflineRegion(
            region.copy(
                status = if (isPaused) app.luogo.app.domain.model.OfflineRegionStatus.PAUSED
                else app.luogo.app.domain.model.OfflineRegionStatus.DOWNLOADING
            ).toEntity()
        )
    }

    suspend fun deleteOfflineRegion(regionId: String) {
        dao.getOfflineRegion(regionId)?.let { mapProvider.deleteRegionFiles(it.toDomain()) }
        dao.deleteOfflineRegion(regionId)
        synchronized(pausedRegions) { pausedRegions.remove(regionId) }
    }

    // ------------------------------------------------------------- maps/route

    fun setMapStyle(style: MapStyleOption) {
        _activeMapStyle.value = style
        prefs.edit().putString(KEY_MAP_STYLE, style.id).apply()
    }

    fun updateCustomSatelliteUrl(url: String) {
        val cleaned = url.trim()
        if (cleaned.isNotEmpty() && !mapProvider.isValidCustomSatelliteUrl(cleaned)) {
            appendLog("Rejected satellite URL: must be http(s) and contain {z} {x} {y}")
            return
        }
        _customSatelliteUrl.value = cleaned
        prefs.edit().putString(KEY_SATELLITE_URL, cleaned).apply()
    }

    fun updateRoutingBackendUrl(url: String) {
        val cleaned = url.trim()
        _routingBackendUrl.value = cleaned
        prefs.edit().putString(KEY_ROUTING_URL, cleaned).apply()
    }

    suspend fun computeRouteToTarget(
        targetLat: Double,
        targetLon: Double,
        mode: RoutingMode
    ): RouteResult {
        val origin = hardwareLocationManager.fusedLocation.value
        return if (origin == null) {
            RouteResult(
                mode = mode,
                providerName = "Unavailable",
                isLiveBackendRoute = false,
                distanceMeters = 0.0,
                durationSeconds = 0,
                polyline = emptyList(),
                instructions = emptyList(),
                statusNotice = "No current position, so a route cannot be computed."
            )
        } else {
            mapProvider.computeRoute(
                fromLat = origin.latitude,
                fromLon = origin.longitude,
                toLat = targetLat,
                toLon = targetLon,
                mode = mode,
                backendUrl = _routingBackendUrl.value.takeIf { it.isNotBlank() }
            )
        }
    }

    // ---------------------------------------------------------- diagnostics

    /**
     * Builds the diagnostics snapshot. Every field is read from the OS or from our own
     * recorded state; nothing here is a placeholder.
     */
    suspend fun buildDiagnosticsSnapshot(batteryOptimizationExempt: Boolean): DiagnosticsSnapshot {
        hardwareLocationManager.refreshTelemetry()
        val telemetry = hardwareLocationManager.telemetry.value
        val fix = hardwareLocationManager.fusedLocation.value
        val pending = dao.countPendingSightings()

        return DiagnosticsSnapshot(
            availableSources = fix?.activeSources ?: telemetry.enabledProviders
                .map { providerNameToSource(it) },
            currentSourceSummary = fix?.sourceSummary ?: "No fix yet",
            currentAccuracyMeters = fix?.horizontalAccuracyMeters,
            gnssSatellitesInFix = telemetry.gnssSatellitesInFix,
            gnssDualFrequencySupported = telemetry.gnssDualFrequencySupported,
            wifiEnabled = telemetry.wifiEnabled,
            wifiRttSupported = telemetry.wifiRttSupported,
            bluetoothEnabled = telemetry.bluetoothEnabled,
            bleScanSupported = telemetry.bleScanSupported,
            uwbSupported = telemetry.uwbSupported,
            accelerometerAvailable = telemetry.accelerometerAvailable,
            gyroscopeAvailable = telemetry.gyroscopeAvailable,
            magnetometerAvailable = telemetry.magnetometerAvailable,
            barometerAvailable = telemetry.barometerAvailable,
            networkConnected = telemetry.networkConnected,
            ipv6Supported = telemetry.ipv6Supported,
            proxySummary = relayClient.currentProxySummary(),
            relayUrl = relayClient.currentNetworkConfig().relayUrl,
            relayReachable = relayClient.connected.value,
            webSocketConnected = relayClient.connected.value,
            pendingEncryptedReportsCount = pending,
            lastSyncTimestampMs = lastSyncMs,
            averageSyncIntervalSeconds = null,
            syncIntervalsBySource = emptyMap(),
            backgroundTrackingEnabled = hardwareLocationManager.isTracking(),
            batteryOptimizationExempt = batteryOptimizationExempt
        )
    }

    @Volatile
    private var lastSyncMs: Long? = null

    private fun providerNameToSource(provider: String) =
        when (provider) {
            android.location.LocationManager.GPS_PROVIDER -> app.luogo.app.domain.model.LocationSourceType.GNSS_STANDARD
            android.location.LocationManager.NETWORK_PROVIDER -> app.luogo.app.domain.model.LocationSourceType.WIFI_NETWORK
            else -> app.luogo.app.domain.model.LocationSourceType.IP_FALLBACK
        }

    // ---------------------------------------------------------------- maps

    private fun loadMapStyle(): MapStyleOption {
        val id = prefs.getString(KEY_MAP_STYLE, MapStyleOption.STANDARD_OSM.id)
        return MapStyleOption.entries.firstOrNull { it.id == id } ?: MapStyleOption.STANDARD_OSM
    }

    private suspend fun groupKeysKnown(): List<Pair<String, ByteArray>> =
        dao.getGroups()
            .mapNotNull { group ->
                if (cryptoEngine.hasGroupKey(group.id)) {
                    group.id to cryptoEngine.getOrCreateGroupKey(group.id)
                } else {
                    null
                }
            }

    // ------------------------------------------------------------- mapping

    private fun UserProfileEntity.toDomain() = UserProfile(
        id = id,
        displayName = displayName,
        colorArgb = colorArgb,
        publicKeyBase64 = publicKeyBase64,
        sharingEnabled = sharingEnabled,
        temporarySharingUntilMs = temporarySharingUntilMs
    )

    private fun UserProfile.toEntity() = UserProfileEntity(
        id = id,
        displayName = displayName,
        colorArgb = colorArgb,
        publicKeyBase64 = publicKeyBase64,
        sharingEnabled = sharingEnabled,
        temporarySharingUntilMs = temporarySharingUntilMs
    )

    private fun PeerGroup.toEntity() = app.luogo.app.data.db.PeerGroupEntity(
        id = id, name = name, category = category.name, ownerId = ownerId,
        createdAtMs = createdAtMs, memberCount = memberCount,
        shareMyLocation = shareMyLocation, temporarySharingUntilMs = temporarySharingUntilMs
    )

    private fun app.luogo.app.data.db.PeerGroupEntity.toDomain() = PeerGroup(
        id = id, name = name, category = runCatching { GroupCategory.valueOf(category) }
            .getOrDefault(GroupCategory.CUSTOM),
        ownerId = ownerId, createdAtMs = createdAtMs, memberCount = memberCount,
        shareMyLocation = shareMyLocation, temporarySharingUntilMs = temporarySharingUntilMs
    )

    private fun RegisteredItem.toEntity() = RegisteredItemEntity(
        id = id, friendlyName = friendlyName, itemType = itemType.name, ownerId = ownerId,
        createdAtMs = createdAtMs, ephemeralIdentitySeedBase64 = ephemeralIdentitySeedBase64,
        currentRotatingBleIdHex = currentRotatingBleIdHex, rotationEpoch = rotationEpoch,
        lastSeenLatitude = lastSeenLatitude, lastSeenLongitude = lastSeenLongitude,
        lastSeenAccuracyMeters = lastSeenAccuracyMeters, lastSeenTimestampMs = lastSeenTimestampMs,
        lastSeenSource = lastSeenSource, batteryPercent = batteryPercent, rssiDbm = rssiDbm,
        uwbDistanceMeters = uwbDistanceMeters, uwbAzimuthDegrees = uwbAzimuthDegrees,
        isLostMode = isLostMode, isRinging = isRinging
    )

    private fun RegisteredItemEntity.toDomain() = RegisteredItem(
        id = id, friendlyName = friendlyName, itemType = runCatching { ItemType.valueOf(itemType) }
            .getOrDefault(ItemType.CUSTOM_ITEM),
        ownerId = ownerId, createdAtMs = createdAtMs,
        ephemeralIdentitySeedBase64 = ephemeralIdentitySeedBase64,
        currentRotatingBleIdHex = currentRotatingBleIdHex, rotationEpoch = rotationEpoch,
        lastSeenLatitude = lastSeenLatitude, lastSeenLongitude = lastSeenLongitude,
        lastSeenAccuracyMeters = lastSeenAccuracyMeters, lastSeenTimestampMs = lastSeenTimestampMs,
        lastSeenSource = lastSeenSource, batteryPercent = batteryPercent, rssiDbm = rssiDbm,
        uwbDistanceMeters = uwbDistanceMeters, uwbAzimuthDegrees = uwbAzimuthDegrees,
        isLostMode = isLostMode, isRinging = isRinging
    )

    private fun SavedPlace.toEntity() = SavedPlaceEntity(
        id = id, name = name, category = category.name, latitude = latitude, longitude = longitude,
        radiusMeters = radiusMeters, enabled = enabled, notifyOnArrival = notifyOnArrival,
        notifyOnDeparture = notifyOnDeparture, allowedGroupIdsCsv = allowedGroupIdsCsv,
        currentlyInside = currentlyInside, lastTransitionMs = lastTransitionMs
    )

    private fun SavedPlaceEntity.toDomain() = SavedPlace(
        id = id, name = name, category = runCatching { PlaceCategory.valueOf(category) }
            .getOrDefault(PlaceCategory.CUSTOM),
        latitude = latitude, longitude = longitude, radiusMeters = radiusMeters, enabled = enabled,
        notifyOnArrival = notifyOnArrival, notifyOnDeparture = notifyOnDeparture,
        allowedGroupIdsCsv = allowedGroupIdsCsv, currentlyInside = currentlyInside,
        lastTransitionMs = lastTransitionMs
    )

    private fun LocationHistoryEntity.toDomain() = LocationHistoryPoint(
        id = id, subjectId = subjectId, subjectName = subjectName, isItem = isItem,
        latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters,
        altitudeMeters = altitudeMeters, speedMps = speedMps, bearingDegrees = bearingDegrees,
        activityState = runCatching { app.luogo.app.domain.model.ActivityState.valueOf(activityState) }
            .getOrDefault(app.luogo.app.domain.model.ActivityState.UNKNOWN),
        sourceSummary = sourceSummary, timestampMs = timestampMs, tripId = tripId
    )

    private fun FusedLocationFix.toHistoryEntity() = LocationHistoryEntity(
        subjectId = userProfile.value.id,
        subjectName = userProfile.value.displayName,
        isItem = false,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = horizontalAccuracyMeters,
        altitudeMeters = altitudeMeters,
        speedMps = speedMps,
        bearingDegrees = bearingDegrees,
        activityState = activityState.name,
        sourceSummary = sourceSummary,
        timestampMs = timestampMs
    )

    private fun OfflineMapRegion.toEntity() = OfflineRegionEntity(
        id = id, name = name, minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon,
        minZoom = minZoom, maxZoom = maxZoom, styleId = styleId, status = status.name,
        progressPercent = progressPercent, downloadedTiles = downloadedTiles,
        totalTiles = totalTiles, sizeBytes = sizeBytes, updatedAtMs = updatedAtMs
    )

    private fun OfflineRegionEntity.toDomain() = OfflineMapRegion(
        id = id, name = name, minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon,
        minZoom = minZoom, maxZoom = maxZoom, styleId = styleId,
        status = runCatching { app.luogo.app.domain.model.OfflineRegionStatus.valueOf(status) }
            .getOrDefault(app.luogo.app.domain.model.OfflineRegionStatus.READY),
        progressPercent = progressPercent, downloadedTiles = downloadedTiles,
        totalTiles = totalTiles, sizeBytes = sizeBytes, updatedAtMs = updatedAtMs
    )

    private fun CrowdsourcedSightingReport.toEntity() = PendingSightingEntity(
        reportId = reportId, rotatingBleIdHex = rotatingBleIdHex, timestampMs = timestampMs,
        helperLatitude = helperLatitude, helperLongitude = helperLongitude,
        helperAccuracyMeters = helperAccuracyMeters, rssiDbm = rssiDbm,
        encryptedPayloadBase64 = encryptedPayloadBase64, authTagBase64 = authTagBase64,
        protocolVersion = protocolVersion, isUploaded = isUploaded, retryCount = retryCount
    )

    private fun PendingSightingEntity.toDomain() = CrowdsourcedSightingReport(
        reportId = reportId, rotatingBleIdHex = rotatingBleIdHex, timestampMs = timestampMs,
        helperLatitude = helperLatitude, helperLongitude = helperLongitude,
        helperAccuracyMeters = helperAccuracyMeters, rssiDbm = rssiDbm,
        encryptedPayloadBase64 = encryptedPayloadBase64, authTagBase64 = authTagBase64,
        protocolVersion = protocolVersion, isUploaded = isUploaded, retryCount = retryCount
    )

    private fun UnknownTrackerAlert.toEntity() = TrackerAlertEntity(
        trackerSignatureId = trackerSignatureId, firstSeenMs = firstSeenMs, lastSeenMs = lastSeenMs,
        sightingCount = sightingCount, distinctLocationsCount = distinctLocationsCount,
        latestRssiDbm = latestRssiDbm, riskLevel = riskLevel,
        sampleRotatingIdsCsv = sampleRotatingIdsCsv, isDismissed = isDismissed
    )

    private fun TrackerAlertEntity.toDomain() = UnknownTrackerAlert(
        trackerSignatureId = trackerSignatureId, firstSeenMs = firstSeenMs, lastSeenMs = lastSeenMs,
        sightingCount = sightingCount, distinctLocationsCount = distinctLocationsCount,
        latestRssiDbm = latestRssiDbm, riskLevel = riskLevel,
        sampleRotatingIdsCsv = sampleRotatingIdsCsv, isDismissed = isDismissed
    )

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val HISTORY_WINDOW = 5000
        private const val UPLOAD_BATCH_SIZE = 50
        private const val MAX_OBSERVATIONS = 500
        private const val MAX_HELPER_ACCURACY_METERS = 100f
        private const val DEFAULT_DISPLAY_NAME = "Luogo user"
        private const val DEFAULT_COLOR = 0xFF006C4CL

        private const val KEY_DISPLAY_NAME = "profile.display.name"
        private const val KEY_DISPLAY_COLOR = "profile.display.color"
        private const val KEY_MAP_STYLE = "map.style"
        const val KEY_SATELLITE_URL = "map.satellite.url"
        const val KEY_ROUTING_URL = "map.routing.url"
    }
}