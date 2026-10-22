package cl.nheox.quests.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Pin
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate

/**
 * One quest in a list.
 *
 * The whole app is judged on this row, so it is worth being explicit about the
 * priorities: the title must be readable first, the three ratings must be
 * distinguishable without reading, and the row must stay 44dp high so a
 * screenful is a useful amount of information rather than six cards.
 *
 * The urgency stripe is on the leading edge, where the eye already is when
 * scanning a left-aligned list.
 */
@Composable
public fun QuestRow(
    quest: Quest,
    today: LocalDate,
    selected: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    onToggleMark: () -> Unit,
    onComplete: () -> Unit,
    onFail: () -> Unit,
    modifier: Modifier = Modifier,
    childCount: Int = 0,
    indent: Int = 0,
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val text = strings

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Space.rowHeight)
            .background(
                when {
                    selected -> theme.accent.copy(alpha = 0.16f)
                    marked -> theme.progression.copy(alpha = 0.10f)
                    hovered -> theme.hover
                    else -> Color.Transparent
                },
                RoundedCornerShape(Radii.md),
            )
            .focusRing()
            .clickableRow(interaction = interaction, onClick = onClick)
            .padding(start = Space.xs + (indent * 18).dp, end = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrgencyBadge(quest.urgency)
        Spacer(Modifier.width(Space.sm))

        // The checkbox is the complete action. In a quest tracker, ticking a box
        // and finishing a quest are the same gesture, so they are the same
        // control rather than a box plus a button that mean the same thing.
        CompleteBox(
            state = quest.state,
            onClick = onComplete,
            modifier = Modifier,
        )

        Spacer(Modifier.width(Space.sm))

        if (onToggleExpand != null && childCount > 0) {
            IconAction(
                glyph = if (expanded == true) Glyph.CHEVRON_DOWN else Glyph.CHEVRON_RIGHT,
                description = text.messageSubQuests,
                size = 20.dp,
                onClick = onToggleExpand,
            )
            Spacer(Modifier.width(Space.xxs))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = quest.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (quest.isOpen) theme.text else theme.textFaint,
                textDecoration = if (quest.isDone) TextDecoration.LineThrough else null,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (quest.notes.isNotBlank() || childCount > 0) {
                Text(
                    text = if (childCount > 0) text.countSubQuests(childCount) else quest.notes.lineSequence().first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (quest.isRepeating) QuestIcon(Glyph.REPEAT, size = 12.dp, tint = theme.textFaint)
            if (quest.reminders.isNotEmpty()) QuestIcon(Glyph.BELL, size = 12.dp, tint = theme.textFaint)
            FearBadge(quest.fear)
            DifficultyBadge(quest.difficulty)
            DueChip(quest, today)
            // Hover reveals the fail action rather than keeping it on every row:
            // failing is deliberate, and a row with two destructive buttons on it
            // invites the wrong one.
            if (hovered && quest.isOpen) {
                IconAction(Glyph.CROSS, text.actionFail, size = 24.dp, tint = theme.danger, onClick = onFail)
            } else {
                XpPill(quest.projectedXp())
            }
            // A pin places an open quest; on a finished one, in a history list
            // that ignores pins, the marker would be noise.
            PinMarker(if (quest.isOpen) quest.pin else Pin.NONE)
            MarkDot(marked = marked, onClick = onToggleMark)
        }
    }
}

/**
 * A pinned quest's marker, in a slot every row reserves.
 *
 * The slot is there whether or not the quest is pinned, so pinning or clearing
 * a pin never shifts the row's trailing edge, and the marker lands at the same
 * x in every row: a column of pins down the right reads at a glance.
 */
@Composable
private fun PinMarker(pin: Pin) {
    val text = strings
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        when (pin) {
            Pin.TOP -> QuestIcon(Glyph.PIN, size = 12.dp, tint = theme.textMuted, description = text.pinnedToTop)
            Pin.BOTTOM -> QuestIcon(Glyph.PIN, size = 12.dp, tint = theme.textMuted, description = text.pinnedToBottom)
            Pin.NONE -> Unit
        }
    }
}

@Composable
private fun CompleteBox(
    state: QuestState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val text = strings
    val border = when (state) {
        QuestState.OPEN -> if (hovered) theme.success else theme.borderStrong
        QuestState.DONE -> theme.success
        QuestState.FAILED -> theme.danger
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .background(
                when (state) {
                    QuestState.DONE -> theme.success.copy(alpha = 0.22f)
                    QuestState.FAILED -> theme.danger.copy(alpha = 0.18f)
                    else -> if (hovered) theme.success.copy(alpha = 0.12f) else Color.Transparent
                },
                RoundedCornerShape(Radii.sm),
            )
            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(Radii.sm))
            .focusRing(RoundedCornerShape(Radii.sm))
            .clickableRow(enabled = state == QuestState.OPEN, interaction = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            QuestState.DONE -> QuestIcon(Glyph.CHECK, size = 13.dp, tint = theme.success, description = text.actionComplete)
            QuestState.FAILED -> QuestIcon(Glyph.CROSS, size = 13.dp, tint = theme.danger, description = text.actionFail)
            QuestState.OPEN -> if (hovered) {
                QuestIcon(Glyph.CHECK, size = 13.dp, tint = theme.success, description = text.actionComplete)
            }
        }
    }
}

/** The multi-selection handle. Small on purpose: it is not the primary action. */
@Composable
private fun MarkDot(marked: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val markColour = theme.progression
    val hoverColour = theme.textMuted
    Box(
        modifier = Modifier
            .size(18.dp)
            .focusRing(RoundedCornerShape(Radii.pill))
            .clickableRow(interaction = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(9.dp)) {
            if (marked) {
                drawCircle(markColour)
            } else {
                drawCircle(
                    color = if (hovered) hoverColour else Color.Transparent,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                )
            }
        }
    }
}
