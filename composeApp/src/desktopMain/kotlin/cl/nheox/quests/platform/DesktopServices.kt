package cl.nheox.quests.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * The few places the app has to talk to the desktop itself.
 *
 * Every call is wrapped: a headless session, a sandbox without a portal, or a
 * machine with no file manager installed must degrade to "that button did
 * nothing" rather than taking the window down with it.
 */
public object DesktopServices {
    public fun copyToClipboard(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)

    public fun openFolder(path: Path): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile())
            true
        } else {
            // Desktop.open is unreliable on Linux; xdg-open is what actually works.
            ProcessBuilder("xdg-open", path.toString()).start()
            true
        }
    }.getOrDefault(false)

    /**
     * Asks for a folder with the platform's own chooser, or null if the user
     * cancelled.
     *
     * The chooser should be the native one because this is where people create
     * the folder as well as pick it — inside Dropbox, inside iCloud Drive —
     * with the sidebar, bookmarks and New Folder button they already know,
     * none of which Swing's chooser can show. Each candidate from
     * [FolderPicker.candidates] is tried in turn; one that cannot run hands
     * over to the next, and a cancel stops the search, because asking twice is
     * worse than not asking.
     *
     * External pickers are separate processes and run off the UI thread, so the
     * window keeps painting while they are up. The AWT and Swing dialogs are
     * modal and must be shown from the event thread.
     */
    public suspend fun chooseFolder(title: String, startingAt: Path?): Path? {
        val start = startingAt?.let { DataFolder.nearestExisting(it) }
            ?: Paths.get(System.getProperty("user.home"))
        val os = System.getProperty("os.name").orEmpty()
        for (picker in FolderPicker.candidates(os, ::isOnPath)) {
            val attempt = when (picker) {
                FolderPicker.MAC_PANEL -> withContext(Dispatchers.Swing) { macPanel(title, start) }
                FolderPicker.ZENITY, FolderPicker.KDIALOG -> external(FolderPicker.command(picker, title, start))
                FolderPicker.SWING -> withContext(Dispatchers.Swing) { swingChooser(title, start) }
            }
            when (attempt) {
                is PickerAttempt.Chosen -> return attempt.folder
                PickerAttempt.Cancelled -> return null
                PickerAttempt.Unavailable -> Unit
            }
        }
        return null
    }

    /**
     * The Finder panel. `apple.awt.fileDialogForDirectories` is what turns
     * AWT's file dialog into a folder chooser — with Finder's own New Folder
     * button — and it is global, so it is set only for the life of this one
     * dialog and put back afterwards.
     */
    private fun macPanel(title: String, start: Path): PickerAttempt = runCatching {
        val previous = System.getProperty(MAC_DIRECTORIES_PROPERTY)
        System.setProperty(MAC_DIRECTORIES_PROPERTY, "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.directory = start.toString()
            dialog.isVisible = true
            val directory = dialog.directory
            val file = dialog.file
            if (directory == null || file == null) PickerAttempt.Cancelled else PickerAttempt.Chosen(Paths.get(directory, file))
        } finally {
            if (previous == null) System.clearProperty(MAC_DIRECTORIES_PROPERTY) else System.setProperty(MAC_DIRECTORIES_PROPERTY, previous)
        }
    }.getOrDefault(PickerAttempt.Unavailable)

    /**
     * Runs a picker process and reads its answer. If whoever asked stops
     * waiting — the window closed, the app quit — the picker is closed too,
     * rather than left on screen answering nobody.
     */
    private suspend fun external(command: List<String>): PickerAttempt {
        val process = runCatching {
            ProcessBuilder(command)
                // GTK and Qt both talk on stderr; nobody reads it, and an
                // unread pipe that fills up would hang the picker.
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrElse { return PickerAttempt.Unavailable }
        return coroutineScope {
            // The read blocks and cannot be interrupted, so it runs apart and
            // the wait for it is what gets cancelled. Destroying the process
            // closes its output, which is what ends the read.
            val answer = async(Dispatchers.IO) {
                runCatching {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    FolderPicker.interpret(process.waitFor(), output)
                }.getOrDefault(PickerAttempt.Unavailable)
            }
            try {
                answer.await()
            } finally {
                process.destroy()
            }
        }
    }

    /** The last resort, so that a bare system still gets a chooser of some kind. */
    private fun swingChooser(title: String, start: Path): PickerAttempt = runCatching {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(start.toFile())
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isMultiSelectionEnabled = false
        val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
        chooser.selectedFile?.toPath()?.takeIf { approved }?.let { PickerAttempt.Chosen(it) } ?: PickerAttempt.Cancelled
    }.getOrDefault(PickerAttempt.Unavailable)

    private fun isOnPath(binary: String): Boolean = runCatching {
        System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.any { directory ->
            Files.isExecutable(Paths.get(directory, binary))
        }
    }.getOrDefault(false)

    private const val MAC_DIRECTORIES_PROPERTY: String = "apple.awt.fileDialogForDirectories"
}

/** What one picker did. */
internal sealed interface PickerAttempt {
    data class Chosen(val folder: Path) : PickerAttempt

    data object Cancelled : PickerAttempt

    /** It could not run at all — not installed, no display, crashed — so try the next one. */
    data object Unavailable : PickerAttempt
}

/**
 * Which folder choosers to try, in order, and how to talk to the external
 * ones. Pure, so the choice can be tested without a desktop.
 */
internal enum class FolderPicker {
    MAC_PANEL,
    ZENITY,
    KDIALOG,
    SWING,
    ;

    companion object {
        /**
         * macOS gets Finder's panel. Linux gets the GTK chooser through
         * `zenity`, then KDE's through `kdialog` — both have a create-folder
         * button — and Swing always comes last, because it needs nothing
         * installed.
         */
        fun candidates(osName: String, isOnPath: (String) -> Boolean): List<FolderPicker> = buildList {
            if (osName.lowercase().contains("mac")) {
                add(MAC_PANEL)
            } else {
                if (isOnPath("zenity")) add(ZENITY)
                if (isOnPath("kdialog")) add(KDIALOG)
            }
            add(SWING)
        }

        fun command(picker: FolderPicker, title: String, start: Path): List<String> = when (picker) {
            // The trailing separator makes zenity open *inside* the folder
            // rather than in its parent with the folder preselected.
            ZENITY -> listOf("zenity", "--file-selection", "--directory", "--title=$title", "--filename=$start${File.separator}")
            KDIALOG -> listOf("kdialog", "--title", title, "--getexistingdirectory", start.toString())
            MAC_PANEL, SWING -> error("$picker is not an external program")
        }

        /** Both programs agree: 0 with a path is a choice, 1 is a cancel, anything else is a failure to run. */
        fun interpret(exitCode: Int, output: String): PickerAttempt {
            val path = output.lineSequence().firstOrNull()?.trim().orEmpty()
            return when {
                exitCode == 0 && path.isNotEmpty() -> runCatching { PickerAttempt.Chosen(Paths.get(path)) }
                    .getOrDefault(PickerAttempt.Unavailable)
                exitCode == 0 || exitCode == 1 -> PickerAttempt.Cancelled
                else -> PickerAttempt.Unavailable
            }
        }
    }
}
