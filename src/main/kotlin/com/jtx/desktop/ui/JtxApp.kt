package com.jtx.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.jtx.desktop.ui.theme.JtxBoardTheme
import com.jtx.desktop.ui.screens.journals.JournalsScreen
import com.jtx.desktop.ui.screens.notes.NotesScreen
import com.jtx.desktop.ui.screens.tasks.TasksScreen
import com.jtx.desktop.ui.screens.kanban.KanbanScreen
import com.jtx.desktop.domain.model.EntryType

enum class Tab(val title: String, val icon: ImageVector) {
    Journals("Journals", Icons.Default.Book),
    Notes("Notes", Icons.Default.StickyNote2),
    Tasks("Tasks", Icons.Default.CheckCircle),
    Kanban("Board", Icons.Default.Dashboard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JtxApp() {
    var selectedTab by remember { mutableStateOf(Tab.Journals) }
    var showNewDialog by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync")
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
                FloatingActionButton(
                    onClick = { showNewDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    Tab.Journals -> JournalsScreen()
                    Tab.Notes -> NotesScreen()
                    Tab.Tasks -> TasksScreen()
                    Tab.Kanban -> KanbanScreen()
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