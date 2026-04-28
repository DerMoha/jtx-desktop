package com.jtx.desktop.ui.desktop

import com.jtx.desktop.domain.model.TaskEntry
import com.jtx.desktop.domain.model.Reminder
import kotlinx.coroutines.*
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.Icon

object ReminderScheduler {
    private var schedulerJob: Job? = null
    private val checkedTasks = mutableSetOf<String>()

    fun scheduleReminders(scope: CoroutineScope, tasks: List<TaskEntry>, onReminder: (TaskEntry, Reminder) -> Unit) {
        schedulerJob?.cancel()
        val activeKeys = tasks.flatMap { task ->
            task.reminders.mapNotNull { reminder -> task.due?.let { "${task.id}_${it}_${reminder.minutesBefore}" } }
        }.toSet()
        checkedTasks.retainAll(activeKeys)
        schedulerJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                for (task in tasks) {
                    if (task.completed || task.due == null) continue
                    for (reminder in task.reminders) {
                        val key = "${task.id}_${task.due}_${reminder.minutesBefore}"
                        if (checkedTasks.contains(key)) continue

                        val dueTime = task.due
                        val reminderTime = dueTime - (reminder.minutesBefore * 60 * 1000L)

                        if (now >= reminderTime && now <= dueTime) {
                            onReminder(task, reminder)
                            checkedTasks.add(key)
                        }
                    }
                }
                delay(30000)
            }
        }
    }

    fun cancelReminders() {
        schedulerJob?.cancel()
        schedulerJob = null
        checkedTasks.clear()
    }

    fun showDesktopNotification(title: String, message: String, trayManager: TrayManager?) {
        trayManager?.showNotification(title, message)
    }
}
