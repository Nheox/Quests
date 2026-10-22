package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the duration and repeat pickers accept when a value is typed rather than chosen. */
class QuickAddPickerInputTest {

    @Test
    fun `minutes are read as quick-add reads a duration`() {
        assertEquals(37, QuickAdd.parseMinutes("37"))
        assertEquals(45, QuickAdd.parseMinutes("45m"))
        assertEquals(45, QuickAdd.parseMinutes("45 min"))
        assertEquals(90, QuickAdd.parseMinutes("1h 30m"))
        assertEquals(120, QuickAdd.parseMinutes("2h"))
        assertNull(QuickAdd.parseMinutes(""))
        assertNull(QuickAdd.parseMinutes("soon"))
        assertNull(QuickAdd.parseMinutes("0"))
    }

    @Test
    fun `repetitions are read with or without the word every`() {
        assertEquals(Repetition(RepeatUnit.DAY, 3), QuickAdd.parseRepetition("3 days"))
        assertEquals(Repetition(RepeatUnit.WEEK, 2), QuickAdd.parseRepetition("every 2 weeks"))
        assertEquals(Repetition(RepeatUnit.WEEK, 1), QuickAdd.parseRepetition("week"))
        assertEquals(Repetition(RepeatUnit.DAY, 1), QuickAdd.parseRepetition("daily"))
        assertNull(QuickAdd.parseRepetition(""))
        assertNull(QuickAdd.parseRepetition("sometimes"))
    }
}
