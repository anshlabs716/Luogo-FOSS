package app.luogo.app.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.luogo.app.data.crypto.CryptoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Bluetooth LE transport for the crowdsourced finding network.
 *
 * Honest about Android's real limits:
 *  - A phone cannot scan continuously in the background. Scanning is throttled by the platform
 *    to roughly a few scans every 15 minutes once the app leaves the foreground. This class
 *    therefore *schedules* scans against [SCAN_INTERVAL_MS] rather than pretending to run
 *    forever, and reports its own state truthfully so the UI can say "scanning paused by
 *    Android" instead of "scanning".
 *  - Background scan requires the app to be in the foreground at least once; after that,
 *    Android delivers at most 4 BLE scans per 30 minutes for a background app. We do not
 *    attempt to defeat that, and we do not hold a foreground service open just to scan.
 *  - Advertising a rotating identifier does not reveal a permanent MAC address.
 *
 * All cryptography lives in [BleFindingProtocol]; this class only moves bytes.
 */
class BleFindingEngine(
    private val context: Context,
    private val protocol: BleFindingProtocol,
    private val crypto: CryptoEngine
) {

    data class Detection(
        val rotatingIdHex: String,
        val rssiDbm: Int,
        val timestampMs: Long
    )

    data class ScanState(
        val isScanning: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val hasPermission: Boolean = false,
        val lastScanStartedMs: Long? = null,
        val lastScanFinishedMs: Long? = null,
        val detectionsSinceLastStart: Int = 0,
        val note: String = "Idle"
    )

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)

    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _detections = MutableStateFlow<Map<String, Detection>>(emptyMap())
    /** Latest detection per rotating id. */
    val detections: StateFlow<Map<String, Detection>> = _detections.asStateFlow()

    private var scanning = false
    private var advertisingIds = emptyMap<String, UUID>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDetection(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onDetection(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _scanState.value = _scanState.value.copy(
                isScanning = false,
                note = "Scan failed (Android error $errorCode)"
            )
        }
    }

    private fun onDetection(result: ScanResult) {
        val rotatingId = extractRotatingId(result.scanRecord) ?: return
        val detection = Detection(
            rotatingIdHex = rotatingId,
            rssiDbm = result.rssi,
            timestampMs = System.currentTimeMillis()
        )
        _detections.value = _detections.value + (rotatingId to detection)
        _scanState.value = _scanState.value.copy(detectionsSinceLastStart = _scanState.value.detectionsSinceLastStart + 1)
    }

    private val advertiseCallbacks = HashMap<String, AdvertiseCallback>()

    // ------------------------------------------------------------ capability

    fun hasBleSupport(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasAdvertiseSupport(): Boolean =
        adapter?.isMultipleAdvertisementSupported == true

    fun isBluetoothEnabled(): Boolean =
        runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun isBluetoothReady(): Boolean = hasBleSupport() && isBluetoothEnabled() && hasScanPermission()

    // -------------------------------------------------------------- scanning

    /**
     * Starts one scan window for the caller's own items.
     *
     * [knownRotatingIds] is the set of currently advertised identifiers belonging to this
     * user's items. Filtering by it means unrelated BLE traffic in the area is never even
     * delivered to the app, which is both faster and more private.
     */
    @SuppressLint("MissingPermission")
    fun startScan(knownRotatingIds: Collection<String>, durationMs: Int = SCAN_DURATION_MS) {
        if (!isBluetoothReady()) {
            _scanState.value = _scanState.value.copy(
                isScanning = false,
                bluetoothEnabled = isBluetoothEnabled(),
                hasPermission = hasScanPermission(),
                note = when {
                    !hasBleSupport() -> "This device has no Bluetooth LE support"
                    !isBluetoothEnabled() -> "Bluetooth is turned off"
                    else -> "Bluetooth scan permission not granted"
                }
            )
            return
        }
        if (scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return

        val filters = knownRotatingIds
            .mapNotNull { hex -> hex.takeIf { it.length == ROTATING_ID_HEX_LENGTH } }
            .map { hex ->
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(LUOGO_SERVICE_UUID))
                    .setServiceData(ParcelUuid(LUOGO_SERVICE_UUID), hexToBytes(hex))
                    .build()
            }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        scanning = true
        _scanState.value = _scanState.value.copy(
            isScanning = true,
            bluetoothEnabled = true,
            hasPermission = true,
            lastScanStartedMs = System.currentTimeMillis(),
            detectionsSinceLastStart = 0,
            note = "Scanning for your items"
        )
        runCatching { scanner.startScan(filters, settings, scanCallback) }
            .onFailure {
                scanning = false
                _scanState.value = _scanState.value.copy(isScanning = false, note = "Could not start scan")
            }

        // Android ends a scan on its own schedule; we stop cleanly so we do not hold the radio.
        android.os.Handler(context.mainLooper).postDelayed({
            if (scanning) stopScan("Scan window finished")
        }, durationMs.toLong())
    }

    @SuppressLint("MissingPermission")
    fun stopScan(note: String = "Scan paused") {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanState.value = _scanState.value.copy(
            isScanning = false,
            lastScanFinishedMs = System.currentTimeMillis(),
            note = note
        )
    }

    /**
     * How long until the next scan window is allowed.
     *
     * In the background Android permits only a handful of BLE scans per 30 minutes, so this is
     * the honest interval. Surfaced in diagnostics rather than hidden.
     */
    fun nextScanAllowedInMs(nowMs: Long = System.currentTimeMillis()): Long {
        val last = _scanState.value.lastScanFinishedMs ?: return 0L
        return (last + SCAN_INTERVAL_MS - nowMs).coerceAtLeast(0L)
    }

    fun isScanWindowOpen(nowMs: Long = System.currentTimeMillis()): Boolean =
        !scanning && nextScanAllowedInMs(nowMs) == 0L

    // ----------------------------------------------------------- advertising

    /**
     * Starts advertising the rotating identifier for one item.
     *
     * The advertised payload is a truncated HMAC of the item seed plus the current rotation
     * epoch. It is not the device MAC and not a stable hardware identifier.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(itemId: String, seedBase64: String) {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        if (!hasAdvertiseSupport()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val rotatingId = protocol.currentRotatingBleIdHex(seedBase64)
        val serviceUuid = LUOGO_SERVICE_UUID
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .addServiceData(ParcelUuid(serviceUuid), hexToBytes(rotatingId))
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(ADVERTISE_WINDOW_MS)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                advertisingIds = advertisingIds - itemId
            }
        }
        advertiseCallbacks[itemId] = callback
        advertisingIds = advertisingIds + (itemId to serviceUuid)
        runCatching { advertiser.startAdvertising(settings, data, callback) }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising(itemId: String) {
        val callback = advertiseCallbacks.remove(itemId) ?: return
        advertisingIds = advertisingIds - itemId
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
    }

    fun stopAllAdvertising() {
        for (itemId in advertisingIds.keys.toList()) stopAdvertising(itemId)
    }

    fun isAdvertising(itemId: String): Boolean = advertisingIds.containsKey(itemId)

    // -------------------------------------------------------------- helpers

    private fun extractRotatingId(record: ScanRecord?): String? {
        record ?: return null
        val data = record.getServiceData(ParcelUuid(LUOGO_SERVICE_UUID)) ?: return null
        if (data.size != ROTATING_ID_BYTES) return null
        return data.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            (((hex[i * 2].digitToIntOrNull(16) ?: 0) shl 4) or
                (hex[i * 2 + 1].digitToIntOrNull(16) ?: 0)).toByte()
        }

    companion object {
        /** Private service UUID; not an adopted Bluetooth SIG service. */
        val LUOGO_SERVICE_UUID: UUID = UUID.fromString("0000f055-0000-1000-8000-00805f9b34fb")

        private const val ROTATING_ID_BYTES = 16
        private const val ROTATING_ID_HEX_LENGTH = ROTATING_ID_BYTES * 2

        /** Android's practical background cadence. See the class docs. */
        const val SCAN_INTERVAL_MS = 15 * 60_000L
        private const val SCAN_DURATION_MS = 12_000
        private const val ADVERTISE_WINDOW_MS = 30_000
    }
}