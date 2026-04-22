package com.jtx.desktop.data.repository

import com.jtx.desktop.data.local.InMemoryLocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JournalRepository(private val local: InMemoryLocalDataSource) {
    fun getAll(): Flow<List<JournalEntry>> = local.getAllJournals()
    fun getAllCombined(): Flow<List<CombinedEntry>> = local.getAllJournals().map { journals ->
        journals.map { journal ->
            CombinedEntry(
                id = journal.id,
                type = EntryType.JOURNAL,
                title = journal.title,
                description = journal.description,
                date = journal.dtstart,
                categories = journal.categories,
                color = journal.color,
                progress = null,
                completed = null
            )
        }
    }
    suspend fun getById(id: String): JournalEntry? = local.getJournalById(id)
    suspend fun insert(entry: JournalEntry) = local.insertJournal(entry)
    suspend fun update(entry: JournalEntry) = local.updateJournal(entry)
    suspend fun delete(id: String) = local.deleteJournal(id)
}

class NoteRepository(private val local: InMemoryLocalDataSource) {
    fun getAll(): Flow<List<NoteEntry>> = local.getAllNotes()
    fun getAllCombined(): Flow<List<CombinedEntry>> = local.getAllNotes().map { notes ->
        notes.map { note ->
            CombinedEntry(
                id = note.id,
                type = EntryType.NOTE,
                title = note.title,
                description = note.description,
                date = null,
                categories = note.categories,
                color = note.color,
                progress = null,
                completed = null
            )
        }
    }
    suspend fun getById(id: String): NoteEntry? = local.getNoteById(id)
    suspend fun insert(entry: NoteEntry) = local.insertNote(entry)
    suspend fun update(entry: NoteEntry) = local.updateNote(entry)
    suspend fun delete(id: String) = local.deleteNote(id)
}

class TaskRepository(private val local: InMemoryLocalDataSource) {
    fun getAll(): Flow<List<TaskEntry>> = local.getAllTasks()
    fun getAllCombined(): Flow<List<CombinedEntry>> = local.getAllTasks().map { tasks ->
        tasks.map { task ->
            CombinedEntry(
                id = task.id,
                type = EntryType.TASK,
                title = task.title,
                description = task.description,
                date = task.due,
                categories = task.categories,
                color = task.color,
                progress = task.progress,
                completed = task.completed
            )
        }
    }
    suspend fun getById(id: String): TaskEntry? = local.getTaskById(id)
    suspend fun insert(entry: TaskEntry) = local.insertTask(entry)
    suspend fun update(entry: TaskEntry) = local.updateTask(entry)
    suspend fun delete(id: String) = local.deleteTask(id)
}

class SyncRepository(
    private val local: InMemoryLocalDataSource,
    private val calDavClient: CalDavClient,
    private val parser: ICalendarParser
) {
    suspend fun sync(credentials: CalDavCredentials, collection: String): Result<Unit> {
        val fetchResult = calDavClient.fetchEntries(credentials, collection)
        return fetchResult.fold(
            onSuccess = { hrefs ->
                for (href in hrefs) {
                    calDavClient.fetchCalendarData(credentials, href).onSuccess { data ->
                        val entry = when {
                            data.contains("BEGIN:VJOURNAL") -> parser.parseVJournal(data)
                            data.contains("BEGIN:VTODO") -> parser.parseVTodo(data)
                            else -> null
                        }
                        when (entry) {
                            is JournalEntry -> local.insertJournal(entry)
                            is NoteEntry -> local.insertNote(entry)
                            is TaskEntry -> local.insertTask(entry)
                            else -> {}
                        }
                    }
                }
                val settings = local.getSettings()
                local.saveSettings(settings.copy(lastSyncTime = System.currentTimeMillis()))
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun uploadJournal(credentials: CalDavCredentials, entry: JournalEntry, collection: String): Result<String> {
        val ics = parser.journalToIcs(entry)
        val href = "$collection/${entry.uid}.ics"
        return calDavClient.putEntry(credentials, href, ics)
    }

    suspend fun uploadTask(credentials: CalDavCredentials, entry: TaskEntry, collection: String): Result<String> {
        val ics = parser.taskToIcs(entry)
        val href = "$collection/${entry.uid}.ics"
        return calDavClient.putEntry(credentials, href, ics)
    }

    suspend fun deleteFromServer(credentials: CalDavCredentials, href: String): Result<Unit> {
        return calDavClient.deleteEntry(credentials, href)
    }

    suspend fun getSettings(): AppSettings = local.getSettings()
    suspend fun saveSettings(settings: AppSettings) = local.saveSettings(settings)
}