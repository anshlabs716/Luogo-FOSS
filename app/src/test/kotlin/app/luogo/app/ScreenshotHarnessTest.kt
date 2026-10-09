package app.luogo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.luogo.app.ui.components.CategoryIconTile
import app.luogo.app.ui.components.EmptyState
import app.luogo.app.ui.components.FreshnessChip
import app.luogo.app.ui.components.FreshnessTone
import app.luogo.app.ui.components.InitialsAvatar
import app.luogo.app.ui.components.NoticeCard
import app.luogo.app.ui.components.NoticeTone
import app.luogo.app.ui.components.StatTile
import app.luogo.app.ui.theme.LuogoTheme
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's composables to PNGs so the design can be reviewed as an image.
 *
 * The UI was rewritten twice without ever being seen on anything, which is how flat surfaces
 * and invisible controls got shipped. This writes real rendered pixels under
 * `build/screenshots/` so the result can be looked at instead of inferred from source.
 *
 * Each test fails if the view has zero size or the draw throws, so a composable that cannot
 * render at all is caught.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotHarnessTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun dir(): File = File("build/screenshots").apply { mkdirs() }

    /** Sets [content], waits for it to settle, then writes the decor view to a PNG. */
    private fun capture(name: String, dark: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            LuogoTheme(darkTheme = dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) { content() }
            }
        }
        compose.waitForIdle()
        write(name)
    }

    private fun write(name: String) {
        val decor = compose.activity.window.decorView
        check(decor.width > 0 && decor.height > 0) {
            "decor has zero size (${decor.width}x${decor.height}); nothing was laid out"
        }
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        decor.draw(canvas)
        val target = File(dir(), "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot: ${target.absolutePath} (${decor.width}x${decor.height})")
    }

    @Test
    fun tonalLadder() {
        capture("01-dark-ladder") {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val steps = listOf(
                    "background" to MaterialTheme.colorScheme.background,
                    "surface" to MaterialTheme.colorScheme.surface,
                    "surfaceContainerLow" to MaterialTheme.colorScheme.surfaceContainerLow,
                    "surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
                    "surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
                    "surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
                    "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant
                )
                steps.forEach { (label, colour) ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(colour)
                            .padding(12.dp)
                    ) {
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    @Test
    fun components() {
        capture("02-dark-components") {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("12 m", "accuracy", Modifier.weight(1f))
                    StatTile("3", "people", Modifier.weight(1f))
                    StatTile("LIVE", "status", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreshnessChip("LIVE", FreshnessTone.LIVE)
                    FreshnessChip("RECENT", FreshnessTone.RECENT)
                    FreshnessChip("STALE", FreshnessTone.STALE)
                    FreshnessChip("UNKNOWN", FreshnessTone.UNKNOWN)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    InitialsAvatar("Robin", 0xFF00695C.toLong(), size = 44.dp)
                    InitialsAvatar("Sam", 0xFFB25B00.toLong(), size = 44.dp)
                    InitialsAvatar("Alex", 0xFF1565C0.toLong(), size = 44.dp, ringColor = ComposeColor(0xFF00C853))
                    InitialsAvatar("Jay", 0xFF8E24AA.toLong(), size = 44.dp, ringColor = MaterialTheme.colorScheme.outline)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIconTile(
                        Icons.Default.Headphones, "Headphones",
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                    CategoryIconTile(
                        Icons.Default.Watch, "Watch",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                    CategoryIconTile(
                        Icons.Default.Key, "Keys",
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
                NoticeCard("This is an informational notice.", tone = NoticeTone.INFO)
                NoticeCard("This is a warning notice.", tone = NoticeTone.WARNING)
                NoticeCard("This is an error notice.", tone = NoticeTone.ERROR)
            }
        }
    }

    @Test
    fun emptyState() {
        capture("03-dark-empty") {
            Column(Modifier.fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.Groups,
                    title = "Nobody is sharing yet",
                    body = "Create a group and share its invite code. Whoever joins with that " +
                        "code can see your encrypted location, and you can see theirs."
                )
            }
        }
    }
}