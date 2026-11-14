package com.chiyuri.desktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
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
    var startOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(nowTime()) }
    var date by remember { mutableStateOf(nowDate()) }

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

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF1B080E), Night, Color(0xFF09090B)))
            )
        )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DesktopShortcut("Приложения", Icons.Filled.Apps) { startOpen = true }
                            DesktopShortcut("Настройки", Icons.Filled.Settings) {
                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(clock, color = MainText, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text(date, color = Muted, fontSize = 13.sp)
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
                                        AppTile(app) {
                                            launchApp(context, app)
                                            startOpen = false
                                            search = ""
                                        }
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
            apps.take(5).forEach { app ->
                Box(Modifier.size(42.dp).clickable { launchApp(context, app) }, contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = remember(app.packageName) { app.icon.toBitmap(72, 72).asImageBitmap() },
                        contentDescription = app.label,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
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
private fun AppTile(app: LaunchApp, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
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
    }
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

private fun launchApp(context: Context, app: LaunchApp) {
    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun nowTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate() = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())
