package cl.nheox.quests.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finishing a data-folder change at launch. Whatever goes wrong, the launch
 * opens a folder that holds this device's history, and the config points at
 * the new folder only once the copy is there.
 */
class FolderSwitchTest {

    private val root: Path = createTempDirectory("quests-switch-test")
    private val old: Path = root.resolve("old")
    private val new: Path = root.resolve("new")
    private val remembered = mutableListOf<Path>()

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun write(folder: Path, name: String, content: String) {
        val file = folder.resolve(name)
        Files.createDirectories(file.parent)
        file.writeText(content)
    }

    private fun finish(stored: Path? = old, remember: Boolean = true) =
        FolderSwitch.finish(stored, new, device = { "mac" }, remember = { remembered.add(it); remember })

    @Test
    fun `a switch copies this device's files and then repoints the config`() {
        write(old, "events-mac.jsonl", "mac\n")
        write(old, "events-ubuntu.jsonl", "ubuntu\n")

        val (folder, outcome) = finish()

        assertEquals(new, folder)
        assertEquals(SwitchOutcome.Switched(new), outcome)
        assertEquals(listOf(new), remembered)
        assertEquals("mac\n", new.resolve("events-mac.jsonl").readText())
        // Another device's log is that device's to move.
        assertFalse(Files.exists(new.resolve("events-ubuntu.jsonl")))
        // Copied, never moved: the old folder is left as it was.
        assertEquals("mac\n", old.resolve("events-mac.jsonl").readText())
    }

    @Test
    fun `a config that cannot be written keeps the old folder`() {
        write(old, "events-mac.jsonl", "mac\n")

        val (folder, outcome) = finish(remember = false)

        assertEquals(old, folder)
        assertEquals(SwitchOutcome.Failed(new), outcome)
    }

    @Test
    fun `a missing old folder is a failed switch, not an empty new one`() {
        val (folder, outcome) = finish()

        assertEquals(old, folder)
        assertEquals(SwitchOutcome.Failed(new), outcome)
        assertTrue(remembered.isEmpty())
        assertFalse(Files.exists(new))
    }

    @Test
    fun `a conflicting file in the new folder stops the switch and is named`() {
        write(old, "events-mac.jsonl", "mac\n")
        write(new, "events-mac.jsonl", "something else\n")

        val (folder, outcome) = finish()

        assertEquals(old, folder)
        assertEquals(SwitchOutcome.Conflict("events-mac.jsonl"), outcome)
        assertTrue(remembered.isEmpty())
        assertEquals("something else\n", new.resolve("events-mac.jsonl").readText())
    }

    @Test
    fun `switching to the folder already in use changes nothing`() {
        write(old, "events-mac.jsonl", "mac\n")

        val (folder, outcome) = FolderSwitch.finish(old, old, device = { "mac" }, remember = { remembered.add(it); true })

        assertEquals(old, folder)
        assertEquals(SwitchOutcome.Unchanged, outcome)
        assertTrue(remembered.isEmpty())
    }

    @Test
    fun `with no folder stored there is nothing to carry and first run asks`() {
        var asked = false
        val (folder, outcome) = FolderSwitch.finish(null, new, device = { asked = true; "mac" }, remember = { true })

        assertNull(folder)
        assertNull(outcome)
        assertFalse(asked)
    }
}
