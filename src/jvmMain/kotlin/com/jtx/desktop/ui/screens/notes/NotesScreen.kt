package com.jtx.desktop.ui.screens.notes

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
import com.jtx.desktop.data.repository.NoteRepository
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.ui.SortOrder
import com.jtx.desktop.ui.components.EntryCard
import com.jtx.desktop.ui.components.SearchBar

@Composable
fun NotesScreen(
    repository: NoteRepository,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    showArchived: Boolean = false,
    onSortChange: (SortOrder) -> Unit = {},
    onShowArchivedChange: (Boolean) -> Unit = {},
    onDelete: (CombinedEntry) -> Unit = {},
    onUpdate: (CombinedEntry, CombinedEntry) -> Unit = { _, _ -> }
) {
    var notes by remember { mutableStateOf(listOf<CombinedEntry>()) }
    LaunchedEffect(showArchived) {
        repository.getAllCombined(includeArchived = showArchived).collect { notes = it }
    }
    var selectedNote by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredNotes = remember(notes, searchQuery, sortOrder, showArchived) {
        val base = if (showArchived) notes.filter { it.archived } else notes.filter { !it.archived }
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
            SortOrder.MODIFIED_DESC -> searched.sortedByDescending { it.date ?: 0 }
            SortOrder.MODIFIED_ASC -> searched.sortedBy { it.date ?: 0 }
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
                placeholder = "Search notes...",
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredNotes, key = { it.id }) { note ->
                EntryCard(
                    entry = note,
                    onClick = { selectedNote = note }
                )
            }

            if (filteredNotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No notes found" else "No notes yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (selectedNote != null && !isEditing) {
        NoteDetailDialog(
            entry = selectedNote!!,
            onDismiss = { selectedNote = null },
            onEdit = { isEditing = true },
            onDelete = {
                onDelete(selectedNote!!)
                kotlinx.coroutines.runBlocking {
                    repository.delete(selectedNote!!.id)
                }
                selectedNote = null
            },
            onRestore = {
                kotlinx.coroutines.runBlocking {
                    repository.restore(selectedNote!!.id)
                }
                selectedNote = null
            },
            onPermanentDelete = {
                kotlinx.coroutines.runBlocking {
                    repository.permanentlyDelete(selectedNote!!.id)
                }
                selectedNote = null
            }
        )
    }

    if (selectedNote != null && isEditing) {
        NoteEditDialog(
            entry = selectedNote!!,
            onDismiss = { isEditing = false },
            onSave = { updated ->
                onUpdate(updated, selectedNote!!)
                kotlinx.coroutines.runBlocking {
                    repository.updateNote(updated)
                }
                selectedNote = null
                isEditing = false
            }
        )
    }
}

@Composable
fun NoteDetailDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
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
                if (entry.categories.isNotEmpty()) {
                    Text(
                        "Categories: ${entry.categories.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
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

@Composable
fun NoteEditDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onSave: (CombinedEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var description by remember { mutableStateOf(entry.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Note") },
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
