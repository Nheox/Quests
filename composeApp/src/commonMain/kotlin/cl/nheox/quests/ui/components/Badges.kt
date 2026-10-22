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
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
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

public val LongDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
