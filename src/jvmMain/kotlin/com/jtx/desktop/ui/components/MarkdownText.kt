package com.jtx.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtx.desktop.domain.model.DescriptionFormat

@Composable
fun MarkdownText(
    text: String,
    format: DescriptionFormat,
    previewEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (format != DescriptionFormat.MARKDOWN || !previewEnabled) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    Column(modifier = modifier) {
        text.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> Text(
                    trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                trimmed.startsWith("## ") -> Text(
                    trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
                    "• ${trimmed.drop(2)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(8.dp))
                else -> Text(trimmed, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
