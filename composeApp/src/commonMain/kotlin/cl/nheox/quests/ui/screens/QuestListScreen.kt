package cl.nheox.quests.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.DueBucket
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.Reminders
import cl.nheox.quests.domain.SortDirection
import cl.nheox.quests.domain.SortKey
import cl.nheox.quests.domain.Sorting
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.FloatingButtonClearance
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.KeepInView
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestButton
import cl.nheox.quests.ui.components.QuestRow
import cl.nheox.quests.ui.components.QuestTextField
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.LocalDate

/**
 * The list. The screen the app is for.
 *
 * Today and Overdue are grouped into due buckets; the rest are flat, because
 * grouping a list that is already filtered to one day only adds a header. The
 * Plan view is a tree and lives in its own file.
 */
@Composable
public fun QuestListScreen(
    app: AppState,
    state: QuestsState,
    quests: List<Quest>,
    modifier: Modifier = Modifier,
) {
    val view = app.currentView ?: QuestView.ALL
    val text = strings
    val today = app.clock.today()
    // One per view, so each list keeps its own scroll position.
    val listState = remember(view) { LazyListState() }

    Column(modifier.fillMaxSize()) {
        ListToolbar(app, state, quests.size)
        HLine()

        if (app.marked.isNotEmpty()) {
            BulkBar(app, state)
            HLine()
        }

        if (quests.isEmpty()) {
            EmptyState(
                message = when {
                    app.searchOpen && app.searchQuery.isNotBlank() -> text.messageNoResults
                    view == QuestView.TODAY -> text.messageNothingDueToday
                    view == QuestView.OVERDUE -> text.messageNoOverdue
                    view == QuestView.REMINDERS -> text.messageNoReminders
                    state.quests.isEmpty() -> text.questsEmpty
                    else -> text.messageNoQuests
                },
                glyph = when (view) {
                    QuestView.OVERDUE -> Glyph.CHECK
                    QuestView.REMINDERS -> Glyph.BELL
                    else -> Glyph.INBOX
                },
            )
            return@Column
        }

        // [quests] is already in the order shown, buckets included (see
        // AppState.visibleQuests); grouping it again only finds the headings.
        val buckets = if (app.grouped(view)) Sorting.groupByDue(quests, app.clock) else null
        // Each bucket adds its heading as an item before its rows.
        val selectedIndex = quests.indexOfFirst { it.id == app.selectedId }.let { index ->
            if (index < 0 || buckets == null) {
                index
            } else {
                val bucket = Sorting.bucketOf(quests[index], app.clock.today())
                index + buckets.keys.count { it <= bucket }
            }
        }
        KeepInView(listState, selectedIndex)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = Space.md,
                bottom = FloatingButtonClearance,
            ),
            // Cards, not lines: a gap between rows instead of a rule.
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (buckets != null) {
                buckets.forEach { (bucket, bucketQuests) ->
                    item(key = "header-" + bucket.name) {
                        BucketHeader(bucket, bucketQuests.size)
                    }
                    items(bucketQuests, key = { it.id }) { quest ->
                        ListRow(app, state, quest, today)
                    }
                }
            } else {
                items(quests, key = { it.id }) { quest ->
                    ListRow(app, state, quest, today, subtitle = if (view == QuestView.REMINDERS) nextReminder(app, quest) else null)
                }
            }
        }
    }
}

/** "Reminder today 14:30": the line the Reminders list shows under each quest. */
@Composable
private fun nextReminder(app: AppState, quest: Quest): String? {
    val zone = app.clock.zone()
    val next = Reminders.next(quest, app.clock.now(), zone) ?: return null
    return strings.reminderAt(next.atZone(zone).toLocalDateTime(), app.clock.today())
}

@Composable
private fun ListRow(app: AppState, state: QuestsState, quest: Quest, today: LocalDate, subtitle: String? = null) {
    QuestRow(
        subtitle = subtitle,
        quest = quest,
        today = today,
        selected = app.selectedId == quest.id,
        marked = quest.id in app.marked,
        childCount = state.children(quest.id).size,
        onClick = { app.selectedId = quest.id },
        onToggleMark = { app.toggleMark(quest.id) },
        onComplete = {
            app.store.completeQuest(quest.id)
        },
        onFail = { app.store.failQuest(quest.id) },
    )
}

@Composable
private fun BucketHeader(bucket: DueBucket, count: Int) {
    val text = strings
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.xs, start = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text = text.bucket(bucket).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (bucket == DueBucket.OVERDUE) theme.danger else theme.textFaint,
            fontWeight = FontWeight.SemiBold,
        )
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = theme.textFaint)
        Box(Modifier.weight(1f).height(1.dp).background(theme.border))
    }
}

@Composable
private fun ListToolbar(app: AppState, state: QuestsState, count: Int) {
    val text = strings
    val view = app.currentView ?: QuestView.ALL

    Row(
        modifier = Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (app.searchOpen) {
            val focus = remember { FocusRequester() }
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = app.searchQuery,
                    onValueChange = { app.searchQuery = it },
                    placeholder = text.messageSearchPlaceholder,
                    onFocusChange = { app.typing = it },
                    modifier = Modifier.focusRequester(focus),
                )
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            IconAction(Glyph.CROSS, text.actionClose, onClick = { app.closeSearch() })
        } else {
            Text(
                text = text.view(view),
                style = MaterialTheme.typography.headlineSmall,
                color = theme.text,
            )
            Pill(count.toString(), color = theme.textMuted)
            Spacer(Modifier.weight(1f))
            IconAction(Glyph.SEARCH, text.actionSearch, onClick = { app.openSearch() })
            IconAction(Glyph.SHUFFLE, text.actionPickRandom, onClick = {
                app.selectRandom(app.visibleQuests(state))
            })
            SortControl(app)
        }
    }
}

@Composable
private fun SortControl(app: AppState) {
    var open by remember { mutableStateOf(false) }
    val text = strings
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.LIST, text.sortBy, onClick = { open = true })
            IconAction(
                glyph = if (app.sort.direction == SortDirection.ASCENDING) Glyph.ARROW_UP else Glyph.ARROW_DOWN,
                description = if (app.sort.direction == SortDirection.ASCENDING) {
                    text.sortAscending
                } else {
                    text.sortDescending
                },
                size = 26.dp,
                onClick = { app.sort = app.sort.copy(direction = app.sort.direction.flipped()) },
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = theme.card,
        ) {
            SortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text.sortKey(key),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (app.sort.key == key) theme.accent else theme.text,
                        )
                    },
                    onClick = {
                        app.sort = app.sort.copy(key = key)
                        if (key == SortKey.RANDOM) app.randomSeed = app.clock.nowMillis()
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * The quick-add line.
 *
 * One text field that parses difficulty, fear, urgency, duration, due date,
 * repetition and skills out of what was typed (§7.4). The live preview below is
 * the part that makes the syntax learnable: nobody reads a syntax reference,
 * but everybody notices a chip appearing as they type. Shared with the
 * Standing screen, so `N` adds a quest the same way wherever it is pressed.
 */
@Composable
internal fun QuickAddBar(app: AppState, onSubmit: (String) -> Unit) {
    val text = strings
    val focus = remember { FocusRequester() }

    Column(Modifier.fillMaxWidth().background(theme.surface).padding(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.weight(1f)) {
                QuestTextField(
                    value = app.quickAddText,
                    onValueChange = { app.quickAddText = it },
                    placeholder = text.messageQuickAddPlaceholder,
                    onFocusChange = { app.typing = it },
                    onImeAction = {
                        if (app.quickAddText.isNotBlank()) onSubmit(app.quickAddText)
                    },
                    modifier = Modifier.focusRequester(focus),
                )
            }
            QuestButton(
                text = text.actionAdd,
                primary = true,
                enabled = app.quickAddText.isNotBlank(),
                onClick = { if (app.quickAddText.isNotBlank()) onSubmit(app.quickAddText) },
            )
            IconAction(Glyph.CROSS, text.actionClose, onClick = { app.closeQuickAdd() })
        }

        if (app.quickAddText.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            QuickAddPreview(app.quickAddText, app.clock.today())
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * What a quick-add line will become, read back as it is typed: the title it
 * leaves, then a pill for each field the line set. Seeing "#thesis" turn into
 * a skill before pressing Enter is how the syntax gets learnt.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddPreview(line: String, today: LocalDate) {
    val text = strings
    val parsed = remember(line, today) { cl.nheox.quests.domain.QuickAdd.parse(line, today) }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = parsed.title.ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMuted,
        )
        parsed.difficulty?.let { Pill(text.difficulty(it), color = cl.nheox.quests.ui.theme.Palette.of(it)) }
        parsed.fear?.let { Pill(text.fear(it), color = cl.nheox.quests.ui.theme.Palette.of(it)) }
        parsed.urgency?.let { Pill(text.urgency(it), color = cl.nheox.quests.ui.theme.Palette.of(it)) }
        parsed.estimatedMinutes?.let { Pill(text.durationShort(it), leading = Glyph.CLOCK) }
        parsed.dueDate?.let { date ->
            Pill(text.dueMeta(date, parsed.dueTime, today), leading = Glyph.CALENDAR, color = theme.accent)
        }
        parsed.repetition?.let { Pill(text.repeatsEvery(it.every, text.repeatUnit(it.unit)), leading = Glyph.REPEAT) }
        parsed.skillNames.forEach { Pill("#$it", color = theme.progression, leading = Glyph.SPARK) }
    }
}

/** The bar that appears over a multi-selection. */
@Composable
private fun BulkBar(app: AppState, state: QuestsState) {
    val text = strings
    val ids = app.marked.toList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.progression.copy(alpha = 0.10f))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text.countSelected(ids.size),
            style = MaterialTheme.typography.labelLarge,
            color = theme.progression,
            modifier = Modifier.padding(end = Space.xs),
        )
        QuestButton(text.actionComplete, compact = true, glyph = Glyph.CHECK, onClick = {
            app.store.completeAll(ids)
            app.clearMarks()
        })
        QuestButton(text.actionFail, compact = true, glyph = Glyph.CROSS, onClick = {
            app.store.failAll(ids)
            app.clearMarks()
        })
        QuestButton(text.rescheduleTitle, compact = true, glyph = Glyph.CALENDAR, onClick = {
            app.overlay = cl.nheox.quests.ui.Overlay.Reschedule(ids)
        })
        QuestButton(text.fieldSkills, compact = true, glyph = Glyph.SPARK, onClick = {
            app.overlay = cl.nheox.quests.ui.Overlay.SkillPicker(ids)
        })
        QuestButton(text.actionDelete, compact = true, danger = true, glyph = Glyph.TRASH, onClick = {
            app.overlay = cl.nheox.quests.ui.Overlay.ConfirmDelete(
                ids,
                state.quests[ids.firstOrNull()]?.title.orEmpty(),
            )
        })
        Spacer(Modifier.weight(1f))
        QuestButton(text.actionClearSelection, compact = true, onClick = { app.clearMarks() })
    }
}
