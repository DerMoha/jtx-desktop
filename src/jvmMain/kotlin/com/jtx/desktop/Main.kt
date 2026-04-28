package com.jtx.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowState
import com.jtx.desktop.data.local.SqliteLocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.ui.JtxApp
import com.jtx.desktop.ui.desktop.TrayManager
import com.jtx.desktop.ui.desktop.createAppMenuBar
import java.awt.Dimension
import javax.swing.JFrame

var windowRef: java.awt.Window? = null
var trayManagerRef: TrayManager? = null
var minimizeToTray = true

fun main() = application {
    val windowState = WindowState()
    val localDataSource = SqliteLocalDataSource("jtx_board.db")
    val calDavClient = CalDavClient()
    val parser = ICalendarParser()
    val syncRepository = SyncRepository(localDataSource, calDavClient, parser)

    Window(
        onCloseRequest = {
            if (minimizeToTray && trayManagerRef != null) {
                windowRef?.isVisible = false
            } else {
                exitApplication()
            }
        },
        title = "jtxBoard Desktop",
        state = windowState
    ) {
        window.minimumSize = Dimension(800, 600)
        windowRef = window

        if (window is JFrame) {
            val frame = window as JFrame
            frame.menuBar = createAppMenuBar(
                onNewEntry = { },
                onSync = { },
                onImport = { },
                onExport = { },
                onQuit = {
                    trayManagerRef?.dispose()
                    exitApplication()
                },
                onShowJournals = { },
                onShowNotes = { },
                onShowTasks = { },
                onShowKanban = { },
                onShowSettings = { },
                onAbout = { }
            )
        }

        val tm = TrayManager(
            onSyncClick = {
            },
            onOpenClick = {
                windowRef?.isVisible = true
            },
            onQuitClick = {
                trayManagerRef?.dispose()
                exitApplication()
            }
        )
        tm.initialize()
        trayManagerRef = tm

        JtxApp(
            syncRepository = syncRepository,
            trayManager = tm
        )
    }
}