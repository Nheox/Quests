package cl.nheox.quests.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The data folder, before it is opened and when it changes.
 *
 * The copy is tested hardest, because it is the one place the app writes into
 * a folder that may already hold someone's history: it must never overwrite a
 * file it did not put there, and never touch the folder it is leaving.
 */
class DataFolderTest {

    private val root: Path = createTempDirectory("quests-folder-test")
    private val from: Path = root.resolve("old")
    private val to: Path = root.resolve("new")

    @AfterTest
    fun cleanUp() {
        // Put write permission back first, or the cleanup itself is refused.
        root.toFile().walkTopDown().forEach { it.setWritable(true) }
        root.toFile().deleteRecursively()
    }

    private fun write(folder: Path, name: String, content: String): Path {
        val file = folder.resolve(name)
        Files.createDirectories(file.parent)
        file.writeText(content)
        return file
    }

    /** The old folder of a device called `mac`, beside another device's files. */
    private fun oldFolder() {
        write(from, "events-mac.jsonl", "mac line 1\nmac line 2\n")
        write(from, "events-ubuntu.jsonl", "ubuntu line\n")
        write(from, "snapshots/mac-00001.json", "{\"mac\":1}")
        write(from, "snapshots/mac-00002.json", "{\"mac\":2}")
        write(from, "snapshots/mac-mini-00001.json", "{\"mini\":1}")
        write(from, "snapshots/ubuntu-00001.json", "{\"ubuntu\":1}")
        write(from, "export/quests.md", "# generated")
    }

    private fun listing(folder: Path): Map<String, String> = folder.toFile().walkTopDown()
        .filter { it.isFile }
        .associate { folder.relativize(it.toPath()).toString() to it.readText() }

    @Test
    fun `a folder is described by what is in it`() {
        data class Case(val name: String, val setUp: (Path) -> Unit, val expected: FolderStatus)

        val cases = listOf(
            Case("not there, parent writable", {}, FolderStatus.Missing),
            Case("not there, several levels deep", { }, FolderStatus.Missing),
            Case("empty", { Files.createDirectories(it) }, FolderStatus.Empty),
            Case("one device's log", { write(it, "events-mac.jsonl", "x") }, FolderStatus.HasQuests(1)),
            Case(
                "two devices' logs",
                { write(it, "events-mac.jsonl", "x"); write(it, "events-ubuntu.jsonl", "y") },
                FolderStatus.HasQuests(2),
            ),
            Case("other files only", { write(it, "notes.txt", "hello") }, FolderStatus.HasOtherFiles),
            Case(
                "snapshots but no log is not mistaken for quests",
                { write(it, "snapshots/mac-00001.json", "{}") },
                FolderStatus.HasOtherFiles,
            ),
            Case("a file where the folder should be", { write(it.parent, it.fileName.toString(), "x") }, FolderStatus.NotAFolder),
        )

        cases.forEachIndexed { index, case ->
            val folder = if (case.name.contains("several")) {
                root.resolve("case-$index").resolve("a").resolve("b")
            } else {
                root.resolve("case-$index")
            }
            case.setUp(folder)
            assertEquals(case.expected, DataFolder.inspect(folder), case.name)
        }
    }

    @Test
    fun `a folder whose parent is a file cannot be created`() {
        val file = write(root, "plain.txt", "x")
        assertEquals(FolderStatus.NotAFolder, DataFolder.inspect(file.resolve("Quests")))
    }

    @Test
    fun `a read-only folder is not usable`() {
        val locked = root.resolve("locked")
        Files.createDirectories(locked)
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"))
        // Root can write anywhere, so the permission means nothing to a
        // container running as root; the case only holds where it can.
        if (Files.isWritable(locked)) return
        assertEquals(FolderStatus.NotWritable, DataFolder.inspect(locked))
        assertEquals(FolderStatus.NotWritable, DataFolder.inspect(locked.resolve("Quests")))
        assertFalse(DataFolder.prepare(locked.resolve("Quests")))
    }

    @Test
    fun `usable is everything that can hold the data`() {
        val usable = listOf(
            FolderStatus.Missing,
            FolderStatus.Empty,
            FolderStatus.HasQuests(3),
            FolderStatus.HasOtherFiles,
        )
        usable.forEach { assertTrue(it.usable, "$it") }
        listOf(FolderStatus.NotAFolder, FolderStatus.NotWritable).forEach { assertFalse(it.usable, "$it") }
    }

    @Test
    fun `preparing creates the folder and leaves nothing behind`() {
        val deep = root.resolve("a").resolve("b").resolve("Quests")
        assertTrue(DataFolder.prepare(deep))
        assertTrue(Files.isDirectory(deep))
        assertEquals(emptyMap(), listing(deep), "the write probe must be removed")
        assertFalse(DataFolder.prepare(write(root, "file", "x")), "a file is not a folder")
    }

    @Test
    fun `the nearest existing folder is found for a path that is not there yet`() {
        data class Case(val path: Path, val expected: Path)
        listOf(
            Case(root.resolve("x").resolve("y"), root),
            Case(root, root),
            Case(root.resolve("x").resolve("..").resolve("z"), root),
        ).forEach { case -> assertEquals(case.expected, DataFolder.nearestExisting(case.path), "${case.path}") }
    }

    @Test
    fun `only this device's own log and snapshots are copied`() {
        oldFolder()

        val result = DataFolder.copyOwnFiles(from, to, "mac")

        assertEquals(
            Move.Copied(listOf("events-mac.jsonl", "snapshots/mac-00001.json", "snapshots/mac-00002.json")),
            result,
        )
        assertEquals(
            mapOf(
                "events-mac.jsonl" to "mac line 1\nmac line 2\n",
                "snapshots/mac-00001.json" to "{\"mac\":1}",
                "snapshots/mac-00002.json" to "{\"mac\":2}",
            ),
            listing(to),
            "another device's log, a longer device name and the generated export all stay behind",
        )
    }

    @Test
    fun `the old folder is never touched`() {
        oldFolder()
        val before = listing(from)
        DataFolder.copyOwnFiles(from, to, "mac")
        assertEquals(before, listing(from))
    }

    @Test
    fun `a different file of the same name stops the copy before anything is written`() {
        oldFolder()
        write(to, "snapshots/mac-00002.json", "{\"someone else\":true}")
        write(to, "events-ubuntu.jsonl", "ubuntu's own, newer\n")
        val before = listing(to)

        assertEquals(Move.Conflict("snapshots/mac-00002.json"), DataFolder.checkMove(from, to, "mac"))
        assertEquals(Move.Conflict("snapshots/mac-00002.json"), DataFolder.copyOwnFiles(from, to, "mac"))
        assertEquals(before, listing(to), "a conflict must leave the destination exactly as it was")
        assertEquals("{\"someone else\":true}", to.resolve("snapshots/mac-00002.json").readText())
    }

    @Test
    fun `an identical file already there is left alone, so a retry is harmless`() {
        oldFolder()
        write(to, "events-mac.jsonl", "mac line 1\nmac line 2\n")

        assertEquals(
            Move.Ready(listOf("snapshots/mac-00001.json", "snapshots/mac-00002.json")),
            DataFolder.checkMove(from, to, "mac"),
        )
        assertEquals(
            Move.Copied(listOf("snapshots/mac-00001.json", "snapshots/mac-00002.json")),
            DataFolder.copyOwnFiles(from, to, "mac"),
        )
        assertEquals(Move.Copied(emptyList()), DataFolder.copyOwnFiles(from, to, "mac"), "second run")
        assertFalse(listing(to).keys.any { it.contains(".copying") }, "no temporary file is left")
    }

    @Test
    fun `a device that never wrote anything moves nothing and still succeeds`() {
        Files.createDirectories(from)
        assertEquals(Move.Copied(emptyList()), DataFolder.copyOwnFiles(from, to, "mac"))
        assertTrue(Files.isDirectory(to))
    }

    @Test
    fun `the folder already in use is reported as such`() {
        oldFolder()
        assertEquals(Move.SameFolder, DataFolder.checkMove(from, from, "mac"))
        assertEquals(Move.SameFolder, DataFolder.checkMove(from, from.resolve("x").resolve(".."), "mac"))
        assertEquals(Move.SameFolder, DataFolder.copyOwnFiles(from, from, "mac"))
    }

    @Test
    fun `a destination that cannot hold the data is refused before copying`() {
        oldFolder()
        val file = write(root, "not-a-folder", "x")
        assertEquals(Move.Unusable(FolderStatus.NotAFolder), DataFolder.copyOwnFiles(from, file, "mac"))
        assertEquals("x", file.readText())
    }
}

class LaunchPlanTest {

    private val default: Path = Paths.get("/home/ana/Quests")
    private val stored: Path = Paths.get("/home/ana/Dropbox/Quests")

    @Test
    fun `launch opens the stored folder only when it can`() {
        data class Case(val stored: Path?, val status: FolderStatus, val expected: LaunchPlan)

        val cases = listOf(
            // First run: nothing chosen yet, so ask, offering ~/Quests.
            Case(null, FolderStatus.Missing, LaunchPlan.Ask(default, previousUnavailable = false)),
            Case(null, FolderStatus.HasQuests(1), LaunchPlan.Ask(default, previousUnavailable = false)),
            Case(stored, FolderStatus.HasQuests(2), LaunchPlan.Open(stored)),
            Case(stored, FolderStatus.Empty, LaunchPlan.Open(stored)),
            Case(stored, FolderStatus.HasOtherFiles, LaunchPlan.Open(stored)),
            // Gone (an unplugged drive, a sync client not mounted yet) is asked
            // about, never quietly recreated empty.
            Case(stored, FolderStatus.Missing, LaunchPlan.Ask(stored, previousUnavailable = true)),
            Case(stored, FolderStatus.NotWritable, LaunchPlan.Ask(stored, previousUnavailable = true)),
            Case(stored, FolderStatus.NotAFolder, LaunchPlan.Ask(stored, previousUnavailable = true)),
        )

        cases.forEach { case ->
            assertEquals(case.expected, LaunchPlan.decide(case.stored, default) { case.status }, "$case")
        }
    }
}

class FolderPickerTest {

    @Test
    fun `each platform tries its native chooser first and Swing last`() {
        data class Case(val os: String, val installed: Set<String>, val expected: List<FolderPicker>)

        val cases = listOf(
            Case("Mac OS X", emptySet(), listOf(FolderPicker.MAC_PANEL, FolderPicker.SWING)),
            // A Mac never shells out, even if someone installed zenity there.
            Case("Mac OS X", setOf("zenity"), listOf(FolderPicker.MAC_PANEL, FolderPicker.SWING)),
            Case("Linux", setOf("zenity", "kdialog"), listOf(FolderPicker.ZENITY, FolderPicker.KDIALOG, FolderPicker.SWING)),
            Case("Linux", setOf("zenity"), listOf(FolderPicker.ZENITY, FolderPicker.SWING)),
            Case("Linux", setOf("kdialog"), listOf(FolderPicker.KDIALOG, FolderPicker.SWING)),
            Case("Linux", emptySet(), listOf(FolderPicker.SWING)),
            Case("Windows 11", emptySet(), listOf(FolderPicker.SWING)),
        )

        cases.forEach { case ->
            assertEquals(case.expected, FolderPicker.candidates(case.os) { it in case.installed }, "$case")
        }
    }

    @Test
    fun `an external picker's answer is read from its exit code`() {
        data class Case(val exit: Int, val output: String, val expected: PickerAttempt)

        val cases = listOf(
            Case(0, "/home/ana/Dropbox/Quests\n", PickerAttempt.Chosen(Paths.get("/home/ana/Dropbox/Quests"))),
            Case(0, "/home/ana/My Quests", PickerAttempt.Chosen(Paths.get("/home/ana/My Quests"))),
            Case(0, "", PickerAttempt.Cancelled),
            // 1 is the user pressing Cancel or closing the dialog: stop there.
            Case(1, "", PickerAttempt.Cancelled),
            // Anything else means the program could not do its job; try the next.
            Case(5, "", PickerAttempt.Unavailable),
            Case(254, "", PickerAttempt.Unavailable),
            Case(-1, "", PickerAttempt.Unavailable),
        )

        cases.forEach { case -> assertEquals(case.expected, FolderPicker.interpret(case.exit, case.output), "$case") }
    }

    @Test
    fun `the external pickers are asked for a folder, starting where the data is`() {
        val start = Paths.get("/home/ana/Quests")
        assertEquals(
            listOf("zenity", "--file-selection", "--directory", "--title=Pick", "--filename=/home/ana/Quests/"),
            FolderPicker.command(FolderPicker.ZENITY, "Pick", start),
        )
        assertEquals(
            listOf("kdialog", "--title", "Pick", "--getexistingdirectory", "/home/ana/Quests"),
            FolderPicker.command(FolderPicker.KDIALOG, "Pick", start),
        )
    }
}
