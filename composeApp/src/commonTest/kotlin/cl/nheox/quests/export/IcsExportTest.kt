package cl.nheox.quests.export

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Skill
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.projection.QuestsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The calendar feed (§7.5).
 *
 * This file is the app's only route to a reminder that actually reaches
 * somebody, so it is tested against the letter of RFC 5545 rather than against
 * "it looked right in one calendar client". A feed that fails to import fails
 * silently, and takes the whole reminder story with it.
 */
class IcsExportTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneId.of("UTC"))
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun quest(
        id: String = "q1",
        title: String = "Write the methods section",
        dueDate: LocalDate? = LocalDate.of(2026, 10, 20),
        dueTime: LocalTime? = null,
        state: QuestState = QuestState.OPEN,
        reminders: List<Reminder> = emptyList(),
        notes: String = "",
        skills: Set<String> = emptySet(),
        minutes: Int? = 120,
    ) = Quest(
        id = id,
        title = title,
        notes = notes,
        difficulty = Difficulty.HIGH,
        fear = Fear.STRONG,
        urgency = Urgency.CRITICAL,
        estimatedMinutes = minutes,
        dueDate = dueDate,
        dueTime = dueTime,
        reminders = reminders,
        state = state,
        skillIds = skills,
        createdAt = created,
        updatedAt = created,
    )

    private fun stateWith(vararg quests: Quest, skills: List<Skill> = emptyList()) = QuestsState(
        quests = quests.associateBy { it.id },
        skills = skills.associateBy { it.id },
    )

    private fun render(state: QuestsState) = IcsExport.render(state, clock)

    @Test
    fun `the envelope is well formed`() {
        val ics = render(stateWith(quest()))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0\r\n"))
        assertTrue(ics.contains("PRODID:"))
    }

    @Test
    fun `every line ends with CRLF`() {
        val ics = render(stateWith(quest(reminders = listOf(Reminder.BeforeDue(30)))))
        val lines = ics.split("\r\n").dropLast(1)
        assertTrue(lines.isNotEmpty())
        assertFalse(ics.replace("\r\n", "").contains("\n"), "a bare LF would make the feed invalid")
    }

    @Test
    fun `a date-only due produces an all-day event`() {
        val ics = render(stateWith(quest(dueTime = null)))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261020"), ics)
        // DTEND is exclusive, so a one-day event ends on the following day.
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261021"), ics)
    }

    @Test
    fun `a timed due produces a UTC instant and a duration from the estimate`() {
        val ics = render(stateWith(quest(dueTime = LocalTime.of(15, 30), minutes = 90)))
        assertTrue(ics.contains("DTSTART:20261020T153000Z"), ics)
        assertTrue(ics.contains("DTEND:20261020T170000Z"), ics)
    }

    @Test
    fun `the UID is stable so a refresh updates rather than duplicates`() {
        val first = render(stateWith(quest()))
        val second = render(stateWith(quest()))
        assertTrue(first.contains("UID:q1@"))
        assertEquals(first, second)
    }

    @Test
    fun `state maps onto calendar status`() {
        assertTrue(render(stateWith(quest(state = QuestState.OPEN))).contains("STATUS:CONFIRMED"))
        assertTrue(
            render(stateWith(quest(state = QuestState.DONE)), ).let { it.isEmpty() || !it.contains("q1@") },
            "completed quests are excluded unless asked for",
        )
        assertTrue(
            IcsExport.render(stateWith(quest(state = QuestState.DONE)), clock, includeCompleted = true)
                .contains("STATUS:COMPLETED"),
        )
        assertTrue(render(stateWith(quest(state = QuestState.FAILED))).contains("STATUS:CANCELLED"))
    }

    @Test
    fun `urgency becomes calendar priority`() {
        assertTrue(render(stateWith(quest())).contains("PRIORITY:1"))
    }

    @Test
    fun `reminders become alarms`() {
        val ics = render(
            stateWith(quest(reminders = listOf(Reminder.BeforeDue(30), Reminder.BeforeDue(0)))),
        )
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("TRIGGER:-PT30M"), ics)
        assertTrue(ics.contains("TRIGGER:-PT0M"), ics)
        assertTrue(ics.contains("ACTION:DISPLAY"))
    }

    @Test
    fun `quests with no due date are not in the feed at all`() {
        val ics = render(stateWith(quest(dueDate = null)))
        assertFalse(ics.contains("BEGIN:VEVENT"), "nothing to put on a calendar")
    }

    @Test
    fun `special characters are escaped`() {
        val ics = render(stateWith(quest(title = "Email Smith; Jones, and co\\ltd", notes = "line one\nline two")))
        assertTrue(ics.contains("""SUMMARY:Email Smith\; Jones\, and co\\ltd"""), ics)
        assertTrue(ics.contains("""line one\nline two"""), ics)
    }

    @Test
    fun `long lines are folded at 75 octets with a leading space`() {
        val longTitle = "Write up the complete methods and materials section including every protocol step"
        val ics = render(stateWith(quest(title = longTitle)))
        ics.split("\r\n").forEach { line ->
            assertTrue(
                line.toByteArray(Charsets.UTF_8).size <= 75,
                "line exceeds 75 octets (${line.toByteArray(Charsets.UTF_8).size}): $line",
            )
        }
        // Unfolding must recover the original.
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(unfolded.contains("SUMMARY:$longTitle"), unfolded)
    }

    @Test
    fun `folding counts bytes and never splits a multi-byte character`() {
        // The bug this guards against is invisible in English and immediate in
        // Spanish: a title of accented characters folded by character count
        // produces an invalid feed.
        val accented = "Revisión de la sección de métodos y materiales, incluyendo cada protocolo específico"
        val ics = render(stateWith(quest(title = accented)))
        ics.split("\r\n").forEach { line ->
            assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75, "over 75 octets: $line")
        }
        // Compared against the escaped form: the title contains a comma, which
        // RFC 5545 requires be written as `\,`.
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(
            unfolded.contains("SUMMARY:" + IcsExport.escape(accented)),
            "accents did not survive folding: $unfolded",
        )
    }

    @Test
    fun `fold leaves a short line alone`() {
        assertEquals(listOf("SUMMARY:short"), IcsExport.fold("SUMMARY:short"))
    }

    @Test
    fun `skills become categories`() {
        val ics = render(
            stateWith(
                quest(skills = setOf("s1")),
                skills = listOf(Skill(id = "s1", name = "Thesis", createdAt = created)),
            ),
        )
        assertTrue(ics.contains("CATEGORIES:Thesis"), ics)
    }

    @Test
    fun `events come out in due order so a diff of the feed stays readable`() {
        val ics = render(
            stateWith(
                quest(id = "late", dueDate = LocalDate.of(2026, 12, 1)),
                quest(id = "early", dueDate = LocalDate.of(2026, 10, 16)),
            ),
        )
        assertTrue(ics.indexOf("UID:early@") < ics.indexOf("UID:late@"))
    }

    @Test
    fun `an empty state still produces a valid, importable calendar`() {
        val ics = render(QuestsState())
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }
}
