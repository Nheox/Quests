package cl.nheox.quests.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Energy
import cl.nheox.quests.domain.LaunchView
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.SortKey
import cl.nheox.quests.domain.SortOrder
import cl.nheox.quests.domain.Sorting
import cl.nheox.quests.domain.Views
import cl.nheox.quests.projection.QuestStore
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.screens.flatten

/** Which top-level screen is showing. */
public sealed interface Route {
    /** The launch view (§7.5): where things stand, at a glance. */
    public data object Standing : Route

    public data class Quests(val view: QuestView) : Route

    public data object Skills : Route

    public data object Rewards : Route

    public data object Inventory : Route

    public data object Scratchpad : Route

    public data object Insight : Route

    public data object Settings : Route

    public data object Help : Route
}

private val GroupedViews = setOf(QuestView.TODAY, QuestView.OVERDUE, QuestView.ALL)

/** The screen a [LaunchView] setting opens the app on. */
public fun launchRoute(view: LaunchView): Route = when (view) {
    LaunchView.STANDING -> Route.Standing
    LaunchView.TODAY -> Route.Quests(QuestView.TODAY)
    LaunchView.ALL -> Route.Quests(QuestView.ALL)
    LaunchView.PLAN -> Route.Quests(QuestView.PLAN)
}

/** A modal layer over whatever is showing. */
public sealed interface Overlay {
    public data object CommandPalette : Overlay

    public data object Suggester : Overlay

    public data class Reschedule(val questIds: List<String>) : Overlay

    public data class SkillPicker(val questIds: List<String>) : Overlay

    /** "Move under…": a new parent for these quests, chosen in the picker. */
    public data class MoveUnder(val questIds: List<String>) : Overlay

    public data class ConfirmDelete(val questIds: List<String>, val title: String) : Overlay
}

/**
 * All of the UI's own state — what is selected, what is being searched, which
 * overlay is up.
 *
 * Kept entirely separate from [QuestsState], which is derived from the log. The
 * split is worth having: nothing here is ever written to disk, so a stray UI
 * flag can never corrupt the user's history, and the projection can be rebuilt
 * from scratch at any moment without disturbing where the cursor is.
 */
public class AppState(
    public val store: QuestStore,
    public val clock: Clock,
) {
    /** Starts where Settings says to: the store has been loaded by the time this exists. */
    public var route: Route by mutableStateOf(launchRoute(store.state.value.settings.launchView))
        private set

    public var sort: SortOrder by mutableStateOf(Views.defaultSort((route as? Route.Quests)?.view ?: QuestView.TODAY))

    public var selectedId: String? by mutableStateOf(null)

    /** The multi-selection for bulk operations. Empty means "just the cursor". */
    public var marked: Set<String> by mutableStateOf(emptySet())
        private set

    public var searchOpen: Boolean by mutableStateOf(false)
        private set

    public var searchQuery: String by mutableStateOf("")

    public var overlay: Overlay? by mutableStateOf(null)

    public var quickAddOpen: Boolean by mutableStateOf(false)
        private set

    public var quickAddText: String by mutableStateOf("")

    /**
     * True while a text field has focus.
     *
     * Single-letter shortcuts are suspended while it is set, because otherwise
     * typing "d" into a quest title would duplicate the quest behind the field.
     */
    public var typing: Boolean by mutableStateOf(false)

    public var message: String? by mutableStateOf(null)

    /** Re-seeded by hand so a random sort holds still while it is being read. */
    public var randomSeed: Long by mutableStateOf(1L)

    public var suggesterMinutes: Int by mutableStateOf(30)

    /**
     * The Plan branches folded away. Here rather than in the Plan screen so the
     * arrow keys walk the same rows the tree shows, and so a branch stays
     * folded while the user is off in another view.
     */
    public var planCollapsed: Set<String> by mutableStateOf(emptySet())
        private set

    /** The skills the Balance radar shows; null means the strongest few. Kept for the session. */
    public var radarSkills: Set<String>? by mutableStateOf(null)

    /**
     * The data folder chosen in Settings, as shown to the user, until the next
     * launch switches to it. Null when no switch is waiting.
     */
    public var pendingFolder: String? by mutableStateOf(null)

    public val currentView: QuestView?
        get() = (route as? Route.Quests)?.view

    public fun go(route: Route) {
        this.route = route
        (route as? Route.Quests)?.let { sort = Views.defaultSort(it.view) }
        // Standing has no cursor. A selection carried in from a list would stay
        // live but out of sight there, and Space would complete it unseen.
        if (route == Route.Standing) selectedId = null
        clearMarks()
    }

    public fun goToView(view: QuestView): Unit = go(Route.Quests(view))

    public fun openSearch() {
        searchOpen = true
    }

    public fun closeSearch() {
        searchOpen = false
        searchQuery = ""
    }

    /**
     * Starts a new quest. In a list it opens in the detail column; on
     * Standing, as a bar under the title. Anywhere else there is nowhere to
     * show it, so it opens over Today, where a new quest most often belongs.
     */
    public fun openQuickAdd() {
        if (route !is Route.Quests && route != Route.Standing) go(Route.Quests(QuestView.TODAY))
        quickAddOpen = true
    }

    /**
     * The due date a quest added here takes when its title gives none: the
     * day a dated list is about, so the new quest shows up in the list it was
     * added from. Other lists add undated quests.
     */
    public fun defaultDueDate(): java.time.LocalDate? = when (currentView) {
        QuestView.TODAY -> clock.today()
        QuestView.TOMORROW -> clock.today().plusDays(1)
        else -> null
    }

    public fun closeQuickAdd() {
        quickAddOpen = false
        quickAddText = ""
        typing = false
    }

    public fun toggleMark(id: String) {
        marked = if (id in marked) marked - id else marked + id
    }

    public fun markAll(ids: Collection<String>) {
        marked = ids.toSet()
    }

    public fun clearMarks() {
        marked = emptySet()
    }

    /** The ids a command should act on: the marked set, or else the cursor. */
    public fun targets(): List<String> = marked.ifEmpty { listOfNotNull(selectedId) }.toList()

    public fun notify(text: String) {
        message = text
    }

    public fun dismissMessage() {
        message = null
    }

    /**
     * The visible, filtered, sorted list for the current view.
     *
     * Search deliberately escapes the current view: looking for something you
     * half-remember is not a question about today, and having to guess which
     * list it is in before searching would make the search useless.
     */
    /**
     * The quests the current view shows, in the order it shows them — which
     * is the order the arrow keys walk, so the cursor never jumps somewhere
     * the eye did not expect. Plan is its tree, a grouped list its groups.
     */
    public fun visibleQuests(state: QuestsState): List<Quest> {
        val view = currentView ?: return emptyList()
        val query = searchQuery.trim()
        val searching = searchOpen && query.isNotEmpty()
        if (view == QuestView.PLAN && !searching) {
            return flatten(state, planCollapsed, clock).map { it.quest }
        }
        val pool = if (searching) search(state, query) else Views.filter(view, state.quests.values, clock)
        val sorted = Sorting.sort(pool, sort, state.skills, randomSeed, clock)
        return if (grouped(view)) Sorting.groupByDue(sorted, clock).values.flatten() else sorted
    }

    /**
     * Whether a list shows its quests under due-date headings.
     *
     * Today's filter is "due today or earlier", so it genuinely contains two
     * kinds of thing and the headings earn their place under a plain sort.
     * Priority and random are the two sorts grouping would fight: priority
     * already weighs how late a quest is against everything else and places
     * pins at the very top and bottom.
     */
    public fun grouped(view: QuestView): Boolean =
        view in GroupedViews && sort.key != SortKey.RANDOM && sort.key != SortKey.PRIORITY

    /** Folds or unfolds a Plan branch. */
    public fun setCollapsed(id: String, collapsed: Boolean) {
        planCollapsed = if (collapsed) planCollapsed + id else planCollapsed - id
    }

    public fun expandAll() {
        planCollapsed = emptySet()
    }

    /** Folds every branch that has open sub-quests, leaving the top level. */
    public fun collapseAll(state: QuestsState) {
        planCollapsed = state.openQuests.mapNotNull { it.parentId }.toSet()
    }

    /**
     * Shows a quest where it sits in the hierarchy: the Plan view, with every
     * branch above it unfolded and the cursor on it.
     */
    public fun showInPlan(id: String, state: QuestsState) {
        val quest = state.quests[id] ?: return
        planCollapsed = planCollapsed - state.ancestors(quest.id).map { it.id }.toSet()
        go(Route.Quests(QuestView.PLAN))
        selectedId = id
    }

    /** All terms must match somewhere, which is what people expect of a search box. */
    public fun search(state: QuestsState, query: String): List<Quest> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return state.quests.values.filter { quest ->
            val haystack = buildString {
                append(quest.title.lowercase())
                append(' ')
                append(quest.notes.lowercase())
                quest.skillIds.forEach { id ->
                    state.skills[id]?.let { append(' ').append(it.name.lowercase()) }
                }
            }
            terms.all { haystack.contains(it) }
        }
    }

    public fun energy(state: QuestsState): Int = Energy.current(state.profile, clock.localNow().toLocalTime())

    /** Moves the cursor, keeping it inside the list. */
    public fun moveCursor(list: List<Quest>, delta: Int) {
        if (list.isEmpty()) {
            selectedId = null
            return
        }
        val index = list.indexOfFirst { it.id == selectedId }
        val next = when {
            index < 0 -> if (delta > 0) 0 else list.lastIndex
            else -> (index + delta).coerceIn(0, list.lastIndex)
        }
        selectedId = list[next].id
    }

    public fun selectRandom(list: List<Quest>) {
        val open = list.filter { it.isOpen }
        if (open.isEmpty()) return
        randomSeed = clock.nowMillis()
        selectedId = open.random(kotlin.random.Random(randomSeed)).id
    }

    /**
     * The way out of whatever is currently in the way, one layer at a time.
     *
     * Clearing [typing] is not incidental. An overlay that owned a text field
     * leaves the flag set when it is dismissed, and a stuck flag suspends every
     * single-letter shortcut in the app — the keyboard appears to stop working
     * and nothing says why.
     */
    public fun escape() {
        when {
            overlay != null -> overlay = null
            quickAddOpen -> closeQuickAdd()
            searchOpen -> closeSearch()
            marked.isNotEmpty() -> clearMarks()
            else -> selectedId = null
        }
        typing = false
    }
}
