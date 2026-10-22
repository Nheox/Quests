package cl.nheox.quests.i18n

import cl.nheox.quests.domain.PriorityBreakdown
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsEnTest {

    @Test
    fun `the priority breakdown reads as plain numbers`() {
        data class Case(val breakdown: PriorityBreakdown, val expected: String)

        val cases = listOf(
            Case(
                PriorityBreakdown(urgency = 4.0, due = 5.0, fear = 0.4, age = 0.0),
                "Priority 9.4 — urgency 4 · due 5 · fear 0.4 · age 0",
            ),
            Case(
                PriorityBreakdown(urgency = 0.0, due = 0.0, fear = 0.0, age = 0.0),
                "Priority 0 — urgency 0 · due 0 · fear 0 · age 0",
            ),
            Case(
                PriorityBreakdown(urgency = 7.0, due = 10.0, fear = 2.2, age = 0.35),
                "Priority 19.55 — urgency 7 · due 10 · fear 2.2 · age 0.35",
            ),
            Case(
                PriorityBreakdown(urgency = 2.0, due = 6.4, fear = 1.5, age = 3.0),
                "Priority 12.9 — urgency 2 · due 6.4 · fear 1.5 · age 3",
            ),
        )

        cases.forEach { case -> assertEquals(case.expected, StringsEn.priorityBreakdown(case.breakdown)) }
    }

    @Test
    fun `the energy modes are called Auto and Manual, never a sleep schedule`() {
        assertEquals("Auto", StringsEn.energyAuto)
        assertEquals("Manual", StringsEn.energyManual)
        assertEquals("Wake up · bedtime", StringsEn.energyTimes)
        assertEquals("Drains across your day, from wake-up to bedtime.", StringsEn.energyAutoBody)
    }

    @Test
    fun `a data-folder change says where, when, and that nothing was left behind`() {
        val cases = listOf(
            StringsEn.firstRunCreate("~/Quests") to "Create ~/Quests",
            StringsEn.firstRunUse("~/Quests") to "Use ~/Quests",
            StringsEn.firstRunExisting(1) to "Existing quests found — 1 device",
            StringsEn.firstRunExisting(3) to "Existing quests found — 3 devices",
            StringsEn.folderSwitchScheduled("~/Dropbox/Quests") to
                "From the next launch Quests will use ~/Dropbox/Quests, with a copy of this device's log. " +
                "The current folder is left untouched.",
            StringsEn.folderSwitchPending("~/Dropbox/Quests") to "Switching to ~/Dropbox/Quests at the next launch.",
            StringsEn.folderSwitched("~/Dropbox/Quests") to "Now using ~/Dropbox/Quests. The previous folder was left untouched.",
            StringsEn.folderSwitchConflict("events-mac.jsonl") to
                "Kept the current folder: the new one already holds a different events-mac.jsonl.",
            StringsEn.folderSwitchFailed("~/Dropbox/Quests") to
                "Kept the current folder: this device's files could not be copied to ~/Dropbox/Quests.",
            StringsEn.folderCannotRemember("~/.config/quests") to "Quests couldn't save this choice in ~/.config/quests.",
        )
        cases.forEach { (actual, expected) -> assertEquals(expected, actual) }
    }

    @Test
    fun `no interface text speaks of sleep or waking hours`() {
        // Every plain string on the interface, read through its getter, so a
        // new label cannot slip the old wording back in unnoticed.
        val texts = Strings::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java }
            .map { it.name to it.invoke(StringsEn) as String }
        assertTrue(texts.size > 100, "found only ${texts.size} strings")
        texts.forEach { (name, value) ->
            listOf("sleep", "waking").forEach { word ->
                assertFalse(value.contains(word, ignoreCase = true), "$name: $value")
            }
        }
    }

    @Test
    fun `amounts group their thousands with a narrow no-break space`() {
        val nnbsp = "\u202F"
        assertEquals("0 XP", StringsEn.xpAmount(0))
        assertEquals("999 XP", StringsEn.xpAmount(999))
        assertEquals("1${nnbsp}210 XP", StringsEn.xpAmount(1210))
        assertEquals("12${nnbsp}379 XP", StringsEn.xpAmount(12379))
        assertEquals("1${nnbsp}000${nnbsp}000 points", StringsEn.pointsAmount(1_000_000))
        assertEquals("−1${nnbsp}500 points", StringsEn.pointsAmount(-1500))
    }

    @Test
    fun `a reminder says when it fires, relative to today when it can`() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals("Reminder today 14:30", StringsEn.reminderAt(today.atTime(14, 30), today))
        assertEquals("Reminder tomorrow 09:05", StringsEn.reminderAt(today.plusDays(1).atTime(9, 5), today))
        assertEquals("Reminder Thu 29 Oct 08:00", StringsEn.reminderAt(LocalDateTime.of(2026, 10, 29, 8, 0), today))
    }

    @Test
    fun `dates are spelled in English whatever the system language`() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals("Thursday 22 October", StringsEn.fieldDate(today, today))
        assertEquals("Friday 1 January 2027", StringsEn.fieldDate(LocalDate.of(2027, 1, 1), today))
        assertEquals("October 2026", StringsEn.monthYear(YearMonth.of(2026, 10)))
        assertEquals("M", StringsEn.weekdayInitial(java.time.DayOfWeek.MONDAY))
    }
}
