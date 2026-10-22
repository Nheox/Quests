package cl.nheox.quests.platform

import java.nio.file.Files
import java.nio.file.Path

/** How finishing a data-folder change went, for the launch to report. */
public sealed interface SwitchOutcome {
    /** Nothing to tell: the change named the folder already in use. */
    public data object Unchanged : SwitchOutcome

    /** The files were copied and the new folder is the one to open. */
    public data class Switched(val folder: Path) : SwitchOutcome

    /** The new folder was not taken up; the old one opens as before. */
    public data class Failed(val folder: Path) : SwitchOutcome

    /** The new folder holds a file of this name with other content; nothing was overwritten. */
    public data class Conflict(val file: String) : SwitchOutcome
}

/**
 * Finishes a data-folder change chosen in Settings during an earlier session.
 *
 * Done at launch, before anything is loaded, because that is the one moment
 * when this device is certainly not writing to the old folder. The file
 * system work is [DataFolder.copyOwnFiles]; what is passed in is what touches
 * the config directory, so the decision can be tested without it.
 */
public object FolderSwitch {
    /**
     * Copies this device's own files from [stored] to [pending], then repoints
     * the config with [remember]. Returns the folder to open and what to say.
     *
     * The device is asked for only once there is something to copy, because
     * asking may create its identity file. With no stored folder there is
     * nothing to carry across, and first run will ask where the data lives.
     */
    public fun finish(
        stored: Path?,
        pending: Path,
        device: () -> String,
        remember: (Path) -> Boolean,
    ): Pair<Path?, SwitchOutcome?> {
        if (stored == null) return null to null
        // With the old folder gone (an unplugged drive) there is nothing to
        // copy, and switching anyway would leave this device's history behind
        // unseen.
        if (!Files.isDirectory(stored)) return stored to SwitchOutcome.Failed(pending)
        // An unreadable old folder throws rather than answering; that is a
        // failed switch like any other, not a reason to fail the launch.
        val result = runCatching { DataFolder.copyOwnFiles(stored, pending, device()) }.getOrNull()
            ?: return stored to SwitchOutcome.Failed(pending)
        return when (result) {
            is Move.Copied -> if (remember(pending)) {
                pending to SwitchOutcome.Switched(pending)
            } else {
                stored to SwitchOutcome.Failed(pending)
            }

            Move.SameFolder -> stored to SwitchOutcome.Unchanged
            is Move.Conflict -> stored to SwitchOutcome.Conflict(result.file)
            is Move.Unusable, is Move.Failed -> stored to SwitchOutcome.Failed(pending)
        }
    }
}
