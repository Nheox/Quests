package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import cl.nheox.quests.domain.QuickAdd
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.LabelledField
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * A new quest, in the detail column where it will be edited once it exists.
 *
 * Only the title is asked for, because a quest is worth having the moment it
 * has a name: Enter creates it and this pane becomes its editor, with every
 * other field there to set or to leave. The title understands the quick-add
 * syntax, so "call the lab tomorrow 10am ~15m" fills in three fields on the
 * way, and what it understood is shown before Enter is pressed.
 *
 * Nothing is written until Enter: a pane closed with Escape leaves no
 * half-made quest behind in the log.
 */
@Composable
public fun NewQuestPane(
    app: AppState,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val today = app.clock.today()
    val focus = remember { FocusRequester() }
    var showSyntax by remember { mutableStateOf(false) }
    val line = app.quickAddText
    val ready = QuickAdd.parse(line, today).title.isNotBlank()
    fun create() {
        if (ready) onCreate(line)
    }

    Column(
        modifier
            .width(Space.detailWidth)
            .fillMaxHeight()
            .background(theme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.actionNewQuest, style = MaterialTheme.typography.titleMedium, color = theme.text)
            Spacer(Modifier.weight(1f))
            IconAction(Glyph.CROSS, text.actionClose, onClick = { app.closeQuickAdd() })
        }
        HLine()

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Space.md),
        ) {
            LabelledField(text.fieldTitle) {
                QuestTextField(
                    value = line,
                    onValueChange = { app.quickAddText = it },
                    placeholder = text.messageQuickAddPlaceholder,
                    onFocusChange = { app.typing = it },
                    onImeAction = ::create,
                    modifier = Modifier.focusRequester(focus),
                )
            }
            if (line.isNotBlank()) {
                QuickAddPreview(line, today)
                Spacer(Modifier.height(Space.sm))
            }
            // The list's own day, which an undated quest takes so that it
            // lands in the list it was added from rather than out of sight.
            val parsedDue = QuickAdd.parse(line, today).dueDate
            app.defaultDueDate()?.takeIf { parsedDue == null }?.let { due ->
                Text(text.newQuestDefaultDue(due, today), style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
                Spacer(Modifier.height(Space.sm))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                QuestButton(text.actionCreateQuest, primary = true, enabled = ready, onClick = ::create)
                QuestButton(text.actionCancel, onClick = { app.closeQuickAdd() })
            }

            Spacer(Modifier.height(Space.xl))
            Text(text.newQuestTitleHint, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
            Spacer(Modifier.height(Space.sm))
            Text(
                "write methods tomorrow 3pm #thesis !! ~2h",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = theme.progression,
            )
            Spacer(Modifier.height(Space.sm))
            QuestButton(
                if (showSyntax) text.newQuestHideSyntax else text.newQuestShowSyntax,
                compact = true,
                glyph = if (showSyntax) Glyph.CHEVRON_DOWN else Glyph.CHEVRON_RIGHT,
                onClick = { showSyntax = !showSyntax },
            )
            if (showSyntax) {
                Spacer(Modifier.height(Space.sm))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.card, RoundedCornerShape(Radii.md))
                        .padding(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    QUICK_ADD_SYNTAX.forEach { (token, meaning) ->
                        Column {
                            Text(
                                token,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = theme.text,
                            )
                            Text(meaning, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
