package app.luogo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import app.luogo.app.domain.model.LocationHistoryPoint
import app.luogo.app.ui.components.EmptyState
import app.luogo.app.ui.components.NoticeCard
import app.luogo.app.ui.components.NoticeTone
import app.luogo.app.ui.components.ScreenScaffold
import app.luogo.app.ui.components.StatTile
import app.luogo.app.ui.theme.LuogoSpacing
import app.luogo.app.ui.viewmodel.LuogoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Location history: what was recorded, how far, and where time was spent.
 *
 * The route is drawn from real recorded points. Nothing here is generated to fill a shape,
 * and an empty history says so instead of drawing an empty grid.
 */
@Composable
fun HistoryScreen(viewModel: LuogoViewModel) {
    val points by viewModel.historyPoints.collectAsState()
    val places by viewModel.places.collectAsState()
    val trips by viewModel.detectedTrips.collectAsState()
    val scope = rememberCoroutineScope()

    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var includeItems by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val filtered = remember(points, includeItems) {
        points.filter { includeItems || !it.isItem }
    }
    val totalDistance = remember(filtered) {
        viewModel.repository.geofenceEngine.calculateTotalDistanceMeters(filtered)
    }
    val timeAtPlaces = remember(filtered, places) {
        viewModel.repository.geofenceEngine.calculateTimeSpentAtPlacesMinutes(filtered, places)
    }
    val playbackPoint = remember(filtered, playbackFraction) {
        viewModel.repository.geofenceEngine.samplePlaybackPoint(filtered, playbackFraction)
    }

    // Playback advances the shared fraction; the marker interpolates between real fixes
    // rather than jumping between them.
    LaunchedEffect(isPlaying, filtered.size) {
        if (!isPlaying || filtered.size < 2) return@LaunchedEffect
        while (isPlaying && playbackFraction < 1f) {
            delay(PLAYBACK_TICK_MS)
            playbackFraction = (playbackFraction + 1f / playbackPointCount(filtered)).coerceAtMost(1f)
        }
        if (playbackFraction >= 1f) isPlaying = false
    }

    ScreenScaffold(
        title = "History",
        subtitle = if (filtered.isEmpty()) "Nothing recorded yet" else "${filtered.size} recorded point(s)"
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen"),
        contentPadding = padding
    ) {

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "No history yet",
                    body = "History is recorded while sharing is on. Nothing is kept before " +
                        "the app first records a fix, and it is pruned to your retention window."
                )
            }
            item { Spacer(Modifier.height(LuogoSpacing.large)) }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(LuogoSpacing.small)
            ) {
                StatTile(
                    value = "%.2f km".format(totalDistance / 1000.0),
                    label = "Distance",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${filtered.size}",
                    label = "Fixes",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${trips.size}",
                    label = "Trips",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (timeAtPlaces.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Time at saved places", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        timeAtPlaces.forEach { (place, minutes) ->
                            Text(
                                text = "$place · $minutes min",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = LuogoSpacing.medium, vertical = 6.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Route", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    RoutePreview(
                        points = filtered,
                        playbackPoint = playbackPoint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("route_preview")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (playbackFraction >= 1f) playbackFraction = 0f
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier.testTag("playback_button")
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause playback" else "Play route"
                            )
                        }
                        Slider(
                            value = playbackFraction,
                            onValueChange = {
                                playbackFraction = it
                                isPlaying = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    playbackPoint?.let { point ->
                        Text(
                            text = "${timeLabel(point.timestampMs)} · ${point.activityState.label} · " +
                                "${"%.1f".format(point.speedMps)} m/s",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "±${point.accuracyMeters.toInt()} m · ${point.sourceSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { includeItems = !includeItems },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (includeItems) "Hiding items" else "Including items")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.exportHistoryGpx(filtered)
                            viewModel.postToast("Exported ${filtered.size} point(s) as GPX")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = filtered.isNotEmpty()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
                IconButton(onClick = { confirmDeleteAll = true }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete all history")
                }
            }
        }

        item {
            Text(
                text = "Recent fixes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LuogoSpacing.medium, vertical = 8.dp)
            )
        }

        items(filtered.takeLast(MAX_LISTED_POINTS).reversed(), key = { it.id }) { point ->
            HistoryRow(
                point = point,
                onDelete = {
                    viewModel.deleteHistoryRange(point.timestampMs, point.timestampMs)
                }
            )
        }
    }
}

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all history?") },
            text = { Text("Every recorded fix is removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllHistory()
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

private const val PLAYBACK_TICK_MS = 40L
private const val MAX_LISTED_POINTS = 100

/** One tick per recorded point, so playback speed follows track density. */
private fun playbackPointCount(points: List<LocationHistoryPoint>): Float =
    (points.size.toFloat() * PLAYBACK_TICK_MS).coerceAtLeast(1f)

/**
 * Draws the recorded track.
 *
 * Bounding box is computed from the real points, so the shape is proportional and not
 * stretched. When there is a single point the extent is padded rather than dividing by zero.
 */
@Composable
private fun RoutePreview(
    points: List<LocationHistoryPoint>,
    playbackPoint: LocationHistoryPoint?,
    modifier: Modifier = Modifier
) {
    val pathColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

        fun project(lat: Double, lon: Double): Offset {
            val nx = ((lon - minLon) / lonSpan).toFloat().coerceIn(0f, 1f)
            val ny = 1f - ((lat - minLat) / latSpan).toFloat().coerceIn(0f, 1f)
            return Offset(
                x = 20f + nx * (size.width - 40f),
                y = 20f + ny * (size.height - 40f)
            )
        }

        if (points.size == 1) {
            val only = project(points.first().latitude, points.first().longitude)
            drawCircle(gridColor, radius = 6f, center = only)
            return@Canvas
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val offset = project(point.latitude, point.longitude)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, color = pathColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

        playbackPoint?.let { point ->
            val offset = project(point.latitude, point.longitude)
            drawCircle(Color.White, radius = 10f, center = offset)
            drawCircle(pathColor, radius = 7f, center = offset)
        }
    }
}

@Composable
private fun HistoryRow(point: LocationHistoryPoint, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LuogoSpacing.medium, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
                Text(
                    text = timeLabel(point.timestampMs),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "±${point.accuracyMeters.toInt()} m · ${point.sourceSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${"%.4f".format(point.latitude)}, ${"%.4f".format(point.longitude)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete this point",
                modifier = Modifier.height(18.dp)
            )
        }
    }
}

private fun timeLabel(timestampMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))