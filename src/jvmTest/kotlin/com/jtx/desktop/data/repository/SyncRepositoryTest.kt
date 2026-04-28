package com.jtx.desktop.data.repository

import com.jtx.desktop.data.local.SqliteLocalDataSource
import com.jtx.desktop.data.remote.CalDavClient
import com.jtx.desktop.data.remote.ICalendarParser
import com.jtx.desktop.domain.model.CalDavCredentials
import com.jtx.desktop.domain.model.EntryType
import com.jtx.desktop.domain.model.JournalEntry
import com.jtx.desktop.domain.model.ObjectSyncMetadata
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncRepositoryTest {
    @Test
    fun syncUploadsLocalCreatesUpdatesAndDeletes() = runBlocking {
        TestCalDavServer().use { server ->
            val dataSource = SqliteLocalDataSource(Files.createTempFile("jtx-sync", ".db").toString())
            val repository = SyncRepository(dataSource, CalDavClient(), ICalendarParser())
            val journals = JournalRepository(dataSource)

            journals.insert(journalEntry(id = "journal-create", title = "Create me", updated = 1_000L))
            val createResult = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(createResult.successCount > 0)
            assertTrue(server.hasObject("/calendars/main/journal-create.ics"))
            val createdMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, "journal-create")
            assertNotNull(createdMetadata)
            assertFalse(createdMetadata.dirty)
            assertFalse(createdMetadata.deleted)
            assertNotNull(createdMetadata.href)

            journals.update(journalEntry(id = "journal-create", title = "Updated title", updated = 2_000L))
            val updateResult = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(updateResult.successCount > 0)
            assertTrue(server.objectData("/calendars/main/journal-create.ics")?.contains("SUMMARY:Updated title") == true)
            val updatedMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, "journal-create")
            assertNotNull(updatedMetadata)
            assertFalse(updatedMetadata.dirty)
            assertFalse(updatedMetadata.deleted)

            journals.delete("journal-create")
            val deleteResult = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(deleteResult.successCount > 0)
            assertFalse(server.hasObject("/calendars/main/journal-create.ics"))
            val deletedMetadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, "journal-create")
            assertNotNull(deletedMetadata)
            assertFalse(deletedMetadata.dirty)
            assertTrue(deletedMetadata.deleted)
        }
    }

    @Test
    fun syncDetectsUpdateConflictsAndLeavesLocalChangeQueued() = runBlocking {
        TestCalDavServer(conflictUpdates = true).use { server ->
            val parser = ICalendarParser()
            val dataSource = SqliteLocalDataSource(Files.createTempFile("jtx-sync-conflict", ".db").toString())
            val repository = SyncRepository(dataSource, CalDavClient(), parser)
            val href = "${server.collectionUrl.trimEnd('/')}/journal-conflict.ics"
            val localEntry = journalEntry(id = "journal-conflict", title = "Local title", updated = 3_000L)
            val remoteEntry = journalEntry(id = "journal-conflict", title = "Remote title", updated = 2_000L)

            dataSource.insertJournal(localEntry)
            dataSource.upsertObjectSyncMetadata(
                ObjectSyncMetadata(
                    entryId = localEntry.id,
                    entryType = EntryType.JOURNAL,
                    collectionUrl = server.collectionUrl,
                    href = href,
                    filename = "journal-conflict.ics",
                    etag = "\"old-etag\"",
                    dirty = true,
                    deleted = false,
                    uid = localEntry.uid,
                    lastModified = localEntry.updated
                )
            )
            server.putObject("/calendars/main/journal-conflict.ics", parser.entryToIcs(remoteEntry), "\"remote-etag\"")

            val result = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(result.conflicts.isNotEmpty())
            val metadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, localEntry.id)
            assertNotNull(metadata)
            assertTrue(metadata.dirty)
            assertFalse(metadata.deleted)
            assertEquals("Conflict: remote object changed since last sync", metadata.lastError)
            assertEquals("Local title", dataSource.getJournalById(localEntry.id)?.title)
        }
    }

    @Test
    fun syncSkipsUploadsForReadOnlyCollections() = runBlocking {
        TestCalDavServer(readOnly = true).use { server ->
            val dataSource = SqliteLocalDataSource(Files.createTempFile("jtx-sync-readonly", ".db").toString())
            val repository = SyncRepository(dataSource, CalDavClient(), ICalendarParser())
            val journals = JournalRepository(dataSource)

            journals.insert(journalEntry(id = "journal-readonly", title = "Queued", updated = 1_000L))

            val result = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(result.failures.any { it.contains("read-only", ignoreCase = true) })
            assertFalse(server.hasObject("/calendars/main/journal-readonly.ics"))
            val metadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, "journal-readonly")
            assertNotNull(metadata)
            assertTrue(metadata.dirty)
            assertFalse(metadata.deleted)
        }
    }

    @Test
    fun syncKeepsFailedUploadsQueuedForRetry() = runBlocking {
        TestCalDavServer(failCreates = true).use { server ->
            val dataSource = SqliteLocalDataSource(Files.createTempFile("jtx-sync-offline", ".db").toString())
            val repository = SyncRepository(dataSource, CalDavClient(), ICalendarParser())
            val journals = JournalRepository(dataSource)

            journals.insert(journalEntry(id = "journal-offline", title = "Retry later", updated = 1_000L))

            val result = repository.sync(server.credentials, server.collectionUrl).getOrThrow()

            assertTrue(result.failureCount > 0)
            val metadata = dataSource.getObjectSyncMetadata(EntryType.JOURNAL, "journal-offline")
            assertNotNull(metadata)
            assertTrue(metadata.dirty)
            assertFalse(metadata.deleted)
            assertTrue(metadata.lastError?.contains("HTTP 503") == true)
            assertFalse(server.hasObject("/calendars/main/journal-offline.ics"))
        }
    }

    private fun journalEntry(id: String, title: String, updated: Long): JournalEntry = JournalEntry(
        id = id,
        uid = id,
        title = title,
        description = "body",
        dtstart = updated,
        dtend = null,
        categories = emptyList(),
        created = 1_000L,
        updated = updated,
        color = null,
        location = null,
        comment = null
    )

    private class TestCalDavServer(
        private val readOnly: Boolean = false,
        private val failCreates: Boolean = false,
        private val conflictUpdates: Boolean = false
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val objects = ConcurrentHashMap<String, StoredObject>()
        private var etagCounter = 1

        val baseUrl: String
        val collectionUrl: String
        val credentials: CalDavCredentials

        init {
            server.createContext("/") { exchange -> handle(exchange) }
            server.start()
            baseUrl = "http://127.0.0.1:${server.address.port}"
            collectionUrl = "$baseUrl/calendars/main/"
            credentials = CalDavCredentials(baseUrl, "user", "pass")
        }

        fun hasObject(path: String): Boolean = objects.containsKey(path)

        fun objectData(path: String): String? = objects[path]?.data

        fun putObject(path: String, data: String, etag: String = nextEtag()) {
            objects[path] = StoredObject(etag, data)
        }

        override fun close() {
            server.stop(0)
        }

        private fun handle(exchange: HttpExchange) {
            runCatching {
                when (exchange.requestMethod) {
                    "PROPFIND" -> handlePropfind(exchange)
                    "REPORT" -> handleReport(exchange)
                    "PUT" -> handlePut(exchange)
                    "DELETE" -> handleDelete(exchange)
                    else -> exchange.respond(405, "")
                }
            }.onFailure {
                exchange.respond(500, it.message.orEmpty())
            }
        }

        private fun handlePropfind(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val body = exchange.readBody()
            val response = when {
                body.contains("current-user-principal") -> multistatus(
                    response(path, "<d:current-user-principal><d:href>/principal/</d:href></d:current-user-principal>")
                )
                body.contains("calendar-home-set") -> multistatus(
                    response("/principal/", "<c:calendar-home-set><d:href>/calendars/</d:href></c:calendar-home-set>")
                )
                path == "/calendars/" -> multistatus(collectionResponse())
                else -> multistatus(response(path, ""))
            }
            exchange.respond(207, response)
        }

        private fun handleReport(exchange: HttpExchange) {
            val body = exchange.readBody()
            val response = if (body.contains("calendar-multiget")) {
                val requestedHrefs = Regex("<d:href>(.*?)</d:href>")
                    .findAll(body)
                    .map { it.groupValues[1].unescapeXml() }
                    .toList()
                multistatus(requestedHrefs.mapNotNull { href -> objectResponse(href) }.joinToString(""))
            } else {
                multistatus(objects.keys.sorted().joinToString("") { path -> response(href(path), "<d:getetag>${objects.getValue(path).etag}</d:getetag>") })
            }
            exchange.respond(207, response)
        }

        private fun handlePut(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val isCreate = exchange.requestHeaders.getFirst("If-None-Match") == "*"
            if (failCreates && isCreate) {
                exchange.respond(503, "unavailable")
                return
            }
            if (conflictUpdates && !isCreate) {
                exchange.respond(412, "precondition failed")
                return
            }

            val etag = nextEtag()
            objects[path] = StoredObject(etag, exchange.readBody())
            exchange.responseHeaders.add("ETag", etag)
            exchange.respond(if (isCreate) 201 else 204, "")
        }

        private fun handleDelete(exchange: HttpExchange) {
            objects.remove(exchange.requestURI.path)
            exchange.respond(204, "")
        }

        private fun collectionResponse(): String {
            val privilege = if (readOnly) "<d:read/>" else "<d:read/><d:write/>"
            return response(
                href("/calendars/main/"),
                """
                <d:displayname>Main</d:displayname>
                <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                <d:current-user-privilege-set><d:privilege>$privilege</d:privilege></d:current-user-privilege-set>
                <c:supported-calendar-component-set><c:comp name="VJOURNAL"/><c:comp name="VTODO"/></c:supported-calendar-component-set>
                <cs:getctag>ctag-1</cs:getctag>
                <d:sync-token>sync-1</d:sync-token>
                """.trimIndent()
            )
        }

        private fun objectResponse(hrefValue: String): String? {
            val path = URIishPath(hrefValue)
            val stored = objects[path] ?: return null
            return response(
                href(path),
                "<d:getetag>${stored.etag}</d:getetag><c:calendar-data>${stored.data.escapeXml()}</c:calendar-data>"
            )
        }

        private fun response(hrefValue: String, prop: String): String =
            "<d:response><d:href>${hrefValue.escapeXml()}</d:href><d:propstat><d:prop>$prop</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

        private fun multistatus(content: String): String =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\" xmlns:cs=\"http://calendarserver.org/ns/\">$content</d:multistatus>"

        private fun href(path: String): String = "$baseUrl$path"

        private fun nextEtag(): String = "\"etag-${etagCounter++}\""

        private fun HttpExchange.readBody(): String = requestBody.bufferedReader().readText()

        private fun HttpExchange.respond(status: Int, body: String) {
            val bytes = body.toByteArray()
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }

        private fun URIishPath(value: String): String = runCatching {
            java.net.URI(value).path
        }.getOrElse { value }.ifBlank { value }

        private fun String.escapeXml(): String = replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        private fun String.unescapeXml(): String = replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        private data class StoredObject(val etag: String, val data: String)
    }
}
