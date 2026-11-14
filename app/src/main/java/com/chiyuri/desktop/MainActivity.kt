package com.chiyuri.desktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import java.io.File
import java.io.OutputStream
import android.net.Uri
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.app.ActivityManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream
import java.io.IOException
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
    val isWideLayout = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    var apps by remember { mutableStateOf(loadApps(context)) }
    var pinnedPackages by remember { mutableStateOf(loadPinnedPackages(context, apps)) }
    var workspace by remember { mutableIntStateOf(loadWorkspace(context)) }
    var desktopPackages by remember { mutableStateOf(loadDesktopPackages(context, workspace)) }
    var startOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(nowTime()) }
    var date by remember { mutableStateOf(nowDate()) }
    var filesOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf("files") }
    var wallpaperStyle by remember { mutableIntStateOf(loadWallpaperStyle(context)) }
    var wallpaperScale by remember { mutableIntStateOf(loadWallpaperScale(context)) }
    val focusRequester = remember { FocusRequester() }
    var recentPackages by remember { mutableStateOf(loadRecentPackages(context)) }
    var lastLaunchedPackage by remember { mutableStateOf(loadLastLaunched(context)) }
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

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

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

    fun openApp(app: LaunchApp) {
        lastLaunchedPackage = app.packageName
        recentPackages = recordRecentApp(context, app.packageName)
        launchApp(context, app)
    }

    fun openAppAdjacent(app: LaunchApp) {
        lastLaunchedPackage = app.packageName
        recentPackages = recordRecentApp(context, app.packageName)
        launchAppAdjacent(context, app)
    }

    fun switchWorkspace(index: Int) {
        workspace = index.coerceIn(0, 2)
        desktopPackages = loadDesktopPackages(context, workspace)
        context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putInt("active_workspace", workspace).apply()
    }

    fun toggleDesktopShortcut(app: LaunchApp) {
        val updated = if (app.packageName in desktopPackages) desktopPackages - app.packageName
            else desktopPackages + app.packageName
        desktopPackages = updated
        saveDesktopPackages(context, workspace, updated)
    }

    Box(
        Modifier.fillMaxSize().background(Night).focusRequester(focusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && event.key == Key.One -> {
                        switchWorkspace(0)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Two -> {
                        switchWorkspace(1)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Three -> {
                        switchWorkspace(2)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Spacebar -> {
                        startOpen = !startOpen
                        true
                    }
                    event.isCtrlPressed && event.isAltPressed && event.key == Key.F -> {
                        filesOpen = true
                        if (!isWideLayout) settingsOpen = false
                        activePanel = "files"
                        startOpen = false
                        true
                    }
                    event.isCtrlPressed && event.isAltPressed && event.key == Key.S -> {
                        settingsOpen = true
                        if (!isWideLayout) filesOpen = false
                        activePanel = "settings"
                        startOpen = false
                        true
                    }
                    event.isAltPressed && event.key == Key.Tab -> {
                        val nextApp = apps.firstOrNull { it.packageName in recentPackages && it.packageName != lastLaunchedPackage }
                        if (nextApp != null) openApp(nextApp)
                        nextApp != null
                    }
                    event.key == Key.Escape -> {
                        when {
                            startOpen -> startOpen = false
                            activePanel == "files" && filesOpen -> filesOpen = false
                            activePanel == "settings" && settingsOpen -> settingsOpen = false
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        if (wallpaperBitmap != null) {
            Image(bitmap = wallpaperBitmap!!, contentDescription = "Обои рабочего стола", modifier = Modifier.fillMaxSize(), contentScale = when (wallpaperScale) { 1 -> ContentScale.Fit; 2 -> ContentScale.FillBounds; else -> ContentScale.Crop })
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
                        Icon(Icons.Filled.Wifi, null, tint = if (networkAvailable(context)) Color(0xFF67D69A) else Muted, modifier = Modifier.size(16.dp))
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
                        Text("Рабочий стол ${workspace + 1}", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (0..2).forEach { index ->
                                TextButton(onClick = { switchWorkspace(index) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Стол ${index + 1}", color = if (workspace == index) Crimson else Muted, fontSize = 10.sp)
                                }
                            }
                        }
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
                                                onClick = { openApp(app) },
                                                onLongClick = { toggleDesktopShortcut(app) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DesktopShortcut("Приложения", Icons.Filled.Apps) { startOpen = true }
                            DesktopShortcut("Файлы", Icons.Filled.FolderOpen) { filesOpen = true; if (!isWideLayout) settingsOpen = false; activePanel = "files"; startOpen = false }
                            DesktopShortcut("Оформление", Icons.Filled.Palette) { settingsOpen = true; if (!isWideLayout) filesOpen = false; activePanel = "settings"; startOpen = false }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(clock, color = MainText, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text(date, color = Muted, fontSize = 13.sp)
                    }
                }

                if (filesOpen) {
                    Surface(modifier = Modifier.widthIn(min = 245.dp, max = 380.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (activePanel == "files") Crimson else Color(0xFF343038)), tonalElevation = 8.dp) {
                        FileExplorerPanel(context = context, onClose = { filesOpen = false }, onFocus = { activePanel = "files" })
                    }
                }
                if (settingsOpen) {
                    Surface(modifier = Modifier.widthIn(min = 245.dp, max = 340.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (activePanel == "settings") Crimson else Color(0xFF343038)), tonalElevation = 8.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Оформление Chiyuri", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { activePanel = "settings" })
                            Text("Фон рабочего стола", color = Muted, fontSize = 12.sp)
                            Text("Масштаб изображения", color = Muted, fontSize = 12.sp)
                            listOf("Заполнить", "Вписать", "Растянуть").forEachIndexed { index, label ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    wallpaperScale = index
                                    saveWallpaperScale(context, index)
                                }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = wallpaperScale == index, onClick = {
                                        wallpaperScale = index
                                        saveWallpaperScale(context, index)
                                    }, colors = RadioButtonDefaults.colors(selectedColor = Crimson))
                                    Text(label, color = MainText, fontSize = 12.sp)
                                }
                            }
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
                            Text("Сведения об устройстве", color = Muted, fontSize = 12.sp)
                            Text("Модель: ${Build.MANUFACTURER} ${Build.MODEL}", color = MainText, fontSize = 11.sp)
                            Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = MainText, fontSize = 11.sp)
                            Text("Оперативная память: ${availableMemory(context)} свободно", color = MainText, fontSize = 11.sp)
                            Text("Батарея: ${batteryPercent(context)}%", color = MainText, fontSize = 11.sp)
                            Text("Раздел данных: ${storageSummary()}", color = MainText, fontSize = 11.sp)
                            Text("Сеть: ${if (networkAvailable(context)) "подключена" else "нет подключения"}", color = MainText, fontSize = 11.sp)
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
                            val recentApps = apps.filter { it.packageName in recentPackages }
                            if (recentApps.isNotEmpty()) {
                                Text("Недавно запускались", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    recentApps.forEach { recent ->
                                        Text(recent.label, color = MainText, fontSize = 10.sp,
                                            modifier = Modifier.background(PanelSoft, RoundedCornerShape(8.dp)).clickable {
                                                openApp(recent); startOpen = false; search = ""
                                            }.padding(horizontal = 8.dp, vertical = 6.dp), maxLines = 1)
                                    }
                                }
                            }
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
                                    gridItems(visibleApps, key = { it.packageName }) { app ->
                                        AppTile(
                                            app = app,
                                            pinned = app.packageName in pinnedPackages,
                                            desktopShortcut = app.packageName in desktopPackages,
                                            onClick = {
                                                openApp(app)
                                                startOpen = false
                                                search = ""
                                            },
                                            onLongClick = { togglePin(app) },
                                            onDesktopToggle = { toggleDesktopShortcut(app) },
                                            onAdjacentClick = { openAppAdjacent(app) }
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
                Text("▤", color = if (filesOpen && activePanel == "files") Crimson else MainText, fontSize = 20.sp,
                    modifier = Modifier.clickable { filesOpen = true; if (!isWideLayout) settingsOpen = false; activePanel = "files" }.padding(horizontal = 9.dp, vertical = 8.dp))
                Text("⚙", color = if (settingsOpen && activePanel == "settings") Crimson else MainText, fontSize = 18.sp,
                    modifier = Modifier.clickable { settingsOpen = true; if (!isWideLayout) filesOpen = false; activePanel = "settings" }.padding(horizontal = 9.dp, vertical = 8.dp))
                pinnedApps.forEach { app ->
                    Box(
                        Modifier.size(42.dp).background(if (app.packageName == lastLaunchedPackage) Color(0xFF35101A) else Color.Transparent, RoundedCornerShape(9.dp)).combinedClickable(
                            onClick = { openApp(app) },
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
    onDesktopToggle: () -> Unit,
    onAdjacentClick: () -> Unit
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
        TextButton(onClick = onAdjacentClick, contentPadding = PaddingValues(0.dp)) {
            Text("Открыть рядом", color = Muted, fontSize = 8.sp)
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
private fun FileExplorerPanel(context: Context, onClose: () -> Unit, onFocus: () -> Unit) {
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
    var archivePassword by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var clipboardFile by remember { mutableStateOf<DocumentFile?>(null) }
    var clipboardMove by remember { mutableStateOf(false) }
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
    val visibleEntries = entries.filter { it.name?.contains(fileSearch, ignoreCase = true) == true }

    fun startArchiveExtraction(entry: DocumentFile, format: String) {
        message = "Распаковка $format…"
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (format) {
                    "ZIP" -> extractZipArchive(context, entry, current)
                    "7Z" -> extractSevenZArchive(context, entry, current, archivePassword)
                    else -> extractRarArchive(context, entry, current, archivePassword)
                }
            }
            withContext(Dispatchers.Main) {
                message = result.fold({ "$format распакован: $it файлов" }, { "Ошибка $format: ${it.localizedMessage}" })
                if (result.isSuccess) refreshKey++
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Проводник", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable(onClick = onFocus))
            TextButton(onClick = onClose) { Text("Закрыть", color = Crimson) }
        }
        Text("Папка: ${current?.name ?: root?.name ?: "не выбрана"} · ${entries.size} элементов", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(message, color = Muted, fontSize = 11.sp)
        OutlinedTextField(value = fileSearch, onValueChange = { fileSearch = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Поиск файлов") })
        OutlinedTextField(value = archivePassword, onValueChange = { archivePassword = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), label = { Text("Пароль архива (если нужен)") })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { picker.launch(null) }) { Text(if (root == null) "Выбрать папку" else "Сменить папку") }
            if (folderStack.isNotEmpty()) OutlinedButton(onClick = { folderStack = folderStack.dropLast(1) }) { Text("Назад") }
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
                        if (!entry.isDirectory) {
                            TextButton(onClick = { clipboardFile = entry; clipboardMove = false; message = "Файл добавлен в буфер копирования" }) {
                                Text("Копия", color = Muted, fontSize = 9.sp)
                            }
                            TextButton(onClick = { clipboardFile = entry; clipboardMove = true; message = "Файл готов к переносу" }) {
                                Text("Вырезать", color = Muted, fontSize = 9.sp)
                            }
                            TextButton(onClick = {
                                renameTarget = entry
                                renameValue = entry.name ?: ""
                            }) { Text("Имя", color = Muted, fontSize = 10.sp) }
                            when {
                                entry.name?.endsWith(".zip", ignoreCase = true) == true ->
                                    TextButton(onClick = { startArchiveExtraction(entry, "ZIP") }) { Text("ZIP", color = Crimson, fontSize = 10.sp) }
                                entry.name?.endsWith(".7z", ignoreCase = true) == true ->
                                    TextButton(onClick = { startArchiveExtraction(entry, "7Z") }) { Text("7Z", color = Crimson, fontSize = 10.sp) }
                                entry.name?.endsWith(".rar", ignoreCase = true) == true ->
                                    TextButton(onClick = { startArchiveExtraction(entry, "RAR") }) { Text("RAR", color = Crimson, fontSize = 10.sp) }
                            }
                            TextButton(onClick = {
                                val deleted = runCatching { entry.delete() }.getOrDefault(false)
                                message = if (deleted) "Файл удалён" else "Не удалось удалить файл"
                                if (deleted) refreshKey++
                            }) { Text("Удалить", color = Crimson, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        if (root != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val created = runCatching { current?.createDirectory("Новая папка") != null }.getOrDefault(false)
                    message = if (created) "Папка создана" else "Не удалось создать папку"
                    if (created) refreshKey++
                }) { Text("＋ Новая папка", color = Crimson) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = clipboardFile != null, onClick = {
                    val source = clipboardFile
                    val destination = current
                    if (source != null && destination != null) {
                        message = if (clipboardMove) "Перенос файла…" else "Копирование файла…"
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching {
                                val copied = copyDocumentFile(context, source, destination)
                                if (copied && clipboardMove && !source.delete()) throw IOException("Копия создана, но исходный файл удалить не удалось")
                                copied
                            }
                            withContext(Dispatchers.Main) {
                                message = result.fold({ if (it) "Файл ${if (clipboardMove) "перенесён" else "скопирован"}" else "Файл с таким именем уже существует" }, { "Операция не выполнена: ${it.localizedMessage}" })
                                if (result.getOrDefault(false)) {
                                    refreshKey++
                                    clipboardFile = null
                                }
                            }
                        }
                    }
                }) { Text("Вставить", color = Crimson) }
            }
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
                        val renamed = target != null && renameValue.isNotBlank() && runCatching { target.renameTo(renameValue.trim()) }.getOrDefault(false)
                        message = if (renamed) "Имя изменено" else "Не удалось переименовать"
                        if (renamed) refreshKey++
                        renameTarget = null
                    }) { Text("Сохранить", color = Crimson) }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
            )
        }
    }
}

private fun copyDocumentFile(context: Context, source: DocumentFile, destination: DocumentFile): Boolean {
    require(!source.isDirectory) { "Копирование папок пока не поддерживается" }
    val name = source.name ?: "file"
    if (destination.findFile(name) != null) return false
    val target = destination.createFile(source.type ?: "application/octet-stream", name)
        ?: throw IOException("Не удалось создать файл назначения")
    try {
        context.contentResolver.openInputStream(source.uri).use { input ->
            requireNotNull(input) { "Не удалось прочитать исходный файл" }
            context.contentResolver.openOutputStream(target.uri).use { output ->
                requireNotNull(output) { "Не удалось записать файл" }
                input.copyTo(output)
            }
        }
    } catch (error: Exception) {
        target.delete()
        throw error
    }
    return true
}

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

private fun safeArchiveEntryName(rawName: String): String =
    rawName.substringAfterLast('/').substringAfterLast('\\').trim()

private fun extractSevenZArchive(context: Context, archive: DocumentFile, destination: DocumentFile?, password: String): Int {
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
private fun extractRarArchive(context: Context, archiveFile: DocumentFile, destination: DocumentFile?, password: String): Int {
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

private fun extractZipArchive(context: Context, archive: DocumentFile, destination: DocumentFile?): Int {
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

private fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024f * 1024f))
}

private fun loadWallpaperScale(context: Context): Int =
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getInt("wallpaper_scale", 0).coerceIn(0, 2)

private fun saveWallpaperScale(context: Context, scale: Int) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putInt("wallpaper_scale", scale.coerceIn(0, 2)).apply()
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

private fun loadLastLaunched(context: Context): String? = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getString("last_launched_package", null)

private fun loadRecentPackages(context: Context): List<String> = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getString("recent_packages", "")
    ?.split("|")?.filter { it.isNotBlank() }?.take(8).orEmpty()

private fun recordRecentApp(context: Context, packageName: String): List<String> {
    val updated = (listOf(packageName) + loadRecentPackages(context).filterNot { it == packageName }).take(8)
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit()
        .putString("last_launched_package", packageName).putString("recent_packages", updated.joinToString("|")).apply()
    return updated
}

private fun storageSummary(): String {
    val stats = StatFs(Environment.getDataDirectory().path)
    val total = stats.blockCountLong * stats.blockSizeLong
    val free = stats.availableBlocksLong * stats.blockSizeLong
    return String.format(Locale.getDefault(), "%.1f / %.1f ГБ свободно", free / (1024.0 * 1024.0 * 1024.0), total / (1024.0 * 1024.0 * 1024.0))
}

private fun availableMemory(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return String.format(Locale.getDefault(), "%.1f ГБ", info.availMem / (1024.0 * 1024.0 * 1024.0))
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

private fun loadWorkspace(context: Context): Int = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getInt("active_workspace", 0).coerceIn(0, 2)

private fun loadDesktopPackages(context: Context, workspace: Int): Set<String> {
    val prefs = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
    val key = "desktop_shortcuts_$workspace"
    if (workspace == 0 && !prefs.contains(key)) return prefs.getStringSet("desktop_shortcuts", emptySet())?.toSet() ?: emptySet()
    return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
}

private fun saveDesktopPackages(context: Context, workspace: Int, packages: Set<String>) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
        .edit().putStringSet("desktop_shortcuts_$workspace", packages.toSet()).apply()
}

private fun launchApp(context: Context, app: LaunchApp) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent == null) {
        Toast.makeText(context, "Приложение больше недоступно", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Не удалось запустить приложение", Toast.LENGTH_SHORT).show() }
}

private fun launchAppAdjacent(context: Context, app: LaunchApp) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent == null) {
        Toast.makeText(context, "Приложение больше недоступно", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Не удалось открыть рядом. Попробуй сначала включить разделённый экран Android.", Toast.LENGTH_LONG).show()
    }
}

private fun networkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun nowTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate() = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
