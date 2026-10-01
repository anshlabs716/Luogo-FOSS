package app.luogo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.OfflineRegionStatus
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RoutingMode
import app.luogo.app.ui.components.InteractiveMapCanvas
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val activeRoute by viewModel.activeRoute.collectAsState()
    val routingMode by viewModel.selectedRoutingMode.collectAsState()
    val showPeople by viewModel.showPeopleOnMap.collectAsState()
    val showItems by viewModel.showItemsOnMap.collectAsState()
    val showPlaces by viewModel.showPlacesOnMap.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPeer by viewModel.selectedPeer.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()

    var showStyleSheet by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var newOfflineRegionName by remember { mutableStateOf("Current Map Area") }
    var nowClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            nowClockMs = System.currentTimeMillis()
        }
    }

    val matchingPeers = remember(searchQuery, peers) {
        if (searchQuery.isBlank()) emptyList()
        else peers.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }
    val matchingItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) emptyList()
        else items.filter {
            it.friendlyName.contains(searchQuery, ignoreCase = true) ||
                it.itemType.label.contains(searchQuery, ignoreCase = true)
        }
    }
    val matchingPlaces = remember(searchQuery, places) {
        if (searchQuery.isBlank()) emptyList()
        else places.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        InteractiveMapCanvas(
            centerLat = center.first,
            centerLon = center.second,
            zoom = zoom,
            bearingDegrees = bearing,
            mapStyle = mapStyle,
            myLocation = myLocation,
            myProfile = myProfile,
            peers = peers,
            items = items,
            places = places,
            activeRoute = activeRoute,
            showPeople = showPeople,
            showItems = showItems,
            showPlaces = showPlaces,
            fetchTile = { style, z, x, y -> viewModel.fetchMapTileBitmap(style, z, x, y) },
            onPanZoomRotate = { nLat, nLon, nZoom, nBearing ->
                viewModel.panAndZoomCamera(nLat, nLon, nZoom, disableFollow = true)
                viewModel.setBearing(nBearing)
            },
            onPeerClicked = { viewModel.focusOnPeer(it) },
            onItemClicked = { viewModel.focusOnItem(it) },
            onPlaceClicked = { viewModel.focusOnPlace(it) }
        )

        // Top Search Bar + Live Location Quality & Sensor Fusion Banner
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search people, items (e.g. Pixel Buds 3), places...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_search_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Live Location Quality Banner ("12 m accuracy · GNSS + Wi-Fi · Updated 1.4 s ago")
                    val fix = myLocation
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fix?.formattedQualityBanner(nowClockMs)
                                    ?: "Acquiring multi-source GNSS + Wi-Fi fusion fix...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("location_quality_banner")
                            )
                            Text(
                                text = buildString {
                                    append("Activity: ${fix?.activityState?.label ?: "Detecting"}")
                                    append(" · Target: ~2.0s moving")
                                    append(" · Sharing: ${if (myProfile.sharingEnabled) "LIVE E2EE" else "PAUSED"}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {
                                onRequestLocationPermission()
                                viewModel.sendOneTimeLocationNow()
                            },
                            label = { Text("Send Now") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send location now",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("send_location_now_chip")
                        )
                    }

                    // Layer visibility chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        FilterChip(
                            selected = showPeople,
                            onClick = { viewModel.togglePeopleVisibility() },
                            label = { Text("People (${peers.size})") },
                            leadingIcon = {
                                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("toggle_people_chip")
                        )
                        FilterChip(
                            selected = showItems,
                            onClick = { viewModel.toggleItemsVisibility() },
                            label = { Text("Items (${items.size})") },
                            leadingIcon = {
                                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("toggle_items_chip")
                        )
                        FilterChip(
                            selected = mapStyle == MapStyleOption.SATELLITE_IMAGERY,
                            onClick = {
                                val next = if (mapStyle == MapStyleOption.SATELLITE_IMAGERY) {
                                    MapStyleOption.STANDARD_OSM
                                } else {
                                    MapStyleOption.SATELLITE_IMAGERY
                                }
                                viewModel.setMapStyle(next)
                            },
                            label = { Text("Satellite") },
                            leadingIcon = {
                                Icon(Icons.Default.SatelliteAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("toggle_satellite_chip")
                        )
                    }
                }
            }

            // Instant Search Results Dropdown across People, Items, and Places
            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (matchingPeers.isEmpty() && matchingItems.isEmpty() && matchingPlaces.isEmpty()) {
                            Text("No matching people, items, or places found.", style = MaterialTheme.typography.bodyMedium)
                        }
                        matchingPeers.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSearchQuery("")
                                        viewModel.focusOnPeer(p)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(p.displayName, fontWeight = FontWeight.SemiBold)
                                    Text("Person · ±${p.accuracyMeters.toInt()}m · ${p.sourceSummary}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        matchingItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSearchQuery("")
                                        viewModel.focusOnItem(item)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(item.friendlyName, fontWeight = FontWeight.SemiBold)
                                    Text("${item.itemType.label} · ${item.presenceStatus().label}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        matchingPlaces.forEach { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSearchQuery("")
                                        viewModel.focusOnPlace(place)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(place.name, fontWeight = FontWeight.SemiBold)
                                    Text("Saved Place · ${place.radiusMeters.toInt()}m geofence", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Active Route Card (Walking / Cycling / Driving)
            activeRoute?.let { route ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${route.mode.label} Route · ${String.format("%.2f km", route.distanceMeters / 1000.0)} (~${(route.durationSeconds / 60).coerceAtLeast(1)} min)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = route.providerName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                route.statusNotice?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.clearActiveRoute() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close route")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoutingMode.entries.forEach { m ->
                                FilterChip(
                                    selected = routingMode == m,
                                    onClick = {
                                        val dest = route.polyline.lastOrNull()
                                        if (dest != null) {
                                            viewModel.requestRouteTo(dest.first, dest.second, m)
                                        }
                                    },
                                    label = { Text(m.label) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right-side Map Controls (Compass, Map Styles, Offline Maps, Zoom, Current Location)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 18.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { viewModel.setBearing(0f) },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("map_compass_button")
            ) {
                Icon(Icons.Default.CompassCalibration, contentDescription = "Reset North Compass")
            }

            SmallFloatingActionButton(
                onClick = { showStyleSheet = true },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("map_style_button")
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Map Styles & Satellite")
            }

            SmallFloatingActionButton(
                onClick = { showOfflineSheet = true },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("offline_maps_button")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Offline Maps")
            }

            FloatingActionButton(
                onClick = {
                    onRequestLocationPermission()
                    viewModel.recenterOnMe()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("recenter_location_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Current Location & Follow")
            }
        }

        // Scale Indicator & Attribution Pill (Bottom-Left)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 18.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = "Zoom ${String.format("%.1f", zoom)} · ${mapStyle.title}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = mapStyle.attribution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Selected Person Detail Bottom Sheet
    selectedPeer?.let { peer ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissBottomSheetSelections() }
        ) {
            PeerDetailSheetContent(
                peer = peer,
                nowMs = nowClockMs,
                onNavigate = {
                    viewModel.dismissBottomSheetSelections()
                    viewModel.requestRouteTo(peer.latitude, peer.longitude, routingMode)
                }
            )
        }
    }

    // Selected Item Detail Bottom Sheet (Always showing friendly human-readable name)
    selectedItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissBottomSheetSelections() }
        ) {
            ItemQuickSheetContent(
                item = item,
                onRing = { viewModel.triggerItemRing(item.id, !item.isRinging) },
                onFindNearby = {
                    viewModel.dismissBottomSheetSelections()
                    viewModel.openNearbyFinding(item)
                },
                onRoute = {
                    val lat = item.lastSeenLatitude
                    val lon = item.lastSeenLongitude
                    if (lat != null && lon != null) {
                        viewModel.dismissBottomSheetSelections()
                        viewModel.requestRouteTo(lat, lon, routingMode)
                    }
                }
            )
        }
    }

    // Map Style & Satellite Selector Bottom Sheet
    if (showStyleSheet) {
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Map Style & Satellite Imagery", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(12.dp))
                MapStyleOption.entries.forEach { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                viewModel.setMapStyle(option)
                                showStyleSheet = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mapStyle == option) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium)
                            Text(option.subtitle, style = MaterialTheme.typography.bodyMedium)
                            Text(option.attribution, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Offline Maps Manager Bottom Sheet
    if (showOfflineSheet) {
        ModalBottomSheet(onDismissRequest = { showOfflineSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Offline Map Regions", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Download regions for full offline map rendering without network access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newOfflineRegionName,
                        onValueChange = { newOfflineRegionName = it },
                        label = { Text("Region Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.downloadOfflineRegion(newOfflineRegionName)
                        },
                        modifier = Modifier.testTag("download_offline_region_button")
                    ) {
                        Text("Download")
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(offlineRegions, key = { it.id }) { reg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(reg.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${reg.status.label} · ${reg.downloadedTiles}/${reg.totalTiles} tiles · ${String.format("%.1f MB", reg.sizeBytes / 1_000_000.0)}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Row {
                                        if (reg.status == OfflineRegionStatus.DOWNLOADING || reg.status == OfflineRegionStatus.PAUSED) {
                                            IconButton(onClick = { viewModel.pauseOrResumeOfflineRegion(reg) }) {
                                                Icon(
                                                    if (reg.status == OfflineRegionStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Pause or resume offline download"
                                                )
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteOfflineRegion(reg.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete offline region")
                                        }
                                    }
                                }
                                if (reg.progressPercent < 100) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { reg.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PeerDetailSheetContent(
    peer: PeerLocationState,
    nowMs: Long,
    onNavigate: () -> Unit
) {
    val ageSec = ((nowMs - peer.timestampMs) / 1000L).coerceAtLeast(0L)
    val stale = peer.isStale(nowMs)

    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(peer.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (stale) "STALE · Updated ${ageSec / 60}m ago" else "LIVE · Updated ${ageSec}s ago",
                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Accuracy: ±${peer.accuracyMeters.toInt()} m (${peer.sourceSummary})")
        Text("Activity: ${peer.activityState.label} · Speed: ${String.format("%.1f m/s", peer.speedMps)}")
        peer.batteryPercent?.let {
            Text("Battery: $it% ${if (peer.isCharging) "(Charging)" else ""}")
        }
        Text("Coordinates: ${String.format("%.5f, %.5f", peer.latitude, peer.longitude)}")
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Directions, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Route to ${peer.displayName}")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ItemQuickSheetContent(
    item: RegisteredItem,
    onRing: () -> Unit,
    onFindNearby: () -> Unit,
    onRoute: () -> Unit
) {
    val status = item.presenceStatus()
    Column(modifier = Modifier.padding(20.dp)) {
        Text(item.friendlyName, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${item.itemType.label} · Status: ${status.label} · ${item.lastSeenSource}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Signal: ${item.signalQualityDescription()}")
        item.batteryPercent?.let { Text("Battery: $it%") }
        item.lastSeenAccuracyMeters?.let { Text("Location Accuracy: ±${it.toInt()} m") }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onRing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isRinging) "Stop Ring" else "Ring")
            }
            Button(
                onClick = onFindNearby,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Radar, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Find Nearby")
            }
            OutlinedButton(
                onClick = onRoute,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Route")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
