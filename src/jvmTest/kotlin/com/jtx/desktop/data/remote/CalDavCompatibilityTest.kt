package com.jtx.desktop.data.remote

import com.jtx.desktop.domain.model.JournalEntry
import com.jtx.desktop.domain.model.NoteEntry
import com.jtx.desktop.domain.model.TaskEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CalDavCompatibilityTest {
    private val parser = ICalendarParser()

    @Test
    fun parsesNextcloudStyleVtodoWithNamespaceFriendlyContent() {
        val entry = parser.parseEntry(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Nextcloud Tasks v0.16.1
            BEGIN:VTODO
            UID:nextcloud-task-1
            DTSTAMP:20260428T120000Z
            LAST-MODIFIED:20260428T121500Z
            SUMMARY;LANGUAGE=en:Buy milk\, bread
            DESCRIPTION:Line one\nLine two
            STATUS:NEEDS-ACTION
            PERCENT-COMPLETE:25
            PRIORITY:5
            DUE;TZID=Europe/Berlin:20260429T090000
            CATEGORIES:home\, errands,shopping
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        )

        val task = assertIs<TaskEntry>(entry)
        assertEquals("nextcloud-task-1", task.uid)
        assertEquals("Buy milk, bread", task.title)
        assertEquals("Line one\nLine two", task.description)
        assertEquals(25, task.progress)
        assertEquals("Europe/Berlin", task.dueTimezone)
        assertEquals(listOf("home, errands", "shopping"), task.categories)
        assertEquals(15, task.reminders.single().minutesBefore)
    }

    @Test
    fun parsesSimpleServerVjournalNoteWithoutDtstart() {
        val entry = parser.parseEntry(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Simple CalDAV Server
            BEGIN:VJOURNAL
            UID:simple-note-1
            DTSTAMP:20260428T120000Z
            SUMMARY:Plain note
            DESCRIPTION:No start date means note
            END:VJOURNAL
            END:VCALENDAR
            """.trimIndent()
        )

        val note = assertIs<NoteEntry>(entry)
        assertEquals("simple-note-1", note.uid)
        assertEquals("Plain note", note.title)
        assertEquals("No start date means note", note.description)
    }

    @Test
    fun ical4jAcceptsFoldedCalDavObjects() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Compatibility Fixture
            BEGIN:VTODO
            UID:folded-1
            DTSTAMP:20260428T120000Z
            SUMMARY:This is a long summary that is folded by a CalDAV server and must still
             parse successfully
            END:VTODO
            END:VCALENDAR
            """.trimIndent()

        assertEquals("This is a long summary that is folded by a CalDAV server and must stillparse successfully", assertIs<TaskEntry>(parser.parseEntry(ics)).title)
    }

    @Test
    fun parsesAndroidStyleVjournalWithMetadata() {
        val entry = parser.parseEntry(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//jtx Board Android
            BEGIN:VJOURNAL
            UID:android-journal-1
            DTSTAMP:20260428T120000Z
            LAST-MODIFIED:20260428T130000Z
            SEQUENCE:3
            DTSTART;TZID=Europe/Vienna:20260428T083000
            SUMMARY:Morning review
            DESCRIPTION:First line\nSecond line
            CATEGORIES:work,planning
            RELATED-TO:task-123
            ATTACH;VALUE=URI;FMTTYPE=image/png;FILENAME=photo.png;SIZE=1234:file:///tmp/photo.png
            COMMENT:Reviewed later
            X-JTX-ANDROID-FLAG:kept
            END:VJOURNAL
            END:VCALENDAR
            """.trimIndent()
        )

        val journal = assertIs<JournalEntry>(entry)
        assertEquals("android-journal-1", journal.uid)
        assertEquals("Europe/Vienna", journal.startTimezone)
        assertEquals(3, journal.sequence)
        assertEquals(listOf("work", "planning"), journal.categories)
        assertEquals(listOf("task-123"), journal.relatedEntries)
        assertEquals("photo.png", journal.attachments.single().filename)
        assertEquals("image/png", journal.attachments.single().mimeType)
        assertEquals(1234, journal.attachments.single().size)
        assertEquals("Reviewed later", journal.comments.single().text)
        assertEquals("X-JTX-ANDROID-FLAG:kept", journal.unknownProperties.single().line)
    }

    @Test
    fun preservesRecurrenceRulesAndUnsupportedAlarms() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:recurring-task-1
            SUMMARY:Water plants
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=8
            RDATE;TZID=Europe/Berlin:20260501T090000,20260503T090000
            EXDATE;TZID=Europe/Berlin:20260508T090000
            RECURRENCE-ID;TZID=Europe/Berlin:20260501T090000
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER;VALUE=DATE-TIME:20260430T080000Z
            END:VALARM
            END:VTODO
            END:VCALENDAR
            """.trimIndent()

        val task = assertIs<TaskEntry>(parser.parseEntry(ics))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=8", task.recurrenceRule?.rawRule)
        assertEquals("Europe/Berlin", task.recurrenceTimezone)
        assertEquals("Europe/Berlin", task.recurrenceIdTimezone)
        assertTrue(task.recurrenceDates.isNotEmpty())
        assertTrue(task.exceptionDates.isNotEmpty())
        assertTrue(task.unknownProperties.any { it.line.startsWith("TRIGGER;VALUE=DATE-TIME") })

        val serialized = parser.entryToIcs(task)
        assertTrue("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=8" in serialized)
        assertTrue("RECURRENCE-ID;TZID=Europe/Berlin" in serialized)
        assertTrue("TRIGGER;VALUE=DATE-TIME:20260430T080000Z" in serialized)
    }

    @Test
    fun escapesAndFoldsSerializedTextWithoutDroppingAttachments() {
        val original = assertIs<TaskEntry>(parser.parseEntry(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:escape-task-1
            SUMMARY:Title with comma\, semicolon\; and slash\\
            DESCRIPTION:Line one\nLine two with comma\, and semicolon\;
            CATEGORIES:alpha\,beta,gamma
            ATTACH;VALUE=URI;FMTTYPE=text/plain;FILENAME=notes.txt;SIZE=42:https://example.test/notes.txt
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        ))

        val serialized = parser.entryToIcs(original.copy(title = original.title + " ".repeat(80) + "tail"))
        assertTrue("\r\n " in serialized, "Long iCalendar lines should be folded")
        assertTrue("SUMMARY:Title with comma\\, semicolon\\;" in serialized)
        assertTrue("ATTACH;VALUE=URI" in serialized)
        assertTrue("FILENAME=notes.txt" in serialized)

        val reparsed = assertIs<TaskEntry>(parser.parseEntry(serialized))
        assertTrue(reparsed.title.startsWith("Title with comma, semicolon; and slash\\"))
        assertEquals("Line one\nLine two with comma, and semicolon;", reparsed.description)
        assertEquals(listOf("alpha,beta", "gamma"), reparsed.categories)
        assertEquals("notes.txt", reparsed.attachments.single().filename)
    }
}
