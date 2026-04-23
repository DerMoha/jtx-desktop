package com.jtx.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import java.awt.Dimension

fun main() = application {
    val windowState = WindowState()
    Window(
        onCloseRequest = ::exitApplication,
        title = "jtxBoard Desktop",
        state = windowState
    ) {
        window.minimumSize = Dimension(800, 600)
        com.jtx.desktop.ui.JtxApp()
    }
}