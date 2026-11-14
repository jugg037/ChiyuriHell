package com.chiyuri.desktop

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@Composable
internal fun FileExplorerPanel(context: Context, onClose: () -> Unit, onMinimize: () -> Unit, onFocus: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE) }
    var root by remember {
        mutableStateOf(prefs.getString("file_tree_uri", null)?.let { saved ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(saved)) }.getOrNull()
        })
    }
    var folderStack by remember { mutableStateOf(emptyList<DocumentFile>()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Выбери папку, к которой Chiyuri Desktop получит доступ") }
    var fileSearch by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(prefs.getString("file_sort_mode", "name") ?: "name") }
    var archivePassword by remember { mutableStateOf("") }
    var showArchivePassword by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("Обработка…") }
    var archivePreview by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var renameTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var createFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("Новая папка") }
    var clipboardFile by remember { mutableStateOf<DocumentFile?>(null) }
    var clipboardMove by remember { mutableStateOf(false) }
    var actionMenuFor by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            root = DocumentFile.fromTreeUri(context, uri)
            folderStack = emptyList()
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.isSuccess || runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                prefs.edit().putString("file_tree_uri", uri.toString()).apply()
                message = "Доступ к папке сохранён"
            } else {
                prefs.edit().remove("file_tree_uri").apply()
                message = "Папка доступна только до закрытия приложения"
            }
        }
    }
    val current = folderStack.lastOrNull() ?: root

    LaunchedEffect(root) {
        if (root != null && root?.canRead() != true) {
            root = null
            folderStack = emptyList()
            prefs.edit().remove("file_tree_uri").apply()
            message = "Сохранённый доступ к папке истёк. Выбери папку заново."
        }
    }
    val entries = remember(current, refreshKey) {
        runCatching { current?.listFiles()?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase(Locale.getDefault()) ?: "" }).orEmpty() }
            .getOrElse { emptyList() }
    }
    val filteredEntries = entries.filter { it.name?.contains(fileSearch, ignoreCase = true) == true }
    val visibleEntries = when (sortMode) {
        "date" -> filteredEntries.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenByDescending { it.lastModified() })
        "size" -> filteredEntries.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenByDescending { it.length() })
        "type" -> filteredEntries.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().substringAfterLast(".", "").lowercase(Locale.getDefault()) }.thenBy { it.name.orEmpty().lowercase(Locale.getDefault()) })
        else -> filteredEntries.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase(Locale.getDefault()) })
    }

    fun previewArchive(entry: DocumentFile, format: String) {
        if (isBusy) return
        isBusy = true
        busyLabel = "Чтение состава $format…"
        message = busyLabel
        scope.launch(Dispatchers.IO) {
            val result = runCatching { listArchiveEntries(context, entry, format, archivePassword) }
            withContext(Dispatchers.Main) {
                isBusy = false
                archivePreview = result.fold(
                    { names -> if (names.isEmpty()) "Архив пуст" else names.joinToString("\n") },
                    { "Не удалось прочитать архив: ${it.localizedMessage}" }
                )
                message = if (result.isSuccess) "Состав архива готов" else "Не удалось прочитать архив"
            }
        }
    }

    fun startArchiveExtraction(entry: DocumentFile, format: String) {
        if (isBusy) return
        isBusy = true
        busyLabel = "Распаковка $format…"
        message = busyLabel
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (format) {
                    "ZIP" -> extractZipArchive(context, entry, current, archivePassword)
                    "7Z" -> extractSevenZArchive(context, entry, current, archivePassword)
                    else -> extractRarArchive(context, entry, current, archivePassword)
                }
            }
            withContext(Dispatchers.Main) {
                isBusy = false
                message = result.fold({ "$format распакован: $it файлов" }, { "Ошибка $format: ${it.localizedMessage}" })
                if (result.isSuccess) refreshKey++
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Проводник", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable(onClick = onFocus))
            TextButton(onClick = onMinimize) { Text("—", color = LocalAccent.current) }
            TextButton(enabled = !isBusy, onClick = onClose) { Text("×", color = LocalAccent.current) }
        }
        Text("Папка: ${current?.name ?: root?.name ?: "не выбрана"} · ${entries.size} элементов", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Доступ: ${when { root == null -> "не выбран"; root?.canWrite() == true -> "чтение и запись"; root?.canRead() == true -> "только чтение"; else -> "недоступен" }}", color = Muted, fontSize = 10.sp)
        Text(message, color = Muted, fontSize = 11.sp)
        if (isBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("$busyLabel Не закрывай проводник.", color = Muted, fontSize = 9.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Сортировка:", color = Muted, fontSize = 10.sp)
            TextButton(enabled = !isBusy, onClick = {
                sortMode = when (sortMode) { "name" -> "date"; "date" -> "size"; "size" -> "type"; else -> "name" }
                prefs.edit().putString("file_sort_mode", sortMode).apply()
            }) {
                Text(when (sortMode) { "date" -> "По дате"; "size" -> "По размеру"; "type" -> "По типу"; else -> "По имени" }, color = LocalAccent.current, fontSize = 10.sp)
            }
        }
        OutlinedTextField(value = fileSearch, onValueChange = { fileSearch = it }, enabled = !isBusy, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Поиск файлов") })
        OutlinedTextField(
            value = archivePassword,
            onValueChange = { archivePassword = it },
            enabled = !isBusy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showArchivePassword) VisualTransformation.None else PasswordVisualTransformation(),
            label = { Text("Пароль архива (если нужен)") },
            trailingIcon = {
                IconButton(onClick = { showArchivePassword = !showArchivePassword }) {
                    Icon(
                        if (showArchivePassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showArchivePassword) "Скрыть пароль" else "Показать пароль",
                        tint = Muted
                    )
                }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = !isBusy, onClick = { picker.launch(null) }) { Text(if (root == null) "Выбрать папку" else "Сменить папку") }
            if (folderStack.isNotEmpty()) OutlinedButton(enabled = !isBusy, onClick = { folderStack = folderStack.dropLast(1); fileSearch = "" }) { Text("Назад") }
            if (folderStack.isNotEmpty()) OutlinedButton(enabled = !isBusy, onClick = { folderStack = emptyList(); fileSearch = "" }) { Text("Корень") }
        }
        if (root == null) {
            Spacer(Modifier.height(16.dp))
            Text("Android откроет системный выбор папки. Можно выбрать доступную папку во внутренней памяти или на SD-карте.", color = Muted, fontSize = 12.sp)
        } else if (visibleEntries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (entries.isEmpty()) "Папка пуста или недоступна" else "Ничего не найдено", color = Muted, fontSize = 12.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                lazyListItems(visibleEntries, key = { it.uri.toString() }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().background(PanelSoft, RoundedCornerShape(9.dp)).clickable(enabled = !isBusy) {
                            if (entry.isDirectory) { folderStack = folderStack + entry; fileSearch = "" }
                            else runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(entry.uri, entry.type ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }.onFailure { message = "Не удалось открыть файл: ${it.localizedMessage}" }
                        }.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.isDirectory) "▰" else "▤", color = LocalAccent.current, modifier = Modifier.padding(end = 9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name ?: "Без названия", color = MainText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.isDirectory) "Папка" else readableSize(entry.length()), color = Muted, fontSize = 9.sp)
                        }
                        Box {
                            IconButton(enabled = !isBusy, onClick = { actionMenuFor = entry.uri }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Действия с элементом", tint = Muted)
                            }
                            DropdownMenu(
                                expanded = actionMenuFor == entry.uri,
                                onDismissRequest = { actionMenuFor = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Переименовать") },
                                    enabled = current?.canWrite() == true,
                                    onClick = {
                                        actionMenuFor = null
                                        renameTarget = entry
                                        renameValue = entry.name ?: ""
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Копировать") },
                                    onClick = {
                                        actionMenuFor = null
                                        clipboardFile = entry
                                        clipboardMove = false
                                        message = "Элемент добавлен в буфер копирования"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Вырезать") },
                                    enabled = entry.canWrite(),
                                    onClick = {
                                        actionMenuFor = null
                                        clipboardFile = entry
                                        clipboardMove = true
                                        message = "Элемент готов к переносу"
                                    }
                                )
                                if (!entry.isDirectory) {
                                    val archiveFormat = when {
                                        entry.name?.endsWith(".zip", ignoreCase = true) == true -> "ZIP"
                                        entry.name?.endsWith(".7z", ignoreCase = true) == true -> "7Z"
                                        entry.name?.endsWith(".rar", ignoreCase = true) == true -> "RAR"
                                        else -> null
                                    }
                                    if (archiveFormat != null) {
                                        DropdownMenuItem(
                                            text = { Text("Содержимое $archiveFormat") },
                                            onClick = {
                                                actionMenuFor = null
                                                previewArchive(entry, archiveFormat)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Распаковать $archiveFormat сюда") },
                                            enabled = current?.canWrite() == true,
                                            onClick = {
                                                actionMenuFor = null
                                                startArchiveExtraction(entry, archiveFormat)
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Удалить", color = LocalAccent.current) },
                                    enabled = current?.canWrite() == true,
                                    onClick = {
                                        actionMenuFor = null
                                        deleteTarget = entry
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (root != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !isBusy && current?.canWrite() == true, onClick = {
                    newFolderName = "Новая папка"
                    createFolderDialog = true
                }) { Text("＋ Новая папка", color = LocalAccent.current) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = !isBusy && clipboardFile != null && current?.canWrite() == true, onClick = {
                    val source = clipboardFile
                    val destination = current
                    if (source != null && destination != null) {
                        val moving = clipboardMove
                        busyLabel = if (moving) "Перенос элемента…" else "Копирование элемента…"
                        message = busyLabel
                        isBusy = true
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching {
                                if (moving && !source.canWrite()) throw IOException("Нет разрешения на удаление исходного элемента")
                                val copied = copyDocumentEntry(context, source, destination)
                                if (copied && moving && !deleteDocumentEntry(source)) throw IOException("Копия создана, но исходный элемент удалить не удалось")
                                copied
                            }
                            withContext(Dispatchers.Main) {
                                isBusy = false
                                message = result.fold({ if (it) "Элемент ${if (moving) "перенесён" else "скопирован"}" else "Элемент с таким именем уже существует" }, { "Операция не выполнена: ${it.localizedMessage}" })
                                if (result.getOrDefault(false)) {
                                    refreshKey++
                                    if (moving && clipboardFile?.uri == source.uri) {
                                        clipboardFile = null
                                        clipboardMove = false
                                    }
                                }
                            }
                        }
                    }
                }) { Text("Вставить", color = LocalAccent.current) }
            }
        }
        if (deleteTarget != null) {
            val target = deleteTarget
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Удалить элемент?") },
                text = { Text(if (target?.isDirectory == true) "Папка и всё её содержимое будут удалены без возможности восстановления." else "Файл будет удалён без возможности восстановления.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (target != null) {
                            busyLabel = "Удаление элемента…"
                            message = busyLabel
                            isBusy = true
                            scope.launch(Dispatchers.IO) {
                                val result = runCatching { deleteDocumentEntry(target) }
                                withContext(Dispatchers.Main) {
                                    isBusy = false
                                    message = result.fold({ if (it) "Элемент удалён" else "Не удалось удалить элемент" }, { "Ошибка удаления: ${it.localizedMessage}" })
                                    if (result.getOrDefault(false)) refreshKey++
                                    deleteTarget = null
                                }
                            }
                        }
                    }) { Text("Удалить", color = LocalAccent.current) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
            )
        }
        if (archivePreview != null) {
            AlertDialog(
                onDismissRequest = { archivePreview = null },
                title = { Text("Содержимое архива") },
                text = {
                    Text(
                        archivePreview.orEmpty(),
                        color = MainText,
                        fontSize = 11.sp,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = { TextButton(onClick = { archivePreview = null }) { Text("Готово", color = LocalAccent.current) } }
            )
        }
        if (createFolderDialog) {
            AlertDialog(
                onDismissRequest = { createFolderDialog = false },
                title = { Text("Создать папку") },
                text = {
                    OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, singleLine = true, label = { Text("Имя папки") })
                },
                confirmButton = {
                    TextButton(onClick = {
                        val cleanName = newFolderName.trim()
                        val validName = cleanName.isNotBlank() && cleanName.length <= 255 && cleanName != "." && cleanName != ".." &&
                            !cleanName.contains('/') && !cleanName.contains('\\') && cleanName.none { it.code < 0x20 || it.code == 0x7F }
                        val created = validName && current?.canWrite() == true &&
                            runCatching { current?.createDirectory(cleanName) != null }.getOrDefault(false)
                        message = when {
                            !validName -> "Недопустимое имя папки"
                            created -> "Папка создана"
                            else -> "Не удалось создать папку"
                        }
                        if (created) refreshKey++
                        createFolderDialog = false
                    }) { Text("Создать", color = LocalAccent.current) }
                },
                dismissButton = { TextButton(onClick = { createFolderDialog = false }) { Text("Отмена") } }
            )
        }
        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Переименовать") },
                text = {
                    OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Новое имя") })
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = renameTarget
                        val cleanName = renameValue.trim()
                        val validName = cleanName.isNotBlank() && cleanName.length <= 255 && cleanName != "." && cleanName != ".." &&
                            !cleanName.contains('/') && !cleanName.contains('\\') && cleanName.none { it.code < 0x20 || it.code == 0x7F }
                        val renamed = target != null && validName && target.canWrite() &&
                            runCatching { target.renameTo(cleanName) }.getOrDefault(false)
                        message = when {
                            !validName -> "Недопустимое имя элемента"
                            renamed -> "Имя изменено"
                            else -> "Не удалось переименовать"
                        }
                        if (renamed) refreshKey++
                        renameTarget = null
                    }) { Text("Сохранить", color = LocalAccent.current) }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
            )
        }
    }
}

private data class DeleteBudget(var entries: Int = 0)

private fun countDocumentEntries(entry: DocumentFile, depth: Int = 0): Int {
    if (depth > 64) throw IOException("Слишком глубокая структура папок")
    if (!entry.isDirectory) return 1
    return 1 + entry.listFiles().sumOf { countDocumentEntries(it, depth + 1) }
}

private fun deleteDocumentEntry(entry: DocumentFile): Boolean {
    if (countDocumentEntries(entry) > 1000) throw IOException("Удаление ограничено 1000 элементами")
    return deleteDocumentEntry(entry, DeleteBudget(), 0)
}

private fun deleteDocumentEntry(entry: DocumentFile, budget: DeleteBudget, depth: Int): Boolean {
    budget.entries++
    if (budget.entries > 1000) throw IOException("Удаление ограничено 1000 элементами")
    if (depth > 64) throw IOException("Слишком глубокая структура папок")
    if (entry.isDirectory) {
        entry.listFiles().forEach { child ->
            if (!deleteDocumentEntry(child, budget, depth + 1)) throw IOException("Не удалось удалить ${child.name ?: "элемент"}")
        }
    }
    return entry.delete()
}

private data class CopyBudget(var entries: Int = 0, var bytes: Long = 0L)

private fun copyDocumentEntry(
    context: Context,
    source: DocumentFile,
    destination: DocumentFile,
    budget: CopyBudget,
    created: MutableList<DocumentFile>,
    depth: Int = 0
): DocumentFile {
    budget.entries++
    if (budget.entries > 1000) throw IOException("В операции больше 1000 элементов")
    if (depth > 64) throw IOException("Слишком глубокая структура папок")
    val name = source.name?.substringAfterLast('/')?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() && it.length <= 255 && it != "." && it != ".." && it.none { character -> character.code < 0x20 || character.code == 0x7F } }
        ?: throw IOException("Недопустимое имя элемента")
    if (destination.findFile(name) != null) throw IOException("Элемент с именем $name уже существует")
    if (source.isDirectory) {
        val targetDirectory = destination.createDirectory(name)
            ?: throw IOException("Не удалось создать папку $name")
        created += targetDirectory
        source.listFiles().forEach { child ->
            copyDocumentEntry(context, child, targetDirectory, budget, created, depth + 1)
        }
        return targetDirectory
    }
    val target = destination.createFile(source.type ?: "application/octet-stream", name)
        ?: throw IOException("Не удалось создать файл $name")
    created += target
    context.contentResolver.openInputStream(source.uri).use { input ->
        requireNotNull(input) { "Не удалось прочитать исходный файл" }
        context.contentResolver.openOutputStream(target.uri).use { output ->
            requireNotNull(output) { "Не удалось записать файл" }
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                budget.bytes += count
                if (budget.bytes > 512L * 1024L * 1024L) throw IOException("Копирование ограничено 512 МБ за операцию")
                output.write(buffer, 0, count)
            }
        }
    }
    return target
}

private fun copyDocumentEntry(context: Context, source: DocumentFile, destination: DocumentFile): Boolean {
    if (destination.findFile(source.name ?: "Без имени") != null) return false
    if (source.isDirectory) {
        var ancestor: DocumentFile? = destination
        while (ancestor != null) {
            if (ancestor.uri == source.uri) throw IOException("Нельзя копировать папку внутрь самой себя")
            ancestor = ancestor.parentFile
        }
        val sourceId = runCatching { DocumentsContract.getDocumentId(source.uri) }.getOrNull()
        val destinationId = runCatching { DocumentsContract.getDocumentId(destination.uri) }.getOrNull()
        if (sourceId != null && destinationId != null &&
            (destinationId == sourceId || destinationId.startsWith("$sourceId/"))) {
            throw IOException("Нельзя копировать папку внутрь самой себя")
        }
    }
    val created = mutableListOf<DocumentFile>()
    return try {
        copyDocumentEntry(context, source, destination, CopyBudget(), created)
        true
    } catch (error: Exception) {
        created.asReversed().forEach { runCatching { it.delete() } }
        throw error
    }
}
