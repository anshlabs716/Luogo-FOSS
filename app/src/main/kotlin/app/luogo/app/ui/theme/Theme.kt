package app.luogo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing scale.
 *
 * One scale, used everywhere, so padding is consistent instead of being guessed per screen.
 * Values follow the 4dp grid.
 */
@Immutable
data class Spacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

val LuogoSpacing = Spacing()

val LocalSpacing = staticCompositionLocalOf { LuogoSpacing }

/**
 * Colours for controls that float above the map.
 *
 * Deliberately theme-independent and light in both light and dark mode. A dark circle on a
 * map reads as a hole punched in it rather than as a button, so map chrome stays
 * high-contrast against whatever basemap or satellite imagery is underneath.
 */
object MapChrome {
    val container = Color(0xFFF7F9F9)
    val onContainer = Color(0xFF1A2226)
    val emphasisedContainer = Color(0xFF1A88E5)
    val onEmphasisedContainer = Color(0xFFFFFFFF)
    val activeContainer = Color(0xFFDCEBF7)
    val activeOnContainer = Color(0xFF0D4E7F)
    val scrim = Color(0x33000000)
}

private val LuogoDarkColors = darkColorScheme(
    primary = Color(0xFF7FE3D4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF9FFFE9),
    secondary = Color(0xFF9FCAFF),
    onSecondary = Color(0xFF00325B),
    secondaryContainer = Color(0xFF00497F),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4E2600),
    tertiaryContainer = Color(0xFF6F3A00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    // Tonal ladder. Each step is visibly distinct so cards read as raised surfaces rather
    // than flat noise: background is the floor and the container tints climb from there.
    background = Color(0xFF070B0E),
    onBackground = Color(0xFFE3E7EA),
    surface = Color(0xFF0F1519),
    onSurface = Color(0xFFE3E7EA),
    surfaceDim = Color(0xFF070B0E),
    surfaceBright = Color(0xFF343E44),
    surfaceVariant = Color(0xFF2A343A),
    onSurfaceVariant = Color(0xFFB6C2C7),
    surfaceContainerLowest = Color(0xFF05080A),
    surfaceContainerLow = Color(0xFF141B20),
    surfaceContainer = Color(0xFF1B2429),
    surfaceContainerHigh = Color(0xFF253037),
    surfaceContainerHighest = Color(0xFF303C44),
    surfaceTint = Color(0xFF7FE3D4),
    inverseSurface = Color(0xFFE3E7EA),
    inverseOnSurface = Color(0xFF121A1E),
    outline = Color(0xFF8B979D),
    outlineVariant = Color(0xFF2C363C)
)

private val LuogoLightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF2E1),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF1565C0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3FF),
    onSecondaryContainer = Color(0xFF001B3D),
    tertiary = Color(0xFFB25B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF331200),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEEF3F1),
    onBackground = Color(0xFF141A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A1A),
    surfaceDim = Color(0xFFDCE3E1),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E9E6),
    onSurfaceVariant = Color(0xFF3C4745),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF0EE),
    surfaceContainerHighest = Color(0xFFE1E9E6),
    surfaceTint = Color(0xFF00695C),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFEDF2F0),
    outline = Color(0xFF697573),
    outlineVariant = Color(0xFFBFCBC8)
)

/**
 * Full Material 3 type scale.
 *
 * Every style is defined so nothing silently falls back to a default, and line heights are
 * generous enough for large-text accessibility settings without clipping.
 */
val LuogoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 60.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 50.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

val LuogoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun LuogoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Dynamic colour follows the user's wallpaper on Android 12+.
     *
     * Off by default so the map controls, which float above arbitrary wallpaper colours,
     * keep their contrast. Callers can opt in.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> LuogoDarkColors
        else -> LuogoLightColors
    }

    CompositionLocalProvider(LocalSpacing provides LuogoSpacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LuogoTypography,
            shapes = LuogoShapes,
            content = content
        )
    }
}