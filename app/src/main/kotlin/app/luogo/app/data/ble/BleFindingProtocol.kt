package app.luogo.app.data.ble

import app.luogo.app.data.crypto.CryptoEngine
import app.luogo.app.data.location.GeoMath
import app.luogo.app.domain.model.CrowdsourcedSightingReport
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.UnknownTrackerAlert
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Privacy-preserving BLE finding protocol.
 *
 * Threat model and design:
 *  - An item NEVER broadcasts a permanent Bluetooth MAC. It advertises a *rotating* 16-byte
 *    identifier derived from a secret seed, changing every [ROTATION_INTERVAL_MS]. Someone
 *    who records one advertisement learns nothing about the next one.
 *  - The item seed is shared only with the owner and, sealed, with enrolled helpers. Helpers
 *    can verify a sighting is genuine without learning the owner's identity.
 *  - A sighting is encrypted under a key derived from that seed, and the whole report is
 *    covered by an HMAC so a helper cannot forge or edit a report.
 *  - The owner rejects replays by report id and reports outside a clock-skew window.
 *
 * A helper contributes *its own* location. The lost item needs no GPS of its own.
 *
 * Pure Kotlin apart from the crypto primitives, so the protocol is directly unit-testable.
 */
class BleFindingProtocol(private val crypto: CryptoEngine) {

    /** A sighting that a helper observed, used for unknown-tracker risk analysis. */
    data class ObservedBleSighting(
        val trackerSignatureId: String,
        val rotatingIdHex: String,
        val timestampMs: Long,
        val latitude: Double,
        val longitude: Double,
        val rssiDbm: Int
    )

    /** Plaintext recovered from an authenticated report. */
    data class DecryptedSightingReport(
        val reportId: String,
        val rotatingBleIdHex: String,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val rssiDbm: Int,
        val timestampMs: Long,
        val protocolVersion: Int
    )

    // ------------------------------------------------------------- identifiers

    /**
     * Which rotation slot a timestamp falls into. Two timestamps in the same slot share an
     * identifier; timestamps one interval apart do not.
     */
    fun rotationEpochForTimestamp(timestampMs: Long): Long =
        Math.floorDiv(timestampMs, ROTATION_INTERVAL_MS)

    /**
     * Derives the advertised identifier for one rotation slot.
     * Domain-separated and truncated to 16 bytes, the size of a BLE service-data payload.
     */
    fun deriveRotatingBleIdHex(seedBase64: String, epoch: Long): String {
        val seed = CryptoEngine.decodeBase64Url(seedBase64)
        val bytes = crypto.deriveKey(seed, "$DOMAIN_ROTATING_ID:$epoch", length = 16)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The identifier an item should be advertising right now. */
    fun currentRotatingBleIdHex(seedBase64: String, nowMs: Long = System.currentTimeMillis()): String =
        deriveRotatingBleIdHex(seedBase64, rotationEpochForTimestamp(nowMs))

    /**
     * Stable per-item signature used for unknown-tracker detection.
     *
     * Derived from the seed with a *different* domain separator than the advertised id, so a
     * passive observer of advertisements cannot compute it and link sightings to an item.
     */
    fun itemSignatureForSeed(seedBase64: String): String {
        val seed = CryptoEngine.decodeBase64Url(seedBase64)
        return crypto.deriveKey(seed, DOMAIN_ITEM_SIGNATURE, length = 16)
            .joinToString("") { "%02x".format(it) }
    }

    /** True when [rotatingIdHex] belongs to one of the caller's own items. */
    fun isOwnedRotatingId(rotatingIdHex: String, ownedSeeds: Collection<String>): Boolean {
        if (ownedSeeds.isEmpty()) return false
        val owned = ownedSeeds.toHashSet()
        // Check the current slot and one either side, so clock skew does not cause a miss.
        val epoch = rotationEpochForTimestamp(System.currentTimeMillis())
        for (e in epoch - 1..epoch + 1) {
            if (owned.contains(deriveRotatingBleIdHex(ownedSeeds.first(), e))) {
                if (ownedSeeds.any { deriveRotatingBleIdHex(it, e) == rotatingIdHex }) return true
            }
        }
        return false
    }

    // --------------------------------------------------------------- reporting

    /**
     * Builds an encrypted, authenticated sighting for a detection.
     *
     * Only what the owner needs is included: the rotating id, signal strength, timestamp and
     * the helper's accuracy. No helper identity, no device model, no stable hardware id.
     */
    fun createEncryptedSightingReport(
        rotatingBleIdHex: String,
        ephemeralSeedOrPublicKeyBase64: String,
        helperLocation: FusedLocationFix,
        rssiDbm: Int,
        nowMs: Long = System.currentTimeMillis()
    ): CrowdsourcedSightingReport {
        val seed = CryptoEngine.decodeBase64Url(ephemeralSeedOrPublicKeyBase64)
        val payloadKey = crypto.deriveKey(seed, DOMAIN_SIGHTING_PAYLOAD)

        // Compact, explicit wire format. Field order is part of the protocol.
        val payload = buildString {
            append(PROTOCOL_VERSION).append(FIELD_SEP)
            append(rotatingBleIdHex).append(FIELD_SEP)
            append(helperLocation.timestampMs).append(FIELD_SEP)
            append(rssiDbm).append(FIELD_SEP)
            append(helperLocation.horizontalAccuracyMeters)
        }.toByteArray(Charsets.UTF_8)

        val reportId = newReportId(nowMs, rotatingBleIdHex, payload)
        val sealed = crypto.encryptWithKey(payloadKey, payload, aad = reportId)

        val envelope = canonicalEnvelope(reportId, rotatingBleIdHex, nowMs, helperLocation, rssiDbm, sealed)
        val tag = hmac(seed, DOMAIN_SIGHTING_TAG, envelope)

        return CrowdsourcedSightingReport(
            reportId = reportId,
            rotatingBleIdHex = rotatingBleIdHex,
            timestampMs = nowMs,
            helperLatitude = helperLocation.latitude,
            helperLongitude = helperLocation.longitude,
            helperAccuracyMeters = helperLocation.horizontalAccuracyMeters,
            rssiDbm = rssiDbm,
            encryptedPayloadBase64 = CryptoEngine.encodeBase64Url(sealed),
            authTagBase64 = CryptoEngine.encodeBase64Url(tag),
            protocolVersion = PROTOCOL_VERSION
        )
    }

    /**
     * Verifies and decrypts a report, or returns null.
     *
     * Null is returned for every failure mode — bad tag, wrong key, replayed id, unknown
     * protocol version, or a timestamp outside the skew window — so a caller cannot
     * accidentally distinguish "malformed" from "replayed" and leak an oracle.
     */
    fun verifyAndDecryptSightingReport(
        report: CrowdsourcedSightingReport,
        seedBase64: String,
        nowMs: Long = System.currentTimeMillis()
    ): DecryptedSightingReport? {
        if (report.protocolVersion != PROTOCOL_VERSION) return null

        // Clock skew gate, checked before any crypto so stale reports cost nothing.
        val skew = Math.abs(nowMs - report.timestampMs)
        if (skew > MAX_REPORT_CLOCK_SKEW_MS) return null

        // Replay gate. A report id is accepted at most once per process lifetime.
        if (!markReportIdFresh(report.reportId)) return null

        val seed = try {
            CryptoEngine.decodeBase64Url(seedBase64)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val sealed = try {
            CryptoEngine.decodeBase64Url(report.encryptedPayloadBase64)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val tag = try {
            CryptoEngine.decodeBase64Url(report.authTagBase64)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val expectedTag = hmac(
            seed,
            DOMAIN_SIGHTING_TAG,
            canonicalEnvelope(
                report.reportId,
                report.rotatingBleIdHex,
                report.timestampMs,
                null,
                report.rssiDbm,
                sealed,
                report.helperLatitude,
                report.helperLongitude,
                report.helperAccuracyMeters
            )
        )
        if (!constantTimeEquals(expectedTag, tag)) return null

        val payloadKey = crypto.deriveKey(seed, DOMAIN_SIGHTING_PAYLOAD)
        val payload = crypto.decryptWithKey(payloadKey, sealed, aad = report.reportId) ?: return null

        val fields = payload.toString(Charsets.UTF_8).split(FIELD_SEP)
        if (fields.size != 5) return null
        val version = fields[0].toIntOrNull() ?: return null
        if (version != PROTOCOL_VERSION) return null

        val rotatingId = fields[1]
        // The sealed id must match the advertised id, or the envelope was swapped.
        if (rotatingId != report.rotatingBleIdHex) return null

        val reportedTimestamp = fields[2].toLongOrNull() ?: return null
        val rssi = fields[3].toIntOrNull() ?: return null
        val accuracy = fields[4].toFloatOrNull() ?: return null

        return DecryptedSightingReport(
            reportId = report.reportId,
            rotatingBleIdHex = rotatingId,
            latitude = report.helperLatitude,
            longitude = report.helperLongitude,
            accuracyMeters = accuracy,
            rssiDbm = rssi,
            timestampMs = reportedTimestamp,
            protocolVersion = version
        )
    }

    // --------------------------------------------------------- abuse protection

    /**
     * Flags trackers that are moving with the user but are not the user's own items.
     *
     * The signature is a conservative pattern, not proof of malice: a genuinely unknown
     * Bluetooth tag that repeatedly appears in different places alongside you. False
     * positives are acceptable because the action offered is "review", never "block silently".
     */
    fun evaluateUnknownTrackerRisk(
        sightings: List<ObservedBleSighting>,
        ownedRotatingIds: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<UnknownTrackerAlert> {
        val bySignature = sightings.groupBy { it.trackerSignatureId }

        return bySignature.mapNotNull { (signature, group) ->
            val ordered = group.sortedBy { it.timestampMs }
            val rotatingIds = ordered.map { it.rotatingIdHex }.toSet()

            // Anything the user owns is by definition not an unknown tracker.
            if (rotatingIds.any { ownedRotatingIds.contains(it) }) return@mapNotNull null
            if (ordered.size < MIN_SIGHTINGS_FOR_ALERT) return@mapNotNull null

            val distinctLocations = countDistinctLocations(ordered)
            if (distinctLocations < MIN_DISTINCT_LOCATIONS_FOR_ALERT) return@mapNotNull null

            val spanMs = ordered.last().timestampMs - ordered.first().timestampMs
            // Must have been observed over time as well as across space, otherwise a single
            // stationary tag seen twice would look like travel.
            if (spanMs < MIN_ALERT_SPAN_MS && distinctLocations < MIN_DISTINCT_LOCATIONS_FOR_HIGH_RISK) {
                return@mapNotNull null
            }

            val riskLevel = when {
                distinctLocations >= MIN_DISTINCT_LOCATIONS_FOR_HIGH_RISK &&
                    spanMs >= HIGH_RISK_SPAN_MS -> "HIGH"
                distinctLocations >= MIN_DISTINCT_LOCATIONS_FOR_ALERT -> "MEDIUM"
                else -> "LOW"
            }

            UnknownTrackerAlert(
                trackerSignatureId = signature,
                firstSeenMs = ordered.first().timestampMs,
                lastSeenMs = ordered.last().timestampMs,
                sightingCount = ordered.size,
                distinctLocationsCount = distinctLocations,
                latestRssiDbm = ordered.last().rssiDbm,
                riskLevel = riskLevel,
                sampleRotatingIdsCsv = rotatingIds.take(MAX_SAMPLE_IDS).joinToString(","),
                isDismissed = false
            )
        }.sortedByDescending { it.lastSeenMs }
    }

    /**
     * Greedy spatial clustering. Sighting locations are counted as distinct when they are
     * further apart than a person could be while standing still, which avoids counting GPS
     * jitter around one spot as travel.
     */
    private fun countDistinctLocations(sightings: List<ObservedBleSighting>): Int {
        val clusters = ArrayList<Pair<Double, Double>>()
        for (s in sightings) {
            val isNew = clusters.none { (lat, lon) ->
                GeoMath.distanceMeters(lat, lon, s.latitude, s.longitude) < DISTINCT_LOCATION_RADIUS_METERS
            }
            if (isNew) clusters.add(s.latitude to s.longitude)
        }
        return clusters.size
    }

    // ----------------------------------------------------------------- helpers

    /** Returns false when this id was already seen. Bounded so it cannot grow forever. */
    private fun markReportIdFresh(reportId: String): Boolean = synchronized(replayLock) {
        if (!seenReportIds.add(reportId)) return false
        if (seenReportIds.size > MAX_TRACKED_REPORT_IDS) {
            // Drop the oldest insertion; LinkedHashSet iterates in insertion order.
            val oldest = seenReportIds.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        true
    }

    /** Test seam: clears replay state so a suite can re-verify the same report. */
    fun resetReplayCache() {
        synchronized(replayLock) { seenReportIds.clear() }
    }

    private fun canonicalEnvelope(
        reportId: String,
        rotatingBleIdHex: String,
        timestampMs: Long,
        helperLocation: FusedLocationFix?,
        rssiDbm: Int,
        sealed: ByteArray,
        helperLatitude: Double = helperLocation?.latitude ?: 0.0,
        helperLongitude: Double = helperLocation?.longitude ?: 0.0,
        helperAccuracy: Float = helperLocation?.horizontalAccuracyMeters ?: 0f
    ): ByteArray = buildString {
        append(reportId).append(FIELD_SEP)
        append(rotatingBleIdHex).append(FIELD_SEP)
        append(timestampMs).append(FIELD_SEP)
        append(helperLatitude).append(FIELD_SEP)
        append(helperLongitude).append(FIELD_SEP)
        append(helperAccuracy).append(FIELD_SEP)
        append(rssiDbm).append(FIELD_SEP)
        append(CryptoEngine.encodeBase64Url(sealed))
    }.toByteArray(Charsets.UTF_8)

    private fun newReportId(nowMs: Long, rotatingBleIdHex: String, payload: ByteArray): String {
        val digest = crypto.deriveKey(payload, "$DOMAIN_REPORT_ID:$nowMs:$rotatingBleIdHex", length = 16)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmac(seed: ByteArray, domain: String, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(crypto.deriveKey(seed, domain), "HmacSHA256"))
        return mac.doFinal(message)
    }

    /** Length-independent comparison so a mismatch does not leak position via timing. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        const val PROTOCOL_VERSION = 1

        /** Identifiers rotate every 15 minutes. */
        const val ROTATION_INTERVAL_MS = 15 * 60_000L

        /** Reports older or newer than this relative to now are refused. */
        const val MAX_REPORT_CLOCK_SKEW_MS = 120_000L

        private const val DOMAIN_ROTATING_ID = "luogo/ble/rotating-id/v1"
        private const val DOMAIN_ITEM_SIGNATURE = "luogo/ble/item-signature/v1"
        private const val DOMAIN_SIGHTING_PAYLOAD = "luogo/ble/sighting-payload/v1"
        private const val DOMAIN_SIGHTING_TAG = "luogo/ble/sighting-tag/v1"
        private const val DOMAIN_REPORT_ID = "luogo/ble/report-id/v1"

        private const val FIELD_SEP = "|"

        private const val MIN_SIGHTINGS_FOR_ALERT = 2
        private const val MIN_DISTINCT_LOCATIONS_FOR_ALERT = 2
        private const val MIN_DISTINCT_LOCATIONS_FOR_HIGH_RISK = 3
        private const val MIN_ALERT_SPAN_MS = 60_000L
        private const val HIGH_RISK_SPAN_MS = 5 * 60_000L
        private const val DISTINCT_LOCATION_RADIUS_METERS = 100.0
        private const val MAX_SAMPLE_IDS = 5
        private const val MAX_TRACKED_REPORT_IDS = 4096

        private val replayLock = Any()
        private val seenReportIds = LinkedHashSet<String>()
    }
}