package app.luogo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.BuildConfig
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.NetworkConfig
import app.luogo.app.ui.components.DetailRow
import app.luogo.app.ui.components.NoticeCard
import app.luogo.app.ui.components.NoticeTone
import app.luogo.app.ui.components.ScreenHeader
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.viewmodel.LuogoViewModel

/**
 * Settings, grouped by the thing being configured.
 *
 * Diagnostic values are only rendered when the snapshot actually exists. Nothing here
 * substitutes a placeholder for a reading the OS did not give us.
 */
@Composable
fun SettingsScreen(
    viewModel: LuogoViewModel,
    isBatteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val networkConfig by viewModel.networkConfig.collectAsState()
    val mapStyle by viewModel.activeMapStyle.collectAsState()
    val satelliteUrl by viewModel.customSatelliteUrl.collectAsState()
    val health by viewModel.healthStatusMessage.collectAsState()
    val diagnostics by viewModel.diagnosticsSnapshot.collectAsState()
    val logs by viewModel.appLogs.collectAsState()

    var relayUrl by remember(networkConfig) { mutableStateOf(networkConfig.relayUrl) }
    var socksHost by remember(networkConfig) { mutableStateOf(networkConfig.socks5Host) }
    var socksPort by remember(networkConfig) { mutableStateOf(networkConfig.socks5Port.toString()) }
    var satelliteInput by remember(satelliteUrl) { mutableStateOf(satelliteUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(LuogoSpacing.small)
    ) {
        ScreenHeader(title = "Settings")

        // ------------------------------------------------------------ location
        SettingsSection("Location", Icons.Default.MyLocation) {
            DetailRow("Background tracking", if (isBatteryExempt) "Exempt from battery optimisation" else "Restricted by the system")
            if (!isBatteryExempt) {
                TextButton(onClick = onRequestBatteryExemption) {
                    Text("Request battery optimisation exemption")
                }
                Text(
                    text = "Without this, Android may pause live sharing when the app is closed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRequestPermissions) { Text("Explain and grant permissions") }
        }

        // ---------------------------------------------------------------- maps
        SettingsSection("Maps", Icons.Default.Layers) {
            DetailRow("Map style", mapStyle.title)
            DetailRow("Attribution", mapStyle.attribution)
            for (option in MapStyleOption.entries) {
                val selected = option == mapStyle
                Card(
                    onClick = { viewModel.setMapStyle(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .testTag("settings_style_${option.id}"),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(LuogoSpacing.small))
            Text("Custom satellite imagery", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Point at your own imagery service. The template must contain " +
                    "{z}, {x} and {y}. Leave empty to use Esri World Imagery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = satelliteInput,
                onValueChange = { satelliteInput = it },
                label = { Text("Tile template URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("satellite_url_field")
            )
            TextButton(onClick = { viewModel.updateCustomSatelliteUrl(satelliteInput) }) {
                Text("Save imagery source")
            }
        }

        // ------------------------------------------------------------- network
        SettingsSection("Network and server", Icons.Default.Cloud) {
            Text(
                text = "Luogo-FOSS does not require a particular server. Point it at any relay " +
                    "you host; nothing is sent unless you enable sharing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it },
                label = { Text("Relay URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("relay_url_field")
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.testServerConnection(relayUrl) },
                    modifier = Modifier.testTag("test_connection_button")
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Test connection")
                }
            }
            Text(
                text = health,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(LuogoSpacing.small))
            Text("Proxy", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = socksHost,
                onValueChange = { socksHost = it },
                label = { Text("SOCKS5 host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = socksPort,
                onValueChange = { socksPort = it.filter(Char::isDigit).take(5) },
                label = { Text("SOCKS5 port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Use Tor via Orbot by leaving the host at 127.0.0.1 and port 9050 with " +
                    "Tor enabled. Traffic is never rerouted without this being switched on.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    viewModel.updateNetworkSettings(
                        NetworkConfig(
                            relayUrl = relayUrl.trim(),
                            useTorOrbot = socksHost == "127.0.0.1" && socksPort == "9050",
                            socks5Enabled = true,
                            socks5Host = socksHost.trim(),
                            socks5Port = socksPort.toIntOrNull() ?: 9050,
                            retentionDays = networkConfig.retentionDays
                        )
                    )
                },
                modifier = Modifier.testTag("save_network_button")
            ) { Text("Save network settings") }
        }

        // ------------------------------------------------------------- privacy
        SettingsSection("Privacy and data", Icons.Default.PrivacyTip) {
            DetailRow("Ads", "None")
            DetailRow("Analytics", "None")
            DetailRow("Google account", "Not required")
            DetailRow("Google Play Services", "Not used")
            DetailRow("History retention", "${networkConfig.retentionDays} days")
            Spacer(Modifier.height(6.dp))
            Text("Encryption", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Device identity is an Ed25519 key pair. Group locations are sealed with " +
                    "AES-256-GCM under a per-group key, and the relay only ever stores ciphertext.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.deleteAllHistory() },
                    modifier = Modifier.testTag("delete_all_history_button")
                ) { Text("Delete all history") }
            }
        }

        // ------------------------------------------------------- notifications
        SettingsSection("Notifications", Icons.Default.Notifications) {
            Text(
                text = "Channels: live tracking, saved-place arrival and departure, and item " +
                    "detection with tracker alerts. Android requires the persistent live-sharing " +
                    "notification while background location is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --------------------------------------------------------- diagnostics
        SettingsSection("Diagnostics", Icons.Default.BugReport) {
            val snapshot = diagnostics
            if (snapshot == null) {
                Text(
                    text = "Not collected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { viewModel.refreshDiagnostics(isBatteryExempt) },
                    modifier = Modifier.testTag("refresh_diagnostics_button")
                ) { Text("Collect diagnostics") }
            } else {
                DetailRow("Current source", snapshot.currentSourceSummary)
                DetailRow(
                    "Accuracy",
                    snapshot.currentAccuracyMeters?.let { "±${it.toInt()} m" } ?: "No fix"
                )
                DetailRow("Sources available", snapshot.availableSources.joinToString().ifEmpty { "None" })
                DetailRow("GNSS satellites (fix)", "${snapshot.gnssSatellitesInFix}")
                DetailRow("Dual-frequency GNSS", if (snapshot.gnssDualFrequencySupported) "Detected" else "Not observed")
                DetailRow("Wi-Fi", if (snapshot.wifiEnabled) "On" else "Off")
                DetailRow("Wi-Fi RTT", if (snapshot.wifiRttSupported) "Supported" else "Not available")
                DetailRow("Bluetooth", if (snapshot.bluetoothEnabled) "On" else "Off")
                DetailRow("BLE scanning", if (snapshot.bleScanSupported) "Supported" else "Not available")
                DetailRow("UWB", if (snapshot.uwbSupported) "Available" else "Not available")
                DetailRow(
                    "Sensors",
                    listOfNotNull(
                        "accel".takeIf { snapshot.accelerometerAvailable },
                        "gyro".takeIf { snapshot.gyroscopeAvailable },
                        "mag".takeIf { snapshot.magnetometerAvailable },
                        "baro".takeIf { snapshot.barometerAvailable }
                    ).joinToString().ifEmpty { "none" }
                )
                DetailRow("Network", if (snapshot.networkConnected) "Connected" else "Offline")
                DetailRow("IPv6", if (snapshot.ipv6Supported) "Available" else "Not available")
                DetailRow("Route", snapshot.proxySummary)
                DetailRow("Relay", snapshot.relayUrl)
                DetailRow("Pending encrypted reports", "${snapshot.pendingEncryptedReportsCount}")
                DetailRow("Background tracking", if (snapshot.backgroundTrackingEnabled) "Running" else "Stopped")
                OutlinedButton(
                    onClick = { viewModel.refreshDiagnostics(isBatteryExempt) },
                    modifier = Modifier.testTag("refresh_diagnostics_button")
                ) { Text("Refresh") }
            }

            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(LuogoSpacing.small))
                Text("Recent activity", style = MaterialTheme.typography.titleSmall)
                logs.take(12).forEach { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --------------------------------------------------------------- about
        SettingsSection("About", Icons.Default.Info) {
            DetailRow("App", BuildConfig.APPLICATION_ID)
            DetailRow("Version", BuildConfig.VERSION_NAME)
            DetailRow("Protocol version", "${BuildConfig.PROTOCOL_VERSION}")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Luogo-FOSS is a fork of Luogo by lukehmcc, licensed under the " +
                    "European Union Public Licence 1.2. See LICENSE for terms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Map data © OpenStreetMap contributors. Satellite imagery © Esri.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(LuogoSpacing.extraLarge))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LuogoSpacing.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}