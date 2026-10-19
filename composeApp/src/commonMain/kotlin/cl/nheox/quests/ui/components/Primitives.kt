package cl.nheox.quests.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.theme

/**
 * The small, shared pieces every screen is built from.
 *
 * Deliberately not Material's buttons and cards. Material's defaults carry
 * their own spacing, elevation and ripple opinions, and fighting them at every
 * call site is how an app ends up looking like a Material app with a different
 * accent colour — which is precisely the outcome §7.6 is trying to avoid.
 */
@Composable
public fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = theme.textMuted,
    background: Color = color.copy(alpha = 0.14f),
    leading: Glyph? = null,
    bold: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(Radii.sm))
            .padding(horizontal = Space.sm, vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (leading != null) QuestIcon(leading, size = 11.dp, tint = color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
public fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textFaint,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f).height(1.dp).background(theme.border))
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

@Composable
public fun HLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(theme.border))
}

@Composable
public fun VLine(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(theme.border))
}

/**
 * A button that is a button: one rectangle, one label, a hover state and a
 * visible focus ring. The focus ring is not decoration — it is the whole of
 * "keyboard first" (§3.4) made visible, and an app whose focus is invisible is
 * keyboard-navigable only in theory.
 */
@Composable
public fun QuestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = when {
        danger -> theme.danger
        primary -> theme.accent
        else -> theme.text
    }
    val fill = when {
        !enabled -> Color.Transparent
        primary -> if (hovered) theme.accent else theme.accent.copy(alpha = 0.88f)
        danger -> if (hovered) theme.danger.copy(alpha = 0.18f) else theme.danger.copy(alpha = 0.10f)
        hovered -> theme.hover
        else -> theme.card
    }
    val content = if (primary && enabled) theme.onAccent else accent

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(fill, RoundedCornerShape(Radii.md))
            .border(
                BorderStroke(1.dp, if (primary) Color.Transparent else theme.border),
                RoundedCornerShape(Radii.md),
            )
            .focusRing()
            .clickableRow(enabled = enabled, interaction = interaction, onClick = onClick)
            .padding(
                horizontal = if (compact) Space.sm else Space.md,
                vertical = if (compact) Space.xs else Space.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (glyph != null) QuestIcon(glyph, size = 14.dp, tint = content)
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
public fun IconAction(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = theme.textMuted,
    size: Dp = 30.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .background(if (hovered && enabled) theme.hover else Color.Transparent, RoundedCornerShape(Radii.sm))
            .focusRing()
            .clickableRow(enabled = enabled, interaction = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        QuestIcon(glyph, size = size * 0.52f, tint = if (hovered && enabled) theme.text else tint, description = description)
    }
}

@Composable
public fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    glyph: Glyph = Glyph.INBOX,
    hint: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        QuestIcon(glyph, size = 40.dp, tint = theme.textFaint.copy(alpha = 0.55f))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = theme.textMuted)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A labelled key, for the help screen and the command palette. */
@Composable
public fun KeyCap(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = theme.textMuted,
        modifier = modifier
            .background(theme.background, RoundedCornerShape(Radii.sm))
            .border(BorderStroke(1.dp, theme.border), RoundedCornerShape(Radii.sm))
            .padding(horizontal = Space.xs + 1.dp, vertical = 2.dp),
    )
}
