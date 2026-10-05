package app.luogo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.luogo.app.ui.theme.LuogoSpacing

/**
 * Shared building blocks.
 *
 * Every screen composes from these so spacing, corner radius and empty-state behaviour stay
 * consistent instead of drifting per screen.
 */

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LuogoSpacing.medium, vertical = LuogoSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LuogoSpacing.medium, vertical = LuogoSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/**
 * Shown when a list has nothing in it.
 *
 * States the situation and says what to do next, rather than showing an empty void or
 * placeholder rows.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LuogoSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(LuogoSpacing.medium))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(LuogoSpacing.xSmall))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(LuogoSpacing.medium))
            action()
        }
    }
}

/** Small metric tile used for distance, duration and counts. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Circular initials avatar tinted with the person's configured colour. */
@Composable
fun InitialsAvatar(
    name: String,
    colorArgb: Long,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    ringColor: Color? = null
) {
    Box(
        modifier = modifier
            .size(if (ringColor != null) size + 8.dp else size)
            .then(
                if (ringColor != null) {
                    Modifier.border(3.dp, ringColor, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(colorArgb.toInt()), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Status chip whose colour reflects how fresh the underlying data is.
 *
 * LIVE, recently seen, stale and unknown are visually distinct, so a glance is enough.
 */
@Composable
fun FreshnessChip(
    label: String,
    tone: FreshnessTone,
    modifier: Modifier = Modifier
) {
    val container = when (tone) {
        FreshnessTone.LIVE -> MaterialTheme.colorScheme.primaryContainer
        FreshnessTone.RECENT -> MaterialTheme.colorScheme.tertiaryContainer
        FreshnessTone.STALE -> MaterialTheme.colorScheme.surfaceContainerHighest
        FreshnessTone.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (tone) {
        FreshnessTone.LIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        FreshnessTone.RECENT -> MaterialTheme.colorScheme.onTertiaryContainer
        FreshnessTone.STALE -> MaterialTheme.colorScheme.onSurfaceVariant
        FreshnessTone.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = container,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

enum class FreshnessTone { LIVE, RECENT, STALE, UNKNOWN }

/**
 * Rounded icon tile used to give list rows a visual identity.
 *
 * A list of item cards with nothing but text reads as a debug dump; a tinted icon per
 * category is what makes a list scannable.
 */
@Composable
fun CategoryIconTile(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** Label/value row used throughout the detail sheets. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(116.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Inline notice, used for warnings and for "this is unavailable" explanations. */
@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
    tone: NoticeTone = NoticeTone.INFO
) {
    val container = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
        NoticeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        NoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        NoticeTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        NoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(10.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

enum class NoticeTone { INFO, WARNING, ERROR }

val ScreenPadding = PaddingValues(
    horizontal = LuogoSpacing.medium,
    vertical = LuogoSpacing.small
)

val ScreenGaps = Arrangement.spacedBy(LuogoSpacing.small)