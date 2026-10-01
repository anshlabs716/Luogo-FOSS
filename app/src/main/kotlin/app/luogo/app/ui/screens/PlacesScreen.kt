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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.PlaceCategory
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.ui.viewmodel.LuogoViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen(viewModel: LuogoViewModel) {
    val places by viewModel.places.collectAsState()
    val myFix by viewModel.fusedLocation.collectAsState()
    var showAddPlaceDialog by remember { mutableStateOf(false) }

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
                        text = "Saved Places & Geofences",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Configure Home, Work, School, or custom geofences with arrival and departure notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddPlaceDialog = true },
                    modifier = Modifier.testTag("add_place_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Place")
                }
            }
        }

        items(places, key = { it.id }) { place ->
            SavedPlaceCard(
                place = place,
                onToggleEnabled = { enabled -> viewModel.togglePlaceEnabled(place.id, enabled) },
                onViewOnMap = { viewModel.focusOnPlace(place) },
                onNavigate = { viewModel.requestRouteTo(place.latitude, place.longitude) },
                onDelete = { viewModel.deletePlace(place.id) }
            )
        }
    }

    if (showAddPlaceDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(PlaceCategory.CUSTOM) }
        var radiusMeters by remember { mutableFloatStateOf(150f) }
        var notifyArrival by remember { mutableStateOf(true) }
        var notifyDeparture by remember { mutableStateOf(true) }
        val defaultLat = myFix?.latitude ?: 37.7749
        val defaultLon = myFix?.longitude ?: -122.4194
        var latInput by remember { mutableStateOf(String.format("%.5f", defaultLat)) }
        var lonInput by remember { mutableStateOf(String.format("%.5f", defaultLon)) }

        AlertDialog(
            onDismissRequest = { showAddPlaceDialog = false },
            title = { Text("Add Saved Place Geofence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Place Name (e.g. Home, Gym, School)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_place_name_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaceCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = {
                                    category = cat
                                    if (name.isBlank()) name = cat.label
                                },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { latInput = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lonInput,
                            onValueChange = { lonInput = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Geofence Radius: ${radiusMeters.toInt()} meters", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 40f..1000f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = notifyArrival, onCheckedChange = { notifyArrival = it })
                        Text("Notify on Arrival")
                        Spacer(modifier = Modifier.width(12.dp))
                        Checkbox(checked = notifyDeparture, onCheckedChange = { notifyDeparture = it })
                        Text("Notify on Departure")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = latInput.toDoubleOrNull() ?: defaultLat
                        val lon = lonInput.toDoubleOrNull() ?: defaultLon
                        viewModel.addSavedPlace(
                            name = name,
                            category = category,
                            lat = lat,
                            lon = lon,
                            radiusMeters = radiusMeters,
                            notifyArrival = notifyArrival,
                            notifyDeparture = notifyDeparture
                        )
                        showAddPlaceDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_place_button")
                ) {
                    Text("Save Place")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlaceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedPlaceCard(
    place: SavedPlace,
    onToggleEnabled: (Boolean) -> Unit,
    onViewOnMap: () -> Unit,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (place.category) {
        PlaceCategory.HOME -> Icons.Default.Home
        PlaceCategory.WORK -> Icons.Default.Work
        PlaceCategory.SCHOOL -> Icons.Default.School
        PlaceCategory.CUSTOM -> Icons.Default.Place
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${place.category.label} · ${place.radiusMeters.toInt()}m radius · ${String.format("%.4f, %.4f", place.latitude, place.longitude)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = place.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (place.currentlyInside) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = buildString {
                            append(if (place.currentlyInside) "INSIDE GEOFENCE" else "OUTSIDE")
                            if (place.notifyOnArrival) append(" · Arrival Alert")
                            if (place.notifyOnDeparture) append(" · Departure Alert")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row {
                    IconButton(onClick = onViewOnMap) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Show place on map")
                    }
                    IconButton(onClick = onNavigate) {
                        Icon(Icons.Default.Directions, contentDescription = "Route to place")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete place")
                    }
                }
            }
        }
    }
}
