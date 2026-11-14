package com.chiyuri.desktop

import java.util.Locale

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

internal fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024f * 1024f))
}

internal fun isValidFileName(value: String): Boolean {
    val name = value.trim()
    return name.isNotBlank() && name.length <= 255 && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') &&
        name.none { it.code < 0x20 || it.code == 0x7F }
}

internal fun isValidWorkspaceName(value: String): Boolean {
    val name = value.trim()
    return name.isNotBlank() && name.length <= 24 &&
        name.none { it.code < 0x20 || it.code == 0x7F }
}
