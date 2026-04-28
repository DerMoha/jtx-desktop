package com.jtx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jtx.desktop.data.local.exportJsonBackup
import com.jtx.desktop.data.local.importJsonBackup
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.data.repository.TemplateRepository
import com.jtx.desktop.domain.model.*
import com.jtx.desktop.ui.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncRepository: SyncRepository,
    templateRepository: TemplateRepository? = null,
    onSync: () -> Unit,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    onDarkModeChange: (DarkModePreference) -> Unit = {},
    listDensity: ListDensity = ListDensity.COMFORTABLE,
    onListDensityChange: (ListDensity) -> Unit = {},
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    onSortChange: (SortOrder) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var discoveredCollections by remember { mutableStateOf<List<CalDavCollection>>(emptyList()) }

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showKanbanConfig by remember { mutableStateOf(false) }
    var kanbanColumns by remember { mutableStateOf(settings.kanbanColumns) }

    LaunchedEffect(Unit) {
        settings = syncRepository.getSettings()
        settings.credentials?.let {
            serverUrl = it.serverUrl
            username = it.username
            password = it.password
        }
        collection = settings.collection ?: ""
        kanbanColumns = settings.kanbanColumns
        discoveredCollections = syncRepository.localDataSource.getAllCollections().first()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DarkModePreference.entries.forEach { pref ->
                        FilterChip(
                            selected = darkModePreference == pref,
                            onClick = { onDarkModeChange(pref) },
                            label = {
                                Text(
                                    when (pref) {
                                        DarkModePreference.LIGHT -> "Light"
                                        DarkModePreference.DARK -> "Dark"
                                        DarkModePreference.SYSTEM -> "System"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (pref) {
                                        DarkModePreference.LIGHT -> Icons.Default.LightMode
                                        DarkModePreference.DARK -> Icons.Default.DarkMode
                                        DarkModePreference.SYSTEM -> Icons.Default.Settings
                                    },
                                    null
                                )
                            }
                        )
                    }
                }
            }
        }

        Text("List Density", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListDensity.entries.forEach { density ->
                FilterChip(
                    selected = listDensity == density,
                    onClick = { onListDensityChange(density) },
                    label = {
                        Text(
                            when (density) {
                                ListDensity.COMPACT -> "Compact"
                                ListDensity.COMFORTABLE -> "Comfortable"
                            }
                        )
                    }
                )
            }
        }

        Text(
            text = "Default Sort",
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOrder.entries.take(3).forEach { order ->
                FilterChip(
                    selected = sortOrder == order,
                    onClick = { onSortChange(order) },
                    label = {
                        Text(
                            when (order) {
                                SortOrder.DATE_DESC -> "Date ↓"
                                SortOrder.DATE_ASC -> "Date ↑"
                                SortOrder.TITLE_ASC -> "A-Z"
                                SortOrder.TITLE_DESC -> "Z-A"
                                SortOrder.MODIFIED_DESC -> "Modified ↓"
                                SortOrder.MODIFIED_ASC -> "Modified ↑"
                            }
                        )
                    }
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "CalDAV Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://caldav.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    }
                )

                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Calendar Collection") },
                    placeholder = { Text("principals/username/calendar") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        isDiscovering = true
                        syncMessage = null
                        scope.launch {
                            val credentials = CalDavCredentials(serverUrl, username, password)
                            val result = syncRepository.discoverCollections(credentials)
                            result.fold(
                                onSuccess = { collections ->
                                    discoveredCollections = collections
                                    if (collection.isBlank()) {
                                        collection = collections.firstOrNull()?.url.orEmpty()
                                    }
                                    syncMessage = "Discovered ${collections.size} CalDAV collection${if (collections.size == 1) "" else "s"}"
                                },
                                onFailure = { error ->
                                    syncMessage = "Discovery failed: ${error.message}"
                                }
                            )
                            isDiscovering = false
                        }
                    },
                    enabled = !isSaving && !isSyncing && !isDiscovering && serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Discover Collections")
                    }
                }

                discoveredCollections.forEach { discovered ->
                    CollectionCapabilityCard(
                        collection = discovered,
                        selected = collection == discovered.url,
                        onSelect = { collection = discovered.url }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val credentials = if (serverUrl.isNotBlank()) {
                            CalDavCredentials(serverUrl, username, password)
                        } else null
                        syncRepository.saveSettings(
                            settings.copy(
                                credentials = credentials,
                                collection = collection.ifBlank { null },
                                darkModePreference = darkModePreference,
                                sortPreference = when (sortOrder) {
                                    SortOrder.DATE_DESC -> SortPreference(SortField.DATE, false)
                                    SortOrder.DATE_ASC -> SortPreference(SortField.DATE, true)
                                    SortOrder.TITLE_ASC -> SortPreference(SortField.TITLE, true)
                                    SortOrder.TITLE_DESC -> SortPreference(SortField.TITLE, false)
                                    SortOrder.MODIFIED_DESC -> SortPreference(SortField.MODIFIED, false)
                                    SortOrder.MODIFIED_ASC -> SortPreference(SortField.MODIFIED, true)
                                },
                                listDensity = listDensity,
                                kanbanColumns = kanbanColumns.normalizedKanbanColumns()
                            )
                        )
                        kanbanColumns = kanbanColumns.normalizedKanbanColumns()
                        isSaving = false
                        syncMessage = "Settings saved"
                    }
                },
                enabled = !isSaving && !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Settings")
                }
            }

            OutlinedButton(
                onClick = {
                    isSyncing = true
                    syncMessage = null
                    scope.launch {
                        val creds = settings.credentials
                        val coll = settings.collection
                        if (creds != null && coll != null) {
                            val result = syncRepository.sync(creds, coll)
                            syncMessage = result.fold(
                                onSuccess = { syncResult ->
                                    when {
                                        syncResult.failureCount > 0 -> {
                                            "Sync completed: ${syncResult.successCount} synced, ${syncResult.failureCount} failed"
                                        }
                                        else -> "Sync completed successfully (${syncResult.successCount} entries)"
                                    }
                                },
                                onFailure = { "Sync failed: ${it.message}" }
                            )
                            if (result.isSuccess) {
                                onSync()
                            }
                        } else {
                            syncMessage = "Please configure CalDAV settings first"
                        }
                        isSyncing = false
                    }
                },
                enabled = !isSaving && !isSyncing && serverUrl.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sync Now")
                }
            }
        }

        syncMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.startsWith("Sync failed") || msg.startsWith("Discovery failed") || msg.startsWith("Please"))
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(16.dp),
                    color = if (msg.startsWith("Sync failed") || msg.startsWith("Discovery failed") || msg.startsWith("Please"))
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        settings.lastSyncTime?.let { timestamp ->
            Text(
                text = "Last sync: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text(
            text = "Data Management",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Data")
            }

            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Data")
            }
        }

        HorizontalDivider()

        Text(
            text = "Kanban Board",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        TextButton(onClick = { showKanbanConfig = true }) {
            Icon(Icons.Default.ViewColumn, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Configure Columns")
        }

        if (showKanbanConfig) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    kanbanColumns.forEachIndexed { index, column ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = column.title,
                                    onValueChange = { newTitle ->
                                        kanbanColumns = kanbanColumns.toMutableList().apply {
                                            set(index, column.copy(title = newTitle))
                                        }
                                    },
                                    label = { Text("Column ${index + 1} Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = column.progressMin.toString(),
                                        onValueChange = { value ->
                                            kanbanColumns = kanbanColumns.toMutableList().apply {
                                                set(index, column.copy(progressMin = value.toIntOrNull()?.coerceIn(0, 100) ?: 0))
                                            }
                                        },
                                        label = { Text("Min %") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = column.progressMax.toString(),
                                        onValueChange = { value ->
                                            kanbanColumns = kanbanColumns.toMutableList().apply {
                                                set(index, column.copy(progressMax = value.toIntOrNull()?.coerceIn(0, 100) ?: 0))
                                            }
                                        },
                                        label = { Text("Max %") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                kanbanColumns = kanbanColumns.toMutableList().apply {
                                    removeAt(index)
                                }
                            }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            kanbanColumns = kanbanColumns + KanbanColumnConfig(
                                id = "col_${System.currentTimeMillis()}",
                                title = "New Column",
                                color = 0xFF9E9E9E,
                                progressMin = 0,
                                progressMax = 0
                            )
                        }) {
                            Icon(Icons.Default.Add, null)
                            Text("Add Column")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            kanbanColumns = defaultKanbanColumns
                        }) {
                            Text("Reset to Default")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = "Templates",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Built-in Templates", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                templateRepository?.getTemplates()?.forEach { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (template.type) {
                                EntryType.TASK -> Icons.Default.CheckCircle
                                EntryType.NOTE -> Icons.Default.StickyNote2
                                EntryType.JOURNAL -> Icons.Default.Book
                            },
                            null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(template.title, modifier = Modifier.weight(1f))
                    }
                } ?: Text("No templates available", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Data") },
            text = {
                Column {
                    Text("Export all your data to a JSON file.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This will include all journals, notes, and tasks.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val file = chooseJsonBackupFile("Export Backup", FileDialog.SAVE) ?: return@launch
                        val json = syncRepository.localDataSource.exportJsonBackup()
                        withContext(Dispatchers.IO) { file.writeText(json) }
                        syncMessage = "Backup exported to ${file.name}"
                        showExportDialog = false
                    }
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Data") },
            text = {
                Column {
                    Text("Import data from a JSON file.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Warning: This will merge with existing data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val file = chooseJsonBackupFile("Import Backup", FileDialog.LOAD) ?: return@launch
                        try {
                            val json = withContext(Dispatchers.IO) { file.readText() }
                            val result = syncRepository.localDataSource.importJsonBackup(json)
                            settings = syncRepository.getSettings()
                            syncMessage = "Restored ${result.totalEntries} entries from ${file.name}"
                        } catch (e: Exception) {
                            syncMessage = "Import failed: ${e.message}"
                        }
                        showImportDialog = false
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CollectionCapabilityCard(
    collection: CalDavCollection,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val components = collection.supportedComponents.joinToString { component ->
        when (component) {
            EntryType.JOURNAL -> "journals"
            EntryType.NOTE -> "notes"
            EntryType.TASK -> "tasks"
        }
    }.ifBlank { "unknown components" }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(collection.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(collection.url, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onSelect, enabled = !selected) {
                    Text(if (selected) "Selected" else "Select")
                }
            }
            Text(
                text = "Supports $components${if (collection.readOnly) " (read-only)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            collection.color?.takeIf { it.isNotBlank() }?.let { color ->
                Text(
                    text = "Color: $color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun List<KanbanColumnConfig>.normalizedKanbanColumns(): List<KanbanColumnConfig> {
    return if (isEmpty()) {
        defaultKanbanColumns
    } else {
        map { column ->
            val min = column.progressMin.coerceIn(0, 100)
            val max = column.progressMax.coerceIn(0, 100)
            column.copy(
                progressMin = min.coerceAtMost(max),
                progressMax = max.coerceAtLeast(min)
            )
        }
    }
}

private suspend fun chooseJsonBackupFile(title: String, mode: Int): File? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as java.awt.Frame?, title, mode).apply {
        file = if (mode == FileDialog.SAVE) "jtxboard-backup.json" else "*.json"
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val selectedFile = dialog.file ?: return@withContext null
    File(dialog.directory, selectedFile).withJsonExtensionIfMissing(mode)
}

private fun File.withJsonExtensionIfMissing(mode: Int): File {
    if (mode != FileDialog.SAVE || name.endsWith(".json", ignoreCase = true)) return this
    return File(parentFile, "$name.json")
}
