package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ViewsTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun quest(
        id: String,
        due: LocalDate? = null,
        dueTime: LocalTime? = null,
        state: QuestState = QuestState.OPEN,
        completedAt: Instant? = null,
    ) = Quest(
        id = id,
        title = id,
        dueDate = due,
        dueTime = dueTime,
        state = state,
        completedAt = completedAt,
        createdAt = created,
        updatedAt = created,
    )

    private val all = listOf(
        quest("overdue", due = today.minusDays(2)),
        quest("today", due = today),
        quest("tomorrow", due = today.plusDays(1)),
        quest("next", due = today.plusDays(5)),
        quest("undated"),
        quest("done", due = today, state = QuestState.DONE, completedAt = clock.now()),
        quest("failed", due = today, state = QuestState.FAILED, completedAt = clock.now()),
        quest("old-done", due = today.minusDays(30), state = QuestState.DONE, completedAt = created),
    )

    private fun ids(view: QuestView) = Views.filter(view, all, clock).map { it.id }.toSet()

    @Test
    fun `today includes everything already due, which is what makes the buckets earn their headers`() {
        assertEquals(setOf("overdue", "today"), ids(QuestView.TODAY))
    }

    @Test
    fun `tomorrow is exactly tomorrow`() {
        assertEquals(setOf("tomorrow"), ids(QuestView.TOMORROW))
    }

    @Test
    fun `overdue excludes today`() {
        assertEquals(setOf("overdue"), ids(QuestView.OVERDUE))
    }

    @Test
    fun `next is the horizon beyond tomorrow`() {
        assertEquals(setOf("next"), ids(QuestView.NEXT))
    }

    @Test
    fun `all and plan show every open quest`() {
        val open = setOf("overdue", "today", "tomorrow", "next", "undated")
        assertEquals(open, ids(QuestView.ALL))
        assertEquals(open, ids(QuestView.PLAN))
    }

    @Test
    fun `completed and failed are separate lists`() {
        assertEquals(setOf("done", "old-done"), ids(QuestView.COMPLETED))
        assertEquals(setOf("failed"), ids(QuestView.FAILED))
    }

    @Test
    fun `last seven days is bounded by completion date`() {
        assertEquals(setOf("done", "failed"), ids(QuestView.LAST_SEVEN_DAYS))
    }

    @Test
    fun `a due time decides whether today is already overdue`() {
        // At noon, 09:00 today has passed and 18:00 has not.
        val past = quest("past", due = today, dueTime = LocalTime.of(9, 0))
        val future = quest("future", due = today, dueTime = LocalTime.of(18, 0))
        val overdue = Views.filter(QuestView.OVERDUE, listOf(past, future), clock).map { it.id }
        assertEquals(listOf("past"), overdue)
    }

    @Test
    fun `a date-only quest is not overdue until the day is over`() {
        assertTrue(!Dates.isOverdue(today, null, clock))
        assertTrue(Dates.isOverdue(today.minusDays(1), null, clock))
    }

    @Test
    fun `every view has a default sort`() {
        QuestView.entries.forEach { view ->
            // Exhaustive, so adding a view cannot leave it with no ordering.
            Views.defaultSort(view)
        }
        assertEquals(SortKey.URGENCY, Views.defaultSort(QuestView.TODAY).key)
        assertEquals(SortKey.DUE_DATE, Views.defaultSort(QuestView.NEXT).key)
    }
}
