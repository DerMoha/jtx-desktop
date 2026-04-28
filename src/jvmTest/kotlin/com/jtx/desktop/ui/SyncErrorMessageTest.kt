package com.jtx.desktop.ui

import com.jtx.desktop.data.remote.CalDavHttpException
import com.jtx.desktop.data.repository.SyncRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncErrorMessageTest {
    @Test
    fun syncResultMessageHighlightsConflictsReadOnlyAndFailures() {
        assertEquals(
            "2 sync conflicts need resolution",
            SyncRepository.SyncResult(0, 0, emptyList(), conflicts = listOf(conflict(), conflict())).toUserFacingSyncMessage()
        )
        assertEquals(
            "Collection is read-only; local changes remain queued",
            SyncRepository.SyncResult(0, 0, listOf("Collection is read-only; local changes remain queued")).toUserFacingSyncMessage()
        )
        assertEquals(
            "Sync finished with 1 failure: authentication rejected; check username, password, or app password",
            SyncRepository.SyncResult(0, 1, listOf("HTTP 401")).toUserFacingSyncMessage()
        )
    }

    @Test
    fun throwableMessageExplainsHttpAndNetworkFailures() {
        assertEquals(
            "Sync failed: server error HTTP 503; retry later",
            CalDavHttpException(503, "HTTP 503").toUserFacingSyncMessage()
        )
        assertTrue(
            IllegalStateException("failed to connect to server").toUserFacingSyncMessage()
                .contains("server is unreachable")
        )
    }

    private fun conflict(): SyncRepository.SyncConflictInfo = SyncRepository.SyncConflictInfo(
        localEntry = Any(),
        serverEntry = Any(),
        serverHref = "/cal/item.ics",
        localUpdated = 2_000L,
        serverUpdated = 1_000L
    )
}
