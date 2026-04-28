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

    fun getAll(includeArchived: Boolean = false): Flow<List<JournalEntry>> = local.getAllJournals(includeArchived)
    fun getAllCombined(includeArchived: Boolean = false): Flow<List<CombinedEntry>> = local.getAllJournals(includeArchived).map { journals ->
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

    fun getAll(includeArchived: Boolean = false): Flow<List<NoteEntry>> = local.getAllNotes(includeArchived)
    fun getAllCombined(includeArchived: Boolean = false): Flow<List<CombinedEntry>> = local.getAllNotes(includeArchived).map { notes ->
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

    fun getAll(includeArchived: Boolean = false): Flow<List<TaskEntry>> = local.getAllTasks(includeArchived)
    fun getAllCombined(includeArchived: Boolean = false): Flow<List<CombinedEntry>> = local.getAllTasks(includeArchived).map { tasks ->
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
                    if (!settings.syncJournals && href.contains(".ics") && !href.contains("VTODO") && !href.contains("VJOURNAL") && !href.contains("VNOTE")) {
                        continue
                    }
                    val fetchDataResult = calDavClient.fetchCalendarData(credentials, href)
                    fetchDataResult.fold(
                        onSuccess = { data ->
                            val entry = when {
                                data.contains("BEGIN:VJOURNAL") && settings.syncJournals -> parser.parseVJournal(data)
                                data.contains("BEGIN:VTODO") && settings.syncTasks -> parser.parseVTodo(data)
                                data.contains("BEGIN:VNOTE") && settings.syncNotes -> parser.parseVNote(data)
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

    private val customTemplates = mutableListOf<EntryTemplate>()

    fun getTemplates(): List<EntryTemplate> = builtInTemplates + customTemplates

    fun getTemplatesByType(type: EntryType): List<EntryTemplate> =
        (builtInTemplates + customTemplates).filter { it.type == type }

    fun getBuiltInTemplates(): List<EntryTemplate> = builtInTemplates

    fun getCustomTemplates(): List<EntryTemplate> = customTemplates

    fun addTemplate(template: EntryTemplate) {
        customTemplates.add(template)
    }

    fun updateTemplate(template: EntryTemplate) {
        val index = customTemplates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            customTemplates[index] = template
        }
    }

    fun deleteTemplate(id: String) {
        customTemplates.removeIf { it.id == id }
    }

    fun createTemplate(
        name: String,
        type: EntryType,
        defaultTitle: String = "",
        defaultDescription: String? = null,
        categories: List<String> = emptyList(),
        color: String? = null,
        dueDays: Int? = null
    ): EntryTemplate {
        val template = EntryTemplate(
            id = "custom_${System.currentTimeMillis()}",
            type = type,
            title = name,
            description = defaultDescription,
            categories = categories,
            color = color,
            dueDays = dueDays
        )
        addTemplate(template)
        return template
    }
}

class TagRepository(private val local: LocalDataSource) {
    private val tags = mutableListOf<Tag>()

    fun getTags(): List<Tag> = tags

    fun getTagById(id: String): Tag? = tags.find { it.id == id }

    fun addTag(tag: Tag) {
        tags.add(tag)
    }

    fun updateTag(tag: Tag) {
        val index = tags.indexOfFirst { it.id == tag.id }
        if (index >= 0) {
            tags[index] = tag
        }
    }

    fun deleteTag(id: String) {
        tags.removeIf { it.id == id }
    }

    fun createTag(name: String, color: String, description: String = ""): Tag {
        val tag = Tag(
            id = "tag_${System.currentTimeMillis()}",
            name = name,
            color = color,
            description = description
        )
        addTag(tag)
        return tag
    }
}

class SavedFilterRepository(private val local: LocalDataSource) {
    private val savedFilters = mutableListOf<SavedFilter>()

    fun getSavedFilters(): List<SavedFilter> = savedFilters

    fun getFilterById(id: String): SavedFilter? = savedFilters.find { it.id == id }

    fun addFilter(filter: SavedFilter) {
        savedFilters.add(filter)
    }

    fun updateFilter(filter: SavedFilter) {
        val index = savedFilters.indexOfFirst { it.id == filter.id }
        if (index >= 0) {
            savedFilters[index] = filter
        }
    }

    fun deleteFilter(id: String) {
        savedFilters.removeIf { it.id == id }
    }

    fun createFilter(
        name: String,
        entryType: EntryType? = null,
        categories: List<String> = emptyList(),
        priorities: List<Priority> = emptyList(),
        completed: Boolean? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null
    ): SavedFilter {
        val filter = SavedFilter(
            id = "filter_${System.currentTimeMillis()}",
            name = name,
            entryType = entryType,
            categories = categories,
            priorities = priorities,
            completed = completed,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
        addFilter(filter)
        return filter
    }

    fun getDefaultFilters(): List<SavedFilter> = listOf(
        SavedFilter("due_today", "Due Today", dateFrom = getStartOfDay(), dateTo = getEndOfDay()),
        SavedFilter("high_priority", "High Priority", priorities = listOf(Priority.HIGH, Priority.URGENT))
    )

    private fun getStartOfDay(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getEndOfDay(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
