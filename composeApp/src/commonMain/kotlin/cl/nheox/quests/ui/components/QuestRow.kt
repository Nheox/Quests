package cl.nheox.quests.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Pin
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Motion
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate

/**
 * One quest in a list, as a card (the owner's list + detail layout).
 *
 * The whole app is judged on this row, so it is worth being explicit about the
 * priorities: the title must be readable first, then one line saying when it
 * is due and how long it takes, then the ratings and the payout at the right,
 * distinguishable without reading. Every card is the same height whatever it
 * holds, so the list never jumps as quests change.
 *
 * Selection is a teal tint with a teal edge; hover lays a faint highlight on
 * top of whatever the base is, so a selected card still answers the pointer
 * (DESIGN §5: compound states layer, they do not replace each other).
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
    subtitle: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val text = strings
    val shape = RoundedCornerShape(Radii.md)
    val overdue = quest.isOpen && quest.dueDate?.isBefore(today) == true

    Row(
        modifier = modifier
            .padding(start = (indent * 18).dp)
            .fillMaxWidth()
            .height(RowHeight)
            .background(
                when {
                    selected -> theme.accent.copy(alpha = 0.14f)
                    marked -> theme.progression.copy(alpha = 0.10f)
                    else -> theme.card
                },
                shape,
            )
            .then(if (selected) Modifier.border(BorderStroke(1.dp, theme.accent.copy(alpha = 0.55f)), shape) else Modifier)
            .background(if (hovered) theme.text.copy(alpha = 0.04f) else Color.Transparent, shape)
            .focusRing(shape)
            .clickableRow(interaction = interaction, onClick = onClick)
            .padding(start = Space.md, end = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The checkbox is the complete action. In a quest tracker, ticking a box
        // and finishing a quest are the same gesture, so they are the same
        // control rather than a box plus a button that mean the same thing.
        CompleteBox(
            state = quest.state,
            onClick = onComplete,
            modifier = Modifier,
        )

        Spacer(Modifier.width(Space.md))

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
            // A view can say something more useful here — the Reminders list
            // says when the next reminder fires. Otherwise: when it is due, how
            // long it takes, how urgent it is, how much hangs under it.
            val secondLine = subtitle ?: rowMeta(quest, today, childCount)
            if (secondLine != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    // Red as a mark, not as text colour: body-sized red text
                    // fails contrast on a card, a dot of it does not have to.
                    if (overdue && subtitle == null) Box(Modifier.size(6.dp).background(theme.danger, CircleShape))
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.width(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (quest.isRepeating) QuestIcon(Glyph.REPEAT, size = 12.dp, tint = theme.textFaint)
            if (quest.reminders.isNotEmpty()) QuestIcon(Glyph.BELL, size = 12.dp, tint = theme.textFaint)
            FearBadge(quest.fear)
            DifficultyBadge(quest.difficulty)
            // The payout keeps its place while the fail action fades in over
            // it, so the slot is always the pill's own width and the trailing
            // edge never moves as the pointer crosses the row (DESIGN §2).
            // Hover reveals the fail action rather than keeping it on every
            // row: failing is deliberate, and a row with two destructive
            // buttons on it invites the wrong one.
            val failing = hovered && quest.isOpen
            val payout by animateFloatAsState(if (failing) 0f else 1f, Motion.tween(Motion.SHORT))
            Box(contentAlignment = Alignment.CenterEnd) {
                XpPill(quest.projectedXp(), Modifier.alpha(payout))
                if (failing) {
                    IconAction(Glyph.CROSS, text.actionFail, size = 24.dp, tint = theme.danger, onClick = onFail)
                }
            }
            // A pin places an open quest; on a finished one, in a history list
            // that ignores pins, the marker would be noise.
            PinMarker(if (quest.isOpen) quest.pin else Pin.NONE)
            MarkDot(marked = marked, onClick = onToggleMark)
        }
    }
}

/** Every card is this tall, one line or two, so nothing shifts as rows change. */
private val RowHeight = 56.dp

/** "due today 15:00 · 90 min · high urgency · 3 sub-quests", or null when there is nothing to say. */
@Composable
private fun rowMeta(quest: Quest, today: LocalDate, childCount: Int): String? {
    val text = strings
    val parts = listOfNotNull(
        quest.dueDate?.takeIf { quest.isOpen }?.let { text.dueMeta(it, quest.dueTime, today) },
        quest.estimatedMinutes?.let { text.durationShort(it) },
        quest.urgency.takeIf { quest.isOpen && it.index >= Urgency.HIGH.index }?.let { text.urgencyMeta(it) },
        childCount.takeIf { it > 0 }?.let { text.countSubQuests(it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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

/**
 * The complete action, as the box it is ticked in. Shared with the Standing
 * screen's picks, so finishing a quest is the same gesture everywhere.
 */
@Composable
internal fun CompleteBox(
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
