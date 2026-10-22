package cl.nheox.quests.i18n

import cl.nheox.quests.domain.PriorityBreakdown
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
        assertEquals("Wake up", StringsEn.energyWakeTime)
        assertEquals("Bedtime", StringsEn.energyBedtime)
        assertEquals("Drains across your day, from wake-up to bedtime.", StringsEn.energyAutoBody)
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
}
