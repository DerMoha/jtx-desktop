package com.jtx.desktop.performance

import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.domain.model.CombinedEntry
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.domain.model.RecurrenceFrequency
import com.jtx.desktop.domain.model.RecurrenceRule
import com.jtx.desktop.domain.model.TaskEntry
import com.jtx.desktop.ui.matchesGlobalSearch
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceSmokeTest {
    @Test
    fun globalSearchHandlesLargeEntryLists() {
        val entries = (1..2_000).map { index ->
            CombinedEntry(
                id = "entry-$index",
                type = when (index % 3) {
                    0 -> EntryType.JOURNAL
                    1 -> EntryType.NOTE
                    else -> EntryType.TASK
                },
                title = "Entry $index",
                description = "Large collection item $index",
                date = index.toLong(),
                categories = listOf("bulk", "tag$index"),
                color = null,
                progress = null,
                completed = null,
                location = if (index % 10 == 0) "Vienna" else null
            )
        }

        var results = emptyList<CombinedEntry>()
        val elapsed = measureTimeMillis {
            results = entries.filter { it.matchesGlobalSearch("bulk tag1500") }
        }

        assertEquals(listOf("entry-1500"), results.map { it.id })
        assertTrue(elapsed < 5_000, "Global search smoke test took ${elapsed}ms")
    }

    @Test
    fun parserRoundTripsRecurringTaskCollections() {
        val parser = ICalendarParser()
        val tasks = (1..300).map { index ->
            recurringTask(index)
        }

        var parsedCount = 0
        val elapsed = measureTimeMillis {
            parsedCount = tasks.count { task ->
                val ics = parser.entryToIcs(task)
                val parsed = parser.parseEntry(ics) as? TaskEntry
                parsed?.id == task.id && parsed.recurrenceRule?.rawRule == task.recurrenceRule?.rawRule
            }
        }

        assertEquals(tasks.size, parsedCount)
        assertTrue(elapsed < 10_000, "Recurring task round trip smoke test took ${elapsed}ms")
    }

    private fun recurringTask(index: Int): TaskEntry = TaskEntry(
        id = "task-$index",
        uid = "task-$index",
        title = "Recurring task $index",
        description = "Performance smoke task $index",
        due = 1_800_000_000_000L + index,
        start = null,
        completed = false,
        progress = index % 100,
        categories = listOf("bulk", "recurring"),
        created = 1_700_000_000_000L,
        updated = 1_700_000_000_000L + index,
        color = null,
        location = null,
        subtasks = emptyList(),
        relatedEntries = emptyList(),
        recurrenceRule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            count = 8,
            rawRule = "FREQ=WEEKLY;BYDAY=MO,WE;COUNT=8"
        ),
        recurrenceDates = listOf(1_800_086_400_000L + index),
        exceptionDates = listOf(1_800_172_800_000L + index)
    )
}
