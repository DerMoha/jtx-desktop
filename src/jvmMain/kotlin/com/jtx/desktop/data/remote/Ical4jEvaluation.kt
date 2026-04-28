package com.jtx.desktop.data.remote

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Calendar
import java.io.StringReader

/**
 * Small compile-time probe for the mature iCalendar parser selected for the parser rewrite.
 * The handwritten parser remains the app entry point until conversion/round-trip behavior is mapped.
 */
class Ical4jEvaluation {
    fun parseCalendar(ics: String): Calendar = CalendarBuilder().build(StringReader(ics))

    fun canParse(ics: String): Boolean = runCatching { parseCalendar(ics) }.isSuccess
}
