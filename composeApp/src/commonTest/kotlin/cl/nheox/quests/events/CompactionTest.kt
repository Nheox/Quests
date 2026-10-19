package cl.nheox.quests.events

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.projection.Projector
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path
import java.time.Instant

/**
 * Compaction (§6.5).
 *
 * The governing requirement is stated as a test rather than as a comment:
 * compacting must not change what replay produces. Everything else about
 * compaction is an optimisation, and an optimisation that alters history is a
 * bug with extra steps.
 */
class CompactionTest {

    private val root: Path = createTempDirectory("quests-compaction-test")
    private val device = DeviceIdentity("macbook")
    private val vault = Vault(root)
    private val log = FileEventLog(vault, device)
    private val now: Instant = Instant.parse("2026-10-15T09:00:00Z")

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun clock(counter: Int, node: String = "macbook") =
        Hlc(now.toEpochMilli(), counter, node).encode()

    private fun created(id: String, counter: Int, node: String = "macbook") = Event.QuestCreated(
        ts = now, hlc = clock(counter, node), device = node, id = id, title = "Quest $id",
    )

    private fun changed(id: String, counter: Int, patch: FieldPatch, node: String = "macbook") =
        Event.QuestChanged(now, clock(counter, node), node, id, patch.build())

    @Test
    fun `superseded field edits are dropped`() {
        val events = listOf(
            created("q1", 1),
            changed("q1", 2, FieldPatch().set(QuestFields.TITLE, "first")),
            changed("q1", 3, FieldPatch().set(QuestFields.TITLE, "second")),
            changed("q1", 4, FieldPatch().set(QuestFields.TITLE, "third")),
        )
        val result = Compaction.compact(events)
        assertEquals(2, result.kept.size)
        assertEquals(2, result.droppedEvents)
        assertEquals("third", (result.kept.last() as Event.QuestChanged).fields[QuestFields.TITLE]?.toString()?.trim('"'))
    }

    @Test
    fun `an event is trimmed rather than dropped when only some of its fields are superseded`() {
        val events = listOf(
            created("q1", 1),
            changed(
                "q1", 2,
                FieldPatch()
                    .set(QuestFields.TITLE, "old title")
                    .set(QuestFields.NOTES, "notes that survive"),
            ),
            changed("q1", 3, FieldPatch().set(QuestFields.TITLE, "new title")),
        )
        val result = Compaction.compact(events)
        assertEquals(3, result.kept.size)
        assertEquals(1, result.droppedFields)
        val trimmed = result.kept[1] as Event.QuestChanged
        assertEquals(setOf(QuestFields.NOTES), trimmed.fields.keys)
    }

    @Test
    fun `compaction never changes what replay produces`() {
        val events = busyHistory()
        val before = Projector.replay(events)
        val after = Projector.replay(Compaction.compact(events).kept)

        assertEquals(before.quests, after.quests)
        assertEquals(before.skills, after.skills)
        assertEquals(before.profile, after.profile)
        assertEquals(before.settings, after.settings)
        assertEquals(before.completions.size, after.completions.size)
        assertEquals(before.deletedQuestIds, after.deletedQuestIds)
    }

    @Test
    fun `history that the insight screens depend on is never compacted away`() {
        // The fear log and the trend charts are built from completions and
        // failures. Folding those away would compact away the point of the app.
        val events = busyHistory()
        val kept = Compaction.compact(events).kept
        assertEquals(
            events.count { it is Event.QuestCompleted },
            kept.count { it is Event.QuestCompleted },
        )
        assertEquals(
            events.count { it is Event.QuestFailed },
            kept.count { it is Event.QuestFailed },
        )
        assertEquals(
            events.count { it is Event.RewardClaimed },
            kept.count { it is Event.RewardClaimed },
        )
        assertTrue(kept.any { it is Event.QuestDeleted }, "tombstones must survive")
    }

    @Test
    fun `an edit that lost to another device's later edit still loses after compaction`() {
        // The safety argument in the code, as an executable claim: dropping an
        // edit superseded by the same device cannot change any interleaving.
        val mine = listOf(
            created("q1", 1),
            changed("q1", 2, FieldPatch().set(QuestFields.TITLE, "mine early")),
            changed("q1", 9, FieldPatch().set(QuestFields.TITLE, "mine late")),
        )
        val theirs = listOf(
            changed("q1", 5, FieldPatch().set(QuestFields.TITLE, "theirs"), node = "ubuntu"),
        )

        val full = Projector.replay(mine + theirs)
        val compacted = Projector.replay(Compaction.compact(mine).kept + theirs)
        assertEquals("mine late", full.quests.getValue("q1").title)
        assertEquals(full.quests, compacted.quests)
    }

    @Test
    fun `compaction is idempotent`() {
        val once = Compaction.compact(busyHistory()).kept
        val twice = Compaction.compact(once)
        assertEquals(once, twice.kept)
        assertTrue(!twice.changedAnything)
    }

    @Test
    fun `nothing to compact is reported rather than pretended`() {
        val result = Compaction.compact(listOf(created("q1", 1)))
        assertTrue(!result.changedAnything)
        assertEquals(1, result.kept.size)
    }

    @Test
    fun `running compaction leaves a snapshot and empties the log`() {
        val events = busyHistory().filter { it.device == device.id }
        log.append(events)

        val result = Compaction.run(vault, device, log)
        assertTrue(result != null && result.changedAnything)

        val snapshots = Snapshots.list(vault)
        assertEquals(1, snapshots.size)
        assertTrue(java.nio.file.Files.readString(log.ownLogFile()).isBlank(), "the log should be truncated")

        // And the state is unchanged from the user's point of view.
        val reloaded = FileEventLog(vault, device).load()
        assertEquals(
            Projector.replay(events).quests,
            Projector.replay(reloaded.events).quests,
        )
    }

    @Test
    fun `a snapshot and a not-yet-truncated log do not double count`() {
        val events = busyHistory().filter { it.device == device.id }
        log.append(events)
        // Deliberately writes the snapshot without truncating, which is exactly
        // what a crash between the two steps would leave behind.
        Snapshots.write(
            vault,
            Snapshot(device = device.id, upToHlc = events.last().hlc, events = events),
        )

        val loaded = FileEventLog(vault, device).load()
        assertEquals(events.size, loaded.events.size, "clock is the deduplication key")
    }

    @Test
    fun `snapshot numbering increments per device`() {
        assertEquals(1, Snapshots.nextIndex(vault, "macbook"))
        Snapshots.write(vault, Snapshot(device = "macbook", upToHlc = clock(1), events = emptyList()))
        assertEquals(2, Snapshots.nextIndex(vault, "macbook"))
        // Another device's snapshots do not affect this device's numbering.
        assertEquals(1, Snapshots.nextIndex(vault, "ubuntu"))
    }

    private fun busyHistory(): List<Event> = listOf(
        Event.SkillCreated(now, clock(1), "macbook", "s1", "Thesis"),
        created("q1", 2),
        changed("q1", 3, FieldPatch().set(QuestFields.TITLE, "v1")),
        changed("q1", 4, FieldPatch().setEnum(QuestFields.DIFFICULTY, Difficulty.LOW)),
        changed("q1", 5, FieldPatch().set(QuestFields.TITLE, "v2")),
        changed("q1", 6, FieldPatch().setEnum(QuestFields.DIFFICULTY, Difficulty.EXTREME)),
        changed("q1", 7, FieldPatch().setEnum(QuestFields.URGENCY, Urgency.CRITICAL)),
        created("q2", 8),
        changed("q2", 9, FieldPatch().set(QuestFields.NOTES, "a")),
        changed("q2", 10, FieldPatch().set(QuestFields.NOTES, "b")),
        Event.QuestCompleted(now, clock(11), "macbook", "q1", now, xp = 50, points = 5, skillXp = mapOf("s1" to 50)),
        Event.QuestFailed(now, clock(12), "macbook", "q2", now, pointsLost = 0),
        Event.RewardCreated(now, clock(13), "macbook", "r1", "Coffee", cost = 10),
        Event.RewardClaimed(now, clock(14), "macbook", "r1", now, costPaid = 10),
        created("q3", 15),
        Event.QuestDeleted(now, clock(16), "macbook", "q3"),
        Event.ProfileChanged(
            now, clock(17), "macbook",
            FieldPatch().set(ProfileFields.DISPLAY_NAME, "first").build(),
        ),
        Event.ProfileChanged(
            now, clock(18), "macbook",
            FieldPatch().set(ProfileFields.DISPLAY_NAME, "second").build(),
        ),
    )
}
