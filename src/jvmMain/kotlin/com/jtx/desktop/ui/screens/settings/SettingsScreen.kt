package com.jtx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.domain.model.AppSettings
import com.jtx.desktop.domain.model.CalDavCredentials
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncRepository: SyncRepository,
    onSync: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = syncRepository.getSettings()
        settings.credentials?.let {
            serverUrl = it.serverUrl
            username = it.username
            password = it.password
        }
        collection = settings.collection ?: ""
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
                                collection = collection.ifBlank { null }
                            )
                        )
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
                    containerColor = if (msg.startsWith("Sync failed") || msg.startsWith("Please"))
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(16.dp),
                    color = if (msg.startsWith("Sync failed") || msg.startsWith("Please"))
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
    }
}