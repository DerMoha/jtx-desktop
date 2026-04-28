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
            val url = resolveUrl(credentials, collection)
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.requestMethod = "REPORT"
            conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            conn.setRequestProperty("Depth", "1")
            setAuth(conn, credentials)

            val body = """<?xml version="1.0" encoding="utf-8"?>
                <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                    <d:prop>
                        <d:getetag/>
                        <c:calendar-data/>
                    </d:prop>
                    <c:filter>
                        <c:comp-filter name="VCALENDAR"/>
                    </c:filter>
                </c:calendar-query>"""

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

    suspend fun discoverCollections(credentials: CalDavCredentials): Result<List<CalDavCollection>> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val principalXml = propfind(
                credentials = credentials,
                href = credentials.serverUrl,
                depth = "0",
                body = """<?xml version="1.0" encoding="utf-8"?>
                    <d:propfind xmlns:d="DAV:">
                        <d:prop><d:current-user-principal/></d:prop>
                    </d:propfind>"""
            )
            val principalHref = firstHrefInElement(principalXml, "current-user-principal") ?: credentials.serverUrl
            val homeSetXml = propfind(
                credentials = credentials,
                href = principalHref,
                depth = "0",
                body = """<?xml version="1.0" encoding="utf-8"?>
                    <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                        <d:prop><c:calendar-home-set/></d:prop>
                    </d:propfind>"""
            )
            val calendarHomeHref = firstHrefInElement(homeSetXml, "calendar-home-set") ?: principalHref
            val collectionsXml = propfind(
                credentials = credentials,
                href = calendarHomeHref,
                depth = "1",
                body = """<?xml version="1.0" encoding="utf-8"?>
                    <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/">
                        <d:prop>
                            <d:displayname/>
                            <d:resourcetype/>
                            <d:current-user-privilege-set/>
                            <c:supported-calendar-component-set/>
                            <cs:getctag/>
                            <cs:calendar-color/>
                            <d:sync-token/>
                        </d:prop>
                    </d:propfind>"""
            )
            Result.success(parseCollections(collectionsXml))
        }
    }

    suspend fun fetchCalendarData(credentials: CalDavCredentials, href: String): Result<String> = withContext(Dispatchers.IO) {
        retryWithBackoff(maxRetries) {
            val url = resolveUrl(credentials, href)
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
            val url = resolveUrl(credentials, href)
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
            val url = resolveUrl(credentials, href)
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

    private fun propfind(credentials: CalDavCredentials, href: String, depth: String, body: String): String {
        val url = resolveUrl(credentials, href)
        val conn = url.openConnection() as HttpsURLConnection
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        conn.requestMethod = "PROPFIND"
        conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        conn.setRequestProperty("Depth", depth)
        setAuth(conn, credentials)
        conn.doOutput = true
        conn.outputStream.write(body.toByteArray())

        val responseCode = conn.responseCode
        if (responseCode != 207) {
            throw Exception("HTTP $responseCode: ${conn.responseMessage}")
        }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun resolveUrl(credentials: CalDavCredentials, href: String): URL {
        if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            return URL(URI(href).toString())
        }
        val base = URI(credentials.serverUrl.trimEnd('/') + "/")
        return URL(base.resolve(href).toString())
    }

    private fun parseHrefs(xml: String): List<String> {
        return responseBlocks(xml).mapNotNull { response ->
            elementText(response, "href")
        }.distinct()
    }

    private fun firstHrefInElement(xml: String, element: String): String? {
        val block = elementBlock(xml, element) ?: return null
        return elementText(block, "href")
    }

    private fun parseCollections(xml: String): List<CalDavCollection> {
        return responseBlocks(xml).mapNotNull { response ->
            if (!response.contains(Regex("<(?:\\w+:)?calendar[\\s>/]", RegexOption.IGNORE_CASE))) return@mapNotNull null
            val href = elementText(response, "href") ?: return@mapNotNull null
            val components = parseSupportedComponents(response)
            CalDavCollection(
                url = href,
                displayName = elementText(response, "displayname") ?: href.trim('/').substringAfterLast('/'),
                color = elementText(response, "calendar-color"),
                supportedComponents = components,
                readOnly = !response.contains(Regex("<(?:\\w+:)?write[\\s>/]", RegexOption.IGNORE_CASE)),
                syncToken = elementText(response, "sync-token"),
                ctag = elementText(response, "getctag"),
                lastSync = null
            )
        }
    }

    private fun parseSupportedComponents(response: String): List<EntryType> {
        val names = Regex("<(?:\\w+:)?comp[^>]*name=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(response)
            .map { it.groupValues[1].uppercase() }
            .toSet()
        val components = mutableSetOf<EntryType>()
        if ("VTODO" in names) components.add(EntryType.TASK)
        if ("VJOURNAL" in names) {
            components.add(EntryType.JOURNAL)
            components.add(EntryType.NOTE)
        }
        if ("VNOTE" in names) components.add(EntryType.NOTE)
        return components.toList()
    }

    private fun responseBlocks(xml: String): List<String> = Regex(
        "<(?:\\w+:)?response\\b[^>]*>(.*?)</(?:\\w+:)?response>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(xml).map { it.value }.toList()

    private fun elementBlock(xml: String, element: String): String? = Regex(
        "<(?:\\w+:)?${Regex.escape(element)}\\b[^>]*>(.*?)</(?:\\w+:)?${Regex.escape(element)}>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(xml)?.value

    private fun elementText(xml: String, element: String): String? = elementBlock(xml, element)
        ?.replace(Regex("<[^>]+>"), "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

class ICalendarParser {

    private val ical4j = Ical4jEvaluation()

    private val commonKnownProperties = listOf(
        "BEGIN", "END", "VERSION", "PRODID", "UID", "DTSTAMP", "LAST-MODIFIED", "SUMMARY", "DESCRIPTION",
        "CALSCALE", "CATEGORIES", "CREATED", "COLOR", "SEQUENCE", "URL", "CONTACT", "GEO", "CLASS",
        "LOCATION", "X-APPLE-STRUCTURED-LOCATION", "RELATED-TO", "ATTACH"
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
            "VJOURNAL" in componentNames -> if (ics.hasProperty("DTSTART")) parseVJournal(ics) else parseVJournalNote(ics)
            else -> null
        }
    }

    private fun String.hasProperty(name: String): Boolean = unfoldLines(lines()).any { it.isProperty(name) }

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
            var startTimezone: String? = null
            var dtend: Long? = null
            var endTimezone: String? = null
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var lastModified: Long? = null
            var sequence = 0
            var color: String? = null
            var location: String? = null
            var url: String? = null
            var contact: String? = null
            var geo: String? = null
            var classification: String? = null
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
                    line.isProperty("SUMMARY") -> summary = line.propertyTextValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.propertyTextValue()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("DTSTART") -> {
                        dtstart = parseIcsDate(line)
                        startTimezone = parseTimezone(line)
                    }
                    line.isProperty("DTEND") -> {
                        dtend = parseIcsDate(line)
                        endTimezone = parseTimezone(line)
                    }
                    line.isProperty("CATEGORIES") -> categories = parseIcsTextList(line.propertyValue()).toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("LAST-MODIFIED") -> lastModified = parseIcsDate(line)
                    line.isProperty("SEQUENCE") -> sequence = line.propertyValue().toIntOrNull() ?: 0
                    line.isProperty("URL") -> url = line.propertyValue()
                    line.isProperty("CONTACT") -> contact = line.propertyTextValue()
                    line.isProperty("GEO") -> geo = line.propertyValue()
                    line.isProperty("CLASS") -> classification = line.propertyValue()
                    line.isProperty("LOCATION") -> location = line.propertyTextValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("COMMENT") -> {
                        val text = line.propertyTextValue()
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
                dtstart = dtstart, startTimezone = startTimezone,
                dtend = dtend, endTimezone = endTimezone, categories = categories,
                created = created ?: System.currentTimeMillis(),
                updated = lastModified ?: dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, comment = comment,
                relatedEntries = relatedEntries, attachments = attachments,
                comments = comments, unknownProperties = unknownProperties,
                sequence = sequence, url = url, contact = contact,
                geo = geo, classification = classification
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
            var dueTimezone: String? = null
            var start: Long? = null
            var startTimezone: String? = null
            var completed = false
            var progress = 0
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var lastModified: Long? = null
            var sequence = 0
            var color: String? = null
            var location: String? = null
            var url: String? = null
            var contact: String? = null
            var geo: String? = null
            var classification: String? = null
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
            var reminders = emptyList<Reminder>()
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf(
                "DUE", "DTSTART", "STATUS", "PERCENT-COMPLETE", "PRIORITY", "RRULE", "RDATE", "EXDATE",
                "RECURRENCE-ID", "LOCATION", "COMMENT", "VALARM", "TRIGGER", "ACTION"
            )
            reminders = parseAlarms(lines)
            unknownProperties = unknownProperties + parseUnsupportedAlarmProperties(lines)

            for (line in linesOutsideAlarms(lines)) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyTextValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.propertyTextValue()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("DUE") -> {
                        due = parseIcsDate(line)
                        dueTimezone = parseTimezone(line)
                    }
                    line.isProperty("DTSTART") -> {
                        start = parseIcsDate(line)
                        startTimezone = parseTimezone(line)
                    }
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
                    line.isProperty("CATEGORIES") -> categories = parseIcsTextList(line.propertyValue()).toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("LAST-MODIFIED") -> lastModified = parseIcsDate(line)
                    line.isProperty("SEQUENCE") -> sequence = line.propertyValue().toIntOrNull() ?: 0
                    line.isProperty("URL") -> url = line.propertyValue()
                    line.isProperty("CONTACT") -> contact = line.propertyTextValue()
                    line.isProperty("GEO") -> geo = line.propertyValue()
                    line.isProperty("CLASS") -> classification = line.propertyValue()
                    line.isProperty("LOCATION") -> location = line.propertyTextValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("COLOR") -> color = line.propertyValue()
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COMMENT") -> comments = comments + EntryComment(line.propertyTextValue())
                }
            }

            if (uid.isEmpty()) return null

            TaskEntry(
                id = uid, uid = uid, title = summary, description = description,
                due = due, start = start, completed = completed, progress = progress,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = lastModified ?: dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, subtasks = emptyList(), relatedEntries = relatedEntries,
                priority = priority, recurrenceRule = recurrenceRule, recurrenceDates = recurrenceDates,
                exceptionDates = exceptionDates, recurrenceId = recurrenceId,
                reminders = reminders,
                dueTimezone = dueTimezone, startTimezone = startTimezone,
                recurrenceTimezone = recurrenceTimezone, recurrenceIdTimezone = recurrenceIdTimezone,
                attachments = attachments, comments = comments,
                unknownProperties = unknownProperties, sequence = sequence,
                url = url, contact = contact, geo = geo, classification = classification
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
            var lastModified: Long? = null
            var sequence = 0
            var color: String? = null
            var location: String? = null
            var url: String? = null
            var contact: String? = null
            var geo: String? = null
            var classification: String? = null
            var relatedEntries = emptyList<String>()
            var attachments = emptyList<EntryAttachment>()
            var comments = emptyList<EntryComment>()
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf("COMMENT")

            for (line in lines) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyTextValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.propertyTextValue()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("CATEGORIES") -> categories = parseIcsTextList(line.propertyValue()).toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("LAST-MODIFIED") -> lastModified = parseIcsDate(line)
                    line.isProperty("SEQUENCE") -> sequence = line.propertyValue().toIntOrNull() ?: 0
                    line.isProperty("URL") -> url = line.propertyValue()
                    line.isProperty("CONTACT") -> contact = line.propertyTextValue()
                    line.isProperty("GEO") -> geo = line.propertyValue()
                    line.isProperty("CLASS") -> classification = line.propertyValue()
                    line.isProperty("LOCATION") -> location = line.propertyTextValue()
                    line.isProperty("COLOR") -> color = line.propertyValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COMMENT") -> comments = comments + EntryComment(line.propertyTextValue())
                }
            }

            if (uid.isEmpty()) return null

            NoteEntry(
                id = uid, uid = uid, title = summary, description = description,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = lastModified ?: dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, relatedEntries = relatedEntries,
                attachments = attachments, comments = comments,
                unknownProperties = unknownProperties, sequence = sequence,
                url = url, contact = contact, geo = geo, classification = classification
            )
        } catch (e: Exception) { null }
    }

    private fun parseVJournalNote(ics: String): NoteEntry? {
        return try {
            val lines = unfoldLines(ics.lines())
            var uid = ""
            var summary = ""
            var description = ""
            var categories = mutableListOf<String>()
            var created: Long? = null
            var dtstamp: Long? = null
            var lastModified: Long? = null
            var sequence = 0
            var color: String? = null
            var location: String? = null
            var url: String? = null
            var contact: String? = null
            var geo: String? = null
            var classification: String? = null
            var relatedEntries = emptyList<String>()
            var attachments = emptyList<EntryAttachment>()
            var comments = emptyList<EntryComment>()
            var unknownProperties = emptyList<UnknownProperty>()
            val knownProperties = commonKnownProperties + listOf("COMMENT")

            for (line in lines) {
                if (isUnknownContentLine(line, knownProperties)) unknownProperties = unknownProperties + UnknownProperty(line)
                when {
                    line.isProperty("UID") -> uid = line.propertyValue()
                    line.isProperty("SUMMARY") -> summary = line.propertyTextValue()
                    line.isProperty("DESCRIPTION") -> {
                        val value = line.propertyTextValue()
                        if (description.isEmpty()) description = value else description += value
                    }
                    line.isProperty("CATEGORIES") -> categories = parseIcsTextList(line.propertyValue()).toMutableList()
                    line.isProperty("CREATED") -> created = parseIcsDate(line)
                    line.isProperty("DTSTAMP") -> dtstamp = parseIcsDate(line)
                    line.isProperty("LAST-MODIFIED") -> lastModified = parseIcsDate(line)
                    line.isProperty("SEQUENCE") -> sequence = line.propertyValue().toIntOrNull() ?: 0
                    line.isProperty("URL") -> url = line.propertyValue()
                    line.isProperty("CONTACT") -> contact = line.propertyTextValue()
                    line.isProperty("GEO") -> geo = line.propertyValue()
                    line.isProperty("CLASS") -> classification = line.propertyValue()
                    line.isProperty("LOCATION") -> location = line.propertyTextValue()
                    line.isProperty("COLOR") -> color = line.propertyValue()
                    line.isProperty("X-APPLE-STRUCTURED-LOCATION") -> location = line.propertyValue()
                    line.isProperty("RELATED-TO") -> relatedEntries = relatedEntries + line.propertyValue()
                    line.isProperty("ATTACH") -> attachments = attachments + parseAttachment(line)
                    line.isProperty("COMMENT") -> comments = comments + EntryComment(line.propertyTextValue())
                }
            }

            if (uid.isEmpty()) return null

            NoteEntry(
                id = uid, uid = uid, title = summary, description = description,
                categories = categories, created = created ?: System.currentTimeMillis(),
                updated = lastModified ?: dtstamp ?: System.currentTimeMillis(),
                color = color, location = location, relatedEntries = relatedEntries,
                attachments = attachments, comments = comments,
                unknownProperties = unknownProperties, sequence = sequence,
                url = url, contact = contact, geo = geo, classification = classification
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
            if (entry.sequence > 0) appendLine("SEQUENCE:${entry.sequence}")
            if (entry.dtstart != null) appendLine("DTSTART${entry.startTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.dtstart, entry.startTimezone)}")
            if (entry.dtend != null) appendLine("DTEND${entry.endTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.dtend, entry.endTimezone)}")
            appendLine("SUMMARY:${entry.title.escapeIcsText()}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description.escapeIcsText()}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",") { it.escapeIcsText() }}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            appendOptionalFields(entry.url, entry.contact, entry.geo, entry.classification, entry.location)
            entry.comments.ifEmpty { entry.comment?.let { listOf(EntryComment(it)) } ?: emptyList() }
                .forEach { appendLine("COMMENT:${it.text.escapeIcsText()}") }
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
            if (entry.sequence > 0) appendLine("SEQUENCE:${entry.sequence}")
            if (entry.start != null) appendLine("DTSTART${entry.startTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.start, entry.startTimezone)}")
            if (entry.due != null) appendLine("DUE${entry.dueTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.due, entry.dueTimezone)}")
            appendLine("SUMMARY:${entry.title.escapeIcsText()}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description.escapeIcsText()}")
            if (entry.completed) appendLine("STATUS:COMPLETED")
            appendLine("PERCENT-COMPLETE:${entry.progress}")
            if (entry.priority != Priority.NONE) appendLine("PRIORITY:${entry.priority.toIcsPriority()}")
            entry.recurrenceRule?.let { appendLine("RRULE:${it.toIcsRRule()}") }
            if (entry.recurrenceDates.isNotEmpty()) appendLine("RDATE${entry.recurrenceTimezone.toIcsTimezoneParam()}:${entry.recurrenceDates.joinToString(",") { formatIcsDate(it, entry.recurrenceTimezone) }}")
            if (entry.exceptionDates.isNotEmpty()) appendLine("EXDATE${entry.recurrenceTimezone.toIcsTimezoneParam()}:${entry.exceptionDates.joinToString(",") { formatIcsDate(it, entry.recurrenceTimezone) }}")
            if (entry.recurrenceId != null) appendLine("RECURRENCE-ID${entry.recurrenceIdTimezone.toIcsTimezoneParam()}:${formatIcsDate(entry.recurrenceId, entry.recurrenceIdTimezone)}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",") { it.escapeIcsText() }}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            appendOptionalFields(entry.url, entry.contact, entry.geo, entry.classification, entry.location)
            entry.comments.forEach { appendLine("COMMENT:${it.text.escapeIcsText()}") }
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            entry.attachments.forEach { appendLine(it.toIcsAttach()) }
            entry.reminders.forEach { appendLine(it.toIcsAlarm()) }
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
            appendLine("BEGIN:VJOURNAL")
            appendLine("UID:${entry.uid}")
            appendLine("DTSTAMP:${formatIcsDate(entry.updated)}")
            if (entry.sequence > 0) appendLine("SEQUENCE:${entry.sequence}")
            appendLine("SUMMARY:${entry.title.escapeIcsText()}")
            if (entry.description.isNotEmpty()) appendLine("DESCRIPTION:${entry.description.escapeIcsText()}")
            if (entry.categories.isNotEmpty()) appendLine("CATEGORIES:${entry.categories.joinToString(",") { it.escapeIcsText() }}")
            if (entry.color != null) appendLine("COLOR:${entry.color}")
            appendOptionalFields(entry.url, entry.contact, entry.geo, entry.classification, entry.location)
            entry.comments.forEach { appendLine("COMMENT:${it.text.escapeIcsText()}") }
            entry.relatedEntries.forEach { appendLine("RELATED-TO:$it") }
            entry.attachments.forEach { appendLine(it.toIcsAttach()) }
            entry.unknownProperties.forEach { appendLine(it.line) }
            appendLine("CREATED:${formatIcsDate(entry.created)}")
            appendLine("LAST-MODIFIED:${formatIcsDate(entry.updated)}")
            appendLine("END:VJOURNAL")
            appendLine("END:VCALENDAR")
        }.withIcsLineEndings()
    }

    private fun String.withIcsLineEndings(): String = trimEnd()
        .lines()
        .flatMap { foldIcsLine(it) }
        .joinToString("\r\n", postfix = "\r\n")

    private fun StringBuilder.appendOptionalFields(
        url: String?,
        contact: String?,
        geo: String?,
        classification: String?,
        location: String?
    ) {
        if (!url.isNullOrBlank()) appendLine("URL:$url")
        if (!contact.isNullOrBlank()) appendLine("CONTACT:${contact.escapeIcsText()}")
        if (!geo.isNullOrBlank()) appendLine("GEO:$geo")
        if (!classification.isNullOrBlank()) appendLine("CLASS:$classification")
        if (!location.isNullOrBlank()) appendLine("LOCATION:${location.escapeIcsText()}")
    }

    private fun foldIcsLine(line: String): List<String> {
        val limit = 75
        if (line.length <= limit) return listOf(line)

        val lines = mutableListOf<String>()
        var start = 0
        var chunkSize = limit
        while (start < line.length) {
            val end = minOf(start + chunkSize, line.length)
            val prefix = if (start == 0) "" else " "
            lines.add(prefix + line.substring(start, end))
            start = end
            chunkSize = limit - 1
        }
        return lines
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

    private fun String.propertyTextValue(): String = propertyValue().unescapeIcsText()

    private fun propertyName(line: String): String = line.substringBefore(":").substringBefore(";").uppercase()

    private fun parseIcsTextList(value: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (char in value) {
            when {
                escaping -> {
                    current.append('\\').append(char)
                    escaping = false
                }
                char == '\\' -> escaping = true
                char == ',' -> {
                    values.add(current.toString().trim().unescapeIcsText())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (escaping) current.append('\\')
        values.add(current.toString().trim().unescapeIcsText())
        return values.filter { it.isNotEmpty() }
    }

    private fun String.unescapeIcsText(): String {
        val result = StringBuilder()
        var escaping = false
        for (char in this) {
            if (escaping) {
                result.append(
                    when (char) {
                        'n', 'N' -> '\n'
                        '\\', ';', ',' -> char
                        else -> char
                    }
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                result.append(char)
            }
        }
        if (escaping) result.append('\\')
        return result.toString()
    }

    private fun String.escapeIcsText(): String = buildString {
        for (char in this@escapeIcsText) {
            when (char) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                ';' -> append("\\;")
                ',' -> append("\\,")
                else -> append(char)
            }
        }
    }

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
            count = parts["COUNT"]?.toIntOrNull(),
            rawRule = value
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

    private fun parseAlarms(lines: List<String>): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        var inAlarm = false
        var trigger: String? = null
        var soundEnabled = false

        for (line in lines) {
            when {
                line.equals("BEGIN:VALARM", ignoreCase = true) -> {
                    inAlarm = true
                    trigger = null
                    soundEnabled = false
                }
                line.equals("END:VALARM", ignoreCase = true) -> {
                    parseReminderMinutes(trigger)?.let { minutes ->
                        reminders.add(Reminder(minutesBefore = minutes, soundEnabled = soundEnabled))
                    }
                    inAlarm = false
                }
                inAlarm && line.isProperty("TRIGGER") -> trigger = line.propertyValue()
                inAlarm && line.isProperty("ACTION") -> soundEnabled = line.propertyValue().equals("AUDIO", ignoreCase = true)
            }
        }

        return reminders
    }

    private fun parseUnsupportedAlarmProperties(lines: List<String>): List<UnknownProperty> {
        val properties = mutableListOf<UnknownProperty>()
        var alarmLines = mutableListOf<String>()
        var inAlarm = false

        for (line in lines) {
            when {
                line.equals("BEGIN:VALARM", ignoreCase = true) -> {
                    inAlarm = true
                    alarmLines = mutableListOf(line)
                }
                line.equals("END:VALARM", ignoreCase = true) && inAlarm -> {
                    alarmLines.add(line)
                    val trigger = alarmLines.firstOrNull { it.isProperty("TRIGGER") }?.propertyValue()
                    if (parseReminderMinutes(trigger) == null) {
                        properties.addAll(alarmLines.map { UnknownProperty(it) })
                    }
                    inAlarm = false
                }
                inAlarm -> alarmLines.add(line)
            }
        }

        return properties
    }

    private fun linesOutsideAlarms(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var inAlarm = false
        for (line in lines) {
            when {
                line.equals("BEGIN:VALARM", ignoreCase = true) -> inAlarm = true
                line.equals("END:VALARM", ignoreCase = true) -> inAlarm = false
                !inAlarm -> result.add(line)
            }
        }
        return result
    }

    private fun parseReminderMinutes(trigger: String?): Int? {
        if (trigger.isNullOrBlank() || !trigger.startsWith("-P", ignoreCase = true)) return null
        val value = trigger.uppercase().removePrefix("-P")
        var minutes = 0
        val dayMatch = Regex("(\\d+)D").find(value)
        val hourMatch = Regex("(\\d+)H").find(value)
        val minuteMatch = Regex("(\\d+)M").find(value)
        minutes += (dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0) * 24 * 60
        minutes += (hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0) * 60
        minutes += minuteMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return minutes.takeIf { it > 0 }
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

    private fun Reminder.toIcsAlarm(): String {
        val days = minutesBefore / (24 * 60)
        val hours = (minutesBefore % (24 * 60)) / 60
        val minutes = minutesBefore % 60
        val duration = buildString {
            append("-P")
            if (days > 0) append("${days}D")
            if (hours > 0 || minutes > 0) {
                append("T")
                if (hours > 0) append("${hours}H")
                if (minutes > 0) append("${minutes}M")
            }
        }
        return buildString {
            appendLine("BEGIN:VALARM")
            appendLine("ACTION:${if (soundEnabled) "AUDIO" else "DISPLAY"}")
            appendLine("TRIGGER:$duration")
            append("END:VALARM")
        }
    }

    private fun RecurrenceRule.toIcsRRule(): String {
        if (!rawRule.isNullOrBlank()) return rawRule
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

    private fun formatIcsDate(timestamp: Long, timezone: String? = null): String {
        val cal = java.util.Calendar.getInstance(timezone?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestamp
        }
        return String.format("%04d%02d%02dT%02d%02d%02d%s",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            if (timezone.isNullOrBlank()) "Z" else ""
        )
    }
}
