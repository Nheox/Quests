package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Table-driven tests for the priority score (§5.6).
 *
 * As with BalanceTest, every expected value is written out by hand from the
 * published weights. The order of every open list hangs off these numbers, so
 * a weight that drifts should fail here before anyone notices a list feeling
 * wrong.
 */
class PriorityTest {

    // Thursday 15 October 2026, 10:00 in UTC.
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private val now: Instant = Instant.parse("2026-10-15T10:00:00Z")

    private fun quest(
        id: String,
        title: String = id,
        urgency: Urgency = Urgency.WHENEVER,
        fear: Fear = Fear.NONE,
        due: LocalDate? = null,
        dueTime: LocalTime? = null,
        minutes: Int? = null,
        pin: Int? = null,
        created: Instant = now,
    ) = Quest(
        id = id,
        title = title,
        urgency = urgency,
        fear = fear,
        dueDate = due,
        dueTime = dueTime,
        estimatedMinutes = minutes,
        manualOrder = pin,
        createdAt = created,
        updatedAt = created,
    )

    @Test
    fun `the published constants`() {
        data class Case(val name: String, val actual: Double, val expected: Double)

        listOf(
            Case("OVERDUE_BASE", Priority.OVERDUE_BASE, 6.0),
            Case("OVERDUE_PER_DAY", Priority.OVERDUE_PER_DAY, 0.4),
            Case("OVERDUE_DAYS_CAP", Priority.OVERDUE_DAYS_CAP.toDouble(), 10.0),
            Case("DUE_TODAY", Priority.DUE_TODAY, 5.0),
            Case("DUE_TOMORROW", Priority.DUE_TOMORROW, 3.0),
            Case("DUE_WITHIN_THREE_DAYS", Priority.DUE_WITHIN_THREE_DAYS, 2.0),
            Case("DUE_WITHIN_A_WEEK", Priority.DUE_WITHIN_A_WEEK, 1.0),
            Case("AGE_PER_DAY", Priority.AGE_PER_DAY, 0.05),
            Case("AGE_CAP", Priority.AGE_CAP, 3.0),
        ).forEach { case -> assertEquals(case.expected, case.actual, case.name) }
        assertEquals(listOf(0.0, 1.0, 2.0, 4.0, 7.0), Priority.URGENCY_WEIGHTS)
        assertEquals(listOf(0.0, 0.4, 0.9, 1.5, 2.2), Priority.FEAR_WEIGHTS)
    }

    @Test
    fun `urgency weights are the published ones`() {
        val expected = mapOf(
            Urgency.WHENEVER to 0.0,
            Urgency.LOW to 1.0,
            Urgency.NORMAL to 2.0,
            Urgency.HIGH to 4.0,
            Urgency.CRITICAL to 7.0,
        )
        assertEquals(Urgency.entries.toSet(), expected.keys)
        expected.forEach { (urgency, weight) -> assertEquals(weight, Priority.urgencyWeight(urgency), "$urgency") }
    }

    @Test
    fun `fear weights are the published ones and push upward`() {
        val expected = mapOf(
            Fear.NONE to 0.0,
            Fear.MILD to 0.4,
            Fear.MODERATE to 0.9,
            Fear.STRONG to 1.5,
            Fear.SEVERE to 2.2,
        )
        assertEquals(Fear.entries.toSet(), expected.keys)
        expected.forEach { (fear, weight) -> assertEquals(weight, Priority.fearWeight(fear), "$fear") }
    }

    @Test
    fun `due weight covers every bucket and boundary`() {
        data class Case(val label: String, val date: LocalDate?, val time: LocalTime?, val expected: Double)

        val cases = listOf(
            Case("no due date", null, null, 0.0),
            // Overdue agrees with Dates.isOverdue: due today at 09:00 is late at
            // 10:00, by zero whole days.
            Case("overdue by 0 days (time passed)", today, LocalTime.of(9, 0), 6.0),
            Case("overdue by 0 days, to the minute", today, LocalTime.of(9, 59), 6.0),
            Case("due today, time still ahead", today, LocalTime.of(11, 0), 5.0),
            Case("due today at this very minute", today, LocalTime.of(10, 0), 5.0),
            Case("due today, no time", today, null, 5.0),
            Case("overdue by 1 day", today.minusDays(1), null, 6.4),
            Case("overdue by 1 day, late in that day", today.minusDays(1), LocalTime.of(23, 30), 6.4),
            Case("overdue by 2 days", today.minusDays(2), null, 6.8),
            Case("overdue by 10 days", today.minusDays(10), null, 10.0),
            Case("overdue by 11 days, capped", today.minusDays(11), null, 10.0),
            Case("overdue by 15 days, capped", today.minusDays(15), null, 10.0),
            Case("tomorrow", today.plusDays(1), null, 3.0),
            Case("tomorrow, early", today.plusDays(1), LocalTime.of(0, 5), 3.0),
            Case("in 2 days", today.plusDays(2), null, 2.0),
            Case("in 3 days", today.plusDays(3), null, 2.0),
            Case("in 4 days", today.plusDays(4), null, 1.0),
            Case("in 7 days", today.plusDays(7), null, 1.0),
            Case("in 8 days", today.plusDays(8), null, 0.0),
            Case("in a year", today.plusYears(1), null, 0.0),
        )

        cases.forEach { case ->
            assertEquals(case.expected, Priority.dueWeight(case.date, case.time, clock), case.label)
        }
    }

    @Test
    fun `age ramps slowly and stops at the ceiling`() {
        data class Case(val daysOld: Long, val expected: Double)

        val cases = listOf(
            Case(0, 0.0),
            Case(1, 0.05),
            Case(7, 0.35),
            Case(20, 1.0),
            Case(59, 2.95),
            Case(60, 3.0),
            Case(61, 3.0),
            Case(400, 3.0),
        )

        cases.forEach { case ->
            assertEquals(case.expected, Priority.ageWeight(now.minusSeconds(case.daysOld * 86_400), clock), "${case.daysOld} days")
        }
        // Another device's fast clock can stamp a creation ahead of this one's
        // now; that is a new quest, not a negative age.
        assertEquals(0.0, Priority.ageWeight(now.plusSeconds(3 * 86_400), clock))
    }

    @Test
    fun `days are whole calendar days in the clock's own zone`() {
        // 01:00 UTC on the 16th is still 22:00 on the 15th three hours west.
        val west = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneId.of("-03:00"))
        assertEquals(LocalDate.of(2026, 10, 15), west.today())

        assertEquals(5.0, Priority.dueWeight(LocalDate.of(2026, 10, 15), null, west), "still today there")
        assertEquals(3.0, Priority.dueWeight(LocalDate.of(2026, 10, 16), null, west), "tomorrow there")
        // Created 02:00 UTC on the 14th: 23:00 on the 13th locally, so two days old.
        assertEquals(0.1, Priority.ageWeight(Instant.parse("2026-10-14T02:00:00Z"), west))
        // Made at 23:59 local the night before: a day old, though not 24 hours.
        assertEquals(0.05, Priority.ageWeight(Instant.parse("2026-10-15T02:59:00Z"), west))

        // A timed due date is read against the local wall clock too: 22:00
        // there, so 21:00 has passed and 23:00 has not.
        assertEquals(6.0, Priority.dueWeight(LocalDate.of(2026, 10, 15), LocalTime.of(21, 0), west), "late by the hour")
        assertEquals(5.0, Priority.dueWeight(LocalDate.of(2026, 10, 15), LocalTime.of(23, 0), west), "later tonight")
        assertEquals(6.4, Priority.dueWeight(LocalDate.of(2026, 10, 14), LocalTime.of(23, 30), west), "last night")
    }

    @Test
    fun `a daylight-saving change neither adds nor loses a day`() {
        data class Case(val label: String, val now: String, val created: String, val due: LocalDate, val age: Double, val dueW: Double)

        val berlin = ZoneId.of("Europe/Berlin")
        val cases = listOf(
            // Autumn: the night of 24–25 October 2026 is 25 hours long.
            Case("autumn", "2026-10-25T09:00:00Z", "2026-10-24T21:30:00Z", LocalDate.of(2026, 10, 26), 0.05, 3.0),
            Case("autumn, a week late", "2026-10-25T09:00:00Z", "2026-10-18T09:00:00Z", LocalDate.of(2026, 10, 18), 0.35, 8.8),
            // Spring: the night of 28–29 March 2026 is 23 hours long.
            Case("spring", "2026-03-29T10:00:00Z", "2026-03-22T11:00:00Z", LocalDate.of(2026, 3, 28), 0.35, 6.4),
            Case("spring, due in a week", "2026-03-29T10:00:00Z", "2026-03-28T22:30:00Z", LocalDate.of(2026, 4, 5), 0.05, 1.0),
        )

        cases.forEach { case ->
            val clock = Clock.fixed(Instant.parse(case.now), berlin)
            assertEquals(case.age, Priority.ageWeight(Instant.parse(case.created), clock), "${case.label}: age")
            assertEquals(case.dueW, Priority.dueWeight(case.due, null, clock), "${case.label}: due")
        }
    }

    @Test
    fun `the total is the sum of the four parts`() {
        data class Case(val quest: Quest, val expected: PriorityBreakdown, val total: Double)

        val cases = listOf(
            // The worked example: high urgency, due today, mild fear, brand new.
            Case(
                quest("a", urgency = Urgency.HIGH, fear = Fear.MILD, due = today),
                PriorityBreakdown(urgency = 4.0, due = 5.0, fear = 0.4, age = 0.0),
                9.4,
            ),
            Case(quest("b"), PriorityBreakdown(0.0, 0.0, 0.0, 0.0), 0.0),
            Case(
                quest(
                    "c",
                    urgency = Urgency.CRITICAL,
                    fear = Fear.SEVERE,
                    due = today.minusDays(30),
                    created = now.minusSeconds(90 * 86_400),
                ),
                PriorityBreakdown(urgency = 7.0, due = 10.0, fear = 2.2, age = 3.0),
                22.2,
            ),
            Case(
                quest("d", urgency = Urgency.NORMAL, fear = Fear.STRONG, due = today.plusDays(5), created = now.minusSeconds(7 * 86_400)),
                PriorityBreakdown(urgency = 2.0, due = 1.0, fear = 1.5, age = 0.35),
                4.85,
            ),
        )

        cases.forEach { case ->
            val breakdown = Priority.breakdown(case.quest, clock)
            assertEquals(case.expected, breakdown, case.quest.id)
            assertEquals(case.total, breakdown.total, case.quest.id)
            assertEquals(case.total, Priority.score(case.quest, clock), case.quest.id)
        }
    }

    @Test
    fun `an old quest can never outrank a genuine emergency`() {
        val ancient = quest("ancient", urgency = Urgency.LOW, fear = Fear.SEVERE, created = now.minusSeconds(1000 * 86_400))
        val emergency = quest("emergency", urgency = Urgency.CRITICAL, due = today)
        assertEquals(listOf("emergency", "ancient"), Priority.order(listOf(ancient, emergency), clock).map { it.id })
    }

    @Test
    fun `difficulty is absent from the score`() {
        Difficulty.entries.forEach { difficulty ->
            assertEquals(2.0, Priority.score(quest("x", urgency = Urgency.NORMAL).copy(difficulty = difficulty), clock))
        }
    }

    @Test
    fun `ties break by shorter estimate, then title ignoring case, then id`() {
        // All four score 2.0 (Normal urgency, nothing else).
        val quests = listOf(
            quest("long", title = "b", urgency = Urgency.NORMAL, minutes = 60),
            quest("q2", title = "Same", urgency = Urgency.NORMAL, minutes = 30),
            quest("q1", title = "same", urgency = Urgency.NORMAL, minutes = null),
            quest("short", title = "z", urgency = Urgency.NORMAL, minutes = 15),
            quest("alpha", title = "Alpha", urgency = Urgency.NORMAL, minutes = 30),
        )
        // No estimate counts as the 30-minute reference, so it ties with 30.
        val expected = listOf("short", "alpha", "q1", "q2", "long")
        assertEquals(expected, Priority.order(quests, clock).map { it.id })
        assertEquals(expected, Priority.order(quests.reversed(), clock).map { it.id }, "the order is total")
        assertEquals(expected, Priority.order(quests.shuffled(kotlin.random.Random(7)), clock).map { it.id })
    }

    @Test
    fun `top pins come first, bottom pins last, and the automatic block sits between`() {
        val quests = listOf(
            quest("low", urgency = Urgency.LOW),
            quest("bottom-5", urgency = Urgency.CRITICAL, pin = 5),
            quest("top-2", urgency = Urgency.WHENEVER, pin = -2),
            quest("high", urgency = Urgency.CRITICAL),
            quest("bottom-1", urgency = Urgency.WHENEVER, pin = 1),
            // 0 is the anchor itself; a legacy "move to top" could write it, and
            // it reads as a top pin.
            quest("top-0", urgency = Urgency.WHENEVER, pin = 0),
            quest("top-1", urgency = Urgency.WHENEVER, pin = -1),
        )
        assertEquals(
            listOf("top-2", "top-1", "top-0", "high", "low", "bottom-1", "bottom-5"),
            Priority.order(quests, clock).map { it.id },
        )
    }

    @Test
    fun `quests sharing a pin value fall back to the automatic order`() {
        val quests = listOf(
            quest("calm", urgency = Urgency.LOW, pin = -1),
            quest("urgent", urgency = Urgency.HIGH, pin = -1),
        )
        assertEquals(listOf("urgent", "calm"), Priority.order(quests, clock).map { it.id })
    }

    @Test
    fun `pin positions read from the stored value`() {
        data class Case(val manualOrder: Int?, val expected: Pin)

        listOf(
            Case(null, Pin.NONE),
            Case(-7, Pin.TOP),
            Case(-1, Pin.TOP),
            Case(0, Pin.TOP),
            Case(1, Pin.BOTTOM),
            Case(42, Pin.BOTTOM),
        ).forEach { case ->
            assertEquals(case.expected, Pin.of(case.manualOrder), "${case.manualOrder}")
            assertEquals(case.expected, quest("x", pin = case.manualOrder).pin, "${case.manualOrder}")
        }
    }
}
