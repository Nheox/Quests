package cl.nheox.quests.domain

import cl.nheox.quests.projection.CompletionRecord
import cl.nheox.quests.projection.QuestsState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The numbers behind the Standing screen. The clock sits in a zone that is
 * not UTC, on a Thursday, so day boundaries and the ISO week are both
 * exercised rather than lining up with UTC midnight by accident.
 */
class StandingTest {

    private val zone = ZoneId.of("America/Santiago")
    private val today: LocalDate = LocalDate.of(2026, 10, 22) // a Thursday
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(zone).toInstant(), zone)
    private val created = Instant.parse("2026-07-01T09:00:00Z")

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Instant = date.atTime(time).atZone(zone).toInstant()

    private fun completion(date: LocalDate, xp: Int, time: LocalTime = LocalTime.NOON, id: String = "c$date-$xp-$time") =
        CompletionRecord(
            questId = id,
            title = id,
            at = at(date, time),
            xp = xp,
            points = 1,
            difficulty = Difficulty.MODERATE,
            fear = Fear.NONE,
            estimatedMinutes = 30,
            skillIds = emptySet(),
        )

    private fun quest(
        id: String,
        due: LocalDate? = null,
        state: QuestState = QuestState.OPEN,
        completedAt: Instant? = null,
        urgency: Urgency = Urgency.NORMAL,
        fear: Fear = Fear.NONE,
    ) = Quest(
        id = id,
        title = id,
        dueDate = due,
        state = state,
        completedAt = completedAt,
        urgency = urgency,
        fear = fear,
        createdAt = created,
        updatedAt = created,
    )

    private fun skill(id: String, xp: Long, name: String = id) = Skill(id = id, name = name, totalXp = xp, createdAt = created)

    // ------------------------------------------------------------ activity

    @Test
    fun `the heatmap covers twelve weeks ending today, empty days included`() {
        val days = Standing.dailyActivity(QuestsState(), clock)
        assertEquals(Standing.HEATMAP_DAYS, days.size)
        assertEquals(today, days.last().date)
        assertEquals(today.minusDays(83), days.first().date)
        assertTrue(days.all { it.xp == 0 && it.completed == 0 })
    }

    @Test
    fun `a completion belongs to the local day it happened on`() {
        // 23:30 in Santiago is already tomorrow in UTC.
        val late = completion(today.minusDays(1), xp = 15, time = LocalTime.of(23, 30))
        val days = Standing.dailyActivity(QuestsState(completions = listOf(late)), clock)
        assertEquals(15, days.single { it.date == today.minusDays(1) }.xp)
        assertEquals(0, days.single { it.date == today }.xp)
    }

    @Test
    fun `a completion stamped after today waits for its day`() {
        val ahead = completion(today.plusDays(1), xp = 40)
        val days = Standing.dailyActivity(QuestsState(completions = listOf(ahead)), clock)
        assertEquals(0, days.sumOf { it.xp })
    }

    @Test
    fun `the current week is Monday to Sunday around today`() {
        val state = QuestsState(
            completions = listOf(
                completion(today.minusDays(3), xp = 10), // Monday
                completion(today, xp = 20),
                completion(today.minusDays(4), xp = 99), // last Sunday
            ),
        )
        val week = Standing.currentWeek(state, clock)
        assertEquals((0L..6L).map { today.minusDays(3).plusDays(it) }, week.map { it.date })
        assertEquals(listOf(10, 0, 0, 20, 0, 0, 0), week.map { it.xp })
    }

    @Test
    fun `week over week compares ISO weeks, not the last seven days`() {
        val state = QuestsState(
            completions = listOf(
                completion(today.minusDays(3), xp = 10), // this Monday
                completion(today, xp = 5),
                completion(today.minusDays(4), xp = 30), // last Sunday
                completion(today.minusDays(10), xp = 7), // last Monday
                completion(today.minusDays(11), xp = 100), // the week before
            ),
        )
        assertEquals(WeekComparison(thisWeek = 15, lastWeek = 37), Standing.weekOverWeek(state, clock))
    }

    // ------------------------------------------------------------ shading

    @Test
    fun `shades scale to the best day, but never below the floor`() {
        assertEquals(Standing.HEAT_MIN_SCALE, Standing.heatScale(listOf(DayActivity(today, 15, 1))))
        assertEquals(200, Standing.heatScale(listOf(DayActivity(today, 200, 3), DayActivity(today, 10, 1))))
    }

    @Test
    fun `heat levels step by quarters of the scale`() {
        val scale = 100
        fun level(xp: Int, completed: Int = 1) = Standing.heatLevel(DayActivity(today, xp, completed), scale)
        assertEquals(0, level(0, completed = 0))
        assertEquals(1, level(0, completed = 1), "a completion always shows, even one that paid nothing")
        assertEquals(1, level(1))
        assertEquals(1, level(25))
        assertEquals(2, level(26))
        assertEquals(3, level(75))
        assertEquals(4, level(76))
        assertEquals(4, level(100))
        assertEquals(4, level(500), "above the scale is still the top shade")
    }

    @Test
    fun `the streak runs back from today, and yesterday still counts`() {
        val state = QuestsState(
            completions = listOf(
                completion(today.minusDays(1), 10),
                completion(today.minusDays(2), 10),
                completion(today.minusDays(3), 10),
                completion(today.minusDays(5), 10),
            ),
        )
        assertEquals(3, Standing.streak(state, clock))
    }

    // ------------------------------------------------------------ today and overdue

    @Test
    fun `today's plate is open quests due by today plus those finished today`() {
        val state = QuestsState(
            quests = listOf(
                quest("open-today", due = today),
                quest("open-overdue", due = today.minusDays(2)),
                quest("open-tomorrow", due = today.plusDays(1)),
                quest("open-undated"),
                quest("done-today", due = today, state = QuestState.DONE, completedAt = at(today, LocalTime.of(9, 0))),
                quest("done-yesterday", due = today.minusDays(1), state = QuestState.DONE, completedAt = at(today.minusDays(1))),
                quest("failed", due = today, state = QuestState.FAILED),
            ).associateBy { it.id },
        )
        assertEquals(TodayProgress(done = 1, open = 2), Standing.todayProgress(state, clock))
        val plate = Standing.todayPlate(state, clock).map { it.id }
        assertEquals("done-today", plate.first(), "finished quests lead the checklist")
        assertEquals(setOf("open-today", "open-overdue"), plate.drop(1).toSet())
        assertEquals(3, plate.size)
    }

    @Test
    fun `the open part of the plate is in priority order`() {
        val state = QuestsState(
            quests = listOf(
                quest("calm", due = today),
                quest("critical", due = today, urgency = Urgency.CRITICAL),
                quest("late", due = today.minusDays(5)),
            ).associateBy { it.id },
        )
        // late: overdue 6 + 5×0.4 + normal 2 = 10; critical: today 5 + 7 = 12; calm: 5 + 2 = 7.
        assertEquals(listOf("critical", "late", "calm"), Standing.todayPlate(state, clock).map { it.id })
    }

    @Test
    fun `the worst overdue are ranked by priority score`() {
        val state = QuestsState(
            quests = listOf(
                quest("a", due = today.minusDays(1)),
                quest("b", due = today.minusDays(11)),
                quest("c", due = today.minusDays(2), fear = Fear.SEVERE),
                quest("d", due = today.minusDays(3)),
                quest("not-yet", due = today.plusDays(1)),
            ).associateBy { it.id },
        )
        val summary = Standing.summarise(state, clock)
        assertEquals(4, summary.overdueCount)
        // b: 6 + 10×0.4 + 2 = 12; c: 6 + 0.8 + 2 + 2.2 = 11; d: 6 + 1.2 + 2 = 9.2; a: 8.4.
        assertEquals(listOf("b", "c", "d"), summary.worstOverdue.map { it.id })
    }

    @Test
    fun `days overdue are whole calendar days, never negative`() {
        assertEquals(11, Standing.daysOverdue(quest("x", due = today.minusDays(11)), clock))
        assertEquals(0, Standing.daysOverdue(quest("x", due = today), clock))
        assertEquals(0, Standing.daysOverdue(quest("x", due = today.plusDays(3)), clock))
        assertEquals(0, Standing.daysOverdue(quest("x"), clock))
    }

    // ------------------------------------------------------------ skills

    @Test
    fun `the closest skills are those with least XP to go, nearest first`() {
        // Level 2 starts at 303 XP and level 3 at 579.
        val skills = listOf(
            skill("fresh", 0), // 303 to go
            skill("almost", 570), // 9 to go
            skill("halfway", 450), // 129 to go
            skill("far", 303), // 276 to go
        )
        assertEquals(listOf("almost", "halfway", "far"), Standing.closestToLevel(skills).map { it.id })
    }

    @Test
    fun `equal distances fall to the name, so the card does not flicker`() {
        val skills = listOf(skill("2", 0, name = "Writing"), skill("1", 0, name = "Cooking"))
        assertEquals(listOf("Cooking", "Writing"), Standing.closestToLevel(skills).map { it.name })
    }

    // ------------------------------------------------------------ where a quest opens

    @Test
    fun `a quest asked for from Standing opens in the list that holds it`() {
        assertEquals(QuestView.TODAY, Views.home(quest("x", due = today), clock))
        assertEquals(QuestView.TODAY, Views.home(quest("x", due = today.minusDays(4)), clock))
        assertEquals(QuestView.TOMORROW, Views.home(quest("x", due = today.plusDays(1)), clock))
        assertEquals(QuestView.NEXT, Views.home(quest("x", due = today.plusDays(9)), clock))
        assertEquals(QuestView.NO_DUE_DATE, Views.home(quest("x"), clock))
        assertEquals(QuestView.COMPLETED, Views.home(quest("x", state = QuestState.DONE), clock))
        assertEquals(QuestView.FAILED, Views.home(quest("x", state = QuestState.FAILED), clock))
    }
}
