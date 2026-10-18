package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate

/**
 * The "what now?" suggester (§7.4).
 *
 * Tested for the behaviour that justifies it existing rather than for exact
 * scores: frightening work should be pushed into high-energy windows and kept
 * out of low-energy ones, and nothing that cannot fit in the time available
 * should be suggested at all.
 */
class SuggesterTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun quest(
        id: String,
        difficulty: Difficulty = Difficulty.MODERATE,
        fear: Fear = Fear.NONE,
        urgency: Urgency = Urgency.NORMAL,
        minutes: Int? = 30,
        due: LocalDate? = null,
        state: QuestState = QuestState.OPEN,
        skills: Set<String> = emptySet(),
    ) = Quest(
        id = id,
        title = id,
        difficulty = difficulty,
        fear = fear,
        urgency = urgency,
        estimatedMinutes = minutes,
        dueDate = due,
        state = state,
        skillIds = skills,
        createdAt = created,
        updatedAt = created,
    )

    private fun context(energy: Int, minutes: Int? = null, skills: Map<String, Skill> = emptyMap()) =
        SuggestContext(energy = energy, minutesAvailable = minutes, today = today, skills = skills)

    @Test
    fun `suggests at most three`() {
        val quests = (1..20).map { quest("q$it") }
        assertEquals(3, Suggester.suggest(quests, context(70)).size)
    }

    @Test
    fun `only open quests are suggested`() {
        val quests = listOf(
            quest("done", state = QuestState.DONE),
            quest("failed", state = QuestState.FAILED),
            quest("open"),
        )
        assertEquals(listOf("open"), Suggester.suggest(quests, context(70)).map { it.quest.id })
    }

    @Test
    fun `high energy favours the frightening one`() {
        // The central design claim: fear is cheapest to face when energy is high,
        // so that is when the app should point at it.
        val quests = listOf(
            quest("scary", fear = Fear.SEVERE, difficulty = Difficulty.HIGH),
            quest("dull", fear = Fear.NONE, difficulty = Difficulty.LOW),
        )
        val best = Suggester.suggest(quests, context(energy = 95)).first()
        assertEquals("scary", best.quest.id)
        assertTrue(SuggestionReason.GOOD_ENERGY_FOR_FEAR in best.reasons)
    }

    @Test
    fun `spent energy favours the gentle one`() {
        val quests = listOf(
            quest("scary", fear = Fear.SEVERE, difficulty = Difficulty.VERY_HIGH),
            quest("gentle", fear = Fear.NONE, difficulty = Difficulty.VERY_LOW),
        )
        val best = Suggester.suggest(quests, context(energy = 10)).first()
        assertEquals("gentle", best.quest.id)
        assertTrue(SuggestionReason.GENTLE_ON_LOW_ENERGY in best.reasons)
    }

    @Test
    fun `work that does not fit the window is pushed down the list`() {
        val quests = listOf(
            quest("long", minutes = 240),
            quest("fits", minutes = 20),
        )
        val ranked = Suggester.suggest(quests, context(energy = 70, minutes = 25)).map { it.quest.id }
        assertEquals("fits", ranked.first())
    }

    @Test
    fun `overdue work is surfaced and says so`() {
        val quests = listOf(
            quest("overdue", due = today.minusDays(4)),
            quest("someday"),
        )
        val best = Suggester.suggest(quests, context(energy = 60)).first()
        assertEquals("overdue", best.quest.id)
        assertTrue(SuggestionReason.OVERDUE in best.reasons)
    }

    @Test
    fun `urgency breaks a tie`() {
        val quests = listOf(
            quest("calm", urgency = Urgency.WHENEVER),
            quest("urgent", urgency = Urgency.CRITICAL),
        )
        assertEquals("urgent", Suggester.suggest(quests, context(60)).first().quest.id)
    }

    @Test
    fun `a quest that would tip a skill over a level is flagged`() {
        val skill = Skill(id = "s1", name = "Thesis", totalXp = 300, createdAt = created)
        // Level 2 begins at 303 XP, and this quest pays more than the three
        // needed, so finishing it levels the skill up.
        val quests = listOf(quest("tips", skills = setOf("s1")))
        val suggestion = Suggester.suggest(quests, context(60, skills = mapOf("s1" to skill))).first()
        assertTrue(SuggestionReason.CLOSE_TO_A_LEVEL in suggestion.reasons)
    }

    @Test
    fun `a short quest in a long window is called a quick win`() {
        val suggestion = Suggester.suggest(listOf(quest("tiny", minutes = 5)), context(60, minutes = 120)).first()
        assertTrue(SuggestionReason.QUICK_WIN in suggestion.reasons)
    }

    @Test
    fun `every suggestion carries at least one reason`() {
        // A suggester that cannot explain itself gets ignored.
        val quests = listOf(
            quest("a", due = today, fear = Fear.STRONG),
            quest("b", urgency = Urgency.HIGH, minutes = 15),
            quest("c", due = today.minusDays(1)),
        )
        Suggester.suggest(quests, context(80, minutes = 60)).forEach { suggestion ->
            assertTrue(suggestion.reasons.isNotEmpty(), "${suggestion.quest.id} had no reason")
        }
    }

    @Test
    fun `ranking is deterministic for identical quests`() {
        // Two quests that score the same must not swap places between calls, or
        // the panel reshuffles under the cursor.
        val quests = listOf(quest("b"), quest("a"), quest("c"))
        val first = Suggester.suggest(quests, context(60)).map { it.quest.id }
        val second = Suggester.suggest(quests.reversed(), context(60)).map { it.quest.id }
        assertEquals(first, second)
    }

    @Test
    fun `an empty list suggests nothing rather than failing`() {
        assertTrue(Suggester.suggest(emptyList(), context(60)).isEmpty())
    }

    @Test
    fun `a blank title is never suggested`() {
        val quests = listOf(quest("real"), quest("blank").copy(title = "  "))
        assertEquals(listOf("real"), Suggester.suggest(quests, context(60)).map { it.quest.id })
    }
}
