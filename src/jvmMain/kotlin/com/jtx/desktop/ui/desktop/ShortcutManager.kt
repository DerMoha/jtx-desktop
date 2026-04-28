package com.jtx.desktop.ui.desktop

import androidx.compose.ui.input.key.KeyEvent
import java.awt.event.KeyEvent as AWTKeyEvent

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