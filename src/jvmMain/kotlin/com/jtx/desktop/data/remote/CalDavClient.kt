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

    suspend fun fetchEntries(credentials: CalDavCredentials, collection: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$collection").toString())
            val conn = url.openConnection() as HttpsURLConnection
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCalendarData(credentials: CalDavCredentials, href: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, credentials)

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val data = conn.inputStream.bufferedReader().readText()
                Result.success(data)
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun putEntry(credentials: CalDavCredentials, href: String, icsContent: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(credentials: CalDavCredentials, href: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL(URI("${credentials.serverUrl.trimEnd('/')}/$href").toString())
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "DELETE"
            setAuth(conn, credentials)

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    fun parseVJournal(ics: String): JournalEntry? {
        return try {
            val lines = ics.lines()
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

            for (line in lines) {
                when {
                    line.startsWith("UID:") -> uid = line.substringAfter("UID:").trim()
                    line.startsWith("SUMMARY:") -> summary = line.substringAfter("SUMMARY:").trim()
                    line.startsWith("DESCRIPTION:") -> description = line.substringAfter("DESCRIPTION:").trim()
                    line.startsWith("DTSTART") -> dtstart = parseIcsDate(line)
                    line.startsWith("DTEND") -> dtend = parseIcsDate(line)
                    line.startsWith("CATEGORIES:") -> categories = line.substringAfter("CATEGORIES:").trim().split(",").map { it.trim() }.toMutableList()
                    line.startsWith("CREATED:") -> created = parseIcsDate(line)
                    line.startsWith("DTSTAMP:") -> dtstamp = parseIcsDate(line)
                    line.startsWith("X-APPLE-STRUCTURED-LOCATION") -> location = line.substringAfter("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:").trim()
                    line.startsWith("COMMENT:") -> comment = line.substringAfter("COMMENT:").trim()
                    line.startsWith("COLOR:") -> color = line.substringAfter("COLOR:").trim()
                }
            }

            if (uid.isEmpty()) return null

            JournalEntry(
                id = uid, uid = uid, title = summary, description = description,
                dtstart = dtstart, dtend = dtend, categories = categories,
                created = created ?: System.currentTimeMillis(),
                updated = dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, comment = comment
            )
        } catch (e: Exception) { null }
    }

    fun parseVTodo(ics: String): TaskEntry? {
        return try {
            val lines = ics.lines()
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

            for (line in lines) {
                when {
                    line.startsWith("UID:") -> uid = line.substringAfter("UID:").trim()
                    line.startsWith("SUMMARY:") -> summary = line.substringAfter("SUMMARY:").trim()
                    line.startsWith("DESCRIPTION:") -> description = line.substringAfter("DESCRIPTION:").trim()
                    line.startsWith("DUE:") -> due = parseIcsDate(line)
                    line.startsWith("DTSTART") -> start = parseIcsDate(line)
                    line.startsWith("STATUS:COMPLETED") -> completed = true
                    line.startsWith("PERCENT-COMPLETE:") -> progress = line.substringAfter("PERCENT-COMPLETE:").trim().toIntOrNull() ?: 0
                    line.startsWith("CATEGORIES:") -> categories = line.substringAfter("CATEGORIES:").trim().split(",").map { it.trim() }.toMutableList()
                    line.startsWith("CREATED:") -> created = parseIcsDate(line)
                    line.startsWith("DTSTAMP:") -> dtstamp = parseIcsDate(line)
                    line.startsWith("LOCATION:") -> location = line.substringAfter("LOCATION:").trim()
                    line.startsWith("X-APPLE-STRUCTURED-LOCATION") -> location = line.substringAfter("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:").trim()
                }
            }

            if (uid.isEmpty()) return null

            TaskEntry(
                id = uid, uid = uid, title = summary, description = description,
                due = due, start = start, completed = completed, progress = progress,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, subtasks = emptyList(), relatedEntries = emptyList()
            )
        } catch (e: Exception) { null }
    }

    fun journalToIcs(entry: JournalEntry): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
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
            if (entry.comment != null) appendLine("COMMENT:${entry.comment}")
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VJOURNAL")
            appendLine("END:VCALENDAR")
        }
    }

    fun taskToIcs(entry: TaskEntry): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
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
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",")}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            if (entry.location != null) appendLine("X-APPLE-STRUCTURED-LOCATION;VALUE=URI:${entry.location}")
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VTODO")
            appendLine("END:VCALENDAR")
        }
    }

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