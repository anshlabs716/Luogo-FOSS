package app.luogo.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(viewModel: LuogoViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPoints by viewModel.historyPoints.collectAsState()
    val places by viewModel.places.collectAsState()
    val trips by viewModel.detectedTrips.collectAsState()

    var selectedDaySpan by remember { mutableIntStateOf(1) } // 1 = Today, 7 = 7 Days, 30 = 30 Days
    var includeItemsFilter by remember { mutableStateOf(true) }
    var playbackFraction by remember { mutableFloatStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val startCutoff = now - selectedDaySpan * 86_400_000L

    val filteredPoints = remember(allPoints, selectedDaySpan, includeItemsFilter) {
        allPoints.filter { pt ->
            pt.timestampMs >= startCutoff && (includeItemsFilter || !pt.isItem)
        }
    }

    val totalDistanceMeters = remember(filteredPoints) {
        viewModel.repository.geofenceEngine.calculateTotalDistanceMeters(filteredPoints)
    }

    val timeAtPlacesMinutes = remember(filteredPoints, places) {
        viewModel.repository.geofenceEngine.calculateTimeSpentAtPlacesMinutes(filteredPoints, places)
    }

    val currentPlaybackPoint = remember(filteredPoints, playbackFraction) {
        viewModel.repository.geofenceEngine.samplePlaybackPoint(filteredPoints, playbackFraction)
    }

    LaunchedEffect(isPlaying, filteredPoints.size) {
        if (isPlaying && filteredPoints.size >= 2) {
            if (playbackFraction >= 0.99f) playbackFraction = 0f
            while (isPlaying && playbackFraction < 1f) {
                delay(80L)
                playbackFraction = (playbackFraction + 0.02f).coerceAtMost(1f)
                if (playbackFraction >= 1f) {
                    isPlaying = false
                }
            }
        }
    }

    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Location History & Route Playback", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Stored locally on-device with automatic retention cleanup. Export to standard GPX or delete at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Date & Subject Filters
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedDaySpan == 1,
                    onClick = { selectedDaySpan = 1 },
                    label = { Text("Last 24 Hours") }
                )
                FilterChip(
                    selected = selectedDaySpan == 7,
                    onClick = { selectedDaySpan = 7 },
                    label = { Text("Last 7 Days") }
                )
                FilterChip(
                    selected = selectedDaySpan == 30,
                    onClick = { selectedDaySpan = 30 },
                    label = { Text("Last 30 Days") }
                )
                FilterChip(
                    selected = includeItemsFilter,
                    onClick = { includeItemsFilter = !includeItemsFilter },
                    label = { Text("Include Item Sightings") }
                )
            }
        }

        // Daily Summary & Time Spent at Places
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Distance Travelled: ${String.format("%.2f km", totalDistanceMeters / 1000.0)} (${filteredPoints.size} fixes)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (timeAtPlacesMinutes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Time Spent at Saved Places: " +
                                timeAtPlacesMinutes.entries.joinToString(" · ") { "${it.key}: ${it.value} min" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Interactive Route Visualization & Playback Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Interactive Route Playback", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val routeColor = MaterialTheme.colorScheme.primary
                    val activeMarkerColor = MaterialTheme.colorScheme.tertiary

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    ) {
                        drawRect(Color(0xFF10191D))
                        if (filteredPoints.size >= 2) {
                            val minLat = filteredPoints.minOf { it.latitude }
                            val maxLat = filteredPoints.maxOf { it.latitude }
                            val minLon = filteredPoints.minOf { it.longitude }
                            val maxLon = filteredPoints.maxOf { it.longitude }
                            val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
                            val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)

                            fun toCanvas(lat: Double, lon: Double): Offset {
                                val nx = ((lon - minLon) / lonSpan).toFloat().coerceIn(0f, 1f)
                                val ny = 1f - ((lat - minLat) / latSpan).toFloat().coerceIn(0f, 1f)
                                return Offset(
                                    x = 24f + nx * (size.width - 48f),
                                    y = 24f + ny * (size.height - 48f)
                                )
                            }

                            val path = Path()
                            filteredPoints.forEachIndexed { idx, pt ->
                                val o = toCanvas(pt.latitude, pt.longitude)
                                if (idx == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                            }
                            drawPath(
                                path = path,
                                color = routeColor,
                                style = Stroke(width = 6f, cap = StrokeCap.Round)
                            )

                            currentPlaybackPoint?.let { playPt ->
                                val playOffset = toCanvas(playPt.latitude, playPt.longitude)
                                drawCircle(color = Color.White, radius = 14f, center = playOffset)
                                drawCircle(color = activeMarkerColor, radius = 10f, center = playOffset)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    currentPlaybackPoint?.let { pt ->
                        Text(
                            text = "${pt.subjectName} · ${timeFormat.format(Date(pt.timestampMs))} · ${pt.activityState.label} (${String.format("%.1f m/s", pt.speedMps)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Coords: ${String.format("%.5f, %.5f", pt.latitude, pt.longitude)} · ±${pt.accuracyMeters.toInt()}m (${pt.sourceSummary})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.testTag("route_playback_button")
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or pause route playback"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = playbackFraction,
                            onValueChange = {
                                isPlaying = false
                                playbackFraction = it
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Export & Delete Controls
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val file = viewModel.exportHistoryGpx(filteredPoints)
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/gpx+xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export GPX History"))
                            } catch (_: Exception) {
                                viewModel.postToast("Saved GPX to ${file.name}")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_gpx_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export GPX")
                }

                OutlinedButton(
                    onClick = { viewModel.deleteHistoryRange(startCutoff, now + 60_000L) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Range")
                }

                OutlinedButton(
                    onClick = { viewModel.deleteAllHistory() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }

        // Detected Trips List
        item {
            Text("Detected Trips (${trips.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(trips, key = { it.tripId }) { trip ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${trip.primaryActivity.label} Trip · ${String.format("%.2f km", trip.distanceMeters / 1000.0)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${timeFormat.format(Date(trip.startTimeMs))} → ${timeFormat.format(Date(trip.endTimeMs))} (${trip.pointCount} fixes)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Avg Speed: ${String.format("%.1f m/s", trip.averageSpeedMps)} · Max: ${String.format("%.1f m/s", trip.maxSpeedMps)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
