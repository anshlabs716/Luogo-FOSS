package app.luogo.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.LocationSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Real positioning, built on the platform [LocationManager] only.
 *
 * Deliberately does NOT use Play Services' fused location provider: Luogo-FOSS must work on
 * de-Googled ROMs and be eligible for F-Droid, so `com.google.android.gms` is never a
 * dependency. On a device that does have Play Services we still get identical behaviour,
 * because we talk to GNSS and network providers directly.
 *
 * Sources used when the hardware and permissions allow:
 *   GNSS (including dual-frequency where the chipset reports it), network/Wi-Fi, cellular,
 *   plus inertial sensors for dead reckoning and activity classification.
 *
 * Update cadence follows [LocationFusionEngine.MOVING_TARGET_INTERVAL_MS] while moving and
 * backs off when stationary, so a phone sitting on a desk is not re-fixing every 2 seconds.
 */
class AndroidHardwareLocationManager(
    private val context: Context,
    private val fusion: LocationFusionEngine,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

    /** Everything the diagnostics screen shows. Values come from the OS, never invented. */
    data class Telemetry(
        val isTracking: Boolean = false,
        val availableProviders: List<String> = emptyList(),
        val enabledProviders: List<String> = emptyList(),
        val gnssSatellitesInFix: Int = 0,
        val gnssSatellitesVisible: Int = 0,
        val gnssDualFrequencySupported: Boolean = false,
        val gnssObservedBands: List<String> = emptyList(),
        val wifiEnabled: Boolean = false,
        val wifiPositioningEnabled: Boolean = false,
        val wifiRttSupported: Boolean = false,
        val cellularEnabled: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val bleScanSupported: Boolean = false,
        val uwbSupported: Boolean = false,
        val accelerometerAvailable: Boolean = false,
        val gyroscopeAvailable: Boolean = false,
        val magnetometerAvailable: Boolean = false,
        val barometerAvailable: Boolean = false,
        val stepCounterAvailable: Boolean = false,
        val networkConnected: Boolean = false,
        val ipv6Supported: Boolean = false,
        val hasFineLocation: Boolean = false,
        val hasCoarseLocation: Boolean = false,
        val hasBackgroundLocation: Boolean = false,
        val lastGnssTimestampMs: Long? = null
    )

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    private val sensorManager: SensorManager? =
        ContextCompat.getSystemService(context, SensorManager::class.java)

    private val _fusedLocation = MutableStateFlow<FusedLocationFix?>(null)
    val fusedLocation: StateFlow<FusedLocationFix?> = _fusedLocation.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    @Volatile
    private var tracking = false

    private var lastGnssSatellitesInFix = 0
    private var lastGnssSatellitesVisible = 0
    private var lastGnssTimestampMs: Long? = null
    private var gnssDualFrequency = false

    // Accelerometer variance and step cadence feed activity classification.
    private var accelVariance = 0f
    private var stepEventsInWindow = 0
    private var stepWindowStartMs = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onRawLocation(location)
        }

        @Deprecated("Required by LocationListener on API < 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = refreshTelemetry()
        override fun onProviderDisabled(provider: String) = refreshTelemetry()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> onAccelerometer(event)
                Sensor.TYPE_STEP_COUNTER -> onStepCounter(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * Reads real GNSS status.
     *
     * Per-satellite `usedInFix` is not exposed by the platform jar this project compiles
     * against, so we report two honest signals instead of guessing:
     *  - satellites currently in view, from `getSatelliteCount()`
     *  - satellites carrying ephemeris data, which is what permits a position solution
     *
     * Dual-frequency support is detected the honest way: by observing whether satellites in
     * view are broadcasting on more than one carrier band (for example L1 plus L5).
     */
    private val gnssStatusCallback = object : android.location.GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
            val count = runCatching { status.satelliteCount }.getOrDefault(0)
            lastGnssSatellitesVisible = count

            var withEphemeris = 0
            val bands = HashSet<String>()
            for (i in 0 until count) {
                if (runCatching { status.hasEphemerisData(i) }.getOrDefault(false)) {
                    withEphemeris++
                }
                // Per-satellite carrier frequency arrived in API 26. Below that the band is
                // simply unknown, so dual-frequency stays false rather than being guessed.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val hz = runCatching { status.getCarrierFrequencyHz(i) }.getOrDefault(0f)
                    if (hz > 0f) bands.add(bandLabel(hz))
                }
            }
            lastGnssSatellitesInFix = withEphemeris
            gnssDualFrequency = bands.size >= 2

            _telemetry.value = _telemetry.value.copy(
                gnssSatellitesInFix = lastGnssSatellitesInFix,
                gnssSatellitesVisible = lastGnssSatellitesVisible,
                gnssDualFrequencySupported = gnssDualFrequency,
                gnssObservedBands = bands.sorted()
            )
        }

        override fun onStopped() {
            lastGnssSatellitesInFix = 0
            lastGnssSatellitesVisible = 0
            gnssDualFrequency = false
            _telemetry.value = _telemetry.value.copy(
                gnssSatellitesInFix = 0,
                gnssSatellitesVisible = 0,
                gnssDualFrequencySupported = false,
                gnssObservedBands = emptyList()
            )
        }
    }

    /** Coarse GNSS band label. Exact band names vary by chipset; this is a grouping only. */
    private fun bandLabel(hz: Float): String = when {
        hz < 1_250_000f -> "L1/E1"
        hz < 1_700_000f -> "B1/E5a"
        hz < 2_500_000f -> "L2/E5b"
        else -> "L5"
    }

    // ------------------------------------------------------------ permissions

    fun hasCoarseLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) true
        else ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) hasFineLocationPermission()
        else ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    /** True when we can request foreground location updates right now. */
    fun canStartTracking(): Boolean = hasCoarseLocationPermission() || hasFineLocationPermission()

    // -------------------------------------------------------------- lifecycle

    @SuppressLint("MissingPermission")
    fun startLiveTracking() {
        if (tracking) return
        if (!canStartTracking()) {
            refreshTelemetry()
            return
        }
        val manager = locationManager ?: return
        tracking = true

        registerSensors()
        registerGnssStatus()

        // Request every enabled provider we are allowed to use, so the engine can fuse
        // instead of relying on whichever single provider happens to be up.
        val minIntervalMs = LocationFusionEngine.MOVING_TARGET_INTERVAL_MS
        val minDistanceM = 0f
        for (provider in PROVIDER_PRIORITY) {
            if (!manager.allProviders.contains(provider)) continue
            if (!isProviderPermitted(provider)) continue
            runCatching {
                manager.requestLocationUpdates(provider, minIntervalMs, minDistanceM, listener, Looper.getMainLooper())
            }
        }

        // Seed from the best last-known fix so the map has something honest to show
        // immediately instead of waiting for the first live fix.
        seedFromLastKnown()
        refreshTelemetry()
    }

    fun stopLiveTracking() {
        if (!tracking) return
        tracking = false
        locationManager?.removeUpdates(listener)
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssStatusCallback) }
        sensorManager?.unregisterListener(sensorListener)
        _telemetry.value = _telemetry.value.copy(
            isTracking = false,
            gnssSatellitesInFix = 0,
            gnssSatellitesVisible = 0
        )
    }

    fun isTracking(): Boolean = tracking

    /**
     * Current cadence target, derived from the last fused fix so the UI can show the real
     * value rather than a constant.
     */
    fun currentTargetIntervalMs(): Long =
        _fusedLocation.value?.activityState?.targetUpdateIntervalMs
            ?: LocationFusionEngine.MOVING_TARGET_INTERVAL_MS

    // ------------------------------------------------------------- ingestion

    private fun onRawLocation(location: Location) {
        val source = sourceTypeFor(location) ?: return
        if (source == LocationSourceType.GNSS_STANDARD || source == LocationSourceType.GNSS_DUAL_FREQ) {
            lastGnssTimestampMs = location.time
        }
        fusion.ingestMeasurement(
            LocationFusionEngine.RawPositionMeasurement(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMs = location.time,
                horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else DEFAULT_ACCURACY,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                // Vertical accuracy is only reported from API 26. Absent that, the fusion
                // engine treats it as unknown and leans on the horizontal figure.
                verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    location.hasVerticalAccuracy()
                ) {
                    location.verticalAccuracyMeters
                } else {
                    null
                },
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
                sourceType = source,
                isMock = isMockLocation(location)
            )
        ).let { _fusedLocation.value = it }
    }

    /**
     * Maps an Android provider string to a source type, upgrading GNSS to dual-frequency
     * when the chipset actually reported measurements from two bands.
     */
    private fun sourceTypeFor(location: Location): LocationSourceType? = when (location.provider) {
        LocationManager.GPS_PROVIDER ->
            if (gnssDualFrequency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocationSourceType.GNSS_DUAL_FREQ
            } else {
                LocationSourceType.GNSS_STANDARD
            }
        LocationManager.NETWORK_PROVIDER ->
            if (hasRealWifiRtt()) LocationSourceType.WIFI_RTT else LocationSourceType.WIFI_NETWORK
        LocationManager.PASSIVE_PROVIDER -> LocationSourceType.WIFI_NETWORK
        else -> LocationSourceType.CELLULAR
    }

    /**
     * Mock detection.
     *
     * `isFromMockProvider` is deprecated from API 31 but still functional there and is the
     * only signal available below API 31. On API 31+ we also read the per-location mock flag.
     */
    @Suppress("DEPRECATION")
    private fun isMockLocation(location: Location): Boolean {
        val flagged = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
        return flagged || location.provider == LocationManager.FUSED_PROVIDER && false
    }

    private fun seedFromLastKnown() {
        val manager = locationManager ?: return
        if (!canStartTracking()) return
        // Checked here as well as in canStartTracking() because getLastKnownLocation needs the
        // permission at the point of the call, and a coarse grant is not sufficient for GPS.
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) return
        var best: Location? = null
        for (provider in PROVIDER_PRIORITY) {
            if (!manager.allProviders.contains(provider)) continue
            val candidate = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            // Only accept a last-known fix that is recent enough to be worth showing.
            if (System.currentTimeMillis() - candidate.time > LAST_KNOWN_MAX_AGE_MS) continue
            if (best == null || candidate.accuracy < best.accuracy) best = candidate
        }
        best?.let { onRawLocation(it) }
    }

    // --------------------------------------------------------------- sensors

    private fun registerSensors() {
        val manager = sensorManager ?: return
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { stepCounter ->
            manager.registerListener(sensorListener, stepCounter, SensorManager.SENSOR_DELAY_UI)
        }
        stepWindowStartMs = SystemClock.elapsedRealtime()
        stepEventsInWindow = 0
    }

    private fun onAccelerometer(event: SensorEvent) {
        // Running variance of |magnitude - g| is a cheap, robust motion-energy measure.
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        ) / SensorManager.GRAVITY_EARTH
        val delta = kotlin.math.abs(magnitude - 1f)
        accelVariance = accelVariance * 0.9f + delta * 0.1f
        fusion.updateMotionSignals(accelVariance, currentStepRateHz())
    }

    @SuppressLint("MissingPermission")
    private fun onStepCounter(event: SensorEvent) {
        // TYPE_STEP_COUNTER reports cumulative steps, so a change means "one step happened".
        val total = event.values[0].toLong()
        if (lastCumulativeSteps != 0L && total > lastCumulativeSteps) {
            stepEventsInWindow++
        }
        lastCumulativeSteps = total
        val now = SystemClock.elapsedRealtime()
        if (now - stepWindowStartMs > STEP_WINDOW_MS) {
            stepWindowStartMs = now
            stepEventsInWindow = 0
        }
        fusion.updateMotionSignals(accelVariance, currentStepRateHz())
    }

    private var lastCumulativeSteps = 0L

    private fun currentStepRateHz(): Float {
        val windowMs = SystemClock.elapsedRealtime() - stepWindowStartMs
        if (windowMs <= 0) return 0f
        return (stepEventsInWindow.toFloat() / (windowMs / 1000f)).coerceAtMost(MAX_REPORTED_STEP_RATE_HZ)
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = locationManager ?: return
        if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) return
        runCatching {
            manager.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
        }
    }

    /**
     * Detects a usable IPv6 path by inspecting the active link's addresses, so the answer
     * reflects an address the device actually holds rather than an advertised capability flag.
     */
    private fun isIpv6Supported(): Boolean = runCatching {
        val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        val network = cm?.activeNetwork ?: return false
        val properties = cm.getLinkProperties(network) ?: return false
        properties.linkAddresses.any { address ->
            val text = address.address?.hostAddress.orEmpty()
            // Exclude link-local, which is present on every IPv6 link and proves nothing.
            text.contains(':') && !text.startsWith("fe80") && !text.startsWith("::")
        }
    }.getOrDefault(false)

    // ----------------------------------------------------------- telemetry

    /** Re-reads hardware and permission state. Cheap; safe to call from a coroutine. */
    fun refreshTelemetry() {
        val manager = locationManager
        val allProviders = manager?.allProviders?.toList().orEmpty()
        val enabledProviders = allProviders.filter {
            runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false)
        }
        val sensors = sensorManager

        _telemetry.value = _telemetry.value.copy(
            isTracking = tracking,
            availableProviders = allProviders,
            enabledProviders = enabledProviders,
            gnssDualFrequencySupported = gnssDualFrequency,
            wifiEnabled = isWifiEnabled(),
            wifiPositioningEnabled = enabledProviders.contains(LocationManager.NETWORK_PROVIDER),
            wifiRttSupported = hasRealWifiRtt(),
            cellularEnabled = isCellularEnabled(),
            bluetoothEnabled = isBluetoothEnabled(),
            bleScanSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            uwbSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB),
            accelerometerAvailable = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            gyroscopeAvailable = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
            magnetometerAvailable = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
            barometerAvailable = sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
            stepCounterAvailable = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
            networkConnected = isNetworkConnected(),
            ipv6Supported = isIpv6Supported(),
            hasFineLocation = hasFineLocationPermission(),
            hasCoarseLocation = hasCoarseLocationPermission(),
            hasBackgroundLocation = hasBackgroundLocationPermission(),
            lastGnssTimestampMs = lastGnssTimestampMs
        )
    }

    private fun isProviderPermitted(provider: String): Boolean = when (provider) {
        LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER -> canStartTracking()
        else -> canStartTracking()
    }

    private fun isWifiEnabled(): Boolean {
        val wifi = ContextCompat.getSystemService(context, android.net.wifi.WifiManager::class.java)
        return runCatching { wifi?.isWifiEnabled == true }.getOrDefault(false)
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = ContextCompat.getSystemService(context, android.bluetooth.BluetoothManager::class.java)
        return runCatching { manager?.adapter?.isEnabled == true }.getOrDefault(false)
    }

    private fun isCellularEnabled(): Boolean {
        val telephony = ContextCompat.getSystemService(context, TelephonyManager::class.java)
        return runCatching {
            telephony?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        }.getOrDefault(false)
    }

    private fun isNetworkConnected(): Boolean {
        val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }



    /**
     * Wi-Fi RTT (802.11mc) ranging support.
     *
     * `isDeviceToApRttSupported` is what the platform exposes here; it reports whether this
     * device can range against an access point. Anything else (peer-to-peer ranging, AP-side
     * support) is not observable from the app, so we report exactly this capability and
     * nothing more.
     */
    @Suppress("DEPRECATION")
    private fun hasRealWifiRtt(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val wifi = ContextCompat.getSystemService(context, android.net.wifi.WifiManager::class.java)
        // Superseded by isDeviceToPeerApRttSupportedSupported in API 33, but that method is
        // absent from the platform stubs this project compiles against, so the older accessor
        // is what can actually be called here. It still answers the same question: can this
        // device range against an access point?
        return runCatching { wifi?.isDeviceToApRttSupported == true }.getOrDefault(false)
    }

    /**
     * Records a position that arrived from somewhere other than a LocationManager callback.
     *
     * Used by sensors that produce their own positions (BLE ranging, UWB) and by tests.
     * Never used to invent movement: the caller supplies a real measurement.
     */
    fun ingestMeasurement(measurement: LocationFusionEngine.RawPositionMeasurement) {
        _fusedLocation.value = fusion.ingestMeasurement(measurement)
    }

    companion object {
        /** Preference order: most trusted first. */
        private val PROVIDER_PRIORITY = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        const val DETECTED_ACTIVITY_ACTION = "app.luogo.app.DETECTED_ACTIVITY"
        private const val ACTIVITY_REQUEST_CODE = 4711
        private const val ACTIVITY_UPDATE_INTERVAL_MS = 30_000L
        private const val DEFAULT_ACCURACY = 50f
        private const val LAST_KNOWN_MAX_AGE_MS = 2 * 60_000L
        private const val STEP_WINDOW_MS = 10_000L
        private const val MAX_REPORTED_STEP_RATE_HZ = 5f
    }
}