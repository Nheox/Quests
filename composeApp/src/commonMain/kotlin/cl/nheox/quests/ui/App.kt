package cl.nheox.quests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.SettingsFields
import cl.nheox.quests.i18n.Translations
import cl.nheox.quests.ui.components.VLine
import cl.nheox.quests.ui.screens.HelpScreen
import cl.nheox.quests.ui.screens.InsightScreen
import cl.nheox.quests.ui.screens.InventoryScreen
import cl.nheox.quests.ui.screens.OnboardingOverlay
import cl.nheox.quests.ui.screens.PlanScreen
import cl.nheox.quests.ui.screens.QuestEditorPane
import cl.nheox.quests.ui.screens.QuestListScreen
import cl.nheox.quests.ui.screens.RewardsScreen
import cl.nheox.quests.ui.screens.ScratchpadScreen
import cl.nheox.quests.ui.screens.SettingsScreen
import cl.nheox.quests.ui.screens.SkillsScreen
import cl.nheox.quests.ui.theme.QuestsTheme
import cl.nheox.quests.ui.theme.theme

/**
 * The whole window.
 *
 * Three columns: the rail, the list, and the editor. The editor is a pane
 * rather than a dialog so that editing never hides the list — on a desktop
 * there is room for both, and a modal editor is how a phone app behaves in a
 * big window, which is the thing this app exists to stop doing.
 */
@Composable
public fun QuestsApp(
    app: AppState,
    actions: AppActions,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by app.store.state.collectAsState()
    val undoable by app.store.undoable.collectAsState()
    val levelUps by app.store.levelUps.collectAsState()
    val strings = remember(state.settings.language) { Translations.of(state.settings.language) }
    val keyboardFocus = remember { FocusRequester() }

    QuestsTheme(dark = darkTheme, strings = strings, clock = app.clock) {
        // The whole window is one key handler. Compose routes a preview key
        // event to the focused subtree first, so a text field still wins for
        // ordinary typing, and `app.typing` suppresses the bare-letter commands.
        Box(
            modifier
                .fillMaxSize()
                .background(theme.background)
                .focusRequester(keyboardFocus)
                .focusable()
                .onPreviewKeyEvent { event -> Shortcuts.handle(event, app, state, actions) },
        ) {
            Row(Modifier.fillMaxSize()) {
                NavRail(app, state)
                VLine()

                Box(Modifier.weight(1f)) {
                    when (val route = app.route) {
                        is Route.Quests -> if (route.view == QuestView.PLAN) {
                            PlanScreen(app, state)
                        } else {
                            QuestListScreen(
                                app = app,
                                state = state,
                                quests = app.visibleQuests(state),
                                onQuickAdd = actions.quickAdd,
                            )
                        }

                        Route.Skills -> SkillsScreen(app, state)
                        Route.Rewards -> RewardsScreen(app, state)
                        Route.Inventory -> InventoryScreen(app, state)
                        Route.Scratchpad -> ScratchpadScreen(app, state)
                        Route.Insight -> InsightScreen(app, state)
                        Route.Help -> HelpScreen()
                        Route.Settings -> SettingsScreen(
                            app = app,
                            state = state,
                            onExportNow = actions.exportNow,
                            onInstallHotkey = actions.installHotkey,
                            onOpenFolder = actions.openFolder,
                            onChooseFolder = actions.chooseFolder,
                            onCompact = actions.compact,
                        )
                    }
                }

                // The editor only takes its column when there is something in it.
                if (app.selectedId != null && app.route is Route.Quests) {
                    VLine()
                    QuestEditorPane(app, state)
                }
            }

            Column(Modifier.align(Alignment.BottomStart)) {
                LevelUpBar(levelUps, onDismiss = { app.store.consumeLevelUps() })
                MessageBar(app.message, onDismiss = { app.dismissMessage() })
                UndoBar(
                    undoable = undoable,
                    onUndo = { app.store.undo() },
                    onDismiss = { app.store.clearUndo() },
                )
            }

            when (val overlay = app.overlay) {
                null -> Unit
                Overlay.CommandPalette -> CommandPaletteOverlay(app, state, actions)
                Overlay.Suggester -> SuggesterOverlay(app, state)
                Overlay.Onboarding -> OnboardingOverlay(
                    onFinish = {
                        app.store.changeSettings(FieldPatch().set(SettingsFields.ONBOARDING_COMPLETE, true))
                        app.overlay = null
                    },
                )

                is Overlay.Reschedule -> RescheduleOverlay(app, state, overlay.questIds)
                is Overlay.SkillPicker -> SkillPickerOverlay(app, state, overlay.questIds)
                is Overlay.ConfirmDelete -> ConfirmDeleteOverlay(app, state, overlay.questIds, overlay.title)
            }
        }

        // Without this the window opens with nothing focused, and the first
        // keystroke is swallowed establishing focus instead of doing something.
        LaunchedEffect(Unit) {
            keyboardFocus.requestFocus()
            if (!state.settings.onboardingComplete) app.overlay = Overlay.Onboarding
        }

        // Focus has to come *back* when an overlay closes. An overlay's text
        // field takes focus away from this Box, and when the overlay leaves the
        // composition the focus goes nowhere — so the root stops receiving key
        // events and every shortcut silently stops working until something is
        // clicked. Re-requesting it is the whole of "keyboard first" surviving
        // contact with a dialog.
        LaunchedEffect(app.overlay, app.quickAddOpen, app.searchOpen) {
            if (app.overlay == null && !app.quickAddOpen && !app.searchOpen) {
                app.typing = false
                keyboardFocus.requestFocus()
            }
        }
    }
}
