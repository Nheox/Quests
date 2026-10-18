package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import java.time.LocalDate
import java.time.LocalTime

class RepetitionTest {

    @Test
    fun `daily and weekly advance the date and keep the time`() {
        val date = LocalDate.of(2026, 10, 15)
        val time = LocalTime.of(9, 30)

        assertEquals(LocalDate.of(2026, 10, 16) to time, Repetition.DAILY.next(date, time))
        assertEquals(LocalDate.of(2026, 10, 22) to time, Repetition.WEEKLY.next(date, time))
        assertEquals(LocalDate.of(2026, 10, 29) to time, Repetition(RepeatUnit.WEEK, 2).next(date, time))
    }

    @Test
    fun `monthly clamps to the length of the target month`() {
        // 31 January plus one month is 28 February, not the 31st of a month that
        // does not have one. java.time already does this; the test pins it so a
        // future hand-rolled calendar cannot regress it.
        assertEquals(
            LocalDate.of(2026, 2, 28) to null,
            Repetition.MONTHLY.next(LocalDate.of(2026, 1, 31), null),
        )
        assertEquals(
            LocalDate.of(2028, 2, 29) to null,
            Repetition.MONTHLY.next(LocalDate.of(2028, 1, 31), null),
        )
    }

    @Test
    fun `yearly handles the 29th of February`() {
        assertEquals(
            LocalDate.of(2029, 2, 28) to null,
            Repetition.YEARLY.next(LocalDate.of(2028, 2, 29), null),
        )
    }

    @Test
    fun `hourly rolls over midnight`() {
        val date = LocalDate.of(2026, 10, 15)
        assertEquals(
            LocalDate.of(2026, 10, 15) to LocalTime.of(23, 0),
            Repetition.HOURLY.next(date, LocalTime.of(22, 0)),
        )
        assertEquals(
            LocalDate.of(2026, 10, 16) to LocalTime.of(0, 30),
            Repetition.HOURLY.next(date, LocalTime.of(23, 30)),
        )
        assertEquals(
            LocalDate.of(2026, 10, 16) to LocalTime.of(5, 15),
            Repetition(RepeatUnit.HOUR, 8).next(date, LocalTime.of(21, 15)),
        )
    }

    @Test
    fun `hourly on a quest with no time advances by a day instead of doing nothing`() {
        // There is nowhere to put the hour, and silently not advancing would
        // leave a repeating quest stuck on the same date forever.
        val (date, time) = Repetition.HOURLY.next(LocalDate.of(2026, 10, 15), null)
        assertEquals(LocalDate.of(2026, 10, 16), date)
        assertNull(time)
    }

    @Test
    fun `an interval below one is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { Repetition(RepeatUnit.DAY, 0) }
        assertFailsWith<IllegalArgumentException> { Repetition(RepeatUnit.WEEK, -3) }
    }
}
