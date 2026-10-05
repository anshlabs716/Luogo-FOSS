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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.ui.components.DetailRow
import app.luogo.app.ui.components.EmptyState
import app.luogo.app.ui.components.FreshnessChip
import app.luogo.app.ui.components.FreshnessTone
import app.luogo.app.ui.components.NoticeCard
import app.luogo.app.ui.components.NoticeTone
import app.luogo.app.ui.components.ScreenHeader
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlin.math.roundToInt

/**
 * Saved places and the geofences around them.
 *
 * The inside/outside state is shown per place because "did the app notice?" is the question
 * people actually have about geofencing.
 */
@Composable
fun PlacesScreen(viewModel: LuogoViewModel) {
    val places by viewModel.places.collectAsState()
    val myFix by viewModel.fusedLocation.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedPlace?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("places_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = LuogoSpacing.extraLarge
        )
    ) {
        item {
            ScreenHeader(
                title = "Places",
                subtitle = if (places.isEmpty()) {
                    "No geofences yet"
                } else {
                    "${places.count { it.currentlyInside }} of ${places.size} geofences active"
                },
                trailing = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add a saved place")
                    }
                }
            )
        }

        if (myFix == null) {
            item {
                NoticeCard(
                    text = "Places are added at your current location. Waiting for a location fix.",
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium),
                    tone = NoticeTone.INFO
                )
            }
        }

        if (places.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Place,
                    title = "No saved places",
                    body = "Add Home, Work or School to get an alert when someone arrives or leaves."
                )
            }
        } else {
            items(places, key = { it.id }) { place ->
                PlaceCard(
                    place = place,
                    onToggle = { viewModel.togglePlaceEnabled(place.id, it) },
                    onShowOnMap = { viewModel.focusOnPlace(place) },
                    onRoute = { viewModel.requestRouteTo(place.latitude, place.longitude) },
                    onDelete = { deleteTarget = place },
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 4.dp)
                )
            }
        }
    }

    if (showAdd) {
        AddPlaceDialog(
            fix = myFix,
            onDismiss = { showAdd = false },
            onSave = { name, category, lat, lon, radius ->
                viewModel.addSavedPlace(name, category, lat, lon, radius, true, true)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { place ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${place.name}?") },
            text = { Text("Arrival and departure alerts for this place will stop.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlaceCard(
    place: SavedPlace,
    onToggle: (Boolean) -> Unit,
    onShowOnMap: () -> Unit,
    onRoute: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("place_card_${place.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = place.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${place.category.label} · ${place.radiusMeters.toInt()} m radius",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FreshnessChip(
                    label = if (place.currentlyInside) "INSIDE" else "OUTSIDE",
                    tone = if (place.currentlyInside) FreshnessTone.LIVE else FreshnessTone.UNKNOWN
                )
            }

            if (!place.enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Geofence is switched off for this place.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Alert on arrival and departure",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = place.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowOnMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Place, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Show")
                }
                OutlinedButton(onClick = onRoute, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Route")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${place.name}")
                }
            }
        }
    }
}

@Composable
private fun AddPlaceDialog(
    fix: FusedLocationFix?,
    onDismiss: () -> Unit,
    onSave: (String, PlaceCategory, Double, Double, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PlaceCategory.CUSTOM) }
    var radius by remember { mutableFloatStateOf(120f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New saved place") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Home") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(LuogoSpacing.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceCategory.entries.forEach { candidate ->
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Spacer(Modifier.height(LuogoSpacing.medium))
                Text(
                    "Geofence radius: ${radius.roundToInt()} m",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..1000f,
                    modifier = Modifier.testTag("radius_slider")
                )
                // A place needs a real position. Saving 0,0 would silently create a geofence
                // in the Gulf of Guinea and look like it worked.
                if (fix == null) {
                    Text(
                        text = "Waiting for a location fix. A place cannot be saved until the " +
                            "app knows where you are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Text(
                        text = "Using your current fix, accurate to ±" +
                            "${fix.horizontalAccuracyMeters.toInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val position = fix
                    if (position != null) {
                        onSave(
                            name.trim(),
                            category,
                            position.latitude,
                            position.longitude,
                            radius
                        )
                    }
                },
                enabled = name.isNotBlank() && fix != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun PlaceCategory.icon(): ImageVector = when (this) {
    PlaceCategory.HOME -> Icons.Default.Home
    PlaceCategory.SCHOOL -> Icons.Default.School
    PlaceCategory.WORK -> Icons.Default.Work
    PlaceCategory.CUSTOM -> Icons.Default.Place
}