package cl.nheox.quests.projection

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.Vault
import cl.nheox.quests.ui.screens.parentOptions
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** "Move under…": the parents offered, the move, and its undo. */
class MoveUnderTest {

    private val root: Path = createTempDirectory("quests-move-test")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC"))
    private val device = DeviceIdentity("macbook")
    private val vault = Vault(root)
    private val store = QuestStore(vault, device, FileEventLog(vault, device), clock, Random(1)).also { it.load() }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun quest(title: String, parent: String? = null): String =
        store.createQuest(QuestDraft(title = title, parentId = parent))

    private fun state() = store.state.value

    @Test
    fun `ancestors run outermost first`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        val draft = quest("Draft", methods)
        assertEquals(listOf("Thesis", "Methods"), state().ancestors(draft).map { it.title })
        assertEquals(emptyList(), state().ancestors(thesis))
    }

    @Test
    fun `a quest cannot be offered as a parent for itself or anything above it in its branch`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        val draft = quest("Draft", methods)
        val other = quest("Grant")

        val targets = state().moveTargets(listOf(methods)).map { it.title }.toSet()
        assertEquals(setOf("Thesis", "Grant"), targets, "Methods and Draft, under it, are not offered")

        val options = parentOptions(state(), listOf(methods), "Top level")
        assertNull(options.first().value, "Top level leads")
        assertEquals(listOf("Top level", "Grant", "Thesis"), options.map { it.label })
        assertEquals(null, options.single { it.value == thesis }.detail)
        assertEquals(other, options[1].value)
        // A deeper quest shows its path.
        val deep = parentOptions(state(), listOf(other), "Top level").single { it.value == draft }
        assertEquals("Thesis › Methods", deep.detail)
    }

    @Test
    fun `moving several quests is one undoable action`() {
        val thesis = quest("Thesis")
        val a = quest("A")
        val b = quest("B", thesis)
        val c = quest("C")

        store.moveUnder(listOf(a, b, c), thesis)

        assertEquals(thesis, state().quests.getValue(a).parentId)
        assertEquals(thesis, state().quests.getValue(c).parentId)
        val moved = assertIs<Undoable.Moved>(store.undoable.value)
        assertEquals(listOf(a, c), moved.moves.map { it.questId }, "B was already there and is not counted")
        assertEquals("Thesis", moved.parentTitle)

        store.undo()
        assertNull(state().quests.getValue(a).parentId)
        assertNull(state().quests.getValue(c).parentId)
        assertEquals(thesis, state().quests.getValue(b).parentId, "untouched by the move, untouched by the undo")
    }

    @Test
    fun `a quest that would land in its own branch stays where it is, and the rest still move`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        val loose = quest("Loose")

        store.moveUnder(listOf(thesis, loose), methods)

        assertNull(state().quests.getValue(thesis).parentId, "Thesis under its own child would be a cycle")
        assertEquals(methods, state().quests.getValue(loose).parentId)
    }

    @Test
    fun `moving to the top level clears the parent`() {
        val thesis = quest("Thesis")
        val methods = quest("Methods", thesis)
        store.moveUnder(listOf(methods), null)
        assertNull(state().quests.getValue(methods).parentId)
        assertNull(assertIs<Undoable.Moved>(store.undoable.value).parentTitle)
    }
}
