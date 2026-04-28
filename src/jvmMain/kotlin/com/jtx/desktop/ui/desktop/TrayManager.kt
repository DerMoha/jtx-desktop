package com.jtx.desktop.ui.desktop

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import java.awt.image.BufferedImage

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
        updateTooltip(status)
    }

    fun updateReminderCount(count: Int) {
        reminderCount = count
        updateIcon()
    }

    private fun updateIcon() {
        trayIcon?.image = createTrayIcon()
    }

    private fun updateTooltip(status: TrayStatus) {
        val tooltip = when (status) {
            TrayStatus.IDLE -> "jtxBoard Desktop - Ready"
            TrayStatus.SYNCING -> "jtxBoard Desktop - Syncing..."
            TrayStatus.SUCCESS -> "jtxBoard Desktop - Sync complete"
            TrayStatus.ERROR -> "jtxBoard Desktop - Sync error"
            TrayStatus.OFFLINE -> "jtxBoard Desktop - Offline"
            TrayStatus.REMINDER -> "jtxBoard Desktop - $reminderCount reminder(s)"
        }
        trayIcon?.toolTip = tooltip
    }

    fun showNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }

    fun dispose() {
        trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        trayIcon = null
    }
}