package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.AppSettings
import com.jtx.desktop.domain.model.CalDavCollection
import com.jtx.desktop.domain.model.JournalEntry
import com.jtx.desktop.domain.model.NoteEntry
import com.jtx.desktop.domain.model.ObjectSyncMetadata
import com.jtx.desktop.domain.model.TaskEntry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CURRENT_BACKUP_VERSION = 1

private val backupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
data class JsonBackupEnvelope(
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val journals: List<JournalEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
    val tasks: List<TaskEntry> = emptyList(),
    val collections: List<CalDavCollection> = emptyList(),
    val objectSyncMetadata: List<ObjectSyncMetadata> = emptyList(),
    val settings: AppSettings = AppSettings()
)

data class JsonBackupRestoreResult(
    val journals: Int,
    val notes: Int,
    val tasks: Int,
    val collections: Int,
    val syncMetadata: Int
) {
    val totalEntries: Int = journals + notes + tasks
}

suspend fun LocalDataSource.exportJsonBackup(): String {
    val envelope = JsonBackupEnvelope(
        journals = getAllJournals(includeArchived = true).first(),
        notes = getAllNotes(includeArchived = true).first(),
        tasks = getAllTasks(includeArchived = true).first(),
        collections = getAllCollections().first(),
        objectSyncMetadata = getAllObjectSyncMetadata().first(),
        settings = getSettings()
    )
    return backupJson.encodeToString(envelope)
}

suspend fun LocalDataSource.importJsonBackup(json: String): JsonBackupRestoreResult {
    val envelope = backupJson.decodeFromString<JsonBackupEnvelope>(json)
    require(envelope.version in 1..CURRENT_BACKUP_VERSION) {
        "Unsupported backup version ${envelope.version}"
    }

    envelope.journals.forEach { insertJournal(it) }
    envelope.notes.forEach { insertNote(it) }
    envelope.tasks.forEach { insertTask(it) }
    envelope.collections.forEach { upsertCollection(it) }
    envelope.objectSyncMetadata.forEach { upsertObjectSyncMetadata(it) }
    saveSettings(envelope.settings)

    return JsonBackupRestoreResult(
        journals = envelope.journals.size,
        notes = envelope.notes.size,
        tasks = envelope.tasks.size,
        collections = envelope.collections.size,
        syncMetadata = envelope.objectSyncMetadata.size
    )
}
