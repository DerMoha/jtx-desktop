package com.jtx.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtx.desktop.domain.model.EntryAttachment
import java.awt.Desktop
import java.io.File
import java.net.URI

@Composable
fun AttachmentsSection(
    attachments: List<EntryAttachment>,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            "Attachments",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        attachments.forEach { attachment ->
            AttachmentRow(attachment)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AttachmentRow(attachment: EntryAttachment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openAttachment(attachment) }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    attachment.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    attachment.localPath ?: attachment.uri,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun EntryAttachment.displayName(): String {
    return filename?.takeIf { it.isNotBlank() }
        ?: uri.substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: "Attachment"
}

private fun openAttachment(attachment: EntryAttachment) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    runCatching {
        val localPath = attachment.localPath
        if (!localPath.isNullOrBlank()) {
            desktop.open(File(localPath))
        } else {
            val uri = URI(attachment.uri)
            if (uri.scheme.equals("file", ignoreCase = true)) {
                desktop.open(File(uri))
            } else {
                desktop.browse(uri)
            }
        }
    }
}
