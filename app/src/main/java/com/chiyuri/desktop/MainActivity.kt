package com.chiyuri.desktop

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.Process
import android.os.SystemClock
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Night = Color(0xFF09090B)
private val Panel = Color(0xEE17171C)
internal val PanelSoft = Color(0xCC24242B)
private val DefaultCrimson = Color(0xFFD7193F)
internal val LocalAccent = staticCompositionLocalOf { DefaultCrimson }
internal val MainText = Color(0xFFF4F1F2)
internal val Muted = Color(0xFFAAA4A8)

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
            primary = DefaultCrimson,
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
    var processCpuPercent by remember { mutableIntStateOf(0) }
    var filesOpen by remember { mutableStateOf(false) }
    var filesMinimized by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsMinimized by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf("files") }
    var wallpaperStyle by remember { mutableIntStateOf(loadWallpaperStyle(context)) }
    var accentStyle by remember { mutableIntStateOf(loadAccentStyle(context)) }
    var rootStatus by remember { mutableStateOf("Не проверено") }
    var rootChecking by remember { mutableStateOf(false) }
    var usageAccessGranted by remember { mutableStateOf(hasUsageAccess(context)) }
    var activePackage by remember { mutableStateOf<String?>(null) }
    var usageSince by remember { mutableLongStateOf(System.currentTimeMillis() - 24L * 60L * 60L * 1000L) }
    val uiScope = rememberCoroutineScope()
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
            wallpaperUri?.let { saved -> runCatching { decodeWallpaper(context, saved) }.getOrNull() }
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    LaunchedEffect(startOpen) {
        if (startOpen) {
            val refreshed = loadApps(context)
            apps = refreshed
            val installedPackages = refreshed.map { it.packageName }.toSet()
            val validPins = pinnedPackages.filter { it in installedPackages }
            if (validPins != pinnedPackages) {
                pinnedPackages = validPins
                savePinnedPackages(context, validPins)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowTime()
            date = nowDate()
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        var previousCpu = Process.getElapsedCpuTime()
        var previousWall = SystemClock.elapsedRealtime()
        while (true) {
            delay(2000)
            val currentCpu = Process.getElapsedCpuTime()
            val currentWall = SystemClock.elapsedRealtime()
            val wallDelta = currentWall - previousWall
            processCpuPercent = if (wallDelta > 0) (((currentCpu - previousCpu) * 100L) / wallDelta).toInt().coerceIn(0, 100) else 0
            previousCpu = currentCpu
            previousWall = currentWall
        }
    }

    LaunchedEffect(Unit) {
        var previousPermission = usageAccessGranted
        while (true) {
            val granted = hasUsageAccess(context)
            if (granted && !previousPermission) {
                usageSince = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
                activePackage = null
            }
            usageAccessGranted = granted
            if (granted) {
                val snapshot = queryForegroundPackage(context, usageSince, activePackage)
                activePackage = snapshot.first
                usageSince = snapshot.second
            } else {
                activePackage = null
            }
            previousPermission = granted
            delay(2500)
        }
    }

    val visibleApps = apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    val pinnedApps = pinnedPackages.mapNotNull { packageName -> apps.firstOrNull { it.packageName == packageName } }

    fun togglePin(app: LaunchApp) {
        val updated = if (app.packageName in pinnedPackages) pinnedPackages.filterNot { it == app.packageName }
            else pinnedPackages + app.packageName
        pinnedPackages = updated
        savePinnedPackages(context, updated)
    }

    fun movePinned(app: LaunchApp, direction: Int) {
        val updated = reorderPinnedPackages(pinnedPackages, app.packageName, direction)
        if (updated != pinnedPackages) {
            pinnedPackages = updated
            savePinnedPackages(context, updated)
        }
    }

    fun openApp(app: LaunchApp) {
        if (launchApp(context, app)) {
            lastLaunchedPackage = app.packageName
            recentPackages = recordRecentApp(context, app.packageName)
        }
    }

    fun openAppAdjacent(app: LaunchApp) {
        if (launchAppAdjacent(context, app)) {
            lastLaunchedPackage = app.packageName
            recentPackages = recordRecentApp(context, app.packageName)
        }
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

    CompositionLocalProvider(LocalAccent provides accentColor(accentStyle)) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = LocalAccent.current, secondary = LocalAccent.current)) {
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
                        filesMinimized = false
                        if (!isWideLayout) settingsOpen = false
                        activePanel = "files"
                        startOpen = false
                        true
                    }
                    event.isCtrlPressed && event.isAltPressed && event.key == Key.S -> {
                        settingsOpen = true
                        settingsMinimized = false
                        if (!isWideLayout) filesOpen = false
                        activePanel = "settings"
                        startOpen = false
                        true
                    }
                    event.isAltPressed && event.key == Key.Tab -> {
                        val nextApp = recentPackages.asSequence().filter { it != lastLaunchedPackage }.mapNotNull { packageName -> apps.firstOrNull { it.packageName == packageName } }.firstOrNull()
                        if (nextApp != null) openApp(nextApp)
                        nextApp != null
                    }
                    event.key == Key.Escape -> {
                        when {
                            startOpen -> startOpen = false
                            activePanel == "files" && filesOpen -> { filesOpen = false; filesMinimized = false }
                            activePanel == "settings" && settingsOpen -> { settingsOpen = false; settingsMinimized = false }
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
                .width(3.dp).height(92.dp).background(LocalAccent.current, RoundedCornerShape(4.dp))
        )

        Column(Modifier.fillMaxSize().padding(bottom = 58.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CHIYURI", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("DESKTOP", color = LocalAccent.current, fontSize = 11.sp, letterSpacing = 3.sp)
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
                                    Text("Стол ${index + 1}", color = if (workspace == index) LocalAccent.current else Muted, fontSize = 10.sp)
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
                            DesktopShortcut("Файлы", Icons.Filled.FolderOpen) { filesOpen = true; filesMinimized = false; if (!isWideLayout) settingsOpen = false; activePanel = "files"; startOpen = false }
                            DesktopShortcut("Оформление", Icons.Filled.Palette) { settingsOpen = true; settingsMinimized = false; if (!isWideLayout) filesOpen = false; activePanel = "settings"; startOpen = false }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(clock, color = MainText, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text(date, color = Muted, fontSize = 13.sp)
                    }
                }

                if (filesOpen) {
                    Surface(modifier = if (filesMinimized) Modifier.size(0.dp) else Modifier.widthIn(min = 245.dp, max = 380.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (activePanel == "files") LocalAccent.current else Color(0xFF343038)), tonalElevation = 8.dp) {
                        FileExplorerPanel(context = context, onClose = { filesOpen = false; filesMinimized = false }, onMinimize = { filesMinimized = true }, onFocus = { activePanel = "files" })
                    }
                }
                if (settingsOpen) {
                    Surface(modifier = if (settingsMinimized) Modifier.size(0.dp) else Modifier.widthIn(min = 245.dp, max = 340.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (activePanel == "settings") LocalAccent.current else Color(0xFF343038)), tonalElevation = 8.dp) {
                        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Оформление Chiyuri", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { activePanel = "settings" })
                                TextButton(onClick = { settingsMinimized = true }) { Text("—", color = LocalAccent.current) }
                                TextButton(onClick = { settingsOpen = false; settingsMinimized = false }) { Text("×", color = LocalAccent.current) }
                            }
                            Text("Акцентный цвет", color = Muted, fontSize = 12.sp)
                            listOf("Кровавый", "Бордовый", "Алый").forEachIndexed { index, label ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    accentStyle = index
                                    saveAccentStyle(context, index)
                                }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = accentStyle == index, onClick = {
                                        accentStyle = index
                                        saveAccentStyle(context, index)
                                    }, colors = RadioButtonDefaults.colors(selectedColor = LocalAccent.current))
                                    Text(label, color = MainText, fontSize = 12.sp)
                                }
                            }
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
                                    }, colors = RadioButtonDefaults.colors(selectedColor = LocalAccent.current))
                                    Text(label, color = MainText, fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) { Text("Выбрать изображение") }
                            if (wallpaperUri != null) TextButton(onClick = {
                                wallpaperUri = null
                                context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().remove("wallpaper_uri").apply()
                            }) { Text("Убрать изображение", color = LocalAccent.current) }
                            listOf("Кровавый градиент", "Чёрный", "Багровое свечение").forEachIndexed { index, label ->
                                Row(Modifier.fillMaxWidth().background(if (wallpaperStyle == index) Color(0xFF35101A) else PanelSoft, RoundedCornerShape(10.dp)).clickable {
                                    wallpaperStyle = index
                                    saveWallpaperStyle(context, index)
                                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = MainText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    if (wallpaperStyle == index) Text("✓", color = LocalAccent.current)
                                }
                            }
                            Text("Сведения об устройстве", color = Muted, fontSize = 12.sp)
                            Text("Модель: ${Build.MANUFACTURER} ${Build.MODEL}", color = MainText, fontSize = 11.sp)
                            Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = MainText, fontSize = 11.sp)
                            Text("Оперативная память: ${memoryUsage(context)}", color = MainText, fontSize = 11.sp)
                            Text("Свободно: ${availableMemory(context)}", color = Muted, fontSize = 10.sp)
                            Text("Chiyuri: CPU ${processCpuPercent}% · RAM ${appMemoryPss(context)}", color = MainText, fontSize = 11.sp)
                            Text("Батарея: ${batteryPercent(context)}% · ${batteryState(context)}", color = MainText, fontSize = 11.sp)
                            Text("Раздел данных: ${storageSummary()}", color = MainText, fontSize = 11.sp)
                            Text("Сеть: ${if (networkAvailable(context)) "подключена" else "нет подключения"}", color = MainText, fontSize = 11.sp)
                            Text("Дополнительные права", color = Muted, fontSize = 12.sp)
                            Text("Root не нужен для обычной работы лаунчера. Проверка запускается только вручную.", color = Muted, fontSize = 10.sp)
                            Text("Состояние: $rootStatus", color = MainText, fontSize = 11.sp)
                            TextButton(enabled = !rootChecking, onClick = {
                                rootChecking = true
                                rootStatus = "Ожидание ответа системы…"
                                uiScope.launch(Dispatchers.IO) {
                                    val result = checkRootAccess()
                                    withContext(Dispatchers.Main) {
                                        rootStatus = result
                                        rootChecking = false
                                    }
                                }
                            }) { Text(if (rootChecking) "Проверка…" else "Проверить root-доступ", color = LocalAccent.current) }
                            Text("Активное приложение", color = Muted, fontSize = 12.sp)
                            Text(if (usageAccessGranted) "Usage Access разрешён" else "Usage Access не предоставлен", color = MainText, fontSize = 11.sp)
                            Text("Сейчас: ${apps.firstOrNull { it.packageName == activePackage }?.label ?: if (usageAccessGranted) "рабочий стол или системное окно" else "не определяется"}", color = MainText, fontSize = 11.sp)
                            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                                Text("Настроить отслеживание", color = LocalAccent.current)
                            }
                            Text("Системные настройки Android", color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }) { Text("Открыть настройки устройства", color = LocalAccent.current) }
                            TextButton(onClick = { settingsOpen = false; settingsMinimized = false }) { Text("Закрыть", color = LocalAccent.current) }
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
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalAccent.current),
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
                                            onAdjacentClick = { openAppAdjacent(app) },
                                            onMoveLeft = { movePinned(app, -1) },
                                            onMoveRight = { movePinned(app, 1) }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { startOpen = false; search = "" }) {
                                Text("Закрыть", color = LocalAccent.current)
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
                    Text("CP", color = LocalAccent.current, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Проводник",
                    tint = if (filesOpen && !filesMinimized && activePanel == "files") LocalAccent.current else MainText,
                    modifier = Modifier.size(38.dp).clickable {
                        if (filesOpen && !filesMinimized && activePanel == "files") filesMinimized = true
                        else { filesOpen = true; filesMinimized = false; if (!isWideLayout) settingsOpen = false; activePanel = "files" }
                    }.padding(9.dp))
                Icon(Icons.Filled.Palette, contentDescription = "Оформление",
                    tint = if (settingsOpen && !settingsMinimized && activePanel == "settings") LocalAccent.current else MainText,
                    modifier = Modifier.size(38.dp).clickable {
                        if (settingsOpen && !settingsMinimized && activePanel == "settings") settingsMinimized = true
                        else { settingsOpen = true; settingsMinimized = false; if (!isWideLayout) filesOpen = false; activePanel = "settings" }
                    }.padding(9.dp))
                pinnedApps.forEach { app ->
                    Box(
                        Modifier.size(42.dp).background(if (app.packageName == (if (usageAccessGranted) activePackage else lastLaunchedPackage)) Color(0xFF35101A) else Color.Transparent, RoundedCornerShape(9.dp)).combinedClickable(
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
    }
}

@Composable
private fun DesktopShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(92.dp).background(PanelSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(25.dp))
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
    onAdjacentClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
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
            Text(if (pinned) "На панели" else "Удержи: закрепить", color = if (pinned) LocalAccent.current else Muted, fontSize = 8.sp)
            Text("·", color = Muted, fontSize = 8.sp)
            TextButton(onClick = onDesktopToggle, contentPadding = PaddingValues(0.dp)) {
                Text(if (desktopShortcut) "Убрать ярлык" else "+ На стол", color = if (desktopShortcut) LocalAccent.current else Muted, fontSize = 8.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pinned) {
                TextButton(onClick = onMoveLeft, contentPadding = PaddingValues(0.dp)) { Text("←", color = Muted, fontSize = 10.sp) }
                TextButton(onClick = onMoveRight, contentPadding = PaddingValues(0.dp)) { Text("→", color = Muted, fontSize = 10.sp) }
            }
            TextButton(onClick = onAdjacentClick, contentPadding = PaddingValues(0.dp)) {
                Text("Рядом", color = Muted, fontSize = 8.sp)
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

private fun loadWallpaperScale(context: Context): Int =
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getInt("wallpaper_scale", 0).coerceIn(0, 2)

private fun saveWallpaperScale(context: Context, scale: Int) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putInt("wallpaper_scale", scale.coerceIn(0, 2)).apply()
}

private fun loadAccentStyle(context: Context): Int = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).getInt("accent_style", 0).coerceIn(0, 2)

private fun saveAccentStyle(context: Context, style: Int) {
    context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE).edit().putInt("accent_style", style.coerceIn(0, 2)).apply()
}

private fun accentColor(style: Int): Color = when (style) {
    1 -> Color(0xFF9E1235)
    2 -> Color(0xFFFF304F)
    else -> DefaultCrimson
}

private fun decodeWallpaper(context: Context, savedUri: String): androidx.compose.ui.graphics.ImageBitmap? {
    val uri = Uri.parse(savedUri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Не удалось открыть изображение" }
        BitmapFactory.decodeStream(input, null, bounds)
    }
    val targetWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1) * 2
    val targetHeight = context.resources.displayMetrics.heightPixels.coerceAtLeast(1) * 2
    var sample = 1
    while (bounds.outWidth / sample > targetWidth || bounds.outHeight / sample > targetHeight) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Не удалось открыть изображение повторно" }
        BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
    }
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

private fun batteryState(context: Context): String {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "заряжается"
        BatteryManager.BATTERY_STATUS_FULL -> "заряжена"
        else -> "от батареи"
    }
}

private fun appMemoryPss(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = manager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
    return "${memory?.totalPss?.div(1024) ?: 0} МБ"
}

private fun memoryUsage(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    val used = (info.totalMem - info.availMem).coerceAtLeast(0L)
    return String.format(Locale.getDefault(), "%.1f / %.1f ГБ занято", used / (1024.0 * 1024.0 * 1024.0), info.totalMem / (1024.0 * 1024.0 * 1024.0))
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

private fun loadPinnedPackages(context: Context, apps: List<LaunchApp>): List<String> {
    val prefs = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
    val ordered = prefs.getString("pinned_packages_ordered", null)
    if (ordered != null) return ordered.split("|").filter { it.isNotBlank() }
    if (prefs.getBoolean("pins_initialized", false)) {
        val legacy = prefs.getStringSet("pinned_packages", emptySet()).orEmpty()
        val migrated = apps.filter { it.packageName in legacy }.map { it.packageName }
        savePinnedPackages(context, migrated)
        return migrated
    }
    val defaults = apps.take(5).map { it.packageName }
    savePinnedPackages(context, defaults)
    prefs.edit().putBoolean("pins_initialized", true).apply()
    return defaults
}

private fun savePinnedPackages(context: Context, packages: List<String>) {
    val prefs = context.getSharedPreferences("desktop_preferences", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("pinned_packages_ordered", packages.joinToString("|"))
        .putStringSet("pinned_packages", packages.toSet())
        .putBoolean("pins_initialized", true)
        .apply()
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

private fun launchApp(context: Context, app: LaunchApp): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent == null) {
        Toast.makeText(context, "Приложение больше недоступно", Toast.LENGTH_SHORT).show()
        return false
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        Toast.makeText(context, "Не удалось запустить приложение", Toast.LENGTH_SHORT).show()
        false
    }
}

private fun launchAppAdjacent(context: Context, app: LaunchApp): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent == null) {
        Toast.makeText(context, "Приложение больше недоступно", Toast.LENGTH_SHORT).show()
        return false
    }
    return runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        context.startActivity(intent)
        true
    }.getOrElse {
        Toast.makeText(context, "Не удалось открыть рядом. Попробуй сначала включить разделённый экран Android.", Toast.LENGTH_LONG).show()
        false
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

private fun queryForegroundPackage(
    context: Context,
    beginTime: Long,
    previousPackage: String?
): Pair<String?, Long> {
    val now = System.currentTimeMillis()
    return runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(beginTime, now + 1)
        val event = UsageEvents.Event()
        var foreground = previousPackage
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED -> if (foreground == event.packageName) foreground = null
                }
            } else {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (foreground == event.packageName) foreground = null
                }
            }
        }
        foreground to now
    }.getOrDefault(previousPackage to now).let { snapshot ->
        (snapshot.first?.takeIf { it != context.packageName }) to snapshot.second
    }
}

private fun checkRootAccess(): String {
    return try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val completed = process.waitFor(20, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            "Проверка не завершена вовремя"
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() == 0 && output.contains("uid=0")) "Root-доступ подтверждён"
            else "Root-доступ не предоставлен"
        }
    } catch (_: Exception) {
        "Root-доступ не обнаружен"
    }
}

private fun networkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun nowTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate() = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
