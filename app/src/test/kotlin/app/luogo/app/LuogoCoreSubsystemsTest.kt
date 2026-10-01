package app.luogo.app

import app.luogo.app.data.ble.BleFindingProtocol
import app.luogo.app.data.crypto.CryptoEngine
import app.luogo.app.data.geofence.GeofenceAndHistoryEngine
import app.luogo.app.data.location.LocationFusionEngine
import app.luogo.app.data.location.SmoothMarkerInterpolator
import app.luogo.app.data.network.KotlinRelayServerEngine
import app.luogo.app.domain.model.ActivityState
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.ItemType
import app.luogo.app.domain.model.LocationHistoryPoint
import app.luogo.app.domain.model.LocationQualityLevel
import app.luogo.app.domain.model.LocationSourceType
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.SavedPlace
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuogoCoreSubsystemsTest {

    @Test
    fun `location fusion combines multi-source fixes and targets 2s moving interval`() {
        val engine = LocationFusionEngine()
        val now = 1_700_000_000_000L

        val wifiFix = LocationFusionEngine.RawPositionMeasurement(
            latitude = 37.7749,
            longitude = -122.4194,
            timestampMs = now,
            horizontalAccuracyMeters = 24.0f,
            sourceType = LocationSourceType.WIFI_NETWORK
        )
        val gnssDualFix = LocationFusionEngine.RawPositionMeasurement(
            latitude = 37.7750,
            longitude = -122.4195,
            timestampMs = now + 1_900L,
            horizontalAccuracyMeters = 4.2f,
            speedMps = 1.6f,
            bearingDegrees = 45f,
            sourceType = LocationSourceType.GNSS_DUAL_FREQ
        )

        engine.ingestMeasurement(wifiFix)
        val fused = engine.ingestMeasurement(gnssDualFix)

        assertTrue(fused.horizontalAccuracyMeters <= 5.0f)
        assertTrue(fused.activeSources.contains(LocationSourceType.GNSS_DUAL_FREQ))
        assertTrue(fused.activeSources.contains(LocationSourceType.WIFI_NETWORK))
        assertEquals(ActivityState.WALKING, fused.activityState)
        assertEquals(2_000L, fused.activityState.targetUpdateIntervalMs)
        assertEquals(LocationQualityLevel.HIGH, fused.qualityLevel)
    }

    @Test
    fun `impossible jump is detected and does not manufacture false precision`() {
        val engine = LocationFusionEngine()
        val now = 1_700_000_000_000L

        engine.ingestMeasurement(
            LocationFusionEngine.RawPositionMeasurement(
                latitude = 37.7749,
                longitude = -122.4194,
                timestampMs = now,
                horizontalAccuracyMeters = 6.0f,
                sourceType = LocationSourceType.GNSS_STANDARD
            )
        )

        // 50 km jump in 1 second -> impossible ground speed
        val jumpFix = engine.ingestMeasurement(
            LocationFusionEngine.RawPositionMeasurement(
                latitude = 38.2500,
                longitude = -122.4194,
                timestampMs = now + 1_000L,
                horizontalAccuracyMeters = 8.0f,
                sourceType = LocationSourceType.CELLULAR
            )
        )

        assertTrue(jumpFix.isSuspiciousJump)
        assertEquals(LocationQualityLevel.SUSPICIOUS, jumpFix.qualityLevel)
        assertTrue(jumpFix.confidence < 0.5f)
    }

    @Test
    fun `dead reckoning accounts for accumulated drift and inflates uncertainty`() {
        val engine = LocationFusionEngine()
        val now = 1_700_000_000_000L
        val base = engine.ingestMeasurement(
            LocationFusionEngine.RawPositionMeasurement(
                latitude = 37.7749,
                longitude = -122.4194,
                timestampMs = now,
                horizontalAccuracyMeters = 5.0f,
                sourceType = LocationSourceType.GNSS_STANDARD
            )
        )

        val dr = engine.propagateDeadReckoning(
            nowMs = now + 4_000L,
            stepStrideMeters = 3.0,
            headingDegrees = 90f
        )
        assertNotNull(dr)
        assertTrue(dr!!.horizontalAccuracyMeters > base.horizontalAccuracyMeters)
        assertTrue(dr.activeSources.contains(LocationSourceType.INERTIAL_DEAD_RECKONING))
    }

    @Test
    fun `activity state transitions across stationary, walking, running, cycling, and driving`() {
        val engine = LocationFusionEngine()
        assertEquals(ActivityState.STATIONARY, engine.classifyActivity(speedMps = 0.1f, accelVariance = 0.05f, stepRateHz = 0f))
        assertEquals(ActivityState.WALKING, engine.classifyActivity(speedMps = 1.4f, accelVariance = 1.0f, stepRateHz = 1.6f))
        assertEquals(ActivityState.RUNNING, engine.classifyActivity(speedMps = 3.4f, accelVariance = 2.8f, stepRateHz = 2.7f))
        assertEquals(ActivityState.CYCLING, engine.classifyActivity(speedMps = 6.2f, accelVariance = 0.8f, stepRateHz = 0f))
        assertEquals(ActivityState.DRIVING, engine.classifyActivity(speedMps = 18.5f, accelVariance = 0.4f, stepRateHz = 0f))
    }

    @Test
    fun `stale location detection distinguishes live, recently seen, and stale states`() {
        val now = 1_700_000_000_000L
        val fix = FusedLocationFix(
            latitude = 37.7749,
            longitude = -122.4194,
            timestampMs = now - 90_000L,
            horizontalAccuracyMeters = 10f
        )
        assertTrue(fix.isStale(now, thresholdMs = 60_000L))

        val item = RegisteredItem(
            id = "item-1",
            friendlyName = "Ansh's Pixel Buds 3",
            itemType = ItemType.EARBUDS,
            ownerId = "owner-1",
            createdAtMs = now - 3_600_000L,
            ephemeralIdentitySeedBase64 = "seed",
            currentRotatingBleIdHex = "00112233",
            rotationEpoch = 100L,
            lastSeenTimestampMs = now - 5_000L
        )
        assertEquals(ItemPresenceStatus.LIVE, item.presenceStatus(now))
        assertEquals(ItemPresenceStatus.RECENTLY_SEEN, item.copy(lastSeenTimestampMs = now - 120_000L).presenceStatus(now))
        assertEquals(ItemPresenceStatus.STALE, item.copy(lastSeenTimestampMs = now - 3_600_000L).presenceStatus(now))
        assertEquals(ItemPresenceStatus.UNKNOWN, item.copy(lastSeenTimestampMs = null).presenceStatus(now))
    }

    @Test
    fun `encryption engine round-trips group AEAD, Ed25519 signatures, and invite wire format`() {
        val crypto = CryptoEngine()
        val identity = crypto.loadOrCreateDeviceIdentity()
        val msg = "luogo-authenticated-payload".toByteArray(Charsets.UTF_8)
        val sig = crypto.signDetached(msg, identity.privateKeySeed)
        assertTrue(crypto.verifyDetached(msg, sig, identity.publicKeyBytes))
        assertFalse(crypto.verifyDetached("tampered".toByteArray(), sig, identity.publicKeyBytes))

        val groupId = "grp-test"
        val groupKey = crypto.getOrCreateGroupKey(groupId)
        val cleartext = """{"coordinates":[37.7749,-122.4194],"name":"Ansh"}""".toByteArray(Charsets.UTF_8)
        val ciphertext = crypto.encryptForGroup(groupId, cleartext)
        val decrypted = crypto.decryptForGroup(groupId, ciphertext)
        assertNotNull(decrypted)
        assertArrayEquals(cleartext, decrypted)

        // Wrong key must fail authentication
        val wrongKey = crypto.generateKey()
        assertNull(crypto.decryptWithKey(wrongKey, ciphertext))

        // Upstream invite wire format compatibility
        val inviteStr = crypto.buildInvitePayload(groupId, "tok-123", groupKey)
        assertTrue(inviteStr.startsWith("luogo-invite-key: v1:grp-test:tok-123:"))
        val parsed = crypto.parseInvitePayload(inviteStr)
        assertNotNull(parsed)
        assertEquals(groupId, parsed!!.groupId)
        assertEquals("tok-123", parsed.inviteToken)
        assertArrayEquals(groupKey, parsed.groupKey)
    }

    @Test
    fun `BLE protocol rotates identifiers every 15m, encrypts sightings, and blocks replays`() {
        val crypto = CryptoEngine()
        val protocol = BleFindingProtocol(crypto)
        val seedB64 = CryptoEngine.encodeBase64Url(crypto.generateKey())

        val t0 = 1_700_000_000_000L
        val epoch0 = protocol.rotationEpochForTimestamp(t0)
        val epoch1 = protocol.rotationEpochForTimestamp(t0 + BleFindingProtocol.ROTATION_INTERVAL_MS)

        val eid0 = protocol.deriveRotatingBleIdHex(seedB64, epoch0)
        val eid1 = protocol.deriveRotatingBleIdHex(seedB64, epoch1)
        assertEquals(32, eid0.length)
        assertNotEquals(eid0, eid1)

        val helperFix = FusedLocationFix(
            latitude = 37.7755,
            longitude = -122.4180,
            timestampMs = t0,
            horizontalAccuracyMeters = 7.5f,
            sourceSummary = "GNSS (L1+L5)"
        )
        val report = protocol.createEncryptedSightingReport(
            rotatingBleIdHex = eid0,
            ephemeralSeedOrPublicKeyBase64 = seedB64,
            helperLocation = helperFix,
            rssiDbm = -62,
            nowMs = t0
        )

        val decrypted = protocol.verifyAndDecryptSightingReport(report, seedB64, nowMs = t0 + 1_000L)
        assertNotNull(decrypted)
        assertEquals(37.7755, decrypted!!.latitude, 1e-5)
        assertEquals(-62, decrypted.rssiDbm)

        // Replay of the same reportId must be rejected
        val replayAttempt = protocol.verifyAndDecryptSightingReport(report, seedB64, nowMs = t0 + 2_000L)
        assertNull(replayAttempt)

        // Out-of-window timestamp skew must be rejected
        val freshReport = protocol.createEncryptedSightingReport(eid0, seedB64, helperFix, -60, t0)
        val staleAttempt = protocol.verifyAndDecryptSightingReport(
            freshReport,
            seedB64,
            nowMs = t0 + BleFindingProtocol.MAX_REPORT_CLOCK_SKEW_MS + 60_000L
        )
        assertNull(staleAttempt)
    }

    @Test
    fun `unknown tracker abuse protection flags unowned tracker moving across distinct locations`() {
        val crypto = CryptoEngine()
        val protocol = BleFindingProtocol(crypto)
        val now = 1_700_000_000_000L

        val sightings = listOf(
            BleFindingProtocol.ObservedBleSighting("unk-tag-99", "aa01", now, 37.7749, -122.4194, -58),
            BleFindingProtocol.ObservedBleSighting("unk-tag-99", "aa01", now + 300_000L, 37.7810, -122.4194, -60),
            BleFindingProtocol.ObservedBleSighting("unk-tag-99", "aa02", now + 600_000L, 37.7890, -122.4194, -55)
        )

        val alerts = protocol.evaluateUnknownTrackerRisk(sightings, ownedRotatingIds = emptySet())
        assertEquals(1, alerts.size)
        assertEquals("unk-tag-99", alerts.first().trackerSignatureId)
        assertEquals(3, alerts.first().distinctLocationsCount)
    }

    @Test
    fun `geofencing triggers arrival and departure with hysteresis and history exports valid GPX`() {
        val engine = GeofenceAndHistoryEngine()
        val home = SavedPlace(
            id = "place-home",
            name = "Home",
            category = PlaceCategory.HOME,
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 100f,
            enabled = true,
            notifyOnArrival = true,
            notifyOnDeparture = true,
            currentlyInside = false
        )

        // Move inside geofence -> ARRIVED
        val (placesAfterEnter, enterEvents) = engine.evaluateGeofences(
            places = listOf(home),
            subjectName = "Ansh",
            latitude = 37.7749,
            longitude = -122.4194,
            accuracyMeters = 6f
        )
        assertEquals(1, enterEvents.size)
        assertEquals(GeofenceAndHistoryEngine.GeofenceTransitionType.ARRIVED, enterEvents.first().transitionType)
        assertTrue(placesAfterEnter.first().currentlyInside)

        // Move far outside geofence -> DEPARTED
        val (placesAfterExit, exitEvents) = engine.evaluateGeofences(
            places = placesAfterEnter,
            subjectName = "Ansh",
            latitude = 37.7850,
            longitude = -122.4194,
            accuracyMeters = 6f
        )
        assertEquals(1, exitEvents.size)
        assertEquals(GeofenceAndHistoryEngine.GeofenceTransitionType.DEPARTED, exitEvents.first().transitionType)
        assertFalse(placesAfterExit.first().currentlyInside)

        // History distance & GPX export
        val pts = listOf(
            LocationHistoryPoint(1, "me", "Ansh", false, 37.7749, -122.4194, 5f, 10.0, 1.5f, 0f, ActivityState.WALKING, "GNSS", 1000L, "t1"),
            LocationHistoryPoint(2, "me", "Ansh", false, 37.7769, -122.4194, 5f, 12.0, 1.5f, 0f, ActivityState.WALKING, "GNSS", 61000L, "t1")
        )
        val dist = engine.calculateTotalDistanceMeters(pts)
        assertTrue(dist > 200.0)
        val gpx = engine.exportToGpx(pts)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"37.7749\""))

        // Smooth marker interpolator
        val interp = SmoothMarkerInterpolator()
        interp.onNewTrustedFix("me", 37.7749, -122.4194, 0f, 6f, 1.5f, nowMs = 1000L)
        interp.onNewTrustedFix("me", 37.7759, -122.4194, 90f, 6f, 1.5f, nowMs = 2000L)
        val midPose = interp.sample("me", nowMs = 2900L)
        assertNotNull(midPose)
        assertTrue(midPose!!.latitude > 37.7749 && midPose.latitude <= 37.7759)
    }

    @Test
    fun `kotlin relay server engine handles registration, single-use invites, bounded E2EE log, and live fan-out`() {
        val relay = KotlinRelayServerEngine(maxLogPerGroup = 3)
        val (alice, aliceToken) = relay.createUser("Alice", 0xFF006C4CL, "pub-alice")
        val (bob, bobToken) = relay.createUser("Bob", 0xFF0284C7L, "pub-bob")

        assertEquals(alice.id, relay.authenticateToken("Bearer $aliceToken")?.id)
        assertEquals(bob.id, relay.authenticateToken(bobToken)?.id)
        assertNull(relay.authenticateToken("invalid-token"))

        val group = relay.createGroup(alice.id, "Family Circle")
        val invite = relay.createInvite(group.id, alice.id)
        assertNotNull(invite)

        val bobEvents = mutableListOf<String>()
        val wsSession = relay.connectWebSocket(bob.id) { payload -> bobEvents.add(payload) }

        // Bob joins with single-use invite token
        val joined = relay.joinGroupWithInvite(group.id, bob.id, invite!!.token)
        assertNotNull(joined)
        // Second use of the same invite token must be rejected
        assertNull(relay.joinGroupWithInvite(group.id, bob.id, invite.token))

        // Alice publishes 4 E2EE messages; maxLogPerGroup=3 prunes seq 1
        relay.appendMessage(group.id, alice.id, "cipher-1")
        relay.appendMessage(group.id, alice.id, "cipher-2")
        relay.appendMessage(group.id, alice.id, "cipher-3")
        relay.appendMessage(group.id, alice.id, "cipher-4")

        val resync = relay.getMessagesAfter(group.id, bob.id, afterSeq = 0L)
        assertNotNull(resync)
        assertEquals(3, resync!!.size)
        assertEquals("cipher-2", resync.first().ciphertext)
        assertEquals("cipher-4", resync.last().ciphertext)
        assertTrue(bobEvents.any { it.contains("\"type\":\"message\"") && it.contains("cipher-4") })

        wsSession.close()
    }
}
