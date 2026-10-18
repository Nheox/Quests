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
 *    "which computer am I".
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

    public fun defaultVault(): Path = home.resolve(Vault.DEFAULT_FOLDER_NAME)

    /** The folder the user picked, or `~/Quests` until they pick one. */
    public fun resolveVault(): Path {
        val pointer = vaultPointerFile()
        if (Files.isRegularFile(pointer)) {
            val stored = runCatching { Files.readString(pointer).trim() }.getOrNull()
            if (!stored.isNullOrBlank()) {
                val path = runCatching { Paths.get(stored) }.getOrNull()
                if (path != null) return path
            }
        }
        return defaultVault()
    }

    public fun rememberVault(path: Path) {
        runCatching {
            Files.createDirectories(configDir())
            Files.writeString(vaultPointerFile(), path.toAbsolutePath().toString())
        }
    }

    public fun hostname(): String? = runCatching {
        System.getenv("HOSTNAME")
            ?: System.getenv("COMPUTERNAME")
            ?: java.net.InetAddress.getLocalHost().hostName
    }.getOrNull()
}
