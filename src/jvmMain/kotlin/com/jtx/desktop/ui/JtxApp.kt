package com.jtx.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jtx.desktop.domain.model.EntryType

enum class Tab(val title: String, val icon: ImageVector) {
    Journals("Journals", Icons.AutoMirrored.Filled.List),
    Notes("Notes", Icons.AutoMirrored.Filled.List),
    Tasks("Tasks", Icons.Default.CheckCircle),
    Kanban("Board", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JtxApp(syncRepository: SyncRepository) {
    val journalRepository = remember { JournalRepository(syncRepository.localDataSource) }
    val noteRepository = remember { NoteRepository(syncRepository.localDataSource) }
    val taskRepository = remember { TaskRepository(syncRepository.localDataSource) }

    var selectedTab by remember { mutableStateOf(Tab.Journals) }
    var showNewDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
    }

    JtxBoardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("jtxBoard") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync")
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
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    Tab.Journals -> JournalsScreen(repository = journalRepository)
                    Tab.Notes -> NotesScreen(repository = noteRepository)
                    Tab.Tasks -> TasksScreen(repository = taskRepository)
                    Tab.Kanban -> KanbanScreen(repository = taskRepository)
                    Tab.Settings -> SettingsScreen(
                        syncRepository = syncRepository,
                        onSync = { refreshTrigger++ }
                    )
                }
            }
        }

        if (showNewDialog) {
            NewEntryDialog(
                onDismiss = { showNewDialog = false },
                onCreate = { type ->
                    showNewDialog = false
                }
            )
        }
    }
}

@Composable
fun NewEntryDialog(onDismiss: () -> Unit, onCreate: (EntryType) -> Unit) {
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