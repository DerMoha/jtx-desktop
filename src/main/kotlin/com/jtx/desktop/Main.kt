package com.jtx.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(title = "jtxBoard Desktop") {
        com.jtx.desktop.ui.JtxApp()
    }
}