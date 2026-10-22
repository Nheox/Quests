package cl.nheox.quests

import cl.nheox.quests.i18n.StringsEn
import cl.nheox.quests.platform.FolderStatus
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The words on the first-run window. They are the whole of the explanation the
 * user gets before choosing where their data lives, so each state reads
 * differently and says exactly what one click will do.
 */
class FirstRunTest {

    private val home: Path = Paths.get(System.getProperty("user.home"))
    private val default: Path = home.resolve("Quests")
    private val other: Path = home.resolve("Dropbox").resolve("Quests")

    @Test
    fun `the main button names the default folder and says whether it will be created`() {
        data class Case(val selected: Path, val status: FolderStatus, val expected: String)

        val cases = listOf(
            Case(default, FolderStatus.Missing, "Create ~/Quests"),
            Case(default, FolderStatus.Empty, "Use ~/Quests"),
            Case(default, FolderStatus.HasQuests(2), "Use ~/Quests"),
            Case(default.resolve("x").resolve(".."), FolderStatus.HasOtherFiles, "Use ~/Quests"),
            Case(other, FolderStatus.Missing, "Create this folder"),
            Case(other, FolderStatus.HasQuests(1), "Use this folder"),
            Case(other, FolderStatus.Empty, "Use this folder"),
        )

        cases.forEach { case ->
            assertEquals(case.expected, firstRunAction(case.selected, default, case.status, StringsEn), "$case")
        }
    }

    @Test
    fun `every folder state has its own line`() {
        data class Case(val status: FolderStatus, val expected: String)

        val cases = listOf(
            Case(FolderStatus.Missing, "New folder — Quests will create it."),
            Case(FolderStatus.Empty, "Empty folder, ready to use."),
            Case(FolderStatus.HasQuests(1), "Existing quests found — 1 device"),
            Case(FolderStatus.HasQuests(2), "Existing quests found — 2 devices"),
            Case(FolderStatus.HasOtherFiles, "Quests will keep its files beside what is already here."),
            Case(FolderStatus.NotAFolder, "That is a file, not a folder."),
            Case(FolderStatus.NotWritable, "Quests can't write to this folder."),
        )

        cases.forEach { case -> assertEquals(case.expected, folderStatusText(case.status, StringsEn), "$case") }
    }
}
