package com.jtx.desktop.ui.screens.journals

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
import com.jtx.desktop.data.repository.JournalRepository
import com.jtx.desktop.domain.model.*
import com.jtx.desktop.ui.SortOrder
import com.jtx.desktop.ui.components.EntryCard
import com.jtx.desktop.ui.components.SearchBar
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalsScreen(
    repository: JournalRepository,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    showArchived: Boolean = false,
    searchFocusRequest: Int = 0,
    onSortChange: (SortOrder) -> Unit = {},
    onShowArchivedChange: (Boolean) -> Unit = {},
    onDelete: (CombinedEntry) -> Unit = {},
    onUpdate: (CombinedEntry, CombinedEntry) -> Unit = { _, _ -> }
) {
    var journals by remember { mutableStateOf(listOf<CombinedEntry>()) }
    LaunchedEffect(showArchived) {
        repository.getAllCombined(includeArchived = showArchived).collect { journals = it }
    }
    var selectedEntry by remember { mutableStateOf<CombinedEntry?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredJournals = remember(journals, searchQuery, sortOrder, showArchived) {
        val base = if (showArchived) journals.filter { it.archived } else journals.filter { !it.archived }
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
                placeholder = "Search journals...",
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
            items(filteredJournals, key = { it.id }) { journal ->
                EntryCard(
                    entry = journal,
                    onClick = { selectedEntry = journal }
                )
            }

            if (filteredJournals.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No journals found" else "No journals yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                onDelete(selectedEntry!!)
                kotlinx.coroutines.runBlocking {
                    repository.delete(selectedEntry!!.id)
                }
                selectedEntry = null
            },
            onRestore = {
                kotlinx.coroutines.runBlocking {
                    repository.restore(selectedEntry!!.id)
                }
                selectedEntry = null
            },
            onPermanentDelete = {
                kotlinx.coroutines.runBlocking {
                    repository.permanentlyDelete(selectedEntry!!.id)
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
                onUpdate(updated, selectedEntry!!)
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
fun JournalEditDialog(
    entry: CombinedEntry,
    onDismiss: () -> Unit,
    onSave: (CombinedEntry) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var description by remember { mutableStateOf(entry.description) }
    var descriptionFormat by remember { mutableStateOf(entry.descriptionFormat) }
    var startDate by remember { mutableStateOf(entry.date?.toString().orEmpty()) }
    var endDate by remember { mutableStateOf(entry.endDate?.toString().orEmpty()) }
    var categories by remember { mutableStateOf(entry.categories.joinToString(", ")) }
    var color by remember { mutableStateOf(entry.color.orEmpty()) }
    var location by remember { mutableStateOf(entry.location.orEmpty()) }
    var comments by remember { mutableStateOf(entry.comments.joinToString("\n") { it.text }) }
    var attachments by remember { mutableStateOf(entry.attachments.joinToString(", ") { it.uri }) }
    var relatedEntries by remember { mutableStateOf(entry.relatedEntries.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Journal") },
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
                    onValueChange = { startDate = it.filter(Char::isDigit) },
                    label = { Text("Start timestamp") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it.filter(Char::isDigit) },
                    label = { Text("End timestamp") },
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
                        date = startDate.toLongOrNull(),
                        endDate = endDate.toLongOrNull(),
                        categories = categories.toCsvList(),
                        color = color.ifBlank { null },
                        location = location.ifBlank { null },
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

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
