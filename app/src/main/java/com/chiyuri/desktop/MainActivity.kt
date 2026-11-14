package com.chiyuri.desktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF09090B)
private val Panel = Color(0xEE17171C)
private val PanelSoft = Color(0xCC24242B)
private val Crimson = Color(0xFFD7193F)
private val MainText = Color(0xFFF4F1F2)
private val Muted = Color(0xFFAAA4A8)

data class LaunchApp(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChiyuriTheme { DesktopScreen() } }
    }
}

@Composable
private fun ChiyuriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Crimson,
            onPrimary = Color.White,
            background = Night,
            surface = Panel,
            onSurface = MainText,
            secondary = Color(0xFF8E102B)
        ),
        content = content
    )
}

@Composable
private fun DesktopScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf(loadApps(context)) }
    var pinnedPackages by remember { mutableStateOf(loadPinnedPackages(context, apps)) }
    var desktopPackages by remember { mutableStateOf(loadDesktopPackages(context)) }
    var startOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(nowTime()) }
    var date by remember { mutableStateOf(nowDate()) }
    var filesOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var wallpaperStyle by remember { mutableIntStateOf(loadWallpaperStyle(context)) }
    var wallpaperUri by remember { mutableStateOf(loadWallpaperUri(context)) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            wallpaperUri = uri.toString()
            context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putString("wallpaper_uri", uri.toString()).apply()
        }
    }
    val wallpaperBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, wallpaperUri) {
        value = withContext(Dispatchers.IO) {
            wallpaperUri?.let { saved -> runCatching {
                context.contentResolver.openInputStream(Uri.parse(saved))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull() }
        }
    }

    LaunchedEffect(startOpen) {
        if (startOpen) apps = loadApps(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowTime()
            date = nowDate()
            delay(1000)
        }
    }

    val visibleApps = apps.filter { it.label.contains(search, ignoreCase = true) }
    val pinnedApps = apps.filter { it.packageName in pinnedPackages }

    fun togglePin(app: LaunchApp) {
        val updated = if (app.packageName in pinnedPackages) pinnedPackages - app.packageName
            else pinnedPackages + app.packageName
        pinnedPackages = updated
        savePinnedPackages(context, updated)
    }

    fun toggleDesktopShortcut(app: LaunchApp) {
        val updated = if (app.packageName in desktopPackages) desktopPackages - app.packageName
            else desktopPackages + app.packageName
        desktopPackages = updated
        saveDesktopPackages(context, updated)
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (wallpaperBitmap != null) {
            Image(bitmap = wallpaperBitmap!!, contentDescription = "Обои рабочего стола", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(wallpaperBrush(wallpaperStyle)))
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 26.dp)
                .width(3.dp).height(92.dp).background(Crimson, RoundedCornerShape(4.dp))
        )

        Column(Modifier.fillMaxSize().padding(bottom = 58.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CHIYURI", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("DESKTOP", color = Crimson, fontSize = 11.sp, letterSpacing = 3.sp)
                }
                Surface(color = PanelSoft, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, null, tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.BatteryFull, null, tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("${batteryPercent(context)}%", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Рабочий стол", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("Твоя система. Твои правила.", color = Muted, fontSize = 13.sp)
                        Text("Ярлыки рабочего стола", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                        val desktopApps = apps.filter { it.packageName in desktopPackages }
                        if (desktopApps.isEmpty()) {
                            Text("Здесь пока пусто. Удерживай приложение в меню «Пуск», чтобы добавить ярлык.", color = Muted, fontSize = 11.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                desktopApps.chunked(4).forEach { rowApps ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowApps.forEach { app ->
                                            DesktopAppShortcut(
                                                app = app,
                                                onClick = { launchApp(context, app) },
                                                onLongClick = { toggleDesktopShortcut(app) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DesktopShortcut("Приложения", Icons.Filled.Apps) { startOpen = true }
                            DesktopShortcut("Файлы", Icons.Filled.Apps) { filesOpen = true; startOpen = false; settingsOpen = false }
                            DesktopShortcut("Оформление", Icons.Filled.Settings) { settingsOpen = true; filesOpen = false; startOpen = false }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(clock, color = MainText, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text(date, color = Muted, fontSize = 13.sp)
                    }
                }

                if (filesOpen) {
                    Surface(modifier = Modifier.widthIn(min = 245.dp, max = 380.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
                        FileExplorerPanel(context = context, onClose = { filesOpen = false })
                    }
                }
                if (settingsOpen) {
                    Surface(modifier = Modifier.widthIn(min = 245.dp, max = 340.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Оформление Chiyuri", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Фон рабочего стола", color = Muted, fontSize = 12.sp)
                            OutlinedButton(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) { Text("Выбрать изображение") }
                            if (wallpaperUri != null) TextButton(onClick = {
                                wallpaperUri = null
                                context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().remove("wallpaper_uri").apply()
                            }) { Text("Убрать изображение", color = Crimson) }
                            listOf("Кровавый градиент", "Чёрный", "Багровое свечение").forEachIndexed { index, label ->
                                Row(Modifier.fillMaxWidth().background(if (wallpaperStyle == index) Color(0xFF35101A) else PanelSoft, RoundedCornerShape(10.dp)).clickable {
                                    wallpaperStyle = index
                                    saveWallpaperStyle(context, index)
                                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = MainText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    if (wallpaperStyle == index) Text("✓", color = Crimson)
                                }
                            }
                            Text("Системные настройки Android", color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }) { Text("Открыть настройки устройства", color = Crimson) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { settingsOpen = false }) { Text("Закрыть", color = Crimson) }
                        }
                    }
                }
                if (startOpen) {
                    Surface(
                        modifier = Modifier.widthIn(min = 245.dp, max = 340.dp).fillMaxHeight(),
                        color = Panel,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 8.dp
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Пуск", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().background(PanelSoft, RoundedCornerShape(10.dp)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Crimson),
                                    decorationBox = { inner ->
                                        Box {
                                            if (search.isEmpty()) Text("Поиск приложений", color = Muted, fontSize = 14.sp)
                                            inner()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Все приложения", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                            Text("Долгое нажатие: закрепить на панели задач. Для ярлыка рабочего стола используй отдельную кнопку ниже.", color = Muted, fontSize = 10.sp)
                            Spacer(Modifier.height(6.dp))
                            if (visibleApps.isEmpty()) {
                                Box(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (search.isBlank()) "Нет доступных приложений" else "Ничего не найдено",
                                        color = Muted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 76.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(visibleApps, key = { it.packageName }) { app ->
                                        AppTile(
                                            app = app,
                                            pinned = app.packageName in pinnedPackages,
                                            desktopShortcut = app.packageName in desktopPackages,
                                            onClick = {
                                                launchApp(context, app)
                                                startOpen = false
                                                search = ""
                                            },
                                            onLongClick = { togglePin(app) },
                                            onDesktopToggle = { toggleDesktopShortcut(app) }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { startOpen = false; search = "" }) {
                                Text("Закрыть", color = Crimson)
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp)
                .background(Color(0xF20D0D11)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (startOpen) Color(0xFF35101A) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(42.dp).clickable { startOpen = !startOpen }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("C", color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pinnedApps.forEach { app ->
                    Box(
                        Modifier.size(42.dp).combinedClickable(
                            onClick = { launchApp(context, app) },
                            onLongClick = { togglePin(app) }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = remember(app.packageName) { app.icon.toBitmap(72, 72).asImageBitmap() },
                            contentDescription = "${app.label}, закреплено",
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(clock, color = MainText, fontSize = 12.sp)
                Text(date, color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DesktopShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(92.dp).background(PanelSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Crimson, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, color = MainText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppTile(
    app: LaunchApp,
    pinned: Boolean,
    desktopShortcut: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDesktopToggle: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(PanelSoft, RoundedCornerShape(12.dp)).padding(vertical = 10.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() },
            contentDescription = app.label,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(app.label, color = MainText, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (pinned) "На панели" else "Закрепить", color = if (pinned) Crimson else Muted, fontSize = 8.sp)
            Text("·", color = Muted, fontSize = 8.sp)
            TextButton(onClick = onDesktopToggle, contentPadding = PaddingValues(0.dp)) {
                Text(if (desktopShortcut) "Убрать ярлык" else "+ На стол", color = if (desktopShortcut) Crimson else Muted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun DesktopAppShortcut(app: LaunchApp, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() },
            contentDescription = app.label,
            modifier = Modifier.size(38.dp)
        )
        Text(app.label, color = MainText, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FileExplorerPanel(context: Context, onClose: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE) }
    var root by remember {
        mutableStateOf(prefs.getString("file_tree_uri", null)?.let { saved ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(saved)) }.getOrNull()
        })
    }
    var folderStack by remember { mutableStateOf(emptyList<DocumentFile>()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Выбери папку, к которой Chiyuri Desktop получит доступ") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                root = DocumentFile.fromTreeUri(context, uri)
                folderStack = emptyList()
                prefs.edit().putString("file_tree_uri", uri.toString()).apply()
                message = "Доступ к папке сохранён"
            }.onFailure { message = "Не удалось сохранить доступ: ${it.localizedMessage}" }
        }
    }
    val current = folderStack.lastOrNull() ?: root
    val entries = remember(current, refreshKey) {
        runCatching { current?.listFiles()?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase(Locale.getDefault()) ?: "" }).orEmpty() }
            .getOrElse { emptyList() }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Проводник", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Закрыть", color = Crimson) }
        }
        Text(message, color = Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { picker.launch(null) }) { Text(if (root == null) "Выбрать папку" else "Сменить папку") }
            if (folderStack.isNotEmpty()) OutlinedButton(onClick = { folderStack = folderStack.dropLast(1) }) { Text("Назад") }
        }
        if (root == null) {
            Spacer(Modifier.height(16.dp))
            Text("Android откроет системный выбор папки. Можно выбрать доступную папку во внутренней памяти или на SD-карте.", color = Muted, fontSize = 12.sp)
        } else if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Папка пуста или недоступна", color = Muted, fontSize = 12.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(entries, key = { it.uri.toString() }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().background(PanelSoft, RoundedCornerShape(9.dp)).clickable {
                            if (entry.isDirectory) folderStack = folderStack + entry
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
                        Text(if (entry.isDirectory) "▰" else "▤", color = Crimson, modifier = Modifier.padding(end = 9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name ?: "Без названия", color = MainText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.isDirectory) "Папка" else readableSize(entry.length()), color = Muted, fontSize = 9.sp)
                        }
                        if (!entry.isDirectory) TextButton(onClick = {
                            val deleted = runCatching { entry.delete() }.getOrDefault(false)
                            message = if (deleted) "Файл удалён" else "Не удалось удалить файл"
                            if (deleted) refreshKey++
                        }) { Text("Удалить", color = Crimson, fontSize = 10.sp) }
                    }
                }
            }
        }
        if (root != null) TextButton(onClick = {
            val created = runCatching { current?.createDirectory("Новая папка") != null }.getOrDefault(false)
            message = if (created) "Папка создана" else "Не удалось создать папку"
            if (created) refreshKey++
        }) { Text("＋ Новая папка", color = Crimson) }
    }
}

private fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024f * 1024f))
}

private fun loadWallpaperUri(context: Context): String? =
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getString("wallpaper_uri", null)

private fun loadWallpaperStyle(context: Context): Int =
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getInt("wallpaper_style", 0).coerceIn(0, 2)

private fun saveWallpaperStyle(context: Context, style: Int) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putInt("wallpaper_style", style.coerceIn(0, 2)).apply()
}

private fun wallpaperBrush(style: Int): Brush = when (style) {
    1 -> Brush.linearGradient(listOf(Color(0xFF050506), Color(0xFF101014), Color(0xFF050506)))
    2 -> Brush.radialGradient(listOf(Color(0xFF4A0718), Color(0xFF18050C), Night))
    else -> Brush.linearGradient(listOf(Color(0xFF1B080E), Night, Color(0xFF09090B)))
}

private fun loadApps(context: Context): List<LaunchApp> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            LaunchApp(info.loadLabel(pm).toString(), packageName, info.loadIcon(pm))
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun loadPinnedPackages(context: Context, apps: List<LaunchApp>): Set<String> {
    val prefs = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
    if (prefs.getBoolean("pins_initialized", false)) {
        return prefs.getStringSet("pinned_packages", emptySet())?.toSet() ?: emptySet()
    }
    val defaults = apps.take(5).map { it.packageName }.toSet()
    prefs.edit().putStringSet("pinned_packages", defaults).putBoolean("pins_initialized", true).apply()
    return defaults
}

private fun savePinnedPackages(context: Context, packages: Set<String>) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
        .edit().putStringSet("pinned_packages", packages.toSet()).apply()
}

private fun loadDesktopPackages(context: Context): Set<String> =
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
        .getStringSet("desktop_shortcuts", emptySet())?.toSet() ?: emptySet()

private fun saveDesktopPackages(context: Context, packages: Set<String>) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
        .edit().putStringSet("desktop_shortcuts", packages.toSet()).apply()
}

private fun launchApp(context: Context, app: LaunchApp) {
    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun nowTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate() = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
