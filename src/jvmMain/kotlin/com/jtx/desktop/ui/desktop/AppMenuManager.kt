package com.jtx.desktop.ui.desktop

import java.awt.MenuBar
import java.awt.Menu
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.event.KeyEvent
import javax.swing.JFrame

class AppMenuManager(
    private val onNewEntry: () -> Unit,
    private val onSync: () -> Unit,
    private val onImport: () -> Unit,
    private val onExport: () -> Unit,
    private val onQuit: () -> Unit,
    private val onShowJournals: () -> Unit,
    private val onShowNotes: () -> Unit,
    private val onShowTasks: () -> Unit,
    private val onShowKanban: () -> Unit,
    private val onShowSettings: () -> Unit,
    private val onAbout: () -> Unit
) {
    private var menuBar: MenuBar? = null

    fun createMenuBar(): MenuBar {
        menuBar = MenuBar()

        val fileMenu = Menu("File")
        fileMenu.add(MenuItem("New Entry", MenuShortcut(KeyEvent.VK_N))).apply {
            addActionListener { onNewEntry() }
        }
        fileMenu.addSeparator()
        fileMenu.add(MenuItem("Import...")).apply {
            addActionListener { onImport() }
        }
        fileMenu.add(MenuItem("Export...")).apply {
            addActionListener { onExport() }
        }
        fileMenu.addSeparator()
        fileMenu.add(MenuItem("Quit", MenuShortcut(KeyEvent.VK_Q))).apply {
            addActionListener { onQuit() }
        }

        val editMenu = Menu("Edit")
        editMenu.add(MenuItem("Undo", MenuShortcut(KeyEvent.VK_Z)))
        editMenu.add(MenuItem("Redo", MenuShortcut(KeyEvent.VK_Y)))

        val viewMenu = Menu("View")
        viewMenu.add(MenuItem("Journals")).apply {
            addActionListener { onShowJournals() }
        }
        viewMenu.add(MenuItem("Notes")).apply {
            addActionListener { onShowNotes() }
        }
        viewMenu.add(MenuItem("Tasks")).apply {
            addActionListener { onShowTasks() }
        }
        viewMenu.add(MenuItem("Kanban Board")).apply {
            addActionListener { onShowKanban() }
        }
        viewMenu.addSeparator()
        viewMenu.add(MenuItem("Settings...")).apply {
            addActionListener { onShowSettings() }
        }

        val helpMenu = Menu("Help")
        helpMenu.add(MenuItem("About jtxBoard")).apply {
            addActionListener { onAbout() }
        }

        menuBar?.add(fileMenu)
        menuBar?.add(editMenu)
        menuBar?.add(viewMenu)
        menuBar?.add(helpMenu)

        return menuBar!!
    }

    fun attachToFrame(frame: JFrame) {
        frame.menuBar = createMenuBar()
    }
}