package com.jtx.desktop.ui.desktop

import kotlinx.coroutines.*

object SyncScheduler {
    private var syncJob: Job? = null
    private var pendingSync = false
    private var lastTrigger = 0L

    fun triggerSync(scope: CoroutineScope, delayMs: Long = 5000, onSync: suspend () -> Unit) {
        pendingSync = true
        lastTrigger = System.currentTimeMillis()

        syncJob?.cancel()
        syncJob = scope.launch {
            delay(delayMs)
            if (pendingSync && System.currentTimeMillis() - lastTrigger >= delayMs) {
                pendingSync = false
                onSync()
            }
        }
    }

    fun schedulePeriodicSync(
        scope: CoroutineScope,
        intervalMinutes: Int,
        onSync: suspend () -> Unit
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000L)
                onSync()
            }
        }
    }

    fun cancelPendingSync() {
        syncJob?.cancel()
        pendingSync = false
    }
}

object NetworkMonitor {
    fun isNetworkAvailable(): Boolean {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && !networkInterface.isVirtual && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}