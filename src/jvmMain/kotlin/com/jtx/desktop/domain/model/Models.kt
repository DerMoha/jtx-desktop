package com.jtx.desktop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class JournalEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
    val descriptionFormat: DescriptionFormat = DescriptionFormat.PLAIN,
    val dtstart: Long?,
    val startTimezone: String? = null,
    val dtend: Long?,
    val endTimezone: String? = null,
    val categories: List<String>,
    val created: Long,
    val updated: Long,
    val color: String?,
    val location: String?,
    val comment: String?,
    val relatedEntries: List<String> = emptyList(),
    val attachments: List<EntryAttachment> = emptyList(),
    val comments: List<EntryComment> = emptyList(),
    val unknownProperties: List<UnknownProperty> = emptyList(),
    val sequence: Int = 0,
    val url: String? = null,
    val contact: String? = null,
    val geo: String? = null,
    val classification: String? = null,
    val archived: Boolean = false
)

@Serializable
data class NoteEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
    val descriptionFormat: DescriptionFormat = DescriptionFormat.PLAIN,
    val categories: List<String>,
    val created: Long,
    val updated: Long,
    val color: String?,
    val location: String? = null,
    val relatedEntries: List<String> = emptyList(),
    val attachments: List<EntryAttachment> = emptyList(),
    val comments: List<EntryComment> = emptyList(),
    val unknownProperties: List<UnknownProperty> = emptyList(),
    val sequence: Int = 0,
    val url: String? = null,
    val contact: String? = null,
    val geo: String? = null,
    val classification: String? = null,
    val archived: Boolean = false
)

@Serializable
data class TaskEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
    val descriptionFormat: DescriptionFormat = DescriptionFormat.PLAIN,
    val due: Long?,
    val start: Long?,
    val completed: Boolean,
    val progress: Int,
    val categories: List<String>,
    val created: Long,
    val updated: Long,
    val color: String?,
    val location: String?,
    val subtasks: List<Subtask>,
    val relatedEntries: List<String>,
    val recurrenceRule: RecurrenceRule? = null,
    val recurrenceDates: List<Long> = emptyList(),
    val exceptionDates: List<Long> = emptyList(),
    val recurrenceId: Long? = null,
    val archived: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val priority: Priority = Priority.NONE,
    val timezone: String? = null,
    val startTimezone: String? = null,
    val dueTimezone: String? = null,
    val completedTimezone: String? = null,
    val recurrenceTimezone: String? = null,
    val recurrenceIdTimezone: String? = null,
    val attachments: List<EntryAttachment> = emptyList(),
    val comments: List<EntryComment> = emptyList(),
    val unknownProperties: List<UnknownProperty> = emptyList(),
    val sequence: Int = 0,
    val url: String? = null,
    val contact: String? = null,
    val geo: String? = null,
    val classification: String? = null
)

@Serializable
data class EntryAttachment(
    val uri: String,
    val filename: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val localPath: String? = null
)

@Serializable
data class EntryComment(
    val text: String,
    val author: String? = null,
    val created: Long? = null
)

@Serializable
data class UnknownProperty(
    val line: String
)

@Serializable
data class Reminder(
    val minutesBefore: Int,
    val soundEnabled: Boolean = true
)

@Serializable
enum class Priority {
    NONE, LOW, MEDIUM, HIGH, URGENT
}

enum class DescriptionFormat {
    PLAIN, MARKDOWN
}

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean
)

// Subtasks are stored as desktop-local checklist rows. Cross-entry hierarchy that
// should sync through CalDAV is represented by relatedEntries/RELATED-TO instead.

@Serializable
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val endDate: Long? = null,
    val count: Int? = null,
    val rawRule: String? = null
)

@Serializable
enum class RecurrenceFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class EntryType {
    JOURNAL, NOTE, TASK
}

data class CombinedEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val description: String,
    val date: Long?,
    val modified: Long? = null,
    val categories: List<String>,
    val color: String?,
    val progress: Int?,
    val completed: Boolean?,
    val archived: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val endDate: Long? = null,
    val descriptionFormat: DescriptionFormat = DescriptionFormat.PLAIN,
    val location: String? = null,
    val comments: List<EntryComment> = emptyList(),
    val attachments: List<EntryAttachment> = emptyList(),
    val relatedEntries: List<String> = emptyList(),
    val startDate: Long? = null,
    val priority: Priority = Priority.NONE,
    val recurrenceRule: RecurrenceRule? = null,
    val recurrenceDates: List<Long> = emptyList(),
    val exceptionDates: List<Long> = emptyList(),
    val recurrenceId: Long? = null,
    val reminders: List<Reminder> = emptyList(),
    val subtasks: List<Subtask> = emptyList()
)

enum class SyncStatus {
    SYNCED, PENDING, CONFLICT
}

@Serializable
data class CalDavCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

@Serializable
data class CalDavCollection(
    val url: String,
    val displayName: String,
    val color: String? = null,
    val supportedComponents: List<EntryType> = emptyList(),
    val readOnly: Boolean = false,
    val syncToken: String? = null,
    val ctag: String? = null,
    val lastSync: Long? = null
)

@Serializable
data class ObjectSyncMetadata(
    val entryId: String,
    val entryType: EntryType,
    val collectionUrl: String? = null,
    val href: String? = null,
    val filename: String? = null,
    val etag: String? = null,
    val scheduleTag: String? = null,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val uid: String,
    val sequence: Int = 0,
    val lastModified: Long? = null,
    val lastError: String? = null
)

@Serializable
data class AppSettings(
    val credentials: CalDavCredentials? = null,
    val collection: String? = null,
    val lastSyncTime: Long? = null,
    val sortPreference: SortPreference = SortPreference(SortField.DATE, false),
    val darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    val kanbanColumns: List<KanbanColumnConfig> = defaultKanbanColumns,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val autoSyncIntervalMinutes: Int = 15,
    val syncOnChange: Boolean = true,
    val syncToken: String? = null,
    val serverCtag: String? = null,
    val syncJournals: Boolean = true,
    val syncNotes: Boolean = true,
    val syncTasks: Boolean = true,
    val pendingSyncCount: Int = 0,
    val savedFilters: List<SavedFilter> = emptyList(),
    val listDensity: ListDensity = ListDensity.COMFORTABLE
)

@Serializable
data class SortPreference(
    val field: SortField = SortField.DATE,
    val ascending: Boolean = false
)

@Serializable
enum class SortField {
    DATE, TITLE, MODIFIED
}

@Serializable
enum class DarkModePreference {
    LIGHT, DARK, SYSTEM
}

@Serializable
enum class ListDensity {
    COMPACT, COMFORTABLE
}

@Serializable
data class KanbanColumnConfig(
    val id: String,
    val title: String,
    val color: Long,
    val progressMin: Int,
    val progressMax: Int
)

val defaultKanbanColumns = listOf(
    KanbanColumnConfig("todo", "To Do", 0xFF2196F3, 0, 0),
    KanbanColumnConfig("inprogress", "In Progress", 0xFFFFC107, 1, 99),
    KanbanColumnConfig("done", "Done", 0xFF4CAF50, 100, 100)
)

@Serializable
data class EntryTemplate(
    val id: String,
    val type: EntryType,
    val title: String,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val color: String? = null,
    val dueDays: Int? = null,
    val recurrence: RecurrenceRule? = null
)

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: String,
    val description: String = ""
)

@Serializable
data class SavedFilter(
    val id: String,
    val name: String,
    val query: String = "",
    val entryType: EntryType? = null,
    val categories: List<String> = emptyList(),
    val priorities: List<Priority> = emptyList(),
    val completed: Boolean? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val includeArchived: Boolean = false,
    val modifiedOnly: Boolean = false
)

enum class SyncState {
    IDLE, SYNCING, SUCCESS, PENDING, ERROR, CONFLICT, OFFLINE, READ_ONLY
}
