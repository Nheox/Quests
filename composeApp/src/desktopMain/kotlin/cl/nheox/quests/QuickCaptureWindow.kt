package cl.nheox.quests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.QuickAdd
import cl.nheox.quests.i18n.Strings
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.theme

/**
 * The global quick-capture window: one field, type, enter, gone.
 *
 * §7.4 calls this the highest-value desktop feature, and the reason is the one
 * below — a thought that has to wait for an app to be found, focused and
 * navigated is a thought that gets lost. It opens focused, closes on Escape or
 * on submit, and never shows the rest of the app.
 */
@Composable
public fun QuickCaptureContent(
    clock: Clock,
    text: Strings,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val parsed = remember(input) { QuickAdd.parse(input, clock.today()) }

    fun submit() {
        if (input.isNotBlank()) {
            onSubmit(input)
            input = ""
        }
        onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.surface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onDismiss()
                        true
                    }

                    Key.Enter, Key.NumPadEnter -> {
                        submit()
                        true
                    }

                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                QuestIcon(Glyph.TARGET, size = 16.dp, tint = theme.progression)
                Text(text.actionQuickCapture, style = MaterialTheme.typography.labelMedium, color = theme.textFaint)
            }
            Spacer(Modifier.height(Space.sm))
            QuestTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = text.messageQuickAddPlaceholder,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.focusRequester(focus),
                onImeAction = { submit() },
            )

            if (input.isNotBlank()) {
                Spacer(Modifier.height(Space.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parsed.difficulty?.let { Pill(text.difficulty(it), color = Palette.of(it)) }
                    parsed.fear?.let { Pill(text.fear(it), color = Palette.of(it)) }
                    parsed.urgency?.let { Pill(text.urgency(it), color = Palette.of(it)) }
                    parsed.estimatedMinutes?.let { Pill(Dates.formatMinutes(it), leading = Glyph.CLOCK) }
                    parsed.dueDate?.let { date ->
                        Pill(
                            date.toString() + (parsed.dueTime?.let { " $it" } ?: ""),
                            leading = Glyph.CALENDAR,
                            color = theme.accent,
                        )
                    }
                    parsed.skillNames.forEach { Pill("#$it", color = theme.progression, leading = Glyph.SPARK) }
                }
            }
        }

        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
