package cl.nheox.quests.ui.screens

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.projection.QuestsState
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.ZoneId

/**
 * The Plan tree's order: every sibling group in the automatic order, with pins
 * that apply within their own group and nowhere else.
 */
class PlanTreeTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneId.of("UTC"))
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun quest(
        id: String,
        parent: String? = null,
        urgency: Urgency = Urgency.NORMAL,
        pin: Int? = null,
        state: QuestState = QuestState.OPEN,
    ) = Quest(
        id = id,
        title = id,
        parentId = parent,
        urgency = urgency,
        manualOrder = pin,
        state = state,
        createdAt = created,
        updatedAt = created,
    )

    private fun rows(vararg quests: Quest, collapsed: Set<String> = emptySet()): List<Pair<String, Int>> =
        flatten(QuestsState(quests = quests.associateBy { it.id }), collapsed, clock).map { it.quest.id to it.depth }

    @Test
    fun `each sibling group is ordered by priority, pins included`() {
        val tree = arrayOf(
            quest("calm-root", urgency = Urgency.LOW),
            quest("urgent-root", urgency = Urgency.CRITICAL),
            quest("pinned-root", urgency = Urgency.WHENEVER, pin = -1),
            quest("child-low", parent = "calm-root", urgency = Urgency.LOW),
            quest("child-high", parent = "calm-root", urgency = Urgency.HIGH),
            quest("child-sunk", parent = "calm-root", urgency = Urgency.CRITICAL, pin = 1),
            // A child's urgency lifts it within its own group, never its parent.
            quest("child-critical", parent = "urgent-root", urgency = Urgency.CRITICAL),
        )
        assertEquals(
            listOf(
                "pinned-root" to 0,
                "urgent-root" to 0,
                "child-critical" to 1,
                "calm-root" to 0,
                "child-high" to 1,
                "child-low" to 1,
                "child-sunk" to 1,
            ),
            rows(*tree),
        )
    }

    @Test
    fun `finished quests and collapsed branches are left out`() {
        val tree = arrayOf(
            quest("root"),
            quest("done", parent = "root", state = QuestState.DONE),
            quest("open", parent = "root"),
            quest("grandchild", parent = "open"),
        )
        assertEquals(listOf("root" to 0, "open" to 1, "grandchild" to 2), rows(*tree))
        assertEquals(listOf("root" to 0, "open" to 1), rows(*tree, collapsed = setOf("open")))
    }

    @Test
    fun `an open quest under a finished one stands at the top level`() {
        val tree = arrayOf(
            quest("done-root", state = QuestState.DONE),
            quest("reopened", parent = "done-root"),
            quest("its-child", parent = "reopened"),
        )
        assertEquals(listOf("reopened" to 0, "its-child" to 1), rows(*tree))
    }
}
