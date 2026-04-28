package com.jtx.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.domain.model.CombinedEntry

@Composable
fun RelatedEntriesSection(
    relatedEntries: List<CombinedEntry>,
    onEntryClick: (CombinedEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (relatedEntries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Linked entries",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        relatedEntries.forEach { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEntryClick(entry) }
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = entry.title.ifBlank { "(No title)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${entry.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${entry.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun relatedEntriesFor(entry: CombinedEntry, allEntries: List<CombinedEntry>): List<CombinedEntry> {
    val directIds = entry.relatedEntries.toSet()
    return allEntries
        .asSequence()
        .filter { it.id != entry.id }
        .filter { it.id in directIds || entry.id in it.relatedEntries }
        .distinctBy { it.id }
        .sortedWith(compareBy<CombinedEntry> { it.type.name }.thenBy { it.title.lowercase() })
        .toList()
}
