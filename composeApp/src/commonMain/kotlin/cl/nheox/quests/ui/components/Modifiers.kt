package cl.nheox.quests.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.theme

/**
 * Draws a ring when the element has keyboard focus.
 *
 * This is the single most important modifier in the app. §3.4 commits to
 * "everything reachable without the mouse", and a focus indicator you cannot
 * see makes that promise technically true and practically worthless — the user
 * is navigating blind. Material's default indication is tuned for touch ripple,
 * so the ring is ours.
 *
 * Must be placed *before* whatever makes the element focusable (usually
 * [clickableRow]), because `onFocusChanged` observes the focus target declared
 * after it in the chain.
 */
@Composable
public fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(Radii.md),
    color: Color = theme.accent,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            BorderStroke(if (focused) 2.dp else 0.dp, if (focused) color else Color.Transparent),
            shape,
        )
}

/**
 * Clickable without Material's ripple.
 *
 * The ripple is a touch affordance; on a desktop pointer it reads as lag. Hover
 * and focus carry the feedback instead, which is why every caller passes its
 * own interaction source.
 */
@Composable
public fun Modifier.clickableRow(
    enabled: Boolean = true,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier = this
    .hoverable(interaction, enabled = enabled)
    .clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
