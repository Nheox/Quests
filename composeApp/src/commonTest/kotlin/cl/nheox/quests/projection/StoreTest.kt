package cl.nheox.quests.projection

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.RepeatUnit
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.QuestFields
import cl.nheox.quests.events.SettingsFields
import cl.nheox.quests.events.Vault
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The store, end to end, against a real folder on disk.
 *
 * These are the tests that would catch a change which looks right in isolation
 * but breaks the actual loop: intent → event → file → replay → state.
 */
class StoreTest {

    private val root: Path = createTempDirectory("quests-store-test")
    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneId.of("UTC"))
    private val device = DeviceIdentity("macbook")
    private val vault = Vault(root)

    private fun newStore(): QuestStore = QuestStore(
        vault = vault,
        device = device,
        log = FileEventLog(vault, device),
        clock = clock,
        idRandom = Random(1),
    ).also { it.load() }

    private val store = newStore()

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun state() = store.state.value

    @Test
    fun `a created quest survives a restart`() {
        val id = store.createQuest(QuestDraft(title = "Write methods", difficulty = Difficulty.HIGH))
        assertEquals("Write methods", state().quests.getValue(id).title)

        // A brand new store over the same folder must see the same thing: the
        // folder is the source of truth, not the process.
        val reopened = newStore()
        assertEquals("Write methods", reopened.state.value.quests.getValue(id).title)
        assertEquals(Difficulty.HIGH, reopened.state.value.quests.getValue(id).difficulty)
    }

    @Test
    fun `completing pays xp to linked skills and points to the purse`() {
        val skill = store.createSkill("Thesis")
        val id = store.createQuest(
            QuestDraft(title = "x", difficulty = Difficulty.HIGH, fear = Fear.STRONG, estimatedMinutes = 120, skillIds = setOf(skill)),
        )
        store.completeQuest(id)

        assertEquals(QuestState.DONE, state().quests.getValue(id).state)
        assertEquals(65L, state().skills.getValue(skill).totalXp)
        assertEquals(9, state().profile.rewardPointBalance)
        assertEquals(1, state().completions.size)
    }

    @Test
    fun `a reward bonus is paid on completion, recorded, and returned by undo`() {
        val skill = store.createSkill("Thesis")
        // LOW at 30 minutes pays 2 points and 10 XP by formula.
        val id = store.createQuest(
            QuestDraft(title = "x", difficulty = Difficulty.LOW, estimatedMinutes = 30, skillIds = setOf(skill), rewardBonus = 5),
        )
        assertEquals(7, state().quests.getValue(id).projectedPoints())

        store.completeQuest(id)
        assertEquals(7, state().profile.rewardPointBalance)
        assertEquals(7, state().quests.getValue(id).rewardPoints)
        assertEquals(7, state().completions.single().points)
        assertEquals(10L, state().skills.getValue(skill).totalXp, "a bonus is points only, never XP")

        // The payout is what the event recorded, so it survives a restart
        // whatever the formula says by then.
        assertEquals(7, newStore().state.value.profile.rewardPointBalance)

        store.undo()
        assertEquals(0, state().profile.rewardPointBalance)
    }

    @Test
    fun `a reward bonus can be set, changed and cleared by patch`() {
        val id = store.createQuest(QuestDraft(title = "x", difficulty = Difficulty.LOW, estimatedMinutes = 30))
        assertNull(state().quests.getValue(id).rewardBonus)

        store.changeQuest(id, FieldPatch().setIntOrNull(QuestFields.REWARD_BONUS, 4))
        assertEquals(4, state().quests.getValue(id).rewardBonus)
        assertEquals(4, newStore().state.value.quests.getValue(id).rewardBonus)

        store.changeQuest(id, FieldPatch().setIntOrNull(QuestFields.REWARD_BONUS, null))
        assertNull(state().quests.getValue(id).rewardBonus)
        assertNull(newStore().state.value.quests.getValue(id).rewardBonus, "an explicit null clears it on replay too")

        store.changeQuest(id, FieldPatch().setIntOrNull(QuestFields.REWARD_BONUS, 3))
        store.completeQuest(id)
        assertEquals(5, state().profile.rewardPointBalance)
    }

    @Test
    fun `a reward bonus carries to the next occurrence and to a duplicate`() {
        val id = store.createQuest(
            QuestDraft(
                title = "Water the plants",
                dueDate = LocalDate.of(2026, 10, 16),
                repetition = Repetition(RepeatUnit.DAY, 3),
                rewardBonus = 2,
            ),
        )
        val copy = store.duplicateQuest(id)
        assertEquals(2, state().quests.getValue(assertNotNull(copy)).rewardBonus)

        store.completeQuest(id)
        val next = state().quests.values.single { it.isOpen && it.id != copy }
        assertEquals(2, next.rewardBonus)
    }

    @Test
    fun `undo of a completion returns the xp and the points`() {
        val skill = store.createSkill("Thesis")
        val id = store.createQuest(QuestDraft(title = "x", skillIds = setOf(skill)))
        store.completeQuest(id)
        assertTrue(state().skills.getValue(skill).totalXp > 0)

        store.undo()
        assertEquals(QuestState.OPEN, state().quests.getValue(id).state)
        assertEquals(0L, state().skills.getValue(skill).totalXp)
        assertEquals(0, state().profile.rewardPointBalance)
        assertNull(store.undoable.value)
    }

    @Test
    fun `undo of a delete brings the quest back`() {
        val id = store.createQuest(QuestDraft(title = "Mistake"))
        store.deleteQuest(id)
        assertNull(state().quests[id])

        store.undo()
        assertNotNull(state().quests[id])
        assertEquals("Mistake", state().quests.getValue(id).title)
    }

    @Test
    fun `deleting a parent takes its branch and undo restores all of it`() {
        val parent = store.createQuest(QuestDraft(title = "Parent"))
        val child = store.createQuest(QuestDraft(title = "Child", parentId = parent))
        val grandchild = store.createQuest(QuestDraft(title = "Grandchild", parentId = child))

        store.deleteQuest(parent)
        assertTrue(state().quests.keys.none { it in setOf(parent, child, grandchild) })

        store.undo()
        assertEquals(3, listOf(parent, child, grandchild).count { it in state().quests })
        assertEquals(child, state().quests.getValue(grandchild).parentId)
    }

    @Test
    fun `completing a repeating quest creates the next occurrence`() {
        val id = store.createQuest(
            QuestDraft(
                title = "Weekly notes",
                dueDate = LocalDate.of(2026, 10, 16),
                repetition = Repetition(RepeatUnit.WEEK, 1),
            ),
        )
        store.completeQuest(id)

        val next = state().quests.values.single { it.id != id && it.title == "Weekly notes" }
        assertEquals(LocalDate.of(2026, 10, 23), next.dueDate)
        assertEquals(QuestState.OPEN, next.state)
        // The finished one stays finished, so the history of how often it was
        // actually done survives.
        assertEquals(QuestState.DONE, state().quests.getValue(id).state)
    }

    @Test
    fun `failing records the failure and by default costs nothing`() {
        val id = store.createQuest(QuestDraft(title = "x", difficulty = Difficulty.HIGH, estimatedMinutes = 60))
        store.adjustPoints(100, "seed")
        store.failQuest(id)

        assertEquals(QuestState.FAILED, state().quests.getValue(id).state)
        assertEquals(1, state().failures.size)
        assertEquals(100, state().profile.rewardPointBalance, "failure must not cost points by default")
    }

    @Test
    fun `failing can be made to cost points`() {
        store.changeSettings(FieldPatch().set(SettingsFields.FAILURE_COSTS_POINTS, true))
        store.adjustPoints(100, "seed")
        val id = store.createQuest(QuestDraft(title = "x", difficulty = Difficulty.HIGH, estimatedMinutes = 60))
        store.failQuest(id)

        assertEquals(100 - 6, state().profile.rewardPointBalance)
    }

    @Test
    fun `a reward cannot be claimed without the points`() {
        val reward = store.createReward("Coffee", cost = 50)
        assertFalse(store.claimReward(reward))
        assertEquals(0, state().rewards.getValue(reward).claimCount)

        store.adjustPoints(50, "seed")
        assertTrue(store.claimReward(reward))
        assertEquals(0, state().profile.rewardPointBalance)
        assertEquals(1, state().rewards.getValue(reward).claimCount)
    }

    @Test
    fun `escalating cost rises on schedule`() {
        val reward = store.createReward("Coffee", cost = 10, costIncrement = 5, incrementEvery = 2)
        store.adjustPoints(1000, "seed")

        assertEquals(10, state().rewards.getValue(reward).currentCost)
        store.claimReward(reward)
        assertEquals(10, state().rewards.getValue(reward).currentCost)
        store.claimReward(reward)
        assertEquals(15, state().rewards.getValue(reward).currentCost, "the price rises after every two claims")
        store.claimReward(reward)
        assertEquals(15, state().rewards.getValue(reward).currentCost)
        store.claimReward(reward)
        assertEquals(20, state().rewards.getValue(reward).currentCost)
    }

    @Test
    fun `undoing a claim refunds the points and keeps the claim on the record`() {
        val reward = store.createReward("Coffee", cost = 10)
        store.adjustPoints(10, "seed")
        store.claimReward(reward)
        assertEquals(0, state().profile.rewardPointBalance)

        store.undo()
        assertEquals(10, state().profile.rewardPointBalance)
        assertEquals(1, state().claims.size, "the claim happened; the refund is the honest inverse")
    }

    @Test
    fun `consumable items are spent and permanent ones are not`() {
        val consumable = store.createItem("Day off", quantity = 2, consumable = true)
        val permanent = store.createItem("Standing desk", quantity = 1, consumable = false)

        store.useItem(consumable)
        assertEquals(1, state().items.getValue(consumable).quantity)
        store.useItem(permanent)
        assertEquals(1, state().items.getValue(permanent).quantity)
        assertNotNull(state().items.getValue(permanent).lastUsedAt)
    }

    @Test
    fun `an item cannot be used when there is none left`() {
        val item = store.createItem("Token", quantity = 1)
        store.useItem(item)
        store.useItem(item)
        assertEquals(0, state().items.getValue(item).quantity)
    }

    @Test
    fun `a skill is matched by name rather than duplicated`() {
        val first = store.findOrCreateSkill("Thesis")
        val again = store.findOrCreateSkill("thesis")
        assertEquals(first, again)
        assertEquals(1, state().skills.size)
    }

    @Test
    fun `deleting a skill unlinks it from every quest`() {
        val skill = store.createSkill("Thesis")
        val id = store.createQuest(QuestDraft(title = "x", skillIds = setOf(skill)))
        store.deleteSkill(skill)

        assertTrue(state().quests.getValue(id).skillIds.isEmpty())
        assertTrue(skill !in state().skills)
    }

    @Test
    fun `promoting a note creates a quest and links both ways`() {
        val note = store.addNote("Ask about the freezer slot")
        val questId = store.promoteNote(note)
        assertNotNull(questId)
        assertEquals("Ask about the freezer slot", state().quests.getValue(questId).title)
        assertEquals(questId, state().notes.getValue(note).promotedToQuestId)
        assertTrue(state().notes.getValue(note).done)

        // Promoting twice returns the quest already made rather than a duplicate.
        assertEquals(questId, store.promoteNote(note))
        assertEquals(1, state().quests.size)
    }

    @Test
    fun `re-parenting into one's own branch is refused`() {
        val parent = store.createQuest(QuestDraft(title = "Parent"))
        val child = store.createQuest(QuestDraft(title = "Child", parentId = parent))

        store.reparent(parent, child)
        assertNull(state().quests.getValue(parent).parentId, "a cycle would hang the Plan tree")
    }

    @Test
    fun `pin to top and bottom reorder within siblings`() {
        val a = store.createQuest(QuestDraft(title = "a"))
        val b = store.createQuest(QuestDraft(title = "b"))
        val c = store.createQuest(QuestDraft(title = "c"))
        assertTrue(listOf(a, b, c).all { state().quests.getValue(it).manualOrder == null }, "new quests start unpinned")

        store.pinToTop(c)
        assertEquals(-1, state().quests.getValue(c).manualOrder)
        store.pinToBottom(a)
        assertEquals(1, state().quests.getValue(a).manualOrder)
        assertEquals(listOf(c, b, a), visibleInPriorityOrder())

        // Each new pin goes beyond the ones already there.
        store.pinToTop(b)
        assertEquals(-2, state().quests.getValue(b).manualOrder)
        store.pinToBottom(c)
        assertEquals(2, state().quests.getValue(c).manualOrder)
        assertEquals(listOf(b, a, c), visibleInPriorityOrder())
    }

    @Test
    fun `pins are per sibling group`() {
        val parent = store.createQuest(QuestDraft(title = "Parent"))
        val child = store.createQuest(QuestDraft(title = "Child", parentId = parent))
        val root = store.createQuest(QuestDraft(title = "Root"))
        store.pinToTop(root)
        store.pinToTop(root)
        assertEquals(-1, state().quests.getValue(root).manualOrder, "re-pinning the only pin keeps its place")

        // The root group's pin at -1 says nothing about the child's group.
        store.pinToTop(child)
        assertEquals(-1, state().quests.getValue(child).manualOrder)
        store.pinToBottom(parent)
        assertEquals(1, state().quests.getValue(parent).manualOrder)

        // A child whose parent has not synced in yet shows among the roots, so
        // it pins among them too.
        val orphan = store.createQuest(QuestDraft(title = "Orphan", parentId = "q-not-here-yet"))
        store.pinToTop(orphan)
        assertEquals(-2, state().quests.getValue(orphan).manualOrder)
    }

    @Test
    fun `new pins land beyond the anchor whatever older moves left behind`() {
        // Values as a 0.4 "move to top" (0) and "move to bottom" (5) wrote them.
        val legacyTop = store.createQuest(QuestDraft(title = "legacy top"))
        val legacyBottom = store.createQuest(QuestDraft(title = "legacy bottom"))
        val fresh = store.createQuest(QuestDraft(title = "fresh"))
        store.changeQuest(legacyTop, FieldPatch().setIntOrNull(QuestFields.MANUAL_ORDER, 0))
        store.changeQuest(legacyBottom, FieldPatch().setIntOrNull(QuestFields.MANUAL_ORDER, 5))

        store.pinToTop(fresh)
        assertEquals(-1, state().quests.getValue(fresh).manualOrder, "above the legacy top pin at 0")
        assertEquals(listOf(fresh, legacyTop, legacyBottom), visibleInPriorityOrder())

        // Crossing over: a top pin pinned to the bottom goes below every sibling.
        store.pinToBottom(fresh)
        assertEquals(6, state().quests.getValue(fresh).manualOrder)
        assertEquals(listOf(legacyTop, legacyBottom, fresh), visibleInPriorityOrder())

        // Only bottom pins left among the others: a top pin still starts at -1,
        // never at a positive value that would read as the bottom.
        store.clearPin(legacyTop)
        store.pinToTop(legacyTop)
        assertEquals(-1, state().quests.getValue(legacyTop).manualOrder)
    }

    @Test
    fun `a pin and its clearing round-trip through the log`() {
        val id = store.createQuest(QuestDraft(title = "x"))
        val log = vault.logFile(device.id)
        assertFalse(
            java.nio.file.Files.readString(log).contains(QuestFields.MANUAL_ORDER),
            "a new quest writes no creation order",
        )

        store.pinToBottom(id)
        assertEquals(1, newStore().state.value.quests.getValue(id).manualOrder)

        store.clearPin(id)
        assertNull(state().quests.getValue(id).manualOrder)
        assertTrue(
            java.nio.file.Files.readAllLines(log).last().contains("\"${QuestFields.MANUAL_ORDER}\":null"),
            "clearing is an explicit null on the wire",
        )
        assertNull(newStore().state.value.quests.getValue(id).manualOrder, "the clear survives a replay")

        // Clearing what is not pinned writes nothing.
        val before = java.nio.file.Files.size(log)
        store.clearPin(id)
        assertEquals(before, java.nio.file.Files.size(log))
    }

    @Test
    fun `the next occurrence of a pinned repeating quest keeps the pin`() {
        val id = store.createQuest(
            QuestDraft(title = "Morning pages", dueDate = LocalDate.of(2026, 10, 15), repetition = Repetition(RepeatUnit.DAY, 1)),
        )
        store.pinToTop(id)
        store.completeQuest(id)

        val next = state().quests.values.single { it.isOpen }
        assertEquals(-1, next.manualOrder)
        assertEquals(-1, newStore().state.value.quests.getValue(next.id).manualOrder)
    }

    private fun visibleInPriorityOrder(): List<String> =
        cl.nheox.quests.domain.Sorting.sort(
            state().openQuests,
            cl.nheox.quests.domain.SortOrder(cl.nheox.quests.domain.SortKey.PRIORITY, cl.nheox.quests.domain.SortDirection.DESCENDING),
            clock = clock,
        ).map { it.id }

    @Test
    fun `bulk operations apply to every selected quest`() {
        val ids = (1..5).map { store.createQuest(QuestDraft(title = "q$it")) }
        store.rescheduleAll(ids, LocalDate.of(2026, 10, 20))
        assertTrue(ids.all { state().quests.getValue(it).dueDate == LocalDate.of(2026, 10, 20) })

        val skill = store.createSkill("Thesis")
        store.assignSkillsToAll(ids, setOf(skill))
        assertTrue(ids.all { skill in state().quests.getValue(it).skillIds })

        store.setDifficultyForAll(ids, Difficulty.EXTREME)
        assertTrue(ids.all { state().quests.getValue(it).difficulty == Difficulty.EXTREME })
    }

    @Test
    fun `next repetition advances the due date by one cycle`() {
        val id = store.createQuest(
            QuestDraft(
                title = "x",
                dueDate = LocalDate.of(2026, 10, 16),
                repetition = Repetition(RepeatUnit.DAY, 3),
            ),
        )
        store.advanceToNextRepetition(id)
        assertEquals(LocalDate.of(2026, 10, 19), state().quests.getValue(id).dueDate)
    }

    @Test
    fun `clearing a due date clears its time too`() {
        val id = store.createQuest(
            QuestDraft(title = "x", dueDate = LocalDate.of(2026, 10, 16), dueTime = java.time.LocalTime.of(15, 0)),
        )
        store.reschedule(id, null)
        assertNull(state().quests.getValue(id).dueDate)
        assertNull(state().quests.getValue(id).dueTime, "a time with no date would be unreachable")
    }

    @Test
    fun `every write lands in this device's own log and nowhere else`() {
        store.createQuest(QuestDraft(title = "x"))
        store.createSkill("Thesis")

        val own = vault.logFile(device.id)
        assertTrue(java.nio.file.Files.exists(own))
        val others = java.nio.file.Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().startsWith("events-") && it != own }.count()
        }
        assertEquals(0, others)
    }

    @Test
    fun `a change to an unknown quest is a no-op rather than a crash`() {
        store.changeQuest("nope", FieldPatch().set(QuestFields.TITLE, "ghost"))
        assertTrue(state().quests.isEmpty())
    }

    @Test
    fun `an empty patch writes nothing at all`() {
        val id = store.createQuest(QuestDraft(title = "x"))
        val before = java.nio.file.Files.size(vault.logFile(device.id))
        store.changeQuest(id, FieldPatch())
        assertEquals(before, java.nio.file.Files.size(vault.logFile(device.id)))
    }
}
