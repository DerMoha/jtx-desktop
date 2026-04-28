package com.jtx.desktop.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.data.repository.TaskRepository
import com.jtx.desktop.domain.model.*
import com.jtx.desktop.ui.SortOrder
import com.jtx.desktop.ui.components.EntryCard
import com.jtx.desktop.ui.components.SearchBar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
    searchFocusRequest: Int = 0,
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
                focusRequest = searchFocusRequest,
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
    var dueDate by remember { mutableStateOf(entry.date?.toDateTimeInput().orEmpty()) }
    var startDate by remember { mutableStateOf(entry.startDate?.toDateTimeInput().orEmpty()) }
    var completed by remember { mutableStateOf(entry.completed == true) }
    var progress by remember { mutableIntStateOf(entry.progress ?: 0) }
    var descriptionFormat by remember { mutableStateOf(entry.descriptionFormat) }
    var priority by remember { mutableStateOf(entry.priority) }
    var showRecurrence by remember { mutableStateOf(entry.recurrenceRule != null) }
    var recurrenceFrequency by remember { mutableStateOf(entry.recurrenceRule?.frequency ?: RecurrenceFrequency.WEEKLY) }
    var recurrenceInterval by remember { mutableIntStateOf(entry.recurrenceRule?.interval ?: 1) }
    var categories by remember { mutableStateOf(entry.categories.joinToString(", ")) }
    var color by remember { mutableStateOf(entry.color.orEmpty()) }
    var location by remember { mutableStateOf(entry.location.orEmpty()) }
    var reminders by remember { mutableStateOf(entry.reminders.joinToString(", ") { it.minutesBefore.toString() }) }
    var comments by remember { mutableStateOf(entry.comments.joinToString("\n") { it.text }) }
    var subtasks by remember { mutableStateOf(entry.subtasks.joinToString("\n") { subtask -> "${if (subtask.completed) "[x]" else "[ ]"} ${subtask.title}" }) }
    var attachments by remember { mutableStateOf(entry.attachments.joinToString(", ") { it.uri }) }
    var relatedEntries by remember { mutableStateOf(entry.relatedEntries.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = descriptionFormat == DescriptionFormat.PLAIN,
                        onClick = { descriptionFormat = DescriptionFormat.PLAIN },
                        label = { Text("Plain") }
                    )
                    FilterChip(
                        selected = descriptionFormat == DescriptionFormat.MARKDOWN,
                        onClick = { descriptionFormat = DescriptionFormat.MARKDOWN },
                        label = { Text("Markdown") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start date/time") },
                    supportingText = { Text("Blank clears. Use yyyy-MM-dd, yyyy-MM-dd HH:mm, ISO, or millis.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due date/time") },
                    supportingText = { Text("Blank clears. ISO values can include timezone or Z.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed, onCheckedChange = { completed = it })
                    Text("Completed")
                }
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
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { item ->
                        FilterChip(
                            selected = priority == item,
                            onClick = { priority = item },
                            label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reminders,
                    onValueChange = { reminders = it },
                    label = { Text("Reminder minutes before") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categories,
                    onValueChange = { categories = it },
                    label = { Text("Categories") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subtasks,
                    onValueChange = { subtasks = it },
                    label = { Text("Subtasks ([x] Done)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Comments (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = attachments,
                    onValueChange = { attachments = it },
                    label = { Text("Attachment URIs") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = relatedEntries,
                    onValueChange = { relatedEntries = it },
                    label = { Text("Related entry IDs") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    entry.copy(
                        title = title,
                        description = description,
                        descriptionFormat = descriptionFormat,
                        date = dueDate.parseDateTimeInput(),
                        startDate = startDate.parseDateTimeInput(),
                        completed = completed,
                        progress = progress,
                        priority = priority,
                        recurrenceRule = if (showRecurrence) RecurrenceRule(recurrenceFrequency, recurrenceInterval.coerceAtLeast(1)) else null,
                        reminders = reminders.toCsvList().mapNotNull { it.toIntOrNull() }.map { Reminder(it) },
                        categories = categories.toCsvList(),
                        color = color.ifBlank { null },
                        location = location.ifBlank { null },
                        subtasks = subtasks.toSubtasks(entry.subtasks),
                        comments = comments.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { EntryComment(it) },
                        attachments = attachments.toCsvList().map { EntryAttachment(uri = it) },
                        relatedEntries = relatedEntries.toCsvList()
                    )
                )
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

private fun String.toCsvList(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun Long.toDateTimeInput(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()
    .toString()

private fun String.parseDateTimeInput(): Long? {
    val value = trim()
    if (value.isEmpty()) return null
    value.toLongOrNull()?.let { return it }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
}

private fun String.toSubtasks(existing: List<Subtask>): List<Subtask> = lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapIndexed { index, line ->
        val completed = line.startsWith("[x]", ignoreCase = true)
        val title = line.removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]").trim()
        Subtask(
            id = existing.getOrNull(index)?.id ?: UUID.randomUUID().toString(),
            title = title,
            completed = completed
        )
    }

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
