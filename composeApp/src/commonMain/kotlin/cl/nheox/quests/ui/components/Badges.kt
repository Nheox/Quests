package cl.nheox.quests.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A rating shown as a row of filled and empty steps.
 *
 * Three independent ratings per quest is a lot to put on one line, so none of
 * them is a number and none is a word. A seven-step bar for difficulty and a
 * five-step bar for fear are distinguishable at a glance by *length* before
 * colour is even considered, which is what makes a dense list scannable — and
 * what keeps it readable for someone who cannot tell the two ramps apart by
 * hue.
 */
@Composable
public fun StepMeter(
    filled: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    stepWidth: androidx.compose.ui.unit.Dp = 3.dp,
    stepHeight: androidx.compose.ui.unit.Dp = 12.dp,
    gap: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val empty = theme.borderStrong.copy(alpha = 0.55f)
    Canvas(
        modifier = modifier
            .width(stepWidth * total + gap * (total - 1))
            .height(stepHeight),
    ) {
        val stepPx = stepWidth.toPx()
        val gapPx = gap.toPx()
        for (index in 0 until total) {
            // Each step grows a little taller than the last, so "how far along
            // this scale" is legible without counting.
            val fraction = 0.5f + 0.5f * ((index + 1).toFloat() / total)
            val barHeight = size.height * fraction
            drawRoundRect(
                color = if (index < filled) color else empty,
                topLeft = Offset(index * (stepPx + gapPx), size.height - barHeight),
                size = Size(stepPx, barHeight),
                cornerRadius = CornerRadius(1.2f, 1.2f),
            )
        }
    }
}

@Composable
public fun DifficultyBadge(difficulty: Difficulty, modifier: Modifier = Modifier, withLabel: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        StepMeter(
            filled = difficulty.index + 1,
            total = Difficulty.entries.size,
            color = Palette.of(difficulty),
        )
        if (withLabel) Pill(strings.difficulty(difficulty), color = Palette.of(difficulty))
    }
}

@Composable
public fun FearBadge(fear: Fear, modifier: Modifier = Modifier, withLabel: Boolean = false) {
    // Fear None is the default and the common case; drawing nothing for it
    // keeps the list quiet and makes any fear at all stand out.
    if (fear == Fear.NONE && !withLabel) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
        cl.nheox.quests.ui.theme.QuestIcon(Glyph.FLAME, size = 12.dp, tint = Palette.of(fear))
        StepMeter(
            filled = fear.index,
            total = Fear.entries.size - 1,
            color = Palette.of(fear),
            stepHeight = 10.dp,
        )
        if (withLabel) Pill(strings.fear(fear), color = Palette.of(fear))
    }
}

@Composable
public fun UrgencyBadge(urgency: Urgency, modifier: Modifier = Modifier, withLabel: Boolean = false) {
    if (urgency == Urgency.NORMAL && !withLabel) return
    val color = Palette.of(urgency)
    if (withLabel) {
        Pill(strings.urgency(urgency), modifier = modifier, color = color)
    } else {
        Canvas(modifier.size(width = 4.dp, height = 14.dp)) {
            drawRoundRect(color = color, cornerRadius = CornerRadius(2f, 2f))
        }
    }
}

@Composable
public fun XpPill(xp: Int, modifier: Modifier = Modifier) {
    Pill(
        text = strings.xpAmount(xp),
        modifier = modifier,
        color = theme.progression,
        leading = Glyph.SPARK,
        bold = true,
    )
}

@Composable
public fun PointsPill(points: Int, modifier: Modifier = Modifier) {
    Pill(
        text = points.toString(),
        modifier = modifier,
        color = theme.accent,
        leading = Glyph.COIN,
    )
}

/**
 * The due date, coloured by how late it is.
 *
 * Relative wording ("today", "in 3 days") beats an absolute date in a list,
 * because the question a list answers is "is this urgent", not "what is the
 * date". The absolute date is in the editor, where that *is* the question.
 */
@Composable
public fun DueChip(quest: Quest, today: LocalDate, modifier: Modifier = Modifier) {
    val due = quest.dueDate ?: return
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    val color = when {
        days < 0 -> theme.danger
        days == 0L -> theme.warning
        days == 1L -> theme.textMuted
        else -> theme.textFaint
    }
    val label = buildString {
        append(
            when {
                days == 0L -> strings.rescheduleToday
                days == 1L -> strings.rescheduleTomorrow
                days < 0 -> "${-days}d"
                days <= 7 -> "${days}d"
                else -> due.format(shortDate)
            },
        )
        quest.dueTime?.let { append(" ").append(it.format(timeFormat)) }
    }
    Pill(label, modifier = modifier, color = color, leading = if (quest.dueTime != null) Glyph.CLOCK else Glyph.CALENDAR)
}

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

public val LongDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
