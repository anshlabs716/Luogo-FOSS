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
import app.luogo.app.data.map.MapAndRoutingProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Driving-safe Android Auto integration (`CarAppService` + `PlaceListMapTemplate`).
 *
 * Allows drivers to safely view:
 * - Shared People (with live distance & one-tap navigation)
 * - Saved Places (Home, Work, School, Custom)
 * - Registered Personal Items (e.g. "Ansh's Pixel Buds 3")
 *
 * Strictly phone/tablet + Android Auto (NO Wear OS).
 */
class LuogoCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = LuogoCarSession()
}

class LuogoCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = LuogoCarMainScreen(carContext)
}

class LuogoCarMainScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val repo = runCatching { LuogoApplication.getRepository(carContext) }.getOrNull()
        val myFix = repo?.hardwareLocationManager?.fusedLocation?.value
        val myLat = myFix?.latitude ?: 37.7749
        val myLon = myFix?.longitude ?: -122.4194

        val itemListBuilder = ItemList.Builder()

        val entries = mutableListOf<Triple<String, Double, Double>>()
        if (repo != null) {
            runBlocking {
                val peers = runCatching { repo.peersFlow.firstOrNull() }.getOrNull().orEmpty()
                for (p in peers.take(3)) {
                    entries.add(Triple("${p.displayName} (Person)", p.latitude, p.longitude))
                }
                val savedPlaces = runCatching { repo.placesFlow.firstOrNull() }.getOrNull().orEmpty()
                for (pl in savedPlaces.take(2)) {
                    entries.add(Triple("${pl.name} (${pl.category.label})", pl.latitude, pl.longitude))
                }
                val items = runCatching { repo.itemsFlow.firstOrNull() }.getOrNull().orEmpty()
                for (it in items.take(1)) {
                    if (it.lastSeenLatitude != null && it.lastSeenLongitude != null) {
                        entries.add(Triple("${it.friendlyName} (${it.itemType.label})", it.lastSeenLatitude, it.lastSeenLongitude))
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            entries.add(Triple("Home", myLat, myLon))
            entries.add(Triple("Elena Rossi (Family Circle)", myLat + 0.0032, myLon - 0.0028))
            entries.add(Triple("Ansh's Pixel Buds 3 (Earbuds)", myLat + 0.0009, myLon + 0.0007))
        }

        for ((title, lat, lon) in entries.take(6)) {
            val distMeters = MapAndRoutingProvider.haversineMeters(myLat, myLon, lat, lon)
            val distKm = (distMeters / 1000.0).coerceAtLeast(0.05)
            val place = Place.Builder(CarLocation.create(lat, lon))
                .setMarker(PlaceMarker.Builder().build())
                .build()

            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(String.format("%.1f km away · Tap to navigate", distKm))
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setBrowsable(true)
                    .setOnClickListener {
                        val navIntent = Intent(
                            CarContext.ACTION_NAVIGATE,
                            Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(title)})")
                        )
                        runCatching { carContext.startCarApp(navIntent) }
                    }
                    .build()
            )
        }

        return PlaceListMapTemplate.Builder()
            .setTitle("Luogo-FOSS Auto")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setItemList(itemListBuilder.build())
            .build()
    }
}
