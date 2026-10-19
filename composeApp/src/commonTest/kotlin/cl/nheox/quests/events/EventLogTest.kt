package cl.nheox.quests.events

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.RepeatUnit
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.Urgency
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The on-disk format.
 *
 * Two promises are tested here, both of which the user is entitled to rely on:
 * the log is readable and editable plain text, and a damaged line costs one
 * line rather than the whole history.
 */
class EventLogTest {

    private val root: Path = createTempDirectory("quests-log-test")
    private val device = DeviceIdentity("macbook")
    private val vault = Vault(root)
    private val log = FileEventLog(vault, device)

    private val now: Instant = Instant.parse("2026-10-15T09:00:00Z")

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun quest(id: String, counter: Int) = Event.QuestCreated(
        ts = now,
        hlc = Hlc(now.toEpochMilli(), counter, device.id).encode(),
        device = device.id,
        id = id,
        title = "Quest $id",
        difficulty = Difficulty.HIGH,
        fear = Fear.STRONG,
        urgency = Urgency.CRITICAL,
        estimatedMinutes = 120,
        dueDate = LocalDate.of(2026, 10, 20),
        dueTime = LocalTime.of(15, 30),
        repetition = Repetition(RepeatUnit.WEEK, 2),
        skillIds = setOf("s_thesis", "s_lab"),
    )

    @Test
    fun `an event survives a round trip through the file`() {
        val original = quest("q1", 1)
        log.append(listOf(original))

        val loaded = log.load()
        assertEquals(1, loaded.events.size)
        assertEquals(original, loaded.events.single())
        assertTrue(loaded.skipped.isEmpty())
    }

    @Test
    fun `the file is one json object per line with a type discriminator`() {
        log.append(listOf(quest("q1", 1), quest("q2", 2)))
        val text = vault.logFile(device.id).readText()
        val lines = text.trim().lines()

        assertEquals(2, lines.size)
        lines.forEach { line ->
            assertTrue(line.startsWith("{") && line.endsWith("}"), "not a bare object: $line")
            assertTrue(line.contains("\"type\":\"QuestCreated\""), "missing discriminator: $line")
            assertTrue(line.contains("\"hlc\":"), "missing clock: $line")
            assertTrue(line.contains("\"device\":\"macbook\""), "missing device: $line")
        }
        assertTrue(text.endsWith("\n"), "the log must end with a newline so appends stay line-aligned")
    }

    @Test
    fun `appending never rewrites what is already there`() {
        log.append(listOf(quest("q1", 1)))
        val afterFirst = vault.logFile(device.id).readText()
        log.append(listOf(quest("q2", 2)))
        val afterSecond = vault.logFile(device.id).readText()

        assertTrue(afterSecond.startsWith(afterFirst), "an append must be append-only")
    }

    @Test
    fun `defaults are omitted to keep lines readable`() {
        val minimal = Event.QuestCreated(
            ts = now,
            hlc = Hlc(now.toEpochMilli(), 1, device.id).encode(),
            device = device.id,
            id = "q1",
            title = "Simple",
        )
        val line = EventCodec.encode(minimal)
        assertTrue("notes" !in line, "default notes should not be written: $line")
        assertTrue("parentId" !in line, "absent optionals should not be written: $line")
        assertTrue("\"title\":\"Simple\"" in line)
    }

    @Test
    fun `a damaged line costs one line, not the history`() {
        val good = quest("q1", 1)
        val alsoGood = quest("q2", 2)
        vault.prepare()
        vault.logFile(device.id).writeText(
            buildString {
                appendLine(EventCodec.encode(good))
                appendLine("{ this is not json")
                appendLine("")
                appendLine("""{"type":"NoSuchEvent","ts":"2026-10-15T09:00:00Z","hlc":"x","device":"d"}""")
                appendLine(EventCodec.encode(alsoGood))
            },
        )

        val loaded = log.load()
        assertEquals(listOf("q1", "q2"), loaded.events.filterIsInstance<Event.QuestCreated>().map { it.id })
        assertEquals(2, loaded.skipped.size)
        assertTrue(loaded.skipped.all { it.file == "events-macbook.jsonl" })
    }

    @Test
    fun `an event with an unreadable clock is skipped rather than reordering history`() {
        vault.prepare()
        vault.logFile(device.id).writeText(
            """{"type":"QuestCreated","ts":"2026-10-15T09:00:00Z","hlc":"not-a-clock","device":"macbook","id":"q1","title":"x"}""" + "\n",
        )
        val loaded = log.load()
        assertTrue(loaded.events.isEmpty())
        assertEquals("bad clock", loaded.skipped.single().reason)
    }

    @Test
    fun `unknown fields are ignored so a newer version's log still reads`() {
        vault.prepare()
        vault.logFile(device.id).writeText(
            """{"type":"QuestCreated","ts":"2026-10-15T09:00:00Z","hlc":"0001760000000000:00001:macbook","device":"macbook","id":"q1","title":"x","somethingFromTheFuture":42}""" + "\n",
        )
        val loaded = log.load()
        assertEquals(1, loaded.events.size)
        assertTrue(loaded.skipped.isEmpty())
    }

    @Test
    fun `every log in the folder is read, not just this device's`() {
        log.append(listOf(quest("mine", 1)))
        val theirs = Event.QuestCreated(
            ts = now,
            hlc = Hlc(now.toEpochMilli(), 1, "ubuntu").encode(),
            device = "ubuntu",
            id = "theirs",
            title = "From the other machine",
        )
        vault.logFile("ubuntu").writeText(EventCodec.encode(theirs) + "\n")

        val loaded = log.load()
        assertEquals(setOf("mine", "theirs"), loaded.events.filterIsInstance<Event.QuestCreated>().map { it.id }.toSet())
        assertEquals(2, loaded.filesRead.count { it.startsWith("events-") })
    }

    @Test
    fun `only this device's own log is ever written`() {
        // The load-bearing promise of the sync design: no sync tool ever sees a
        // concurrent write, because no file has two writers.
        vault.logFile("ubuntu").writeText("")
        val theirsBefore = vault.logFile("ubuntu").readText()
        log.append(listOf(quest("q1", 1)))
        assertEquals(theirsBefore, vault.logFile("ubuntu").readText())
    }

    @Test
    fun `files that are not logs are left alone`() {
        vault.prepare()
        root.resolve("README.md").writeText("# my notes")
        root.resolve("events-.jsonl").writeText("not a device\n")
        log.append(listOf(quest("q1", 1)))

        val loaded = log.load()
        assertEquals(1, loaded.events.size)
        assertTrue(loaded.filesRead.none { it == "README.md" })
    }

    @Test
    fun `loading an absent folder is empty rather than an error`() {
        val missing = Vault(root.resolve("nope"))
        assertTrue(FileEventLog(missing, device).load().isEmpty)
    }

    @Test
    fun `the same event appearing twice is counted once`() {
        val event = quest("q1", 1)
        vault.prepare()
        vault.logFile(device.id).writeText(EventCodec.encode(event) + "\n" + EventCodec.encode(event) + "\n")
        assertEquals(1, log.load().events.size)
    }

    @Test
    fun `device names are sanitised but stay recognisable`() {
        assertEquals("macbook-pro", DeviceIdentity.sanitise("MacBook-Pro.local"))
        assertEquals("ubuntu", DeviceIdentity.sanitise("ubuntu"))
        assertEquals("my-desktop", DeviceIdentity.sanitise("my_desktop"))
        assertEquals("device", DeviceIdentity.sanitise(""))
        assertEquals("device", DeviceIdentity.sanitise("???"))
        assertEquals("a-b", DeviceIdentity.sanitise("a///b"))
        assertTrue(DeviceIdentity.sanitise("x".repeat(80)).length <= DeviceIdentity.MAX_LENGTH)
    }

    @Test
    fun `a stored device name is reused on the next launch`() {
        val configFile = root.resolve("config").resolve("device")
        val first = DeviceIdentity.loadOrCreate(configFile, "localhost")
        val second = DeviceIdentity.loadOrCreate(configFile, "something-else")
        assertEquals(first, second)
        assertNotNull(Files.readString(configFile))
    }

    @Test
    fun `a useless hostname gets a suffix instead of colliding`() {
        val identity = DeviceIdentity.loadOrCreate(root.resolve("c1").resolve("device"), "localhost")
        assertTrue(identity.id.startsWith("device-"), "was ${identity.id}")
    }

    @Test
    fun `the device name maps to and from its log file name`() {
        assertEquals("macbook", DeviceIdentity.fromLogFileName("events-macbook.jsonl"))
        assertEquals(null, DeviceIdentity.fromLogFileName("events-.jsonl"))
        assertEquals(null, DeviceIdentity.fromLogFileName("quests.md"))
        assertEquals("events-macbook.jsonl", DeviceIdentity("macbook").logFileName)
    }
}
