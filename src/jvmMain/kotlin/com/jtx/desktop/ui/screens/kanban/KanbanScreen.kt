package com.jtx.desktop.ui.screens.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.data.repository.TaskRepository
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType

data class KanbanColumn(
    val title: String,
    val color: Color,
    val entries: List<CombinedEntry>
)

@Composable
fun KanbanScreen(repository: TaskRepository) {
    var columns by remember {
        mutableStateOf(
            listOf(
                KanbanColumn("To Do", Color(0xFF2196F3), emptyList()),
                KanbanColumn("In Progress", Color(0xFFFFC107), emptyList()),
                KanbanColumn("Done", Color(0xFF4CAF50), emptyList())
            )
        )
    }

    LaunchedEffect(Unit) {
        repository.getAllCombined().collect { tasks ->
            columns = listOf(
                KanbanColumn("To Do", Color(0xFF2196F3), tasks.filter { it.completed != true }),
                KanbanColumn("In Progress", Color(0xFFFFC107), emptyList()),
                KanbanColumn("Done", Color(0xFF4CAF50), tasks.filter { it.completed == true })
            )
        }
    }

    var draggedEntry by remember { mutableStateOf<CombinedEntry?>(null) }

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(columns.size) { columnIndex ->
            KanbanColumnCard(
                column = columns[columnIndex],
                onEntryClick = { entry -> },
                onDrop = { entry ->
                    columns = columns.mapIndexed { index, col ->
                        if (index == columnIndex) {
                            col.copy(entries = col.entries + entry)
                        } else {
                            col.copy(entries = col.entries.filter { it.id != entry.id })
                        }
                    }
                    draggedEntry = null
                }
            )
        }
    }
}

@Composable
fun KanbanColumnCard(
    column: KanbanColumn,
    onEntryClick: (CombinedEntry) -> Unit,
    onDrop: (CombinedEntry) -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(column.color, RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${column.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(column.entries, key = { it.id }) { entry ->
                    DraggableEntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun DraggableEntryCard(
    entry: CombinedEntry,
    onClick: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = entry.title.ifEmpty { "(No title)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            if (entry.description.isNotEmpty()) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (entry.type == EntryType.TASK && entry.progress != null) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { entry.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}