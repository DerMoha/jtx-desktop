package com.jtx.desktop.ui.desktop

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TrayStatus {
    IDLE, SYNCING, SUCCESS, ERROR, OFFLINE, REMINDER
}

class TrayManager(
    private val onSyncClick: () -> Unit,
    private val onOpenClick: () -> Unit,
    private val onQuitClick: () -> Unit
) {
    private var trayIcon: TrayIcon? = null
    private var currentStatus = TrayStatus.IDLE
    private var reminderCount = 0
    private var pendingSyncCount = 0
    private var lastSyncTime: Long? = null
    private var statusItem: MenuItem? = null
    private var pendingItem: MenuItem? = null
    private var lastSyncItem: MenuItem? = null

    fun initialize() {
        if (!SystemTray.isSupported()) {
            println("System tray not supported on this platform")
            return
        }

        val icon = createTrayIcon()
        trayIcon = TrayIcon(icon, "jtxBoard Desktop", createPopupMenu())

        trayIcon?.apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        onOpenClick()
                    }
                }
            })
        }

        SystemTray.getSystemTray().add(trayIcon)
    }

    private fun createTrayIcon(): Image {
        val size = 16
        val bufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = bufferedImage.createGraphics()
        g2d.color = when (currentStatus) {
            TrayStatus.IDLE -> Color(200, 200, 200)
            TrayStatus.SYNCING -> Color(33, 150, 243)
            TrayStatus.SUCCESS -> Color(76, 175, 80)
            TrayStatus.ERROR -> Color(244, 67, 54)
            TrayStatus.OFFLINE -> Color(255, 193, 7)
            TrayStatus.REMINDER -> Color(255, 152, 0)
        }
        g2d.fillOval(2, 2, size - 4, size - 4)
        if (reminderCount > 0) {
            g2d.color = Color(244, 67, 54)
            g2d.fillRect(size - 6, 0, 6, 6)
        }
        g2d.dispose()
        return bufferedImage
    }

    private fun createPopupMenu(): PopupMenu {
        val menu = PopupMenu()

        statusItem = MenuItem(statusText(currentStatus)).apply { isEnabled = false }
        pendingItem = MenuItem(pendingText()).apply { isEnabled = false }
        lastSyncItem = MenuItem(lastSyncText()).apply { isEnabled = false }
        menu.add(statusItem)
        menu.add(pendingItem)
        menu.add(lastSyncItem)

        menu.addSeparator()

        val syncItem = MenuItem("Sync Now")
        syncItem.addActionListener { onSyncClick() }
        menu.add(syncItem)

        menu.addSeparator()

        val openItem = MenuItem("Open jtxBoard")
        openItem.addActionListener { onOpenClick() }
        menu.add(openItem)

        menu.addSeparator()

        val quitItem = MenuItem("Quit")
        quitItem.addActionListener { onQuitClick() }
        menu.add(quitItem)

        return menu
    }

    fun updateStatus(status: TrayStatus) {
        currentStatus = status
        updateIcon()
        updateTrayText()
    }

    fun updateSyncSummary(pendingCount: Int, lastSyncTime: Long?) {
        pendingSyncCount = pendingCount
        this.lastSyncTime = lastSyncTime
        updateTrayText()
    }

    fun updateReminderCount(count: Int) {
        reminderCount = count
        updateIcon()
        updateTrayText()
    }

    private fun updateIcon() {
        trayIcon?.image = createTrayIcon()
    }

    private fun updateTrayText() {
        statusItem?.label = statusText(currentStatus)
        pendingItem?.label = pendingText()
        lastSyncItem?.label = lastSyncText()
        trayIcon?.toolTip = listOf(
            "jtxBoard Desktop",
            statusText(currentStatus),
            pendingText(),
            lastSyncText(),
            if (reminderCount > 0) "$reminderCount reminder(s)" else null
        ).filterNotNull().joinToString(" - ")
    }

    private fun statusText(status: TrayStatus): String = when (status) {
        TrayStatus.IDLE -> "Status: Ready"
        TrayStatus.SYNCING -> "Status: Syncing"
        TrayStatus.SUCCESS -> "Status: Synced"
        TrayStatus.ERROR -> "Status: Sync issue"
        TrayStatus.OFFLINE -> "Status: Offline"
        TrayStatus.REMINDER -> "Status: Reminder"
    }

    private fun pendingText(): String = "Pending: $pendingSyncCount"

    private fun lastSyncText(): String {
        val timestamp = lastSyncTime ?: return "Last sync: Never"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "Last sync: ${formatter.format(Date(timestamp))}"
    }

    fun showNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }

    fun dispose() {
        trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        trayIcon = null
    }
}
