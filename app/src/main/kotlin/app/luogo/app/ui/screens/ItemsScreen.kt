package app.luogo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.ItemType
import app.luogo.app.domain.model.RegisteredItem
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Watch
import app.luogo.app.ui.components.CategoryIconTile
import app.luogo.app.ui.components.DetailRow
import app.luogo.app.ui.components.EmptyState
import app.luogo.app.ui.components.FreshnessChip
import app.luogo.app.ui.components.FreshnessTone
import app.luogo.app.ui.components.NoticeCard
import app.luogo.app.ui.components.NoticeTone
import app.luogo.app.ui.components.ScreenScaffold
import app.luogo.app.ui.components.SectionHeader
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.viewmodel.LuogoViewModel

/**
 * Items: your personal things, and the crowdsourced network that helps find them.
 *
 * Every item leads with its friendly name. Rotating BLE identifiers stay internal; the UI
 * never shows one, because it is meaningless to the owner and looks like noise.
 */
@Composable
fun ItemsScreen(
    viewModel: LuogoViewModel,
    onRequestBlePermissions: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val alerts by viewModel.unknownTrackerAlerts.collectAsState()
    val reports by viewModel.sightingReports.collectAsState()
    val nearbyItem by viewModel.nearbyFindingItem.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RegisteredItem?>(null) }
    var removeTarget by remember { mutableStateOf<RegisteredItem?>(null) }

    val nowMs = System.currentTimeMillis()

    ScreenScaffold(
        title = "Items",
        subtitle = if (items.isEmpty()) "Nothing registered yet" else "${items.size} registered",
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.testTag("add_item_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Register an item")
            }
        }
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("items_screen"),
        contentPadding = padding
    ) {

        // The finding network depends on Bluetooth, so surface the state plainly rather than
        // letting items silently never be found.
        item {
            NoticeCard(
                text = "Finding uses Bluetooth to advertise a rotating identifier that changes " +
                    "every 15 minutes. Your item never broadcasts its Bluetooth address, and " +
                    "nearby phones contribute only their own location, never their identity.",
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium),
                icon = Icons.Default.Radar
            )
        }

        if (alerts.isNotEmpty()) {
            item {
                SectionHeader("Safety alerts")
            }
            items(alerts, key = { it.trackerSignatureId }) { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LuogoSpacing.medium, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Unknown tracker nearby",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Seen ${alert.sightingCount} time(s) across " +
                                "${alert.distinctLocationsCount} distinct location(s) as you moved. " +
                                "This can be a tag you forgot about rather than anything malicious.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.dismissTrackerAlert(alert.trackerSignatureId) }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Your items")
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Radar,
                    title = "No items registered",
                    body = "Register the things you lose. Give each one a name you'll recognise, " +
                        "like \"Pixel Buds\" or \"Bike key\".",
                    action = {
                        Button(onClick = { showAdd = true }) { Text("Register an item") }
                    }
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    nowMs = nowMs,
                    onOpenOnMap = { viewModel.focusOnItem(item) },
                    onRing = { viewModel.triggerItemRing(item.id, !item.isRinging) },
                    onToggleLost = { viewModel.toggleItemLost(item.id, it) },
                    onFindNearby = {
                        onRequestBlePermissions()
                        viewModel.openNearbyFinding(item)
                    },
                    onRename = { renameTarget = item },
                    onRemove = { removeTarget = item },
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 4.dp)
                )
            }
        }

        item {
            SectionHeader("Finding network")
            Row(
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.runUnknownTrackerScan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Safety scan")
                }
                OutlinedButton(
                    onClick = { viewModel.flushOfflineQueues() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sync queue")
                }
            }
            Text(
                text = "${reports.size} sighting(s) collected this session. Sightings are stored " +
                    "encrypted and uploaded when a connection is available.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 8.dp)
            )
        }
    }
    }

    if (showAdd) {
        RegisterItemDialog(
            onDismiss = { showAdd = false },
            onRegister = { name, type ->
                viewModel.registerItem(name, type)
                showAdd = false
            }
        )
    }

    renameTarget?.let { item ->
        RenameItemDialog(
            currentName = item.friendlyName,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                viewModel.renameItem(item.id, newName)
                renameTarget = null
            }
        )
    }

    removeTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${item.friendlyName}?") },
            text = {
                Text(
                    "This deletes the item and its encryption key from this device. It cannot be " +
                        "undone, and the item will stop being findable through Luogo-FOSS."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeItem(item.id)
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            }
        )
    }

    nearbyItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.openNearbyFinding(null) },
            title = { Text(item.friendlyName) },
            text = {
                Column {
                    val rssi = item.rssiDbm
                    Text(
                        text = if (rssi != null) {
                            "Signal: ${item.signalQualityDescription()}"
                        } else {
                            "Not currently in Bluetooth range."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // RSSI depends on orientation and obstacles, so it is reported as a signal
                    // band rather than converted into a distance the hardware cannot support.
                    Text(
                        text = "Signal strength is not a distance measurement. Luogo-FOSS does not " +
                            "guess a range from it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.openNearbyFinding(null) }) { Text("Close") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(
    item: RegisteredItem,
    nowMs: Long,
    onOpenOnMap: () -> Unit,
    onRing: () -> Unit,
    onToggleLost: (Boolean) -> Unit,
    onFindNearby: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presence = item.presenceStatus(nowMs)
    val tone = when (presence) {
        ItemPresenceStatus.LIVE -> FreshnessTone.LIVE
        ItemPresenceStatus.RECENTLY_SEEN -> FreshnessTone.RECENT
        ItemPresenceStatus.STALE -> FreshnessTone.STALE
        ItemPresenceStatus.UNKNOWN -> FreshnessTone.UNKNOWN
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_card_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconTile(
                    icon = item.itemType.icon(),
                    contentDescription = item.itemType.label,
                    tint = item.itemType.tint(),
                    container = item.itemType.tint().copy(alpha = 0.16f)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // The friendly name is the identity. The rotating identifier never appears.
                    Text(item.friendlyName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = item.itemType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FreshnessChip(label = presence.label, tone = tone)
            }

            Spacer(Modifier.height(10.dp))
            if (item.lastSeenTimestampMs != null) {
                DetailRow("Last seen", "${formatAge(nowMs - item.lastSeenTimestampMs!!)} ago")
            } else {
                DetailRow("Last seen", "Never seen by any phone")
            }
            item.lastSeenAccuracyMeters?.let { DetailRow("Accuracy", "±${it.toInt()} m") }
            item.batteryPercent?.let { DetailRow("Battery", "$it%") }
            if (item.uwbDistanceMeters != null) {
                DetailRow("UWB range", "${"%.2f".format(item.uwbDistanceMeters)} m")
            }
            DetailRow("Source", item.lastSeenSource)

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mark as lost",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = item.isLostMode,
                    onCheckedChange = onToggleLost,
                    modifier = Modifier.testTag("lost_switch_${item.id}")
                )
            }

            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = onOpenOnMap,
                    label = { Text("Show") },
                    leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null) }
                )
                AssistChip(
                    onClick = onRing,
                    label = { Text(if (item.isRinging) "Stop ring" else "Ring") },
                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                )
                AssistChip(
                    onClick = onFindNearby,
                    label = { Text("Find nearby") },
                    leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null) }
                )
                AssistChip(
                    onClick = onRename,
                    label = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                AssistChip(
                    onClick = onRemove,
                    label = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RegisterItemDialog(
    onDismiss: () -> Unit,
    onRegister: (String, ItemType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ItemType.EARBUDS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register an item") },
        text = {
            Column {
                Text(
                    "Give it a name you will recognise later. A 256-bit identity key is generated " +
                        "on this device and never leaves it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(LuogoSpacing.medium))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friendly name") },
                    placeholder = { Text("Pixel Buds") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_name_field")
                )
                Spacer(Modifier.height(LuogoSpacing.medium))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRegister(name.trim(), type) },
                enabled = name.isNotBlank()
            ) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenameItemDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename item") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Friendly name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
/** Icon per item category so a list of items is scannable at a glance. */
private fun ItemType.icon(): ImageVector = when (this) {
    ItemType.EARBUDS -> Icons.Default.Headphones
    ItemType.PHONE -> Icons.Default.Smartphone
    ItemType.WATCH -> Icons.Default.Watch
    ItemType.LAPTOP -> Icons.Default.Laptop
    ItemType.KEYS -> Icons.Default.VpnKey
    ItemType.BAG -> Icons.Default.Work
    ItemType.BIKE -> Icons.Default.PedalBike
    ItemType.CUSTOM_DEVICE -> Icons.Default.Memory
    ItemType.CUSTOM_ITEM -> Icons.Default.Category
}

private fun ItemType.tint(): Color = when (this) {
    ItemType.EARBUDS -> Color(0xFF7C4DFF)
    ItemType.PHONE -> Color(0xFF1A88E5)
    ItemType.WATCH -> Color(0xFF00A878)
    ItemType.LAPTOP -> Color(0xFF5C6BC0)
    ItemType.KEYS -> Color(0xFFF9A825)
    ItemType.BAG -> Color(0xFF00897B)
    ItemType.BIKE -> Color(0xFFEF5350)
    ItemType.CUSTOM_DEVICE -> Color(0xFF546E7A)
    ItemType.CUSTOM_ITEM -> Color(0xFF8D6E63)
}
