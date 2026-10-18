package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate

class SortingTest {

    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun quest(
        id: String,
        title: String = id,
        order: Int = 0,
        due: LocalDate? = null,
        difficulty: Difficulty = Difficulty.MODERATE,
        urgency: Urgency = Urgency.NORMAL,
        minutes: Int? = null,
    ) = Quest(
        id = id,
        title = title,
        manualOrder = order,
        dueDate = due,
        difficulty = difficulty,
        urgency = urgency,
        estimatedMinutes = minutes,
        createdAt = created,
        updatedAt = created,
    )

    @Test
    fun `manual order is respected`() {
        val quests = listOf(quest("c", order = 3), quest("a", order = 1), quest("b", order = 2))
        assertEquals(
            listOf("a", "b", "c"),
            Sorting.sort(quests, SortOrder(SortKey.MANUAL)).map { it.id },
        )
    }

    @Test
    fun `undated quests sort last whichever direction is chosen`() {
        // A quest with no due date is not "due in year zero"; it belongs at the
        // end either way, or the list is unusable in descending order.
        val quests = listOf(
            quest("none"),
            quest("soon", due = LocalDate.of(2026, 10, 16)),
            quest("later", due = LocalDate.of(2026, 11, 1)),
        )
        assertEquals(
            listOf("soon", "later", "none"),
            Sorting.sort(quests, SortOrder(SortKey.DUE_DATE, SortDirection.ASCENDING)).map { it.id },
        )
        assertEquals(
            "none",
            Sorting.sort(quests, SortOrder(SortKey.DUE_DATE, SortDirection.DESCENDING)).map { it.id }.first(),
        )
    }

    @Test
    fun `sorting is total so it is stable across runs`() {
        // Every quest here ties on the sort key; only the tie-breaks separate
        // them. Without a total order, replay on two devices could present the
        // same list differently.
        val quests = listOf(quest("z", title = "same"), quest("a", title = "same"), quest("m", title = "same"))
        val once = Sorting.sort(quests, SortOrder(SortKey.URGENCY)).map { it.id }
        val twice = Sorting.sort(quests.shuffled(), SortOrder(SortKey.URGENCY)).map { it.id }
        assertEquals(once, twice)
        assertEquals(listOf("a", "m", "z"), once)
    }

    @Test
    fun `a random sort with a fixed seed holds still`() {
        val quests = (1..10).map { quest("q$it") }
        val once = Sorting.sort(quests, SortOrder(SortKey.RANDOM), randomSeed = 99).map { it.id }
        val twice = Sorting.sort(quests, SortOrder(SortKey.RANDOM), randomSeed = 99).map { it.id }
        assertEquals(once, twice, "a list that reshuffles under the cursor is unusable")
        val different = Sorting.sort(quests, SortOrder(SortKey.RANDOM), randomSeed = 100).map { it.id }
        assertTrue(different != once, "a new seed should actually reshuffle")
    }

    @Test
    fun `sorting by xp-to-next-level uses the closest linked skill`() {
        val skills = mapOf(
            "near" to Skill(id = "near", name = "near", totalXp = 300, createdAt = created),
            "far" to Skill(id = "far", name = "far", totalXp = 0, createdAt = created),
        )
        val quests = listOf(
            quest("far-quest").copy(skillIds = setOf("far")),
            quest("near-quest").copy(skillIds = setOf("near")),
            quest("no-skill"),
        )
        val sorted = Sorting.sort(quests, SortOrder(SortKey.XP_TO_NEXT_LEVEL), skills).map { it.id }
        assertEquals("near-quest", sorted.first())
        assertEquals("no-skill", sorted.last())
    }

    @Test
    fun `every sort key works on an awkward list`() {
        val quests = listOf(
            quest("a", minutes = null, due = null),
            quest("b", minutes = 30, due = LocalDate.of(2026, 10, 20)),
        )
        SortKey.entries.forEach { key ->
            SortDirection.entries.forEach { direction ->
                // Exhaustive: no key may throw on nulls, and all of them must
                // return the whole list.
                val sorted = Sorting.sort(quests, SortOrder(key, direction))
                assertEquals(2, sorted.size, "$key/$direction lost a quest")
            }
        }
    }

    @Test
    fun `due buckets classify correctly`() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(DueBucket.OVERDUE, Sorting.bucketOf(quest("x", due = today.minusDays(1)), today))
        assertEquals(DueBucket.TODAY, Sorting.bucketOf(quest("x", due = today), today))
        assertEquals(DueBucket.TOMORROW, Sorting.bucketOf(quest("x", due = today.plusDays(1)), today))
        assertEquals(DueBucket.THIS_WEEK, Sorting.bucketOf(quest("x", due = today.plusDays(5)), today))
        assertEquals(DueBucket.LATER, Sorting.bucketOf(quest("x", due = today.plusDays(30)), today))
        assertEquals(DueBucket.SOMEDAY, Sorting.bucketOf(quest("x", due = null), today))
    }
}
