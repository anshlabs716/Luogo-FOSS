package app.luogo.app.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.luogo.app.data.location.SmoothMarkerInterpolator
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.ItemPresenceStatus
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.domain.model.UserProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun InteractiveMapCanvas(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    bearingDegrees: Float,
    mapStyle: MapStyleOption,
    myLocation: FusedLocationFix?,
    myProfile: UserProfile,
    peers: List<PeerLocationState>,
    items: List<RegisteredItem>,
    places: List<SavedPlace>,
    activeRoute: RouteResult?,
    showPeople: Boolean,
    showItems: Boolean,
    showPlaces: Boolean,
    fetchTile: suspend (MapStyleOption, Int, Int, Int) -> Bitmap?,
    onPanZoomRotate: (newLat: Double, newLon: Double, newZoom: Float, newBearing: Float) -> Unit,
    onPeerClicked: (PeerLocationState) -> Unit,
    onItemClicked: (RegisteredItem) -> Unit,
    onPlaceClicked: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier
) {
    val interpolator = remember { SmoothMarkerInterpolator() }
    val loadedTiles = remember { mutableStateMapOf<String, Bitmap>() }
    var frameClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 60fps render loop for smooth marker interpolation
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                frameClockMs = System.currentTimeMillis()
            }
        }
    }

    // Feed new trusted fixes into SmoothMarkerInterpolator
    LaunchedEffect(myLocation) {
        myLocation?.let { fix ->
            interpolator.onNewTrustedFix(
                id = "me",
                latitude = fix.latitude,
                longitude = fix.longitude,
                bearingDegrees = fix.bearingDegrees,
                accuracyMeters = fix.horizontalAccuracyMeters,
                speedMps = fix.speedMps
            )
        }
    }

    LaunchedEffect(peers) {
        peers.forEach { p ->
            interpolator.onNewTrustedFix(
                id = "peer:${p.userId}",
                latitude = p.latitude,
                longitude = p.longitude,
                bearingDegrees = p.bearingDegrees,
                accuracyMeters = p.accuracyMeters,
                speedMps = p.speedMps
            )
        }
    }

    LaunchedEffect(items) {
        items.forEach { item ->
            val lat = item.lastSeenLatitude
            val lon = item.lastSeenLongitude
            if (lat != null && lon != null) {
                interpolator.onNewTrustedFix(
                    id = "item:${item.id}",
                    latitude = lat,
                    longitude = lon,
                    bearingDegrees = 0f,
                    accuracyMeters = item.lastSeenAccuracyMeters ?: 10f,
                    speedMps = 0f
                )
            }
        }
    }

    // Prefetch visible slippy tiles asynchronously
    val intZoom = zoom.toInt().coerceIn(3, 18)
    val centerTileX = lonToTileXDouble(centerLon, intZoom).toInt()
    val centerTileY = latToTileYDouble(centerLat, intZoom).toInt()
    LaunchedEffect(mapStyle, intZoom, centerTileX, centerTileY) {
        for (dx in -2..2) {
            for (dy in -2..2) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                val key = "${mapStyle.id}_${intZoom}_${tx}_$ty"
                if (!loadedTiles.containsKey(key)) {
                    val bmp = fetchTile(mapStyle, intZoom, tx, ty)
                    if (bmp != null) {
                        loadedTiles[key] = bmp
                        // A 256x256 ARGB bitmap is ~256 KB, so an unbounded cache is a
                        // guaranteed out-of-memory. Evict in insertion order, which the
                        // map's key order approximates well enough for a view cache.
                        while (loadedTiles.size > MAX_CACHED_TILES) {
                            val oldest = loadedTiles.keys.firstOrNull() ?: break
                            loadedTiles.remove(oldest)
                        }
                    }
                }
            }
        }
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val subLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("interactive_map_canvas")
            .pointerInput(centerLat, centerLon, zoom, bearingDegrees) {
                detectTransformGestures { _, pan, gestureZoom, gestureRotate ->
                    val scaleFactor = (2.0.pow(zoom.toDouble()) * 256.0)
                    val dLon = -(pan.x / scaleFactor) * 360.0
                    val latRad = centerLat * PI / 180.0
                    val dLat = (pan.y / scaleFactor) * 360.0 * cos(latRad)
                    val nextZoom = (zoom + (ln(gestureZoom.toDouble()) / ln(2.0)).toFloat()).coerceIn(3f, 19f)
                    val nextBearing = if (kotlin.math.abs(gestureRotate) > 1.2f) {
                        (bearingDegrees + gestureRotate) % 360f
                    } else bearingDegrees
                    onPanZoomRotate(
                        (centerLat + dLat).coerceIn(-84.0, 84.0),
                        ((centerLon + dLon + 540.0) % 360.0) - 180.0,
                        nextZoom,
                        nextBearing
                    )
                }
            }
            .pointerInput(centerLat, centerLon, zoom, peers, items, places, showPeople, showItems, showPlaces) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()

                    if (showItems) {
                        for (item in items) {
                            val lat = item.lastSeenLatitude ?: continue
                            val lon = item.lastSeenLongitude ?: continue
                            val pos = latLonToScreenOffset(lat, lon, centerLat, centerLon, zoom, w, h)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 68f) {
                                onItemClicked(item)
                                return@detectTapGestures
                            }
                        }
                    }

                    if (showPeople) {
                        for (peer in peers) {
                            val pos = latLonToScreenOffset(peer.latitude, peer.longitude, centerLat, centerLon, zoom, w, h)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 68f) {
                                onPeerClicked(peer)
                                return@detectTapGestures
                            }
                        }
                    }

                    if (showPlaces) {
                        for (place in places) {
                            val pos = latLonToScreenOffset(place.latitude, place.longitude, centerLat, centerLon, zoom, w, h)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 72f) {
                                onPlaceClicked(place)
                                return@detectTapGestures
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val nowMs = frameClockMs

        // 1. Style-specific base background. Only a flat backdrop: never a synthesised map.
        val bgColor = when (mapStyle) {
            MapStyleOption.STANDARD_OSM -> Color(0xFFE8ECEF)
            MapStyleOption.PROTOMAPS_DARK -> Color(0xFF11191D)
            MapStyleOption.TERRAIN_TOPO -> Color(0xFFE7E4D5)
            MapStyleOption.SATELLITE_IMAGERY -> Color(0xFF0B1E26)
        }
        drawRect(bgColor)

        val unavailableTileColor = when (mapStyle) {
            MapStyleOption.PROTOMAPS_DARK -> Color(0xFF1B262B)
            else -> Color(0xFFDDE3E6)
        }

        val tileZoom = zoom.toInt().coerceIn(3, 18)
        val scaleFraction = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
        val tileSizePx = 256f * scaleFraction
        val centerTileExactX = lonToTileXDouble(centerLon, tileZoom)
        val centerTileExactY = latToTileYDouble(centerLat, tileZoom)

        // 2. Render cached/downloaded map or satellite tiles
        for (dx in -2..2) {
            for (dy in -2..2) {
                val tx = floor(centerTileExactX).toInt() + dx
                val ty = floor(centerTileExactY).toInt() + dy
                val screenX = (w / 2f) + ((tx - centerTileExactX).toFloat() * tileSizePx)
                val screenY = (h / 2f) + ((ty - centerTileExactY).toFloat() * tileSizePx)

                val key = "${mapStyle.id}_${tileZoom}_${tx}_$ty"
                val bmp = loadedTiles[key]
                if (bmp != null) {
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(screenX.toInt(), screenY.toInt()),
                        dstSize = IntSize(tileSizePx.toInt() + 2, tileSizePx.toInt() + 2)
                    )
                } else {
                    // No tile yet, or none reachable. Draw an empty cell and nothing else.
                    //
                    // This deliberately does NOT synthesise streets, contours or satellite
                    // imagery. Invented cartography is indistinguishable from a real map at a
                    // glance, so a user could plan a route against a picture that does not
                    // exist. An honest blank cell reads as "loading" or "offline"; a fake one
                    // reads as truth.
                    drawRect(
                        color = unavailableTileColor.copy(alpha = 0.55f),
                        topLeft = Offset(screenX, screenY),
                        size = Size(tileSizePx, tileSizePx)
                    )
                }
            }
        }

        // 3. Saved Places & Geofence Radius Rings
        if (showPlaces) {
            for (place in places) {
                val pos = latLonToScreenOffset(place.latitude, place.longitude, centerLat, centerLon, zoom, w, h)
                val radiusPx = metersToPixels(place.radiusMeters, place.latitude, zoom).coerceIn(18f, 340f)
                val placeColor = if (place.currentlyInside) Color(0xFF26A69A) else Color(0xFF5C6BC0)

                drawCircle(
                    color = placeColor.copy(alpha = 0.16f),
                    radius = radiusPx,
                    center = pos
                )
                drawCircle(
                    color = placeColor.copy(alpha = 0.75f),
                    radius = radiusPx,
                    center = pos,
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = if (place.enabled) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )
                drawCircle(
                    color = placeColor,
                    radius = 11f,
                    center = pos
                )
                drawPillLabel(
                    title = place.name,
                    subtitle = "${place.radiusMeters.toInt()}m geofence",
                    anchor = Offset(pos.x, pos.y - 22f),
                    badgeColor = placeColor,
                    labelPaint = labelPaint,
                    subLabelPaint = subLabelPaint
                )
            }
        }

        // 4. Active Route Polyline
        if (activeRoute != null && activeRoute.polyline.size >= 2) {
            val routePath = Path()
            activeRoute.polyline.forEachIndexed { idx, (lat, lon) ->
                val pt = latLonToScreenOffset(lat, lon, centerLat, centerLon, zoom, w, h)
                if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
            }
            drawPath(
                path = routePath,
                color = Color(0xFF00ACC1),
                style = Stroke(width = 9f, cap = StrokeCap.Round)
            )
        }

        // 5. Registered Items Markers (ALWAYS showing friendly human-readable name)
        if (showItems) {
            for (item in items) {
                val rawLat = item.lastSeenLatitude ?: continue
                val rawLon = item.lastSeenLongitude ?: continue
                val pose = interpolator.sample("item:${item.id}", nowMs)
                val lat = pose?.latitude ?: rawLat
                val lon = pose?.longitude ?: rawLon
                val accMeters = pose?.accuracyMeters ?: (item.lastSeenAccuracyMeters ?: 10f)

                val pos = latLonToScreenOffset(lat, lon, centerLat, centerLon, zoom, w, h)
                val accPx = metersToPixels(accMeters, lat, zoom).coerceIn(14f, 220f)
                val presence = item.presenceStatus(nowMs)
                val itemColor = when (presence) {
                    ItemPresenceStatus.LIVE -> Color(0xFF00BFA5)
                    ItemPresenceStatus.RECENTLY_SEEN -> Color(0xFFFFB300)
                    ItemPresenceStatus.STALE -> Color(0xFF8D6E63)
                    ItemPresenceStatus.UNKNOWN -> Color(0xFF78909C)
                }

                drawCircle(
                    color = itemColor.copy(alpha = 0.15f),
                    radius = accPx,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 20f,
                    center = pos
                )
                drawCircle(
                    color = itemColor,
                    radius = 16f,
                    center = pos
                )

                val ageSec = item.lastSeenTimestampMs?.let { ((nowMs - it) / 1000L).coerceAtLeast(0L) } ?: 0L
                val ageLabel = if (ageSec < 60) "${ageSec}s ago" else "${ageSec / 60}m ago"
                drawPillLabel(
                    title = item.friendlyName,
                    subtitle = "${presence.label} · ±${accMeters.toInt()}m · $ageLabel",
                    anchor = Offset(pos.x, pos.y - 30f),
                    badgeColor = itemColor,
                    labelPaint = labelPaint,
                    subLabelPaint = subLabelPaint
                )
            }
        }

        // 6. Live People Markers (with clear LIVE vs STALE visual identity, accuracy ring & battery)
        if (showPeople) {
            for (peer in peers) {
                val pose = interpolator.sample("peer:${peer.userId}", nowMs)
                val lat = pose?.latitude ?: peer.latitude
                val lon = pose?.longitude ?: peer.longitude
                val accMeters = pose?.accuracyMeters ?: peer.accuracyMeters
                val stale = peer.isStale(nowMs)

                val pos = latLonToScreenOffset(lat, lon, centerLat, centerLon, zoom, w, h)
                val accPx = metersToPixels(accMeters, lat, zoom).coerceIn(12f, 260f)
                val baseColor = Color(peer.colorArgb.toInt() or -0x1000000)
                val markerColor = if (stale) baseColor.copy(alpha = 0.55f) else baseColor

                // True-to-accuracy circle (never renders a 100m fix as a 3m fix)
                drawCircle(
                    color = markerColor.copy(alpha = if (stale) 0.10f else 0.18f),
                    radius = accPx,
                    center = pos
                )
                drawCircle(
                    color = markerColor.copy(alpha = 0.65f),
                    radius = accPx,
                    center = pos,
                    style = Stroke(
                        width = 2f,
                        pathEffect = if (stale) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                    )
                )

                // Outer status halo + person avatar circle
                drawCircle(
                    color = if (stale) Color.LightGray else Color.White,
                    radius = 23f,
                    center = pos
                )
                drawCircle(
                    color = markerColor,
                    radius = 19f,
                    center = pos
                )

                val ageSec = ((nowMs - peer.timestampMs) / 1000L).coerceAtLeast(0L)
                val ageText = if (ageSec < 60) "${ageSec}s" else "${ageSec / 60}m"
                val batteryStr = peer.batteryPercent?.let { " · $it%" } ?: ""
                val statePrefix = if (stale) "STALE ($ageText)" else "LIVE ($ageText)"

                drawPillLabel(
                    title = peer.displayName,
                    subtitle = "$statePrefix · ±${accMeters.toInt()}m$batteryStr",
                    anchor = Offset(pos.x, pos.y - 34f),
                    badgeColor = markerColor,
                    labelPaint = labelPaint,
                    subLabelPaint = subLabelPaint
                )
            }
        }

        // 7. Current User Marker (Fluidly interpolated at 60fps from ~2s fused fixes)
        myLocation?.let { fix ->
            val pose = interpolator.sample("me", nowMs)
            val lat = pose?.latitude ?: fix.latitude
            val lon = pose?.longitude ?: fix.longitude
            val heading = pose?.bearingDegrees ?: fix.bearingDegrees
            val accMeters = pose?.accuracyMeters ?: fix.horizontalAccuracyMeters

            val pos = latLonToScreenOffset(lat, lon, centerLat, centerLon, zoom, w, h)
            val accPx = metersToPixels(accMeters, lat, zoom).coerceIn(14f, 300f)
            val myColor = Color(myProfile.colorArgb.toInt() or -0x1000000)

            // True-to-scale accuracy circle
            drawCircle(
                color = myColor.copy(alpha = 0.22f),
                radius = accPx,
                center = pos
            )
            drawCircle(
                color = myColor.copy(alpha = 0.85f),
                radius = accPx,
                center = pos,
                style = Stroke(width = 2.5f)
            )

            // Heading cone
            rotate(degrees = heading, pivot = pos) {
                val cone = Path().apply {
                    moveTo(pos.x, pos.y)
                    lineTo(pos.x - 22f, pos.y - 54f)
                    lineTo(pos.x + 22f, pos.y - 54f)
                    close()
                }
                drawPath(cone, color = myColor.copy(alpha = 0.35f))
            }

            drawCircle(color = Color.White, radius = 22f, center = pos)
            drawCircle(color = myColor, radius = 17f, center = pos)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillLabel(
    title: String,
    subtitle: String,
    anchor: Offset,
    badgeColor: Color,
    labelPaint: Paint,
    subLabelPaint: Paint
) {
    val titleWidth = labelPaint.measureText(title)
    val subWidth = subLabelPaint.measureText(subtitle)
    val boxWidth = max(titleWidth, subWidth) + 36f
    val boxHeight = 68f
    val left = anchor.x - boxWidth / 2f
    val top = anchor.y - boxHeight

    drawRoundRect(
        color = Color(0xD910181B),
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawRoundRect(
        color = badgeColor,
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 2.2f)
    )

    drawContext.canvas.nativeCanvas.apply {
        drawText(title, left + 18f, top + 30f, labelPaint)
        drawText(subtitle, left + 18f, top + 56f, subLabelPaint)
    }
}

private fun lonToTileXDouble(lon: Double, zoom: Int): Double {
    val n = 2.0.pow(zoom.toDouble())
    return ((lon + 180.0) / 360.0) * n
}

private fun latToTileYDouble(lat: Double, zoom: Int): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = clamped * PI / 180.0
    val n = 2.0.pow(zoom.toDouble())
    return ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0) * n
}

private fun latLonToScreenOffset(
    lat: Double,
    lon: Double,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    widthPx: Float,
    heightPx: Float
): Offset {
    val worldSizePx = 256.0 * 2.0.pow(zoom.toDouble())
    val cx = ((centerLon + 180.0) / 360.0) * worldSizePx
    val centerRad = centerLat.coerceIn(-85.0511, 85.0511) * PI / 180.0
    val cy = ((1.0 - ln(tan(centerRad) + 1.0 / cos(centerRad)) / PI) / 2.0) * worldSizePx

    val px = ((lon + 180.0) / 360.0) * worldSizePx
    val ptRad = lat.coerceIn(-85.0511, 85.0511) * PI / 180.0
    val py = ((1.0 - ln(tan(ptRad) + 1.0 / cos(ptRad)) / PI) / 2.0) * worldSizePx

    return Offset(
        x = (widthPx / 2f) + (px - cx).toFloat(),
        y = (heightPx / 2f) + (py - cy).toFloat()
    )
}

private fun metersToPixels(meters: Float, latitude: Double, zoom: Float): Float {
    val metersPerPixel = (156543.03392 * cos(latitude * PI / 180.0)) / 2.0.pow(zoom.toDouble())
    return (meters / metersPerPixel.coerceAtLeast(0.01)).toFloat()
}

/** Bounded so a long pan session cannot exhaust memory. */
private const val MAX_CACHED_TILES = 160
