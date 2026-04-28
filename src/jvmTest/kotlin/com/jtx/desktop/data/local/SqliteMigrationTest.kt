package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.DescriptionFormat
import com.jtx.desktop.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SqliteMigrationTest {
    @Test
    fun migratesLegacyEntryTablesWithoutDroppingRows() = runBlocking {
        val dbPath = Files.createTempFile("jtx-legacy", ".db")
        createLegacyDatabase(dbPath.toString())

        val dataSource = SqliteLocalDataSource(dbPath.toString())

        val journal = dataSource.getAllJournals(includeArchived = true).first().single()
        assertEquals("legacy journal", journal.title)
        assertEquals(DescriptionFormat.PLAIN, journal.descriptionFormat)
        assertEquals(emptyList(), journal.relatedEntries)
        assertEquals(0, journal.sequence)
        assertNull(journal.startTimezone)

        val note = dataSource.getAllNotes(includeArchived = true).first().single()
        assertEquals("legacy note", note.title)
        assertEquals(DescriptionFormat.PLAIN, note.descriptionFormat)
        assertEquals(emptyList(), note.relatedEntries)
        assertFalse(note.archived)

        val task = dataSource.getAllTasks(includeArchived = true).first().single()
        assertEquals("legacy task", task.title)
        assertEquals(DescriptionFormat.PLAIN, task.descriptionFormat)
        assertEquals(Priority.NONE, task.priority)
        assertEquals(emptyList(), task.recurrenceDates)
        assertEquals(emptyList(), task.exceptionDates)

        val metadata = dataSource.getAllObjectSyncMetadata().first().single()
        assertEquals("legacy-task", metadata.entryId)
        assertNull(metadata.lastError)
    }

    private fun createLegacyDatabase(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE journals (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        dtstart INTEGER,
                        dtend INTEGER,
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        comment TEXT,
                        archived INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    INSERT INTO journals
                    (id, uid, title, description, dtstart, dtend, categories, created, updated, color, location, comment, archived)
                    VALUES ('legacy-journal', 'legacy-journal', 'legacy journal', 'body', 1000, NULL, '[]', 1000, 1000, NULL, NULL, NULL, 0)
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE notes (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        archived INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    INSERT INTO notes
                    (id, uid, title, description, categories, created, updated, color, location, archived)
                    VALUES ('legacy-note', 'legacy-note', 'legacy note', 'body', '[]', 1000, 1000, NULL, NULL, 0)
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE tasks (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        due INTEGER,
                        start INTEGER,
                        completed INTEGER NOT NULL,
                        progress INTEGER NOT NULL,
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        subtasks TEXT NOT NULL,
                        related_entries TEXT NOT NULL,
                        recurrence_rule TEXT,
                        archived INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    INSERT INTO tasks
                    (id, uid, title, description, due, start, completed, progress, categories, created, updated, color, location, subtasks, related_entries, recurrence_rule, archived)
                    VALUES ('legacy-task', 'legacy-task', 'legacy task', 'body', NULL, NULL, 0, 0, '[]', 1000, 1000, NULL, NULL, '[]', '[]', NULL, 0)
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE object_sync_metadata (
                        entry_type TEXT NOT NULL,
                        entry_id TEXT NOT NULL,
                        collection_url TEXT,
                        href TEXT,
                        filename TEXT,
                        etag TEXT,
                        schedule_tag TEXT,
                        dirty INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        uid TEXT NOT NULL,
                        sequence INTEGER NOT NULL DEFAULT 0,
                        last_modified INTEGER,
                        PRIMARY KEY (entry_type, entry_id)
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    INSERT INTO object_sync_metadata
                    (entry_type, entry_id, collection_url, href, filename, etag, schedule_tag, dirty, deleted, uid, sequence, last_modified)
                    VALUES ('TASK', 'legacy-task', 'https://example.test/cal', '/cal/legacy-task.ics', 'legacy-task.ics', 'etag-1', NULL, 0, 0, 'legacy-task', 0, 1000)
                    """.trimIndent()
                )
            }
        }
    }
}
