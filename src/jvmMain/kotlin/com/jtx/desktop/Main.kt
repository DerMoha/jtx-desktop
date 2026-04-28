package com.jtx.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.jtx.desktop.data.local.SqliteLocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.data.repository.SyncRepository
import com.jtx.desktop.ui.AppMenuAction
import com.jtx.desktop.ui.JtxApp
import com.jtx.desktop.ui.desktop.TrayManager
import com.jtx.desktop.ui.desktop.createAppMenuBar
import com.jtx.desktop.domain.model.AppSettings
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import javax.swing.JFrame

var windowRef: java.awt.Window? = null
var trayManagerRef: TrayManager? = null
var minimizeToTray = true

fun main() = application {
    val localDataSource = SqliteLocalDataSource("jtx_board.db")
    val calDavClient = CalDavClient()
    val parser = ICalendarParser()
    val syncRepository = SyncRepository(localDataSource, calDavClient, parser)

    val settings: AppSettings = runBlocking { syncRepository.getSettings() }

    val windowWidth = settings.windowWidth?.dp ?: 1200.dp
    val windowHeight = settings.windowHeight?.dp ?: 800.dp

    val windowState = WindowState(
        size = androidx.compose.ui.unit.DpSize(windowWidth, windowHeight)
    )
    var menuAction by remember { mutableStateOf<AppMenuAction?>(null) }

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
                onNewEntry = { menuAction = AppMenuAction.NEW_ENTRY },
                onSync = { menuAction = AppMenuAction.SYNC },
                onImport = { menuAction = AppMenuAction.IMPORT },
                onExport = { menuAction = AppMenuAction.EXPORT },
                onQuit = {
                    runBlocking {
                        val ws = windowRef?.bounds
                        val newSettings = settings.copy(
                            windowX = ws?.x,
                            windowY = ws?.y,
                            windowWidth = ws?.width,
                            windowHeight = ws?.height
                        )
                        syncRepository.saveSettings(newSettings)
                    }
                    trayManagerRef?.dispose()
                    exitApplication()
                },
                onUndo = { menuAction = AppMenuAction.UNDO },
                onRedo = { menuAction = AppMenuAction.REDO },
                onShowJournals = { menuAction = AppMenuAction.SHOW_JOURNALS },
                onShowNotes = { menuAction = AppMenuAction.SHOW_NOTES },
                onShowTasks = { menuAction = AppMenuAction.SHOW_TASKS },
                onShowKanban = { menuAction = AppMenuAction.SHOW_KANBAN },
                onShowSettings = { menuAction = AppMenuAction.SHOW_SETTINGS },
                onAbout = { menuAction = AppMenuAction.ABOUT }
            )
        }

        val tm = TrayManager(
            onSyncClick = {
                menuAction = AppMenuAction.SYNC
            },
            onOpenClick = {
                windowRef?.isVisible = true
                windowRef?.toFront()
                windowRef?.requestFocus()
            },
            onQuitClick = {
                runBlocking {
                    val ws = windowRef?.bounds
                    val newSettings = settings.copy(
                        windowX = ws?.x,
                        windowY = ws?.y,
                        windowWidth = ws?.width,
                        windowHeight = ws?.height
                    )
                    syncRepository.saveSettings(newSettings)
                }
                trayManagerRef?.dispose()
                exitApplication()
            }
        )
        tm.initialize()
        trayManagerRef = tm

        JtxApp(
            syncRepository = syncRepository,
            trayManager = tm,
            menuAction = menuAction,
            onMenuActionHandled = { menuAction = null }
        )
    }
}
