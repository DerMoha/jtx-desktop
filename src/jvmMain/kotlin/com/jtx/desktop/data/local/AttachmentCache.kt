package com.jtx.desktop.data.local

import com.jtx.desktop.domain.model.EntryAttachment
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

object AttachmentCache {
    private val cacheDirectory: Path = Path.of(
        System.getProperty("user.home"),
        ".jtx-desktop",
        "attachments"
    )

    fun cacheUri(uri: String): EntryAttachment {
        val source = runCatching { Path.of(URI(uri)) }.getOrNull()
        if (source == null || !Files.isRegularFile(source)) return EntryAttachment(uri = uri)

        Files.createDirectories(cacheDirectory)
        val filename = source.fileName?.toString()
        val target = cacheDirectory.resolve("${UUID.randomUUID()}_${filename ?: "attachment"}")
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

        return EntryAttachment(
            uri = uri,
            filename = filename,
            mimeType = Files.probeContentType(source),
            size = Files.size(source),
            localPath = target.toAbsolutePath().toString()
        )
    }
}
