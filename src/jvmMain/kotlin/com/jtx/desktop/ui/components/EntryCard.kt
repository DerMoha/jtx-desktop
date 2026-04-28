package com.jtx.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.domain.model.ListDensity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EntryCard(
    entry: CombinedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    density: ListDensity = ListDensity.COMFORTABLE
) {
    val horizontalPadding = if (density == ListDensity.COMPACT) 12.dp else 16.dp
    val verticalPadding = if (density == ListDensity.COMPACT) 2.dp else 4.dp
    val contentPadding = if (density == ListDensity.COMPACT) 8.dp else 12.dp
    val descriptionLines = if (density == ListDensity.COMPACT) 1 else 2
    val categoryLimit = if (density == ListDensity.COMPACT) 2 else 3

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val indicatorColor = when (entry.type) {
                EntryType.JOURNAL -> Color(0xFF4CAF50)
                EntryType.NOTE -> Color(0xFF9C27B0)
                EntryType.TASK -> Color(0xFF2196F3)
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifEmpty { "(No title)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.description.isNotEmpty()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = descriptionLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entry.date != null) {
                    Text(
                        text = formatDate(entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (entry.categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CategoryChips(entry.categories, limit = categoryLimit)
                }
            }
            if (entry.type == EntryType.TASK) {
                if (entry.completed == true) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (entry.progress != null && entry.progress > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(indicatorColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${entry.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
