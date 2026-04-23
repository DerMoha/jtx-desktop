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
    suspend fun updateJournal(combined: CombinedEntry) {
        val existing = local.getJournalById(combined.id) ?: return
        local.updateJournal(existing.copy(title = combined.title, description = combined.description))
    }
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
    suspend fun updateNote(combined: CombinedEntry) {
        val existing = local.getNoteById(combined.id) ?: return
        local.updateNote(existing.copy(title = combined.title, description = combined.description))
    }
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
    suspend fun updateTask(combined: CombinedEntry) {
        val existing = local.getTaskById(combined.id) ?: return
        local.updateTask(existing.copy(
            title = combined.title,
            description = combined.description,
            progress = combined.progress ?: 0,
            completed = combined.completed ?: false
        ))
    }
    suspend fun updateTaskCompleted(id: String, completed: Boolean) {
        val existing = local.getTaskById(id) ?: return
        local.updateTask(existing.copy(completed = completed))
    }
    suspend fun delete(id: String) = local.deleteTask(id)
}

class SyncRepository(
    private val local: InMemoryLocalDataSource,
    private val calDavClient: CalDavClient,
    private val parser: ICalendarParser
) {
    val localDataSource: InMemoryLocalDataSource get() = local

    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val failures: List<String>
    )

    suspend fun sync(credentials: CalDavCredentials, collection: String): Result<SyncResult> {
        val fetchResult = calDavClient.fetchEntries(credentials, collection)
        return fetchResult.fold(
            onSuccess = { hrefs ->
                var successCount = 0
                var failureCount = 0
                val failures = mutableListOf<String>()

                for (href in hrefs) {
                    val fetchDataResult = calDavClient.fetchCalendarData(credentials, href)
                    fetchDataResult.fold(
                        onSuccess = { data ->
                            val entry = when {
                                data.contains("BEGIN:VJOURNAL") -> parser.parseVJournal(data)
                                data.contains("BEGIN:VTODO") -> parser.parseVTodo(data)
                                data.contains("BEGIN:VNOTE") -> parser.parseVNote(data)
                                else -> null
                            }
                            when (entry) {
                                is JournalEntry -> {
                                    local.insertJournal(entry)
                                    successCount++
                                }
                                is NoteEntry -> {
                                    local.insertNote(entry)
                                    successCount++
                                }
                                is TaskEntry -> {
                                    local.insertTask(entry)
                                    successCount++
                                }
                                else -> {}
                            }
                        },
                        onFailure = { e ->
                            failureCount++
                            failures.add("Failed to fetch $href: ${e.message}")
                        }
                    )
                }
                val settings = local.getSettings()
                local.saveSettings(settings.copy(lastSyncTime = System.currentTimeMillis()))
                if (failureCount > 0) {
                    Result.success(SyncResult(successCount, failureCount, failures))
                } else {
                    Result.success(SyncResult(successCount, failureCount, emptyList()))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun uploadJournal(credentials: CalDavCredentials, entry: JournalEntry, collection: String): Result<String> {
        val ics = parser.journalToIcs(entry)
        val href = "$collection/${entry.uid}.ics"
        return calDavClient.putEntry(credentials, href, ics)
    }

    suspend fun uploadNote(credentials: CalDavCredentials, entry: NoteEntry, collection: String): Result<String> {
        val ics = parser.noteToIcs(entry)
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