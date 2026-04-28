package com.jtx.desktop.ui.screens.tasks

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskEditorInputTest {
    @Test
    fun dateTimeInputAcceptsIsoInstantsForDueDates() {
        assertEquals(
            Instant.parse("2026-04-28T12:30:00Z").toEpochMilli(),
            "2026-04-28T12:30:00Z".parseDateTimeInput()
        )
    }
}
