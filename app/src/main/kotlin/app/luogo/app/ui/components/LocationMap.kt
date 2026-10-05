package app.luogo.app.ui.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.luogo.app.domain.model.FusedLocationFix
import app.luogo.app.domain.model.MapStyleOption
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.RouteResult
import app.luogo.app.domain.model.SavedPlace
import app.luogo.app.ui.map.MapGeoJson
import app.luogo.app.ui.map.MapStyleFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Hosts a MapLibre [MapView] inside Compose.
 *
 * Why MapLibre rather than a hand-rolled Compose Canvas:
 *  - one-finger panning, two-finger pinch/rotate/tilt and fling momentum are handled natively
 *  - tile decode and cache run on native worker threads, so panning never blocks the UI thread
 *  - raster and vector sources, offline tile packs and camera animation are real
 *
 * The MapView's lifecycle is forwarded explicitly. Skipping this leaks native GL resources and
 * is the usual cause of crashes on rotation or when backgrounding the app.
 */
@Composable
fun LocationMap(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    bearingDegrees: Float,
    mapStyle: MapStyleOption,
    customSatelliteUrl: String,
    myLocation: FusedLocationFix?,
    myColorArgb: Long,
    peers: List<PeerLocationState>,
    items: List<RegisteredItem>,
    places: List<SavedPlace>,
    route: RouteResult?,
    followMyLocation: Boolean,
    nowMs: Long,
    onUserGesture: () -> Unit,
    onMapTapped: () -> Unit,
    onPeerTapped: (String) -> Unit,
    onItemTapped: (String) -> Unit,
    onPlaceTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapLibre must be initialised before a MapView is inflated.
    remember { MapLibre.getInstance(context) }

    // Keep the latest callbacks without re-creating the map on every recomposition.
    val peerTap by rememberUpdatedState(onPeerTapped)
    val itemTap by rememberUpdatedState(onItemTapped)
    val placeTap by rememberUpdatedState(onPlaceTapped)
    val mapTap by rememberUpdatedState(onMapTapped)
    val userGesture by rememberUpdatedState(onUserGesture)

    val holder = remember { MapHolder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                onCreate(null)
                holder.view = this
                getMapAsync { map ->
                    configureMap(
                        holder = holder,
                        map = map,
                        style = mapStyle,
                        customSatelliteUrl = customSatelliteUrl,
                        onTap = { tappedLatLng ->
                            val id = featureIdAt(map, tappedLatLng)
                            if (id == null) {
                                mapTap()
                            } else when {
                                id.startsWith(PEER_ID_PREFIX) -> peerTap(id.removePrefix(PEER_ID_PREFIX))
                                id.startsWith(ITEM_ID_PREFIX) -> itemTap(id.removePrefix(ITEM_ID_PREFIX))
                                id.startsWith(PLACE_ID_PREFIX) -> placeTap(id.removePrefix(PLACE_ID_PREFIX))
                                else -> mapTap()
                            }
                        },
                        onCameraMoved = { userGesture() }
                    )
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                applyStyleIfNeeded(holder, map, mapStyle, customSatelliteUrl)
                pushData(map, myLocation, myColorArgb, peers, items, places, route, nowMs)
                applyCamera(map, centerLat, centerLon, zoom, bearingDegrees, followMyLocation, myLocation != null)
            }
        }
    )

    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.view
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.view?.onDestroy()
            holder.view = null
            holder.appliedStyleKey = null
        }
    }
}

private const val PEER_ID_PREFIX = "peer:"
private const val ITEM_ID_PREFIX = "item:"
private const val PLACE_ID_PREFIX = "place:"
private const val RESERVED_ME_ID = "__me__"

/**
 * Per-composition holder for the live MapView and the style currently applied.
 *
 * These are held per instance rather than in module-level vars: a module-level var is shared
 * across every map and outlives a configuration change, so onDispose could call onDestroy on
 * a MapView that a freshly composed map is already using, which crashes on rotation.
 */
private class MapHolder {
    var view: MapView? = null
    var appliedStyleKey: String? = null
}

private fun configureMap(
    holder: MapHolder,
    map: MapLibreMap,
    style: MapStyleOption,
    customSatelliteUrl: String,
    onTap: (LatLng) -> Unit,
    onCameraMoved: () -> Unit
) {
    map.uiSettings.apply {
        // Left at their defaults, which is precisely what provides one-finger panning.
        setScrollGesturesEnabled(true)
        setZoomGesturesEnabled(true)
        setRotateGesturesEnabled(true)
        setTiltGesturesEnabled(true)
        setQuickZoomGesturesEnabled(true)
        setDoubleTapGesturesEnabled(true)
        setCompassEnabled(true)
        setCompassFadeFacingNorth(true)
        setAttributionEnabled(true)
        // MapLibre's own wordmark is redundant next to our per-source attribution.
        setLogoEnabled(false)
    }
    map.addOnMapClickListener { latLng ->
        onTap(latLng)
        true
    }
    map.addOnCameraMoveStartedListener { reason ->
        // Only a genuine user gesture should break follow mode; programmatic easing must not.
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            onCameraMoved()
        }
    }
    holder.appliedStyleKey = null
    applyStyleIfNeeded(holder, map, style, customSatelliteUrl)
}

/**
 * Resolves a tap to the id of whatever feature sits under the finger.
 *
 * People and items are tested before places, so a person standing inside a geofence is still
 * selectable. The user's own marker carries a reserved id and is never dispatched.
 */
private fun featureIdAt(map: MapLibreMap, tapped: LatLng): String? {
    val style = map.style ?: return null
    val point = map.projection.toScreenLocation(tapped)
    val candidates = listOf(
        MapStyleFactory.LAYER_PERSON to PEER_ID_PREFIX,
        MapStyleFactory.LAYER_ITEM to ITEM_ID_PREFIX,
        MapStyleFactory.LAYER_GEOFENCE_FILL to PLACE_ID_PREFIX
    )
    for ((layerId, prefix) in candidates) {
        if (style.getLayer(layerId) == null) continue
        val features = map.queryRenderedFeatures(point, layerId)
        for (feature in features) {
            val raw = feature.properties()?.get("id")?.asString ?: continue
            if (raw == RESERVED_ME_ID) continue
            return prefix + raw
        }
    }
    return null
}

private fun applyStyleIfNeeded(
    holder: MapHolder,
    map: MapLibreMap,
    style: MapStyleOption,
    customSatelliteUrl: String
) {
    val key = "${style.id}|$customSatelliteUrl"
    if (holder.appliedStyleKey == key) return
    holder.appliedStyleKey = key
    map.setStyle(Style.Builder().fromJson(MapStyleFactory.build(style, customSatelliteUrl)))
}

private fun pushData(
    map: MapLibreMap,
    myLocation: FusedLocationFix?,
    myColorArgb: Long,
    peers: List<PeerLocationState>,
    items: List<RegisteredItem>,
    places: List<SavedPlace>,
    route: RouteResult?,
    nowMs: Long
) {
    val style = map.style ?: return
    fun setSource(id: String, json: String) {
        (style.getSourceAs<GeoJsonSource>(id))?.setGeoJson(json)
    }
    // The user's own fix lives in the same source as peers; the style separates the two
    // layers with an id filter, so accuracy circles and labels stay consistent.
    val peopleFeatures = buildList {
        addAll(MapGeoJson.meFeatures(myLocation, myColorArgb))
        addAll(MapGeoJson.peopleFeatures(peers, nowMs))
    }
    setSource(MapStyleFactory.SOURCE_PEOPLE, MapGeoJson.collection(peopleFeatures))
    setSource(MapStyleFactory.SOURCE_ITEMS, MapGeoJson.collection(MapGeoJson.itemFeatures(items, nowMs)))
    setSource(MapStyleFactory.SOURCE_PLACES, MapGeoJson.collection(MapGeoJson.placeFeatures(places)))
    setSource(MapStyleFactory.SOURCE_ROUTE, MapGeoJson.collection(MapGeoJson.routeFeatures(route)))
}

private fun applyCamera(
    map: MapLibreMap,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    bearingDegrees: Float,
    follow: Boolean,
    hasFix: Boolean
) {
    if (!follow || !hasFix) return
    val target = LatLng(centerLat, centerLon)
    val current = map.cameraPosition
    val currentTarget = current.target ?: return
    // Only move when the target actually differs. Re-issuing the same camera position every
    // frame restarts the animation and the map never settles.
    if (currentTarget.latitude == target.latitude &&
        currentTarget.longitude == target.longitude
    ) {
        return
    }
    map.easeCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(target)
                .zoom(zoom.toDouble().coerceIn(MIN_ZOOM, MAX_ZOOM))
                .bearing(bearingDegrees.toDouble())
                .build()
        ),
        CAMERA_ANIMATION_MS
    )
}

private const val MIN_ZOOM = 2.0
private const val MAX_ZOOM = 19.0
private const val CAMERA_ANIMATION_MS = 700