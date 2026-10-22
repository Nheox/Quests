package cl.nheox.quests.platform

import cl.nheox.quests.events.Vault
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where things live on disk.
 *
 * Two separate concerns, deliberately kept apart:
 *
 *  - The **vault** is the user's, lives wherever they chose, and is the thing
 *    they sync. It holds nothing machine-specific.
 *  - The **config** directory is this machine's, lives in the usual XDG place,
 *    and holds exactly two things: which folder the vault is, and what this
 *    device is called. Neither may ever be synced, because both are answers to
 *    "which computer am I". (A third file, the folder to switch to, exists
 *    only between choosing a new folder in Settings and the next launch.)
 */
public object AppPaths {
    public const val APP_DIR_NAME: String = "quests"

    private val home: Path get() = Paths.get(System.getProperty("user.home"))

    private val isMac: Boolean get() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    /** `$XDG_CONFIG_HOME/quests`, or the macOS equivalent. */
    public fun configDir(): Path {
        val explicit = System.getenv("XDG_CONFIG_HOME")
        val base = when {
            !explicit.isNullOrBlank() -> Paths.get(explicit)
            isMac -> home.resolve("Library").resolve("Application Support")
            else -> home.resolve(".config")
        }
        return base.resolve(APP_DIR_NAME)
    }

    /**
     * `$XDG_RUNTIME_DIR/quests` for the lock and the socket.
     *
     * Runtime state, not config: a stale lock in `~/.config` would survive a
     * reboot and lock the user out of their own app, which is precisely what
     * `XDG_RUNTIME_DIR` exists to prevent.
     */
    public fun runtimeDir(): Path {
        val explicit = System.getenv("XDG_RUNTIME_DIR")
        val base = if (!explicit.isNullOrBlank()) {
            Paths.get(explicit)
        } else {
            Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve("quests-" + (System.getProperty("user.name") ?: "user"))
        }
        return base.resolve(APP_DIR_NAME)
    }

    public fun deviceFile(): Path = configDir().resolve("device")

    public fun vaultPointerFile(): Path = configDir().resolve("vault")

    public fun pendingVaultFile(): Path = configDir().resolve("vault.next")

    /** Offered on first run; never used without the user saying so. */
    public fun defaultVault(): Path = home.resolve(Vault.DEFAULT_FOLDER_NAME)

    /**
     * The folder the user picked, or null if they never have.
     *
     * There is deliberately no fallback to [defaultVault] here. A missing
     * pointer means first run, and first run asks (§1.2): quietly creating
     * `~/Quests` is how a user ends up with their quests in a folder they never
     * chose and nothing syncing it.
     */
    public fun storedVault(): Path? = readPointer(vaultPointerFile())

    /** Returns false if the choice could not be written down. */
    public fun rememberVault(path: Path): Boolean = writePointer(vaultPointerFile(), path)

    /**
     * The folder chosen in Settings, which the next launch switches to.
     *
     * The switch waits for a launch because the copy has to wait for one:
     * anything this session writes after the copy would otherwise stay behind
     * in the old folder, missing from the new one.
     */
    public fun pendingVault(): Path? = readPointer(pendingVaultFile())

    public fun rememberPendingVault(path: Path): Boolean = writePointer(pendingVaultFile(), path)

    public fun clearPendingVault() {
        runCatching { Files.deleteIfExists(pendingVaultFile()) }
    }

    /** A path as a person reads it: `~/Quests` rather than `/home/ana/Quests`. */
    public fun display(path: Path): String = displayPath(path, home)

    internal fun displayPath(path: Path, home: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        val base = home.toAbsolutePath().normalize()
        return when {
            absolute == base -> "~"
            absolute.startsWith(base) -> "~/" + base.relativize(absolute).joinToString("/")
            else -> absolute.toString()
        }
    }

    private fun readPointer(file: Path): Path? {
        if (!Files.isRegularFile(file)) return null
        val stored = runCatching { Files.readString(file).trim() }.getOrNull()
        if (stored.isNullOrBlank()) return null
        return runCatching { Paths.get(stored) }.getOrNull()
    }

    private fun writePointer(file: Path, path: Path): Boolean = runCatching {
        Files.createDirectories(configDir())
        Files.writeString(file, path.toAbsolutePath().normalize().toString())
        true
    }.getOrDefault(false)

    public fun hostname(): String? = runCatching {
        System.getenv("HOSTNAME")
            ?: System.getenv("COMPUTERNAME")
            ?: java.net.InetAddress.getLocalHost().hostName
    }.getOrNull()
}
