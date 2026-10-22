package cl.nheox.quests

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Language
import cl.nheox.quests.domain.QuickAdd
import cl.nheox.quests.domain.QuickAddResult
import cl.nheox.quests.events.Compaction
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.Vault
import cl.nheox.quests.export.Exporter
import cl.nheox.quests.i18n.Strings
import cl.nheox.quests.i18n.Translations
import cl.nheox.quests.platform.AppPaths
import cl.nheox.quests.platform.DataFolder
import cl.nheox.quests.platform.DesktopServices
import cl.nheox.quests.platform.GnomeKeybinding
import cl.nheox.quests.platform.LaunchPlan
import cl.nheox.quests.platform.MacIntegration
import cl.nheox.quests.platform.Move
import cl.nheox.quests.platform.ReminderScheduler
import cl.nheox.quests.platform.SingleInstance
import cl.nheox.quests.projection.QuestDraft
import cl.nheox.quests.projection.QuestStore
import cl.nheox.quests.projection.QuestsState
import cl.nheox.quests.ui.AppActions
import cl.nheox.quests.ui.AppState
import cl.nheox.quests.ui.QuestsApp
import cl.nheox.quests.ui.Route
import cl.nheox.quests.ui.theme.QuestsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

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

    fun usage(folder: Path?): String = """
        Quests ${BuildInfo.VERSION} — a local-first gamified quest tracker.

          quests              Open the main window.
          quests --capture    Open the quick-capture field. Bind this to a key
                              in your desktop environment's keyboard settings.
          quests --show       Bring the running window to the front.
          quests --version    Print the version and exit.
          quests --help       Print this and exit.

        ${folder?.let { "Your data lives in ${AppPaths.display(it)} as plain JSONL." } ?: "The first launch asks where your data should live."}
        Sync that folder with whatever you already use; Quests never touches
        the network.
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
        /**
         * Opens a data folder and replays it. Also what writes this device's
         * name to the config directory the first time (§1.2).
         */
        fun open(vaultRoot: Path): Runtime {
            val clock = Clock.System
            val device = DeviceIdentity.loadOrCreate(AppPaths.deviceFile(), AppPaths.hostname())
            val vault = Vault(vaultRoot)
            vault.prepare()
            val log = FileEventLog(vault, device)
            val store = QuestStore(vault, device, log, clock)
            store.load()
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

/**
 * The state that sits above any one window: which windows are wanted, and the
 * requests that reach the app from outside it — a second launch, the Dock, the
 * application menu.
 *
 * Those arrive on other threads and outside composition, so they land here as
 * snapshot state for the windows to act on, instead of each caller needing a
 * window to talk to — on first run there is no main window to talk to yet.
 */
private class Shell(startInCapture: Boolean) {
    var mainVisible: Boolean by mutableStateOf(!startInCapture)
    var captureVisible: Boolean by mutableStateOf(startInCapture)

    /** Counts requests to see the main window; each one raises it once. */
    var raises: Int by mutableStateOf(0)

    /** Counts `quests --capture` requests, which during first run raise its window instead. */
    var captures: Int by mutableStateOf(0)

    /** A screen the application menu asked for, consumed once the main window has it. */
    var route: Route? by mutableStateOf(null)

    var quitRequested: Boolean by mutableStateOf(false)

    fun showMain() {
        mainVisible = true
        raises++
    }

    fun showCapture() {
        captureVisible = true
        captures++
    }

    fun show(route: Route) {
        this.route = route
        showMain()
    }
}

public fun main(args: Array<String>) {
    // AWT reads these once, as it starts; nothing above this line may touch it.
    MacIntegration.configureProcess()

    if (Cli.VERSION in args) {
        println(BuildInfo.VERSION)
        return
    }
    if (Cli.HELP in args || "-h" in args) {
        println(Cli.usage(AppPaths.storedVault()))
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
    java.lang.Runtime.getRuntime().addShutdownHook(Thread { instance.release() })

    val (stored, notice) = switchToPendingFolder(AppPaths.storedVault())
    val plan = LaunchPlan.decide(stored, AppPaths.defaultVault(), DataFolder::inspect)
    // A folder that looked fine but still fails to open is asked about again,
    // not reported with a stack trace (DESIGN §5).
    val runtime = (plan as? LaunchPlan.Open)?.let { runCatching { Runtime.open(it.folder) }.getOrNull() }
    val ask = when {
        runtime != null -> null
        plan is LaunchPlan.Ask -> plan
        else -> LaunchPlan.Ask(checkNotNull(stored), previousUnavailable = true)
    }

    launchUi(runtime, ask, notice, instance, startInCapture = wantsCapture)
}

/**
 * Finishes a data-folder change chosen in Settings during an earlier session:
 * copies this device's own files across, then repoints the config.
 *
 * Done here, before anything is loaded, because this is the one moment when
 * this device is certainly not writing to the old folder. Returns the folder to
 * open and, if a switch was waiting, what to tell the user about it.
 */
private fun switchToPendingFolder(stored: Path?): Pair<Path?, ((Strings) -> String)?> {
    val pending = AppPaths.pendingVault() ?: return stored to null
    // Cleared whatever happens: a switch that failed is reported once, not
    // retried silently on every launch.
    AppPaths.clearPendingVault()
    if (stored == null) return null to null

    val shown = AppPaths.display(pending)
    // With the old folder gone (an unplugged drive) there is nothing to copy,
    // and switching anyway would leave this device's history behind unseen.
    if (!Files.isDirectory(stored)) return stored to { text -> text.folderSwitchFailed(shown) }

    val device = DeviceIdentity.loadOrCreate(AppPaths.deviceFile(), AppPaths.hostname())
    // An unreadable old folder throws rather than answering; that is a failed
    // switch like any other, not a reason to fail the launch.
    val result = runCatching { DataFolder.copyOwnFiles(stored, pending, device.id) }.getOrNull()
        ?: return stored to { text -> text.folderSwitchFailed(shown) }
    return when (result) {
        is Move.Copied -> if (AppPaths.rememberVault(pending)) {
            pending to { text -> text.folderSwitched(shown) }
        } else {
            stored to { text -> text.folderSwitchFailed(shown) }
        }

        Move.SameFolder -> stored to null
        is Move.Conflict -> stored to { text -> text.folderSwitchConflict(result.file) }
        is Move.Unusable, is Move.Failed -> stored to { text -> text.folderSwitchFailed(shown) }
    }
}

private fun launchUi(
    initial: Runtime?,
    ask: LaunchPlan.Ask?,
    notice: ((Strings) -> String)?,
    instance: SingleInstance,
    startInCapture: Boolean,
) {
    val shell = Shell(startInCapture)

    // Listening starts before any window, so a second launch during first run
    // is heard: SHOW raises the first-run window, and CAPTURE is held until a
    // folder exists, because there is nowhere to put a quest before then. If
    // the socket cannot be created the app carries on without it: only the
    // keybinding stops working.
    val listening = instance.listen { command ->
        when (command) {
            SingleInstance.Command.CAPTURE -> shell.showCapture()
            SingleInstance.Command.SHOW -> shell.showMain()
        }
    }

    val dockReopens = MacIntegration.install(
        MacIntegration.Handlers(
            reopen = { shell.showMain() },
            about = { shell.show(Route.Help) },
            settings = { shell.show(Route.Settings) },
            quit = { shell.quitRequested = true },
        ),
    )

    application(exitProcessOnExit = false) {
        var runtime by remember { mutableStateOf(initial) }

        // Read straight off the classpath with ImageIO rather than through the
        // Compose resources codegen: it is one line, it has no build-time
        // generation step, and the same PNG is what jpackage installs as the
        // desktop icon.
        val iconBitmap = remember { loadIcon() }
        val icon = remember { BitmapPainter(iconBitmap) }
        val dark = isSystemInDarkTheme() || true

        LaunchedEffect(shell.quitRequested) {
            if (shell.quitRequested) exitApplication()
        }

        val current = runtime
        if (current == null) {
            FirstRunWindow(
                shell = shell,
                icon = icon,
                // Drawn large, so it is filtered properly rather than nearest-neighbour.
                mark = remember { BitmapPainter(iconBitmap, filterQuality = FilterQuality.High) },
                dark = dark,
                ask = checkNotNull(ask),
                onOpened = { opened ->
                    // Asked for while there was nothing to show it in.
                    shell.route = null
                    shell.mainVisible = true
                    runtime = opened
                },
            )
        } else {
            AppWindows(
                runtime = current,
                shell = shell,
                icon = icon,
                dark = dark,
                startInCapture = startInCapture,
                dockReopens = dockReopens,
                onLaunch = { app, text ->
                    if (!listening) app.notify(text.messageCaptureUnavailable)
                    notice?.let { app.notify(it(text)) }
                },
            )
        }
    }

    // macOS asked to quit and is waiting for the answer; give it now that the
    // windows are gone. Then leave, as `application` would have by default:
    // AWT's threads would otherwise keep the process alive.
    MacIntegration.finishQuit()
    exitProcess(0)
}

/** The first-run window: one question, then the app in the same process. */
@Composable
private fun ApplicationScope.FirstRunWindow(
    shell: Shell,
    icon: BitmapPainter,
    mark: BitmapPainter,
    dark: Boolean,
    ask: LaunchPlan.Ask,
    onOpened: (Runtime) -> Unit,
) {
    val strings = remember { Translations.of(Language.EN) }
    Window(
        onCloseRequest = ::exitApplication,
        title = strings.appName,
        icon = icon,
        resizable = false,
        state = rememberWindowState(
            size = DpSize(560.dp, 460.dp),
            position = WindowPosition(androidx.compose.ui.Alignment.Center),
        ),
    ) {
        // Any request to see the app lands here until there is an app to see.
        // toFront alone: it activates the window and hands focus back to
        // what had it, whereas requestFocus on the frame would take focus
        // away from the content and leave the keyboard dead.
        LaunchedEffect(shell.raises, shell.captures) {
            if (shell.raises + shell.captures > 0) window.toFront()
        }
        QuestsTheme(dark = dark, strings = strings) {
            FirstRunContent(
                mark = mark,
                suggested = ask.suggested,
                previousUnavailable = ask.previousUnavailable,
                chooseFolder = { start -> DesktopServices.chooseFolder(strings.folderChooserTitle, start) },
                confirm = { folder ->
                    when (val opened = withContext(Dispatchers.IO) { openChosenFolder(folder) }) {
                        is Opening.Opened -> {
                            onOpened(opened.runtime)
                            null
                        }

                        is Opening.Failed -> opened.failure
                    }
                },
            )
        }
    }
}

private sealed interface Opening {
    data class Opened(val runtime: Runtime) : Opening

    data class Failed(val failure: FirstRunFailure) : Opening
}

/**
 * Creates the chosen folder, proves it writable, replays it, and only then
 * writes the choice down — so a folder that cannot be opened is never left as
 * the one the next launch tries.
 */
private fun openChosenFolder(folder: Path): Opening {
    if (!DataFolder.prepare(folder)) return Opening.Failed(FirstRunFailure.CANNOT_CREATE)
    val runtime = runCatching { Runtime.open(folder) }.getOrElse { return Opening.Failed(FirstRunFailure.CANNOT_OPEN) }
    if (!AppPaths.rememberVault(folder)) {
        runtime.scope.cancel()
        return Opening.Failed(FirstRunFailure.CANNOT_REMEMBER)
    }
    return Opening.Opened(runtime)
}

/** The main window, quick capture and the tray, for an opened folder. */
@Composable
private fun ApplicationScope.AppWindows(
    runtime: Runtime,
    shell: Shell,
    icon: BitmapPainter,
    dark: Boolean,
    startInCapture: Boolean,
    dockReopens: Boolean,
    onLaunch: (AppState, Strings) -> Unit,
) {
    val state by runtime.store.state.collectAsState()
    val app = remember { AppState(runtime.store, runtime.clock) }
    val trayState = rememberTrayState()
    val uiScope = rememberCoroutineScope()
    val strings = remember(state.settings.language) { Translations.of(state.settings.language) }

    // Every write re-generates the exports, so the calendar feed a phone is
    // subscribed to is never more than one action out of date.
    LaunchedEffect(Unit) {
        runtime.store.onChanged = { current ->
            runtime.exporter.exportAll(current)
        }
        runtime.exporter.exportAll(runtime.store.state.value)
        onLaunch(app, strings)
    }

    LaunchedEffect(shell.route) {
        shell.route?.let { route ->
            app.go(route)
            shell.route = null
        }
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

    // No launch notification: the Standing screen is the overdue summary now.
    LaunchedEffect(Unit) { scheduler.start() }

    var choosingFolder by remember { mutableStateOf(false) }

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
                // An external picker runs as its own process, so the button
                // stays live behind it; one chooser at a time.
                if (!choosingFolder) {
                    choosingFolder = true
                    uiScope.launch {
                        try {
                            DesktopServices.chooseFolder(strings.folderChooserTitle, runtime.vault.root)?.let { chosen ->
                                app.notify(
                                    withContext(Dispatchers.IO) {
                                        runCatching { scheduleFolderSwitch(runtime, chosen, strings) }
                                            .getOrElse { strings.folderSwitchFailed(AppPaths.display(chosen)) }
                                    },
                                )
                                app.pendingFolder = AppPaths.pendingVault()?.let(AppPaths::display)
                            }
                        } finally {
                            choosingFolder = false
                        }
                    }
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

    // A headless session, a stripped desktop or a sandbox without a status
    // area has no tray. Hiding the window there would leave the app running
    // with no way to get it back, so something has to be able to restore it —
    // the tray, or on macOS the Dock — before the close button may mean "hide".
    val trayAvailable = isTraySupported && state.settings.trayEnabled
    val mainWindowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))

    Window(
        onCloseRequest = {
            // Closing hides rather than quits, so reminders keep firing with
            // the window out of the way (§7.4).
            if (trayAvailable || dockReopens) shell.mainVisible = false else exitApplication()
        },
        // Hidden, not removed: the window keeps its size, place and scroll
        // position for when the Dock or the tray brings it back.
        visible = shell.mainVisible,
        title = strings.appName,
        icon = icon,
        state = mainWindowState,
    ) {
        // As on first run, toFront and not requestFocus, which would leave
        // the frame rather than the app holding the keyboard.
        LaunchedEffect(shell.raises) {
            if (shell.raises > 0 && shell.mainVisible) {
                mainWindowState.isMinimized = false
                window.toFront()
            }
        }
        QuestsApp(app = app, actions = actions, darkTheme = dark)
    }

    if (shell.captureVisible) {
        Window(
            onCloseRequest = { shell.captureVisible = false },
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
            // Ask the window manager for focus explicitly. An undecorated,
            // always-on-top window is not guaranteed to be given keyboard
            // focus on appearing, and a capture box you cannot type into is
            // worse than no capture box at all.
            LaunchedEffect(Unit) {
                window.toFront()
                window.requestFocus()
            }
            QuestsTheme(dark = dark, strings = strings, clock = runtime.clock) {
                QuickCaptureContent(
                    clock = runtime.clock,
                    text = strings,
                    onSubmit = { line -> applyQuickAdd(runtime.store, line, runtime.clock, app) },
                    onDismiss = {
                        shell.captureVisible = false
                        // A capture-only launch has no other window, so letting
                        // it keep running would leave an invisible process
                        // holding the lock.
                        if (!shell.mainVisible && startInCapture) exitApplication()
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
            onAction = { shell.showMain() },
            menu = {
                Item(strings.actionQuickCapture, onClick = { shell.showCapture() })
                Item(strings.navToday, onClick = { shell.showMain() })
                Item(strings.actionExportNow, onClick = { actions.exportNow() })
                Separator()
                Item(strings.actionClose, onClick = { exitApplication() })
            },
        )
    }
}

/**
 * Checks a folder chosen in Settings and, if nothing stands in the way, sets
 * it to take over at the next launch. Returns what to tell the user.
 *
 * Only checked now; the copy itself waits for the next launch (see
 * [switchToPendingFolder]), because anything this session writes after a copy
 * would be left behind in the old folder.
 */
private fun scheduleFolderSwitch(runtime: Runtime, chosen: Path, text: Strings): String =
    when (val check = DataFolder.checkMove(runtime.vault.root, chosen, runtime.device.id)) {
        Move.SameFolder -> {
            // Choosing the current folder again also takes back a switch
            // that was waiting.
            AppPaths.clearPendingVault()
            text.folderAlreadyInUse
        }

        is Move.Unusable -> folderStatusText(check.status, text)
        is Move.Conflict -> text.folderSwitchConflict(check.file)
        is Move.Ready -> if (AppPaths.rememberPendingVault(chosen)) {
            text.folderSwitchScheduled(AppPaths.display(chosen))
        } else {
            text.folderCannotRemember(AppPaths.display(AppPaths.configDir()))
        }
    }

private fun loadIcon(): ImageBitmap {
    val stream = checkNotNull(
        Thread.currentThread().contextClassLoader?.getResourceAsStream(ICON_RESOURCE)
            ?: object {}.javaClass.getResourceAsStream("/$ICON_RESOURCE"),
    ) { "missing bundled icon: $ICON_RESOURCE" }
    return stream.use { javax.imageio.ImageIO.read(it).toComposeImageBitmap() }
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
