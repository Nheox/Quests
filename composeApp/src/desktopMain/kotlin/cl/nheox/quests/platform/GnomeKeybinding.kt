package cl.nheox.quests.platform

import java.util.concurrent.TimeUnit

/**
 * Installs the global quick-capture shortcut as a GNOME custom keybinding.
 *
 * This is the plan of record from §8.3, and it is worth restating why the app
 * does not simply grab a key itself:
 *
 *  1. mutter on GNOME ≥ 49 no longer honours XWayland global key grabs, so a
 *     grabbed key only fires while Quests already has focus — useless.
 *  2. The replacement, the XDG GlobalShortcuts portal, expects a native Wayland
 *     client and a reverse-DNS app ID backed by an installed `.desktop` file.
 *  3. OpenJDK ships no Wayland backend, so Compose Desktop is always an XWayland
 *     client, which puts it on the wrong side of both of the above.
 *  4. `xdg-desktop-portal-wlr` has no GlobalShortcuts backend at all, so Sway,
 *     Hyprland and Niri users could not have one by any route.
 *
 * So the compositor owns the shortcut and Quests owns the command. The binding
 * runs `quests --capture`, which [SingleInstance] forwards to the already-running
 * process. No portal, no key grab, no Wayland-client requirement; works the same
 * on X11, Wayland, GNOME and KDE.
 *
 * Writing it via `gsettings` is a convenience, not the mechanism. When it fails —
 * most likely inside a Flatpak sandbox, where `gsettings` cannot reach the host's
 * dconf — the user sets the same binding by hand, and the result is identical.
 */
public object GnomeKeybinding {
    public const val DEFAULT_BINDING: String = "<Super><Shift>q"
    public const val COMMAND: String = "quests --capture"

    private const val SCHEMA = "org.gnome.settings-daemon.plugins.media-keys"
    private const val CUSTOM_SCHEMA = "org.gnome.settings-daemon.plugins.media-keys.custom-keybinding"
    private const val SLOT = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/quests/"

    public fun isGnome(): Boolean {
        val desktop = (System.getenv("XDG_CURRENT_DESKTOP") ?: System.getenv("DESKTOP_SESSION")).orEmpty()
        return desktop.contains("GNOME", ignoreCase = true) || desktop.contains("ubuntu", ignoreCase = true)
    }

    public fun isAvailable(): Boolean = runCatching {
        run("gsettings", "--version") != null
    }.getOrDefault(false)

    /**
     * Adds the binding, leaving any other custom keybindings alone.
     *
     * The read-modify-write of the list is the fiddly part: clobbering it would
     * silently delete the user's other shortcuts, which is not an acceptable
     * price for installing ours.
     */
    public fun install(
        binding: String = DEFAULT_BINDING,
        command: String = COMMAND,
        name: String = "Quests quick capture",
    ): Boolean {
        if (!isAvailable()) return false

        val existing = run("gsettings", "get", SCHEMA, "custom-keybindings") ?: return false
        val slots = parseList(existing)
        val updated = if (SLOT in slots) slots else slots + SLOT

        val wroteList = run(
            "gsettings", "set", SCHEMA, "custom-keybindings",
            updated.joinToString(prefix = "[", postfix = "]") { "'$it'" },
        ) != null
        if (!wroteList) return false

        val target = "$CUSTOM_SCHEMA:$SLOT"
        val wroteName = run("gsettings", "set", target, "name", name) != null
        val wroteCommand = run("gsettings", "set", target, "command", command) != null
        val wroteBinding = run("gsettings", "set", target, "binding", binding) != null
        return wroteName && wroteCommand && wroteBinding
    }

    public fun isInstalled(): Boolean {
        val existing = run("gsettings", "get", SCHEMA, "custom-keybindings") ?: return false
        return SLOT in parseList(existing)
    }

    public fun uninstall(): Boolean {
        val existing = run("gsettings", "get", SCHEMA, "custom-keybindings") ?: return false
        val remaining = parseList(existing).filterNot { it == SLOT }
        return run(
            "gsettings", "set", SCHEMA, "custom-keybindings",
            if (remaining.isEmpty()) "[]" else remaining.joinToString(prefix = "[", postfix = "]") { "'$it'" },
        ) != null
    }

    /** `['/a/', '/b/']` or `@as []` — gsettings prints both forms. */
    internal fun parseList(raw: String): List<String> {
        val trimmed = raw.trim().removePrefix("@as").trim()
        if (!trimmed.startsWith("[")) return emptyList()
        return trimmed.removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotBlank() }
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        // A hung gsettings must not hang the settings screen with it.
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() == 0) output else null
    }.getOrNull()
}
