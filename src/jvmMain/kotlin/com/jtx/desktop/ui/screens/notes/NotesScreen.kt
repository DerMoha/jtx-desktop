package com.jtx.desktop.ui.screens.notes

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
import com.jtx.desktop.data.local.AttachmentCache
import com.jtx.desktop.data.repository.NoteRepository
import com.jtx.desktop.domain.model.*
import com.jtx.desktop.ui.SortOrder
import com.jtx.desktop.ui.components.EntryCard
import com.jtx.desktop.ui.components.MarkdownText
import com.jtx.desktop.ui.components.RelatedEntriesSection
import com.jtx.desktop.ui.components.SearchBar
import com.jtx.desktop.ui.components.relatedEntriesFor
import java.awt.FileDialog
import java.awt.Frame

@Composable
fun NotesScreen(
    repository: NoteRepository,
    allEntries: List<CombinedEntry> = emptyList(),
    openEntryRequest: CombinedEntry? = null,
    onOpenEntryRequestHandled: () -> Unit = {},
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    showArchived: Boolean = false,
    searchFocusRequest: Int = 0,
    onSortChange: (SortOrder) -> Unit = {},
    onShowArchivedChange: (Boolean) -> Unit = {},
    onDelete: (CombinedEntry) -> Unit = {},
    onUpdate: (CombinedEntry, CombinedEntry) -> Unit = { _, _ -> },
    onOpenRelatedEntry: (CombinedEntry) -> Unit = {}
) {
    var notes by remember { mutableStateOf(listOf<CombinedEntry>()) }
    LaunchedEffect(showArchived) {
        repository.getAllCombined(includeArchived = showArchived).collect { notes = it }
    }
    var selectedNote by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(openEntryRequest) {
        if (openEntryRequest?.type == EntryType.NOTE) {
            selectedNote = openEntryRequest
            isEditing = false
            onOpenEntryRequestHandled()
        }
    }

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
                placeholder = "Search notes...",
                focusRequest = searchFocusRequest,
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
            relatedEntries = relatedEntriesFor(selectedNote!!, allEntries),
            onRelatedEntryClick = { relatedEntry ->
                if (relatedEntry.type == EntryType.NOTE) {
                    selectedNote = relatedEntry
                    isEditing = false
                } else {
                    onOpenRelatedEntry(relatedEntry)
                }
            },
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
    relatedEntries: List<CombinedEntry> = emptyList(),
    onRelatedEntryClick: (CombinedEntry) -> Unit = {},
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
                    MarkdownText(entry.description, entry.descriptionFormat)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (entry.categories.isNotEmpty()) {
                    Text(
                        "Categories: ${entry.categories.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (relatedEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    RelatedEntriesSection(relatedEntries, onEntryClick = onRelatedEntryClick)
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
    var descriptionFormat by remember { mutableStateOf(entry.descriptionFormat) }
    var categories by remember { mutableStateOf(entry.categories.joinToString(", ")) }
    var color by remember { mutableStateOf(entry.color.orEmpty()) }
    var location by remember { mutableStateOf(entry.location.orEmpty()) }
    var comments by remember { mutableStateOf(entry.comments.joinToString("\n") { it.text }) }
    var attachments by remember { mutableStateOf(entry.attachments.joinToString(", ") { it.uri }) }
    var relatedEntries by remember { mutableStateOf(entry.relatedEntries.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Note") },
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
                    value = categories,
                    onValueChange = { categories = it },
                    label = { Text("Categories") },
                    supportingText = { Text("Comma or line separated") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color") },
                    supportingText = { Text("CSS color, e.g. #3F51B5 or red. Blank clears.") },
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
                TextButton(onClick = {
                    chooseAttachmentUri()?.let { uri ->
                        attachments = listOf(attachments, uri).filter { it.isNotBlank() }.joinToString(", ")
                    }
                }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add File")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = relatedEntries,
                    onValueChange = { relatedEntries = it },
                    label = { Text("Related task IDs") },
                    supportingText = { Text("Comma or line separated IDs to link or unlink entries") },
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
                        categories = categories.toTokenList(),
                        color = color.toCssColorOrNull(),
                        location = location.ifBlank { null },
                        comments = comments.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { EntryComment(it) },
                        attachments = attachments.toAttachments(),
                        relatedEntries = relatedEntries.toTokenList()
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

private fun String.toAttachments(): List<EntryAttachment> = toCsvList().map { AttachmentCache.cacheUri(it) }

private fun String.toTokenList(): List<String> = split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

private fun String.toCssColorOrNull(): String? {
    val value = trim()
    if (value.isEmpty()) return null
    val hex = value.removePrefix("#")
    return when {
        Regex("[0-9a-fA-F]{3}").matches(hex) -> "#${hex.map { "$it$it" }.joinToString("").uppercase()}"
        Regex("[0-9a-fA-F]{6}").matches(hex) -> "#${hex.uppercase()}"
        else -> value
    }
}

private fun chooseAttachmentUri(): String? {
    val dialog = FileDialog(null as Frame?, "Choose Attachment", FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return java.io.File(directory, file).toURI().toString()
}
