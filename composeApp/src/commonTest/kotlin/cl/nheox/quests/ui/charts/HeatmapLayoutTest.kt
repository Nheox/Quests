package cl.nheox.quests.ui.charts

import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.DayActivity
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class HeatmapLayoutTest {

    private fun days(last: LocalDate, count: Int = 84): List<DayActivity> =
        (count - 1 downTo 0).map { DayActivity(last.minusDays(it.toLong()), xp = 0, completed = 0) }

    @Test
    fun `eighty-four days ending on a Thursday fill thirteen week columns`() {
        val layout = heatmapLayout(days(LocalDate.of(2026, 10, 22)))
        assertEquals(13, layout.columns)
        // The oldest day is a Friday, at the foot of the first column…
        val first = layout.cells.first()
        assertEquals(0, first.column)
        assertEquals(4, first.row)
        // …and today, a Thursday, sits in the last.
        val last = layout.cells.last()
        assertEquals(12, last.column)
        assertEquals(3, last.row)
    }

    @Test
    fun `eighty-four days ending on a Sunday fill exactly twelve`() {
        val layout = heatmapLayout(days(LocalDate.of(2026, 10, 25)))
        assertEquals(12, layout.columns)
        assertEquals(0, layout.cells.first().row)
    }

    @Test
    fun `each month is named over the column its first day falls in`() {
        val layout = heatmapLayout(days(LocalDate.of(2026, 10, 22)))
        // Friday 31 Jul to 22 Oct: 1 Aug falls in the first column itself, so
        // July gets no label of its own — it would sit on top of August's.
        assertEquals(listOf(Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER), layout.months.map { it.month })
        assertEquals(listOf(0, 5, 9), layout.months.map { it.column })
    }

    @Test
    fun `the opening month is named when the next one starts far enough away`() {
        // Monday 3 Aug to Sunday 25 Oct: September first starts four columns
        // in, so the grid's opening month is named too.
        val layout = heatmapLayout(days(LocalDate.of(2026, 10, 25)))
        assertEquals(listOf(Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER), layout.months.map { it.month })
        assertEquals(listOf(0, 4, 8), layout.months.map { it.column })
    }

    @Test
    fun `the pitch fits the width it is given, within its bounds`() {
        assertEquals(20.dp, heatmapPitch(available = 32.dp + 260.dp, columns = 13))
        assertEquals(12.dp, heatmapPitch(available = 100.dp, columns = 13), "never smaller than legible")
        assertEquals(26.dp, heatmapPitch(available = 2000.dp, columns = 13), "never bloated")
    }

    @Test
    fun `no days, no grid`() {
        val layout = heatmapLayout(emptyList())
        assertEquals(0, layout.columns)
        assertEquals(emptyList(), layout.cells)
    }
}
