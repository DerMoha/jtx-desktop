package com.jtx.desktop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class JournalEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
    val dtstart: Long?,
    val dtend: Long?,
    val categories: List<String>,
    val created: Long,
    val updated: Long,
    val color: String?,
    val location: String?,
    val comment: String?
)

@Serializable
data class NoteEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
    val categories: List<String>,
    val created: Long,
    val updated: Long,
    val color: String?
)

@Serializable
data class TaskEntry(
    val id: String,
    val uid: String,
    val title: String,
    val description: String,
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
    val relatedEntries: List<String>
)

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean
)

enum class EntryType {
    JOURNAL, NOTE, TASK
}

data class CombinedEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val description: String,
    val date: Long?,
    val categories: List<String>,
    val color: String?,
    val progress: Int?,
    val completed: Boolean?
)

@Serializable
data class CalDavCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

@Serializable
data class AppSettings(
    val credentials: CalDavCredentials? = null,
    val collection: String? = null,
    val lastSyncTime: Long? = null
)