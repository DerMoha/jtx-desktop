package com.jtx.desktop.data.remote

import com.jtx.desktop.domain.model.NoteEntry
import com.jtx.desktop.domain.model.TaskEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
