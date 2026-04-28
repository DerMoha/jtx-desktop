package com.jtx.desktop.ui

import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JtxAppBehaviorTest {
    @Test
    fun quickEntryParserSupportsPrefixesTagsAndDescriptions() {
        val draft = "task: Buy milk #errands #home | Get oat milk".toQuickEntryDraft(EntryType.NOTE)

        assertEquals(EntryType.TASK, draft?.type)
        assertEquals("Buy milk", draft?.title)
        assertEquals("Get oat milk", draft?.description)
        assertEquals(listOf("errands", "home"), draft?.categories)
    }

    @Test
    fun quickEntryParserUsesDefaultTypeAndRejectsEmptyTitles() {
        val draft = "Call Alex #people".toQuickEntryDraft(EntryType.JOURNAL)

        assertEquals(EntryType.JOURNAL, draft?.type)
        assertEquals("Call Alex", draft?.title)
        assertEquals(listOf("people"), draft?.categories)
        assertNull("task: #errands".toQuickEntryDraft(EntryType.NOTE))
    }

    @Test
    fun globalSearchRequiresEveryTermAcrossTitleDescriptionLocationAndCategories() {
        val entry = combinedEntry(
            title = "Project journal",
            description = "Discuss desktop filters",
            location = "Vienna",
            categories = listOf("Work", "Planning")
        )

        assertTrue(entry.matchesGlobalSearch("project work"))
        assertTrue(entry.matchesGlobalSearch("filters vienna"))
        assertFalse(entry.matchesGlobalSearch("project missing"))
    }

    private fun combinedEntry(
        title: String,
        description: String,
        location: String?,
        categories: List<String>
    ): CombinedEntry = CombinedEntry(
        id = "entry-1",
        type = EntryType.JOURNAL,
        title = title,
        description = description,
        date = null,
        categories = categories,
        color = null,
        progress = null,
        completed = null,
        location = location
    )
}
