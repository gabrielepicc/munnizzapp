package com.gabrielepiccione.onda.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.ThemeMode
import com.gabrielepiccione.onda.data.WidgetManager

@Composable
fun SettingsScreen(
    settings: Settings,
    hiddenApps: List<AppInfo>,
    isDefaultLauncher: Boolean,
    notificationAccess: Boolean,
    widgets: WidgetManager,
    onEdit: ((Settings) -> Settings) -> Unit,
    onRequestDefault: () -> Unit,
    onNotificationAccess: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onUnhide: (AppInfo) -> Unit,
    onWallpaper: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalOndaColors.current
    val pm = LocalContext.current.packageManager
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.panel)
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose)
            ) {
                @Suppress("DEPRECATION")
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro", tint = colors.onWallpaper)
            }
            Spacer(Modifier.width(8.dp))
            Text("Impostazioni", fontSize = 26.sp, color = colors.onWallpaper, fontWeight = FontWeight.Medium)
        }

        Group("Configurazione") {
            StatusRow(
                title = "Launcher predefinito",
                ok = isDefaultLauncher,
                okText = "Onda è la tua home",
                koText = "Imposta Onda come home",
                onClick = onRequestDefault,
            )
            StatusRow(
                title = "Notifiche in home",
                ok = notificationAccess,
                okText = "Accesso alle notifiche attivo",
                koText = "Concedi l'accesso alle notifiche",
                onClick = onNotificationAccess,
            )
        }

        Group("Aspetto") {
            Label("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Chiaro", ThemeMode.DARK to "Scuro").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onEdit { it.copy(themeMode = mode) } },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Colore d'accento")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentChoices.forEach { argb ->
                    val selected = settings.accent == argb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(BorderStroke(if (selected) 3.dp else 0.dp, colors.onWallpaper), CircleShape)
                            .clickable { onEdit { it.copy(accent = argb) } }
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow("Mostra icone", settings.showIcons) { v -> onEdit { it.copy(showIcons = v) } }
            SwitchRow("Mostra orologio e data", settings.showClock) { v -> onEdit { it.copy(showClock = v) } }
            SwitchRow("Scroller a sinistra (mancini)", settings.leftHanded) { v -> onEdit { it.copy(leftHanded = v) } }
            Label("Dimensione testo")
            Slider(
                value = settings.textScale,
                onValueChange = { v -> onEdit { it.copy(textScale = v) } },
                valueRange = 0.8f..1.3f,
                steps = 4,
            )
            Label("Oscuramento sfondo")
            Slider(
                value = settings.wallpaperDim,
                onValueChange = { v -> onEdit { it.copy(wallpaperDim = v) } },
                valueRange = 0f..0.8f,
            )
            TextButton(onClick = onWallpaper) { Text("Cambia sfondo…") }
        }

        Group("Widget") {
            if (settings.widgetIds.isEmpty()) {
                Text("Nessun widget in home.", color = colors.onWallpaperMuted, fontSize = 14.sp)
            }
            settings.widgetIds.forEach { id ->
                val info = widgets.infoFor(id)
                val label: String? = info?.let { i -> runCatching { i.loadLabel(pm) }.getOrNull() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        label ?: info?.provider?.className?.substringAfterLast('.') ?: "Widget #$id",
                        color = colors.onWallpaper,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onRemoveWidget(id) }) { Text("Rimuovi") }
                }
            }
            OutlinedButton(onClick = onAddWidget, modifier = Modifier.padding(top = 6.dp)) { Text("Aggiungi widget") }
        }

        Group("App nascoste") {
            if (hiddenApps.isEmpty()) {
                Text("Nessuna app nascosta. Tieni premuta un'app per nasconderla.", color = colors.onWallpaperMuted, fontSize = 14.sp)
            }
            hiddenApps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Image(app.icon, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, color = colors.onWallpaper, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onUnhide(app) }) { Text("Mostra") }
                }
            }
        }

        Group("Gesti") {
            Text(
                "• Trascina sulle lettere a lato per sfogliare tutte le app\n" +
                    "• Scorri verso l'alto in home per cercare\n" +
                    "• Scorri verso il basso per aprire le notifiche\n" +
                    "• Tieni premuto su un'app per le opzioni, su uno spazio vuoto per le impostazioni",
                color = colors.onWallpaperMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }

        Text(
            "Onda 0.1 · launcher minimale",
            color = colors.onWallpaperMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = LocalOndaColors.current
    Text(
        title,
        color = colors.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panelBorder)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalOndaColors.current.onWallpaper, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp)
    ) {
        Text(text, color = LocalOndaColors.current.onWallpaper, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, koText: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) colors.accent else Color(0xFFFF8A7A)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onWallpaper, fontSize = 15.sp)
            Text(if (ok) okText else koText, color = colors.onWallpaperMuted, fontSize = 13.sp)
        }
    }
}
