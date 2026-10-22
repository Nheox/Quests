package cl.nheox.quests.ui

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestView
import cl.nheox.quests.domain.SortKey
import cl.nheox.quests.domain.SortOrder
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.Vault
import cl.nheox.quests.export.BranchExport
import cl.nheox.quests.projection.QuestDraft
import cl.nheox.quests.projection.QuestStore
import cl.nheox.quests.projection.QuestsState
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hierarchy as the user moves through it: the order the arrow keys walk,
 * Show in Plan, adding a quest from a list, exporting a branch, and clearing
 * the scratchpad.
 */
class StructureTest {

    private val root: Path = createTempDirectory("quests-structure-test")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.of(2026, 10, 22)
    private val device = DeviceIdentity("macbook")
    private val vault = Vault(root)
    private val store = QuestStore(vault, device, FileEventLog(vault, device), clock, Random(1)).also { it.load() }
    private val app = AppState(store, clock)

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun quest(title: String, parent: String? = null, due: LocalDate? = null): String =
        store.createQuest(QuestDraft(title = title, parentId = parent, dueDate = due))

    private fun state() = store.state.value

    private fun titles() = app.visibleQuests(state()).map { it.title }

    @Test
    fun `in Plan the arrow keys walk the tree as it is shown`() {
        val thesis = quest("Thesis", due = today)
        quest("Methods", thesis)
        quest("Grant")
        app.goToView(QuestView.PLAN)

        assertEquals(listOf("Thesis", "Methods", "Grant"), titles())

        app.setCollapsed(thesis, true)
        assertEquals(listOf("Thesis", "Grant"), titles(), "a folded branch is skipped")
    }

    @Test
    fun `a grouped list is walked heading by heading`() {
        quest("A later", due = today.plusDays(20))
        quest("B today", due = today)
        quest("C undated")
        app.goToView(QuestView.ALL)
        app.sort = SortOrder(SortKey.NAME)

        assertTrue(app.grouped(QuestView.ALL))
        assertEquals(listOf("B today", "A later", "C undated"), titles())
    }

    @Test
    fun `show in Plan unfolds the branch above the quest and puts the cursor on it`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        val draft = quest("Draft", methods)
        app.goToView(QuestView.PLAN)
        app.setCollapsed(thesis, true)
        app.setCollapsed(methods, true)
        app.goToView(QuestView.ALL)

        app.showInPlan(draft, state())

        assertEquals(Route.Quests(QuestView.PLAN), app.route)
        assertEquals(draft, app.selectedId)
        assertEquals(listOf("Thesis", "Methods", "Draft"), titles())
    }

    @Test
    fun `a quest added from a dated list takes that list's day`() {
        app.goToView(QuestView.TODAY)
        assertEquals(today, app.defaultDueDate())
        app.goToView(QuestView.TOMORROW)
        assertEquals(today.plusDays(1), app.defaultDueDate())
        app.goToView(QuestView.ALL)
        assertNull(app.defaultDueDate())
    }

    @Test
    fun `starting a quest where there is no list to show it in opens Today`() {
        app.go(Route.Skills)
        app.openQuickAdd()
        assertEquals(Route.Quests(QuestView.TODAY), app.route)
        assertTrue(app.quickAddOpen)
    }

    @Test
    fun `a branch exports as an outline, finished sub-quests ticked`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        quest("Write the draft", methods)
        val intro = quest("Introduction", thesis)
        store.completeQuest(intro)

        val markdown = BranchExport.render(state(), thesis, clock)!!
        val outline = markdown.lines().filter { it.trimStart().startsWith("- [") }
            .map { line -> line.substringBefore(" — ") }
        assertEquals(
            listOf(
                "- [ ] Methods",
                "  - [ ] Write the draft",
                "- [x] Introduction",
            ),
            outline,
        )
        assertTrue(markdown.startsWith("# Thesis\n"))
    }

    @Test
    fun `a branch file is named after its title and its quest`() {
        val id = quest("Señal de café: ¿listo?")
        val name = BranchExport.fileName(state().quests.getValue(id))
        assertEquals("senal-de-cafe-listo-${id.takeLast(6).lowercase()}.md", name)
    }

    @Test
    fun `a parent cycle in a hand-edited log does not hang a branch walk`() {
        val created = Instant.parse("2026-10-01T09:00:00Z")
        fun looped(id: String, parent: String) =
            Quest(id = id, title = id, parentId = parent, createdAt = created, updatedAt = created)
        val state = QuestsState(quests = listOf(looped("a", "b"), looped("b", "a")).associateBy { it.id })

        assertEquals(listOf("a", "b"), state.branch("a").map { it.id })
    }

    @Test
    fun `clearing the scratchpad removes only finished lines`() {
        val done = store.addNote("Buy pipette tips")
        store.addNote("Ask about the cluster")
        val promoted = store.addNote("Email Ana")
        store.toggleNote(done)
        store.promoteNote(promoted)

        assertEquals(2, store.clearFinishedNotes())
        assertEquals(listOf("Ask about the cluster"), state().globalNotes().map { it.text })
    }
}
