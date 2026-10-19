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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.RepeatUnit
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
public fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = theme.textFaint,
        modifier = modifier.padding(bottom = Space.xs),
    )
}

@Composable
public fun LabelledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = Space.md)) {
        FieldLabel(label)
        content()
    }
}

/**
 * A text field built on [BasicTextField] rather than Material's `TextField`.
 *
 * Material's text fields bring a 56dp minimum height and a floating label,
 * which in a dense desktop form wastes about a third of the window. The trade
 * is that focus, border and placeholder are ours to draw — which they had to be
 * anyway, to match the rest of the design system.
 *
 * [onFocusChange] exists so the app can suspend single-letter shortcuts while
 * the user is typing. Without it, typing "c" in a title field would complete
 * the selected quest.
 */
@Composable
public fun QuestTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    numeric: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onFocusChange: (Boolean) -> Unit = {},
    onImeAction: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background, RoundedCornerShape(Radii.md))
            .border(
                BorderStroke(1.dp, if (focused) theme.accent else theme.border),
                RoundedCornerShape(Radii.md),
            )
            .padding(horizontal = Space.sm, vertical = Space.sm),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, style = textStyle, color = theme.textFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = theme.text),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = if (onImeAction != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onImeAction?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                },
        )
    }
}

/**
 * One choice out of a handful, laid out as a row of segments.
 *
 * Used for difficulty, fear and urgency. A dropdown would hide the scale; the
 * whole point of a seven-step difficulty rating is seeing where this quest sits
 * on it, and a closed dropdown shows one value out of seven.
 */
@Composable
public fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    // Not a plain `(T) -> Color` with a default: a default argument cannot read
    // a composition local, and the fallback tint is the theme's accent.
    tint: ((T) -> Color)? = null,
    compact: Boolean = false,
) {
    val fallback = theme.accent
    val tintOf: (T) -> Color = tint ?: { fallback }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background, RoundedCornerShape(Radii.md))
            .border(BorderStroke(1.dp, theme.border), RoundedCornerShape(Radii.md))
            .padding(Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        options.forEach { option ->
            val chosen = option == selected
            val interaction = remember(option) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        when {
                            chosen -> tintOf(option).copy(alpha = 0.22f)
                            hovered -> theme.hover
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(Radii.sm),
                    )
                    .focusRing(RoundedCornerShape(Radii.sm))
                    .clickableRow(interaction = interaction) { onSelect(option) }
                    .padding(vertical = if (compact) Space.xs else Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) tintOf(option) else theme.textMuted,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
public fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = theme.text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
            }
        }
        Spacer(Modifier.width(Space.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.onAccent,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = theme.textFaint,
                uncheckedTrackColor = theme.card,
                uncheckedBorderColor = theme.border,
            ),
        )
    }
}

/** A numeric stepper for minutes, costs and quantities. */
@Composable
public fun NumberField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    step: Int = 5,
    minimum: Int = 0,
    placeholder: String = "—",
    onFocusChange: (Boolean) -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        IconAction(Glyph.MINUS, "−", size = 26.dp, onClick = {
            val next = ((value ?: 0) - step).coerceAtLeast(minimum)
            onValueChange(if (next <= 0 && minimum <= 0) null else next)
        })
        Box(Modifier.width(78.dp)) {
            QuestTextField(
                value = value?.toString() ?: "",
                onValueChange = { text ->
                    val digits = text.filter { it.isDigit() }.take(6)
                    onValueChange(digits.toIntOrNull()?.coerceAtLeast(minimum))
                },
                placeholder = placeholder,
                numeric = true,
                onFocusChange = onFocusChange,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
        }
        IconAction(Glyph.PLUS, "+", size = 26.dp, onClick = {
            onValueChange(((value ?: 0) + step).coerceAtLeast(minimum))
        })
        if (suffix.isNotEmpty()) {
            Text(suffix, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
        }
    }
}

/**
 * A month grid, written here rather than taken from Material.
 *
 * Material3's `DatePicker` is experimental across Compose Multiplatform
 * versions, is tuned for a touch target, and is several hundred dp tall. This
 * one fits a popover, is fully driven from the keyboard by the surrounding
 * shortcuts, and will not change shape under a dependency bump.
 */
@Composable
public fun MonthGrid(
    month: LocalDate,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    Column(modifier.width(266.dp).padding(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.CHEVRON_UP, "−1", size = 26.dp, onClick = { onMonthChange(month.minusMonths(1)) })
            Text(
                text = month.format(monthTitle),
                style = MaterialTheme.typography.titleSmall,
                color = theme.text,
                modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
            )
            IconAction(Glyph.CHEVRON_DOWN, "+1", size = 26.dp, onClick = { onMonthChange(month.plusMonths(1)) })
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth()) {
            // Monday-based, which is both the ISO week and the Chilean one.
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW_STANDALONE, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textFaint,
                    modifier = Modifier.weight(1f).padding(vertical = Space.xxs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        val first = month.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val cells = buildList {
            repeat(lead) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.withDayOfMonth(day))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.heightIn(max = 220.dp),
        ) {
            items(cells) { date ->
                if (date == null) {
                    Box(Modifier.size(34.dp))
                } else {
                    val isSelected = date == selected
                    val isToday = date == today
                    val interaction = remember(date) { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .padding(1.dp)
                            .background(
                                when {
                                    isSelected -> theme.accent
                                    hovered -> theme.hover
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(Radii.sm),
                            )
                            .border(
                                BorderStroke(1.dp, if (isToday && !isSelected) theme.accent else Color.Transparent),
                                RoundedCornerShape(Radii.sm),
                            )
                            .focusRing(RoundedCornerShape(Radii.sm))
                            .clickableRow(interaction = interaction) { onSelect(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) theme.onAccent else theme.text,
                        )
                    }
                }
            }
        }
    }
}

/** `HH:mm`, parsed forgivingly and only committed when it is actually valid. */
@Composable
public fun TimeField(
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var text by remember(value) { mutableStateOf(value?.format(timeOnly) ?: "") }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(Modifier.width(92.dp)) {
            QuestTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed.filter { it.isDigit() || it == ':' }.take(5)
                    onValueChange(parseTime(text))
                },
                placeholder = "--:--",
                onFocusChange = onFocusChange,
            )
        }
        if (value != null) {
            IconAction(Glyph.CROSS, strings.actionClear, size = 26.dp, onClick = {
                text = ""
                onValueChange(null)
            })
        }
    }
}

internal fun parseTime(text: String): LocalTime? {
    val cleaned = text.trim()
    val parts = cleaned.split(':')
    return when {
        parts.size == 2 -> {
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }

        cleaned.length == 4 && cleaned.all { it.isDigit() } -> {
            val hour = cleaned.substring(0, 2).toInt()
            val minute = cleaned.substring(2, 4).toInt()
            if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }

        else -> null
    }
}

@Composable
public fun RepetitionField(
    value: Repetition?,
    onValueChange: (Repetition?) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val text = strings
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NumberField(
                value = value?.every,
                onValueChange = { every ->
                    if (every == null || every < 1) {
                        onValueChange(null)
                    } else {
                        onValueChange(Repetition(value?.unit ?: RepeatUnit.DAY, every))
                    }
                },
                step = 1,
                minimum = 1,
                placeholder = "—",
                onFocusChange = onFocusChange,
            )
            if (value != null) {
                IconAction(Glyph.CROSS, strings.actionClear, size = 26.dp, onClick = { onValueChange(null) })
            }
        }
        if (value != null) {
            Spacer(Modifier.height(Space.xs))
            SegmentedSelector(
                options = RepeatUnit.entries,
                selected = value.unit,
                onSelect = { onValueChange(value.copy(unit = it)) },
                label = { text.repeatUnit(it) },
                compact = true,
            )
        }
    }
}

private val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
