package app.luogo.app

import android.app.Application
import android.content.Context
import app.luogo.app.data.ble.BleFindingProtocol
import app.luogo.app.data.crypto.CryptoEngine
import app.luogo.app.data.db.LuogoDatabase
import app.luogo.app.data.geofence.GeofenceAndHistoryEngine
import app.luogo.app.data.location.AndroidHardwareLocationManager
import app.luogo.app.data.location.LocationFusionEngine
import app.luogo.app.data.map.MapAndRoutingProvider
import app.luogo.app.data.network.RelayClient
import app.luogo.app.data.repository.LuogoRepository
import app.luogo.app.service.LuogoNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LuogoApplication : Application() {

    lateinit var repository: LuogoRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        val prefs = getSharedPreferences("luogo_foss_prefs", Context.MODE_PRIVATE)
        val database = LuogoDatabase.getInstance(this)
        val cryptoEngine = CryptoEngine(prefs)
        val bleProtocol = BleFindingProtocol(cryptoEngine)
        val fusionEngine = LocationFusionEngine()
        val hardwareLocationManager = AndroidHardwareLocationManager(this, fusionEngine, appScope)
        val relayClient = RelayClient(prefs, cryptoEngine, appScope)
        val mapProvider = MapAndRoutingProvider(this)
        val geofenceEngine = GeofenceAndHistoryEngine()
        val notificationHelper = LuogoNotificationHelper(this)

        repository = LuogoRepository(
            context = this,
            dao = database.luogoDao(),
            prefs = prefs,
            cryptoEngine = cryptoEngine,
            bleProtocol = bleProtocol,
            fusionEngine = fusionEngine,
            hardwareLocationManager = hardwareLocationManager,
            relayClient = relayClient,
            mapProvider = mapProvider,
            geofenceEngine = geofenceEngine,
            notificationHelper = notificationHelper
        )
    }

    companion object {
        @Volatile
        private var instance: LuogoApplication? = null

        fun getRepository(context: Context): LuogoRepository {
            val app = context.applicationContext as? LuogoApplication
            if (app != null) return app.repository
            return instance?.repository ?: throw IllegalStateException("LuogoApplication not initialized")
        }
    }
}
