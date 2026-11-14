package com.chiyuri.desktop

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
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

internal fun safeArchiveEntryPath(rawName: String): List<String>? {
    val normalized = rawName.trim().replace('\\', '/')
    if (normalized.isBlank() || normalized.startsWith("/") ||
        Regex("^[A-Za-z]:").containsMatchIn(normalized)) return null
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty() || segments.size > 64 ||
        segments.any { it == "." || it == ".." || it.length > 255 || it.any { character -> character.code < 0x20 || character.code == 0x7F } }) return null
    return segments
}

private fun createArchiveTarget(
    destination: DocumentFile,
    path: List<String>,
    isDirectory: Boolean,
    mimeType: String = "application/octet-stream"
): DocumentFile? {
    if (path.isEmpty()) return null
    var parent = destination
    path.dropLast(1).forEach { segment ->
        val existing = parent.findFile(segment)
        parent = when {
            existing == null -> parent.createDirectory(segment) ?: return null
            existing.isDirectory -> existing
            else -> return null
        }
    }
    val name = path.last()
    val existing = parent.findFile(name)
    return if (isDirectory) {
        when {
            existing?.isDirectory == true -> existing
            existing != null -> null
            else -> parent.createDirectory(name)
        }
    } else {
        if (existing != null) null else parent.createFile(mimeType, name)
    }
}

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
                val path = safeArchiveEntryPath(entry.name ?: "")
                if (path != null) {
                    val target = createArchiveTarget(destination, path, entry.isDirectory)
                    if (!entry.isDirectory && target != null) {
                        try {
                            context.contentResolver.openOutputStream(target.uri).use { output ->
                                requireNotNull(output) { "Не удалось создать файл: ${path.last()}" }
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
    val parent = archiveFile.parentFile
    val siblings = parent?.listFiles()?.filter { it.isFile }?.toMutableList() ?: mutableListOf()
    if (siblings.none { it.uri == archiveFile.uri }) siblings += archiveFile

    val partPattern = Regex("^(.*)\\.part(\\d+)\\.rar$", RegexOption.IGNORE_CASE)
    val selectedPart = partPattern.matchEntire(name)
    val volumes = if (selectedPart != null) {
        val prefix = selectedPart.groupValues[1]
        val found = siblings.mapNotNull { item ->
            val match = partPattern.matchEntire(item.name ?: "") ?: return@mapNotNull null
            if (!match.groupValues[1].equals(prefix, ignoreCase = true)) return@mapNotNull null
            val number = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            number to item
        }.sortedBy { it.first }
        if (!isCompleteRarPartSequence(found.map { it.first })) {
            throw IOException("Неполный набор RAR5: нужны все части начиная с part1")
        }
        found.map { it.second }
    } else {
        val oldPattern = Regex("^(.*)\\.([r-z])(\\d{2})$", RegexOption.IGNORE_CASE)
        val selectedOldPart = oldPattern.matchEntire(name)
        val baseName = when {
            selectedOldPart != null -> selectedOldPart.groupValues[1]
            name.endsWith(".rar", ignoreCase = true) -> name.dropLast(4)
            else -> return copySingleRarToTemp(context, archiveFile)
        }
        val firstVolume = siblings.firstOrNull { it.name.equals("$baseName.rar", ignoreCase = true) }
            ?: (if (selectedOldPart == null) archiveFile.takeIf { name.endsWith(".rar", true) } else null)
            ?: throw IOException("Не найден первый том $baseName.rar")
        val oldParts = siblings.mapNotNull { item ->
            val match = oldPattern.matchEntire(item.name ?: "") ?: return@mapNotNull null
            if (!match.groupValues[1].equals(baseName, ignoreCase = true)) return@mapNotNull null
            val letter = match.groupValues[2].lowercase(Locale.ROOT)[0]
            val number = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            ((letter - 'r') * 100 + number) to item
        }.sortedBy { it.first }
        if (oldParts.isNotEmpty() || selectedOldPart != null) {
            if (!isCompleteLegacyRarPartSequence(oldParts.map { it.first })) {
                throw IOException("Неполный набор RAR: отсутствует один из томов .r00/.s00 и далее")
            }
            listOf(firstVolume) + oldParts.map { it.second }
        } else {
            listOf(archiveFile)
        }
    }

    if (volumes.size > 1000) throw IOException("В наборе RAR больше 1000 томов")
    val tempDir = File(context.cacheDir, "chiyuri-rar-${System.nanoTime()}")
    if (!tempDir.mkdirs()) throw IOException("Не удалось создать временную папку для RAR")
    try {
        var total = 0L
        val copied = volumes.map { volume ->
            val volumeName = volume.name?.substringAfterLast('/')?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
                ?: throw IOException("У тома RAR недопустимое имя")
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

private fun copySingleRarToTemp(context: Context, archiveFile: DocumentFile): Pair<File, File> {
    val tempDir = File(context.cacheDir, "chiyuri-rar-${System.nanoTime()}")
    if (!tempDir.mkdirs()) throw IOException("Не удалось создать временную папку для RAR")
    try {
        val name = archiveFile.name?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: throw IOException("У RAR-архива недопустимое имя")
        val target = File(tempDir, name)
        context.contentResolver.openInputStream(archiveFile.uri).use { input ->
            requireNotNull(input) { "Не удалось прочитать RAR-архив" }
            target.outputStream().use { output ->
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
        return tempDir to target
    } catch (error: Exception) {
        tempDir.deleteRecursively()
        throw error
    }
}

internal fun isCompleteLegacyRarPartSequence(partNumbers: List<Int>): Boolean =
    partNumbers.isNotEmpty() && partNumbers.sorted() == (0..(partNumbers.maxOrNull() ?: -1)).toList()

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
                val path = safeArchiveEntryPath(header.fileName ?: "")
                if (path != null) {
                    val target = createArchiveTarget(destination, path, header.isDirectory)
                    if (!header.isDirectory && target != null) {
                        try {
                            context.contentResolver.openOutputStream(target.uri).use { output ->
                                requireNotNull(output) { "Не удалось создать файл: ${path.last()}" }
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
            val path = safeArchiveEntryPath(header.fileName)
            if (path != null) {
                val target = createArchiveTarget(destination, path, header.isDirectory)
                if (!header.isDirectory && target != null) {
                    try {
                        zip.getInputStream(header).use { input ->
                            context.contentResolver.openOutputStream(target.uri).use { output ->
                                requireNotNull(output) { "Не удалось создать файл: ${path.last()}" }
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
