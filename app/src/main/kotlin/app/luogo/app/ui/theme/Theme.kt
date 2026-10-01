package app.luogo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LuogoDarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00251A),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF004B6E),
    onSecondaryContainer = Color(0xFFE1F5FE),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF0E1416),
    onBackground = Color(0xFFE1E6E8),
    surface = Color(0xFF141C1F),
    onSurface = Color(0xFFE1E6E8),
    surfaceVariant = Color(0xFF212D31),
    onSurfaceVariant = Color(0xFFBDC8CC),
    error = Color(0xFFFF8A80)
)

private val LuogoLightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5FE),
    onSecondaryContainer = Color(0xFF001E32),
    tertiary = Color(0xFFEF6C00),
    onTertiary = Color.White,
    background = Color(0xFFF6FAFA),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFE3ECEE),
    onSurfaceVariant = Color(0xFF3F484A),
    error = Color(0xFFC62828)
)

val LuogoTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun LuogoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuogoTypography,
        content = content
    )
}
