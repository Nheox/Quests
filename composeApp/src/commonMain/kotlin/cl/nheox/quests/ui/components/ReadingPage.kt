package cl.nheox.quests.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.theme

/*
 * The reading page (the owner's layout 6): one column of sections, each row
 * a name, a short explanation, and a control. Settings uses it, and so will
 * the profile.
 */

/**
 * A section of a reading page: its name in small capitals and a rule to the
 * column's edge, which on a page this wide is what holds a section together.
 */
@Composable
public fun ReadingSection(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textFaint,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.weight(1f).height(1.dp).background(theme.border))
    }
}

/**
 * One setting: what it is, in a word or two; what it does, in a line; and the
 * control at the right, where the eye finds every control in the same column.
 */
@Composable
public fun SettingRow(
    name: String,
    explanation: String?,
    control: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = theme.text, fontWeight = FontWeight.SemiBold)
            if (!explanation.isNullOrBlank()) {
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            content = control,
        )
    }
}

/**
 * A choice among a few named values, as a compact button showing the current
 * one with a chevron, and a menu of the rest. [highlight] tints the value
 * teal, for a choice that is itself a place in the app, like where it opens.
 */
@Composable
public fun <T> ChoiceButton(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    highlight: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radii.md)
    Box {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .background(theme.card, shape)
                .border(1.dp, theme.border, shape)
                .focusRing(shape)
                .clickableRow(onClick = { open = true })
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                label(value),
                style = MaterialTheme.typography.labelLarge,
                color = if (highlight) theme.accent else theme.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            QuestIcon(Glyph.CHEVRON_DOWN, size = 12.dp, tint = theme.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = theme.card) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            color = if (option == value) theme.accent else theme.text,
                            fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** Space at the foot of a reading page, so its last row is never at the window's edge. */
@Composable
public fun ReadingPageEnd() {
    Spacer(Modifier.height(Space.xxl))
}

/** The width a reading page's column stops at, so lines stay readable on a wide window. */
public val ReadingWidth: androidx.compose.ui.unit.Dp = 760.dp
