package app.luogo.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.luogo.app.data.crypto.CryptoEngine
import app.luogo.app.data.db.LuogoDatabase
import app.luogo.app.data.db.LuogoDao
import app.luogo.app.data.geofence.GeofenceAndHistoryEngine
import app.luogo.app.data.location.AndroidHardwareLocationManager
import app.luogo.app.data.location.LocationFusionEngine
import app.luogo.app.data.map.MapAndRoutingProvider
import app.luogo.app.data.network.RelayClient
import app.luogo.app.data.repository.LuogoRepository
import app.luogo.app.data.ble.BleFindingProtocol
import app.luogo.app.service.LuogoNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builds a real [LuogoRepository] against an in-memory database, so screens can be rendered
 * with genuine state rather than hand-written stand-ins.
 *
 * Everything here is the production class. The only substitutes are the in-memory Room
 * database and Robolectric's SharedPreferences, neither of which affects what a screen draws.
 */
class ScreenFixture {

    private val context: Context = ApplicationProvider.getApplicationContext()

    val db: LuogoDatabase = Room.inMemoryDatabaseBuilder(context, LuogoDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val dao: LuogoDao = db.luogoDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    val crypto = CryptoEngine(context.getSharedPreferences("crypto", Context.MODE_PRIVATE))

    val viewModel: app.luogo.app.ui.viewmodel.LuogoViewModel by lazy {
        app.luogo.app.ui.viewmodel.LuogoViewModel(repository())
    }

    private val repository by lazy {
        val fusion = LocationFusionEngine()
        LuogoRepository(
            context = context,
            dao = dao,
            prefs = context.getSharedPreferences("repo", Context.MODE_PRIVATE),
            cryptoEngine = crypto,
            bleProtocol = BleFindingProtocol(crypto),
            fusionEngine = fusion,
            hardwareLocationManager = AndroidHardwareLocationManager(context, fusion, scope),
            relayClient = RelayClient(
                context.getSharedPreferences("relay", Context.MODE_PRIVATE),
                crypto,
                scope
            ),
            mapProvider = MapAndRoutingProvider(context),
            geofenceEngine = GeofenceAndHistoryEngine(),
            notificationHelper = LuogoNotificationHelper(context)
        )
    }

    private fun repository() = repository

    /**
     * Feeds realistic relay frames through the repository's real inbound path, so screens are
     * drawn from state the production code produced rather than values poked in by the test.
     *
     * [applyInboundPayload] is private and is invoked by the decryption path, so it is reached
     * reflectively here. Widening production visibility purely to serve a screenshot harness
     * would be the wrong trade.
     */
    fun seedPeers(vararg frames: Triple<String, String, Long>) {
        val method = repository.javaClass
            .getDeclaredMethod("applyInboundPayload", String::class.java, ByteArray::class.java)
            .apply { isAccessible = true }
        frames.forEach { (groupId, json, _) ->
            method.invoke(repository, groupId, json.toByteArray(Charsets.UTF_8))
        }
    }

    /** Creates groups through the real repository API. */
    fun seedGroups(vararg specs: Pair<String, app.luogo.app.domain.model.GroupCategory>) {
        kotlinx.coroutines.runBlocking {
            specs.forEach { (name, category) -> repository.createGroup(name, category) }
        }
    }

    fun close() = db.close()
}