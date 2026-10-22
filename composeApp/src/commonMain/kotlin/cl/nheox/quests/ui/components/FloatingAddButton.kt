package cl.nheox.quests.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Motion
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.theme

/**
 * The way to add a quest: a teal square floating over the bottom-right corner
 * of a list, where it is in the same place whatever the list holds and never
 * scrolls away.
 *
 * Pointing at it names what it does and its key, beside it rather than over
 * it, so the label never covers the button it explains.
 */
@Composable
public fun FloatingAddButton(
    description: String,
    shortcut: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val grow by animateFloatAsState(if (hovered) 1.06f else 1f, Motion.tween(Motion.SHORT))
    val shape = RoundedCornerShape(Radii.lg)

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        AnimatedVisibility(hovered, enter = fadeIn(Motion.tween(Motion.SHORT)), exit = fadeOut(Motion.tween(Motion.SHORT))) {
            Row(
                Modifier
                    .shadow(4.dp, RoundedCornerShape(Radii.md))
                    .background(theme.card, RoundedCornerShape(Radii.md))
                    .padding(horizontal = Space.md, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(description, style = MaterialTheme.typography.labelLarge, color = theme.text, maxLines = 1)
                KeyCap(shortcut)
            }
        }
        Box(
            Modifier
                .size(ButtonSize)
                .scale(grow)
                .shadow(if (hovered) 10.dp else 6.dp, shape)
                .background(if (hovered) theme.accent else theme.accent.copy(alpha = 0.92f), shape)
                .focusRing(shape)
                .clickableRow(interaction = interaction, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            QuestIcon(Glyph.PLUS, size = 22.dp, tint = theme.onAccent)
        }
    }
}

private val ButtonSize = 52.dp

/** What a list leaves free below its last row, so the button never covers it. */
public val FloatingButtonClearance: androidx.compose.ui.unit.Dp = ButtonSize + Space.xl * 2
