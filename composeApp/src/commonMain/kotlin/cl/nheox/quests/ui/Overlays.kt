package cl.nheox.quests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.SuggestContext
import cl.nheox.quests.domain.Suggester
import cl.nheox.quests.domain.Suggestion
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.components.DifficultyBadge
import cl.nheox.quests.ui.components.EnergyBar
import cl.nheox.quests.ui.components.FearBadge
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.MonthGrid
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestButton
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

/** The scrim every overlay sits on. Clicking it dismisses. */
@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.72f))
            .clickableRow(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun Panel(
    width: androidx.compose.ui.unit.Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .width(width)
            .background(theme.surface, RoundedCornerShape(Radii.lg))
            .padding(Space.lg),
        content = content,
    )
}

/**
 * "What now?" — three candidates, each with the reason it was chosen.
 *
 * The reasons are not decoration. A suggester that cannot say *why* gets
 * second-guessed and then ignored; one that says "your energy is high enough
 * for the scary one" is making an argument the user can agree or disagree with.
 */
@Composable
public fun SuggesterOverlay(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val energy = app.energy(state)
    var nonce by remember { mutableStateOf(0) }

    val suggestions = remember(state, energy, app.suggesterMinutes, nonce) {
        Suggester.suggest(
            quests = state.quests.values,
            context = SuggestContext(
                energy = energy,
                minutesAvailable = app.suggesterMinutes,
                today = app.clock.today(),
                skills = state.skills,
            ),
        )
    }

    Scrim(onDismiss = { app.overlay = null }) {
        Panel(width = 520.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                QuestIcon(Glyph.TARGET, size = 18.dp, tint = theme.accent)
                Text(text.suggesterTitle, style = MaterialTheme.typography.headlineSmall, color = theme.text)
                Spacer(Modifier.weight(1f))
                IconAction(Glyph.CROSS, text.actionClose, onClick = { app.overlay = null })
            }

            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(text.energy, style = MaterialTheme.typography.labelMedium, color = theme.textFaint)
                Box(Modifier.width(96.dp)) { EnergyBar(energy) }
                Text(
                    text.energyBand(cl.nheox.quests.domain.Energy.band(energy)),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                )
            }

            Spacer(Modifier.height(Space.sm))
            Text(text.suggesterMinutes, style = MaterialTheme.typography.labelMedium, color = theme.textFaint)
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                listOf(10, 25, 45, 90, 180).forEach { minutes ->
                    QuestButton(
                        text = Dates.formatMinutes(minutes),
                        compact = true,
                        primary = app.suggesterMinutes == minutes,
                        onClick = { app.suggesterMinutes = minutes },
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
            HLine()
            Spacer(Modifier.height(Space.md))

            if (suggestions.isEmpty()) {
                Text(text.suggesterNoCandidates, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            } else {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionCard(app, suggestion)
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            QuestButton(text.suggesterRefresh, compact = true, glyph = Glyph.SHUFFLE, onClick = { nonce++ })
        }
    }
}

@Composable
private fun SuggestionCard(app: AppState, suggestion: Suggestion) {
    val text = strings
    val quest = suggestion.quest

    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.card, RoundedCornerShape(Radii.md))
            .focusRing()
            .clickableRow {
                app.selectedId = quest.id
                app.overlay = null
            }
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                quest.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            XpPill(quest.projectedXp())
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            DifficultyBadge(quest.difficulty)
            FearBadge(quest.fear)
            quest.estimatedMinutes?.let {
                Pill(Dates.formatMinutes(it), leading = Glyph.CLOCK)
            }
        }
        if (suggestion.reasons.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                suggestion.reasons.joinToString(" · ") { text.suggestionReason(it) },
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent,
            )
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            QuestButton(text.actionComplete, compact = true, primary = true, glyph = Glyph.CHECK, onClick = {
                app.store.completeQuest(quest.id)
            })
            QuestButton(text.actionMarkWorked, compact = true, glyph = Glyph.CLOCK, onClick = {
                app.store.markWorked(quest.id)
                app.overlay = null
            })
        }
    }
}

/**
 * Bulk reschedule with a review step (§7.4).
 *
 * The list of what is about to move is shown, because "reschedule 23 overdue
 * quests to tomorrow" is a destructive-feeling action and a silent one would be
 * frightening to use.
 */
@Composable
public fun RescheduleOverlay(
    app: AppState,
    state: QuestsState,
    questIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val today = app.clock.today()
    var month by remember { mutableStateOf(today) }
    val quests = questIds.mapNotNull { state.quests[it] }

    fun apply(date: java.time.LocalDate?) {
        app.store.rescheduleAll(questIds, date)
        app.overlay = null
        app.clearMarks()
    }

    Scrim(onDismiss = { app.overlay = null }) {
        Panel(width = 480.dp) {
            Text(text.rescheduleOverdueTitle, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Text(
                text.rescheduleOverdueBody,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
                modifier = Modifier.padding(top = Space.xxs),
            )

            Spacer(Modifier.height(Space.md))
            Column(
                Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
            ) {
                quests.forEach { quest ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Text(
                            quest.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        quest.dueDate?.let {
                            Pill(it.toString(), color = theme.danger, leading = Glyph.CALENDAR)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestButton(text.rescheduleToday, compact = true, onClick = { apply(today) })
                QuestButton(text.rescheduleTomorrow, compact = true, onClick = { apply(today.plusDays(1)) })
                QuestButton(text.reschedulePlusWeek, compact = true, onClick = { apply(today.plusWeeks(1)) })
                QuestButton(text.reschedulePlusMonth, compact = true, onClick = { apply(today.plusMonths(1)) })
            }
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestButton(text.rescheduleNextRepetition, compact = true, glyph = Glyph.REPEAT, onClick = {
                    questIds.forEach { app.store.advanceToNextRepetition(it) }
                    app.overlay = null
                    app.clearMarks()
                })
                QuestButton(text.rescheduleClear, compact = true, danger = true, onClick = { apply(null) })
            }

            Spacer(Modifier.height(Space.md))
            Box(Modifier.background(theme.background, RoundedCornerShape(Radii.md))) {
                MonthGrid(
                    month = month,
                    selected = null,
                    today = today,
                    onSelect = { apply(it) },
                    onMonthChange = { month = it },
                )
            }
        }
    }
}

/** Batch skill assignment across a selection (§7.1). */
@Composable
public fun SkillPickerOverlay(
    app: AppState,
    state: QuestsState,
    questIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var chosen by remember { mutableStateOf(emptySet<String>()) }

    Scrim(onDismiss = { app.overlay = null }) {
        Panel {
            Text(text.fieldSkills, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Text(
                text.countSelected(questIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textFaint,
            )
            Spacer(Modifier.height(Space.md))

            if (state.skills.isEmpty()) {
                Text(text.skillsEmpty, style = MaterialTheme.typography.bodyMedium, color = theme.textMuted)
            } else {
                Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                ) {
                    state.skills.values.sortedBy { it.name.lowercase() }.forEach { skill ->
                        val picked = skill.id in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (picked) theme.accent.copy(alpha = 0.16f) else Color.Transparent,
                                    RoundedCornerShape(Radii.sm),
                                )
                                .clickableRow {
                                    chosen = if (picked) chosen - skill.id else chosen + skill.id
                                }
                                .padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            QuestIcon(
                                if (picked) Glyph.CHECK else Glyph.SPARK,
                                size = 13.dp,
                                tint = if (picked) theme.accent else Palette.forSkill(skill.id),
                            )
                            Text(skill.name, style = MaterialTheme.typography.bodyLarge, color = theme.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestButton(text.actionAdd, primary = true, enabled = chosen.isNotEmpty(), onClick = {
                    app.store.assignSkillsToAll(questIds, chosen, replace = false)
                    app.overlay = null
                    app.clearMarks()
                })
                QuestButton(text.actionSave, enabled = chosen.isNotEmpty(), onClick = {
                    app.store.assignSkillsToAll(questIds, chosen, replace = true)
                    app.overlay = null
                    app.clearMarks()
                })
                Spacer(Modifier.weight(1f))
                QuestButton(text.actionCancel, compact = true, onClick = { app.overlay = null })
            }
        }
    }
}

/**
 * Delete confirmation.
 *
 * Kept even though delete is undoable, because deleting a parent takes its
 * whole branch with it and the branch size is the thing worth knowing before
 * rather than after.
 */
@Composable
public fun ConfirmDeleteOverlay(
    app: AppState,
    state: QuestsState,
    questIds: List<String>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val branchSize = questIds.sumOf { state.branch(it).size }

    Scrim(onDismiss = { app.overlay = null }) {
        Panel(width = 400.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                QuestIcon(Glyph.TRASH, size = 18.dp, tint = theme.danger)
                Text(text.actionDelete, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            }
            Spacer(Modifier.height(Space.md))
            Text(
                if (questIds.size == 1) title else text.countSelected(questIds.size),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textMuted,
            )
            if (branchSize > questIds.size) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text.countSubQuests(branchSize - questIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.warning,
                )
            }
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                QuestButton(text.actionDelete, danger = true, glyph = Glyph.TRASH, onClick = {
                    if (questIds.size == 1) {
                        app.store.deleteQuest(questIds.first())
                    } else {
                        app.store.deleteAll(questIds)
                    }
                    if (app.selectedId in questIds) app.selectedId = null
                    app.clearMarks()
                    app.overlay = null
                })
                Spacer(Modifier.weight(1f))
                QuestButton(text.actionCancel, onClick = { app.overlay = null })
            }
        }
    }
}
