package com.jtx.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CategoryChips(
    categories: List<String>,
    modifier: Modifier = Modifier,
    limit: Int = 6,
    onCategoryClick: (String) -> Unit = {}
) {
    if (categories.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.take(limit).forEach { category ->
            AssistChip(
                onClick = { onCategoryClick(category) },
                label = { Text(category) }
            )
        }
        val remaining = categories.size - limit
        if (remaining > 0) {
            AssistChip(
                onClick = {},
                label = { Text("+$remaining") }
            )
        }
    }
}
