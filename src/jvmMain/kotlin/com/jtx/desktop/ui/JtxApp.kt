package com.jtx.desktop.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jtx.desktop.ui.theme.JtxBoardTheme
import com.jtx.desktop.ui.screens.journals.JournalsScreen
import com.jtx.desktop.ui.screens.notes.NotesScreen
import com.jtx.desktop.ui.screens.tasks.TasksScreen
import com.jtx.desktop.ui.screens.kanban.KanbanScreen
import com.jtx.desktop.ui.screens.settings.SettingsScreen
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.data.repository.JournalRepository
import com.jtx.desktop.data.repository.NoteRepository
import com.jtx.desktop.data.repository.TaskRepository
import com.jtx.desktop.data.repository.TemplateRepository
import com.jtx.desktop.domain.model.*
import com.jtx.desktop.ui.desktop.TrayManager
import com.jtx.desktop.ui.desktop.TrayStatus
import kotlinx.coroutines.launch

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

class UndoManager<T> {
    private val undoStack = mutableListOf<T>()
    private val redoStack = mutableListOf<T>()

    fun push(action: T) {
        undoStack.add(action)
        redoStack.clear()
    }

    fun undo(): T? = if (undoStack.isNotEmpty()) undoStack.removeLast() else null
    fun redo(): T? = if (redoStack.isNotEmpty()) redoStack.removeLast() else null
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
    trayManager: TrayManager? = null
) {
    val journalRepository = remember { JournalRepository(syncRepository.localDataSource) }
    val noteRepository = remember { NoteRepository(syncRepository.localDataSource) }
    val taskRepository = remember { TaskRepository(syncRepository.localDataSource) }
    val templateRepository = remember { TemplateRepository(syncRepository.localDataSource) }

    var selectedTab by remember { mutableStateOf(Tab.Journals) }
    var showNewDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var syncState by remember { mutableStateOf(SyncState.IDLE) }
    var darkModePreference by remember { mutableStateOf(DarkModePreference.SYSTEM) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showArchived by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isOffline by remember { mutableStateOf(false) }

    val undoManager = remember { UndoManager<UndoAction>() }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        settings = syncRepository.getSettings()
        settings?.let {
            darkModePreference = it.darkModePreference
            sortOrder = when (it.sortPreference.field) {
                SortField.DATE -> if (it.sortPreference.ascending) SortOrder.DATE_ASC else SortOrder.DATE_DESC
                SortField.TITLE -> if (it.sortPreference.ascending) SortOrder.TITLE_ASC else SortOrder.TITLE_DESC
                SortField.MODIFIED -> if (it.sortPreference.ascending) SortOrder.MODIFIED_ASC else SortOrder.MODIFIED_DESC
            }
        }
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
                            TextButton(onClick = {
                                val action = undoManager.undo()
                                if (action != null) {
                                    when (action.type) {
                                        UndoType.DELETE -> {
                                            when (action.entry.type) {
                                                EntryType.JOURNAL -> scope.launch {
                                                    journalRepository.insert(JournalEntry(
                                                        action.entry.id, action.entry.id, action.entry.title,
                                                        action.entry.description, action.entry.date, null,
                                                        action.entry.categories, System.currentTimeMillis(),
                                                        System.currentTimeMillis(), action.entry.color, null, null, false
                                                    ))
                                                }
                                                EntryType.NOTE -> scope.launch {
                                                    noteRepository.insert(NoteEntry(
                                                        action.entry.id, action.entry.id, action.entry.title,
                                                        action.entry.description, action.entry.categories,
                                                        System.currentTimeMillis(), System.currentTimeMillis(),
                                                        action.entry.color, null, false
                                                    ))
                                                }
                                                EntryType.TASK -> scope.launch {
                                                    taskRepository.insert(TaskEntry(
                                                        action.entry.id, action.entry.id, action.entry.title,
                                                        action.entry.description, null, null, false, 0,
                                                        action.entry.categories, System.currentTimeMillis(),
                                                        System.currentTimeMillis(), action.entry.color, null,
                                                        emptyList(), emptyList(), null, false
                                                    ))
                                                }
                                            }
                                        }
                                        UndoType.CREATE -> {
                                            when (action.entry.type) {
                                                EntryType.JOURNAL -> scope.launch { journalRepository.delete(action.entry.id) }
                                                EntryType.NOTE -> scope.launch { noteRepository.delete(action.entry.id) }
                                                EntryType.TASK -> scope.launch { taskRepository.delete(action.entry.id) }
                                            }
                                        }
                                        UndoType.UPDATE -> {
                                            if (action.previousState != null) {
                                                scope.launch {
                                                    when (action.entry.type) {
                                                        EntryType.JOURNAL -> journalRepository.updateJournal(action.previousState)
                                                        EntryType.NOTE -> noteRepository.updateNote(action.previousState)
                                                        EntryType.TASK -> taskRepository.updateTask(action.previousState)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    snackbarMessage = "Undone: ${action.type.name.lowercase()}"
                                }
                            }) {
                                Text("Undo", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                syncState = SyncState.SYNCING
                                val result = syncRepository.sync(
                                    settings?.credentials ?: return@launch,
                                    settings?.collection ?: return@launch
                                )
                                syncState = if (result.isSuccess) SyncState.SUCCESS else SyncState.ERROR
                            }
                        }) {
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
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) }
                    )
                    Tab.Notes -> NotesScreen(
                        repository = noteRepository,
                        sortOrder = sortOrder,
                        showArchived = showArchived,
                        onSortChange = { sortOrder = it },
                        onShowArchivedChange = { showArchived = it },
                        onDelete = { entry -> undoManager.push(UndoAction(UndoType.DELETE, entry)) },
                        onUpdate = { entry, prev -> undoManager.push(UndoAction(UndoType.UPDATE, entry, prev)) }
                    )
                    Tab.Tasks -> TasksScreen(
                        repository = taskRepository,
                        sortOrder = sortOrder,
                        showArchived = showArchived,
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
            var selectedType by remember { mutableStateOf<EntryType?>(null) }
            NewEntryDialog(
                onDismiss = { showNewDialog = false },
                onCreate = { type ->
                    selectedType = type
                    showNewDialog = false
                },
                templates = templateRepository.getTemplates()
            )
            selectedType?.let { type ->
                LaunchedEffect(type) {
                    when (type) {
                        EntryType.JOURNAL -> {}
                        EntryType.NOTE -> {}
                        EntryType.TASK -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun NewEntryDialog(
    onDismiss: () -> Unit,
    onCreate: (EntryType) -> Unit,
    templates: List<EntryTemplate> = emptyList()
) {
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
                        onClick = { onCreate(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
                if (templates.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Templates:", style = MaterialTheme.typography.labelMedium)
                    templates.forEach { template ->
                        TextButton(
                            onClick = {
                                onCreate(template.type)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(template.title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}