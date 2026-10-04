package app.luogo.app.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import app.luogo.app.LuogoApplication
import app.luogo.app.data.location.GeoMath
import app.luogo.app.domain.model.PeerLocationState
import app.luogo.app.domain.model.RegisteredItem
import app.luogo.app.domain.model.SavedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Driving-safe Android Auto integration.
 *
 * Scope is deliberately small: look at where your people, saved places and items are, and
 * hand off to the car's own navigation app. Nothing here is editable while driving.
 *
 * Two rules this file takes seriously:
 *  1. **No invented data.** If there is no saved place, no shared person and no item, the list
 *     says so. It never substitutes a hardcoded city or a made-up contact, because a driver
 *     acting on a fabricated position is worse than an empty list.
 *  2. **No blocking on the main thread.** Android Auto calls [Screen.onGetTemplate]
 *     synchronously, so the data is pre-collected into an immutable snapshot on a background
 *     scope and the template just reads it.
 *
 * Phone/tablet plus Android Auto only. There is deliberately no Wear OS component.
 */
class LuogoCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreate() {
        super.onCreate()
        AutoSnapshot.start(this)
    }

    override fun onCreateSession(): Session = LuogoCarSession()
}

class LuogoCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = LuogoCarMainScreen(carContext)
}

/**
 * Immutable, main-thread-safe view of what the car UI is allowed to show.
 *
 * Populated from the repository's flows off the main thread, so [LuogoCarMainScreen] never
 * has to block on I/O.
 */
object AutoSnapshot {

    data class Entry(
        val title: String,
        val subtitle: String,
        val latitude: Double,
        val longitude: Double
    )

    data class State(
        val entries: List<Entry> = emptyList(),
        val hasFix: Boolean = false
    )

    @Volatile
    var state: State = State()
        private set

    private var started = false

    fun start(service: CarAppService) {
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = runCatching { LuogoApplication.getRepository(service) }.getOrNull() ?: return

        scope.launch {
            repository.hardwareLocationManager.fusedLocation.collectLatest { fix ->
                rebuild(repository, fix?.latitude, fix?.longitude, fix != null)
            }
        }
        scope.launch {
            combineLatest(
                repository.peersFlow,
                repository.placesFlow,
                repository.itemsFlow
            ) { peers, places, items -> Triple(peers, places, items) }
                .collectLatest { (peers, places, items) ->
                    rebuildFromData(peers, places, items, repository)
                }
        }
    }

    private suspend fun <A, B, C, R> combineLatest(
        a: kotlinx.coroutines.flow.Flow<A>,
        b: kotlinx.coroutines.flow.Flow<B>,
        c: kotlinx.coroutines.flow.Flow<C>,
        transform: suspend (A, B, C) -> R
    ): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(a, b, c, transform)

    private fun rebuildFromData(
        peers: List<PeerLocationState>,
        places: List<SavedPlace>,
        items: List<RegisteredItem>,
        repository: app.luogo.app.data.repository.LuogoRepository
    ) {
        val fix = repository.hardwareLocationManager.fusedLocation.value
        rebuild(repository, fix?.latitude, fix?.longitude, fix != null, peers, places, items)
    }

    private fun rebuild(
        repository: app.luogo.app.data.repository.LuogoRepository,
        latitude: Double?,
        longitude: Double?,
        hasFix: Boolean,
        peers: List<PeerLocationState> = emptyList(),
        places: List<SavedPlace> = emptyList(),
        items: List<RegisteredItem> = emptyList()
    ) {
        val fromLat = latitude
        val fromLon = longitude
        val entries = ArrayList<Entry>()

        for (peer in peers.take(MAX_PEERS)) {
            val distance = if (fromLat != null && fromLon != null) {
                val km = GeoMath.distanceMeters(fromLat, fromLon, peer.latitude, peer.longitude) / 1000.0
                val age = ((System.currentTimeMillis() - peer.timestampMs) / 1000L).coerceAtLeast(0L)
                val freshness = if (peer.isStale(System.currentTimeMillis())) {
                    "stale, ${age / 60}m old"
                } else {
                    "updated ${age}s ago"
                }
                "%.1f km · ±%d m · %s".format(km, peer.accuracyMeters.toInt(), freshness)
            } else {
                "shared with you"
            }
            entries.add(Entry(peer.displayName, distance, peer.latitude, peer.longitude))
        }

        for (place in places.take(MAX_PLACES)) {
            val distance = if (fromLat != null && fromLon != null) {
                "%.1f km away".format(
                    GeoMath.distanceMeters(fromLat, fromLon, place.latitude, place.longitude) / 1000.0
                )
            } else {
                place.category.label
            }
            entries.add(Entry(place.name, distance, place.latitude, place.longitude))
        }

        for (item in items.take(MAX_ITEMS)) {
            val lat = item.lastSeenLatitude
            val lon = item.lastSeenLongitude
            if (lat == null || lon == null) continue
            val age = item.lastSeenTimestampMs?.let {
                ((System.currentTimeMillis() - it) / 60000L).coerceAtLeast(0L)
            }
            val subtitle = if (age != null) {
                "${item.itemType.label} · ${item.presenceStatus().label.lowercase()} ${age}m ago"
            } else {
                "${item.itemType.label} · never seen"
            }
            entries.add(Entry(item.friendlyName, subtitle, lat, lon))
        }

        state = State(entries = entries.take(MAX_TOTAL), hasFix = hasFix)
    }

    private const val MAX_PEERS = 3
    private const val MAX_PLACES = 3
    private const val MAX_ITEMS = 2
    private const val MAX_TOTAL = 6
}

class LuogoCarMainScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val snapshot = AutoSnapshot.state
        val builder = ItemList.Builder()

        if (snapshot.entries.isEmpty()) {
            // Honest empty state. No placeholder rows, no invented contacts.
            builder.addItem(
                Row.Builder()
                    .setTitle("Nothing shared yet")
                    .addText(
                        if (snapshot.hasFix) {
                            "Add a saved place or share your location with someone to see them here."
                        } else {
                            "Waiting for a location fix. Grant location permission in Luogo-FOSS on your phone."
                        }
                    )
                    .setBrowsable(false)
                    .build()
            )
        } else {
            for (entry in snapshot.entries) {
                val place = Place.Builder(CarLocation.create(entry.latitude, entry.longitude))
                    .setMarker(PlaceMarker.Builder().build())
                    .build()
                builder.addItem(
                    Row.Builder()
                        .setTitle(entry.title)
                        .addText(entry.subtitle)
                        .setMetadata(Metadata.Builder().setPlace(place).build())
                        .setBrowsable(true)
                        .setOnClickListener {
                            val uri = Uri.parse(
                                "geo:${entry.latitude},${entry.longitude}?q=" +
                                    "${entry.latitude},${entry.longitude}(${Uri.encode(entry.title)})"
                            )
                            runCatching {
                                carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
                            }
                        }
                        .build()
                )
            }
        }

        return PlaceListMapTemplate.Builder()
            .setTitle("Luogo-FOSS Auto")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setItemList(builder.build())
            .build()
    }
}