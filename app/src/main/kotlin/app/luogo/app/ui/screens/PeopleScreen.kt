package app.luogo.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.GroupCategory
import app.luogo.app.domain.model.PeerGroup
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.ui.util.QrCodeGenerator
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(viewModel: LuogoViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by viewModel.userProfile.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val peers by viewModel.peers.collectAsState()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var activeInvitePayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var displayNameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var selectedColorArgb by remember(profile.colorArgb) { mutableLongStateOf(profile.colorArgb) }

    val colorChoices = listOf(
        0xFF00796BL,
        0xFF1E88E5L,
        0xFFD81B60L,
        0xFF8E24AAL,
        0xFFF4511EL,
        0xFF43A047L
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Live People & E2EE Groups",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "End-to-end encrypted with XChaCha20/ChaCha20-Poly1305. Group keys are shared only via QR/invite codes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // My Identity & Global Live Sharing Card
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedColorArgb.toInt() or -0x1000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayNameInput.take(2).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (profile.sharingEnabled) "Live E2EE Sharing Active (~2s moving)" else "Sharing Paused",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (profile.sharingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = profile.sharingEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, enabled)
                            },
                            modifier = Modifier.testTag("global_sharing_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Person Color Picker + Save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorChoices.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(col.toInt() or -0x1000000))
                                        .clickable {
                                            selectedColorArgb = col
                                            viewModel.updateProfileAndSharing(displayNameInput, col, profile.sharingEnabled)
                                        }
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, profile.sharingEnabled)
                            }
                        ) {
                            Text("Save Profile")
                        }
                    }

                    // Temporary Sharing Timers
                    Text(
                        "Temporary Sharing Timer:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, true, 15) },
                            label = { Text("15 min") }
                        )
                        AssistChip(
                            onClick = { viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, true, 60) },
                            label = { Text("1 hour") }
                        )
                        AssistChip(
                            onClick = { viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, true, 480) },
                            label = { Text("8 hours") }
                        )
                        AssistChip(
                            onClick = { viewModel.updateProfileAndSharing(displayNameInput, selectedColorArgb, true, null) },
                            label = { Text("Always On") }
                        )
                    }
                }
            }
        }

        // Group Action Buttons
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_group_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Group")
                }
                OutlinedButton(
                    onClick = { showJoinGroupDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("join_group_button")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join via Invite")
                }
            }
        }

        // Groups List with Per-Group Sharing & Members
        items(groups, key = { it.id }) { group ->
            val groupPeers = peers.filter { it.groupId == group.id }
            GroupCard(
                group = group,
                members = groupPeers,
                onToggleShare = { enabled -> viewModel.toggleGroupSharing(group.id, enabled) },
                onInviteClick = {
                    scope.launch {
                        val payload = viewModel.generateInviteForGroup(group.id)
                        activeInvitePayload = group.name to payload
                    }
                },
                onLeaveGroup = { viewModel.leaveGroup(group.id) },
                onRemoveMember = { userId -> viewModel.removeMember(group.id, userId) },
                onLocatePeer = { peer -> viewModel.focusOnPeer(peer) },
                onRoutePeer = { peer -> viewModel.requestRouteTo(peer.latitude, peer.longitude) }
            )
        }
    }

    if (showCreateGroupDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(GroupCategory.FRIENDS) }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create E2EE Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Group Name (e.g. Family, Hiking Crew)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createGroup(newName, selectedCategory)
                        showCreateGroupDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinGroupDialog) {
        var inviteCodeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = { Text("Join Group with Invite Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a 'luogo-invite-key: v1:...' payload shared out-of-band or scanned from a QR code:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it },
                        label = { Text("luogo-invite-key: v1:...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinGroupWithInvite(inviteCodeInput)
                        showJoinGroupDialog = false
                    }
                ) {
                    Text("Join Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) { Text("Cancel") }
            }
        )
    }

    activeInvitePayload?.let { (groupName, payload) ->
        val qrBitmap = remember(payload) { QrCodeGenerator.generateQrBitmap(payload, 512) }
        AlertDialog(
            onDismissRequest = { activeInvitePayload = null },
            title = { Text("Invite to $groupName") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Scan QR code or copy out-of-band key. The relay server never sees this 256-bit encryption key.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Group Invite QR Code",
                            modifier = Modifier.size(210.dp)
                        )
                    }
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Luogo Invite Key", payload))
                        viewModel.postToast("Copied invite key to clipboard")
                        activeInvitePayload = null
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeInvitePayload = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun GroupCard(
    group: PeerGroup,
    members: List<PeerLocationState>,
    onToggleShare: (Boolean) -> Unit,
    onInviteClick: () -> Unit,
    onLeaveGroup: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onLocatePeer: (PeerLocationState) -> Unit,
    onRoutePeer: (PeerLocationState) -> Unit
) {
    val nowMs = System.currentTimeMillis()
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
                        text = "${group.name} (${group.category.label})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${members.size + 1} members · E2EE ChaCha20-Poly1305",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onInviteClick) {
                        Icon(Icons.Default.QrCode, contentDescription = "Generate QR Invite")
                    }
                    IconButton(onClick = onLeaveGroup) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Leave Group")
                    }
                    Switch(
                        checked = group.shareMyLocation,
                        onCheckedChange = onToggleShare
                    )
                }
            }

            if (members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                members.forEach { peer ->
                    val stale = peer.isStale(nowMs)
                    val ageSec = ((nowMs - peer.timestampMs) / 1000L).coerceAtLeast(0L)
                    val ageStr = if (ageSec < 60) "${ageSec}s ago" else "${ageSec / 60}m ago"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(peer.colorArgb.toInt() or -0x1000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.displayName.take(2).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(peer.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = buildString {
                                        append(if (stale) "STALE ($ageStr)" else "LIVE ($ageStr)")
                                        append(" · ±${peer.accuracyMeters.toInt()}m")
                                        append(" · ${peer.activityState.label}")
                                        peer.batteryPercent?.let { append(" · 🔋$it%") }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onLocatePeer(peer) }) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Locate on map")
                            }
                            IconButton(onClick = { onRoutePeer(peer) }) {
                                Icon(Icons.Default.Directions, contentDescription = "Route to person")
                            }
                            IconButton(onClick = { onRemoveMember(peer.userId) }) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "Remove member")
                            }
                        }
                    }
                }
            }
        }
    }
}
