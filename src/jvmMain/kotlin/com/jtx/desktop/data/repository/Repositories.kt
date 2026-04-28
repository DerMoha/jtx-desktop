package com.jtx.desktop.data.repository

import com.jtx.desktop.data.local.LocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.CalDavHttpException
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

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
                modified = journal.updated,
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
        local.upsertObjectSyncMetadata(entry.createDirtyMetadata(EntryType.JOURNAL))
        onDataChange?.invoke()
    }
    suspend fun update(entry: JournalEntry) {
        local.updateJournal(entry)
        local.markDirty(entry, EntryType.JOURNAL)
        onDataChange?.invoke()
    }
    suspend fun updateJournal(combined: CombinedEntry) {
        val existing = local.getJournalById(combined.id) ?: return
        val now = System.currentTimeMillis()
        val updatedEntry = existing.copy(
            title = combined.title,
            description = combined.description,
            archived = combined.archived,
            updated = now
        )
        local.updateJournal(updatedEntry)
        local.markDirty(updatedEntry, EntryType.JOURNAL)
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.markDeleted(EntryType.JOURNAL, id)
        local.deleteJournal(id)
        onDataChange?.invoke()
    }
    suspend fun restore(id: String) {
        local.restoreFromArchive(EntryType.JOURNAL, id)
        onDataChange?.invoke()
    }
    suspend fun permanentlyDelete(id: String) {
        local.permanentlyDeleteJournal(id)
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
                date = note.created,
                modified = note.updated,
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
        local.upsertObjectSyncMetadata(entry.createDirtyMetadata(EntryType.NOTE))
        onDataChange?.invoke()
    }
    suspend fun update(entry: NoteEntry) {
        local.updateNote(entry)
        local.markDirty(entry, EntryType.NOTE)
        onDataChange?.invoke()
    }
    suspend fun updateNote(combined: CombinedEntry) {
        val existing = local.getNoteById(combined.id) ?: return
        val now = System.currentTimeMillis()
        val updatedEntry = existing.copy(
            title = combined.title,
            description = combined.description,
            archived = combined.archived,
            updated = now
        )
        local.updateNote(updatedEntry)
        local.markDirty(updatedEntry, EntryType.NOTE)
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.markDeleted(EntryType.NOTE, id)
        local.deleteNote(id)
        onDataChange?.invoke()
    }
    suspend fun restore(id: String) {
        local.restoreFromArchive(EntryType.NOTE, id)
        onDataChange?.invoke()
    }
    suspend fun permanentlyDelete(id: String) {
        local.permanentlyDeleteNote(id)
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
                modified = task.updated,
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
        local.upsertObjectSyncMetadata(entry.createDirtyMetadata(EntryType.TASK))
        onDataChange?.invoke()
    }
    suspend fun update(entry: TaskEntry) {
        local.updateTask(entry)
        local.markDirty(entry, EntryType.TASK)
        onDataChange?.invoke()
    }
    suspend fun updateTask(combined: CombinedEntry) {
        val existing = local.getTaskById(combined.id) ?: return
        val now = System.currentTimeMillis()
        val updatedEntry = existing.copy(
            title = combined.title,
            description = combined.description,
            progress = combined.progress ?: 0,
            completed = combined.completed ?: false,
            archived = combined.archived,
            updated = now
        )
        local.updateTask(updatedEntry)
        local.markDirty(updatedEntry, EntryType.TASK)
        onDataChange?.invoke()
    }
    suspend fun updateTaskCompleted(id: String, completed: Boolean) {
        val existing = local.getTaskById(id) ?: return
        val updatedEntry = existing.copy(completed = completed, updated = System.currentTimeMillis())
        local.updateTask(updatedEntry)
        local.markDirty(updatedEntry, EntryType.TASK)
        onDataChange?.invoke()
    }
    suspend fun updateTaskProgress(id: String, progress: Int) {
        val existing = local.getTaskById(id) ?: return
        val updatedEntry = existing.copy(
            progress = progress,
            completed = progress >= 100,
            updated = System.currentTimeMillis()
        )
        local.updateTask(updatedEntry)
        local.markDirty(updatedEntry, EntryType.TASK)
        onDataChange?.invoke()
    }
    suspend fun delete(id: String) {
        local.markDeleted(EntryType.TASK, id)
        local.deleteTask(id)
        onDataChange?.invoke()
    }
    suspend fun restore(id: String) {
        local.restoreFromArchive(EntryType.TASK, id)
        onDataChange?.invoke()
    }
    suspend fun permanentlyDelete(id: String) {
        local.permanentlyDeleteTask(id)
        onDataChange?.invoke()
    }
}

private fun JournalEntry.createDirtyMetadata(entryType: EntryType): ObjectSyncMetadata = ObjectSyncMetadata(
    entryId = id,
    entryType = entryType,
    dirty = true,
    deleted = false,
    uid = uid,
    sequence = sequence,
    lastModified = updated,
    lastError = null
)

private fun NoteEntry.createDirtyMetadata(entryType: EntryType): ObjectSyncMetadata = ObjectSyncMetadata(
    entryId = id,
    entryType = entryType,
    dirty = true,
    deleted = false,
    uid = uid,
    sequence = sequence,
    lastModified = updated,
    lastError = null
)

private fun TaskEntry.createDirtyMetadata(entryType: EntryType): ObjectSyncMetadata = ObjectSyncMetadata(
    entryId = id,
    entryType = entryType,
    dirty = true,
    deleted = false,
    uid = uid,
    sequence = sequence,
    lastModified = updated,
    lastError = null
)

private suspend fun LocalDataSource.markDirty(entry: JournalEntry, entryType: EntryType) {
    val existing = getObjectSyncMetadata(entryType, entry.id)
    upsertObjectSyncMetadata(
        (existing ?: entry.createDirtyMetadata(entryType)).copy(
            dirty = true,
            deleted = false,
            uid = entry.uid,
            sequence = entry.sequence,
            lastModified = entry.updated,
            lastError = null
        )
    )
}

private suspend fun LocalDataSource.markDirty(entry: NoteEntry, entryType: EntryType) {
    val existing = getObjectSyncMetadata(entryType, entry.id)
    upsertObjectSyncMetadata(
        (existing ?: entry.createDirtyMetadata(entryType)).copy(
            dirty = true,
            deleted = false,
            uid = entry.uid,
            sequence = entry.sequence,
            lastModified = entry.updated,
            lastError = null
        )
    )
}

private suspend fun LocalDataSource.markDirty(entry: TaskEntry, entryType: EntryType) {
    val existing = getObjectSyncMetadata(entryType, entry.id)
    upsertObjectSyncMetadata(
        (existing ?: entry.createDirtyMetadata(entryType)).copy(
            dirty = true,
            deleted = false,
            uid = entry.uid,
            sequence = entry.sequence,
            lastModified = entry.updated,
            lastError = null
        )
    )
}

private suspend fun LocalDataSource.markDeleted(entryType: EntryType, entryId: String) {
    val existing = getObjectSyncMetadata(entryType, entryId) ?: return
    if (existing.href == null) {
        deleteObjectSyncMetadata(entryType, entryId)
        return
    }
    upsertObjectSyncMetadata(existing.copy(dirty = true, deleted = true, lastError = null))
}

private fun Any.sequenceValue(): Int = when (this) {
    is JournalEntry -> sequence
    is NoteEntry -> sequence
    is TaskEntry -> sequence
    else -> 0
}

private fun Any.updatedValue(): Long? = when (this) {
    is JournalEntry -> updated
    is NoteEntry -> updated
    is TaskEntry -> updated
    else -> null
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

    suspend fun discoverCollections(credentials: CalDavCredentials): Result<List<CalDavCollection>> {
        return calDavClient.discoverCollections(credentials).onSuccess { collections ->
            collections.forEach { collection ->
                local.upsertCollection(collection)
            }
        }
    }

    suspend fun sync(credentials: CalDavCredentials, collection: String): Result<SyncResult> {
        val settings = local.getSettings()
        val discoveredCollections = discoverCollections(credentials).getOrNull().orEmpty()
        val collectionMetadata = discoveredCollections.findCollection(collection) ?: local.getCollectionByUrl(collection)
        val uploadResult = uploadLocalDeletes(credentials) + uploadLocalCreates(credentials, collection) + uploadLocalUpdates(credentials, collection)
        val fetchResult = calDavClient.fetchEntries(credentials, collection)
        return fetchResult.fold(
            onSuccess = { hrefs ->
                var successCount = uploadResult.successCount
                var failureCount = uploadResult.failureCount
                val failures = uploadResult.failures.toMutableList()
                val conflicts = uploadResult.conflicts.toMutableList()
                successCount += applyRemoteDeletes(collection, hrefs)

                val objectResult = calDavClient.fetchCalendarObjects(credentials, collection, hrefs)
                objectResult.fold(
                    onSuccess = { remoteObjects ->
                        for (remoteObject in remoteObjects) {
                            val entry = parser.parseEntry(remoteObject.data)
                            when (entry) {
                                is JournalEntry -> if (settings.syncJournals) {
                                    val existing = local.getJournalById(entry.id)
                                    if (existing == null) {
                                        local.insertJournal(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertJournal(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, remoteObject.href, existing.updated, entry.updated))
                                    } else {
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    }
                                }
                                is NoteEntry -> if (settings.syncNotes) {
                                    val existing = local.getNoteById(entry.id)
                                    if (existing == null) {
                                        local.insertNote(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertNote(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, remoteObject.href, existing.updated, entry.updated))
                                    } else {
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    }
                                }
                                is TaskEntry -> if (settings.syncTasks) {
                                    val existing = local.getTaskById(entry.id)
                                    if (existing == null) {
                                        local.insertTask(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated < entry.updated) {
                                        local.insertTask(entry)
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    } else if (existing.updated > entry.updated && existing.title != entry.title) {
                                        conflicts.add(SyncConflictInfo(existing, entry, remoteObject.href, existing.updated, entry.updated))
                                    } else {
                                        storeRemoteMetadata(entry, remoteObject, collection)
                                        successCount++
                                    }
                                }
                                else -> {}
                            }
                        }
                    },
                    onFailure = { e ->
                        failureCount += hrefs.size
                        failures.add("Failed to fetch calendar objects: ${e.message}")
                    }
                )
                val now = System.currentTimeMillis()
                collectionMetadata?.let { metadata ->
                    local.upsertCollection(metadata.copy(lastSync = now))
                }
                local.saveSettings(
                    settings.copy(
                        lastSyncTime = now,
                        syncToken = collectionMetadata?.syncToken ?: settings.syncToken,
                        serverCtag = collectionMetadata?.ctag ?: settings.serverCtag
                    )
                )
                Result.success(SyncResult(successCount, failureCount, failures, conflicts))
            },
            onFailure = { Result.failure(it) }
        )
    }

    private fun List<CalDavCollection>.findCollection(collectionUrl: String): CalDavCollection? {
        val normalizedUrl = collectionUrl.trimEnd('/')
        return firstOrNull { collection ->
            collection.url.trimEnd('/') == normalizedUrl || collection.url.trim('/').endsWith(normalizedUrl.trim('/'))
        }
    }

    private suspend fun applyRemoteDeletes(collection: String, remoteHrefs: List<String>): Int {
        val remoteHrefSet = remoteHrefs.map { it.trimEnd('/') }.toSet()
        val metadata = local.getAllObjectSyncMetadata().first()
        var deleteCount = 0

        metadata.filter { item ->
            item.href != null &&
                !item.dirty &&
                !item.deleted &&
                item.collectionUrl.matchesCollection(collection) &&
                item.href.trimEnd('/') !in remoteHrefSet
        }.forEach { item ->
            when (item.entryType) {
                EntryType.JOURNAL -> local.deleteJournal(item.entryId)
                EntryType.NOTE -> local.deleteNote(item.entryId)
                EntryType.TASK -> local.deleteTask(item.entryId)
            }
            local.upsertObjectSyncMetadata(item.copy(deleted = true, dirty = false))
            deleteCount++
        }

        return deleteCount
    }

    private fun String?.matchesCollection(collection: String): Boolean {
        if (this == null) return false
        val left = trimEnd('/')
        val right = collection.trimEnd('/')
        return left == right || left.trim('/').endsWith(right.trim('/'))
    }

    private data class LocalUploadResult(
        val successCount: Int,
        val failureCount: Int,
        val failures: List<String>,
        val conflicts: List<SyncConflictInfo> = emptyList()
    ) {
        operator fun plus(other: LocalUploadResult): LocalUploadResult = LocalUploadResult(
            successCount = successCount + other.successCount,
            failureCount = failureCount + other.failureCount,
            failures = failures + other.failures,
            conflicts = conflicts + other.conflicts
        )
    }

    private suspend fun uploadLocalCreates(credentials: CalDavCredentials, collection: String): LocalUploadResult {
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<String>()
        val creates = local.getDirtyObjectSyncMetadata().filter { metadata ->
            metadata.dirty && !metadata.deleted && metadata.href == null
        }

        creates.forEach { metadata ->
            val entry = when (metadata.entryType) {
                EntryType.JOURNAL -> local.getJournalById(metadata.entryId)
                EntryType.NOTE -> local.getNoteById(metadata.entryId)
                EntryType.TASK -> local.getTaskById(metadata.entryId)
            }
            if (entry == null) {
                local.upsertObjectSyncMetadata(metadata.copy(lastError = "Missing local entry"))
                failureCount++
                failures.add("Missing local ${metadata.entryType.name.lowercase()} ${metadata.entryId}")
                return@forEach
            }

            val href = "${collection.trimEnd('/')}/${metadata.uid}.ics"
            val result = calDavClient.putNewEntry(credentials, href, parser.entryToIcs(entry))
            result.fold(
                onSuccess = { etag ->
                    local.upsertObjectSyncMetadata(
                        metadata.copy(
                            collectionUrl = collection,
                            href = href,
                            filename = href.substringAfterLast('/'),
                            etag = etag,
                            dirty = false,
                            deleted = false,
                            lastError = null
                        )
                    )
                    successCount++
                },
                onFailure = { error ->
                    val message = error.message ?: error::class.simpleName ?: "Unknown error"
                    local.upsertObjectSyncMetadata(metadata.copy(lastError = message))
                    failureCount++
                    failures.add("Failed to upload ${metadata.entryType.name.lowercase()} ${metadata.entryId}: $message")
                }
            )
        }

        return LocalUploadResult(successCount, failureCount, failures)
    }

    private suspend fun uploadLocalDeletes(credentials: CalDavCredentials): LocalUploadResult {
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<String>()
        val deletes = local.getDirtyObjectSyncMetadata().filter { metadata ->
            metadata.dirty && metadata.deleted && metadata.href != null
        }

        deletes.forEach { metadata ->
            val href = metadata.href ?: return@forEach
            val result = calDavClient.deleteEntry(credentials, href)
            result.fold(
                onSuccess = {
                    local.upsertObjectSyncMetadata(metadata.copy(dirty = false, deleted = true, lastError = null))
                    successCount++
                },
                onFailure = { error ->
                    val message = error.message ?: error::class.simpleName ?: "Unknown error"
                    local.upsertObjectSyncMetadata(metadata.copy(lastError = message))
                    failureCount++
                    failures.add("Failed to delete ${metadata.entryType.name.lowercase()} ${metadata.entryId}: $message")
                }
            )
        }

        return LocalUploadResult(successCount, failureCount, failures)
    }

    private suspend fun uploadLocalUpdates(credentials: CalDavCredentials, collection: String): LocalUploadResult {
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflictInfo>()
        val updates = local.getDirtyObjectSyncMetadata().filter { metadata ->
            metadata.dirty && !metadata.deleted && metadata.href != null
        }

        updates.forEach { metadata ->
            val entry = when (metadata.entryType) {
                EntryType.JOURNAL -> local.getJournalById(metadata.entryId)
                EntryType.NOTE -> local.getNoteById(metadata.entryId)
                EntryType.TASK -> local.getTaskById(metadata.entryId)
            }
            val href = metadata.href
            if (entry == null || href == null) {
                local.upsertObjectSyncMetadata(metadata.copy(lastError = "Missing local entry or href"))
                failureCount++
                failures.add("Missing local ${metadata.entryType.name.lowercase()} ${metadata.entryId}")
                return@forEach
            }

            val result = calDavClient.putExistingEntry(credentials, href, parser.entryToIcs(entry), metadata.etag)
            result.fold(
                onSuccess = { etag ->
                    local.upsertObjectSyncMetadata(
                        metadata.copy(
                            etag = etag ?: metadata.etag,
                            dirty = false,
                            deleted = false,
                            sequence = entry.sequenceValue(),
                            lastModified = entry.updatedValue(),
                            lastError = null
                        )
                    )
                    successCount++
                },
                onFailure = { error ->
                    val message = error.message ?: error::class.simpleName ?: "Unknown error"
                    val conflict = detectUploadConflict(credentials, collection, metadata, entry, error)
                    if (conflict != null) {
                        local.upsertObjectSyncMetadata(metadata.copy(lastError = "Conflict: remote object changed since last sync"))
                        conflicts.add(conflict)
                    } else {
                        local.upsertObjectSyncMetadata(metadata.copy(lastError = message))
                        failureCount++
                        failures.add("Failed to upload ${metadata.entryType.name.lowercase()} ${metadata.entryId}: $message")
                    }
                }
            )
        }

        return LocalUploadResult(successCount, failureCount, failures, conflicts)
    }

    private suspend fun detectUploadConflict(
        credentials: CalDavCredentials,
        collection: String,
        metadata: ObjectSyncMetadata,
        localEntry: Any,
        error: Throwable
    ): SyncConflictInfo? {
        val httpError = error as? CalDavHttpException ?: return null
        if (httpError.statusCode !in setOf(409, 412)) return null
        val href = metadata.href ?: return null
        val remoteObject = calDavClient.fetchCalendarObjects(
            credentials,
            metadata.collectionUrl ?: collection,
            listOf(href)
        ).getOrNull()?.firstOrNull() ?: return null
        val serverEntry = parser.parseEntry(remoteObject.data) ?: return null
        return SyncConflictInfo(
            localEntry = localEntry,
            serverEntry = serverEntry,
            serverHref = remoteObject.href,
            localUpdated = localEntry.updatedValue() ?: 0,
            serverUpdated = serverEntry.updatedValue() ?: 0
        )
    }

    private suspend fun storeRemoteMetadata(
        entry: JournalEntry,
        remoteObject: CalDavClient.RemoteCalendarObject,
        collection: String
    ) {
        local.upsertObjectSyncMetadata(
            ObjectSyncMetadata(
                entryId = entry.id,
                entryType = EntryType.JOURNAL,
                collectionUrl = collection,
                href = remoteObject.href,
                filename = remoteObject.href.substringAfterLast('/'),
                etag = remoteObject.etag,
                dirty = false,
                deleted = false,
                uid = entry.uid,
                sequence = entry.sequence,
                lastModified = entry.updated
            )
        )
    }

    private suspend fun storeRemoteMetadata(
        entry: NoteEntry,
        remoteObject: CalDavClient.RemoteCalendarObject,
        collection: String
    ) {
        local.upsertObjectSyncMetadata(
            ObjectSyncMetadata(
                entryId = entry.id,
                entryType = EntryType.NOTE,
                collectionUrl = collection,
                href = remoteObject.href,
                filename = remoteObject.href.substringAfterLast('/'),
                etag = remoteObject.etag,
                dirty = false,
                deleted = false,
                uid = entry.uid,
                sequence = entry.sequence,
                lastModified = entry.updated
            )
        )
    }

    private suspend fun storeRemoteMetadata(
        entry: TaskEntry,
        remoteObject: CalDavClient.RemoteCalendarObject,
        collection: String
    ) {
        local.upsertObjectSyncMetadata(
            ObjectSyncMetadata(
                entryId = entry.id,
                entryType = EntryType.TASK,
                collectionUrl = collection,
                href = remoteObject.href,
                filename = remoteObject.href.substringAfterLast('/'),
                etag = remoteObject.etag,
                dirty = false,
                deleted = false,
                uid = entry.uid,
                sequence = entry.sequence,
                lastModified = entry.updated
            )
        )
    }

    suspend fun uploadJournal(credentials: CalDavCredentials, entry: JournalEntry, collection: String): Result<String> {
        val ics = parser.entryToIcs(entry)
        val href = "$collection/${entry.uid}.ics"
        return calDavClient.putEntry(credentials, href, ics)
    }

    suspend fun uploadNote(credentials: CalDavCredentials, entry: NoteEntry, collection: String): Result<String> {
        val ics = parser.entryToIcs(entry)
        val href = "$collection/${entry.uid}.ics"
        return calDavClient.putEntry(credentials, href, ics)
    }

    suspend fun uploadTask(credentials: CalDavCredentials, entry: TaskEntry, collection: String): Result<String> {
        val ics = parser.entryToIcs(entry)
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
                val result = calDavClient.putExistingEntry(
                    credentials,
                    conflict.serverHref,
                    parser.entryToIcs(conflict.localEntry),
                    null
                )
                val metadata = conflict.localEntry.toSyncMetadata(
                    collection = collection,
                    href = conflict.serverHref,
                    etag = result.getOrNull(),
                    dirty = result.isFailure,
                    lastError = result.exceptionOrNull()?.message
                )
                local.upsertObjectSyncMetadata(metadata)
            }
            ConflictResolution.KEEP_SERVER -> {
                insertServerConflictEntry(conflict.serverEntry)
                local.upsertObjectSyncMetadata(
                    conflict.serverEntry.toSyncMetadata(
                        collection = collection,
                        href = conflict.serverHref,
                        dirty = false,
                        lastError = null
                    )
                )
            }
            ConflictResolution.KEEP_BOTH -> {
                insertServerConflictEntry(conflict.serverEntry)
                val localCopy = conflict.localEntry.copyForConflict()
                insertServerConflictEntry(localCopy)
                when (localCopy) {
                    is JournalEntry -> local.markDirty(localCopy, EntryType.JOURNAL)
                    is NoteEntry -> local.markDirty(localCopy, EntryType.NOTE)
                    is TaskEntry -> local.markDirty(localCopy, EntryType.TASK)
                }
            }
        }
    }

    private suspend fun insertServerConflictEntry(entry: Any) {
        when (entry) {
            is JournalEntry -> local.insertJournal(entry)
            is NoteEntry -> local.insertNote(entry)
            is TaskEntry -> local.insertTask(entry)
        }
    }

    private fun Any.copyForConflict(): Any {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        return when (this) {
            is JournalEntry -> copy(id = id, uid = id, title = "$title (local copy)", created = now, updated = now)
            is NoteEntry -> copy(id = id, uid = id, title = "$title (local copy)", created = now, updated = now)
            is TaskEntry -> copy(id = id, uid = id, title = "$title (local copy)", created = now, updated = now)
            else -> this
        }
    }

    private fun Any.toSyncMetadata(
        collection: String,
        href: String,
        etag: String? = null,
        dirty: Boolean,
        lastError: String?
    ): ObjectSyncMetadata {
        return when (this) {
            is JournalEntry -> ObjectSyncMetadata(id, EntryType.JOURNAL, collection, href, href.substringAfterLast('/'), etag, dirty = dirty, deleted = false, uid = uid, sequence = sequence, lastModified = updated, lastError = lastError)
            is NoteEntry -> ObjectSyncMetadata(id, EntryType.NOTE, collection, href, href.substringAfterLast('/'), etag, dirty = dirty, deleted = false, uid = uid, sequence = sequence, lastModified = updated, lastError = lastError)
            is TaskEntry -> ObjectSyncMetadata(id, EntryType.TASK, collection, href, href.substringAfterLast('/'), etag, dirty = dirty, deleted = false, uid = uid, sequence = sequence, lastModified = updated, lastError = lastError)
            else -> error("Unsupported conflict entry: ${this::class.simpleName}")
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
