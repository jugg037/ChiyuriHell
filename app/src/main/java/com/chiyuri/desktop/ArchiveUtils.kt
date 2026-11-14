package com.chiyuri.desktop

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

private class LimitedOutputStream(
    private val target: OutputStream,
    private val limit: Long
) : OutputStream() {
    var bytesWritten: Long = 0
        private set

    override fun write(value: Int) {
        if (bytesWritten + 1 > limit) throw IOException("Превышен лимит распаковки 256 МБ")
        target.write(value)
        bytesWritten++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (bytesWritten + length > limit) throw IOException("Превышен лимит распаковки 256 МБ")
        target.write(buffer, offset, length)
        bytesWritten += length
    }

    override fun flush() = target.flush()
}

internal fun reorderPinnedPackages(packages: List<String>, packageName: String, direction: Int): List<String> {
    val from = packages.indexOf(packageName)
    if (from < 0 || packages.isEmpty()) return packages
    val to = (from + direction).coerceIn(0, packages.lastIndex)
    if (from == to) return packages
    return packages.toMutableList().apply {
        val item = removeAt(from)
        add(to, item)
    }
}

internal fun safeArchiveEntryName(rawName: String): String =
    rawName.substringAfterLast('/').substringAfterLast('\\').trim()

internal fun extractSevenZArchive(context: Context, archive: DocumentFile, destination: DocumentFile?, password: String): Int {
    require(destination != null && destination.canWrite()) { "Нет доступа на запись в текущую папку" }
    val tempFile = File.createTempFile("chiyuri-7z-", ".7z", context.cacheDir)
    try {
        context.contentResolver.openInputStream(archive.uri).use { input ->
            requireNotNull(input) { "Не удалось прочитать 7Z-архив" }
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > 512L * 1024L * 1024L) throw IOException("Архив больше 512 МБ")
                    output.write(buffer, 0, count)
                }
            }
        }
        var extracted = 0
        var processed = 0
        var totalBytes = 0L
        SevenZFile.builder().setFile(tempFile).setMaxMemoryLimitKiB(64 * 1024).apply {
            if (password.isNotEmpty()) setPassword(password.toCharArray())
        }.get().use { seven ->
            var entry = seven.nextEntry
            while (entry != null) {
                processed++
                if (processed > 1000) throw IOException("В архиве больше 1000 элементов")
                if (!entry.isDirectory) {
                    val name = safeArchiveEntryName(entry.name ?: "")
                    if (name.isNotBlank() && name != "." && name != ".." && destination.findFile(name) == null) {
                        val target = destination.createFile("application/octet-stream", name)
                        if (target != null) {
                            try {
                                context.contentResolver.openOutputStream(target.uri).use { output ->
                                    requireNotNull(output) { "Не удалось создать файл: $name" }
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = seven.read(buffer)
                                        if (count < 0) break
                                        totalBytes += count
                                        if (totalBytes > 256L * 1024L * 1024L) throw IOException("Превышен лимит распаковки 256 МБ")
                                        output.write(buffer, 0, count)
                                    }
                                }
                                extracted++
                            } catch (error: Exception) {
                                target.delete()
                                throw error
                            }
                        }
                    }
                }
                entry = seven.nextEntry
            }
        }
        return extracted
    } finally {
        tempFile.delete()
    }
}

// Junrar is used for extraction only. Its UnRAR license prohibits using the code to develop a RAR-compatible archiver.
internal fun extractRarArchive(context: Context, archiveFile: DocumentFile, destination: DocumentFile?, password: String): Int {
    require(destination != null && destination.canWrite()) { "Нет доступа на запись в текущую папку" }
    var extracted = 0
    var processed = 0
    var totalBytes = 0L
    context.contentResolver.openInputStream(archiveFile.uri).use { input ->
        requireNotNull(input) { "Не удалось прочитать RAR-архив" }
        val options = ArchiveOptions.builder().maxDictionarySize(128L * 1024L * 1024L).apply {
            if (password.isNotEmpty()) password(password.toCharArray())
        }.build()
        Archive(input, options).use { rar ->
            var header = rar.nextFileHeader()
            while (header != null) {
                processed++
                if (processed > 1000) throw IOException("В архиве больше 1000 элементов")
                if (!header.isDirectory) {
                    val name = safeArchiveEntryName(header.fileName ?: "")
                    if (name.isNotBlank() && name != "." && name != ".." && destination.findFile(name) == null) {
                        val target = destination.createFile("application/octet-stream", name)
                        if (target != null) {
                            try {
                                context.contentResolver.openOutputStream(target.uri).use { output ->
                                    requireNotNull(output) { "Не удалось создать файл: $name" }
                                    val bounded = LimitedOutputStream(output, 256L * 1024L * 1024L - totalBytes)
                                    rar.extractFile(header, bounded)
                                    totalBytes += bounded.bytesWritten
                                }
                                extracted++
                            } catch (error: Exception) {
                                target.delete()
                                throw error
                            }
                        }
                    }
                }
                header = rar.nextFileHeader()
            }
        }
    }
    return extracted
}

internal fun extractZipArchive(context: Context, archive: DocumentFile, destination: DocumentFile?): Int {
    require(destination != null && destination.canWrite()) { "Нет доступа на запись в текущую папку" }
    var extracted = 0
    var processedEntries = 0
    var totalBytes = 0L
    context.contentResolver.openInputStream(archive.uri).use { raw ->
        requireNotNull(raw) { "Не удалось прочитать ZIP-архив" }
        ZipInputStream(raw).use { zip ->
            var item = zip.nextEntry
            while (item != null) {
                processedEntries++
                if (processedEntries > 1000) throw IOException("В архиве больше 1000 элементов")
                if (!item.isDirectory) {
                    val safeName = item.name.substringAfterLast('/').substringAfterLast('\\').trim()
                    if (safeName.isNotBlank() && safeName != "." && safeName != "..") {
                        if (destination.findFile(safeName) == null) {
                            val output = destination.createFile("application/octet-stream", safeName)
                            if (output != null) {
                                try {
                                    context.contentResolver.openOutputStream(output.uri).use { stream ->
                                        requireNotNull(stream) { "Не удалось создать файл: $safeName" }
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val count = zip.read(buffer)
                                            if (count < 0) break
                                            totalBytes += count
                                            if (totalBytes > 256L * 1024L * 1024L) throw IOException("Превышен лимит распаковки 256 МБ")
                                            stream.write(buffer, 0, count)
                                        }
                                    }
                                    extracted++
                                } catch (error: Exception) {
                                    output.delete()
                                    throw error
                                }
                            }
                        }
                    }
                }
                zip.closeEntry()
                item = zip.nextEntry
            }
        }
    }
    return extracted
}
