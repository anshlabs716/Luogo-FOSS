package app.luogo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.OfflineRegionStatus
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RoutingMode
import app.luogo.app.ui.components.DetailRow
import app.luogo.app.ui.components.LocationMap
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.theme.MapChrome
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.delay

/**
 * The map: the screen the app opens on, and the one that has to be immediately useful.
 *
 * Layout is deliberately sparse. The map owns the whole screen; controls float above it in
 * small groups so nothing competes with the geography. Detail appears in a bottom sheet, not
 * as a panel that permanently covers a third of the view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: LuogoViewModel,
    onRequestLocationPermission: () -> Unit
) {
    val center by viewModel.cameraCenter.collectAsState()
    val zoom by viewModel.cameraZoom.collectAsState()
    val bearing by viewModel.cameraBearing.collectAsState()
    val mapStyle by viewModel.activeMapStyle.collectAsState()
    val myLocation by viewModel.fusedLocation.collectAsState()
    val myProfile by viewModel.userProfile.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val items by viewModel.items.collectAsState()
    val places by viewModel.places.collectAsState()
    val route by viewModel.activeRoute.collectAsState()
    val routingMode by viewModel.selectedRoutingMode.collectAsState()
    val showPeople by viewModel.showPeopleOnMap.collectAsState()
    val showItems by viewModel.showItemsOnMap.collectAsState()
    val showPlaces by viewModel.showPlacesOnMap.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPeer by viewModel.selectedPeer.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()
    val customSatelliteUrl by viewModel.customSatelliteUrl.collectAsState()
    val followMe by viewModel.followMyLocation.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // One shared clock for every relative-time label, so they all tick together instead of
    // each keeping its own polling loop.
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LocationMap(
            centerLat = center.first,
            centerLon = center.second,
            zoom = zoom,
            bearingDegrees = bearing,
            mapStyle = mapStyle,
            customSatelliteUrl = customSatelliteUrl,
            myLocation = myLocation,
            myColorArgb = myProfile.colorArgb,
            peers = if (showPeople) peers else emptyList(),
            items = if (showItems) items else emptyList(),
            places = if (showPlaces) places else emptyList(),
            route = route,
            followMyLocation = followMe,
            nowMs = nowMs,
            onUserGesture = { viewModel.setFollowMyLocation(false) },
            onMapTapped = { viewModel.dismissBottomSheetSelections() },
            onPeerTapped = { userId ->
                peers.firstOrNull { it.userId == userId }?.let(viewModel::focusOnPeer)
            },
            onItemTapped = { itemId ->
                items.firstOrNull { it.id == itemId }?.let(viewModel::focusOnItem)
            },
            onPlaceTapped = { placeId ->
                places.firstOrNull { it.id == placeId }?.let(viewModel::focusOnPlace)
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("location_map")
        )

        // -------------------------------------------------------- top cluster
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = LuogoSpacing.medium, vertical = LuogoSpacing.small),
            verticalArrangement = Arrangement.spacedBy(LuogoSpacing.small)
        ) {
            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = {
                        viewModel.setSearchQuery("")
                        searchOpen = false
                    },
                    results = SearchResults(
                        peers = peers,
                        items = items,
                        places = places,
                        nowMs = nowMs
                    ),
                    onPickPeer = {
                        viewModel.setSearchQuery("")
                        searchOpen = false
                        viewModel.focusOnPeer(it)
                    },
                    onPickItem = {
                        viewModel.setSearchQuery("")
                        searchOpen = false
                        viewModel.focusOnItem(it)
                    },
                    onPickPlace = {
                        viewModel.setSearchQuery("")
                        searchOpen = false
                        viewModel.focusOnPlace(it)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LuogoSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingPill(
                    onClick = { searchOpen = !searchOpen },
                    icon = Icons.Default.Search,
                    contentDescription = "Search people, items and places",
                    active = searchOpen,
                    modifier = Modifier.testTag("map_search_button")
                )

                LocationStatusPill(
                    accuracyMeters = myLocation?.horizontalAccuracyMeters,
                    sourceSummary = myLocation?.sourceSummary,
                    ageMs = myLocation?.let { nowMs - it.timestampMs },
                    activityLabel = myLocation?.activityState?.label,
                    onClick = onRequestLocationPermission,
                    modifier = Modifier.weight(1f, fill = false)
                )

                FloatingPill(
                    onClick = { showStyleSheet = true },
                    icon = Icons.Default.Layers,
                    contentDescription = "Map style and satellite imagery",
                    modifier = Modifier.testTag("map_style_button")
                )
            }
        }

        // ------------------------------------------------------ bottom cluster
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = LuogoSpacing.medium, bottom = LuogoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LuogoSpacing.small),
            horizontalAlignment = Alignment.End
        ) {
            FloatingPill(
                onClick = {
                    viewModel.panAndZoomCamera(center.first, center.second, zoom, disableFollow = false)
                    viewModel.setBearing(0f)
                },
                icon = Icons.Default.Explore,
                contentDescription = "Reset bearing to north",
                modifier = Modifier.testTag("map_compass_button")
            )

            FloatingPill(
                onClick = {
                    onRequestLocationPermission()
                    viewModel.recenterOnMe()
                },
                icon = Icons.Default.MyLocation,
                contentDescription = "Centre on my location",
                emphasised = true,
                modifier = Modifier.testTag("recenter_location_button")
            )
        }

        // Layer toggles, bottom-start, compact chips.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = LuogoSpacing.medium, bottom = LuogoSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(LuogoSpacing.xSmall)
        ) {
            MiniChip(
                text = "People ${peers.size}",
                selected = showPeople,
                onClick = viewModel::togglePeopleVisibility,
                tag = "toggle_people_chip"
            )
            MiniChip(
                text = "Items ${items.size}",
                selected = showItems,
                onClick = viewModel::toggleItemsVisibility,
                tag = "toggle_items_chip"
            )
            MiniChip(
                text = "Places ${places.size}",
                selected = showPlaces,
                onClick = viewModel::togglePlacesVisibility,
                tag = "toggle_places_chip"
            )
        }

        // Route summary, only when one exists.
        route?.let { active ->
            RouteSummaryBar(
                mode = active.mode,
                distanceMeters = active.distanceMeters,
                durationSeconds = active.durationSeconds,
                providerName = active.providerName,
                statusNotice = active.statusNotice,
                isLive = active.isLiveBackendRoute,
                routingMode = routingMode,
                onChangeMode = { mode ->
                    val destination = active.polyline.lastOrNull()
                    if (destination != null) {
                        viewModel.requestRouteTo(destination.first, destination.second, mode)
                    }
                },
                onDismiss = viewModel::clearActiveRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 112.dp)
            )
        }
    }

    selectedPeer?.let { peer ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissBottomSheetSelections,
            sheetState = rememberModalBottomSheetState()
        ) {
            PersonSheet(
                peer = peer,
                nowMs = nowMs,
                onRoute = {
                    viewModel.dismissBottomSheetSelections()
                    viewModel.requestRouteTo(peer.latitude, peer.longitude, routingMode)
                }
            )
        }
    }

    selectedItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissBottomSheetSelections,
            sheetState = rememberModalBottomSheetState()
        ) {
            ItemSheet(item = item, nowMs = nowMs, onDismiss = viewModel::dismissBottomSheetSelections)
        }
    }

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            MapStyleSheet(
                current = mapStyle,
                satelliteConfigured = customSatelliteUrl.isNotBlank(),
                onSelect = {
                    viewModel.setMapStyle(it)
                    showStyleSheet = false
                },
            )
        }
    }

}

private const val CLOCK_TICK_MS = 1_000L

// ----------------------------------------------------------------- components

@Composable
private fun FloatingPill(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = false,
    emphasised: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Map chrome is light in both themes. A dark circle on a map reads as a hole in it.
    val background = when {
        emphasised -> MapChrome.emphasisedContainer
        active -> MapChrome.activeContainer
        else -> MapChrome.container
    }
    val tint = when {
        emphasised -> MapChrome.onEmphasisedContainer
        active -> MapChrome.activeOnContainer
        else -> MapChrome.onContainer
    }
    Surface(
        shape = CircleShape,
        color = background,
        shadowElevation = if (emphasised) 8.dp else 4.dp,
        modifier = modifier.size(48.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

/**
 * The live fix readout.
 *
 * Shows accuracy, source and age together, because any one of them alone can mislead. Before
 * the first fix it says so rather than showing a placeholder number.
 */
@Composable
private fun LocationStatusPill(
    accuracyMeters: Float?,
    sourceSummary: String?,
    ageMs: Long?,
    activityLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MapChrome.container,
        shadowElevation = 4.dp,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (accuracyMeters == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Getting a fix",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "Tap to grant location permission",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${accuracyMeters.toInt()} m · ${sourceSummary.orEmpty()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("location_quality_banner")
                )
                Text(
                    text = buildString {
                        append("Updated ${formatAge(ageMs ?: 0L)}")
                        activityLabel?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MapChrome.onContainer
                )
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.testTag(tag)
    )
}

private data class SearchResults(
    val peers: List<PeerLocationState>,
    val items: List<RegisteredItem>,
    val places: List<app.luogo.app.domain.model.SavedPlace>,
    val nowMs: Long
)

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: SearchResults,
    onPickPeer: (PeerLocationState) -> Unit,
    onPickItem: (RegisteredItem) -> Unit,
    onPickPlace: (app.luogo.app.domain.model.SavedPlace) -> Unit
) {
    val matchingPeers = remember(query, results.peers) {
        if (query.isBlank()) emptyList() else results.peers.filter { it.displayName.contains(query, true) }
    }
    val matchingItems = remember(query, results.items) {
        if (query.isBlank()) emptyList() else results.items.filter {
            it.friendlyName.contains(query, true) || it.itemType.label.contains(query, true)
        }
    }
    val matchingPlaces = remember(query, results.places) {
        if (query.isBlank()) emptyList() else results.places.filter { it.name.contains(query, true) }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MapChrome.container,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search people, items, places") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_search_input")
            )

            if (query.isNotBlank()) {
                val total = matchingPeers.size + matchingItems.size + matchingPlaces.size
                if (total == 0) {
                    Text(
                        text = "Nothing matches \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(matchingPeers, key = { "p-${it.userId}" }) { peer ->
                            SearchRow(
                                title = peer.displayName,
                                subtitle = "Person · ±${peer.accuracyMeters.toInt()} m",
                                onClick = { onPickPeer(peer) }
                            )
                        }
                        items(matchingItems, key = { "i-${it.id}" }) { item ->
                            SearchRow(
                                title = item.friendlyName,
                                subtitle = "${item.itemType.label} · ${item.presenceStatus(results.nowMs).label}",
                                onClick = { onPickItem(item) }
                            )
                        }
                        items(matchingPlaces, key = { "pl-${it.id}" }) { place ->
                            SearchRow(
                                title = place.name,
                                subtitle = "Place · ${place.radiusMeters.toInt()} m geofence",
                                onClick = { onPickPlace(place) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MapStyleSheet(
    current: MapStyleOption,
    satelliteConfigured: Boolean,
    onSelect: (MapStyleOption) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Map style", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        for (option in MapStyleOption.entries) {
            val selected = option == current
            Card(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .testTag("style_${option.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (option) {
                            MapStyleOption.STANDARD_OSM -> Icons.Default.NearMe
                            MapStyleOption.PROTOMAPS_DARK -> Icons.Default.WbTwilight
                            MapStyleOption.TERRAIN_TOPO -> Icons.Default.Terrain
                            MapStyleOption.SATELLITE_IMAGERY -> Icons.Default.Layers
                        },
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.titleMedium)
                        Text(option.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text(
                            option.attribution,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (current == MapStyleOption.SATELLITE_IMAGERY && satelliteConfigured) {
            Text(
                text = "Satellite imagery is using your configured source.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

    }
}

@Composable
private fun RouteSummaryBar(
    mode: RoutingMode,
    distanceMeters: Double,
    durationSeconds: Long,
    providerName: String,
    statusNotice: String?,
    isLive: Boolean,
    routingMode: RoutingMode,
    onChangeMode: (RoutingMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MapChrome.container,
        shadowElevation = 8.dp,
        modifier = modifier.padding(horizontal = LuogoSpacing.medium)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${mode.label} · ${"%.1f".format(distanceMeters / 1000.0)} km · " +
                            "${(durationSeconds / 60).coerceAtLeast(1)} min",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss route")
                }
            }

            // A route that is not a real backend result says so, rather than pretending.
            if (!isLive && statusNotice != null) {
                Text(
                    text = statusNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (candidate in RoutingMode.entries) {
                    FilterChip(
                        selected = routingMode == candidate,
                        onClick = { onChangeMode(candidate) },
                        label = { Text(candidate.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonSheet(
    peer: PeerLocationState,
    nowMs: Long,
    onRoute: () -> Unit
) {
    val stale = peer.isStale(nowMs)
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(peer.colorArgb.toInt() or 0xFF000000.toInt())),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.displayName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(peer.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (stale) {
                        "Stale · last seen ${formatAge(nowMs - peer.timestampMs)} ago"
                    } else {
                        "Live · updated ${formatAge(nowMs - peer.timestampMs)} ago"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stale) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        DetailRow("Accuracy", "±${peer.accuracyMeters.toInt()} m (${peer.sourceSummary})")
        DetailRow("Activity", "${peer.activityState.label} · ${"%.1f".format(peer.speedMps)} m/s")
        peer.batteryPercent?.let { DetailRow("Battery", "$it%${if (peer.isCharging) " · charging" else ""}") }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = onRoute, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Directions, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Route here")
            }
        }
    }
}

@Composable
private fun ItemSheet(
    item: RegisteredItem,
    nowMs: Long,
    onDismiss: () -> Unit
) {
    val presence = item.presenceStatus(nowMs)
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(item.friendlyName, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${item.itemType.label} · ${presence.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))
        item.lastSeenTimestampMs?.let {
            DetailRow("Last seen", "${formatAge(nowMs - it)} ago")
        } ?: DetailRow("Last seen", "Never seen")
        item.lastSeenAccuracyMeters?.let {
            DetailRow("Accuracy", "±${it.toInt()} m")
        }
        item.batteryPercent?.let { DetailRow("Battery", "$it%") }
        item.rssiDbm?.let { DetailRow("Signal", item.signalQualityDescription()) }

        if (item.uwbDistanceMeters != null) {
            DetailRow("UWB range", "${"%.2f".format(item.uwbDistanceMeters)} m")
        }
        DetailRow("Source", item.lastSeenSource)

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

/** Compact relative time. Anything under a second reads as "just now" rather than "0 s". */
internal fun formatAge(ageMs: Long): String = when {
    ageMs < 1_000L -> "just now"
    ageMs < 60_000L -> "${ageMs / 1000L}s"
    ageMs < 3_600_000L -> "${ageMs / 60_000L} min"
    ageMs < 86_400_000L -> "${ageMs / 3_600_000L} h"
    else -> "${ageMs / 86_400_000L} d"
}