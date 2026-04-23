package com.jtx.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import com.jtx.desktop.data.local.InMemoryLocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.ui.JtxApp
import java.awt.Dimension

fun main() = application {
    val windowState = WindowState()
    val localDataSource = InMemoryLocalDataSource()
    val calDavClient = CalDavClient()
    val parser = ICalendarParser()
    val syncRepository = SyncRepository(localDataSource, calDavClient, parser)

    Window(
        onCloseRequest = ::exitApplication,
        title = "jtxBoard Desktop",
        state = windowState
    ) {
        window.minimumSize = Dimension(800, 600)
        JtxApp(syncRepository = syncRepository)
    }
}