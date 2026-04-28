package com.jtx.desktop.ui.screens.journals

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JournalEditorInputTest {
    @Test
    fun dateTimeInputAcceptsBlankMillisDatesAndLocalDateTimes() {
        val zone = ZoneId.systemDefault()

        assertNull(" ".parseDateTimeInput())
        assertEquals(1234L, "1234".parseDateTimeInput())
        assertEquals(
            LocalDate.parse("2026-04-28").atStartOfDay(zone).toInstant().toEpochMilli(),
            "2026-04-28".parseDateTimeInput()
        )
        assertEquals(
            LocalDateTime.parse("2026-04-28T13:45").atZone(zone).toInstant().toEpochMilli(),
            "2026-04-28 13:45".parseDateTimeInput()
        )
    }
}
