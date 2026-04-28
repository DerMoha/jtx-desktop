package com.jtx.desktop.data.repository

import com.jtx.desktop.data.local.SqliteLocalDataSource
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.domain.model.JournalEntry
import com.jtx.desktop.domain.model.ObjectSyncMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryStateTest {
    @Test
    fun journalRepositoryTracksDirtyArchiveRestoreAndDeleteState() = runBlocking {
        val dataSource = SqliteLocalDataSource(Files.createTempFile("jtx-repository", ".db").toString())
        val repository = JournalRepository(dataSource)
        val entry = journalEntry(id = "journal-1")

        repository.insert(entry)

        val createdMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, entry.id)
        assertNotNull(createdMetadata)
        assertEquals(entry.uid, createdMetadata.uid)
        assertTrue(createdMetadata.dirty)
        assertFalse(createdMetadata.deleted)
        assertNull(createdMetadata.href)

        repository.delete(entry.id)

        assertTrue(dataSource.getJournalById(entry.id)?.archived == true)
        assertNull(dataSource.getObjectSyncMetadata(EntryType.JOURNAL, entry.id))

        repository.restore(entry.id)

        assertFalse(dataSource.getJournalById(entry.id)?.archived == true)
        val restoredMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, entry.id)
        assertNotNull(restoredMetadata)
        assertTrue(restoredMetadata.dirty)
        assertFalse(restoredMetadata.deleted)
        assertNull(restoredMetadata.href)

        dataSource.upsertObjectSyncMetadata(
            ObjectSyncMetadata(
                entryId = entry.id,
                entryType = EntryType.JOURNAL,
                collectionUrl = "https://example.test/cal/",
                href = "https://example.test/cal/journal-1.ics",
                filename = "journal-1.ics",
                etag = "etag-1",
                dirty = false,
                deleted = false,
                uid = entry.uid,
                lastModified = entry.updated
            )
        )

        repository.delete(entry.id)

        val deletedMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, entry.id)
        assertNotNull(deletedMetadata)
        assertTrue(dataSource.getJournalById(entry.id)?.archived == true)
        assertTrue(deletedMetadata.dirty)
        assertTrue(deletedMetadata.deleted)
        assertEquals("https://example.test/cal/journal-1.ics", deletedMetadata.href)

        repository.restore(entry.id)

        val restoredRemoteMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, entry.id)
        assertNotNull(restoredRemoteMetadata)
        assertFalse(dataSource.getJournalById(entry.id)?.archived == true)
        assertTrue(restoredRemoteMetadata.dirty)
        assertFalse(restoredRemoteMetadata.deleted)
        assertEquals("https://example.test/cal/journal-1.ics", restoredRemoteMetadata.href)

        repository.permanentlyDelete(entry.id)

        assertNull(dataSource.getJournalById(entry.id))
        assertEquals(emptyList(), dataSource.getAllJournals(includeArchived = true).first())
    }

    private fun journalEntry(id: String): JournalEntry = JournalEntry(
        id = id,
        uid = id,
        title = "Repository test journal",
        description = "body",
        dtstart = 1_000L,
        dtend = null,
        categories = emptyList(),
        created = 1_000L,
        updated = 1_000L,
        color = null,
        location = null,
        comment = null
    )
}
