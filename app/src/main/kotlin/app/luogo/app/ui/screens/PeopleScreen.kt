package app.luogo.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.GroupCategory
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.ui.components.DetailRow
import app.luogo.app.ui.components.EmptyState
import app.luogo.app.ui.components.FreshnessChip
import app.luogo.app.ui.components.FreshnessTone
import app.luogo.app.ui.components.InitialsAvatar
import app.luogo.app.ui.components.ScreenScaffold
import app.luogo.app.ui.components.SectionHeader
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.launch

/**
 * People: who you share with, and the groups that make it happen.
 *
 * Layout order follows what people actually come here to do: check whether sharing is on,
 * see who is where, then manage groups.
 */
@Composable
fun PeopleScreen(viewModel: LuogoViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showCreateGroup by remember { mutableStateOf(false) }
    var showJoinGroup by remember { mutableStateOf(false) }
    var inviteForGroupId by remember { mutableStateOf<String?>(null) }
    var inviteForGroupName by remember { mutableStateOf("") }
    var invitePayload by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "People",
        subtitle = if (peers.isEmpty()) "No one else yet" else "${peers.size} sharing with you",
        actions = {
            IconButton(onClick = { showJoinGroup = true }) {
                Icon(Icons.Default.Link, contentDescription = "Join a group")
            }
            IconButton(onClick = { showCreateGroup = true }, modifier = Modifier.testTag("create_group_button")) {
                Icon(Icons.Default.Add, contentDescription = "Create a group")
            }
        }
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("people_screen"),
        contentPadding = padding
    ) {

        item {
            SharingCard(
                displayName = profile.displayName,
                colorArgb = profile.colorArgb,
                sharingEnabled = profile.sharingEnabled,
                onToggle = { enabled ->
                    viewModel.updateProfileAndSharing(
                        profile.displayName,
                        profile.colorArgb,
                        enabled
                    )
                },
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium)
            )
        }

        item {
            SectionHeader("Where they are now")
        }

        if (peers.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Groups,
                    title = "Nobody is sharing yet",
                    body = "Create a group and share its invite code. Whoever joins with that code " +
                        "can see your encrypted location, and you can see theirs."
                )
            }
        } else {
            items(peers, key = { it.userId }) { peer ->
                PeerCard(
                    peer = peer,
                    onOpenOnMap = { viewModel.focusOnPeer(peer) },
                    onRoute = { viewModel.requestRouteTo(peer.latitude, peer.longitude) },
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 4.dp)
                )
            }
        }

        item {
            SectionHeader("Groups")
        }

        if (groups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Groups,
                    title = "No groups yet",
                    body = "Groups keep sharing separate, so family and friends do not all see the " +
                        "same thing."
                )
            }
        } else {
            items(groups, key = { it.id }) { group ->
                GroupCard(
                    name = group.name,
                    category = group.category,
                    memberCount = group.memberCount,
                    shareMyLocation = group.shareMyLocation,
                    onToggleSharing = { enabled -> viewModel.toggleGroupSharing(group.id, enabled) },
                    onInvite = {
                        inviteForGroupId = group.id
                        inviteForGroupName = group.name
                        invitePayload = null
                    },
                    onLeave = { viewModel.leaveGroup(group.id) },
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 4.dp)
                )
            }
        }
    }
}

    if (showCreateGroup) {
        CreateGroupDialog(
            onDismiss = { showCreateGroup = false },
            onCreate = { name, category ->
                viewModel.createGroup(name, category)
                showCreateGroup = false
            }
        )
    }

    if (showJoinGroup) {
        JoinGroupDialog(
            onDismiss = { showJoinGroup = false },
            onJoin = { code ->
                viewModel.joinGroupWithInvite(code)
                showJoinGroup = false
            }
        )
    }

    inviteForGroupId?.let { groupId ->
        InviteDialog(
            groupName = inviteForGroupName,
            payload = invitePayload,
            onDismiss = { inviteForGroupId = null },
            onGenerate = {
                scope.launch { invitePayload = viewModel.generateInviteForGroup(groupId) }
            },
            onCopy = { payload ->
                copyToClipboard(context, payload)
                viewModel.postToast("Invite code copied")
            }
        )
    }
}

@Composable
private fun SharingCard(
    displayName: String,
    colorArgb: Long,
    sharingEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sharing_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = displayName, colorArgb = colorArgb)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (sharingEnabled) {
                        "Sharing your location, end to end encrypted"
                    } else {
                        "Sharing paused. Nobody can see where you are."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = sharingEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("sharing_switch")
            )
        }
    }
}

@Composable
private fun PeerCard(
    peer: PeerLocationState,
    onOpenOnMap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMs = System.currentTimeMillis()
    val stale = peer.isStale(nowMs)
    Card(
        onClick = onOpenOnMap,
        modifier = modifier
            .fillMaxWidth()
            .testTag("peer_card_${peer.userId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(
                    name = peer.displayName,
                    colorArgb = peer.colorArgb,
                    size = 40.dp,
                    // Green ring reads as live, grey as stale, before any text is parsed.
                    ringColor = if (stale) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        Color(0xFF00C853)
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "±${peer.accuracyMeters.toInt()} m · ${peer.sourceSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FreshnessChip(
                    label = if (stale) "STALE" else "LIVE",
                    tone = if (stale) FreshnessTone.STALE else FreshnessTone.LIVE
                )
            }

            Spacer(Modifier.height(10.dp))
            DetailRow("Last update", formatAge(nowMs - peer.timestampMs) + " ago")
            DetailRow("Activity", "${peer.activityState.label} · ${"%.1f".format(peer.speedMps)} m/s")
            peer.batteryPercent?.let { DetailRow("Battery", "$it%") }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenOnMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Show on map")
                }
                OutlinedButton(onClick = onRoute, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Route")
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    name: String,
    category: GroupCategory,
    memberCount: Int,
    shareMyLocation: Boolean,
    onToggleSharing: (Boolean) -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmLeave by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${category.label} · $memberCount member${if (memberCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = shareMyLocation,
                    onCheckedChange = onToggleSharing,
                    modifier = Modifier.testTag("group_share_switch_$name")
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onInvite,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("invite_button_$name")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Invite")
                }
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Leave")
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave \"$name\"?") },
            text = {
                Text(
                    "Leaving deletes this device's copy of the group key. Anything already " +
                        "shared with the group stays shared."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLeave()
                    confirmLeave = false
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, GroupCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(GroupCategory.CUSTOM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(LuogoSpacing.medium))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupCategory.entries.forEach { candidate ->
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), category) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group") },
        text = {
            Column {
                Text(
                    "Paste the invite code someone shared with you. It carries the group's " +
                        "encryption key, so only someone who has it can read the group's location.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(LuogoSpacing.medium))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite code") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_group_code_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code.trim()) },
                enabled = code.isNotBlank()
            ) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InviteDialog(
    groupName: String,
    payload: String?,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to $groupName") },
        text = {
            Column {
                if (payload == null) {
                    Text(
                        "An invite code contains this group's encryption key. Anyone who has it " +
                            "can decrypt that group's shared locations, so only send it to people " +
                            "you trust.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text("Invite code", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("invite_payload")
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = { onCopy(payload) },
                        label = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                }
            }
        },
        confirmButton = {
            if (payload == null) {
                Button(onClick = onGenerate) { Text("Create code") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (payload != null) {
                TextButton(onClick = onGenerate) { Text("Create a new code") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Luogo invite", text))
}