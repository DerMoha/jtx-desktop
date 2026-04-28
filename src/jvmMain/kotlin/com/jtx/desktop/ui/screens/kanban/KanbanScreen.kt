package com.jtx.desktop.ui.screens.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.jtx.desktop.domain.model.KanbanColumnConfig

data class KanbanColumn(
    val config: KanbanColumnConfig,
    val entries: List<CombinedEntry>
)

@Composable
fun KanbanScreen(
    repository: TaskRepository,
    kanbanColumns: List<KanbanColumnConfig> = emptyList(),
    onTaskMove: (String, Int) -> Unit = { _, _ -> }
) {
    var columns by remember {
        mutableStateOf(
            kanbanColumns.ifEmpty { defaultColumns }.map { config ->
                KanbanColumn(config, emptyList())
            }
        )
    }

    LaunchedEffect(Unit, kanbanColumns) {
        repository.getAllCombined().collect { tasks ->
            val updatedColumns = if (kanbanColumns.isEmpty()) {
                defaultColumns
            } else {
                kanbanColumns
            }.map { config ->
                val columnTasks = tasks.filter { task ->
                    val progress = task.progress ?: 0
                    progress >= config.progressMin && progress <= config.progressMax
                }
                KanbanColumn(config, columnTasks)
            }
            columns = updatedColumns
        }
    }

    var draggedEntry by remember { mutableStateOf<CombinedEntry?>(null) }
    var dragTargetColumn by remember { mutableStateOf<String?>(null) }

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(columns.size) { columnIndex ->
            val column = columns[columnIndex]
            KanbanColumnCard(
                column = column,
                onEntryClick = { entry -> },
                onDragOver = { dragTargetColumn = column.config.id },
                onDragLeave = { if (dragTargetColumn == column.config.id) dragTargetColumn = null },
                onDrop = { entry ->
                    val targetColumn = columns.find { it.config.id == dragTargetColumn } ?: column
                    val newProgress = when {
                        targetColumn.config.progressMax == 0 -> 0
                        targetColumn.config.progressMin == 100 -> 100
                        else -> 50
                    }
                    onTaskMove(entry.id, newProgress)
                    dragTargetColumn = null
                    draggedEntry = null
                },
                isDropTarget = dragTargetColumn == column.config.id,
                onDragStart = { draggedEntry = it }
            )
        }
    }
}

@Composable
fun KanbanColumnCard(
    column: KanbanColumn,
    onEntryClick: (CombinedEntry) -> Unit,
    onDragOver: () -> Unit,
    onDragLeave: () -> Unit,
    onDrop: (CombinedEntry) -> Unit,
    isDropTarget: Boolean = false,
    onDragStart: (CombinedEntry) -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .then(
                if (isDropTarget) Modifier.background(
                    Color(column.config.color).copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
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
                        .background(Color(column.config.color), RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = column.config.title,
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
                        onClick = { onEntryClick(entry) },
                        onDragStart = { onDragStart(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun DraggableEntryCard(
    entry: CombinedEntry,
    onClick: () -> Unit,
    onDragStart: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                alpha = if (isDragging) 0.8f else 1f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        isDragging = false
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        isDragging = false
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
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

private val defaultColumns = listOf(
    KanbanColumnConfig("todo", "To Do", 0xFF2196F3, 0, 0),
    KanbanColumnConfig("inprogress", "In Progress", 0xFFFFC107, 1, 99),
    KanbanColumnConfig("done", "Done", 0xFF4CAF50, 100, 100)
)