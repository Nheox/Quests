package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.LocalTime

/**
 * The quick-add grammar.
 *
 * The governing rule, and the reason for the last few tests here: an
 * unparseable line must still become a quest. A capture box that rejects input
 * is a capture box that stops being used, and losing the thought is worse than
 * losing the metadata.
 */
class QuickAddTest {

    // A Thursday, so weekday resolution has somewhere interesting to go.
    private val today = LocalDate.of(2026, 10, 15)

    private fun parse(input: String) = QuickAdd.parse(input, today)

    @Test
    fun `the documented example parses completely`() {
        val result = parse("write methods tomorrow 3pm #thesis !! ~2h")
        assertEquals("write methods", result.title)
        assertEquals(LocalDate.of(2026, 10, 16), result.dueDate)
        assertEquals(LocalTime.of(15, 0), result.dueTime)
        assertEquals(listOf("thesis"), result.skillNames)
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals(120, result.estimatedMinutes)
    }

    @Test
    fun `a bare line is just a title`() {
        val result = parse("buy milk")
        assertEquals("buy milk", result.title)
        assertNull(result.dueDate)
        assertNull(result.difficulty)
        assertTrue(result.skillNames.isEmpty())
    }

    @Test
    fun `skills, including quoted multi-word ones`() {
        val result = parse("""tidy up #lab #"deep work" #data-analysis""")
        assertEquals("tidy up", result.title)
        // Compared as a set: quoted skills are extracted before bare ones, and
        // the order they end up in is not part of the contract.
        assertEquals(setOf("deep work", "lab", "data analysis"), result.skillNames.toSet())
    }

    @Test
    fun `stars set difficulty and carets set fear`() {
        assertEquals(Difficulty.TRIVIAL, parse("thing *").difficulty)
        assertEquals(Difficulty.LOW, parse("thing ***").difficulty)
        assertEquals(Difficulty.EXTREME, parse("thing *******").difficulty)
        assertEquals(Fear.MILD, parse("thing ^").fear)
        assertEquals(Fear.SEVERE, parse("thing ^^^^").fear)
        assertEquals("thing", parse("thing ^^^^").title)
    }

    @Test
    fun `bangs set urgency`() {
        assertEquals(Urgency.HIGH, parse("thing !").urgency)
        assertEquals(Urgency.CRITICAL, parse("thing !!").urgency)
    }

    @Test
    fun `punctuation attached to a word is not a rating`() {
        // "call mom!" is excitement, not urgency. Only a standalone token counts.
        val result = parse("call mom!")
        assertNull(result.urgency)
        assertEquals("call mom!", result.title)
    }

    @Test
    fun `spelled-out ratings`() {
        assertEquals(Difficulty.VERY_HIGH, parse("thing d:very-high").difficulty)
        assertEquals(Difficulty.VERY_HIGH, parse("thing d:veryhigh").difficulty)
        assertEquals(Fear.STRONG, parse("thing f:strong").fear)
        assertEquals(Urgency.WHENEVER, parse("thing u:whenever").urgency)
        assertEquals("thing", parse("thing d:high").title)
    }

    @Test
    fun `durations in every documented shape`() {
        assertEquals(120, parse("x ~2h").estimatedMinutes)
        assertEquals(90, parse("x ~90m").estimatedMinutes)
        assertEquals(90, parse("x ~1h30m").estimatedMinutes)
        assertEquals(45, parse("x ~45").estimatedMinutes)
        assertEquals(150, parse("x ~2h30m").estimatedMinutes)
        assertEquals("x", parse("x ~2h").title)
    }

    @Test
    fun `relative and absolute dates`() {
        assertEquals(today, parse("x today").dueDate)
        assertEquals(today.plusDays(1), parse("x tomorrow").dueDate)
        assertEquals(today.plusDays(3), parse("x +3d").dueDate)
        assertEquals(today.plusWeeks(2), parse("x +2w").dueDate)
        assertEquals(today.plusMonths(1), parse("x +1m").dueDate)
        assertEquals(LocalDate.of(2026, 10, 20), parse("x 2026-10-20").dueDate)
        // Day first, which is how Chile writes dates.
        assertEquals(LocalDate.of(2026, 10, 20), parse("x 20/10").dueDate)
        assertEquals(LocalDate.of(2027, 1, 5), parse("x 05/01").dueDate)
    }

    @Test
    fun `next week means the start of next week`() {
        // 15 October 2026 is a Thursday; its week starts Monday the 12th.
        assertEquals(LocalDate.of(2026, 10, 19), parse("x next week").dueDate)
    }

    @Test
    fun `weekdays resolve forwards`() {
        assertEquals(LocalDate.of(2026, 10, 19), parse("x monday").dueDate)
        assertEquals(LocalDate.of(2026, 10, 16), parse("x fri").dueDate)
        // Today's own weekday means today, unless "next" says otherwise.
        assertEquals(today, parse("x thursday").dueDate)
        assertEquals(today.plusDays(7), parse("x next thursday").dueDate)
    }

    @Test
    fun `times, with and without a date`() {
        assertEquals(LocalTime.of(15, 0), parse("x 3pm").dueTime)
        assertEquals(LocalTime.of(15, 30), parse("x 3:30pm").dueTime)
        assertEquals(LocalTime.of(9, 30), parse("x 9:30am").dueTime)
        assertEquals(LocalTime.of(0, 0), parse("x 12am").dueTime)
        assertEquals(LocalTime.of(12, 0), parse("x 12pm").dueTime)
        assertEquals(LocalTime.of(15, 0), parse("x 15:00").dueTime)
        assertEquals(LocalTime.of(15, 0), parse("x at 15:00").dueTime)
        // A time with no date means today — which is what anyone typing "3pm" means.
        assertEquals(today, parse("x 3pm").dueDate)
    }

    @Test
    fun `an impossible time is left in the title rather than guessed at`() {
        val result = parse("x 25:99")
        assertNull(result.dueTime)
        assertTrue(result.title.contains("25:99"))
    }

    @Test
    fun `repetition`() {
        assertEquals(Repetition(RepeatUnit.DAY, 1), parse("x every day").repetition)
        assertEquals(Repetition(RepeatUnit.WEEK, 2), parse("x every 2 weeks").repetition)
        assertEquals(Repetition(RepeatUnit.MONTH, 1), parse("x monthly").repetition)
        assertEquals(Repetition(RepeatUnit.HOUR, 1), parse("x hourly").repetition)
        assertEquals(Repetition(RepeatUnit.YEAR, 3), parse("x every 3 years").repetition)
        assertEquals("x", parse("x every 2 weeks").title)
    }

    @Test
    fun `Spanish keywords`() {
        assertEquals(today, parse("llamar hoy").dueDate)
        assertEquals("llamar", parse("llamar hoy").title)
        assertEquals(today.plusDays(1), parse("llamar mañana").dueDate)
        assertEquals(today.plusDays(1), parse("llamar manana").dueDate)
        assertEquals(LocalDate.of(2026, 10, 19), parse("x lunes").dueDate)
        assertEquals(Repetition(RepeatUnit.WEEK, 2), parse("x cada 2 semanas").repetition)
        assertEquals(Repetition(RepeatUnit.DAY, 1), parse("x diario").repetition)
        assertEquals(LocalTime.of(15, 0), parse("x a las 15:00").dueTime)
        assertEquals(Urgency.CRITICAL, parse("x u:crítica").urgency)
        assertEquals(Urgency.CRITICAL, parse("x u:critica").urgency)
    }

    @Test
    fun `everything at once`() {
        val result = parse("#thesis prepare the defence slides next monday 9am ***** ^^^ ! ~3h every 2 weeks")
        assertEquals("prepare the defence slides", result.title)
        assertEquals(listOf("thesis"), result.skillNames)
        assertEquals(LocalDate.of(2026, 10, 19), result.dueDate)
        assertEquals(LocalTime.of(9, 0), result.dueTime)
        assertEquals(Difficulty.HIGH, result.difficulty)
        assertEquals(Fear.STRONG, result.fear)
        assertEquals(Urgency.HIGH, result.urgency)
        assertEquals(180, result.estimatedMinutes)
        assertEquals(Repetition(RepeatUnit.WEEK, 2), result.repetition)
    }

    @Test
    fun `an empty line yields an empty result rather than a blank quest`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
        assertTrue(parse("#thesis ~2h").isEmpty)
    }

    @Test
    fun `whitespace is tidied`() {
        assertEquals("write   methods".replace(Regex("\\s+"), " "), parse("write   methods").title)
        assertEquals("write methods", parse("  write   methods  ~2h ").title)
    }

    @Test
    fun `a word containing a keyword is not a keyword`() {
        // "todays" and "Monday-morning standup" must not be eaten by the date rules.
        assertEquals("todays plan", parse("todays plan").title)
        assertNull(parse("todays plan").dueDate)
    }
}
