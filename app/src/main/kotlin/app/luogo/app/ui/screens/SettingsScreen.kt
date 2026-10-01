package app.luogo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.BuildConfig
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.NetworkConfig
import app.luogo.app.ui.viewmodel.LuogoViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: LuogoViewModel,
    isBatteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val netConfig by viewModel.networkConfig.collectAsState()
    val healthMsg by viewModel.healthStatusMessage.collectAsState()
    val diag by viewModel.diagnosticsSnapshot.collectAsState()
    val mapStyle by viewModel.activeMapStyle.collectAsState()
    val satelliteUrl by viewModel.customSatelliteUrl.collectAsState()
    val logs by viewModel.appLogs.collectAsState()
    val profile by viewModel.userProfile.collectAsState()

    var relayUrlInput by remember(netConfig.relayUrl) { mutableStateOf(netConfig.relayUrl) }
    var useTorOrbot by remember(netConfig.useTorOrbot) { mutableStateOf(netConfig.useTorOrbot) }
    var socks5Enabled by remember(netConfig.socks5Enabled) { mutableStateOf(netConfig.socks5Enabled) }
    var socks5Host by remember(netConfig.socks5Host) { mutableStateOf(netConfig.socks5Host) }
    var socks5Port by remember(netConfig.socks5Port) { mutableStateOf(netConfig.socks5Port.toString()) }
    var retentionDays by remember(netConfig.retentionDays) { mutableStateOf(netConfig.retentionDays) }
    var satUrlInput by remember(satelliteUrl) { mutableStateOf(satelliteUrl) }

    var showLogsModal by remember { mutableStateOf(false) }
    var showLicenseModal by remember { mutableStateOf(false) }

    LaunchedEffect(isBatteryExempt) {
        viewModel.refreshDiagnostics(isBatteryExempt)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings, Privacy & Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Privacy-first, self-hostable, zero ads or tracking SDKs, no mandatory Google Play Services.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Diagnostics & Nerd Stats Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Diagnostics & Nerd Stats", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(
                            onClick = { viewModel.refreshDiagnostics(isBatteryExempt) },
                            modifier = Modifier.testTag("refresh_diagnostics_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }

                    val d = diag
                    if (d != null) {
                        Text(
                            text = "Available Sources: ${d.availableSources.joinToString(", ") { it.label }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Active Fusion Source: ${d.currentSourceSummary} · Accuracy: ${d.currentAccuracyMeters?.let { "±${it.toInt()}m" } ?: "Acquiring"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "GNSS Satellites: ${d.gnssSatellitesInFix} in fix · Dual-Frequency (L1+L5): ${if (d.gnssDualFrequencySupported) "Supported" else "Standard"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Wi-Fi: ${if (d.wifiEnabled) "Enabled" else "Off"} · Wi-Fi RTT (802.11mc): ${if (d.wifiRttSupported) "Supported" else "Unavailable"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Bluetooth LE: ${if (d.bleScanSupported) "Supported" else "No"} · UWB Ranging: ${if (d.uwbSupported) "Supported" else "Unavailable"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Inertial/Baro: Accel=${d.accelerometerAvailable}, Gyro=${d.gyroscopeAvailable}, Mag=${d.magnetometerAvailable}, Baro=${d.barometerAvailable}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Network: ${if (d.networkConnected) "Online" else "Offline"} · IPv6: ${d.ipv6Supported} · Routing: ${d.proxySummary}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Pending Encrypted Reports: ${d.pendingEncryptedReportsCount} · Avg Sync Interval: ${
                                d.averageSyncIntervalSeconds?.let { String.format("%.2fs", it) } ?: "2.0s target"
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showLogsModal = true },
                            modifier = Modifier.testTag("open_logs_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Logs (${logs.size})")
                        }
                        OutlinedButton(onClick = onRequestPermissions) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Permissions")
                        }
                    }
                }
            }
        }

        // 2. Network, Self-Hosting, Tor/Orbot & SOCKS5 Proxy Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Self-Hosted Relay, Tor/Orbot & SOCKS5", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = relayUrlInput,
                        onValueChange = { relayUrlInput = it },
                        label = { Text("Relay Server URL (HTTPS / WSS)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("relay_server_url_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connectivity Status: $healthMsg",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Route via Tor / Orbot (127.0.0.1:9050)", fontWeight = FontWeight.Medium)
                            Text("Routes relay and sighting traffic over local Orbot SOCKS5 proxy", style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(
                            checked = useTorOrbot,
                            onCheckedChange = { useTorOrbot = it },
                            modifier = Modifier.testTag("tor_orbot_switch")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom SOCKS5 Proxy", fontWeight = FontWeight.Medium)
                            Text("Configure custom SOCKS5 host and port", style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(
                            checked = socks5Enabled,
                            onCheckedChange = { socks5Enabled = it }
                        )
                    }

                    if (socks5Enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = socks5Host,
                                onValueChange = { socks5Host = it },
                                label = { Text("SOCKS5 Host") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = socks5Port,
                                onValueChange = { socks5Port = it },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val portInt = socks5Port.toIntOrNull() ?: 9050
                                viewModel.updateNetworkSettings(
                                    NetworkConfig(
                                        relayUrl = relayUrlInput,
                                        useTorOrbot = useTorOrbot,
                                        socks5Enabled = socks5Enabled,
                                        socks5Host = socks5Host,
                                        socks5Port = portInt,
                                        retentionDays = retentionDays
                                    )
                                )
                            },
                            modifier = Modifier.testTag("save_network_config_button")
                        ) {
                            Text("Save Network")
                        }
                        OutlinedButton(
                            onClick = { viewModel.testServerConnection(relayUrlInput) },
                            modifier = Modifier.testTag("test_relay_connection_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Connection")
                        }
                    }
                }
            }
        }

        // 3. Maps & Satellite Imagery Provider Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SatelliteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Maps & Satellite Imagery Provider", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapStyleOption.entries.forEach { st ->
                            FilterChip(
                                selected = mapStyle == st,
                                onClick = { viewModel.setMapStyle(st) },
                                label = { Text(st.title) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = satUrlInput,
                        onValueChange = { satUrlInput = it },
                        label = { Text("Custom Licensed Satellite Tile URL ({z}/{x}/{y}) — Optional") },
                        placeholder = { Text(MapStyleOption.SATELLITE_IMAGERY.tileUrlTemplate) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { viewModel.updateCustomSatelliteUrl(satUrlInput) }) {
                        Text("Save Satellite Provider URL")
                    }
                }
            }
        }

        // 4. Location, Battery Optimization & Background Operation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Location Engine & Battery Optimization", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Moving Update Target: ~2.0 seconds (Dynamic GNSS + Wi-Fi + Inertial + Barometer Fusion). Stationary state automatically reduces duty cycle.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBatteryExempt) "Battery Optimization: Unrestricted" else "Battery Optimization: Restricted",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Unrestricted background execution prevents OEMs from throttling live location & BLE finding.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onRequestBatteryExemption) {
                            Text(if (isBatteryExempt) "Exempt" else "Configure")
                        }
                    }
                }
            }
        }

        // 5. Privacy, Encryption Identity & Retention
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy, Encryption & Data Retention", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Device Ed25519 Public Key: ${profile.publicKeyBase64}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Local History Retention Policy:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30, 90).forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    viewModel.updateNetworkSettings(netConfig.copy(retentionDays = days))
                                },
                                label = { Text("$days Days") }
                            )
                        }
                    }
                }
            }
        }

        // 6. About & EUPL v1.2 Open Source License Attribution
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About Luogo-FOSS", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Version: ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE}) · Protocol v${BuildConfig.PROTOCOL_VERSION}")
                    Text("License: European Union Public Licence v. 1.2 (EUPL-1.2)")
                    Text("Original Upstream: https://github.com/lukehmcc/luogo")
                    Text("Map Attribution: © OpenStreetMap contributors · Protomaps · Esri World Imagery")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showLicenseModal = true }) {
                        Text("View EUPL v1.2 License & Attribution Notice")
                    }
                }
            }
        }
    }

    if (showLogsModal) {
        AlertDialog(
            onDismissRequest = { showLogsModal = false },
            title = { Text("Diagnostic Log Viewer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logs.takeLast(18).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsModal = false }) { Text("Close") }
            }
        )
    }

    if (showLicenseModal) {
        AlertDialog(
            onDismissRequest = { showLicenseModal = false },
            title = { Text("EUPL v1.2 & Attribution Notice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Luogo-FOSS is a Kotlin/Jetpack Compose Android derivative work based on Luogo (https://github.com/lukehmcc/luogo), licensed under the European Union Public Licence v. 1.2 (EUPL-1.2).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text(
                        "Pursuant to Article 5 of the EUPL v1.2: This work has been modified in 2026 to provide a native Kotlin/Compose Android & Android Auto implementation with multi-source sensor fusion and a privacy-preserving crowdsourced BLE finding network.",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseModal = false }) { Text("Close") }
            }
        )
    }
}
