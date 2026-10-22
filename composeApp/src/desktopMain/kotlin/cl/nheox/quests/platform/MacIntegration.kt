package cl.nheox.quests.platform

import java.awt.Desktop
import java.awt.desktop.AppReopenedListener
import java.awt.desktop.QuitResponse

/**
 * Where Quests behaves like a Mac app rather than a Java program running on
 * one: the name in the menu bar, the Dock icon that brings the window back,
 * and the About, Settings and Quit items of the application menu.
 *
 * Everything here is asked of `java.awt.Desktop` only when it says it supports
 * it, so on Linux, where none of these actions exist, every call is a no-op
 * and nothing needs an `if (mac)` around it.
 */
public object MacIntegration {
    /**
     * What the application menu and the Dock ask for. AWT promises no
     * particular thread for these, so each should only record the request.
     */
    public class Handlers(
        public val reopen: () -> Unit,
        public val about: () -> Unit,
        public val settings: () -> Unit,
        public val quit: () -> Unit,
    )

    @Volatile
    private var pendingQuit: QuitResponse? = null

    /**
     * Process-wide properties AWT reads once, when it first starts.
     *
     * Must run before anything touches AWT — a window, the toolkit, the
     * clipboard — or macOS has already named the app after its main class and
     * fixed the title bars to the light appearance. Setting a system property
     * loads nothing, so this is safe as the first line of `main`.
     */
    public fun configureProcess() {
        System.setProperty("apple.awt.application.name", APP_NAME)
        System.setProperty("apple.awt.application.appearance", "system")
    }

    /**
     * Connects the Dock and the application menu.
     *
     * Returns true when a Dock click will now reopen the window. That is the
     * fact the close button depends on: hiding a window is only safe when
     * something can bring it back.
     */
    public fun install(handlers: Handlers): Boolean {
        val desktop = runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null }.getOrNull()
            ?: return false

        val reopens = desktop.supports(Desktop.Action.APP_EVENT_REOPENED) {
            addAppEventListener(AppReopenedListener { handlers.reopen() })
        }
        desktop.supports(Desktop.Action.APP_ABOUT) { setAboutHandler { handlers.about() } }
        desktop.supports(Desktop.Action.APP_PREFERENCES) { setPreferencesHandler { handlers.settings() } }
        // Cmd-Q, the Dock's Quit and logging out all arrive here. The answer to
        // macOS is held back until the windows have closed properly — see
        // [finishQuit] — rather than given now: agreeing at once exits the JVM
        // under the UI's feet, and refusing tells a logging-out Mac that
        // Quests cancelled the logout.
        desktop.supports(Desktop.Action.APP_QUIT_HANDLER) {
            setQuitHandler { _, response ->
                pendingQuit = response
                handlers.quit()
            }
        }
        return reopens
    }

    /**
     * Answers a quit that macOS asked for, once the application has wound down.
     * Agreeing ends the process, so this does not return in that case; with no
     * quit pending it does nothing.
     */
    public fun finishQuit() {
        pendingQuit?.let { response ->
            pendingQuit = null
            response.performQuit()
        }
    }

    private inline fun Desktop.supports(action: Desktop.Action, install: Desktop.() -> Unit): Boolean =
        runCatching {
            if (!isSupported(action)) return@runCatching false
            install()
            true
        }.getOrDefault(false)

    private const val APP_NAME: String = "Quests"
}
