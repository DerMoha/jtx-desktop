package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class SqliteLocalDataSource(private val dbPath: String) : LocalDataSource {

    private var _journals = MutableStateFlow<List<JournalEntry>>(emptyList())
    private var _notes = MutableStateFlow<List<NoteEntry>>(emptyList())
    private var _tasks = MutableStateFlow<List<TaskEntry>>(emptyList())
    private var _collections = MutableStateFlow<List<CalDavCollection>>(emptyList())
    private var _objectSyncMetadata = MutableStateFlow<List<ObjectSyncMetadata>>(emptyList())

    private var settings = AppSettings()
    private var settingsChanged = true

    init {
        initializeDatabase()
        loadAllData()
        loadSettings()
    }

    private fun initializeDatabase() {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS journals (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        description_format TEXT NOT NULL DEFAULT 'PLAIN',
                        dtstart INTEGER,
                        dtstart_timezone TEXT,
                        dtend INTEGER,
                        dtend_timezone TEXT,
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        comment TEXT,
                        related_entries TEXT NOT NULL DEFAULT '[]',
                        archived INTEGER DEFAULT 0
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        description_format TEXT NOT NULL DEFAULT 'PLAIN',
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        related_entries TEXT NOT NULL DEFAULT '[]',
                        archived INTEGER DEFAULT 0
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tasks (
                        id TEXT PRIMARY KEY,
                        uid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        due INTEGER,
                        due_timezone TEXT,
                        start INTEGER,
                        start_timezone TEXT,
                        completed INTEGER NOT NULL,
                        completed_timezone TEXT,
                        progress INTEGER NOT NULL,
                        categories TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        updated INTEGER NOT NULL,
                        color TEXT,
                        location TEXT,
                        subtasks TEXT NOT NULL,
                        related_entries TEXT NOT NULL,
                        recurrence_rule TEXT,
                        recurrence_dates TEXT NOT NULL DEFAULT '[]',
                        exception_dates TEXT NOT NULL DEFAULT '[]',
                        recurrence_id INTEGER,
                        recurrence_timezone TEXT,
                        recurrence_id_timezone TEXT,
                        priority TEXT NOT NULL DEFAULT 'NONE',
                        timezone TEXT,
                        archived INTEGER DEFAULT 0
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        url TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        color TEXT,
                        supported_components TEXT NOT NULL,
                        read_only INTEGER NOT NULL DEFAULT 0,
                        sync_token TEXT,
                        ctag TEXT,
                        last_sync INTEGER
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS object_sync_metadata (
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
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        task_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        minutes_before INTEGER NOT NULL,
                        sound_enabled INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY (task_id, position)
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS attachments (
                        entry_type TEXT NOT NULL,
                        entry_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        filename TEXT,
                        mime_type TEXT,
                        size INTEGER,
                        local_path TEXT,
                        PRIMARY KEY (entry_type, entry_id, position)
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS comments (
                        entry_type TEXT NOT NULL,
                        entry_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        author TEXT,
                        created INTEGER,
                        PRIMARY KEY (entry_type, entry_id, position)
                    )
                    """
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """
                )
                addColumnIfMissing(stmt, "journals", "description_format", "TEXT NOT NULL DEFAULT 'PLAIN'")
                addColumnIfMissing(stmt, "journals", "dtstart_timezone", "TEXT")
                addColumnIfMissing(stmt, "journals", "dtend_timezone", "TEXT")
                addColumnIfMissing(stmt, "journals", "related_entries", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(stmt, "notes", "description_format", "TEXT NOT NULL DEFAULT 'PLAIN'")
                addColumnIfMissing(stmt, "notes", "related_entries", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(stmt, "tasks", "due_timezone", "TEXT")
                addColumnIfMissing(stmt, "tasks", "start_timezone", "TEXT")
                addColumnIfMissing(stmt, "tasks", "completed_timezone", "TEXT")
                addColumnIfMissing(stmt, "tasks", "recurrence_dates", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(stmt, "tasks", "exception_dates", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(stmt, "tasks", "recurrence_id", "INTEGER")
                addColumnIfMissing(stmt, "tasks", "recurrence_timezone", "TEXT")
                addColumnIfMissing(stmt, "tasks", "recurrence_id_timezone", "TEXT")
                addColumnIfMissing(stmt, "tasks", "priority", "TEXT NOT NULL DEFAULT 'NONE'")
                addColumnIfMissing(stmt, "tasks", "timezone", "TEXT")
            }
        }
    }

    private fun addColumnIfMissing(stmt: Statement, table: String, column: String, definition: String) {
        stmt.executeQuery("PRAGMA table_info($table)").use { rs ->
            while (rs.next()) {
                if (rs.getString("name") == column) return
            }
        }
        stmt.execute("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    private fun <T> useConnection(block: (Connection) -> T): T {
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        return try {
            block(conn)
        } finally {
            conn.close()
        }
    }

    private fun loadAllData() {
        _journals.value = loadJournals(includeArchived = true)
        _notes.value = loadNotes(includeArchived = true)
        _tasks.value = loadTasks(includeArchived = true)
        _collections.value = loadCollections()
        _objectSyncMetadata.value = loadObjectSyncMetadata()
    }

    private fun loadJournals(includeArchived: Boolean = false): List<JournalEntry> = useConnection { conn ->
        conn.createStatement().use { stmt ->
            val whereClause = if (includeArchived) "" else " WHERE archived = 0"
            stmt.executeQuery("SELECT * FROM journals$whereClause").use { rs ->
                val list = mutableListOf<JournalEntry>()
                while (rs.next()) {
                    list.add(journalFromResultSet(rs))
                }
                list
            }
        }
    }

    private fun loadNotes(includeArchived: Boolean = false): List<NoteEntry> = useConnection { conn ->
        conn.createStatement().use { stmt ->
            val whereClause = if (includeArchived) "" else " WHERE archived = 0"
            stmt.executeQuery("SELECT * FROM notes$whereClause").use { rs ->
                val list = mutableListOf<NoteEntry>()
                while (rs.next()) {
                    list.add(noteFromResultSet(rs))
                }
                list
            }
        }
    }

    private fun loadTasks(includeArchived: Boolean = false): List<TaskEntry> = useConnection { conn ->
        conn.createStatement().use { stmt ->
            val whereClause = if (includeArchived) "" else " WHERE archived = 0"
            stmt.executeQuery("SELECT * FROM tasks$whereClause").use { rs ->
                val list = mutableListOf<TaskEntry>()
                while (rs.next()) {
                    list.add(taskFromResultSet(rs))
                }
                list
            }
        }
    }

    private fun journalFromResultSet(rs: ResultSet): JournalEntry {
        return JournalEntry(
            id = rs.getString("id"),
            uid = rs.getString("uid"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            descriptionFormat = DescriptionFormat.valueOf(rs.getString("description_format")),
            dtstart = rs.getObject("dtstart") as? Long,
            startTimezone = rs.getString("dtstart_timezone"),
            dtend = rs.getObject("dtend") as? Long,
            endTimezone = rs.getString("dtend_timezone"),
            categories = Json.decodeFromString(rs.getString("categories")),
            created = rs.getLong("created"),
            updated = rs.getLong("updated"),
            color = rs.getString("color"),
            location = rs.getString("location"),
            comment = rs.getString("comment"),
            relatedEntries = Json.decodeFromString(rs.getString("related_entries")),
            attachments = loadEntryAttachments(EntryType.JOURNAL, rs.getString("id")),
            comments = loadEntryComments(EntryType.JOURNAL, rs.getString("id")),
            archived = rs.getInt("archived") == 1
        )
    }

    private fun noteFromResultSet(rs: ResultSet): NoteEntry {
        return NoteEntry(
            id = rs.getString("id"),
            uid = rs.getString("uid"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            descriptionFormat = DescriptionFormat.valueOf(rs.getString("description_format")),
            categories = Json.decodeFromString(rs.getString("categories")),
            created = rs.getLong("created"),
            updated = rs.getLong("updated"),
            color = rs.getString("color"),
            location = rs.getString("location"),
            relatedEntries = Json.decodeFromString(rs.getString("related_entries")),
            attachments = loadEntryAttachments(EntryType.NOTE, rs.getString("id")),
            comments = loadEntryComments(EntryType.NOTE, rs.getString("id")),
            archived = rs.getInt("archived") == 1
        )
    }

    private fun taskFromResultSet(rs: ResultSet): TaskEntry {
        return TaskEntry(
            id = rs.getString("id"),
            uid = rs.getString("uid"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            due = rs.getObject("due") as? Long,
            dueTimezone = rs.getString("due_timezone"),
            start = rs.getObject("start") as? Long,
            startTimezone = rs.getString("start_timezone"),
            completed = rs.getInt("completed") == 1,
            completedTimezone = rs.getString("completed_timezone"),
            progress = rs.getInt("progress"),
            categories = Json.decodeFromString(rs.getString("categories")),
            created = rs.getLong("created"),
            updated = rs.getLong("updated"),
            color = rs.getString("color"),
            location = rs.getString("location"),
            subtasks = Json.decodeFromString(rs.getString("subtasks")),
            relatedEntries = Json.decodeFromString(rs.getString("related_entries")),
            recurrenceRule = rs.getString("recurrence_rule")?.let { Json.decodeFromString(it) },
            recurrenceDates = Json.decodeFromString(rs.getString("recurrence_dates")),
            exceptionDates = Json.decodeFromString(rs.getString("exception_dates")),
            recurrenceId = rs.getObject("recurrence_id") as? Long,
            recurrenceTimezone = rs.getString("recurrence_timezone"),
            recurrenceIdTimezone = rs.getString("recurrence_id_timezone"),
            priority = Priority.valueOf(rs.getString("priority")),
            timezone = rs.getString("timezone"),
            archived = rs.getInt("archived") == 1,
            reminders = loadTaskReminders(rs.getString("id")),
            attachments = loadEntryAttachments(EntryType.TASK, rs.getString("id")),
            comments = loadEntryComments(EntryType.TASK, rs.getString("id"))
        )
    }

    private fun loadTaskReminders(taskId: String): List<Reminder> = useConnection { conn ->
        conn.prepareStatement("SELECT * FROM reminders WHERE task_id = ? ORDER BY position").use { ps ->
            ps.setString(1, taskId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<Reminder>()
                while (rs.next()) {
                    list.add(
                        Reminder(
                            minutesBefore = rs.getInt("minutes_before"),
                            soundEnabled = rs.getInt("sound_enabled") == 1
                        )
                    )
                }
                list
            }
        }
    }

    private fun loadEntryAttachments(entryType: EntryType, entryId: String): List<EntryAttachment> = useConnection { conn ->
        conn.prepareStatement("SELECT * FROM attachments WHERE entry_type = ? AND entry_id = ? ORDER BY position").use { ps ->
            ps.setString(1, entryType.name)
            ps.setString(2, entryId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<EntryAttachment>()
                while (rs.next()) {
                    val size = rs.getLong("size")
                    list.add(
                        EntryAttachment(
                            uri = rs.getString("uri"),
                            filename = rs.getString("filename"),
                            mimeType = rs.getString("mime_type"),
                            size = if (rs.wasNull()) null else size,
                            localPath = rs.getString("local_path")
                        )
                    )
                }
                list
            }
        }
    }

    private fun loadEntryComments(entryType: EntryType, entryId: String): List<EntryComment> = useConnection { conn ->
        conn.prepareStatement("SELECT * FROM comments WHERE entry_type = ? AND entry_id = ? ORDER BY position").use { ps ->
            ps.setString(1, entryType.name)
            ps.setString(2, entryId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<EntryComment>()
                while (rs.next()) {
                    val created = rs.getLong("created")
                    list.add(
                        EntryComment(
                            text = rs.getString("text"),
                            author = rs.getString("author"),
                            created = if (rs.wasNull()) null else created
                        )
                    )
                }
                list
            }
        }
    }

    private fun loadCollections(): List<CalDavCollection> = useConnection { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM collections").use { rs ->
                val list = mutableListOf<CalDavCollection>()
                while (rs.next()) {
                    list.add(collectionFromResultSet(rs))
                }
                list
            }
        }
    }

    private fun collectionFromResultSet(rs: ResultSet): CalDavCollection {
        return CalDavCollection(
            url = rs.getString("url"),
            displayName = rs.getString("display_name"),
            color = rs.getString("color"),
            supportedComponents = Json.decodeFromString(rs.getString("supported_components")),
            readOnly = rs.getInt("read_only") == 1,
            syncToken = rs.getString("sync_token"),
            ctag = rs.getString("ctag"),
            lastSync = rs.getObject("last_sync") as? Long
        )
    }

    private fun loadObjectSyncMetadata(): List<ObjectSyncMetadata> = useConnection { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM object_sync_metadata").use { rs ->
                val list = mutableListOf<ObjectSyncMetadata>()
                while (rs.next()) {
                    list.add(objectSyncMetadataFromResultSet(rs))
                }
                list
            }
        }
    }

    private fun objectSyncMetadataFromResultSet(rs: ResultSet): ObjectSyncMetadata {
        return ObjectSyncMetadata(
            entryType = EntryType.valueOf(rs.getString("entry_type")),
            entryId = rs.getString("entry_id"),
            collectionUrl = rs.getString("collection_url"),
            href = rs.getString("href"),
            filename = rs.getString("filename"),
            etag = rs.getString("etag"),
            scheduleTag = rs.getString("schedule_tag"),
            dirty = rs.getInt("dirty") == 1,
            deleted = rs.getInt("deleted") == 1,
            uid = rs.getString("uid"),
            sequence = rs.getInt("sequence"),
            lastModified = rs.getObject("last_modified") as? Long
        )
    }

    override fun getAllJournals(includeArchived: Boolean): Flow<List<JournalEntry>> =
        _journals.map { journals -> if (includeArchived) journals else journals.filter { !it.archived } }
    override fun getAllNotes(includeArchived: Boolean): Flow<List<NoteEntry>> =
        _notes.map { notes -> if (includeArchived) notes else notes.filter { !it.archived } }
    override fun getAllTasks(includeArchived: Boolean): Flow<List<TaskEntry>> =
        _tasks.map { tasks -> if (includeArchived) tasks else tasks.filter { !it.archived } }
    override fun getAllCollections(): Flow<List<CalDavCollection>> = _collections
    override fun getAllObjectSyncMetadata(): Flow<List<ObjectSyncMetadata>> = _objectSyncMetadata

    override suspend fun getJournalById(id: String): JournalEntry? = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("SELECT * FROM journals WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) journalFromResultSet(rs) else null
                }
            }
        }
    }

    override suspend fun getNoteById(id: String): NoteEntry? = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("SELECT * FROM notes WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) noteFromResultSet(rs) else null
                }
            }
        }
    }

    override suspend fun getTaskById(id: String): TaskEntry? = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("SELECT * FROM tasks WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) taskFromResultSet(rs) else null
                }
            }
        }
    }

    override suspend fun getCollectionByUrl(url: String): CalDavCollection? = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("SELECT * FROM collections WHERE url = ?").use { ps ->
                ps.setString(1, url)
                ps.executeQuery().use { rs ->
                    if (rs.next()) collectionFromResultSet(rs) else null
                }
            }
        }
    }

    override suspend fun getObjectSyncMetadata(entryType: EntryType, entryId: String): ObjectSyncMetadata? =
        withContext(Dispatchers.IO) {
            useConnection { conn ->
                conn.prepareStatement("SELECT * FROM object_sync_metadata WHERE entry_type = ? AND entry_id = ?").use { ps ->
                    ps.setString(1, entryType.name)
                    ps.setString(2, entryId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) objectSyncMetadataFromResultSet(rs) else null
                    }
                }
            }
        }

    override suspend fun getDirtyObjectSyncMetadata(): List<ObjectSyncMetadata> = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM object_sync_metadata WHERE dirty = 1 OR deleted = 1").use { rs ->
                    val list = mutableListOf<ObjectSyncMetadata>()
                    while (rs.next()) {
                        list.add(objectSyncMetadataFromResultSet(rs))
                    }
                    list
                }
            }
        }
    }

    override suspend fun getRemindersForTask(taskId: String): List<Reminder> = withContext(Dispatchers.IO) {
        loadTaskReminders(taskId)
    }

    override suspend fun getAttachmentsForEntry(entryType: EntryType, entryId: String): List<EntryAttachment> =
        withContext(Dispatchers.IO) {
            loadEntryAttachments(entryType, entryId)
        }

    override suspend fun getCommentsForEntry(entryType: EntryType, entryId: String): List<EntryComment> =
        withContext(Dispatchers.IO) {
            loadEntryComments(entryType, entryId)
        }

    override suspend fun insertJournal(entry: JournalEntry) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement(
                """INSERT OR REPLACE INTO journals
                   (id, uid, title, description, description_format, dtstart, dtstart_timezone, dtend, dtend_timezone, categories, created, updated, color, location, comment, related_entries, archived)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, entry.id)
                ps.setString(2, entry.uid)
                ps.setString(3, entry.title)
                ps.setString(4, entry.description)
                ps.setString(5, entry.descriptionFormat.name)
                ps.setObject(6, entry.dtstart)
                ps.setString(7, entry.startTimezone)
                ps.setObject(8, entry.dtend)
                ps.setString(9, entry.endTimezone)
                ps.setString(10, Json.encodeToString(entry.categories))
                ps.setLong(11, entry.created)
                ps.setLong(12, entry.updated)
                ps.setString(13, entry.color)
                ps.setString(14, entry.location)
                ps.setString(15, entry.comment)
                ps.setString(16, Json.encodeToString(entry.relatedEntries))
                ps.setInt(17, if (entry.archived) 1 else 0)
                ps.executeUpdate()
            }
        }
        replaceEntryAttachments(EntryType.JOURNAL, entry.id, entry.attachments)
        replaceEntryComments(EntryType.JOURNAL, entry.id, entry.comments)
        _journals.value = loadJournals(includeArchived = true)
    }

    override suspend fun insertNote(entry: NoteEntry) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement(
                """INSERT OR REPLACE INTO notes
                   (id, uid, title, description, description_format, categories, created, updated, color, location, related_entries, archived)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, entry.id)
                ps.setString(2, entry.uid)
                ps.setString(3, entry.title)
                ps.setString(4, entry.description)
                ps.setString(5, entry.descriptionFormat.name)
                ps.setString(6, Json.encodeToString(entry.categories))
                ps.setLong(7, entry.created)
                ps.setLong(8, entry.updated)
                ps.setString(9, entry.color)
                ps.setString(10, entry.location)
                ps.setString(11, Json.encodeToString(entry.relatedEntries))
                ps.setInt(12, if (entry.archived) 1 else 0)
                ps.executeUpdate()
            }
        }
        replaceEntryAttachments(EntryType.NOTE, entry.id, entry.attachments)
        replaceEntryComments(EntryType.NOTE, entry.id, entry.comments)
        _notes.value = loadNotes(includeArchived = true)
    }

    override suspend fun insertTask(entry: TaskEntry) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement(
                """INSERT OR REPLACE INTO tasks
                   (id, uid, title, description, due, due_timezone, start, start_timezone, completed, completed_timezone, progress, categories, created, updated, color, location, subtasks, related_entries, recurrence_rule, recurrence_dates, exception_dates, recurrence_id, recurrence_timezone, recurrence_id_timezone, priority, timezone, archived)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, entry.id)
                ps.setString(2, entry.uid)
                ps.setString(3, entry.title)
                ps.setString(4, entry.description)
                ps.setObject(5, entry.due)
                ps.setString(6, entry.dueTimezone)
                ps.setObject(7, entry.start)
                ps.setString(8, entry.startTimezone)
                ps.setInt(9, if (entry.completed) 1 else 0)
                ps.setString(10, entry.completedTimezone)
                ps.setInt(11, entry.progress)
                ps.setString(12, Json.encodeToString(entry.categories))
                ps.setLong(13, entry.created)
                ps.setLong(14, entry.updated)
                ps.setString(15, entry.color)
                ps.setString(16, entry.location)
                ps.setString(17, Json.encodeToString(entry.subtasks))
                ps.setString(18, Json.encodeToString(entry.relatedEntries))
                ps.setString(19, entry.recurrenceRule?.let { Json.encodeToString(it) })
                ps.setString(20, Json.encodeToString(entry.recurrenceDates))
                ps.setString(21, Json.encodeToString(entry.exceptionDates))
                ps.setObject(22, entry.recurrenceId)
                ps.setString(23, entry.recurrenceTimezone)
                ps.setString(24, entry.recurrenceIdTimezone)
                ps.setString(25, entry.priority.name)
                ps.setString(26, entry.timezone)
                ps.setInt(27, if (entry.archived) 1 else 0)
                ps.executeUpdate()
            }
        }
        replaceTaskReminders(entry.id, entry.reminders)
        replaceEntryAttachments(EntryType.TASK, entry.id, entry.attachments)
        replaceEntryComments(EntryType.TASK, entry.id, entry.comments)
        _tasks.value = loadTasks(includeArchived = true)
    }

    override suspend fun updateJournal(entry: JournalEntry) {
        insertJournal(entry)
    }

    override suspend fun updateNote(entry: NoteEntry) {
        insertNote(entry)
    }

    override suspend fun updateTask(entry: TaskEntry) {
        insertTask(entry)
    }

    override suspend fun upsertCollection(collection: CalDavCollection) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement(
                """INSERT OR REPLACE INTO collections
                   (url, display_name, color, supported_components, read_only, sync_token, ctag, last_sync)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, collection.url)
                ps.setString(2, collection.displayName)
                ps.setString(3, collection.color)
                ps.setString(4, Json.encodeToString(collection.supportedComponents))
                ps.setInt(5, if (collection.readOnly) 1 else 0)
                ps.setString(6, collection.syncToken)
                ps.setString(7, collection.ctag)
                ps.setObject(8, collection.lastSync)
                ps.executeUpdate()
            }
        }
        _collections.value = loadCollections()
    }

    override suspend fun deleteCollection(url: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM collections WHERE url = ?").use { ps ->
                ps.setString(1, url)
                ps.executeUpdate()
            }
        }
        _collections.value = loadCollections()
    }

    override suspend fun upsertObjectSyncMetadata(metadata: ObjectSyncMetadata) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement(
                """INSERT OR REPLACE INTO object_sync_metadata
                   (entry_type, entry_id, collection_url, href, filename, etag, schedule_tag, dirty, deleted, uid, sequence, last_modified)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                ps.setString(1, metadata.entryType.name)
                ps.setString(2, metadata.entryId)
                ps.setString(3, metadata.collectionUrl)
                ps.setString(4, metadata.href)
                ps.setString(5, metadata.filename)
                ps.setString(6, metadata.etag)
                ps.setString(7, metadata.scheduleTag)
                ps.setInt(8, if (metadata.dirty) 1 else 0)
                ps.setInt(9, if (metadata.deleted) 1 else 0)
                ps.setString(10, metadata.uid)
                ps.setInt(11, metadata.sequence)
                ps.setObject(12, metadata.lastModified)
                ps.executeUpdate()
            }
        }
        _objectSyncMetadata.value = loadObjectSyncMetadata()
    }

    override suspend fun deleteObjectSyncMetadata(entryType: EntryType, entryId: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM object_sync_metadata WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, entryType.name)
                ps.setString(2, entryId)
                ps.executeUpdate()
            }
        }
        _objectSyncMetadata.value = loadObjectSyncMetadata()
    }

    override suspend fun replaceTaskReminders(taskId: String, reminders: List<Reminder>) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM reminders WHERE task_id = ?").use { ps ->
                ps.setString(1, taskId)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """INSERT INTO reminders (task_id, position, minutes_before, sound_enabled)
                   VALUES (?, ?, ?, ?)"""
            ).use { ps ->
                reminders.forEachIndexed { index, reminder ->
                    ps.setString(1, taskId)
                    ps.setInt(2, index)
                    ps.setInt(3, reminder.minutesBefore)
                    ps.setInt(4, if (reminder.soundEnabled) 1 else 0)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
        _tasks.value = loadTasks(includeArchived = true)
    }

    override suspend fun replaceEntryAttachments(
        entryType: EntryType,
        entryId: String,
        attachments: List<EntryAttachment>
    ): Unit = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM attachments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, entryType.name)
                ps.setString(2, entryId)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """INSERT INTO attachments
                   (entry_type, entry_id, position, uri, filename, mime_type, size, local_path)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                attachments.forEachIndexed { index, attachment ->
                    ps.setString(1, entryType.name)
                    ps.setString(2, entryId)
                    ps.setInt(3, index)
                    ps.setString(4, attachment.uri)
                    ps.setString(5, attachment.filename)
                    ps.setString(6, attachment.mimeType)
                    ps.setObject(7, attachment.size)
                    ps.setString(8, attachment.localPath)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    override suspend fun replaceEntryComments(
        entryType: EntryType,
        entryId: String,
        comments: List<EntryComment>
    ): Unit = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM comments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, entryType.name)
                ps.setString(2, entryId)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """INSERT INTO comments
                   (entry_type, entry_id, position, text, author, created)
                   VALUES (?, ?, ?, ?, ?, ?)"""
            ).use { ps ->
                comments.forEachIndexed { index, comment ->
                    ps.setString(1, entryType.name)
                    ps.setString(2, entryId)
                    ps.setInt(3, index)
                    ps.setString(4, comment.text)
                    ps.setString(5, comment.author)
                    ps.setObject(6, comment.created)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    override suspend fun deleteJournal(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("UPDATE journals SET archived = 1 WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _journals.value = loadJournals(includeArchived = true)
    }

    override suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("UPDATE notes SET archived = 1 WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _notes.value = loadNotes(includeArchived = true)
    }

    override suspend fun deleteTask(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("UPDATE tasks SET archived = 1 WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _tasks.value = loadTasks(includeArchived = true)
    }

    override suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        settings
    }

    override suspend fun saveSettings(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            this@SqliteLocalDataSource.settings = settings
            settingsChanged = true
            useConnection { conn ->
                conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES ('app_settings', ?)").use { ps ->
                    ps.setString(1, Json.encodeToString(settings))
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun loadSettings() {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT value FROM settings WHERE key = 'app_settings'").use { rs ->
                    if (rs.next()) {
                        try {
                            settings = Json.decodeFromString(rs.getString("value"))
                        } catch (e: Exception) {
                            // Use defaults
                        }
                    }
                }
            }
        }
    }

    suspend fun getArchivedJournals(): List<JournalEntry> = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM journals WHERE archived = 1").use { rs ->
                    val list = mutableListOf<JournalEntry>()
                    while (rs.next()) {
                        list.add(journalFromResultSet(rs))
                    }
                    list
                }
            }
        }
    }

    suspend fun getArchivedNotes(): List<NoteEntry> = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM notes WHERE archived = 1").use { rs ->
                    val list = mutableListOf<NoteEntry>()
                    while (rs.next()) {
                        list.add(noteFromResultSet(rs))
                    }
                    list
                }
            }
        }
    }

    suspend fun getArchivedTasks(): List<TaskEntry> = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM tasks WHERE archived = 1").use { rs ->
                    val list = mutableListOf<TaskEntry>()
                    while (rs.next()) {
                        list.add(taskFromResultSet(rs))
                    }
                    list
                }
            }
        }
    }

    override suspend fun permanentlyDeleteJournal(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM attachments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.JOURNAL.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM comments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.JOURNAL.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM journals WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _journals.value = loadJournals(includeArchived = true)
    }

    override suspend fun permanentlyDeleteNote(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM attachments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.NOTE.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM comments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.NOTE.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM notes WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _notes.value = loadNotes(includeArchived = true)
    }

    override suspend fun permanentlyDeleteTask(id: String) = withContext(Dispatchers.IO) {
        useConnection { conn ->
            conn.prepareStatement("DELETE FROM reminders WHERE task_id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM attachments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.TASK.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM comments WHERE entry_type = ? AND entry_id = ?").use { ps ->
                ps.setString(1, EntryType.TASK.name)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM tasks WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        _tasks.value = loadTasks(includeArchived = true)
    }

    override suspend fun restoreFromArchive(type: EntryType, id: String) = withContext(Dispatchers.IO) {
        val table = when (type) {
            EntryType.JOURNAL -> "journals"
            EntryType.NOTE -> "notes"
            EntryType.TASK -> "tasks"
        }
        useConnection { conn ->
            conn.prepareStatement("UPDATE $table SET archived = 0 WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        loadAllData()
    }

    fun exportAllData(): String = useConnection { conn ->
        val export = mutableMapOf<String, Any>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM journals").use { rs ->
                val journals = mutableListOf<JournalEntry>()
                while (rs.next()) journals.add(journalFromResultSet(rs))
                export["journals"] = journals
            }
            stmt.executeQuery("SELECT * FROM notes").use { rs ->
                val notes = mutableListOf<NoteEntry>()
                while (rs.next()) notes.add(noteFromResultSet(rs))
                export["notes"] = notes
            }
            stmt.executeQuery("SELECT * FROM tasks").use { rs ->
                val tasks = mutableListOf<TaskEntry>()
                while (rs.next()) tasks.add(taskFromResultSet(rs))
                export["tasks"] = tasks
            }
        }
        Json.encodeToString(export)
    }

    suspend fun importData(json: String) = withContext(Dispatchers.IO) {
        try {
            @Suppress("UNCHECKED_CAST")
            val data = Json.decodeFromString<Map<String, Any>>(json)
            (data["journals"] as? List<*>)?.forEach {
                if (it is JournalEntry) insertJournal(it)
            }
            (data["notes"] as? List<*>)?.forEach {
                if (it is NoteEntry) insertNote(it)
            }
            (data["tasks"] as? List<*>)?.forEach {
                if (it is TaskEntry) insertTask(it)
            }
            loadAllData()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid import data: ${e.message}")
        }
    }
}
