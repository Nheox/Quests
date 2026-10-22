package cl.nheox.quests.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Pin
import cl.nheox.quests.domain.Priority
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.QuestFields
import cl.nheox.quests.projection.QuestDraft
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.FieldLabel
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.LabelledField
import cl.nheox.quests.ui.components.MonthGrid
import cl.nheox.quests.ui.components.NumberField
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.components.RepetitionField
import cl.nheox.quests.ui.components.SegmentedSelector
import cl.nheox.quests.ui.components.TimeField
import cl.nheox.quests.ui.components.XpPill
import cl.nheox.quests.ui.components.clickableRow
import cl.nheox.quests.ui.components.focusRing
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Palette
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * The detail pane: edits a quest in place, field by field.
 *
 * There is no Save button, and that is deliberate. Every control writes a
 * single-field event the moment it changes, which is what makes last-write-wins
 * meaningful across devices — a form that saved the whole object on exit would
 * clobber another device's edit to a field this one never touched. The event
 * log also means there is nothing to lose if the window closes mid-edit.
 *
 * Text fields are the exception: they commit on focus loss rather than per
 * keystroke, because one event per character would be an absurd log.
 */
@Composable
public fun QuestEditorPane(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val quest = state.quest(app.selectedId)

    Column(
        modifier
            .width(Space.detailWidth)
            .fillMaxHeight()
            .background(theme.surface),
    ) {
        if (quest == null) {
            EmptyState(
                message = text.messageNoQuests,
                hint = text.questsEmpty,
                glyph = Glyph.TARGET,
            )
            return@Column
        }

        EditorHeader(app, state, quest)
        HLine()

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.md),
        ) {
            if (quest.isOpen) PriorityStrip(app, quest)
            TitleAndNotes(app, quest)

            FieldSection(text.fieldDifficulty)
            SegmentedSelector(
                options = Difficulty.entries,
                selected = quest.difficulty,
                onSelect = { app.store.changeQuest(quest.id, FieldPatch().setEnum(QuestFields.DIFFICULTY, it)) },
                label = { it.index.plus(1).toString() },
                tint = { Palette.of(it) },
                compact = true,
            )
            Text(
                text.difficulty(quest.difficulty),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.of(quest.difficulty),
                modifier = Modifier.padding(top = Space.xs),
            )

            FieldSection(text.fieldFear)
            SegmentedSelector(
                options = Fear.entries,
                selected = quest.fear,
                onSelect = { app.store.changeQuest(quest.id, FieldPatch().setEnum(QuestFields.FEAR, it)) },
                label = { text.fear(it) },
                tint = { Palette.of(it) },
                compact = true,
            )

            FieldSection(text.fieldUrgency)
            SegmentedSelector(
                options = Urgency.entries,
                selected = quest.urgency,
                onSelect = { app.store.changeQuest(quest.id, FieldPatch().setEnum(QuestFields.URGENCY, it)) },
                label = { text.urgency(it) },
                tint = { Palette.of(it) },
                compact = true,
            )
            Text(
                text.balanceNote,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                modifier = Modifier.padding(top = Space.xs),
            )

            FieldSection(text.fieldSkills)
            SkillChips(app, state, quest)

            FieldSection(text.fieldDueDate)
            DueDateEditor(app, quest)

            FieldSection(text.fieldEstimate)
            NumberField(
                value = quest.estimatedMinutes,
                onValueChange = {
                    app.store.changeQuest(quest.id, FieldPatch().setIntOrNull(QuestFields.ESTIMATED_MINUTES, it))
                },
                suffix = text.fieldMinutes,
                onFocusChange = { app.typing = it },
            )

            FieldSection(text.fieldRewardBonus)
            NumberField(
                value = quest.rewardBonus,
                onValueChange = { typed ->
                    // Zero and empty mean the same thing, so only one of them
                    // is ever written: an empty field is no bonus at all.
                    val bonus = typed?.takeIf { it > 0 }
                    if (bonus != quest.rewardBonus) {
                        app.store.changeQuest(quest.id, FieldPatch().setIntOrNull(QuestFields.REWARD_BONUS, bonus))
                    }
                },
                suffix = text.fieldPoints,
                step = 1,
                onFocusChange = { app.typing = it },
            )
            Text(
                text.fieldRewardBonusBody,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                modifier = Modifier.padding(top = Space.xs),
            )

            FieldSection(text.fieldRepetition)
            RepetitionField(
                value = quest.repetition,
                onValueChange = {
                    app.store.changeQuest(quest.id, FieldPatch().setRepetition(QuestFields.REPETITION, it))
                },
                onFocusChange = { app.typing = it },
            )

            FieldSection(text.fieldReminders)
            RemindersEditor(app, quest)

            Spacer(Modifier.height(Space.xl))
        }

        HLine()
        EditorActions(app, state, quest)
    }
}

/**
 * A field's name in the editor: small capitals, no rule. In a pane this
 * narrow a rule after every label would only repeat the gap between fields.
 */
@Composable
private fun FieldSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = theme.textFaint,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
    )
}

@Composable
private fun EditorHeader(app: AppState, state: QuestsState, quest: Quest) {
    val text = strings
    Row(
        Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        XpPill(quest.projectedXp())
        Pill(text.pointsAmount(quest.projectedPoints()), color = theme.accent, leading = Glyph.COIN)
        Spacer(Modifier.weight(1f))
        val parent = state.quest(quest.parentId)
        if (parent != null) {
            IconAction(Glyph.ARROW_UP, text.actionPromote, onClick = { app.store.promoteToTopLevel(quest.id) })
        }
        IconAction(Glyph.CROSS, text.actionClose, onClick = { app.selectedId = null })
    }
}

/**
 * Why this quest sits where it does in its list, and the override for it.
 *
 * The score is shown itemised because an order that cannot explain itself
 * gets hand-sorted instead (§3). Open quests only: priority orders the open
 * lists, and a finished quest has nowhere left to rise to. The pin row keeps
 * one height whether or not the quest is pinned, so pinning never nudges the
 * fields below it.
 */
@Composable
private fun PriorityStrip(app: AppState, quest: Quest) {
    val text = strings
    val breakdown = Priority.breakdown(quest, app.clock)

    Column(Modifier.fillMaxWidth().padding(top = Space.md)) {
        Text(
            text.priorityBreakdown(breakdown),
            // Tabular figures, so the line holds still as a rating changes.
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = theme.textFaint,
            // One line, always. The selectors that change the score sit below
            // this, so a line that wrapped as a rating changed would move the
            // control under the pointer. The longest score fits the pane at the
            // default text size; a larger one truncates the tail instead.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            when (val pin = quest.pin) {
                Pin.NONE -> {
                    Text(
                        text.priorityAutomatic,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textFaint,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(Glyph.ARROW_UP, text.actionPinToTop, size = 26.dp, onClick = {
                        app.store.pinToTop(quest.id)
                    })
                    IconAction(Glyph.ARROW_DOWN, text.actionPinToBottom, size = 26.dp, onClick = {
                        app.store.pinToBottom(quest.id)
                    })
                }

                Pin.TOP, Pin.BOTTOM -> {
                    QuestIcon(Glyph.PIN, size = 12.dp, tint = theme.textMuted)
                    Text(
                        if (pin == Pin.TOP) text.pinnedToTop else text.pinnedToBottom,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    QuestButton(text.actionClearPin, compact = true, onClick = { app.store.clearPin(quest.id) })
                }
            }
        }
    }
}

/**
 * Title and notes commit on focus loss, not on keystroke.
 *
 * The local copy is keyed on the quest id so switching selection reloads the
 * fields, while an edit in progress is not thrown away by an unrelated
 * recomposition.
 */
@Composable
private fun TitleAndNotes(app: AppState, quest: Quest) {
    val text = strings
    var title by remember(quest.id) { mutableStateOf(quest.title) }
    var notes by remember(quest.id) { mutableStateOf(quest.notes) }

    Spacer(Modifier.height(Space.md))
    LabelledField(text.fieldTitle) {
        QuestTextField(
            value = title,
            onValueChange = { title = it },
            onFocusChange = { focused ->
                app.typing = focused
                if (!focused && title.trim() != quest.title && title.isNotBlank()) {
                    app.store.changeQuest(quest.id, FieldPatch().set(QuestFields.TITLE, title.trim()))
                }
            },
            textStyle = MaterialTheme.typography.titleMedium,
        )
    }
    LabelledField(text.fieldNotes) {
        QuestTextField(
            value = notes,
            onValueChange = { notes = it },
            singleLine = false,
            minLines = 3,
            placeholder = text.fieldOptional,
            onFocusChange = { focused ->
                app.typing = focused
                if (!focused && notes != quest.notes) {
                    app.store.changeQuest(quest.id, FieldPatch().set(QuestFields.NOTES, notes))
                }
            },
        )
    }
}

@Composable
private fun DueDateEditor(app: AppState, quest: Quest) {
    val text = strings
    val today = app.clock.today()
    var calendarOpen by remember(quest.id) { mutableStateOf(false) }
    var month by remember(quest.id) { mutableStateOf(quest.dueDate ?: today) }

    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            QuestButton(
                text = quest.dueDate?.format(cl.nheox.quests.ui.components.LongDate) ?: text.fieldNone,
                glyph = Glyph.CALENDAR,
                compact = true,
                onClick = { calendarOpen = !calendarOpen },
            )
            if (quest.dueDate != null) {
                IconAction(Glyph.CROSS, text.rescheduleClear, size = 26.dp, onClick = {
                    app.store.reschedule(quest.id, null)
                })
            }
        }

        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            QuestButton(text.rescheduleToday, compact = true, onClick = {
                app.store.reschedule(quest.id, today, quest.dueTime)
            })
            QuestButton(text.rescheduleTomorrow, compact = true, onClick = {
                app.store.reschedule(quest.id, today.plusDays(1), quest.dueTime)
            })
            QuestButton(text.reschedulePlusWeek, compact = true, onClick = {
                app.store.reschedule(quest.id, today.plusWeeks(1), quest.dueTime)
            })
        }

        if (calendarOpen) {
            Spacer(Modifier.height(Space.sm))
            Box(
                Modifier
                    .background(theme.background, RoundedCornerShape(Radii.md))
                    .padding(Space.xxs),
            ) {
                MonthGrid(
                    month = month,
                    selected = quest.dueDate,
                    today = today,
                    onSelect = { date ->
                        app.store.reschedule(quest.id, date, quest.dueTime)
                        calendarOpen = false
                    },
                    onMonthChange = { month = it },
                )
            }
        }

        if (quest.dueDate != null) {
            Spacer(Modifier.height(Space.sm))
            FieldLabel(text.fieldDueTime)
            TimeField(
                value = quest.dueTime,
                onValueChange = { app.store.reschedule(quest.id, quest.dueDate, it) },
                onFocusChange = { app.typing = it },
            )
        }
    }
}

/**
 * Reminder offsets as presets rather than a free-form duration field.
 *
 * Nobody wants a reminder 37 minutes before. The presets also keep the `.ics`
 * `VALARM` triggers to values every calendar client handles identically.
 */
@Composable
private fun RemindersEditor(app: AppState, quest: Quest) {
    val text = strings
    val presets = listOf(0, 10, 30, 60, 24 * 60)

    Column(Modifier.fillMaxWidth()) {
        if (quest.reminders.isEmpty()) {
            Text(text.fieldNone, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                quest.reminders.forEachIndexed { index, reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Pill(
                            text = when (reminder) {
                                is Reminder.BeforeDue -> if (reminder.minutesBefore == 0) {
                                    text.fieldDueTime
                                } else {
                                    text.remindBefore(reminder.minutesBefore)
                                }

                                is Reminder.At -> "${reminder.date} ${reminder.time}"
                            },
                            color = theme.accent,
                            leading = Glyph.BELL,
                        )
                        IconAction(Glyph.CROSS, text.actionRemove, size = 22.dp, onClick = {
                            app.store.changeQuest(
                                quest.id,
                                FieldPatch().setReminders(
                                    QuestFields.REMINDERS,
                                    quest.reminders.filterIndexed { position, _ -> position != index },
                                ),
                            )
                        })
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            presets.forEach { minutes ->
                val already = quest.reminders.any { it is Reminder.BeforeDue && it.minutesBefore == minutes }
                QuestButton(
                    text = if (minutes == 0) text.fieldDueTime else Dates.formatMinutes(minutes),
                    compact = true,
                    enabled = !already && quest.dueDate != null,
                    onClick = {
                        app.store.changeQuest(
                            quest.id,
                            FieldPatch().setReminders(
                                QuestFields.REMINDERS,
                                quest.reminders + Reminder.BeforeDue(minutes),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SkillChips(app: AppState, state: QuestsState, quest: Quest) {
    val text = strings
    var adding by remember(quest.id) { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        if (state.skills.isEmpty()) {
            Text(text.skillsEmpty, style = MaterialTheme.typography.bodySmall, color = theme.textFaint)
        } else {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                state.skills.values.sortedBy { it.name.lowercase() }.forEach { skill ->
                    val linked = skill.id in quest.skillIds
                    val colour = Palette.forSkill(skill.id)
                    Box(
                        Modifier
                            .background(
                                if (linked) colour.copy(alpha = 0.22f) else theme.card,
                                RoundedCornerShape(Radii.sm),
                            )
                            .focusRing(RoundedCornerShape(Radii.sm))
                            .clickableRow {
                                val next = if (linked) quest.skillIds - skill.id else quest.skillIds + skill.id
                                app.store.setSkills(quest.id, next)
                            }
                            .padding(horizontal = Space.sm, vertical = Space.xs),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            QuestIcon(Glyph.SPARK, size = 11.dp, tint = if (linked) colour else theme.textFaint)
                            Text(
                                skill.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (linked) colour else theme.textMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = adding,
                    onValueChange = { adding = it },
                    placeholder = text.actionAdd + " " + text.fieldSkills.lowercase(),
                    onFocusChange = { app.typing = it },
                    onImeAction = {
                        if (adding.isNotBlank()) {
                            val id = app.store.findOrCreateSkill(adding)
                            app.store.setSkills(quest.id, quest.skillIds + id)
                            adding = ""
                        }
                    },
                )
            }
            IconAction(Glyph.PLUS, text.actionAdd, size = 26.dp, onClick = {
                if (adding.isNotBlank()) {
                    val id = app.store.findOrCreateSkill(adding)
                    app.store.setSkills(quest.id, quest.skillIds + id)
                    adding = ""
                }
            })
        }
    }
}

@Composable
private fun EditorActions(app: AppState, state: QuestsState, quest: Quest) {
    val text = strings
    Column(Modifier.fillMaxWidth().padding(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (quest.isOpen) {
                QuestButton(text.actionComplete, primary = true, glyph = Glyph.CHECK, onClick = {
                    app.store.completeQuest(quest.id)
                })
                QuestButton(text.actionFail, glyph = Glyph.CROSS, onClick = { app.store.failQuest(quest.id) })
            } else {
                QuestButton(text.actionReopen, primary = true, glyph = Glyph.UNDO, onClick = {
                    app.store.reopenQuest(quest.id)
                })
            }
        }
        Spacer(Modifier.height(Space.xs))
        // Wrapping, not a Row: three labelled buttons already fill the pane, and
        // a longer label — or a translation, should one arrive — would clip the
        // last button off the edge in a fixed row.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            QuestButton(text.actionAddSubQuest, compact = true, glyph = Glyph.TREE, onClick = {
                val id = app.store.createQuest(
                    QuestDraft(
                        title = "",
                        parentId = quest.id,
                        difficulty = quest.difficulty,
                        skillIds = quest.skillIds,
                    ),
                )
                app.selectedId = id
            })
            QuestButton(text.actionDuplicate, compact = true, glyph = Glyph.COPY, onClick = {
                app.store.duplicateQuest(quest.id)?.let { app.selectedId = it }
            })
            QuestButton(text.actionMarkWorked, compact = true, glyph = Glyph.CLOCK, onClick = {
                app.store.markWorked(quest.id)
            })
        }
        Spacer(Modifier.height(Space.xs))
        QuestButton(text.actionDelete, compact = true, danger = true, glyph = Glyph.TRASH, onClick = {
            app.overlay = cl.nheox.quests.ui.Overlay.ConfirmDelete(listOf(quest.id), quest.title)
        })

        val children = state.children(quest.id)
        if (children.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text.countSubQuests(children.size),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
        }
    }
}
