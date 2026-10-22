package cl.nheox.quests.export

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.projection.QuestsState
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.ZoneId

/**
 * The browsable snapshot (§3.3): what someone sees of their quests without the
 * app installed.
 */
class MarkdownExportTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneId.of("UTC"))
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun lineFor(quest: Quest): String {
        val markdown = MarkdownExport.render(QuestsState(quests = mapOf(quest.id to quest)), clock)
        val lines = markdown.lines()
        val title = lines.indexOfFirst { it.startsWith("- [ ] ") }
        // The facts sit on the continuation line under the title.
        return lines[title + 1]
    }

    @Test
    fun `a reward bonus is listed only when there is one`() {
        data class Case(val bonus: Int?, val shown: Boolean)

        val cases = listOf(
            Case(null, shown = false),
            Case(0, shown = false),
            Case(5, shown = true),
        )

        cases.forEach { case ->
            val quest = Quest(id = "q", title = "Call the bank", rewardBonus = case.bonus, createdAt = created, updatedAt = created)
            val facts = lineFor(quest)
            assertEquals(case.shown, facts.contains("+5 bonus points"), "bonus ${case.bonus}: $facts")
            assertEquals(case.shown, facts.contains("bonus"), "bonus ${case.bonus}: $facts")
        }
    }
}
