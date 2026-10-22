package cl.nheox.quests.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {

    private val zone = ZoneId.of("America/Santiago")
    private val today: LocalDate = LocalDate.of(2026, 10, 22)
    private val now: Instant = today.atTime(12, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val created = Instant.parse("2026-07-01T09:00:00Z")

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun quest(
        id: String,
        reminders: List<Reminder>,
        due: LocalDate? = null,
        dueTime: LocalTime? = null,
        state: QuestState = QuestState.OPEN,
    ) = Quest(
        id = id,
        title = id,
        reminders = reminders,
        dueDate = due,
        dueTime = dueTime,
        state = state,
        createdAt = created,
        updatedAt = created,
    )

    @Test
    fun `an offset reminder fires that long before the due moment`() {
        val q = quest("q", listOf(Reminder.BeforeDue(30)), due = today, dueTime = LocalTime.of(15, 0))
        assertEquals(at(today, 14, 30), Reminders.moment(q, q.reminders.single(), zone))
    }

    @Test
    fun `an offset reminder on a quest without a due date has nothing to be before`() {
        val q = quest("q", listOf(Reminder.BeforeDue(30)))
        assertNull(Reminders.moment(q, q.reminders.single(), zone))
    }

    @Test
    fun `a fixed reminder fires at its own date and time`() {
        val reminder = Reminder.At(today.plusDays(2), LocalTime.of(9, 15))
        assertEquals(at(today.plusDays(2), 9, 15), Reminders.moment(quest("q", listOf(reminder)), reminder, zone))
    }

    @Test
    fun `next is the earliest reminder still to come`() {
        val q = quest(
            "q",
            listOf(
                Reminder.At(today, LocalTime.of(9, 0)), // passed
                Reminder.At(today.plusDays(1), LocalTime.of(8, 0)),
                Reminder.At(today, LocalTime.of(18, 0)),
            ),
        )
        assertEquals(at(today, 18), Reminders.next(q, now, zone))
    }

    @Test
    fun `a reminder due this very instant still counts as to come`() {
        val q = quest("q", listOf(Reminder.At(today, LocalTime.NOON)))
        assertEquals(now, Reminders.next(q, now, zone))
    }

    @Test
    fun `finished quests and quests whose reminders have all passed have none to come`() {
        val passed = quest("p", listOf(Reminder.At(today, LocalTime.of(8, 0))))
        val done = quest("d", listOf(Reminder.At(today.plusDays(1), LocalTime.NOON)), state = QuestState.DONE)
        assertNull(Reminders.next(passed, now, zone))
        assertNull(Reminders.next(done, now, zone))
    }

    @Test
    fun `the Reminders view holds what is still to come, soonest first`() {
        val soon = quest("soon", listOf(Reminder.At(today, LocalTime.of(13, 0))))
        val later = quest("later", listOf(Reminder.BeforeDue(60)), due = today.plusDays(3), dueTime = LocalTime.of(10, 0))
        val passed = quest("passed", listOf(Reminder.At(today, LocalTime.of(8, 0))))
        val none = quest("none", emptyList())
        val pool = listOf(later, passed, none, soon)

        val inView = Views.filter(QuestView.REMINDERS, pool, clock)
        assertEquals(setOf("soon", "later"), inView.map { it.id }.toSet())

        val sorted = Sorting.sort(inView, Views.defaultSort(QuestView.REMINDERS), clock = clock)
        assertEquals(listOf("soon", "later"), sorted.map { it.id })
    }

    @Test
    fun `sorting by next reminder puts quests without one last, in any view`() {
        val with = quest("with", listOf(Reminder.At(today.plusDays(1), LocalTime.NOON)))
        val without = quest("without", emptyList())
        val sorted = Sorting.sort(listOf(without, with), SortOrder(SortKey.REMINDER, SortDirection.ASCENDING), clock = clock)
        assertEquals(listOf("with", "without"), sorted.map { it.id })
    }
}
