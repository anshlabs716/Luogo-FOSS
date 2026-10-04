package app.luogo.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.luogo.app.data.db.LuogoDatabase
import app.luogo.app.data.db.RegisteredItemEntity
import app.luogo.app.domain.model.ItemType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LuogoRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: LuogoDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LuogoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `verify app_name resource and friendly item persistence in Room`() = runTest {
        val appName = context.getString(R.string.app_name)
        assertEquals("Luogo-FOSS", appName)

        val dao = db.luogoDao()
        val item = RegisteredItemEntity(
            id = "item-buds-3",
            friendlyName = "Pixel Buds 3",
            itemType = ItemType.EARBUDS.name,
            ownerId = "user-alex",
            createdAtMs = 1_700_000_000_000L,
            ephemeralIdentitySeedBase64 = "c2VlZA",
            currentRotatingBleIdHex = "0102030405060708090a0b0c0d0e0f10",
            rotationEpoch = 1888888L,
            lastSeenLatitude = 37.7749,
            lastSeenLongitude = -122.4194,
            lastSeenAccuracyMeters = 6.2f,
            lastSeenTimestampMs = 1_700_000_000_000L,
            lastSeenSource = "BLE Finding Network",
            batteryPercent = 68,
            rssiDbm = -58,
            uwbDistanceMeters = 2.4f,
            uwbAzimuthDegrees = 15f,
            isLostMode = false,
            isRinging = false
        )
        dao.upsertRegisteredItem(item)

        val allItems = dao.getRegisteredItems()
        assertEquals(1, allItems.size)
        assertEquals("Pixel Buds 3", allItems.first().friendlyName)
        assertTrue(allItems.first().batteryPercent == 68)
    }
}
