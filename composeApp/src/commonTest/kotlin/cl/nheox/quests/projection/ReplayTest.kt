package cl.nheox.quests.projection

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.events.Event
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.Hlc
import cl.nheox.quests.events.QuestFields
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate

/**
 * Replay determinism (§8.4).
 *
 * This is the test that protects sync correctness. If projection ever depends
 * on the order logs happen to be read in, two devices looking at the same folder
 * will disagree about reality, and no amount of careful merging downstream can
 * fix that.
 */
class ReplayTest {

    private val epoch: Instant = Instant.parse("2026-10-01T09:00:00Z")

    private fun at(seconds: Long): Instant = epoch.plusSeconds(seconds)

    private fun clock(seconds: Long, counter: Int = 0, node: String = "a"): String =
        Hlc(at(seconds).toEpochMilli(), counter, node).encode()

    private fun created(
        id: String,
        seconds: Long,
        node: String = "a",
        title: String = "Quest $id",
        difficulty: Difficulty = Difficulty.MODERATE,
        fear: Fear = Fear.NONE,
        skills: Set<String> = emptySet(),
        parent: String? = null,
    ) = Event.QuestCreated(
        ts = at(seconds),
        hlc = clock(seconds, node = node),
        device = node,
        id = id,
        title = title,
        difficulty = difficulty,
        fear = fear,
        skillIds = skills,
        parentId = parent,
    )

    private fun changed(id: String, seconds: Long, node: String = "a", patch: FieldPatch) =
        Event.QuestChanged(at(seconds), clock(seconds, node = node), node, id, patch.build())

    private fun skill(id: String, name: String, seconds: Long = 0) =
        Event.SkillCreated(at(seconds), clock(seconds), "a", id, name)

    @Test
    fun `replay is independent of read order`() {
        val events = buildHistory()

        val reference = Projector.replay(events)
        val random = Random(1234)
        repeat(25) { attempt ->
            val shuffled = events.shuffled(random)
            val replayed = Projector.replay(shuffled)
            assertEquals(reference.quests, replayed.quests, "quests differ on shuffle $attempt")
            assertEquals(reference.skills, replayed.skills, "skills differ on shuffle $attempt")
            assertEquals(reference.profile, replayed.profile, "profile differs on shuffle $attempt")
            assertEquals(reference.settings, replayed.settings, "settings differ on shuffle $attempt")
            assertEquals(
                reference.completions.sortedBy { it.questId },
                replayed.completions.sortedBy { it.questId },
                "completions differ on shuffle $attempt",
            )
            assertEquals(reference.deletedQuestIds, replayed.deletedQuestIds)
        }
    }

    @Test
    fun `replay is independent of how logs are split between devices`() {
        // What a real sync looks like: the same events arriving as two files,
        // one per device, read in whatever order the filesystem offers.
        val events = buildHistory()
        val byDevice = events.groupBy { it.device }
        val reference = Projector.replay(events)

        val permutations = listOf(
            byDevice.keys.toList(),
            byDevice.keys.reversed(),
        )
        permutations.forEach { order ->
            val concatenated = order.flatMap { byDevice.getValue(it) }
            assertEquals(reference, Projector.replay(concatenated).copy(eventCount = reference.eventCount))
        }
    }

    @Test
    fun `last write wins per field, not per object`() {
        // Two devices edit different fields of the same quest while offline.
        // Both edits must survive; a whole-object write would lose one.
        val events = listOf(
            created("q1", 0, title = "Original"),
            changed("q1", 10, node = "macbook", patch = FieldPatch().set(QuestFields.TITLE, "From the laptop")),
            changed("q1", 20, node = "ubuntu", patch = FieldPatch().setEnum(QuestFields.URGENCY, Urgency.CRITICAL)),
        )
        val state = Projector.replay(events)
        val quest = state.quests.getValue("q1")
        assertEquals("From the laptop", quest.title)
        assertEquals(Urgency.CRITICAL, quest.urgency)
    }

    @Test
    fun `the later clock wins a genuine collision`() {
        val events = listOf(
            created("q1", 0, title = "Original"),
            changed("q1", 30, node = "ubuntu", patch = FieldPatch().set(QuestFields.TITLE, "Later")),
            changed("q1", 20, node = "macbook", patch = FieldPatch().set(QuestFields.TITLE, "Earlier")),
        )
        assertEquals("Later", Projector.replay(events).quests.getValue("q1").title)
    }

    @Test
    fun `a collision in the same millisecond is broken consistently`() {
        // Same wall clock, same counter, different devices: the node id decides,
        // and it must decide the same way on every machine.
        val sameMillis = at(100).toEpochMilli()
        val fromA = Event.QuestChanged(
            at(100), Hlc(sameMillis, 3, "aaa").encode(), "aaa", "q1",
            FieldPatch().set(QuestFields.TITLE, "from aaa").build(),
        )
        val fromZ = Event.QuestChanged(
            at(100), Hlc(sameMillis, 3, "zzz").encode(), "zzz", "q1",
            FieldPatch().set(QuestFields.TITLE, "from zzz").build(),
        )
        val base = created("q1", 0)

        assertEquals("from zzz", Projector.replay(listOf(base, fromA, fromZ)).quests.getValue("q1").title)
        assertEquals("from zzz", Projector.replay(listOf(base, fromZ, fromA)).quests.getValue("q1").title)
    }

    @Test
    fun `an explicit null clears a field and an absent key leaves it alone`() {
        val events = listOf(
            created("q1", 0),
            changed(
                "q1", 10,
                patch = FieldPatch()
                    .setDate(QuestFields.DUE_DATE, LocalDate.of(2026, 10, 20))
                    .set(QuestFields.NOTES, "keep me"),
            ),
            changed("q1", 20, patch = FieldPatch().clear(QuestFields.DUE_DATE)),
        )
        val quest = Projector.replay(events).quests.getValue("q1")
        assertNull(quest.dueDate)
        assertEquals("keep me", quest.notes)
    }

    @Test
    fun `a tombstone beats a late-arriving creation`() {
        // An old log syncing in must not resurrect a deleted quest.
        val events = listOf(
            Event.QuestDeleted(at(50), clock(50), "a", "q1"),
            created("q1", 10),
        )
        val state = Projector.replay(events)
        assertTrue("q1" !in state.quests)
        assertTrue("q1" in state.deletedQuestIds)
    }

    @Test
    fun `undoing a delete restores the quest exactly`() {
        val events = listOf(
            created("q1", 0, title = "Important", difficulty = Difficulty.EXTREME, fear = Fear.SEVERE),
            changed("q1", 5, patch = FieldPatch().set(QuestFields.NOTES, "with notes")),
            Event.QuestDeleted(at(10), clock(10), "a", "q1"),
            Event.QuestRestored(at(20), clock(20), "a", "q1"),
        )
        val quest = Projector.replay(events).quests.getValue("q1")
        assertEquals("Important", quest.title)
        assertEquals("with notes", quest.notes)
        assertEquals(Difficulty.EXTREME, quest.difficulty)
        assertEquals(Fear.SEVERE, quest.fear)
        assertTrue("q1" !in Projector.replay(events).deletedQuestIds)
    }

    @Test
    fun `deleting a parent promotes its children instead of hiding them`() {
        val events = listOf(
            created("parent", 0),
            created("child", 1, parent = "parent"),
            Event.QuestDeleted(at(10), clock(10), "a", "parent"),
        )
        val state = Projector.replay(events)
        assertNull(state.quests["parent"])
        assertNull(state.quests.getValue("child").parentId)
    }

    @Test
    fun `completion pays out exactly what the event recorded`() {
        // Not what the current formula would produce: retuning the balance must
        // never rewrite what somebody already earned.
        val events = listOf(
            skill("s1", "Thesis"),
            created("q1", 1, skills = setOf("s1")),
            Event.QuestCompleted(
                ts = at(10), hlc = clock(10), device = "a", id = "q1", at = at(10),
                xp = 999, points = 42, skillXp = mapOf("s1" to 999),
            ),
        )
        val state = Projector.replay(events)
        assertEquals(999L, state.skills.getValue("s1").totalXp)
        assertEquals(42, state.profile.rewardPointBalance)
        assertEquals(QuestState.DONE, state.quests.getValue("q1").state)
        assertEquals(1, state.completions.size)
    }

    @Test
    fun `reopening takes the payout back`() {
        val events = listOf(
            skill("s1", "Thesis"),
            created("q1", 1, skills = setOf("s1")),
            Event.QuestCompleted(at(10), clock(10), "a", "q1", at(10), xp = 60, points = 6, skillXp = mapOf("s1" to 60)),
            Event.QuestReopened(
                at(20), clock(20), "a", "q1",
                xpReturned = 60, pointsReturned = 6, skillXpReturned = mapOf("s1" to 60),
            ),
        )
        val state = Projector.replay(events)
        assertEquals(0L, state.skills.getValue("s1").totalXp)
        assertEquals(0, state.profile.rewardPointBalance)
        assertEquals(QuestState.OPEN, state.quests.getValue("q1").state)
        // And the withdrawn completion leaves the history, so the fear log is
        // not quietly inflated by undo.
        assertEquals(0, state.completions.size)
    }

    @Test
    fun `xp never goes below zero`() {
        val events = listOf(
            skill("s1", "Thesis"),
            Event.SkillXpGranted(at(5), clock(5), "a", "s1", -500),
        )
        assertEquals(0L, Projector.replay(events).skills.getValue("s1").totalXp)
    }

    @Test
    fun `a self-parent is refused rather than producing a cycle`() {
        // The log is a file the user is invited to edit; a cycle here would be an
        // infinite expansion in the Plan tree, not a cosmetic problem.
        val events = listOf(
            created("q1", 0),
            changed("q1", 10, patch = FieldPatch().setStringOrNull(QuestFields.PARENT_ID, "q1")),
        )
        assertNull(Projector.replay(events).quests.getValue("q1").parentId)
    }

    @Test
    fun `events for unknown entities are ignored, not fatal`() {
        val events = listOf(
            changed("missing", 10, patch = FieldPatch().set(QuestFields.TITLE, "nobody")),
            Event.QuestCompleted(at(11), clock(11), "a", "missing", at(11), xp = 10, points = 1),
            Event.RewardClaimed(at(12), clock(12), "a", "missing", at(12), costPaid = 5),
            Event.ItemUsed(at(13), clock(13), "a", "missing", at(13)),
        )
        val state = Projector.replay(events)
        assertTrue(state.quests.isEmpty())
        assertEquals(0, state.profile.rewardPointBalance)
    }

    @Test
    fun `incremental apply agrees with a full replay`() {
        // The store folds locally generated events into existing state instead of
        // replaying from scratch; the two paths must not drift.
        val events = buildHistory()
        val full = Projector.replay(events)

        val ordered = Projector.sortByClock(events)
        var incremental = QuestsState()
        ordered.forEach { event -> incremental = Projector.apply(incremental, listOf(event)) }

        assertEquals(full.quests, incremental.quests)
        assertEquals(full.skills, incremental.skills)
        assertEquals(full.profile, incremental.profile)
    }

    /** A history with two devices, edits, collisions, deletes and payouts. */
    private fun buildHistory(): List<Event> = listOf(
        skill("s_thesis", "Thesis", 0),
        skill("s_lab", "Lab work", 1),
        created("q1", 2, node = "macbook", title = "Write methods", skills = setOf("s_thesis")),
        created("q2", 3, node = "ubuntu", title = "Run PCR", skills = setOf("s_lab")),
        created("q3", 4, node = "macbook", title = "Sub-task", parent = "q1"),
        changed("q1", 10, node = "ubuntu", patch = FieldPatch().setEnum(QuestFields.DIFFICULTY, Difficulty.HIGH)),
        changed("q1", 11, node = "macbook", patch = FieldPatch().setEnum(QuestFields.FEAR, Fear.STRONG)),
        changed("q1", 12, node = "ubuntu", patch = FieldPatch().set(QuestFields.TITLE, "Write the methods section")),
        changed("q2", 13, node = "macbook", patch = FieldPatch().set(QuestFields.ESTIMATED_MINUTES, 180)),
        Event.QuestCompleted(
            at(20), clock(20, node = "macbook"), "macbook", "q3", at(20),
            xp = 15, points = 2, skillXp = emptyMap(),
        ),
        Event.QuestFailed(at(21), clock(21, node = "ubuntu"), "ubuntu", "q2", at(21), pointsLost = 0),
        Event.QuestReopened(at(22), clock(22, node = "ubuntu"), "ubuntu", "q2"),
        Event.RewardCreated(at(23), clock(23), "macbook", "r1", "Coffee", cost = 10),
        Event.PointsAdjusted(at(24), clock(24), "macbook", 50, "seed"),
        Event.RewardClaimed(at(25), clock(25), "macbook", "r1", at(25), costPaid = 10),
        Event.ItemCreated(at(26), clock(26), "ubuntu", "i1", "Day off", quantity = 2),
        Event.ItemUsed(at(27), clock(27), "ubuntu", "i1", at(27)),
        Event.NoteCreated(at(28), clock(28), "macbook", "n1", "remember this"),
        Event.NotePromoted(at(29), clock(29), "macbook", "n1", "q1"),
        Event.ProfileChanged(
            at(30), clock(30), "ubuntu",
            FieldPatch().set(cl.nheox.quests.events.ProfileFields.DISPLAY_NAME, "Researcher").build(),
        ),
        Event.SettingsChanged(
            at(31), clock(31), "macbook",
            FieldPatch().set(cl.nheox.quests.events.SettingsFields.ONBOARDING_COMPLETE, true).build(),
        ),
        created("q4", 32, node = "ubuntu", title = "To be deleted"),
        Event.QuestDeleted(at(33), clock(33, node = "ubuntu"), "ubuntu", "q4"),
    )
}
