package com.jtx.desktop.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.ui.components.EntryCard

@Composable
fun TasksScreen() {
    var tasks by remember { mutableStateOf(listOf<CombinedEntry>()) }
    var selectedTask by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            EntryCard(
                entry = task,
                onClick = { selectedTask = task }
            )
        }

        if (tasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                tasks = tasks.filter { it.id != selectedTask?.id }
                selectedTask = null
            },
            onToggleComplete = { completed ->
                tasks = tasks.map { 
                    if (it.id == selectedTask?.id) it.copy(completed = completed) else it 
                }
            }
        )
    }

    if (selectedTask != null && isEditing) {
        TaskEditDialog(
            entry = selectedTask!!,
            onDismiss = { isEditing = false },
            onSave = { updated ->
                tasks = tasks.map { if (it.id == updated.id) updated else it }
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
    onToggleComplete: (Boolean) -> Unit
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
                if (entry.completed == true) {
                    Text(
                        "Status: Completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        "Status: Pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun TaskEditDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onSave: (CombinedEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var description by remember { mutableStateOf(entry.description) }
    var progress by remember { mutableIntStateOf(entry.progress ?: 0) }

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
    val sdf = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}