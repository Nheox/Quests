package cl.nheox.quests.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.projection.QuestsState

/**
 * Everything the app can be told to do, with no UI attached.
 *
 * Shared by the keyboard handler and the command palette, so a command cannot
 * exist in one and be missing from the other — which is the usual way an app
 * ends up with mouse-only actions despite claiming to be keyboard-complete.
 */
public class AppActions(
    public val quickAdd: (String) -> Unit,
    public val exportNow: () -> Unit,
    public val compact: () -> String?,
    public val installHotkey: () -> Boolean,
    public val openFolder: () -> Unit,
    public val chooseFolder: () -> Unit,
    public val copyQuestAsText: (String) -> Unit,
)

/**
 * The keyboard map.
 *
 * Two rules decide the whole layout:
 *
 *  1. Single letters act on the selected quest, and are *suspended entirely*
 *     while a text field has focus. Without that, typing "dar" into a title
 *     would duplicate, add and reschedule.
 *  2. Ctrl (or Cmd) plus a key is global and always available.
 */
public object Shortcuts {
    /** Ctrl on Linux and Windows, Cmd on macOS — both accepted everywhere. */
    private val KeyEvent.command: Boolean get() = isCtrlPressed || isMetaPressed

    public fun handle(
        event: KeyEvent,
        app: AppState,
        state: QuestsState,
        actions: AppActions,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        // Escape always works, even mid-typing: it is the way out.
        if (event.key == Key.Escape) {
            app.escape()
            return true
        }

        if (event.command) {
            return handleCommandKey(event, app, state, actions)
        }

        // Anything below acts on the list, so it must not fire while typing.
        if (app.typing) return false

        return handleBareKey(event, app, state, actions)
    }

    private fun handleCommandKey(
        event: KeyEvent,
        app: AppState,
        state: QuestsState,
        actions: AppActions,
    ): Boolean = when (event.key) {
        Key.K -> {
            app.overlay = if (app.overlay == Overlay.CommandPalette) null else Overlay.CommandPalette
            true
        }

        Key.N -> {
            app.openQuickAdd()
            true
        }

        Key.F -> {
            app.openSearch()
            true
        }

        Key.Z -> {
            app.store.undo()
            true
        }

        Key.R -> {
            app.store.reload()
            true
        }

        Key.E -> {
            actions.exportNow()
            true
        }

        Key.Comma -> {
            app.go(Route.Settings)
            true
        }

        Key.Zero -> {
            app.go(Route.Standing)
            true
        }

        Key.One -> jump(app, QuestView.TODAY)
        Key.Two -> jump(app, QuestView.TOMORROW)
        Key.Three -> jump(app, QuestView.OVERDUE)
        Key.Four -> jump(app, QuestView.NEXT)
        Key.Five -> jump(app, QuestView.PLAN)
        Key.Six -> jump(app, QuestView.ALL)
        Key.Seven -> jump(app, QuestView.COMPLETED)

        Key.Eight -> {
            app.go(Route.Skills)
            true
        }

        Key.Nine -> {
            app.go(Route.Insight)
            true
        }

        Key.A -> {
            app.markAll(app.visibleQuests(state).map { it.id })
            true
        }

        else -> false
    }

    private fun handleBareKey(
        event: KeyEvent,
        app: AppState,
        state: QuestsState,
        actions: AppActions,
    ): Boolean {
        val list = app.visibleQuests(state)
        val selected = app.selectedId
        val today = app.clock.today()

        return when (event.key) {
            Key.DirectionDown, Key.J -> {
                app.moveCursor(list, 1)
                true
            }

            Key.DirectionUp, Key.K -> {
                app.moveCursor(list, -1)
                true
            }

            Key.Slash -> {
                if (event.isShiftPressed) app.go(Route.Help) else app.openSearch()
                true
            }

            Key.N -> {
                app.openQuickAdd()
                true
            }

            Key.Spacebar, Key.C -> {
                selected?.let { app.store.completeQuest(it) }
                selected != null
            }

            Key.X -> {
                selected?.let { app.store.failQuest(it) }
                selected != null
            }

            Key.O -> {
                selected?.let { app.store.reopenQuest(it) }
                selected != null
            }

            Key.D -> {
                selected?.let { app.store.duplicateQuest(it)?.let { id -> app.selectedId = id } }
                selected != null
            }

            Key.M -> {
                if (event.isShiftPressed) {
                    // Shift-M: Move under… for the marked quests, or the cursor's.
                    val targets = app.targets()
                    if (targets.isNotEmpty()) app.overlay = Overlay.MoveUnder(targets)
                    targets.isNotEmpty()
                } else {
                    selected?.let { app.toggleMark(it) }
                    selected != null
                }
            }

            Key.Delete, Key.Backspace -> {
                val targets = app.targets()
                if (targets.isEmpty()) {
                    false
                } else {
                    app.overlay = Overlay.ConfirmDelete(
                        targets,
                        state.quests[targets.first()]?.title.orEmpty(),
                    )
                    true
                }
            }

            Key.T -> {
                val targets = app.targets()
                if (targets.isEmpty()) {
                    false
                } else {
                    val date = if (event.isShiftPressed) today.plusDays(1) else today
                    targets.forEach { app.store.reschedule(it, date) }
                    true
                }
            }

            Key.W -> {
                val targets = app.targets()
                if (targets.isEmpty()) {
                    false
                } else {
                    val date = if (event.isShiftPressed) today.plusMonths(1) else today.plusWeeks(1)
                    targets.forEach { app.store.reschedule(it, date) }
                    true
                }
            }

            Key.Zero -> {
                val targets = app.targets()
                targets.forEach { app.store.reschedule(it, null) }
                targets.isNotEmpty()
            }

            Key.P -> {
                selected?.let { app.store.promoteToTopLevel(it) }
                selected != null
            }

            Key.LeftBracket -> {
                selected?.let { app.store.pinToTop(it) }
                selected != null
            }

            Key.RightBracket -> {
                selected?.let { app.store.pinToBottom(it) }
                selected != null
            }

            Key.Backslash -> {
                selected?.let { app.store.clearPin(it) }
                selected != null
            }

            Key.R -> {
                app.selectRandom(list)
                true
            }

            Key.A -> {
                app.overlay = Overlay.Suggester
                true
            }

            Key.S -> {
                selected?.let { actions.copyQuestAsText(it) }
                selected != null
            }

            Key.G -> {
                app.store.markWorked(selected ?: return false)
                true
            }

            else -> false
        }
    }

    private fun jump(app: AppState, view: QuestView): Boolean {
        app.goToView(view)
        return true
    }

    /**
     * The table the help screen renders.
     *
     * Written out by hand rather than derived from [handle], because the
     * descriptions are prose and the key names need to read the way a person
     * says them, not the way `Key` spells them.
     */
    public fun reference(): List<Pair<String, String>> = listOf(
        "Ctrl+K" to "Command palette",
        "Ctrl+N" to "New quest",
        "Ctrl+F" to "Search",
        "Ctrl+Z" to "Undo the last complete, fail or delete",
        "Ctrl+R" to "Reload the folder from disk",
        "Ctrl+E" to "Write the calendar and Markdown exports now",
        "Ctrl+A" to "Select every quest in the list",
        "Ctrl+," to "Settings",
        "Ctrl+0" to "Standing, where things stand at a glance",
        "Shift+M" to "Move the selected quests under another quest",
        "Ctrl+1…9" to "Jump to a view",
        "↑ ↓" to "Move the cursor (j and k also work)",
        "Space" to "Complete the selected quest (c also works)",
        "X" to "Fail it",
        "O" to "Reopen it",
        "D" to "Duplicate it",
        "G" to "Mark it as worked on",
        "M" to "Add it to the selection",
        "Del" to "Delete the selection",
        "T" to "Due today (Shift+T for tomorrow)",
        "W" to "Due in a week (Shift+W in a month)",
        "0" to "Clear the due date",
        "P" to "Promote to top level",
        "[  ]" to "Pin to the top or bottom of the list",
        "\\" to "Clear the pin, back to automatic order",
        "R" to "Pick a random open quest",
        "A" to "Ask what to do now",
        "S" to "Copy the quest as text",
        "?" to "This help",
        "Esc" to "Close, or clear the selection",
    )
}
