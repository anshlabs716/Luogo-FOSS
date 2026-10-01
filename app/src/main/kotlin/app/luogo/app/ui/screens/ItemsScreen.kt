package app.luogo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.ItemType
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.ui.viewmodel.LuogoViewModel
import app.luogo.app.ui.viewmodel.PrimaryTab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemsScreen(
    viewModel: LuogoViewModel,
    onRequestBlePermissions: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val sightingReports by viewModel.sightingReports.collectAsState()
    val trackerAlerts by viewModel.unknownTrackerAlerts.collectAsState()
    val nearbyItem by viewModel.nearbyFindingItem.collectAsState()

    var showRegisterDialog by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<RegisteredItem?>(null) }
    var detailItem by remember { mutableStateOf<RegisteredItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personal Items & Finding Network",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Privacy-preserving crowdsourced BLE finding with 15-minute rotating identifiers (HKDF-SHA256).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showRegisterDialog = true },
                    modifier = Modifier.testTag("register_item_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Item")
                }
            }
        }

        // Abuse Protection & Unknown Tracker Safety Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Anti-Stalking & Unknown Tracker Protection",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Automatically monitors rotating BLE beacons for unrecognized tags moving with you.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onRequestBlePermissions()
                                viewModel.runUnknownTrackerScan()
                            },
                            modifier = Modifier.testTag("unknown_tracker_scan_button")
                        ) {
                            Text("Scan Now")
                        }
                    }

                    if (trackerAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        trackerAlerts.forEach { alert ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unknown Tracker (${alert.riskLevel} Risk)",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Seen ${alert.sightingCount} times across ${alert.distinctLocationsCount} distinct locations (${alert.latestRssiDbm} dBm)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    TextButton(onClick = { viewModel.dismissTrackerAlert(alert.trackerSignatureId) }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Registered Personal Items List (Always displaying Friendly Name first)
        items(items, key = { it.id }) { item ->
            RegisteredItemCard(
                item = item,
                onOpenDetail = { detailItem = item },
                onRing = { viewModel.triggerItemRing(item.id, !item.isRinging) },
                onFindNearby = {
                    onRequestBlePermissions()
                    viewModel.openNearbyFinding(item)
                },
                onMarkLost = { viewModel.toggleItemLost(item.id, !item.isLostMode) },
                onRename = { renamingItem = item },
                onLocateOnMap = { viewModel.focusOnItem(item) },
                onRecordSighting = { viewModel.recordCrowdsourcedSighting(item.id) },
                onRemove = { viewModel.removeItem(item.id) }
            )
        }

        // Offline Encrypted Sighting Report Queue Card
        item {
            val pendingCount = sightingReports.count { !it.isUploaded }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Encrypted Sighting Queue (${sightingReports.size} total · $pendingCount pending)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Helper phone BLE detections are encrypted locally and uploaded with replay protection when connectivity returns.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = { viewModel.flushOfflineQueues() },
                            label = { Text("Sync Queue") },
                            leadingIcon = {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("sync_sightings_queue_button")
                        )
                    }
                }
            }
        }
    }

    // Register Item Dialog
    if (showRegisterDialog) {
        var friendlyName by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(ItemType.EARBUDS) }

        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register Personal Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Give your item a friendly human-readable name (e.g. \"Ansh's Pixel Buds 3\"). " +
                            "A 256-bit Ephemeral Identity Key (EIK) will be generated locally.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = friendlyName,
                        onValueChange = { friendlyName = it },
                        label = { Text("Friendly Name (e.g. Ansh's Pixel Buds 3)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_item_friendly_name_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemType.entries.forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerItem(friendlyName, selectedType)
                        showRegisterDialog = false
                    },
                    modifier = Modifier.testTag("confirm_register_item_button")
                ) {
                    Text("Register Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Item Dialog
    renamingItem?.let { item ->
        var updatedName by remember(item.friendlyName) { mutableStateOf(item.friendlyName) }
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text("Rename ${item.friendlyName}") },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Friendly Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameItem(item.id, updatedName)
                        renamingItem = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) { Text("Cancel") }
            }
        )
    }

    // Full Item Page / Detail Modal
    detailItem?.let { current ->
        val freshItem = items.firstOrNull { it.id == current.id } ?: current
        ModalBottomSheet(onDismissRequest = { detailItem = null }) {
            ItemFullDetailModal(
                item = freshItem,
                onRing = { viewModel.triggerItemRing(freshItem.id, !freshItem.isRinging) },
                onFindNearby = {
                    detailItem = null
                    viewModel.openNearbyFinding(freshItem)
                },
                onMarkLost = { viewModel.toggleItemLost(freshItem.id, !freshItem.isLostMode) },
                onRename = {
                    detailItem = null
                    renamingItem = freshItem
                },
                onViewHistory = {
                    detailItem = null
                    viewModel.selectTab(PrimaryTab.HISTORY)
                },
                onLocateOnMap = {
                    detailItem = null
                    viewModel.focusOnItem(freshItem)
                },
                onRemove = {
                    detailItem = null
                    viewModel.removeItem(freshItem.id)
                }
            )
        }
    }

    // Nearby Finding Radar Modal
    nearbyItem?.let { target ->
        val freshTarget = items.firstOrNull { it.id == target.id } ?: target
        ModalBottomSheet(onDismissRequest = { viewModel.openNearbyFinding(null) }) {
            NearbyFindingSheet(
                item = freshTarget,
                onRingToggle = { viewModel.triggerItemRing(freshTarget.id, !freshTarget.isRinging) },
                onRefreshSighting = { viewModel.recordCrowdsourcedSighting(freshTarget.id, rssiDbm = -53) },
                onOpenOnMap = {
                    viewModel.openNearbyFinding(null)
                    viewModel.focusOnItem(freshTarget)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisteredItemCard(
    item: RegisteredItem,
    onOpenDetail: () -> Unit,
    onRing: () -> Unit,
    onFindNearby: () -> Unit,
    onMarkLost: () -> Unit,
    onRename: () -> Unit,
    onLocateOnMap: () -> Unit,
    onRecordSighting: () -> Unit,
    onRemove: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val status = item.presenceStatus(nowMs)
    val statusColor = when (status) {
        ItemPresenceStatus.LIVE -> Color(0xFF00BFA5)
        ItemPresenceStatus.RECENTLY_SEEN -> Color(0xFFFFB300)
        ItemPresenceStatus.STALE -> Color(0xFF8D6E63)
        ItemPresenceStatus.UNKNOWN -> Color(0xFF78909C)
    }
    val ageMinutes = item.lastSeenTimestampMs?.let { ((nowMs - it) / 60_000L).coerceAtLeast(0L) }

    Card(
        onClick = onOpenDetail,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, tint = statusColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.friendlyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append("Type: ${item.itemType.label}")
                                append(" · Status: ${status.label}")
                                if (ageMinutes != null) {
                                    append(if (ageMinutes == 0L) " · Just now" else " · ${ageMinutes}m ago")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connection: ${item.lastSeenSource} · ${item.signalQualityDescription()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item.batteryPercent?.let {
                    Text("Battery: $it%", style = MaterialTheme.typography.labelMedium)
                }
                item.lastSeenAccuracyMeters?.let {
                    Text("Accuracy: ±${it.toInt()}m", style = MaterialTheme.typography.labelMedium)
                }
                if (item.isLostMode) {
                    Text(
                        "LOST MODE ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onRing,
                    label = { Text(if (item.isRinging) "Stop Ring" else "Ring") },
                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onFindNearby,
                    label = { Text("Find Nearby") },
                    leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onMarkLost,
                    label = { Text(if (item.isLostMode) "Unmark Lost" else "Mark as Lost") },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onLocateOnMap,
                    label = { Text("Map") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onRecordSighting,
                    label = { Text("Update BLE Fix") }
                )
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename ${item.friendlyName}")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${item.friendlyName}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemFullDetailModal(
    item: RegisteredItem,
    onRing: () -> Unit,
    onFindNearby: () -> Unit,
    onMarkLost: () -> Unit,
    onRename: () -> Unit,
    onViewHistory: () -> Unit,
    onLocateOnMap: () -> Unit,
    onRemove: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val status = item.presenceStatus(nowMs)
    val ageSec = item.lastSeenTimestampMs?.let { ((nowMs - it) / 1000L).coerceAtLeast(0L) } ?: 0L
    val ageText = if (ageSec < 60) "$ageSec seconds ago" else "${ageSec / 60} minutes ago"

    Column(modifier = Modifier.padding(20.dp)) {
        Text(item.friendlyName, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Type: ${item.itemType.label}", style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${status.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("Last seen: $ageText", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Location: ${
                if (item.lastSeenLatitude != null && item.lastSeenLongitude != null) {
                    String.format("%.5f, %.5f (±%dm)", item.lastSeenLatitude, item.lastSeenLongitude, item.lastSeenAccuracyMeters?.toInt() ?: 8)
                } else "Unknown"
            }",
            style = MaterialTheme.typography.bodyMedium
        )
        item.batteryPercent?.let {
            Text("Battery: $it%", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Connection: ${item.lastSeenSource} (${item.signalQualityDescription()})", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    "Cryptographic BLE Identity (Internal Only · Rotates Every 15m):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Epoch #${item.rotationEpoch} · EID: ${item.currentRotatingBleIdHex.take(16)}…",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onRing) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isRinging) "Stop Ring" else "Ring")
            }
            Button(onClick = onFindNearby) {
                Icon(Icons.Default.Radar, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Find Nearby")
            }
            OutlinedButton(onClick = onMarkLost) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isLostMode) "Disable Lost Mode" else "Mark as Lost")
            }
            OutlinedButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rename")
            }
            OutlinedButton(onClick = onViewHistory) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("View History")
            }
            OutlinedButton(onClick = onLocateOnMap) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Show on Map")
            }
            TextButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NearbyFindingSheet(
    item: RegisteredItem,
    onRingToggle: () -> Unit,
    onRefreshSighting: () -> Unit,
    onOpenOnMap: () -> Unit
) {
    val rssi = item.rssiDbm ?: -75
    // Normalize RSSI (-95..-40 dBm) to 0.1..1.0 proximity ring
    val signalNormalized = ((rssi + 95f) / 55f).coerceIn(0.1f, 1.0f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Nearby Finding: ${item.friendlyName}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = item.signalQualityDescription(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (item.uwbDistanceMeters != null) {
            Text(
                text = "UWB Ranging Supported: ~${String.format("%.1f m", item.uwbDistanceMeters)} (Azimuth ${item.uwbAzimuthDegrees?.toInt() ?: 0}°)",
                style = MaterialTheme.typography.labelMedium
            )
        } else {
            Text(
                text = "BLE RSSI proximity mode (RSSI indicates relative signal strength, not exact centimeter range)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = maxR,
                    center = center,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.22f),
                    radius = maxR * 0.66f,
                    center = center,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = maxR * signalNormalized,
                    center = center
                )
            }
            Text(
                text = "${rssi} dBm",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onRingToggle,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isRinging) "Stop Ring" else "Ring Item")
            }
            OutlinedButton(
                onClick = onRefreshSighting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh Scan")
            }
            OutlinedButton(
                onClick = onOpenOnMap,
                modifier = Modifier.weight(1f)
            ) {
                Text("Map")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
