package com.jtx.desktop.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.data.repository.TaskRepository
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.domain.model.RecurrenceFrequency
import com.jtx.desktop.domain.model.RecurrenceRule
import com.jtx.desktop.ui.SortOrder
import com.jtx.desktop.ui.components.EntryCard
import com.jtx.desktop.ui.components.SearchBar
import java.text.SimpleDateFormat
import java.util.*

enum class TaskFilter {
    ALL, COMPLETED, PENDING, IN_PROGRESS
}

@Composable
fun TasksScreen(
    repository: TaskRepository,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    showArchived: Boolean = false,
    onSortChange: (SortOrder) -> Unit = {},
    onShowArchivedChange: (Boolean) -> Unit = {},
    onDelete: (CombinedEntry) -> Unit = {},
    onUpdate: (CombinedEntry, CombinedEntry) -> Unit = { _, _ -> }
) {
    var tasks by remember { mutableStateOf(listOf<CombinedEntry>()) }
    LaunchedEffect(showArchived) {
        repository.getAllCombined(includeArchived = showArchived).collect { tasks = it }
    }
    var selectedTask by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var selectedTasks by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    val filteredTasks = remember(tasks, searchQuery, sortOrder, showArchived, taskFilter) {
        var base = if (showArchived) tasks.filter { it.archived } else tasks.filter { !it.archived }
        base = when (taskFilter) {
            TaskFilter.ALL -> base
            TaskFilter.COMPLETED -> base.filter { it.completed == true }
            TaskFilter.PENDING -> base.filter { it.completed != true && (it.progress ?: 0) == 0 }
            TaskFilter.IN_PROGRESS -> base.filter { (it.progress ?: 0) > 0 && it.completed != true }
        }
        val searched = if (searchQuery.isBlank()) base else base.filter { entry ->
            entry.title.contains(searchQuery, ignoreCase = true) ||
            entry.description.contains(searchQuery, ignoreCase = true) ||
            entry.categories.any { it.contains(searchQuery, ignoreCase = true) }
        }
        when (sortOrder) {
            SortOrder.DATE_DESC -> searched.sortedByDescending { it.date ?: 0 }
            SortOrder.DATE_ASC -> searched.sortedBy { it.date ?: 0 }
            SortOrder.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
            SortOrder.MODIFIED_DESC -> searched.sortedByDescending { it.modified ?: 0 }
            SortOrder.MODIFIED_ASC -> searched.sortedBy { it.modified ?: 0 }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search tasks...",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
            }
            Box {
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (order) {
                                        SortOrder.DATE_DESC -> "Date (Newest)"
                                        SortOrder.DATE_ASC -> "Date (Oldest)"
                                        SortOrder.TITLE_ASC -> "Title (A-Z)"
                                        SortOrder.TITLE_DESC -> "Title (Z-A)"
                                        SortOrder.MODIFIED_DESC -> "Modified (Newest)"
                                        SortOrder.MODIFIED_ASC -> "Modified (Oldest)"
                                    }
                                )
                            },
                            onClick = {
                                onSortChange(order)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortOrder == order) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskFilter.entries.forEach { filter ->
                FilterChip(
                    selected = taskFilter == filter,
                    onClick = { taskFilter = filter },
                    label = {
                        Text(
                            when (filter) {
                                TaskFilter.ALL -> "All"
                                TaskFilter.COMPLETED -> "Done"
                                TaskFilter.PENDING -> "To Do"
                                TaskFilter.IN_PROGRESS -> "In Progress"
                            }
                        )
                    }
                )
            }
            FilterChip(
                selected = showArchived,
                onClick = { onShowArchivedChange(!showArchived) },
                label = { Text("Archived") },
                leadingIcon = {
                    if (showArchived) {
                        Icon(Icons.Default.Archive, contentDescription = "Archived")
                    }
                }
            )
        }

        if (isMultiSelectMode && selectedTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${selectedTasks.size} selected", modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selectedTasks.forEach { id ->
                        kotlinx.coroutines.runBlocking {
                            repository.updateTaskCompleted(id, true)
                        }
                    }
                    selectedTasks = emptySet()
                    isMultiSelectMode = false
                }) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Complete")
                }
                TextButton(onClick = {
                    selectedTasks.forEach { id ->
                        onDelete(tasks.find { it.id == id }!!)
                        kotlinx.coroutines.runBlocking {
                            repository.delete(id)
                        }
                    }
                    selectedTasks = emptySet()
                    isMultiSelectMode = false
                }) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                TextButton(onClick = {
                    selectedTasks = emptySet()
                    isMultiSelectMode = false
                }) {
                    Text("Cancel")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredTasks, key = { it.id }) { task ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = selectedTasks.contains(task.id),
                            onCheckedChange = { checked ->
                                selectedTasks = if (checked) selectedTasks + task.id else selectedTasks - task.id
                            }
                        )
                    }
                    EntryCard(
                        entry = task,
                        onClick = {
                            if (isMultiSelectMode) {
                                selectedTasks = if (selectedTasks.contains(task.id)) {
                                    selectedTasks - task.id
                                } else {
                                    selectedTasks + task.id
                                }
                            } else {
                                selectedTask = task
                            }
                        },
                        modifier = if (isMultiSelectMode) Modifier.weight(1f) else Modifier
                    )
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No tasks found" else "No tasks yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (selectedTask != null && !isEditing) {
        TaskDetailDialog(
            entry = selectedTask!!,
            onDismiss = { selectedTask = null },
            onEdit = { isEditing = true },
            onDelete = {
                onDelete(selectedTask!!)
                kotlinx.coroutines.runBlocking {
                    repository.delete(selectedTask!!.id)
                }
                selectedTask = null
            },
            onRestore = {
                kotlinx.coroutines.runBlocking {
                    repository.restore(selectedTask!!.id)
                }
                selectedTask = null
            },
            onPermanentDelete = {
                kotlinx.coroutines.runBlocking {
                    repository.permanentlyDelete(selectedTask!!.id)
                }
                selectedTask = null
            },
            onToggleComplete = { completed ->
                kotlinx.coroutines.runBlocking {
                    repository.updateTaskCompleted(selectedTask!!.id, completed)
                }
            },
            onToggleMultiSelect = { isMultiSelectMode = true }
        )
    }

    if (selectedTask != null && isEditing) {
        TaskEditDialog(
            entry = selectedTask!!,
            onDismiss = { isEditing = false },
            onSave = { updated ->
                onUpdate(updated, selectedTask!!)
                kotlinx.coroutines.runBlocking {
                    repository.updateTask(updated)
                }
                selectedTask = null
                isEditing = false
            }
        )
    }
}

@Composable
fun TaskDetailDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onToggleMultiSelect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(entry.title.ifEmpty { "(No title)" }, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (entry.description.isNotEmpty()) {
                    Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (entry.date != null) {
                    Text(
                        "Due: ${formatDate(entry.date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.progress != null) {
                    Text(
                        "Progress: ${entry.progress}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { entry.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = if (entry.completed == true) "Status: Completed" else "Status: Pending",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.completed == true) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.archived) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Archived",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (entry.completed == true) {
                    TextButton(onClick = { onToggleComplete(false) }) {
                        Text("Mark Incomplete")
                    }
                } else {
                    TextButton(onClick = { onToggleComplete(true) }) {
                        Text("Mark Complete")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.Default.Checklist, contentDescription = "Multi-select")
                }
            }
        },
        dismissButton = {
            Row {
                if (entry.archived) {
                    TextButton(onClick = onRestore) {
                        Text("Restore")
                    }
                    TextButton(onClick = onPermanentDelete) {
                        Text("Delete Forever")
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onSave: (CombinedEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var description by remember { mutableStateOf(entry.description) }
    var progress by remember { mutableIntStateOf(entry.progress ?: 0) }
    var showRecurrence by remember { mutableStateOf(false) }
    var recurrenceFrequency by remember { mutableStateOf(RecurrenceFrequency.WEEKLY) }
    var recurrenceInterval by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Progress: $progress%")
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = progress.toFloat(),
                        onValueChange = { progress = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showRecurrence = !showRecurrence }) {
                    Icon(Icons.Default.Repeat, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showRecurrence) "Hide Recurrence" else "Add Recurrence")
                }
                if (showRecurrence) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every")
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = recurrenceInterval.toString(),
                            onValueChange = { recurrenceInterval = it.toIntOrNull() ?: 1 },
                            modifier = Modifier.width(60.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = recurrenceFrequency.name.lowercase().replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                RecurrenceFrequency.entries.forEach { freq ->
                                    DropdownMenuItem(
                                        text = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            recurrenceFrequency = freq
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(entry.copy(title = title, description = description, progress = progress))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
