package cl.nheox.quests.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Priority
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.components.FloatingButtonClearance
import cl.nheox.quests.ui.components.EmptyState
import cl.nheox.quests.ui.components.HLine
import cl.nheox.quests.ui.components.IconAction
import cl.nheox.quests.ui.components.KeepInView
import cl.nheox.quests.ui.components.Pill
import cl.nheox.quests.ui.components.QuestRow
import cl.nheox.quests.ui.theme.Glyph
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme

/**
 * The Plan view: the whole quest hierarchy as a tree.
 *
 * Flattened into a single list with an indent level rather than built from
 * nested composables, so it can still go through `LazyColumn` and stay fast
 * with a deep tree — and so keyboard navigation moves through it as one
 * sequence, which is what the arrow keys mean to a user looking at a tree.
 */
@Composable
public fun PlanScreen(
    app: AppState,
    state: QuestsState,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val today = app.clock.today()
    val listState = rememberLazyListState()

    // Keyed on the day as well, because the order depends on it: at midnight
    // tomorrow's quests become today's and should rise without waiting for an
    // unrelated edit to trigger a re-sort.
    val rows = remember(state.quests, app.planCollapsed, today) {
        flatten(state, app.planCollapsed, app.clock)
    }
    KeepInView(listState, rows.indexOfFirst { it.quest.id == app.selectedId })

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(Space.toolbarHeight).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(text.navPlan, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Pill(rows.size.toString(), color = theme.textMuted)
            Spacer(Modifier.weight(1f))
            IconAction(Glyph.CHEVRON_RIGHT, text.actionCollapseAll, onClick = { app.collapseAll(state) })
            IconAction(Glyph.CHEVRON_DOWN, text.actionExpandAll, onClick = { app.expandAll() })
        }
        HLine()

        if (rows.isEmpty()) {
            EmptyState(text.questsEmpty, glyph = Glyph.TREE)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = Space.md,
                bottom = FloatingButtonClearance,
            ),
            // The same cards as every other list, with the same gap.
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
        ) {
            items(rows, key = { it.quest.id }) { row ->
                val folded = row.quest.id in app.planCollapsed
                QuestRow(
                    quest = row.quest,
                    today = today,
                    selected = app.selectedId == row.quest.id,
                    marked = row.quest.id in app.marked,
                    indent = row.depth,
                    childCount = row.childCount,
                    expanded = row.childCount > 0 && !folded,
                    onToggleExpand = if (row.childCount > 0) {
                        { app.setCollapsed(row.quest.id, !folded) }
                    } else {
                        null
                    },
                    onClick = { app.selectedId = row.quest.id },
                    onToggleMark = { app.toggleMark(row.quest.id) },
                    onComplete = { app.store.completeQuest(row.quest.id) },
                    onFail = { app.store.failQuest(row.quest.id) },
                )
            }
        }
    }
}

internal data class PlanRow(val quest: Quest, val depth: Int, val childCount: Int)

/**
 * Depth-first flatten of the hierarchy.
 *
 * Each sibling group is in the automatic order, pins included, exactly as a
 * flat list would be (§5.6): pins are per sibling group, which is what makes
 * them mean something in a tree.
 *
 * An open quest under a finished one is a root of its own: the tree shows
 * open quests only, and one whose parent is done would otherwise vanish from
 * it while still being there to do.
 *
 * Carries a visited set because the tree comes from a file the user is invited
 * to edit by hand: a parent cycle introduced in an editor must produce a
 * truncated tree, not an infinite loop that hangs the window.
 */
internal fun flatten(
    state: QuestsState,
    collapsed: Set<String>,
    clock: Clock,
): List<PlanRow> {
    val out = mutableListOf<PlanRow>()
    val visited = HashSet<String>()

    fun walk(quest: Quest, depth: Int) {
        if (!visited.add(quest.id)) return
        val children = Priority.order(state.children(quest.id).filter { it.isOpen }, clock)
        out += PlanRow(quest, depth, children.size)
        if (quest.id in collapsed) return
        children.forEach { walk(it, depth + 1) }
    }

    val roots = state.openQuests.filter { quest -> quest.parentId?.let { state.quests[it] }?.isOpen != true }
    Priority.order(roots, clock).forEach { walk(it, 0) }
    return out
}
