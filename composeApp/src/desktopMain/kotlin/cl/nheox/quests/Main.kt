package cl.nheox.quests

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberNotification
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.QuickAdd
import cl.nheox.quests.domain.QuickAddResult
import cl.nheox.quests.events.Compaction
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.Vault
import cl.nheox.quests.export.Exporter
import cl.nheox.quests.i18n.Translations
import cl.nheox.quests.platform.AppPaths
import cl.nheox.quests.platform.DesktopServices
import cl.nheox.quests.platform.GnomeKeybinding
import cl.nheox.quests.platform.ReminderAlert
import cl.nheox.quests.platform.ReminderScheduler
import cl.nheox.quests.platform.SingleInstance
import cl.nheox.quests.projection.QuestDraft
import cl.nheox.quests.projection.QuestStore
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppActions
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.QuestsApp
import cl.nheox.quests.ui.theme.QuestsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Path

/**
 * Command-line surface.
 *
 * Tiny on purpose. `--capture` is the one that matters: it is what a desktop
 * keybinding runs, and it is handed to the already-running instance rather than
 * starting a second app (§8.3).
 */
private object Cli {
    const val CAPTURE = "--capture"
    const val SHOW = "--show"
    const val VERSION = "--version"
    const val HELP = "--help"

    val usage: String = """
        Quests ${BuildInfo.VERSION} — a local-first gamified quest tracker.

          quests              Open the main window.
          quests --capture    Open the quick-capture field. Bind this to a key
                              in your desktop environment's keyboard settings.
          quests --show       Bring the running window to the front.
          quests --version    Print the version and exit.
          quests --help       Print this and exit.

        Your data lives in ${AppPaths.resolveVault()} as plain JSONL. Sync that
        folder with whatever you already use; Quests never touches the network.
    """.trimIndent()
}

/**
 * Everything the app needs, constructed once and passed down by hand.
 *
 * Manual constructor injection, as settled in §8.1 — the project has exactly one
 * of each of these for the life of the process, and a DI container would be
 * machinery in place of four lines.
 */
private class Runtime(
    val clock: Clock,
    val device: DeviceIdentity,
    val vault: Vault,
    val store: QuestStore,
    val exporter: Exporter,
    val scope: CoroutineScope,
) {
    companion object {
        fun open(vaultRoot: Path): Runtime {
            val clock = Clock.System
            val device = DeviceIdentity.loadOrCreate(AppPaths.deviceFile(), AppPaths.hostname())
            val vault = Vault(vaultRoot)
            vault.prepare()
            val log = FileEventLog(vault, device)
            val store = QuestStore(vault, device, log, clock)
            val exporter = Exporter(vault, clock)
            return Runtime(
                clock = clock,
                device = device,
                vault = vault,
                store = store,
                exporter = exporter,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
    }
}

public fun main(args: Array<String>) {
    if (Cli.VERSION in args) {
        println(BuildInfo.VERSION)
        return
    }
    if (Cli.HELP in args || "-h" in args) {
        println(Cli.usage)
        return
    }

    val wantsCapture = Cli.CAPTURE in args
    val instance = SingleInstance()

    // If another Quests already holds the lock, hand it the request and leave.
    // This is the whole of the quick-capture mechanism from the caller's side.
    if (!instance.claim()) {
        val delivered = instance.send(
            if (wantsCapture) SingleInstance.Command.CAPTURE else SingleInstance.Command.SHOW,
        )
        if (!delivered) {
            System.err.println("Quests is already running but did not answer. Try closing it from the tray.")
        }
        return
    }

    Runtime.open(AppPaths.resolveVault()).let { runtime ->
        java.lang.Runtime.getRuntime().addShutdownHook(
            Thread {
                instance.release()
                runtime.scope.cancel()
            },
        )
        launchUi(runtime, instance, startInCapture = wantsCapture)
    }
}

private fun launchUi(
    runtime: Runtime,
    instance: SingleInstance,
    startInCapture: Boolean,
) {
    runtime.store.load()

    application {
        val state by runtime.store.state.collectAsState()
        val app = remember { AppState(runtime.store, runtime.clock) }
        val trayState = rememberTrayState()
        val uiScope = rememberCoroutineScope()

        var mainVisible by remember { mutableStateOf(!startInCapture) }
        var captureVisible by remember { mutableStateOf(startInCapture) }

        // Read straight off the classpath with ImageIO rather than through the
        // Compose resources codegen: it is one line, it has no build-time
        // generation step, and the same PNG is what jpackage installs as the
        // desktop icon.
        val icon = remember { loadIcon() }
        val strings = remember(state.settings.language) { Translations.of(state.settings.language) }

        // Every write re-generates the exports, so the calendar feed a phone is
        // subscribed to is never more than one action out of date.
        LaunchedEffect(Unit) {
            runtime.store.onChanged = { current ->
                runtime.exporter.exportAll(current)
            }
            runtime.exporter.exportAll(runtime.store.state.value)
        }

        // The single-instance socket is what turns `quests --capture` into an
        // open window here. If it cannot be created the app carries on without
        // it: only the keybinding stops working.
        LaunchedEffect(Unit) {
            val listening = instance.listen { command ->
                when (command) {
                    SingleInstance.Command.CAPTURE -> captureVisible = true
                    SingleInstance.Command.SHOW -> mainVisible = true
                }
            }
            if (!listening) app.notify(strings.messageCaptureUnavailable)
        }

        val scheduler = remember {
            ReminderScheduler(
                scope = runtime.scope,
                clock = runtime.clock,
                state = { runtime.store.state.value },
                onAlert = { alert ->
                    trayState.sendNotification(
                        Notification(alert.title, alert.body, Notification.Type.Info),
                    )
                },
            )
        }

        LaunchedEffect(Unit) {
            scheduler.start()
            scheduler.launchSummary(runtime.store.state.value.settings)?.let { alert ->
                trayState.sendNotification(Notification(alert.title, alert.body, Notification.Type.Info))
            }
        }

        val actions = remember {
            AppActions(
                quickAdd = { line -> applyQuickAdd(runtime.store, line, runtime.clock, app) },
                exportNow = {
                    val result = runtime.exporter.exportAll(runtime.store.state.value)
                    app.notify(
                        if (result.errors.isEmpty()) {
                            strings.messageExported
                        } else {
                            result.errors.joinToString("; ")
                        },
                    )
                },
                compact = {
                    val result = Compaction.run(
                        runtime.vault,
                        runtime.device,
                        FileEventLog(runtime.vault, runtime.device),
                    )
                    runtime.store.reload()
                    result?.takeIf { it.changedAnything }?.let {
                        strings.compactedSummary(it.droppedEvents, it.droppedFields)
                    }
                },
                installHotkey = { GnomeKeybinding.install() },
                openFolder = { DesktopServices.openFolder(runtime.vault.root) },
                chooseFolder = {
                    val chosen = DesktopServices.chooseFolder(strings.settingsFolder, runtime.vault.root)
                    if (chosen != null) {
                        // Takes effect on the next launch: swapping the vault under
                        // a running projection would mean rebuilding every piece of
                        // state mid-session, and a restart is both honest and rare.
                        AppPaths.rememberVault(chosen)
                        app.notify(chosen.toString())
                    }
                },
                copyQuestAsText = { id ->
                    val quest = runtime.store.state.value.quests[id]
                    if (quest != null && DesktopServices.copyToClipboard(questAsText(quest, runtime.store.state.value))) {
                        app.notify(strings.messageCopied)
                    }
                },
            )
        }

        val dark = isSystemInDarkTheme() || true

        // A headless session, a stripped desktop or a sandbox without a status
        // area has no tray. Hiding the window there would leave the app running
        // with no way to get it back, so the tray has to be real before the
        // close button is allowed to mean "hide".
        val trayAvailable = isTraySupported && state.settings.trayEnabled

        if (mainVisible) {
            Window(
                onCloseRequest = {
                    // Closing hides rather than quits when there is a tray to
                    // restore it from, so reminders keep firing with the window
                    // out of the way (§7.4).
                    if (trayAvailable) mainVisible = false else exitApplication()
                },
                title = strings.appName,
                icon = icon,
                state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
            ) {
                QuestsApp(app = app, actions = actions, darkTheme = dark)
            }
        }

        if (captureVisible) {
            Window(
                onCloseRequest = { captureVisible = false },
                title = strings.actionQuickCapture,
                icon = icon,
                undecorated = true,
                alwaysOnTop = true,
                resizable = false,
                state = rememberWindowState(
                    size = DpSize(620.dp, 150.dp),
                    // Centred on screen: quick capture appears wherever the user
                    // was looking, not wherever the main window happens to be.
                    position = WindowPosition(androidx.compose.ui.Alignment.Center),
                ),
            ) {
                QuestsTheme(dark = dark, strings = strings, clock = runtime.clock) {
                    QuickCaptureContent(
                        clock = runtime.clock,
                        text = strings,
                        onSubmit = { line -> applyQuickAdd(runtime.store, line, runtime.clock, app) },
                        onDismiss = {
                            captureVisible = false
                            // A capture-only launch has no other window, so letting
                            // it keep running would leave an invisible process
                            // holding the lock.
                            if (!mainVisible && startInCapture) exitApplication()
                        },
                    )
                }
            }
        }

        if (trayAvailable) {
            val openCount = state.openQuests.size
            Tray(
                icon = icon,
                state = trayState,
                tooltip = "${strings.appName} — ${strings.countOpen(openCount)}",
                onAction = { mainVisible = true },
                menu = {
                    Item(strings.actionQuickCapture, onClick = { captureVisible = true })
                    Item(strings.navToday, onClick = { mainVisible = true })
                    Item(strings.actionExportNow, onClick = { actions.exportNow() })
                    Separator()
                    Item(strings.actionClose, onClick = { exitApplication() })
                },
            )
        }
    }
}

private fun loadIcon(): BitmapPainter {
    val stream = checkNotNull(
        Thread.currentThread().contextClassLoader?.getResourceAsStream(ICON_RESOURCE)
            ?: object {}.javaClass.getResourceAsStream("/$ICON_RESOURCE"),
    ) { "missing bundled icon: $ICON_RESOURCE" }
    return stream.use { BitmapPainter(javax.imageio.ImageIO.read(it).toComposeImageBitmap()) }
}

private const val ICON_RESOURCE = "icons/quests-256.png"

/**
 * Turns a typed line into a quest.
 *
 * Skills named with `#` are created if they do not exist yet, because being
 * told "no such skill" by a capture box is how a capture box stops being used.
 */
internal fun applyQuickAdd(
    store: QuestStore,
    line: String,
    clock: Clock,
    app: AppState?,
): String? {
    val parsed: QuickAddResult = QuickAdd.parse(line, clock.today())
    if (parsed.title.isBlank()) return null

    val skillIds = parsed.skillNames.map { store.findOrCreateSkill(it) }.toSet()
    val id = store.createQuest(
        QuestDraft(
            title = parsed.title,
            difficulty = parsed.difficulty ?: cl.nheox.quests.domain.Difficulty.DEFAULT,
            fear = parsed.fear ?: cl.nheox.quests.domain.Fear.DEFAULT,
            urgency = parsed.urgency ?: cl.nheox.quests.domain.Urgency.DEFAULT,
            estimatedMinutes = parsed.estimatedMinutes,
            dueDate = parsed.dueDate,
            dueTime = parsed.dueTime,
            repetition = parsed.repetition,
            skillIds = skillIds,
        ),
    )
    app?.let {
        it.closeQuickAdd()
        it.selectedId = id
    }
    return id
}

/** "Share a quest as text" (§7.1) — plain enough to paste into a message. */
internal fun questAsText(quest: cl.nheox.quests.domain.Quest, state: QuestsState): String = buildString {
    append(quest.title)
    quest.dueDate?.let { date ->
        append("\nDue: ").append(date)
        quest.dueTime?.let { append(" ").append(it) }
    }
    append("\nDifficulty: ").append(quest.difficulty.name.lowercase().replace('_', ' '))
    if (quest.fear.index > 0) append("\nFear: ").append(quest.fear.name.lowercase())
    quest.estimatedMinutes?.let { append("\nEstimate: ").append(cl.nheox.quests.domain.Dates.formatMinutes(it)) }
    val skills = state.skillsOf(quest)
    if (skills.isNotEmpty()) append("\nSkills: ").append(skills.joinToString(", ") { it.name })
    if (quest.notes.isNotBlank()) append("\n\n").append(quest.notes)
}
