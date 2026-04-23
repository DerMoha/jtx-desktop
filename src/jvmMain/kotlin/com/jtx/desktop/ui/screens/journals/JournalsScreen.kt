package com.jtx.desktop.ui.screens.journals

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
import com.jtx.desktop.data.repository.JournalRepository
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.JournalEntry
import com.jtx.desktop.ui.components.EntryCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalsScreen(repository: JournalRepository) {
    var journals by remember { mutableStateOf(listOf<CombinedEntry>()) }
    LaunchedEffect(Unit) {
        repository.getAllCombined().collect { journals = it }
    }
    var selectedEntry by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(journals, key = { it.id }) { journal ->
            EntryCard(
                entry = journal,
                onClick = { selectedEntry = journal }
            )
        }

        if (journals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No journals yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (selectedEntry != null && !isEditing) {
        JournalDetailDialog(
            entry = selectedEntry!!,
            onDismiss = { selectedEntry = null },
            onEdit = { isEditing = true },
            onDelete = {
                kotlinx.coroutines.runBlocking {
                    repository.delete(selectedEntry!!.id)
                }
                selectedEntry = null
            }
        )
    }

    if (selectedEntry != null && isEditing) {
        JournalEditDialog(
            entry = selectedEntry!!,
            onDismiss = { isEditing = false },
            onSave = { updated ->
                kotlinx.coroutines.runBlocking {
                    repository.updateJournal(updated)
                }
                selectedEntry = null
                isEditing = false
            }
        )
    }
}

@Composable
fun JournalDetailDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                        "Date: ${formatDate(entry.date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.categories.isNotEmpty()) {
                    Text(
                        "Categories: ${entry.categories.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
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
fun JournalEditDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onSave: (CombinedEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var description by remember { mutableStateOf(entry.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Journal") },
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(entry.copy(title = title, description = description))
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