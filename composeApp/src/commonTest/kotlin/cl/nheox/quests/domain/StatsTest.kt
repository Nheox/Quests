package cl.nheox.quests.domain

import cl.nheox.quests.projection.CompletionRecord
import cl.nheox.quests.projection.FailureRecord
import cl.nheox.quests.projection.QuestsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {

    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), zone)
    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private val created = Instant.parse("2026-07-01T09:00:00Z")

    private fun on(date: LocalDate): Instant = date.atTime(12, 0).atZone(zone).toInstant()

    private fun completion(fear: Fear, date: LocalDate, xp: Int = 10, skills: Set<String> = emptySet()) =
        CompletionRecord(
            questId = "q${date}-${fear.name}-$xp",
            title = "q",
            at = on(date),
            xp = xp,
            points = 2,
            difficulty = Difficulty.MODERATE,
            fear = fear,
            estimatedMinutes = 30,
            skillIds = skills,
        )

    private fun failure(fear: Fear, date: LocalDate) = FailureRecord(
        questId = "f${date}-${fear.name}",
        title = "f",
        at = on(date),
        difficulty = Difficulty.MODERATE,
        fear = fear,
        pointsLost = 0,
    )

    private fun openQuest(id: String, fear: Fear, due: LocalDate? = null) = Quest(
        id = id,
        title = id,
        fear = fear,
        dueDate = due,
        createdAt = created,
        updatedAt = created,
    )

    @Test
    fun `the fear log separates resolved from everything raised`() {
        // The case that made this matter: two Severe quests resolved, both
        // finished, but six more still sitting open. A rate over resolved
        // quests alone reports 100% and hides the avoidance entirely.
        val state = QuestsState(
            quests = (1..6).associate { "s$it" to openQuest("s$it", Fear.SEVERE, today.minusDays(10)) },
            completions = listOf(completion(Fear.SEVERE, today.minusDays(20)), completion(Fear.SEVERE, today.minusDays(21), xp = 11)),
        )
        val severe = Stats.fearLog(state, clock).single { it.fear == Fear.SEVERE }

        assertEquals(2, severe.completed)
        assertEquals(0, severe.failed)
        assertEquals(6, severe.open)
        assertEquals(6, severe.openOverdue)
        assertEquals(1f, severe.completionRate)
        assertEquals(0.25f, severe.throughputRate, "2 finished out of 8 ever raised")
    }

    @Test
    fun `the fear log reports every level, including untouched ones`() {
        val log = Stats.fearLog(QuestsState(), clock)
        assertEquals(Fear.entries.size, log.size)
        log.forEach { stat ->
            assertNull(stat.completionRate, "a level with no history has no rate, not a zero")
            assertEquals(0, stat.total)
        }
    }

    @Test
    fun `weekly buckets include the weeks with nothing in them`() {
        val state = QuestsState(
            completions = listOf(
                completion(Fear.NONE, today, xp = 50),
                completion(Fear.NONE, today.minusWeeks(3), xp = 20),
            ),
        )
        val weeks = Stats.weeklyBuckets(state, clock, weeks = 6)

        assertEquals(6, weeks.size, "an omitted empty week would make a gap look like consistency")
        // Six buckets ending with the current week, so three weeks back is index 2.
        assertEquals(listOf(0, 0, 20, 0, 0, 50), weeks.map { it.xp })
        // Monday-based, and the last bucket is the current week.
        assertEquals(Dates.startOfWeek(today), weeks.last().weekStart)
    }

    @Test
    fun `weekly completion rate counts failures in the denominator`() {
        val state = QuestsState(
            completions = listOf(completion(Fear.NONE, today), completion(Fear.NONE, today, xp = 11)),
            failures = listOf(failure(Fear.NONE, today), failure(Fear.MILD, today)),
        )
        val week = Stats.weeklyBuckets(state, clock, weeks = 1).single()
        assertEquals(2, week.completed)
        assertEquals(2, week.failed)
        assertEquals(0.5f, week.completionRate)
    }

    @Test
    fun `a week with nothing resolved has no rate rather than zero`() {
        val week = Stats.weeklyBuckets(QuestsState(), clock, weeks = 1).single()
        assertNull(week.completionRate)
    }

    @Test
    fun `streaks count consecutive active days and tolerate today not having started`() {
        // Yesterday still counts: a streak that broke at midnight would punish
        // anyone who works in the evening.
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, Stats.currentStreak(days, today))
        assertEquals(4, Stats.currentStreak(listOf(today) + days, today))
        // A two-day gap ends it.
        assertEquals(0, Stats.currentStreak(listOf(today.minusDays(3)), today))
    }

    @Test
    fun `the longest streak is found anywhere in the history`() {
        val days = listOf(
            today.minusDays(1),
            today.minusDays(10), today.minusDays(11), today.minusDays(12), today.minusDays(13),
            today.minusDays(20),
        )
        assertEquals(4, Stats.longestStreak(days))
        assertEquals(1, Stats.currentStreak(days, today))
        assertEquals(0, Stats.longestStreak(emptyList()))
    }

    @Test
    fun `skill growth attributes xp the same way the payout did`() {
        val skill = Skill(id = "s1", name = "Thesis", totalXp = 100, createdAt = created)
        val other = Skill(id = "s2", name = "Lab", totalXp = 100, createdAt = created)
        val state = QuestsState(
            skills = mapOf("s1" to skill, "s2" to other),
            // 11 XP split across two skills: 6 and 5, matching Balance.distribute.
            completions = listOf(completion(Fear.NONE, today, xp = 11, skills = setOf("s1", "s2"))),
        )
        val growth = Stats.skillGrowth(state, clock).associateBy { it.skillId }
        assertEquals(11, growth.getValue("s1").xpLast30Days + growth.getValue("s2").xpLast30Days)
        assertTrue(growth.getValue("s1").xpLast30Days in 5..6)
    }

    @Test
    fun `the summary agrees with its own parts`() {
        val state = QuestsState(
            quests = mapOf(
                "open" to openQuest("open", Fear.NONE),
                "late" to openQuest("late", Fear.STRONG, today.minusDays(3)),
            ),
            completions = listOf(completion(Fear.NONE, today, xp = 25)),
            failures = listOf(failure(Fear.MILD, today)),
        )
        val summary = Stats.summarise(state, clock)

        assertEquals(2, summary.openCount)
        assertEquals(1, summary.overdueCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.completedLast7Days)
        assertEquals(25, summary.xpLast7Days)
        assertEquals(Fear.entries.size, summary.fearLog.size)
    }

    @Test
    fun `asking for zero weeks returns nothing rather than failing`() {
        assertTrue(Stats.weeklyBuckets(QuestsState(), clock, weeks = 0).isEmpty())
    }
}
