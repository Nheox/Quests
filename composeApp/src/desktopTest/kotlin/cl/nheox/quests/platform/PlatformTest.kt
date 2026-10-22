package cl.nheox.quests.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

/**
 * The desktop-only plumbing.
 *
 * The single-instance socket is tested hard because it is the whole mechanism
 * behind global quick capture, and because it has already produced one crash
 * (a path over the `sun_path` limit) that took the entire application down.
 */
class SingleInstanceTest {

    private val runtimeDir: Path = createTempDirectory("quests-instance-test")

    @AfterTest
    fun cleanUp() {
        runtimeDir.toFile().deleteRecursively()
    }

    @Test
    fun `the first instance claims and a second one does not`() {
        val first = SingleInstance(runtimeDir)
        val second = SingleInstance(runtimeDir)
        try {
            assertTrue(first.claim())
            assertFalse(second.claim(), "a second instance must not also become primary")
        } finally {
            first.release()
            second.release()
        }
    }

    @Test
    fun `releasing lets a later instance claim`() {
        val first = SingleInstance(runtimeDir)
        assertTrue(first.claim())
        first.release()

        val later = SingleInstance(runtimeDir)
        try {
            assertTrue(later.claim(), "a released lock must not lock the user out of their own app")
        } finally {
            later.release()
        }
    }

    @Test
    fun `a command reaches the listening instance`() {
        val primary = SingleInstance(runtimeDir)
        assertTrue(primary.claim())
        val received = java.util.concurrent.LinkedBlockingQueue<SingleInstance.Command>()
        try {
            assertTrue(primary.listen { received.put(it) })

            val secondary = SingleInstance(runtimeDir)
            assertTrue(secondary.send(SingleInstance.Command.CAPTURE))

            val delivered = received.poll(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(SingleInstance.Command.CAPTURE, delivered)
        } finally {
            primary.release()
        }
    }

    @Test
    fun `sending with nobody listening fails quietly`() {
        val lonely = SingleInstance(runtimeDir)
        assertFalse(lonely.send(SingleInstance.Command.SHOW))
    }

    @Test
    fun `a socket left behind by a crash does not prevent listening`() {
        val socket = SingleInstance.socketPath(runtimeDir)
        Files.createDirectories(socket.parent)
        Files.write(socket, "stale".toByteArray(StandardCharsets.UTF_8))

        val primary = SingleInstance(runtimeDir)
        try {
            assertTrue(primary.claim())
            assertTrue(primary.listen { }, "a stale socket file must be replaced, not fatal")
        } finally {
            primary.release()
        }
    }

    @Test
    fun `an over-long runtime path falls back to a short one`() {
        // The bug this exists for: a Unix domain socket path is capped at about
        // 108 bytes, and binding over it threw, which took the window down with
        // it. Inter-process capture is a convenience; the app is not.
        val deep = runtimeDir.resolve("a".repeat(60)).resolve("b".repeat(60)).resolve("quests")
        val chosen = SingleInstance.socketPath(deep)
        assertTrue(
            chosen.toAbsolutePath().toString().toByteArray(StandardCharsets.UTF_8).size
                <= SingleInstance.MAX_SOCKET_PATH,
            "chose an unbindable path: $chosen",
        )
    }

    @Test
    fun `a short runtime path is used as given`() {
        val chosen = SingleInstance.socketPath(Path.of("/run/user/1000/quests"))
        assertEquals("/run/user/1000/quests/capture.sock", chosen.toString())
    }

    @Test
    fun `both ends agree on the path`() {
        assertEquals(SingleInstance.socketPath(runtimeDir), SingleInstance.socketPath(runtimeDir))
    }

    @Test
    fun `commands round-trip through their wire form`() {
        SingleInstance.Command.entries.forEach { command ->
            assertEquals(command, SingleInstance.Command.parse(command.wire))
            assertEquals(command, SingleInstance.Command.parse(command.wire + "\n"))
        }
        assertEquals(null, SingleInstance.Command.parse("nonsense"))
    }
}

class GnomeKeybindingTest {

    @Test
    fun `gsettings list output is parsed in both of its forms`() {
        assertEquals(
            listOf("/a/", "/b/"),
            GnomeKeybinding.parseList("['/a/', '/b/']"),
        )
        assertEquals(emptyList(), GnomeKeybinding.parseList("@as []"))
        assertEquals(emptyList(), GnomeKeybinding.parseList("[]"))
        assertEquals(listOf("/only/"), GnomeKeybinding.parseList("['/only/']"))
        // Anything unexpected must read as "no existing bindings" rather than
        // being mis-parsed into something that then clobbers the user's list.
        assertEquals(emptyList(), GnomeKeybinding.parseList("something else"))
    }

    @Test
    fun `the command the binding runs is the documented one`() {
        // §8.3: the compositor owns the shortcut and runs this; the app does not
        // grab a key itself.
        assertEquals("quests --capture", GnomeKeybinding.COMMAND)
    }
}

class AppPathsTest {

    @Test
    fun `config and runtime directories are kept apart`() {
        // A stale lock in the config directory would survive a reboot and lock
        // the user out; that is what XDG_RUNTIME_DIR exists to prevent.
        assertTrue(AppPaths.configDir() != AppPaths.runtimeDir())
        assertTrue(AppPaths.configDir().toString().endsWith(AppPaths.APP_DIR_NAME))
        assertTrue(AppPaths.runtimeDir().toString().endsWith(AppPaths.APP_DIR_NAME))
    }

    @Test
    fun `the device file and the vault pointer live in the config directory`() {
        assertEquals(AppPaths.configDir(), AppPaths.deviceFile().parent)
        assertEquals(AppPaths.configDir(), AppPaths.vaultPointerFile().parent)
    }

    @Test
    fun `the default vault is a plainly named folder in the home directory`() {
        assertEquals("Quests", AppPaths.defaultVault().fileName.toString())
    }

    @Test
    fun `the pending folder lives beside the pointer, in the config directory`() {
        // Never in the data folder: which folder this machine uses is this
        // machine's business, and must not sync.
        assertEquals(AppPaths.configDir(), AppPaths.pendingVaultFile().parent)
        assertTrue(AppPaths.pendingVaultFile() != AppPaths.vaultPointerFile())
    }

    @Test
    fun `paths under home are shown with a tilde`() {
        data class Case(val path: String, val expected: String)

        val home = Path.of("/home/ana")
        listOf(
            Case("/home/ana/Quests", "~/Quests"),
            Case("/home/ana/Dropbox/Shared Quests", "~/Dropbox/Shared Quests"),
            Case("/home/ana", "~"),
            Case("/home/ana/./Quests/../Quests", "~/Quests"),
            // A sibling that merely starts with the same letters is not home.
            Case("/home/anabel/Quests", "/home/anabel/Quests"),
            Case("/Volumes/Backup/Quests", "/Volumes/Backup/Quests"),
        ).forEach { case -> assertEquals(case.expected, AppPaths.displayPath(Path.of(case.path), home), case.path) }
    }
}
