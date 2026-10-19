package cl.nheox.quests.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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
     * Asks for a folder.
     *
     * Swing's chooser rather than AWT's `FileDialog`, because only the former
     * can be put into directories-only mode on Linux — and picking the sync
     * folder is the one dialog in the app where choosing a file instead of a
     * folder would be a confusing failure.
     */
    public fun chooseFolder(title: String, startingAt: Path?): Path? = runCatching {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(startingAt?.toFile())
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isMultiSelectionEnabled = false
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
    }.getOrNull()

    @Suppress("unused")
    private fun legacyFileDialog(title: String): Path? = runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        dialog.directory?.let { directory -> Paths.get(directory, dialog.file.orEmpty()) }
    }.getOrNull()
}
