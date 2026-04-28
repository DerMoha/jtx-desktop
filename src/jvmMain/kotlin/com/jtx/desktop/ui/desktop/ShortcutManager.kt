package com.jtx.desktop.ui.desktop

import androidx.compose.ui.input.key.KeyEvent
import java.awt.MenuBar
import java.awt.Menu
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.event.KeyEvent as AWTKeyEvent
import java.awt.event.ActionListener

data class KeyboardShortcut(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val action: () -> Unit
)

class ShortcutManager(
    private val onNewEntry: () -> Unit,
    private val onSync: () -> Unit,
    private val onSearch: () -> Unit,
    private val onSettings: () -> Unit,
    private val onEscape: () -> Unit
) {
    private val shortcuts = listOf(
        KeyboardShortcut(AWTKeyEvent.VK_N, ctrl = true) { onNewEntry() },
        KeyboardShortcut(AWTKeyEvent.VK_S, ctrl = true) { onSync() },
        KeyboardShortcut(AWTKeyEvent.VK_F, ctrl = true) { onSearch() },
        KeyboardShortcut(AWTKeyEvent.VK_COMMA, ctrl = true) { onSettings() },
        KeyboardShortcut(AWTKeyEvent.VK_ESCAPE) { onEscape() }
    )

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val awtEvent = event.nativeKeyEvent
        if (awtEvent !is AWTKeyEvent) return false
        val keyCode = awtEvent.keyCode
        val modifiers = awtEvent.modifiers
        val isCtrl = (modifiers and AWTKeyEvent.CTRL_MASK) != 0
        val isShift = (modifiers and AWTKeyEvent.SHIFT_MASK) != 0
        val isAlt = (modifiers and AWTKeyEvent.ALT_MASK) != 0

        val shortcut = shortcuts.find { s ->
            s.keyCode == keyCode &&
            s.ctrl == isCtrl &&
            s.shift == isShift &&
            s.alt == isAlt
        }
        if (shortcut != null) {
            shortcut.action()
            return true
        }
        return false
    }
}

fun createAppMenuBar(
    onNewEntry: () -> Unit,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onQuit: () -> Unit,
    onShowJournals: () -> Unit,
    onShowNotes: () -> Unit,
    onShowTasks: () -> Unit,
    onShowKanban: () -> Unit,
    onShowSettings: () -> Unit,
    onAbout: () -> Unit
): MenuBar {
    val menuBar = MenuBar()

    val fileMenu = Menu("File")
    val newEntryItem = MenuItem("New Entry", MenuShortcut(AWTKeyEvent.VK_N))
    newEntryItem.addActionListener { onNewEntry() }
    fileMenu.add(newEntryItem)
    fileMenu.addSeparator()
    val importItem = MenuItem("Import...")
    importItem.addActionListener { onImport() }
    fileMenu.add(importItem)
    val exportItem = MenuItem("Export...")
    exportItem.addActionListener { onExport() }
    fileMenu.add(exportItem)
    fileMenu.addSeparator()
    val quitItem = MenuItem("Quit", MenuShortcut(AWTKeyEvent.VK_Q))
    quitItem.addActionListener { onQuit() }
    fileMenu.add(quitItem)

    val editMenu = Menu("Edit")
    editMenu.add(MenuItem("Undo", MenuShortcut(AWTKeyEvent.VK_Z)))
    editMenu.add(MenuItem("Redo", MenuShortcut(AWTKeyEvent.VK_Y)))

    val viewMenu = Menu("View")
    val journalsItem = MenuItem("Journals")
    journalsItem.addActionListener { onShowJournals() }
    viewMenu.add(journalsItem)
    val notesItem = MenuItem("Notes")
    notesItem.addActionListener { onShowNotes() }
    viewMenu.add(notesItem)
    val tasksItem = MenuItem("Tasks")
    tasksItem.addActionListener { onShowTasks() }
    viewMenu.add(tasksItem)
    val kanbanItem = MenuItem("Kanban Board")
    kanbanItem.addActionListener { onShowKanban() }
    viewMenu.add(kanbanItem)
    viewMenu.addSeparator()
    val settingsItem = MenuItem("Settings...")
    settingsItem.addActionListener { onShowSettings() }
    viewMenu.add(settingsItem)

    val helpMenu = Menu("Help")
    val aboutItem = MenuItem("About jtxBoard")
    aboutItem.addActionListener { onAbout() }
    helpMenu.add(aboutItem)

    menuBar.add(fileMenu)
    menuBar.add(editMenu)
    menuBar.add(viewMenu)
    menuBar.add(helpMenu)

    return menuBar
}