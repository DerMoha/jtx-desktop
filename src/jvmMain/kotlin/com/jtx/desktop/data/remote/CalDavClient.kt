package com.jtx.desktop.data.remote

import com.jtx.desktop.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

class CalDavClient {
    private val connectTimeout = 30000
    private val readTimeout = 60000
    private val maxRetries = 3
    private val baseDelayMs = 1000L

    suspend fun fetchEntries(credentials: CalDavCredentials, collection: String): Result<List<String>> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$collection").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.requestMethod = "REPORT"
            conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            conn.setRequestProperty("Depth", "1")
            setAuth(conn, credentials)

            val body = """<?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                    <d:prop>
                        <d:href/>
                    </d:prop>
                </d:propfind>"""

            conn.doOutput = true
            conn.outputStream.write(body.toByteArray())

            val responseCode = conn.responseCode
            val response = if (responseCode == 207) {
                conn.inputStream.bufferedReader().readText()
            } else {
                throw Exception("HTTP $responseCode: ${conn.responseMessage}")
            }

            Result.success(parseHrefs(response))
        }
    }

    suspend fun fetchCalendarData(credentials: CalDavCredentials, href: String): Result<String> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.requestMethod = "GET"
            setAuth(conn, credentials)

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val data = conn.inputStream.bufferedReader().readText()
                Result.success(data)
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    suspend fun putEntry(credentials: CalDavCredentials, href: String, icsContent: String): Result<String> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "text/calendar; charset=utf-8")
            conn.setRequestProperty("If-Match", "*")
            setAuth(conn, credentials)
            conn.doOutput = true
            conn.outputStream.write(icsContent.toByteArray())

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(conn.responseMessage ?: "OK")
            } else {
                Result.failure(Exception("HTTP $responseCode: ${conn.responseMessage}"))
            }
        }
    }

    suspend fun deleteEntry(credentials: CalDavCredentials, href: String): Result<Unit> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.requestMethod = "DELETE"
            setAuth(conn, credentials)

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    private suspend fun <T> retryWithBackoff(maxRetries: Int, operation: suspend () -> Result<T>): Result<T> {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val result = operation()
                if (result.isSuccess) return result
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: Exception) {
                lastException = e
            }
            if (attempt < maxRetries) {
                val delay = baseDelayMs * (1 shl (attempt - 1))
                kotlinx.coroutines.delay(delay)
            }
        }
        return Result.failure(lastException ?: Exception("All retries failed"))
    }

    private fun setAuth(conn: HttpsURLConnection, credentials: CalDavCredentials) {
        val auth = "${credentials.username}:${credentials.password}"
        val encoded = Base64.getEncoder().encodeToString(auth.toByteArray())
        conn.setRequestProperty("Authorization", "Basic $encoded")
    }

    private fun parseHrefs(xml: String): List<String> {
        val hrefs = mutableListOf<String>()
        val regex = Regex("<d:href>([^<]+)</d:href>")
        regex.findAll(xml).forEach { hrefs.add(it.groupValues[1]) }
        return hrefs
    }
}

class ICalendarParser {

    private val ical4j = Ical4jEvaluation()

    private val commonKnownProperties = listOf(
        "BEGIN", "END", "VERSION", "PRODID", "UID", "DTSTAMP", "LAST-MODIFIED", "SUMMARY", "DESCRIPTION",
        "CALSCALE", "CATEGORIES", "CREATED", "COLOR", "X-APPLE-STRUCTURED-LOCATION", "RELATED-TO", "ATTACH"
    )

    private fun unfoldLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line.substring(1))
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                }
                current = StringBuilder(line)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    fun parseEntry(ics: String): Any? {
        val componentNames = parseComponentNames(ics)
        return when {
            "VTODO" in componentNames -> parseVTodo(ics)
            "VNOTE" in componentNames -> parseVNote(ics)
            "VJOURNAL" in componentNames -> parseVJournal(ics)
            else -> null
        }
    }

    private fun parseComponentNames(ics: String): Set<String> {
        return runCatching {
            ical4j.parseCalendar(ics).componentList.all.map { it.name.uppercase() }.toSet()
        }.getOrElse {
            unfoldLines(ics.lines())
                .filter { it.startsWith("BEGIN:") }
                .map { it.substringAfter("BEGIN:").trim().uppercase() }
                .toSet()
        }
    }

    fun parseVJournal(ics: String): JournalEntry? {
        return try {
            val lines = unfoldLines(ics.lines())
            var uid = ""
            var summary = ""
            var description = ""
            var dtstart: Long? = null
            var dtend: Long? = null
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var color: String? = null
            var location: String? = null
            var comment: String? = null
            var relatedEntries = emptyList<String>()
            var attachments = emptyList<EntryAttachment>()
            var comments = emptyList<EntryComment>()
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf("DTSTART", "DTEND", "COMMENT")

            for (line in lines) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.substringAfter(":").trim()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("DTSTART") -> dtstart = parseIcsDate(line)
                    line.isProperty("DTEND") -> dtend = parseIcsDate(line)
                    line.isProperty("CATEGORIES") -> categories = line.propertyValue().split(",").map { it.trim() }.toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("COMMENT") -> {
                        val text = line.propertyValue()
                        comment = comment ?: text
                        comments = comments + EntryComment(text)
                    }
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COLOR") -> color = line.propertyValue()
                }
            }

            if (uid.isEmpty()) return null

            JournalEntry(
                id = uid, uid = uid, title = summary, description = description,
                dtstart = dtstart, dtend = dtend, categories = categories,
                created = created ?: System.currentTimeMillis(),
                updated = dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, comment = comment,
                relatedEntries = relatedEntries, attachments = attachments,
                comments = comments, unknownProperties = unknownProperties
            )
        } catch (e: Exception) { null }
    }

    fun parseVTodo(ics: String): TaskEntry? {
        return try {
            val lines = unfoldLines(ics.lines())
            var uid = ""
            var summary = ""
            var description = ""
            var due: Long? = null
            var start: Long? = null
            var completed = false
            var progress = 0
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var color: String? = null
            var location: String? = null
            var priority = Priority.NONE
            var relatedEntries = emptyList<String>()
            var attachments = emptyList<EntryAttachment>()
            var comments = emptyList<EntryComment>()
            var recurrenceRule: RecurrenceRule? = null
            var recurrenceDates = emptyList<Long>()
            var exceptionDates = emptyList<Long>()
            var recurrenceId: Long? = null
            var recurrenceTimezone: String? = null
            var recurrenceIdTimezone: String? = null
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf(
                "DUE", "DTSTART", "STATUS", "PERCENT-COMPLETE", "PRIORITY", "RRULE", "RDATE", "EXDATE",
                "RECURRENCE-ID", "LOCATION", "COMMENT"
            )

            for (line in lines) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.substringAfter(":").trim()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("DUE") -> due = parseIcsDate(line)
                    line.isProperty("DTSTART") -> start = parseIcsDate(line)
                    line.isProperty("STATUS") && line.propertyValue().equals("COMPLETED", ignoreCase = true) -> completed = true
                    line.isProperty("PERCENT-COMPLETE") -> progress = line.propertyValue().toIntOrNull() ?: 0
                    line.isProperty("PRIORITY") -> priority = parsePriority(line.propertyValue())
                    line.isProperty("RRULE") -> recurrenceRule = parseRecurrenceRule(line.propertyValue())
                    line.isProperty("RDATE") -> {
                        recurrenceDates = recurrenceDates + parseDateList(line)
                        recurrenceTimezone = recurrenceTimezone ?: parseTimezone(line)
                    }
                    line.isProperty("EXDATE") -> {
                        exceptionDates = exceptionDates + parseDateList(line)
                        recurrenceTimezone = recurrenceTimezone ?: parseTimezone(line)
                    }
                    line.isProperty("RECURRENCE-ID") -> {
                        recurrenceId = parseIcsDate(line)
                        recurrenceIdTimezone = parseTimezone(line)
                    }
                    line.isProperty("CATEGORIES") -> categories = line.propertyValue().split(",").map { it.trim() }.toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("LOCATION") -> location = line.propertyValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COMMENT") -> comments = comments + EntryComment(line.propertyValue())
                }
            }

            if (uid.isEmpty()) return null

            TaskEntry(
                id = uid, uid = uid, title = summary, description = description,
                due = due, start = start, completed = completed, progress = progress,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, subtasks = emptyList(), relatedEntries = relatedEntries,
                priority = priority, recurrenceRule = recurrenceRule, recurrenceDates = recurrenceDates,
                exceptionDates = exceptionDates, recurrenceId = recurrenceId,
                recurrenceTimezone = recurrenceTimezone, recurrenceIdTimezone = recurrenceIdTimezone,
                attachments = attachments, comments = comments,
                unknownProperties = unknownProperties
            )
        } catch (e: Exception) { null }
    }

    fun parseVNote(ics: String): NoteEntry? {
        return try {
            val lines = unfoldLines(ics.lines())
            var uid = ""
            var summary = ""
            var description = ""
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var color: String? = null
            var location: String? = null
            var relatedEntries = emptyList<String>()
            var attachments = emptyList<EntryAttachment>()
            var comments = emptyList<EntryComment>()
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf("COMMENT")

            for (line in lines) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.substringAfter(":").trim()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("CATEGORIES") -> categories = line.propertyValue().split(",").map { it.trim() }.toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("COLOR") -> color = line.propertyValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COMMENT") -> comments = comments + EntryComment(line.propertyValue())
                }
            }

            if (uid.isEmpty()) return null

            NoteEntry(
                id = uid, uid = uid, title = summary, description = description,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, relatedEntries = relatedEntries,
                attachments = attachments, comments = comments,
                unknownProperties = unknownProperties
            )
        } catch (e: Exception) { null }
    }

    fun entryToIcs(entry: Any): String = when (entry) {
        is JournalEntry -> journalToIcs(entry)
        is NoteEntry -> noteToIcs(entry)
        is TaskEntry -> taskToIcs(entry)
        else -> throw IllegalArgumentException("Unsupported iCalendar entry type: ${entry::class.simpleName}")
    }

    fun journalToIcs(entry: JournalEntry): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("PRODID:-//jtxBoard Desktop//EN")
            appendLine("BEGIN:VJOURNAL")
            appendLine("UID:${entry.uid}")
            appendLine("DTSTAMP:${formatIcsDate(entry.updated)}")
            if (entry.dtstart != null) appendLine("DTSTART:${formatIcsDate(entry.dtstart)}")
            if (entry.dtend != null) appendLine("DTEND:${formatIcsDate(entry.dtend)}")
            appendLine("SUMMARY:${entry.title}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",")}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            if (entry.location != null) appendLine("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:${entry.location}")
            entry.comments.ifEmpty { entry.comment?.let { listOf(EntryComment(it)) } ?: emptyList() }
                .forEach { appendLine("COMMENT:${it.text}") }
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            entry.attachments.forEach { appendLine(it.toIcsAttach()) }
            entry.unknownProperties.forEach { appendLine(it.line) }
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VJOURNAL")
            appendLine("END:VCALENDAR")
        }.withIcsLineEndings()
    }

    fun taskToIcs(entry: TaskEntry): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("PRODID:-//jtxBoard Desktop//EN")
            appendLine("BEGIN:VTODO")
            appendLine("UID:${entry.uid}")
            appendLine("DTSTAMP:${formatIcsDate(entry.updated)}")
            if (entry.start != null) appendLine("DTSTART:${formatIcsDate(entry.start)}")
            if (entry.due != null) appendLine("DUE:${formatIcsDate(entry.due)}")
            appendLine("SUMMARY:${entry.title}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description}")
            if (entry.completed) appendLine("STATUS:COMPLETED")
            appendLine("PERCENT-COMPLETE:${entry.progress}")
            if (entry.priority != Priority.NONE) appendLine("PRIORITY:${entry.priority.toIcsPriority()}")
            entry.recurrenceRule?.let { appendLine("RRULE:${it.toIcsRRule()}") }
            if (entry.recurrenceDates.isNotEmpty()) appendLine("RDATE${entry.recurrenceTimezone.toIcsTimezoneParam()}:${entry.recurrenceDates.joinToString(",") { formatIcsDate(it) }}")
            if (entry.exceptionDates.isNotEmpty()) appendLine("EXDATE${entry.recurrenceTimezone.toIcsTimezoneParam()}:${entry.exceptionDates.joinToString(",") { formatIcsDate(it) }}")
            if (entry.recurrenceId != null) appendLine("RECURRENCE-ID${entry.recurrenceIdTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.recurrenceId)}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",")}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            if (entry.location != null) appendLine("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:${entry.location}")
            entry.comments.forEach { appendLine("COMMENT:${it.text}") }
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            entry.attachments.forEach { appendLine(it.toIcsAttach()) }
            entry.unknownProperties.forEach { appendLine(it.line) }
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VTODO")
            appendLine("END:VCALENDAR")
        }.withIcsLineEndings()
    }

    fun noteToIcs(entry: NoteEntry): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("PRODID:-//jtxBoard Desktop//EN")
            appendLine("BEGIN:VNOTE")
            appendLine("UID:${entry.uid}")
            appendLine("DTSTAMP:${formatIcsDate(entry.updated)}")
            appendLine("SUMMARY:${entry.title}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",")}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            if (entry.location != null) appendLine("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:${entry.location}")
            entry.comments.forEach { appendLine("COMMENT:${it.text}") }
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            entry.attachments.forEach { appendLine(it.toIcsAttach()) }
            entry.unknownProperties.forEach { appendLine(it.line) }
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VNOTE")
            appendLine("END:VCALENDAR")
        }.withIcsLineEndings()
    }

    private fun String.withIcsLineEndings(): String = trimEnd().lines().joinToString("\r\n", postfix = "\r\n")

    private fun parseIcsDate(line: String): Long? {
        return try {
            val value = line.substringAfter(":").trim()
            val cleanValue = value.replace("T", "").replace("Z", "")
            if (cleanValue.length >= 8) {
                val year = cleanValue.substring(0, 4).toInt()
                val month = cleanValue.substring(4, 6).toInt()
                val day = cleanValue.substring(6, 8).toInt()
                val hour = if (cleanValue.length >= 10) cleanValue.substring(8, 10).toInt() else 0
                val minute = if (cleanValue.length >= 12) cleanValue.substring(10, 12).toInt() else 0
                val second = if (cleanValue.length >= 14) cleanValue.substring(12, 14).toInt() else 0
                java.util.Calendar.getInstance().apply {
                    set(year, month - 1, day, hour, minute, second)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else null
        } catch (e: Exception) { null }
    }

    private fun parsePriority(value: String): Priority {
        return when (value.toIntOrNull()) {
            null, 0 -> Priority.NONE
            1 -> Priority.URGENT
            in 2..4 -> Priority.HIGH
            5 -> Priority.MEDIUM
            in 6..9 -> Priority.LOW
            else -> Priority.NONE
        }
    }

    private fun isUnknownContentLine(line: String, knownProperties: List<String>): Boolean {
        if (!line.contains(":")) return false
        val property = propertyName(line)
        return knownProperties.none { it == property }
    }

    private fun String.isProperty(name: String): Boolean = propertyName(this) == name

    private fun String.propertyValue(): String = substringAfter(":").trim()

    private fun propertyName(line: String): String = line.substringBefore(":").substringBefore(";").uppercase()

    private fun parseRecurrenceRule(value: String): RecurrenceRule? {
        val parts = value.split(";").mapNotNull { part ->
            val key = part.substringBefore("=", missingDelimiterValue = "").uppercase()
            val partValue = part.substringAfter("=", missingDelimiterValue = "")
            if (key.isBlank() || partValue.isBlank()) null else key to partValue
        }.toMap()
        val frequency = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> RecurrenceFrequency.DAILY
            "WEEKLY" -> RecurrenceFrequency.WEEKLY
            "MONTHLY" -> RecurrenceFrequency.MONTHLY
            "YEARLY" -> RecurrenceFrequency.YEARLY
            else -> return null
        }
        return RecurrenceRule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.toIntOrNull() ?: 1,
            endDate = parts["UNTIL"]?.let { parseIcsDate("UNTIL:$it") },
            count = parts["COUNT"]?.toIntOrNull()
        )
    }

    private fun parseDateList(line: String): List<Long> {
        return line.substringAfter(":")
            .split(",")
            .mapNotNull { parseIcsDate("DATE:${it.trim()}") }
    }

    private fun parseTimezone(line: String): String? {
        return line.substringBefore(":")
            .split(";")
            .firstNotNullOfOrNull { part ->
                part.takeIf { it.startsWith("TZID=", ignoreCase = true) }?.substringAfter("=")
            }
    }

    private fun parseAttachment(line: String): EntryAttachment {
        val params = line.substringBefore(":")
            .split(";")
            .drop(1)
            .mapNotNull { param ->
                val key = param.substringBefore("=", missingDelimiterValue = "").uppercase()
                val value = param.substringAfter("=", missingDelimiterValue = "")
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
        return EntryAttachment(
            uri = line.substringAfter(":").trim(),
            filename = params["FILENAME"],
            mimeType = params["FMTTYPE"],
            size = params["SIZE"]?.toLongOrNull()
        )
    }

    private fun String?.toIcsTimezoneParam(): String {
        return if (this.isNullOrBlank()) "" else ";TZID=$this"
    }

    private fun EntryAttachment.toIcsAttach(): String {
        return buildString {
            append("ATTACH")
            if (!mimeType.isNullOrBlank()) append(";FMTTYPE=$mimeType")
            if (!filename.isNullOrBlank()) append(";FILENAME=$filename")
            if (size != null) append(";SIZE=$size")
            append(":$uri")
        }
    }

    private fun RecurrenceRule.toIcsRRule(): String {
        return buildString {
            append("FREQ=${frequency.name}")
            if (interval != 1) append(";INTERVAL=$interval")
            if (count != null) append(";COUNT=$count")
            if (endDate != null) append(";UNTIL=${formatIcsDate(endDate)}")
        }
    }

    private fun Priority.toIcsPriority(): Int {
        return when (this) {
            Priority.NONE -> 0
            Priority.URGENT -> 1
            Priority.HIGH -> 3
            Priority.MEDIUM -> 5
            Priority.LOW -> 9
        }
    }

    private fun formatIcsDate(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%04d%02d%02dT%02d%02d%02dZ",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }
}
