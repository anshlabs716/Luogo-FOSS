package app.luogo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.luogo.app.domain.model.GroupCategory
import app.luogo.app.ui.screens.HistoryScreen
import app.luogo.app.ui.screens.ItemsScreen
import app.luogo.app.ui.screens.PeopleScreen
import app.luogo.app.ui.screens.PlacesScreen
import app.luogo.app.ui.screens.SettingsScreen
import app.luogo.app.ui.theme.LuogoTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real screens to PNGs under `build/screenshots/`.
 *
 * Every previous UI pass in this project was written without ever being looked at, which is
 * how flat surfaces, invisible controls and a full-bleed map with no header got shipped. This
 * drives the production screens with production state and writes the actual pixels out, so the
 * design can be reviewed as an image.
 *
 * MapScreen is not here: MapLibre is a native renderer that Robolectric cannot draw.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixture = ScreenFixture()

    @After
    fun tearDown() = fixture.close()

    private fun capture(name: String, dark: Boolean, screen: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            LuogoTheme(darkTheme = dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) { screen() }
            }
        }
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        check(decor.width > 0) { "$name never laid out (${decor.width}x${decor.height})" }
        val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        decor.draw(canvas)
        val out = File("build/screenshots", "$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot: ${out.absolutePath}")
    }

    @Test
    fun peopleScreenWithPeersAndGroups() {
        val now = System.currentTimeMillis()
        fixture.seedPeers(
            Triple("grp-family", """{"lat":12.9716,"lon":77.5946,"name":"Robin","id":"u_robin","ts":${now - 4_000},"acc":8.5,"spd":1.4,"bat":82,"act":"WALKING"}""", now),
            Triple("grp-family", """{"lat":12.9802,"lon":77.6051,"name":"Sam","id":"u_sam","ts":${now - 90_000},"acc":24.0,"spd":0.2,"bat":41,"act":"STILL"}""", now),
            Triple("grp-work", """{"lat":12.9382,"lon":77.6245,"name":"Alex","id":"u_alex","ts":${now - 900_000},"acc":64.0,"spd":0.0,"bat":15,"act":"STILL"}""", now)
        )
        fixture.seedGroups("Family" to GroupCategory.FAMILY, "Study group" to GroupCategory.CUSTOM)
        capture("10-people-dark", dark = true) { PeopleScreen(fixture.viewModel) }
    }

    @Test
    fun peopleScreenEmpty() {
        capture("11-people-empty-dark", dark = true) { PeopleScreen(fixture.viewModel) }
    }

    @Test
    fun itemsScreen() {
        capture("12-items-dark", dark = true) { ItemsScreen(fixture.viewModel, onRequestBlePermissions = {}) }
    }

    @Test
    fun placesScreen() {
        capture("13-places-dark", dark = true) { PlacesScreen(fixture.viewModel) }
    }

    @Test
    fun historyScreen() {
        capture("14-history-dark", dark = true) { HistoryScreen(fixture.viewModel) }
    }

    @Test
    fun settingsScreen() {
        capture("15-settings-dark", dark = true) { SettingsScreen(fixture.viewModel, isBatteryExempt = false, onRequestBatteryExemption = {}, onRequestPermissions = {}) }
    }
}