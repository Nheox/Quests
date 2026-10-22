package cl.nheox.quests.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PickerSearchTest {

    private val options = listOf(
        PickerOption("a", "Draft", detail = "Thesis › Methods"),
        PickerOption("b", "Draft", detail = "Grant › Budget"),
        PickerOption("c", "Bioinformatics"),
        PickerOption("d", "Writing"),
    )

    private fun ids(query: String) = PickerSearch.filter(options, query).map { it.value }

    @Test
    fun `an empty query keeps everything, in order`() {
        assertEquals(listOf("a", "b", "c", "d"), ids(""))
        assertEquals(listOf("a", "b", "c", "d"), ids("   "))
    }

    @Test
    fun `matching ignores case and looks in the detail too`() {
        assertEquals(listOf("c"), ids("BIO"))
        assertEquals(listOf("a"), ids("methods"))
    }

    @Test
    fun `every word must match, in any order`() {
        assertEquals(listOf("b"), ids("budget draft"))
        assertEquals(emptyList(), ids("draft cooking"))
    }
}
