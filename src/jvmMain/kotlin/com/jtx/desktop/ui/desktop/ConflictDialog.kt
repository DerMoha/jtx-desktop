package com.jtx.desktop.ui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConflictResolutionDialog(
    localTitle: String,
    serverTitle: String,
    localModified: Long,
    serverModified: Long,
    onKeepLocal: () -> Unit,
    onKeepServer: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Conflict Detected") },
        text = {
            Column {
                Text("The entry \"$localTitle\" has been modified both locally and on the server.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Local version: ${formatTimestamp(localModified)}")
                Text("Server version: ${formatTimestamp(serverModified)}")
                Spacer(modifier = Modifier.height(16.dp))
                Text("How would you like to resolve this conflict?")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onKeepLocal) {
                    Text("Keep Local")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onKeepServer) {
                    Text("Keep Server")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onKeepBoth) {
                    Text("Keep Both")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}