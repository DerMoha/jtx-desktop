package com.jtx.desktop.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle as CheckCircleIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jtx.desktop.ui.desktop.TrayManager
import com.jtx.desktop.ui.desktop.TrayStatus
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
import java.awt.event.KeyEvent as AWTKeyEvent
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
    var refreshTrigger by remember { mutableStateOf(0) }
    var syncState by remember { mutableStateOf(SyncState.IDLE) }
    var darkModePreference by remember { mutableStateOf(DarkModePreference.SYSTEM) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showArchived by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(focusSearch) }
    var searchFocusRequest by remember { mutableStateOf(0) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var syncConflicts by remember { mutableStateOf<List<SyncRepository.SyncConflictInfo>>(emptyList()) }
    val rootFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val undoManager = remember { UndoManager<UndoAction>() }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<AppSettings?>(null) }

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
        }
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
            syncState = SyncState.SYNCING
            val result = syncRepository.sync(credentials, collection)
            result.onSuccess { syncResult ->
                syncConflicts = syncResult.conflicts
                syncState = if (syncResult.conflicts.isEmpty()) SyncState.SUCCESS else SyncState.ERROR
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

    fun exportEntries() {
        scope.launch {
            val file = chooseFile("Export Entries", FileDialog.SAVE, "jtxboard-export.ics") ?: return@launch
            val journals = journalRepository.getAll(includeArchived = true).first()
            val notes = noteRepository.getAll(includeArchived = true).first()
            val tasks = taskRepository.getAll(includeArchived = true).first()
            val content = buildString {
                journals.forEach { append(parser.entryToIcs(it)) }
                notes.forEach { append(parser.entryToIcs(it)) }
                tasks.forEach { append(parser.entryToIcs(it)) }
            }
            withContext(Dispatchers.IO) { file.writeText(content) }
            snackbarMessage = "Exported ${journals.size + notes.size + tasks.size} entries"
        }
    }

    fun importEntries() {
        scope.launch {
            val file = chooseFile("Import Entry", FileDialog.LOAD, "*.ics") ?: return@launch
            val content = withContext(Dispatchers.IO) { file.readText() }
            val imported = when {
                content.contains("BEGIN:VTODO") -> parser.parseVTodo(content)?.let {
                    taskRepository.insert(it)
                    1
                } ?: 0
                content.contains("BEGIN:VNOTE") -> parser.parseVNote(content)?.let {
                    noteRepository.insert(it)
                    1
                } ?: 0
                content.contains("BEGIN:VJOURNAL") -> parser.parseVJournal(content)?.let {
                    journalRepository.insert(it)
                    1
                } ?: 0
                else -> 0
            }
            snackbarMessage = if (imported > 0) "Imported $imported entry" else "No supported entry found"
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
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = if (result.isSuccess) SyncState.SUCCESS else SyncState.ERROR
                        }
                    }
                }
            }
            noteRepository.setOnDataChangeListener {
                SyncScheduler.triggerSync(scope, 5000) {
                    settings?.credentials?.let { cred ->
                        settings?.collection?.let { col ->
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = if (result.isSuccess) SyncState.SUCCESS else SyncState.ERROR
                        }
                    }
                }
            }
            taskRepository.setOnDataChangeListener {
                SyncScheduler.triggerSync(scope, 5000) {
                    settings?.credentials?.let { cred ->
                        settings?.collection?.let { col ->
                            syncState = SyncState.SYNCING
                            val result = syncRepository.sync(cred, col)
                            syncState = if (result.isSuccess) SyncState.SUCCESS else SyncState.ERROR
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
                        syncState = SyncState.SYNCING
                        val result = syncRepository.sync(cred, col)
                        syncState = if (result.isSuccess) SyncState.SUCCESS else SyncState.ERROR
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
                isOffline -> TrayStatus.OFFLINE
                else -> when (syncState) {
                    SyncState.IDLE -> TrayStatus.IDLE
                    SyncState.SYNCING -> TrayStatus.SYNCING
                    SyncState.SUCCESS -> TrayStatus.SUCCESS
                    SyncState.ERROR -> TrayStatus.ERROR
                }
            }
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
        SyncState.ERROR -> Icons.Default.Warning
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
                        if (undoManager.canUndo()) {
                            TextButton(onClick = { performUndo() }) {
                                Text("Undo", color = MaterialTheme.colorScheme.onPrimary)
                            }
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
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) }
                    )
                    Tab.Notes -> NotesScreen(
                        repository = noteRepository,
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) }
                    )
                    Tab.Tasks -> TasksScreen(
                        repository = taskRepository,
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        searchFocusRequest = searchFocusRequest,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) }
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

private suspend fun chooseFile(title: String, mode: Int, defaultFile: String): File? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as java.awt.Frame?, title, mode).apply {
        file = defaultFile
        isVisible = true
    }
    val selectedFile = dialog.file ?: return@withContext null
    File(dialog.directory, selectedFile)
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
