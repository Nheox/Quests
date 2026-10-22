package cl.nheox.quests.platform

import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.Snapshots
import cl.nheox.quests.events.Vault
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** What a folder would mean as the data folder, read before anything is written to it. */
public sealed interface FolderStatus {
    /** Not there yet, and its nearest existing parent can be written to, so it can be created. */
    public data object Missing : FolderStatus

    public data object Empty : FolderStatus

    /** Holds logs already: one per device that has written here (§6.3). */
    public data class HasQuests(val devices: Int) : FolderStatus

    /** Holds something, but no logs. Quests keeps its files beside whatever is there. */
    public data object HasOtherFiles : FolderStatus

    public data object NotAFolder : FolderStatus

    public data object NotWritable : FolderStatus

    public val usable: Boolean get() = this != NotAFolder && this != NotWritable
}

/**
 * What moving this device's files to another data folder would do, or did.
 *
 * Two sealed views over one set of cases: [Check] is the answer before
 * anything is written, [Result] the answer after the copy. The cases both can
 * give are shared, so a caller handles a conflict the same way whenever it is
 * found.
 */
public object Move {
    public sealed interface Check

    public sealed interface Result

    public data object SameFolder : Check, Result

    public data class Unusable(val status: FolderStatus) : Check, Result

    /** The destination holds a file of this name, relative to the folder, with other content. */
    public data class Conflict(val file: String) : Check, Result

    /** Nothing stands in the way; these files (relative names) would be copied. */
    public data class Ready(val files: List<String>) : Check

    /** Done; these files were written, and the rest were already there and identical. */
    public data class Copied(val files: List<String>) : Result

    /** An I/O error part-way. Nothing was overwritten and the source was not touched. */
    public data class Failed(val file: String) : Result
}

/** What a launch does about the data folder, decided before any window opens. */
public sealed interface LaunchPlan {
    public data class Open(val folder: Path) : LaunchPlan

    /** Show the first-run window, offering [suggested]. */
    public data class Ask(val suggested: Path, val previousUnavailable: Boolean) : LaunchPlan

    public companion object {
        /**
         * Opens the stored folder only if it is there and writable. A folder
         * that has gone missing is *not* silently recreated: that would show
         * an empty list where the user's history was — the one failure that
         * reads as data loss — when the likelier truth is an unplugged drive or
         * a sync client that has not mounted yet.
         */
        public fun decide(stored: Path?, default: Path, inspect: (Path) -> FolderStatus): LaunchPlan {
            if (stored == null) return Ask(default, previousUnavailable = false)
            val status = inspect(stored)
            return if (status == FolderStatus.Missing || !status.usable) {
                Ask(stored, previousUnavailable = true)
            } else {
                Open(stored)
            }
        }
    }
}

/**
 * The data folder before it is opened: what is in a candidate, and how this
 * device's own files move from one folder to another.
 *
 * Moving copies and never deletes. The ownership rule (§6.3) is what makes the
 * copy safe to do without coordination: only this device ever writes
 * `events-<this device>.jsonl` and its own snapshots, so they are the only
 * files that are this device's to take along, and nothing else can be writing
 * them meanwhile. Other devices' logs are left exactly where they are, because
 * each of those devices decides for itself where its data lives (§1.2).
 */
public object DataFolder {
    public fun inspect(folder: Path): FolderStatus {
        if (Files.isDirectory(folder)) {
            if (!Files.isWritable(folder)) return FolderStatus.NotWritable
            // A folder that can be written but not listed is no use either:
            // the logs in it could never be read back.
            val devices = runCatching { DeviceIdentity.logFiles(folder).size }.getOrElse { return FolderStatus.NotWritable }
            if (devices > 0) return FolderStatus.HasQuests(devices)
            val empty = runCatching { Files.list(folder).use { it.findFirst().isEmpty } }.getOrDefault(false)
            return if (empty) FolderStatus.Empty else FolderStatus.HasOtherFiles
        }
        if (Files.exists(folder)) return FolderStatus.NotAFolder
        val parent = nearestExisting(folder) ?: return FolderStatus.NotWritable
        return when {
            !Files.isDirectory(parent) -> FolderStatus.NotAFolder
            !Files.isWritable(parent) -> FolderStatus.NotWritable
            else -> FolderStatus.Missing
        }
    }

    /**
     * Creates the folder if needed and proves it can be written to.
     *
     * Permission bits are not the whole answer — a read-only mount, a sandbox
     * or a sync client's placeholder folder can all say yes and then refuse —
     * so the proof is an actual file, written and removed.
     */
    public fun prepare(folder: Path): Boolean = runCatching {
        Files.createDirectories(folder)
        val probe = Files.createTempFile(folder, ".quests-", ".probe")
        Files.delete(probe)
        true
    }.getOrDefault(false)

    /** The closest folder that exists, for a picker to start in or a check to ask about. */
    public fun nearestExisting(path: Path): Path? {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null && !Files.exists(current)) current = current.parent
        return current
    }

    /** This device's own log and snapshots in [root], as names relative to it. */
    internal fun ownFiles(root: Path, device: String): List<String> {
        val vault = Vault(root)
        val log = vault.logFile(device)
        val names = mutableListOf<String>()
        if (Files.isRegularFile(log)) names += root.relativize(log).toString()
        Snapshots.listOwn(vault, device).forEach { names += root.relativize(it).toString() }
        return names
    }

    /** Everything that would stop a move, checked without writing anything. */
    public fun checkMove(from: Path, to: Path, device: String): Move.Check {
        if (sameFolder(from, to)) return Move.SameFolder
        val status = inspect(to)
        if (!status.usable) return Move.Unusable(status)
        val files = ownFiles(from, device)
        files.firstOrNull { differs(from.resolve(it), to.resolve(it)) }?.let { return Move.Conflict(it) }
        return Move.Ready(files.filterNot { Files.exists(to.resolve(it)) })
    }

    /**
     * Copies this device's own files from [from] into [to].
     *
     * Checks every file before writing any, so a conflict leaves the
     * destination as it was. A file already there with identical content is
     * left alone, which makes a retry after a failure harmless. Each copy goes
     * to a hidden temporary name first and is renamed into place without
     * replacing, so an interrupted copy never leaves a truncated log under the
     * real name — which would otherwise block every later attempt as a
     * conflict.
     */
    public fun copyOwnFiles(from: Path, to: Path, device: String): Move.Result {
        val ready = when (val check = checkMove(from, to, device)) {
            is Move.Ready -> check
            Move.SameFolder -> return Move.SameFolder
            is Move.Unusable -> return check
            is Move.Conflict -> return check
        }
        if (!prepare(to)) return Move.Unusable(FolderStatus.NotWritable)
        for (name in ready.files) {
            val target = to.resolve(name)
            val temp = target.resolveSibling(".${target.fileName}.copying")
            try {
                target.parent?.let { Files.createDirectories(it) }
                Files.copy(from.resolve(name), temp, StandardCopyOption.REPLACE_EXISTING)
                Files.move(temp, target)
            } catch (_: FileAlreadyExistsException) {
                runCatching { Files.deleteIfExists(temp) }
                // Something else put a file there since the check. Identical is
                // fine; anything else is exactly the conflict the check exists for.
                if (differs(from.resolve(name), target)) return Move.Conflict(name)
            } catch (_: IOException) {
                runCatching { Files.deleteIfExists(temp) }
                return Move.Failed(name)
            }
        }
        return Move.Copied(ready.files)
    }

    private fun differs(source: Path, target: Path): Boolean =
        Files.exists(target) && runCatching { Files.mismatch(source, target) != -1L }.getOrDefault(true)

    private fun sameFolder(a: Path, b: Path): Boolean {
        val left = a.toAbsolutePath().normalize()
        val right = b.toAbsolutePath().normalize()
        if (left == right) return true
        return Files.exists(left) && Files.exists(right) && runCatching { Files.isSameFile(left, right) }.getOrDefault(false)
    }
}
