package cl.nheox.quests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.i18n.Strings
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.components.KeyCap
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.components.clickableRow
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.QuestIcon
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

public data class Command(
    val label: String,
    val glyph: Glyph,
    val keys: String? = null,
    val group: String = "",
    val run: () -> Unit,
)

/**
 * Every action in the app, in one list, reachable by typing part of its name.
 *
 * The palette is the answer to the discoverability problem that "keyboard
 * first" creates: a shortcut nobody can find is not a feature. It is built from
 * the same action list the keyboard uses, so the two cannot disagree.
 */
public fun commands(
    app: AppState,
    state: QuestsState,
    actions: AppActions,
    text: Strings,
): List<Command> {
    val selected = app.selectedId
    val targets = app.targets()
    val today = app.clock.today()
    val out = mutableListOf<Command>()

    QuestView.entries.forEach { view ->
        out += Command(text.view(view), Glyph.LIST, group = text.sectionQuests) { app.goToView(view) }
    }
    out += Command(text.navSkills, Glyph.SPARK, "Ctrl+8", text.sectionProgress) { app.go(Route.Skills) }
    out += Command(text.navRewards, Glyph.GIFT, group = text.sectionProgress) { app.go(Route.Rewards) }
    out += Command(text.navInventory, Glyph.BOX, group = text.sectionProgress) { app.go(Route.Inventory) }
    out += Command(text.navScratchpad, Glyph.LIST, group = text.sectionProgress) { app.go(Route.Scratchpad) }
    out += Command(text.navStats, Glyph.CHART, "Ctrl+9", text.sectionProgress) { app.go(Route.Insight) }
    out += Command(text.navSettings, Glyph.GEAR, "Ctrl+,", text.sectionApp) { app.go(Route.Settings) }
    out += Command(text.navHelp, Glyph.HELP, "?", text.sectionApp) { app.go(Route.Help) }

    out += Command(text.actionNewQuest, Glyph.PLUS, "Ctrl+N", text.sectionQuests) { app.openQuickAdd() }
    out += Command(text.actionSearch, Glyph.SEARCH, "Ctrl+F", text.sectionQuests) { app.openSearch() }
    out += Command(text.actionWhatNow, Glyph.TARGET, "A", text.sectionQuests) { app.overlay = Overlay.Suggester }
    out += Command(text.actionPickRandom, Glyph.SHUFFLE, "R", text.sectionQuests) {
        app.selectRandom(app.visibleQuests(state))
    }
    out += Command(text.actionUndo, Glyph.UNDO, "Ctrl+Z", text.sectionApp) { app.store.undo() }
    out += Command(text.actionReload, Glyph.REPEAT, "Ctrl+R", text.sectionApp) { app.store.reload() }
    out += Command(text.actionExportNow, Glyph.CALENDAR, "Ctrl+E", text.sectionApp) { actions.exportNow() }
    out += Command(text.actionOpenFolder, Glyph.FOLDER, group = text.sectionApp) { actions.openFolder() }
    out += Command(text.actionCompact, Glyph.BOX, group = text.sectionApp) {
        app.notify(actions.compact() ?: text.messageNothingToCompact)
    }

    if (selected != null) {
        out += Command(text.actionComplete, Glyph.CHECK, "Space", text.sectionQuests) {
            app.store.completeQuest(selected)
        }
        out += Command(text.actionFail, Glyph.CROSS, "X", text.sectionQuests) { app.store.failQuest(selected) }
        out += Command(text.actionReopen, Glyph.UNDO, "O", text.sectionQuests) { app.store.reopenQuest(selected) }
        out += Command(text.actionDuplicate, Glyph.COPY, "D", text.sectionQuests) {
            app.store.duplicateQuest(selected)?.let { app.selectedId = it }
        }
        out += Command(text.actionMarkWorked, Glyph.CLOCK, "G", text.sectionQuests) { app.store.markWorked(selected) }
        out += Command(text.actionPromote, Glyph.ARROW_UP, "P", text.sectionQuests) {
            app.store.promoteToTopLevel(selected)
        }
        out += Command(text.actionMoveToTop, Glyph.ARROW_UP, "[", text.sectionQuests) { app.store.moveToTop(selected) }
        out += Command(text.actionMoveToBottom, Glyph.ARROW_DOWN, "]", text.sectionQuests) {
            app.store.moveToBottom(selected)
        }
        out += Command(text.actionShareAsText, Glyph.COPY, "S", text.sectionQuests) {
            actions.copyQuestAsText(selected)
        }
    }

    if (targets.isNotEmpty()) {
        out += Command(text.rescheduleToday, Glyph.CALENDAR, "T", text.rescheduleTitle) {
            targets.forEach { app.store.reschedule(it, today) }
        }
        out += Command(text.rescheduleTomorrow, Glyph.CALENDAR, "Shift+T", text.rescheduleTitle) {
            targets.forEach { app.store.reschedule(it, today.plusDays(1)) }
        }
        out += Command(text.reschedulePlusWeek, Glyph.CALENDAR, "W", text.rescheduleTitle) {
            targets.forEach { app.store.reschedule(it, today.plusWeeks(1)) }
        }
        out += Command(text.reschedulePlusMonth, Glyph.CALENDAR, "Shift+W", text.rescheduleTitle) {
            targets.forEach { app.store.reschedule(it, today.plusMonths(1)) }
        }
        out += Command(text.rescheduleNextRepetition, Glyph.REPEAT, group = text.rescheduleTitle) {
            targets.forEach { app.store.advanceToNextRepetition(it) }
        }
        out += Command(text.rescheduleClear, Glyph.CROSS, "0", text.rescheduleTitle) {
            targets.forEach { app.store.reschedule(it, null) }
        }
        out += Command(text.actionDelete, Glyph.TRASH, "Del", text.sectionQuests) {
            app.overlay = Overlay.ConfirmDelete(targets, state.quests[targets.first()]?.title.orEmpty())
        }
    }

    return out
}

/**
 * Subsequence matching, not substring: "nqs" finds "New quest".
 *
 * Deliberately simple and deliberately *stable* — the score only ranks by where
 * the match starts and how tight it is, so the list does not reorder
 * surprisingly as a fourth character is typed.
 */
internal fun fuzzyScore(candidate: String, query: String): Int? {
    if (query.isEmpty()) return 0
    val haystack = candidate.lowercase()
    val needle = query.lowercase()
    if (haystack.startsWith(needle)) return 1000
    if (haystack.contains(needle)) return 500 - haystack.indexOf(needle)

    var index = 0
    var score = 0
    var lastHit = -1
    for (character in needle) {
        val hit = haystack.indexOf(character, index)
        if (hit < 0) return null
        score += if (lastHit >= 0 && hit == lastHit + 1) 3 else 1
        lastHit = hit
        index = hit + 1
    }
    return score
}

@Composable
public fun CommandPaletteOverlay(
    app: AppState,
    state: QuestsState,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val text = strings
    var query by remember { mutableStateOf("") }
    var cursor by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val all = remember(state, app.selectedId, app.marked, app.route) { commands(app, state, actions, text) }
    val matches = remember(all, query) {
        all.mapNotNull { command ->
            fuzzyScore(command.label, query)?.let { command to it }
        }.sortedByDescending { it.second }.map { it.first }
    }

    LaunchedEffect(matches.size) {
        cursor = cursor.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(cursor) {
        if (matches.isNotEmpty()) listState.animateScrollToItem(cursor.coerceIn(0, matches.lastIndex))
    }
    LaunchedEffect(Unit) {
        app.typing = true
        focus.requestFocus()
    }

    fun run(command: Command) {
        app.overlay = null
        app.typing = false
        command.run()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.72f))
            .clickableRow {
                app.overlay = null
                app.typing = false
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 96.dp)
                .width(560.dp)
                .background(theme.surface, RoundedCornerShape(Radii.lg))
                .padding(Space.sm)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (matches.isNotEmpty()) cursor = (cursor + 1).coerceAtMost(matches.lastIndex)
                            true
                        }

                        Key.DirectionUp -> {
                            cursor = (cursor - 1).coerceAtLeast(0)
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            matches.getOrNull(cursor)?.let { run(it) }
                            true
                        }

                        else -> false
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                QuestIcon(Glyph.COMMAND, size = 15.dp, tint = theme.accent)
                Box(Modifier.weight(1f)) {
                    QuestTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            cursor = 0
                        },
                        placeholder = text.messageCommandPlaceholder,
                        onFocusChange = { app.typing = true },
                        modifier = Modifier.focusRequester(focus),
                    )
                }
            }

            Spacer(Modifier.padding(Space.xxs))

            if (matches.isEmpty()) {
                Text(
                    text.messageNoResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textFaint,
                    modifier = Modifier.padding(Space.md),
                )
                return@Column
            }

            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 380.dp)) {
                items(matches) { command ->
                    val index = matches.indexOf(command)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == cursor) theme.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(Radii.sm),
                            )
                            .clickableRow { run(command) }
                            .padding(horizontal = Space.sm, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        QuestIcon(command.glyph, size = 13.dp, tint = theme.textMuted)
                        Text(
                            command.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = theme.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (command.group.isNotEmpty()) {
                            Text(
                                command.group,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textFaint,
                            )
                        }
                        command.keys?.let { KeyCap(it) }
                    }
                }
            }
        }
    }
}
