package com.jtx.desktop.data.repository

import com.jtx.desktop.data.local.LocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JournalRepository(private val local: LocalDataSource) {
    private var onDataChange: (() -> Unit)? = null

    fun setOnDataChangeListener(listener: () -> Unit) {
        onDataChange = listener
    }

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
                completed = null,
                archived = journal.archived,
                syncStatus = SyncStatus.SYNCED
            )
        }
    }
    suspend fun getById(id: String): JournalEntry? = local.getJournalById(id)
    suspend fun insert(entry: JournalEntry) {
        local.insertJournal(entry)
        onDataChange?.invoke()
    }
    suspend fun update(entry: JournalEntry) {
        local.updateJournal(entry)
        onDataChange?.invoke()
    }
    suspend fun updateJournal(combined: CombinedEntry) {
        val existing = local.getJournalById(combined.id) ?: return
        local.updateJournal(existing.copy(
            title = combined.title,
            description = combined.description,
            archived = combined.archived
        ))
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.deleteJournal(id)
        onDataChange?.invoke()
    }
}

class NoteRepository(private val local: LocalDataSource) {
    private var onDataChange: (() -> Unit)? = null

    fun setOnDataChangeListener(listener: () -> Unit) {
        onDataChange = listener
    }

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
                completed = null,
                archived = note.archived,
                syncStatus = SyncStatus.SYNCED
            )
        }
    }
    suspend fun getById(id: String): NoteEntry? = local.getNoteById(id)
    suspend fun insert(entry: NoteEntry) {
        local.insertNote(entry)
        onDataChange?.invoke()
    }
    suspend fun update(entry: NoteEntry) {
        local.updateNote(entry)
        onDataChange?.invoke()
    }
    suspend fun updateNote(combined: CombinedEntry) {
        val existing = local.getNoteById(combined.id) ?: return
        local.updateNote(existing.copy(
            title = combined.title,
            description = combined.description,
            archived = combined.archived
        ))
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.deleteNote(id)
        onDataChange?.invoke()
    }
}

class TaskRepository(private val local: LocalDataSource) {
    private var onDataChange: (() -> Unit)? = null

    fun setOnDataChangeListener(listener: () -> Unit) {
        onDataChange = listener
    }

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
                completed = task.completed,
                archived = task.archived,
                syncStatus = SyncStatus.SYNCED
            )
        }
    }
    suspend fun getById(id: String): TaskEntry? = local.getTaskById(id)
    suspend fun insert(entry: TaskEntry) {
        local.insertTask(entry)
        onDataChange?.invoke()
    }
    suspend fun update(entry: TaskEntry) {
        local.updateTask(entry)
        onDataChange?.invoke()
    }
    suspend fun updateTask(combined: CombinedEntry) {
        val existing = local.getTaskById(combined.id) ?: return
        local.updateTask(existing.copy(
            title = combined.title,
            description = combined.description,
            progress = combined.progress ?: 0,
            completed = combined.completed ?: false,
            archived = combined.archived
        ))
        onDataChange?.invoke()
    }
    suspend fun updateTaskCompleted(id: String, completed: Boolean) {
        val existing = local.getTaskById(id) ?: return
        local.updateTask(existing.copy(completed = completed))
        onDataChange?.invoke()
    }
    suspend fun updateTaskProgress(id: String, progress: Int) {
        val existing = local.getTaskById(id) ?: return
        local.updateTask(existing.copy(
            progress = progress,
            completed = progress >= 100
        ))
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.deleteTask(id)
        onDataChange?.invoke()
    }
}

class SyncRepository(
    private val local: LocalDataSource,
    private val calDavClient: CalDavClient,
    private val parser: ICalendarParser
) {
    val localDataSource: LocalDataSource get() = local

    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val failures: List<String>,
        val conflicts: List<SyncConflictInfo> = emptyList()
    )

    data class SyncConflictInfo(
        val localEntry: Any,
        val serverEntry: Any,
        val serverHref: String,
        val localUpdated: Long,
        val serverUpdated: Long
    )

    suspend fun sync(credentials: CalDavCredentials, collection: String): Result<SyncResult> {
        val settings = local.getSettings()
        val fetchResult = calDavClient.fetchEntries(credentials, collection)
        return fetchResult.fold(
            onSuccess = { hrefs ->
                var successCount = 0
                var failureCount = 0
                val failures = mutableListOf<String>()
                val conflicts = mutableListOf<SyncConflictInfo>()

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
                                    val existing = local.getJournalById(entry.id)
                                    if (existing == null) {
                                        local.insertJournal(entry)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertJournal(entry)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, href, existing.updated, entry.updated))
                                    } else {
                                        successCount++
                                    }
                                }
                                is NoteEntry -> {
                                    val existing = local.getNoteById(entry.id)
                                    if (existing == null) {
                                        local.insertNote(entry)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertNote(entry)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, href, existing.updated, entry.updated))
                                    } else {
                                        successCount++
                                    }
                                }
                                is TaskEntry -> {
                                    val existing = local.getTaskById(entry.id)
                                    if (existing == null) {
                                        local.insertTask(entry)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertTask(entry)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, href, existing.updated, entry.updated))
                                    } else {
                                        successCount++
                                    }
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
                local.saveSettings(settings.copy(lastSyncTime = System.currentTimeMillis()))
                Result.success(SyncResult(successCount, failureCount, failures, conflicts))
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

    suspend fun resolveConflict(
        credentials: CalDavCredentials,
        collection: String,
        conflict: SyncConflictInfo,
        resolution: ConflictResolution
    ) {
        when (resolution) {
            ConflictResolution.KEEP_LOCAL -> {
            }
            ConflictResolution.KEEP_SERVER -> {
            }
            ConflictResolution.KEEP_BOTH -> {
                conflict.localEntry
            }
        }
    }
}

enum class ConflictResolution {
    KEEP_LOCAL, KEEP_SERVER, KEEP_BOTH
}

class TemplateRepository(private val local: LocalDataSource) {
    private val builtInTemplates = listOf(
        EntryTemplate("daily_standup", EntryType.TASK, "Daily Standup", "What I did yesterday:\n\nWhat I plan to do today:\n\nBlockers:", categories = listOf("standup")),
        EntryTemplate("meeting_notes", EntryType.NOTE, "Meeting Notes", "Date:\nLocation:\nAttendees:\n\nAgenda:\n\nNotes:\n\nAction Items:", categories = listOf("meeting")),
        EntryTemplate("weekly_review", EntryType.TASK, "Weekly Review", categories = listOf("review"), recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1))
    )

    fun getTemplates(): List<EntryTemplate> = builtInTemplates

    fun getTemplatesByType(type: EntryType): List<EntryTemplate> =
        builtInTemplates.filter { it.type == type }
}