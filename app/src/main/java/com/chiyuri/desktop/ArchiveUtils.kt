package com.chiyuri.desktop

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.io.OutputStream
import net.lingala.zip4j.ZipFile as Zip4jFile

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

private fun copyArchiveToTemp(context: Context, archive: DocumentFile, prefix: String, suffix: String): File {
    val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
    try {
        context.contentResolver.openInputStream(archive.uri).use { input ->
            requireNotNull(input) { "Не удалось прочитать архив" }
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 512L * 1024L * 1024L) throw IOException("Архив больше 512 МБ")
                    output.write(buffer, 0, count)
                }
            }
        }
        return tempFile
    } catch (error: Exception) {
        tempFile.delete()
        throw error
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
internal fun isCompleteRarPartSequence(partNumbers: List<Int>): Boolean =
    partNumbers.isNotEmpty() && partNumbers.sorted() == (1..(partNumbers.maxOrNull() ?: 0)).toList()

private fun copyRarSetToTemp(context: Context, archiveFile: DocumentFile): Pair<File, File> {
    val name = archiveFile.name ?: throw IOException("У RAR-архива отсутствует имя")
    val partPattern = Regex("^(.*)\\.part(\\d+)\\.rar$", RegexOption.IGNORE_CASE)
    val selectedPart = partPattern.matchEntire(name)
    val volumes = if (selectedPart != null) {
        val prefix = selectedPart.groupValues[1]
        val parent = archiveFile.parentFile
        val found = parent?.listFiles().orEmpty().mapNotNull { item ->
            if (!item.isFile) return@mapNotNull null
            val match = partPattern.matchEntire(item.name ?: "") ?: return@mapNotNull null
            if (!match.groupValues[1].equals(prefix, ignoreCase = true)) return@mapNotNull null
            val number = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            number to item
        }.sortedBy { it.first }
        if (!isCompleteRarPartSequence(found.map { it.first })) {
            throw IOException("Неполный набор томов RAR: нужны части начиная с part1")
        }
        found.map { it.second }
    } else {
        listOf(archiveFile)
    }

    if (volumes.size > 1000) throw IOException("В наборе RAR больше 1000 томов")
    val tempDir = File(context.cacheDir, "chiyuri-rar-${System.nanoTime()}")
    if (!tempDir.mkdirs()) throw IOException("Не удалось создать временную папку для RAR")
    try {
        var total = 0L
        val copied = volumes.map { volume ->
            val volumeName = volume.name?.substringAfterLast('/')?.substringAfterLast('\\')
                ?: throw IOException("У тома RAR отсутствует имя")
            val target = File(tempDir, volumeName)
            context.contentResolver.openInputStream(volume.uri).use { input ->
                requireNotNull(input) { "Не удалось прочитать том $volumeName" }
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 512L * 1024L * 1024L) throw IOException("Набор RAR больше 512 МБ")
                        output.write(buffer, 0, count)
                    }
                }
            }
            target
        }
        return tempDir to copied.first()
    } catch (error: Exception) {
        tempDir.deleteRecursively()
        throw error
    }
}

internal fun extractRarArchive(context: Context, archiveFile: DocumentFile, destination: DocumentFile?, password: String): Int {
    require(destination != null && destination.canWrite()) { "Нет доступа на запись в текущую папку" }
    val (tempDir, firstVolume) = copyRarSetToTemp(context, archiveFile)
    try {
        var extracted = 0
        var processed = 0
        var totalBytes = 0L
        val options = ArchiveOptions.builder().maxDictionarySize(128L * 1024L * 1024L).apply {
            if (password.isNotEmpty()) password(password.toCharArray())
        }.build()
        Archive(firstVolume, options).use { rar ->
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
        return extracted
    } finally {
        tempDir.deleteRecursively()
    }
}

internal fun extractZipArchive(context: Context, archive: DocumentFile, destination: DocumentFile?, password: String): Int {
    require(destination != null && destination.canWrite()) { "Нет доступа на запись в текущую папку" }
    val tempFile = copyArchiveToTemp(context, archive, "chiyuri-zip-", ".zip")
    try {
        val zip = if (password.isEmpty()) Zip4jFile(tempFile) else Zip4jFile(tempFile, password.toCharArray())
        if (zip.isEncrypted() && password.isEmpty()) throw IOException("ZIP-архив защищён паролем")
        val headers = zip.getFileHeaders()
        if (headers.size > 1000) throw IOException("В архиве больше 1000 элементов")
        var extracted = 0
        var totalBytes = 0L
        headers.forEach { header ->
            if (!header.isDirectory) {
                val safeName = safeArchiveEntryName(header.fileName)
                if (safeName.isNotBlank() && safeName != "." && safeName != ".." && destination.findFile(safeName) == null) {
                    val target = destination.createFile("application/octet-stream", safeName)
                    if (target != null) {
                        try {
                            zip.getInputStream(header).use { input ->
                                context.contentResolver.openOutputStream(target.uri).use { output ->
                                    requireNotNull(output) { "Не удалось создать файл: $safeName" }
                                    val bounded = LimitedOutputStream(output, 256L * 1024L * 1024L - totalBytes)
                                    input.copyTo(bounded)
                                    totalBytes += bounded.bytesWritten
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
        }
        return extracted
    } finally {
        tempFile.delete()
    }
}

internal fun listArchiveEntries(context: Context, archive: DocumentFile, format: String, password: String): List<String> {
    val names = mutableListOf<String>()
    when (format) {
        "ZIP" -> {
            val tempFile = copyArchiveToTemp(context, archive, "chiyuri-list-", ".zip")
            try {
                val zip = if (password.isEmpty()) Zip4jFile(tempFile) else Zip4jFile(tempFile, password.toCharArray())
                if (zip.isEncrypted() && password.isEmpty()) throw IOException("ZIP-архив защищён паролем")
                zip.getFileHeaders().take(1000).forEach { header ->
                    names += (if (header.isDirectory) "📁 " else "📄 ") + header.fileName
                }
            } finally {
                tempFile.delete()
            }
        }
        "7Z" -> {
            val tempFile = File.createTempFile("chiyuri-list-", ".7z", context.cacheDir)
            try {
                context.contentResolver.openInputStream(archive.uri).use { input ->
                    requireNotNull(input) { "Не удалось прочитать 7Z-архив" }
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 512L * 1024L * 1024L) throw IOException("Архив больше 512 МБ")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                SevenZFile.builder().setFile(tempFile).setMaxMemoryLimitKiB(64 * 1024).apply {
                    if (password.isNotEmpty()) setPassword(password.toCharArray())
                }.get().use { seven ->
                    var entry = seven.nextEntry
                    while (entry != null && names.size < 1000) {
                        names += (if (entry.isDirectory) "📁 " else "📄 ") + (entry.name ?: "Без имени")
                        entry = seven.nextEntry
                    }
                }
            } finally {
                tempFile.delete()
            }
        }
        else -> {
            val (tempDir, firstVolume) = copyRarSetToTemp(context, archive)
            try {
                val options = ArchiveOptions.builder().maxDictionarySize(128L * 1024L * 1024L).apply {
                    if (password.isNotEmpty()) password(password.toCharArray())
                }.build()
                Archive(firstVolume, options).use { rar ->
                    rar.getFileHeaders().take(1000).forEach { header ->
                        names += (if (header.isDirectory) "📁 " else "📄 ") + (header.fileName ?: "Без имени")
                    }
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
    if (names.size == 1000) names += "… список ограничен первыми 1000 элементами"
    return names
}
