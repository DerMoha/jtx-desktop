package com.jtx.desktop.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle as CheckCircleIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jtx.desktop.ui.desktop.TrayManager
import com.jtx.desktop.ui.desktop.TrayStatus
import com.jtx.desktop.ui.desktop.ReminderScheduler
import com.jtx.desktop.ui.desktop.SyncScheduler
import com.jtx.desktop.ui.desktop.NetworkMonitor
import com.jtx.desktop.ui.theme.JtxBoardTheme
import com.jtx.desktop.ui.screens.journals.JournalsScreen
import com.jtx.desktop.ui.screens.notes.NotesScreen
import com.jtx.desktop.ui.screens.tasks.TasksScreen
import com.jtx.desktop.ui.screens.kanban.KanbanScreen
import com.jtx.desktop.ui.screens.settings.SettingsScreen
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.data.repository.JournalRepository
import com.jtx.desktop.data.repository.NoteRepository
import com.jtx.desktop.data.repository.TaskRepository
import com.jtx.desktop.data.repository.TemplateRepository
import com.jtx.desktop.data.repository.ConflictResolution
import com.jtx.desktop.domain.model.*
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter
import java.awt.event.KeyEvent as AWTKeyEvent
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val title: String, val icon: ImageVector) {
    Journals("Journals", Icons.AutoMirrored.Filled.List),
    Notes("Notes", Icons.AutoMirrored.Filled.List),
    Tasks("Tasks", Icons.Default.CheckCircle),
    Kanban("Board", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings)
}

enum class SortOrder {
    DATE_DESC, DATE_ASC, TITLE_ASC, TITLE_DESC, MODIFIED_DESC, MODIFIED_ASC
}

private fun EntryType.toTab(): Tab = when (this) {
    EntryType.JOURNAL -> Tab.Journals
    EntryType.NOTE -> Tab.Notes
    EntryType.TASK -> Tab.Tasks
}

enum class AppMenuAction {
    NEW_ENTRY,
    SYNC,
    IMPORT,
    EXPORT,
    UNDO,
    REDO,
    SHOW_JOURNALS,
    SHOW_NOTES,
    SHOW_TASKS,
    SHOW_KANBAN,
    SHOW_SETTINGS,
    ABOUT
}

class UndoManager<T> {
    private val undoStack = mutableListOf<T>()
    private val redoStack = mutableListOf<T>()

    fun push(action: T) {
        undoStack.add(action)
        redoStack.clear()
    }

    fun undo(): T? = if (undoStack.isNotEmpty()) {
        undoStack.removeLast().also { redoStack.add(it) }
    } else null
    fun redo(): T? = if (redoStack.isNotEmpty()) {
        redoStack.removeLast().also { undoStack.add(it) }
    } else null
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

data class UndoAction(
    val type: UndoType,
    val entry: CombinedEntry,
    val previousState: CombinedEntry? = null
)

enum class UndoType {
    CREATE, UPDATE, DELETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JtxApp(
    syncRepository: SyncRepository,
    trayManager: TrayManager? = null,
    focusSearch: Boolean = false,
    initialImportFiles: List<File> = emptyList(),
    menuAction: AppMenuAction? = null,
    onMenuActionHandled: () -> Unit = {}
) {
    val journalRepository = remember { JournalRepository(syncRepository.localDataSource) }
    val noteRepository = remember { NoteRepository(syncRepository.localDataSource) }
    val taskRepository = remember { TaskRepository(syncRepository.localDataSource) }
    val templateRepository = remember { TemplateRepository(syncRepository.localDataSource) }
    val parser = remember { ICalendarParser() }

    var selectedTab by remember { mutableStateOf(Tab.Journals) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showQuickEntryDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var syncState by remember { mutableStateOf(SyncState.IDLE) }
    var darkModePreference by remember { mutableStateOf(DarkModePreference.SYSTEM) }
    var listDensity by remember { mutableStateOf(ListDensity.COMFORTABLE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showArchived by remember { mutableStateOf(false) }
    var collectionFilter by remember { mutableStateOf<String?>(null) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(focusSearch) }
    var searchFocusRequest by remember { mutableStateOf(0) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var syncConflicts by remember { mutableStateOf<List<SyncRepository.SyncConflictInfo>>(emptyList()) }
    var syncIssues by remember { mutableStateOf<List<ObjectSyncMetadata>>(emptyList()) }
    var showSyncIssues by remember { mutableStateOf(false) }
    var allEntries by remember { mutableStateOf<List<CombinedEntry>>(emptyList()) }
    var collections by remember { mutableStateOf<List<CalDavCollection>>(emptyList()) }
    var entryToOpen by remember { mutableStateOf<CombinedEntry?>(null) }
    var reminderTasks by remember { mutableStateOf<List<TaskEntry>>(emptyList()) }
    var reminderActionTask by remember { mutableStateOf<TaskEntry?>(null) }
    val rootFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val undoManager = remember { UndoManager<UndoAction>() }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<AppSettings?>(null) }

    suspend fun saveCollectionFilter(filter: String?) {
        val updated = (settings ?: AppSettings()).copy(collectionFilter = filter)
        settings = updated
        syncRepository.saveSettings(updated)
    }

    LaunchedEffect(Unit) {
        combine(
            journalRepository.getAllCombined(includeArchived = true),
            noteRepository.getAllCombined(includeArchived = true),
            taskRepository.getAllCombined(includeArchived = true)
        ) { journals, notes, tasks -> journals + notes + tasks }
            .collect { allEntries = it }
    }

    LaunchedEffect(Unit) {
        syncRepository.localDataSource.getAllCollections().collect { collections = it }
    }

    LaunchedEffect(Unit) {
        syncRepository.localDataSource.getAllObjectSyncMetadata().collect { metadata ->
            syncIssues = metadata.filter { it.dirty || it.lastError != null }
        }
    }

    LaunchedEffect(Unit) {
        taskRepository.getAll(includeArchived = false).collect { tasks ->
            reminderTasks = tasks.filter { !it.archived && !it.completed && it.due != null && it.reminders.isNotEmpty() }
        }
    }

    LaunchedEffect(reminderTasks) {
        trayManager?.updateReminderCount(reminderTasks.sumOf { it.reminders.size })
        ReminderScheduler.scheduleReminders(scope, reminderTasks) { task, reminder ->
            val title = task.title.ifBlank { "Task reminder" }
            val message = if (reminder.minutesBefore > 0) {
                "Due in ${reminder.minutesBefore} minutes"
            } else {
                "Due now"
            }
            ReminderScheduler.showDesktopNotification(title, message, trayManager)
            snackbarMessage = "$title: $message"
            reminderActionTask = task
        }
    }

    DisposableEffect(Unit) {
        onDispose { ReminderScheduler.cancelReminders() }
    }

    fun createNewEntry(
        type: EntryType,
        title: String,
        description: String,
        categories: List<String>,
        color: String?,
        dueDays: Int?,
        recurrenceRule: RecurrenceRule?
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val due = dueDays?.let { now + it * 24L * 60L * 60L * 1000L }
        val combinedEntry = CombinedEntry(
            id = id,
            type = type,
            title = title,
            description = description,
            date = when (type) {
                EntryType.JOURNAL -> now
                EntryType.TASK -> due
                EntryType.NOTE -> null
            },
            modified = now,
            categories = categories,
            color = color,
            progress = if (type == EntryType.TASK) 0 else null,
            completed = if (type == EntryType.TASK) false else null
        )

        scope.launch {
            when (type) {
                EntryType.JOURNAL -> journalRepository.insert(
                    JournalEntry(
                        id = id,
                        uid = id,
                        title = title,
                        description = description,
                        descriptionFormat = DescriptionFormat.PLAIN,
                        dtstart = now,
                        dtend = null,
                        categories = categories,
                        created = now,
                        updated = now,
                        color = color,
                        location = null,
                        comment = null
                    )
                )
                EntryType.NOTE -> noteRepository.insert(
                    NoteEntry(
                        id = id,
                        uid = id,
                        title = title,
                        description = description,
                        descriptionFormat = DescriptionFormat.PLAIN,
                        categories = categories,
                        created = now,
                        updated = now,
                        color = color
                    )
                )
                EntryType.TASK -> taskRepository.insert(
                    TaskEntry(
                        id = id,
                        uid = id,
                        title = title,
                        description = description,
                        due = due,
                        start = null,
                        completed = false,
                        progress = 0,
                        categories = categories,
                        created = now,
                        updated = now,
                        color = color,
                        location = null,
                        subtasks = emptyList(),
                        relatedEntries = emptyList(),
                        recurrenceRule = recurrenceRule
                    )
                )
            }
            undoManager.push(UndoAction(UndoType.CREATE, combinedEntry))
            selectedTab = when (type) {
                EntryType.JOURNAL -> Tab.Journals
                EntryType.NOTE -> Tab.Notes
                EntryType.TASK -> Tab.Tasks
            }
            snackbarMessage = "Created ${type.name.lowercase()}"
            if (settings?.credentials != null) syncState = SyncState.PENDING
        }
    }

    fun syncStateForResult(result: SyncRepository.SyncResult): SyncState = when {
        result.conflicts.isNotEmpty() -> SyncState.CONFLICT
        result.failures.any { it.contains("read-only", ignoreCase = true) } -> SyncState.READ_ONLY
        result.failureCount > 0 -> SyncState.ERROR
        else -> SyncState.SUCCESS
    }

    fun syncNow() {
        scope.launch {
            val currentSettings = settings
            val credentials = currentSettings?.credentials
            val collection = currentSettings?.collection
            if (credentials == null || collection == null) {
                snackbarMessage = "Configure sync settings first"
                return@launch
            }
            if (!NetworkMonitor.isNetworkAvailable()) {
                isOffline = true
                syncState = SyncState.OFFLINE
                snackbarMessage = "Offline; local changes will sync when connection returns"
                return@launch
            }
            isOffline = false
            syncState = SyncState.SYNCING
            val result = syncRepository.sync(credentials, collection)
            result.onSuccess { syncResult ->
                syncConflicts = syncResult.conflicts
                settings = syncRepository.getSettings()
                syncState = syncStateForResult(syncResult)
                snackbarMessage = if (syncResult.conflicts.isEmpty()) {
                    "Sync complete"
                } else {
                    "${syncResult.conflicts.size} sync conflict${if (syncResult.conflicts.size == 1) "" else "s"} need resolution"
                }
            }.onFailure {
                syncState = SyncState.ERROR
                snackbarMessage = "Sync failed"
            }
        }
    }

    fun resolveCurrentConflict(resolution: ConflictResolution) {
        val conflict = syncConflicts.firstOrNull() ?: return
        val currentSettings = settings
        val credentials = currentSettings?.credentials
        val collection = currentSettings?.collection
        if (credentials == null || collection == null) {
            snackbarMessage = "Configure sync settings first"
            return
        }
        scope.launch {
            syncRepository.resolveConflict(credentials, collection, conflict, resolution)
            syncConflicts = syncConflicts.drop(1)
            snackbarMessage = if (syncConflicts.isEmpty()) "Conflict resolved" else "Conflict resolved"
            refreshTrigger++
        }
    }

    fun openReminderTask(task: TaskEntry) {
        selectedTab = Tab.Tasks
        entryToOpen = allEntries.firstOrNull { it.id == task.id && it.type == EntryType.TASK }
        reminderActionTask = null
    }

    fun insertCombinedEntry(entry: CombinedEntry) {
        val now = System.currentTimeMillis()
        when (entry.type) {
            EntryType.JOURNAL -> scope.launch {
                journalRepository.insert(JournalEntry(
                    id = entry.id,
                    uid = entry.id,
                    title = entry.title,
                    description = entry.description,
                    descriptionFormat = DescriptionFormat.PLAIN,
                    dtstart = entry.date,
                    dtend = null,
                    categories = entry.categories,
                    created = entry.date ?: now,
                    updated = now,
                    color = entry.color,
                    location = null,
                    comment = null,
                    archived = entry.archived
                ))
            }
            EntryType.NOTE -> scope.launch {
                noteRepository.insert(NoteEntry(
                    id = entry.id,
                    uid = entry.id,
                    title = entry.title,
                    description = entry.description,
                    descriptionFormat = DescriptionFormat.PLAIN,
                    categories = entry.categories,
                    created = entry.date ?: now,
                    updated = now,
                    color = entry.color,
                    location = null,
                    archived = entry.archived
                ))
            }
            EntryType.TASK -> scope.launch {
                taskRepository.insert(TaskEntry(
                    id = entry.id,
                    uid = entry.id,
                    title = entry.title,
                    description = entry.description,
                    due = entry.date,
                    start = null,
                    completed = entry.completed ?: false,
                    progress = entry.progress ?: 0,
                    categories = entry.categories,
                    created = entry.date ?: now,
                    updated = now,
                    color = entry.color,
                    location = null,
                    subtasks = emptyList(),
                    relatedEntries = emptyList(),
                    recurrenceRule = null,
                    archived = entry.archived
                ))
            }
        }
    }

    fun deleteCombinedEntry(entry: CombinedEntry) {
        when (entry.type) {
            EntryType.JOURNAL -> scope.launch { journalRepository.delete(entry.id) }
            EntryType.NOTE -> scope.launch { noteRepository.delete(entry.id) }
            EntryType.TASK -> scope.launch { taskRepository.delete(entry.id) }
        }
    }

    fun updateCombinedEntry(entry: CombinedEntry) {
        scope.launch {
            when (entry.type) {
                EntryType.JOURNAL -> journalRepository.updateJournal(entry)
                EntryType.NOTE -> noteRepository.updateNote(entry)
                EntryType.TASK -> taskRepository.updateTask(entry)
            }
        }
    }

    fun performUndo() {
        val action = undoManager.undo()
        if (action == null) {
            snackbarMessage = "Nothing to undo"
            return
        }
        when (action.type) {
            UndoType.DELETE -> insertCombinedEntry(action.entry)
            UndoType.CREATE -> deleteCombinedEntry(action.entry)
            UndoType.UPDATE -> {
                action.previousState?.let { updateCombinedEntry(it) }
            }
        }
        snackbarMessage = "Undone: ${action.type.name.lowercase()}"
    }

    fun performRedo() {
        val action = undoManager.redo()
        if (action == null) {
            snackbarMessage = "Nothing to redo"
            return
        }
        when (action.type) {
            UndoType.DELETE -> deleteCombinedEntry(action.entry)
            UndoType.CREATE -> insertCombinedEntry(action.entry)
            UndoType.UPDATE -> updateCombinedEntry(action.entry)
        }
        snackbarMessage = "Redone: ${action.type.name.lowercase()}"
    }

    suspend fun loadFullEntry(entry: CombinedEntry): Any? = when (entry.type) {
        EntryType.JOURNAL -> journalRepository.getById(entry.id)
        EntryType.NOTE -> noteRepository.getById(entry.id)
        EntryType.TASK -> taskRepository.getById(entry.id)
    }

    fun exportEntries(selectedEntries: List<CombinedEntry>? = null) {
        scope.launch {
            val file = chooseFile("Export Entries", FileDialog.SAVE, "jtxboard-export.ics", "ics") ?: return@launch
            val selectedCollection = collectionFilter
            val entriesToExport = selectedEntries ?: allEntries.filter { entry ->
                selectedCollection == null || entry.collectionUrl.matchesSelectedCollection(selectedCollection)
            }
            val fullEntries = entriesToExport.mapNotNull { loadFullEntry(it) }
            val content = buildString {
                fullEntries.forEach { append(parser.entryToIcs(it)) }
            }
            withContext(Dispatchers.IO) { file.writeText(content) }
            snackbarMessage = "Exported ${fullEntries.size} entr${if (fullEntries.size == 1) "y" else "ies"}"
        }
    }

    suspend fun importIcsFile(file: File): Int {
        val content = withContext(Dispatchers.IO) { file.readText() }
        val entry = parser.parseEntry(content)
        return when (entry) {
            is TaskEntry -> {
                taskRepository.insert(entry)
                1
            }
            is NoteEntry -> {
                noteRepository.insert(entry)
                1
            }
            is JournalEntry -> {
                journalRepository.insert(entry)
                1
            }
            else -> 0
        }
    }

    fun importEntries() {
        scope.launch {
            val file = chooseFile("Import Entry", FileDialog.LOAD, "*.ics", "ics") ?: return@launch
            val imported = importIcsFile(file)
            snackbarMessage = if (imported > 0) "Imported $imported entry" else "No supported entry found"
        }
    }

    LaunchedEffect(initialImportFiles) {
        if (initialImportFiles.isNotEmpty()) {
            val imported = initialImportFiles.sumOf { file ->
                runCatching { importIcsFile(file) }.getOrDefault(0)
            }
            snackbarMessage = if (imported > 0) {
                "Imported $imported entr${if (imported == 1) "y" else "ies"} from opened file${if (initialImportFiles.size == 1) "" else "s"}"
            } else {
                "No supported entries found in opened file${if (initialImportFiles.size == 1) "" else "s"}"
            }
        }
    }

    fun handleKeyboardShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val awtEvent = event.nativeKeyEvent
        if (awtEvent !is AWTKeyEvent) return false
        val keyCode = awtEvent.keyCode
        val modifiers = awtEvent.modifiers
        val isCtrlOrMeta = (modifiers and AWTKeyEvent.CTRL_MASK) != 0 || (modifiers and AWTKeyEvent.META_MASK) != 0
        val isShift = (modifiers and AWTKeyEvent.SHIFT_MASK) != 0
        val isAlt = (modifiers and AWTKeyEvent.ALT_MASK) != 0
        when {
            isCtrlOrMeta && !isShift && !isAlt && keyCode == AWTKeyEvent.VK_N -> { showNewDialog = true; return true }
            isCtrlOrMeta && isShift && !isAlt && keyCode == AWTKeyEvent.VK_N -> { showQuickEntryDialog = true; return true }
            isCtrlOrMeta && !isShift && !isAlt && keyCode == AWTKeyEvent.VK_S -> {
                syncNow()
                return true
            }
            isCtrlOrMeta && !isShift && !isAlt && keyCode == AWTKeyEvent.VK_F -> {
                searchFocused = true
                searchFocusRequest++
                return true
            }
            isCtrlOrMeta && !isShift && !isAlt && keyCode == AWTKeyEvent.VK_COMMA -> { selectedTab = Tab.Settings; return true }
            keyCode == AWTKeyEvent.VK_ESCAPE -> {
                showNewDialog = false
                showQuickEntryDialog = false
                searchFocused = false
                focusManager.clearFocus()
                return true
            }
        }
        return false
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        settings = syncRepository.getSettings()
        settings?.let {
            darkModePreference = it.darkModePreference
            listDensity = it.listDensity
            collectionFilter = it.collectionFilter
            sortOrder = when (it.sortPreference.field) {
                SortField.DATE -> if (it.sortPreference.ascending) SortOrder.DATE_ASC else SortOrder.DATE_DESC
                SortField.TITLE -> if (it.sortPreference.ascending) SortOrder.TITLE_ASC else SortOrder.TITLE_DESC
                SortField.MODIFIED -> if (it.sortPreference.ascending) SortOrder.MODIFIED_ASC else SortOrder.MODIFIED_DESC
            }
            isOffline = !NetworkMonitor.isNetworkAvailable()
        }

        if (settings?.syncOnChange == true) {
            journalRepository.setOnDataChangeListener {
                SyncScheduler.triggerSync(scope, 5000) {
                    settings?.credentials?.let { cred ->
                        settings?.collection?.let { col ->
                            if (!NetworkMonitor.isNetworkAvailable()) {
                                isOffline = true
                                syncState = SyncState.OFFLINE
                                return@triggerSync
                            }
                            isOffline = false
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = result.getOrNull()?.let { syncStateForResult(it) } ?: SyncState.ERROR
                        }
                    }
                }
            }
            noteRepository.setOnDataChangeListener {
                SyncScheduler.triggerSync(scope, 5000) {
                    settings?.credentials?.let { cred ->
                        settings?.collection?.let { col ->
                            if (!NetworkMonitor.isNetworkAvailable()) {
                                isOffline = true
                                syncState = SyncState.OFFLINE
                                return@triggerSync
                            }
                            isOffline = false
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = result.getOrNull()?.let { syncStateForResult(it) } ?: SyncState.ERROR
                        }
                    }
                }
            }
            taskRepository.setOnDataChangeListener {
                SyncScheduler.triggerSync(scope, 5000) {
                    settings?.credentials?.let { cred ->
                        settings?.collection?.let { col ->
                            if (!NetworkMonitor.isNetworkAvailable()) {
                                isOffline = true
                                syncState = SyncState.OFFLINE
                                return@triggerSync
                            }
                            isOffline = false
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = result.getOrNull()?.let { syncStateForResult(it) } ?: SyncState.ERROR
                        }
                    }
                }
            }
        }

        val interval = settings?.autoSyncIntervalMinutes ?: 15
        if (interval > 0) {
            SyncScheduler.schedulePeriodicSync(scope, interval) {
                settings?.credentials?.let { cred ->
                    settings?.collection?.let { col ->
                        if (!NetworkMonitor.isNetworkAvailable()) {
                            isOffline = true
                            syncState = SyncState.OFFLINE
                            return@schedulePeriodicSync
                        }
                        isOffline = false
                        syncState = SyncState.SYNCING
                        val result = syncRepository.sync(cred, col)
                        syncState = result.getOrNull()?.let { syncStateForResult(it) } ?: SyncState.ERROR
                    }
                }
            }
        }
    }

    LaunchedEffect(menuAction) {
        when (menuAction) {
            AppMenuAction.NEW_ENTRY -> showNewDialog = true
            AppMenuAction.SYNC -> syncNow()
            AppMenuAction.IMPORT -> importEntries()
            AppMenuAction.EXPORT -> exportEntries()
            AppMenuAction.UNDO -> performUndo()
            AppMenuAction.REDO -> performRedo()
            AppMenuAction.SHOW_JOURNALS -> selectedTab = Tab.Journals
            AppMenuAction.SHOW_NOTES -> selectedTab = Tab.Notes
            AppMenuAction.SHOW_TASKS -> selectedTab = Tab.Tasks
            AppMenuAction.SHOW_KANBAN -> selectedTab = Tab.Kanban
            AppMenuAction.SHOW_SETTINGS -> selectedTab = Tab.Settings
            AppMenuAction.ABOUT -> showAboutDialog = true
            null -> Unit
        }
        if (menuAction != null) onMenuActionHandled()
    }

    LaunchedEffect(syncState) {
        trayManager?.updateStatus(
            when {
                isOffline || syncState == SyncState.OFFLINE -> TrayStatus.OFFLINE
                else -> when (syncState) {
                    SyncState.IDLE -> TrayStatus.IDLE
                    SyncState.SYNCING -> TrayStatus.SYNCING
                    SyncState.SUCCESS -> TrayStatus.SUCCESS
                    SyncState.PENDING -> TrayStatus.IDLE
                    SyncState.ERROR, SyncState.CONFLICT, SyncState.READ_ONLY -> TrayStatus.ERROR
                    SyncState.OFFLINE -> TrayStatus.OFFLINE
                }
            }
        )
    }

    LaunchedEffect(syncIssues, settings?.lastSyncTime) {
        trayManager?.updateSyncSummary(
            pendingCount = syncIssues.count { it.dirty },
            lastSyncTime = settings?.lastSyncTime
        )
    }

    val isDarkTheme = when (darkModePreference) {
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
        DarkModePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val syncStateIcon = when (syncState) {
        SyncState.IDLE -> Icons.Default.Refresh
        SyncState.SYNCING -> Icons.Default.Refresh
        SyncState.SUCCESS -> Icons.Default.CheckCircleIcon
        SyncState.PENDING -> Icons.Default.Refresh
        SyncState.ERROR, SyncState.CONFLICT, SyncState.OFFLINE, SyncState.READ_ONLY -> Icons.Default.Warning
    }
    val syncStateLabel = when (syncState) {
        SyncState.IDLE -> null
        SyncState.SYNCING -> "Syncing"
        SyncState.SUCCESS -> "Synced"
        SyncState.PENDING -> "Pending"
        SyncState.ERROR -> "Error"
        SyncState.CONFLICT -> "Conflict"
        SyncState.OFFLINE -> "Offline"
        SyncState.READ_ONLY -> "Read-only"
    }

    JtxBoardTheme(darkTheme = isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { handleKeyboardShortcut(it) }
        ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("jtxBoard") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        if (isOffline) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Offline") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (!isOffline && syncStateLabel != null) {
                            AssistChip(
                                onClick = {},
                                label = { Text(syncStateLabel) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (undoManager.canUndo()) {
                            TextButton(onClick = { performUndo() }) {
                                Text("Undo", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        if (collections.isNotEmpty()) {
                            Box {
                                TextButton(onClick = { showCollectionMenu = true }) {
                                    Text(
                                        text = collectionFilter.collectionLabel(collections),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCollectionMenu,
                                    onDismissRequest = { showCollectionMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All collections") },
                                        onClick = {
                                            collectionFilter = null
                                            showCollectionMenu = false
                                            scope.launch { saveCollectionFilter(null) }
                                        },
                                        leadingIcon = {
                                            if (collectionFilter == null) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected")
                                            }
                                        }
                                    )
                                    collections.forEach { collection ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(collection.displayName.ifBlank { collection.url })
                                                    Text(
                                                        text = collection.url,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                collectionFilter = collection.url
                                                showCollectionMenu = false
                                                scope.launch { saveCollectionFilter(collection.url) }
                                            },
                                            leadingIcon = {
                                                if (collectionFilter?.let { collection.url.matchesSelectedCollection(it) } == true) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (syncIssues.isNotEmpty() || syncState == SyncState.ERROR || syncState == SyncState.CONFLICT) {
                            IconButton(onClick = { showSyncIssues = true }) {
                                Icon(Icons.Default.Warning, contentDescription = "Sync issues", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        IconButton(onClick = { showGlobalSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Global search", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { syncNow() }) {
                            Icon(syncStateIcon, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab != Tab.Settings) {
                    FloatingActionButton(
                        onClick = { showNewDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            },
            snackbarHost = {
                snackbarMessage?.let { message ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { snackbarMessage = null }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(message)
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    Tab.Journals -> JournalsScreen(
                        repository = journalRepository,
                        allEntries = allEntries,
                        openEntryRequest = entryToOpen,
                        onOpenEntryRequestHandled = { entryToOpen = null },
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        listDensity = listDensity,
                        collectionFilter = collectionFilter,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) },
                        onOpenRelatedEntry = { entry ->
                            selectedTab = entry.type.toTab()
                            entryToOpen = entry
                        }
                    )
                    Tab.Notes -> NotesScreen(
                        repository = noteRepository,
                        allEntries = allEntries,
                        openEntryRequest = entryToOpen,
                        onOpenEntryRequestHandled = { entryToOpen = null },
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        listDensity = listDensity,
                        collectionFilter = collectionFilter,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) },
                        onOpenRelatedEntry = { entry ->
                            selectedTab = entry.type.toTab()
                            entryToOpen = entry
                        }
                    )
                    Tab.Tasks -> TasksScreen(
                        repository = taskRepository,
                        allEntries = allEntries,
                        openEntryRequest = entryToOpen,
                        onOpenEntryRequestHandled = { entryToOpen = null },
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        listDensity = listDensity,
                        collectionFilter = collectionFilter,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) },
                        onOpenRelatedEntry = { entry ->
                            selectedTab = entry.type.toTab()
                            entryToOpen = entry
                        }
                    )
                    Tab.Kanban -> KanbanScreen(
                        repository = taskRepository,
                        kanbanColumns = settings?.kanbanColumns ?: defaultKanbanColumns,
                        onTaskMove = { taskId, progress ->
                            scope.launch { taskRepository.updateTaskProgress(taskId, progress) }
                        }
                    )
                    Tab.Settings -> SettingsScreen(
                        syncRepository = syncRepository,
                        templateRepository = templateRepository,
                        onSync = { refreshTrigger++ },
                        darkModePreference = darkModePreference,
                        onDarkModeChange = { darkModePreference = it },
                        listDensity = listDensity,
                        onListDensityChange = { listDensity = it },
                        onSortChange = { sortOrder = it },
                        sortOrder = sortOrder
                    )
                }
            }
        }

        if (showNewDialog) {
            NewEntryDialog(
                onDismiss = { showNewDialog = false },
                onCreate = { type, title, description, categories, color, dueDays, recurrenceRule ->
                    createNewEntry(type, title, description, categories, color, dueDays, recurrenceRule)
                    showNewDialog = false
                },
                templates = templateRepository.getTemplates()
            )
        }

        if (showQuickEntryDialog) {
            QuickEntryDialog(
                defaultType = when (selectedTab) {
                    Tab.Tasks, Tab.Kanban -> EntryType.TASK
                    Tab.Notes -> EntryType.NOTE
                    Tab.Journals, Tab.Settings -> EntryType.JOURNAL
                },
                onDismiss = { showQuickEntryDialog = false },
                onCreate = { type, title, description, categories ->
                    createNewEntry(type, title, description, categories, null, null, null)
                    showQuickEntryDialog = false
                }
            )
        }

        if (showGlobalSearch) {
            GlobalSearchDialog(
                entries = allEntries,
                collectionFilter = collectionFilter,
                savedFilters = settings?.savedFilters.orEmpty(),
                onSaveFilter = { filter ->
                    val current = settings ?: AppSettings()
                    val updated = current.copy(
                        savedFilters = current.savedFilters.filterNot { it.id == filter.id } + filter
                    )
                    settings = updated
                    scope.launch { syncRepository.saveSettings(updated) }
                },
                onDismiss = { showGlobalSearch = false },
                onOpenEntry = { entry ->
                    selectedTab = entry.type.toTab()
                    entryToOpen = entry
                    showGlobalSearch = false
                },
                onExportEntries = { entries ->
                    exportEntries(entries)
                    showGlobalSearch = false
                }
            )
        }

        if (showSyncIssues) {
            SyncIssuesDialog(
                issues = syncIssues,
                conflictCount = syncConflicts.size,
                syncState = syncState,
                onDismiss = { showSyncIssues = false },
                onRetrySync = {
                    showSyncIssues = false
                    syncNow()
                }
            )
        }

        syncConflicts.firstOrNull()?.let { conflict ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Sync Conflict") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local and server versions both changed. Choose which version to keep.")
                        Text("Local: ${conflict.localEntry.conflictTitle()}")
                        Text("Server: ${conflict.serverEntry.conflictTitle()}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { resolveCurrentConflict(ConflictResolution.KEEP_LOCAL) }) {
                        Text("Keep Local")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { resolveCurrentConflict(ConflictResolution.KEEP_SERVER) }) {
                            Text("Keep Server")
                        }
                        TextButton(onClick = { resolveCurrentConflict(ConflictResolution.KEEP_BOTH) }) {
                            Text("Keep Both")
                        }
                    }
                }
            )
        }

        reminderActionTask?.let { task ->
            AlertDialog(
                onDismissRequest = { reminderActionTask = null },
                title = { Text(task.title.ifBlank { "Task reminder" }) },
                text = { Text("This task reminder is due now or soon.") },
                confirmButton = {
                    TextButton(onClick = { openReminderTask(task) }) {
                        Text("Open")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { reminderActionTask = null }) {
                            Text("Dismiss")
                        }
                        TextButton(onClick = {
                            scope.launch { taskRepository.updateTaskCompleted(task.id, true) }
                            reminderActionTask = null
                        }) {
                            Text("Complete")
                        }
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About jtxBoard Desktop") },
                text = { Text("Desktop-native companion for jtxBoard with local editing and CalDAV sync.") },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
        }
    }
}

private fun Any.conflictTitle(): String = when (this) {
    is JournalEntry -> "journal \"$title\""
    is NoteEntry -> "note \"$title\""
    is TaskEntry -> "task \"$title\""
    else -> "entry"
}

@Composable
private fun SyncIssuesDialog(
    issues: List<ObjectSyncMetadata>,
    conflictCount: Int,
    syncState: SyncState,
    onDismiss: () -> Unit,
    onRetrySync: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Issues") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("State: ${syncState.name.lowercase().replace('_', ' ')}")
                if (conflictCount > 0) {
                    Text("$conflictCount conflict${if (conflictCount == 1) "" else "s"} need resolution.")
                }
                if (issues.isEmpty()) {
                    Text("No queued local changes or recorded sync errors.")
                } else {
                    Text("Queued changes and errors", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(issues, key = { "${it.entryType}-${it.entryId}" }) { issue ->
                            ListItem(
                                headlineContent = { Text("${issue.entryType.name.lowercase()} ${issue.entryId}") },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            if (issue.deleted) "delete queued" else if (issue.dirty) "upload queued" else null,
                                            issue.lastError,
                                            issue.collectionUrl
                                        ).joinToString(" - ")
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetrySync) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun GlobalSearchDialog(
    entries: List<CombinedEntry>,
    collectionFilter: String?,
    savedFilters: List<SavedFilter>,
    onSaveFilter: (SavedFilter) -> Unit,
    onDismiss: () -> Unit,
    onOpenEntry: (CombinedEntry) -> Unit,
    onExportEntries: (List<CombinedEntry>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterName by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<EntryType?>(null) }
    var statusFilter by remember { mutableStateOf(TaskStatusFilter.ANY) }
    var priorityFilter by remember { mutableStateOf<Priority?>(null) }
    var categoryFilter by remember { mutableStateOf("") }
    var dueFrom by remember { mutableStateOf("") }
    var dueTo by remember { mutableStateOf("") }
    var modifiedOnly by remember { mutableStateOf(false) }
    var includeArchived by remember { mutableStateOf(false) }
    val results = remember(
        entries,
        query,
        typeFilter,
        statusFilter,
        priorityFilter,
        categoryFilter,
        dueFrom,
        dueTo,
        modifiedOnly,
        includeArchived,
        collectionFilter
    ) {
        val from = dueFrom.parseMillisFilter()
        val to = dueTo.parseMillisFilter()
        entries
            .filter { entry -> query.isBlank() || entry.matchesGlobalSearch(query) }
            .filter { entry -> typeFilter == null || entry.type == typeFilter }
            .filter { entry -> includeArchived || !entry.archived }
            .filter { entry -> collectionFilter == null || entry.collectionUrl.matchesSelectedCollection(collectionFilter) }
            .filter { entry -> categoryFilter.isBlank() || entry.categories.any { it.contains(categoryFilter, ignoreCase = true) } }
            .filter { entry -> priorityFilter == null || entry.priority == priorityFilter }
            .filter { entry -> entry.matchesStatus(statusFilter) }
            .filter { entry -> from == null || (entry.date ?: 0) >= from }
            .filter { entry -> to == null || (entry.date ?: Long.MAX_VALUE) <= to }
            .filter { entry -> !modifiedOnly || (entry.modified != null && entry.modified != entry.date) }
            .sortedWith(compareBy<CombinedEntry> { it.type.name }.thenBy { it.title.lowercase() })
            .take(50)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Global Search") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search entries") },
                    supportingText = { Text("Searches titles, descriptions, categories, and locations") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All") })
                    EntryType.entries.forEach { type ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = if (typeFilter == type) null else type },
                            label = { Text(type.name.lowercase()) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (savedFilters.isNotEmpty()) {
                    Text("Saved filters", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedFilters.take(6).forEach { filter ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    query = filter.query
                                    typeFilter = filter.entryType
                                    statusFilter = filter.completed?.let { if (it) TaskStatusFilter.COMPLETED else TaskStatusFilter.PENDING }
                                        ?: TaskStatusFilter.ANY
                                    priorityFilter = filter.priorities.firstOrNull()
                                    categoryFilter = filter.categories.firstOrNull().orEmpty()
                                    dueFrom = filter.dateFrom?.toString().orEmpty()
                                    dueTo = filter.dateTo?.toString().orEmpty()
                                    includeArchived = filter.includeArchived
                                    modifiedOnly = filter.modifiedOnly
                                },
                                label = { Text(filter.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterName,
                        onValueChange = { filterName = it },
                        label = { Text("Filter name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val name = filterName.ifBlank { query.ifBlank { "Saved filter" } }
                            onSaveFilter(
                                SavedFilter(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    query = query,
                                    entryType = typeFilter,
                                    categories = categoryFilter.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(),
                                    priorities = priorityFilter?.let { listOf(it) }.orEmpty(),
                                    completed = when (statusFilter) {
                                        TaskStatusFilter.ANY -> null
                                        TaskStatusFilter.PENDING -> false
                                        TaskStatusFilter.COMPLETED -> true
                                    },
                                    dateFrom = dueFrom.parseMillisFilter(),
                                    dateTo = dueTo.parseMillisFilter(),
                                    includeArchived = includeArchived,
                                    modifiedOnly = modifiedOnly
                                )
                            )
                            filterName = ""
                        }
                    ) {
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskStatusFilter.entries.forEach { status ->
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = status },
                            label = { Text(status.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = priorityFilter == null, onClick = { priorityFilter = null }, label = { Text("Any priority") })
                    listOf(Priority.URGENT, Priority.HIGH, Priority.MEDIUM, Priority.LOW).forEach { priority ->
                        FilterChip(
                            selected = priorityFilter == priority,
                            onClick = { priorityFilter = if (priorityFilter == priority) null else priority },
                            label = { Text(priority.name.lowercase()) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categoryFilter,
                        onValueChange = { categoryFilter = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueFrom,
                        onValueChange = { dueFrom = it },
                        label = { Text("From millis") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueTo,
                        onValueChange = { dueTo = it },
                        label = { Text("To millis") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = includeArchived,
                        onClick = { includeArchived = !includeArchived },
                        label = { Text("Archived") }
                    )
                    FilterChip(
                        selected = modifiedOnly,
                        onClick = { modifiedOnly = !modifiedOnly },
                        label = { Text("Modified") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { "${it.type}-${it.id}" }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title.ifBlank { "(No title)" }) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        entry.type.name.lowercase(),
                                        entry.location,
                                        entry.categories.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                    ).joinToString(" - ")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                TextButton(onClick = { onOpenEntry(entry) }) {
                                    Text("Open")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onExportEntries(results) },
                    enabled = results.isNotEmpty()
                ) {
                    Text("Export Results")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

private enum class TaskStatusFilter(val label: String) {
    ANY("Any status"),
    PENDING("Pending"),
    COMPLETED("Completed")
}

private fun CombinedEntry.matchesStatus(status: TaskStatusFilter): Boolean = when (status) {
    TaskStatusFilter.ANY -> true
    TaskStatusFilter.PENDING -> type != EntryType.TASK || completed != true
    TaskStatusFilter.COMPLETED -> type == EntryType.TASK && completed == true
}

private fun String.parseMillisFilter(): Long? = trim().takeIf { it.isNotBlank() }?.toLongOrNull()

private fun String?.collectionLabel(collections: List<CalDavCollection>): String {
    val selected = this ?: return "All collections"
    return collections.firstOrNull { it.url.matchesSelectedCollection(selected) }
        ?.displayName
        ?.ifBlank { null }
        ?: "Collection"
}

private fun String?.matchesSelectedCollection(collection: String): Boolean {
    val current = this?.trimEnd('/') ?: return false
    val target = collection.trimEnd('/')
    return current == target || current.endsWith(target) || target.endsWith(current)
}

private fun CombinedEntry.matchesGlobalSearch(query: String): Boolean {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return terms.all { term ->
        title.contains(term, ignoreCase = true) ||
            description.contains(term, ignoreCase = true) ||
            location?.contains(term, ignoreCase = true) == true ||
            categories.any { it.contains(term, ignoreCase = true) }
    }
}

private data class QuickEntryDraft(
    val type: EntryType,
    val title: String,
    val description: String,
    val categories: List<String>
)

@Composable
private fun QuickEntryDialog(
    defaultType: EntryType,
    onDismiss: () -> Unit,
    onCreate: (EntryType, String, String, List<String>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val draft = remember(text, defaultType) { text.toQuickEntryDraft(defaultType) }
    fun submit() {
        draft?.let { onCreate(it.type, it.title, it.description, it.categories) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Entry") },
                    placeholder = { Text("task: Buy milk #errands | Notes") },
                    supportingText = { Text("Prefixes: task:, note:, journal:. Use #tags and | for description.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                submit()
                                true
                            } else {
                                false
                            }
                        }
                )
                draft?.let {
                    Text(
                        text = "Creates ${it.type.name.lowercase()}: ${it.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = draft != null) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun String.toQuickEntryDraft(defaultType: EntryType): QuickEntryDraft? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val prefix = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    val explicitType = when (prefix) {
        "task", "todo", "t" -> EntryType.TASK
        "note", "n" -> EntryType.NOTE
        "journal", "j" -> EntryType.JOURNAL
        else -> null
    }
    val body = if (explicitType != null) trimmed.substringAfter(':').trim() else trimmed
    val parts = body.split('|', limit = 2)
    val rawTitle = parts.firstOrNull().orEmpty()
    val categories = Regex("(?:^|\\s)#([^\\s#]+)")
        .findAll(rawTitle)
        .map { it.groupValues[1] }
        .toList()
    val title = rawTitle.replace(Regex("(?:^|\\s)#([^\\s#]+)"), " ").trim()
    if (title.isBlank()) return null
    return QuickEntryDraft(
        type = explicitType ?: defaultType,
        title = title,
        description = parts.getOrNull(1)?.trim().orEmpty(),
        categories = categories
    )
}

private suspend fun chooseFile(title: String, mode: Int, defaultFile: String, extension: String? = null): File? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as java.awt.Frame?, title, mode).apply {
        file = defaultFile
        if (extension != null) {
            filenameFilter = FilenameFilter { _, name -> name.endsWith(".$extension", ignoreCase = true) }
        }
        isVisible = true
    }
    val selectedFile = dialog.file ?: return@withContext null
    File(dialog.directory, selectedFile).withExtensionIfMissing(mode, extension)
}

private fun File.withExtensionIfMissing(mode: Int, extension: String?): File {
    if (mode != FileDialog.SAVE || extension == null || name.endsWith(".$extension", ignoreCase = true)) return this
    return File(parentFile, "$name.$extension")
}

@Composable
fun NewEntryDialog(
    onDismiss: () -> Unit,
    onCreate: (EntryType, String, String, List<String>, String?, Int?, RecurrenceRule?) -> Unit,
    templates: List<EntryTemplate> = emptyList()
) {
    var selectedType by remember { mutableStateOf<EntryType?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>(null) }
    var dueDays by remember { mutableStateOf<Int?>(null) }
    var recurrenceRule by remember { mutableStateOf<RecurrenceRule?>(null) }

    fun applyTemplate(template: EntryTemplate) {
        selectedType = template.type
        title = template.title
        description = template.description.orEmpty()
        categoryText = template.categories.joinToString(", ")
        color = template.color
        dueDays = template.dueDays
        recurrenceRule = template.recurrence
    }

    fun clearTemplateDefaults(type: EntryType) {
        selectedType = type
        title = ""
        description = ""
        categoryText = ""
        color = null
        dueDays = null
        recurrenceRule = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New") },
        text = {
            Column {
                listOf(
                    EntryType.JOURNAL to "Journal Entry",
                    EntryType.NOTE to "Note",
                    EntryType.TASK to "Task"
                ).forEach { (type, label) ->
                    TextButton(
                        onClick = { clearTemplateDefaults(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            color = if (selectedType == type) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
                selectedType?.let { type ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Categories") },
                        placeholder = { Text("Comma-separated") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("Creating a ${type.name.lowercase()}")
                            color?.let { append(" with color $it") }
                            dueDays?.let { append(" due in $it day${if (it == 1) "" else "s"}") }
                            recurrenceRule?.let { append(" repeating ${it.frequency.name.lowercase()}") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (templates.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Templates:", style = MaterialTheme.typography.labelMedium)
                    templates.forEach { template ->
                        TextButton(
                            onClick = { applyTemplate(template) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(template.title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedType != null,
                onClick = {
                    selectedType?.let { type ->
                        onCreate(
                            type,
                            title,
                            description,
                            categoryText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            color,
                            dueDays,
                            recurrenceRule
                        )
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
