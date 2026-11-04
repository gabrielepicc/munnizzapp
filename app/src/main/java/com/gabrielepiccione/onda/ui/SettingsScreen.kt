package com.gabrielepiccione.onda.ui

import android.os.Build
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.FontChoice
import com.gabrielepiccione.onda.data.IconPacks
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.ThemeMode
import com.gabrielepiccione.onda.data.WidgetManager

data class SettingsStatus(
    val isDefaultLauncher: Boolean,
    val notificationAccess: Boolean,
    val calendarPermission: Boolean,
    val accessibilityEnabled: Boolean,
)

interface SettingsActions {
    fun edit(transform: (Settings) -> Settings)
    fun requestDefault()
    fun notificationAccess()
    fun calendarPermission()
    fun accessibility()
    fun addWidget()
    fun removeWidget(id: Int)
    fun unhide(app: AppInfo)
    fun wallpaper()
    fun exportBackup()
    fun importBackup()
    fun close()
}

@Composable
fun SettingsScreen(
    settings: Settings,
    hiddenApps: List<AppInfo>,
    status: SettingsStatus,
    widgets: WidgetManager,
    actions: SettingsActions,
) {
    val colors = LocalOndaColors.current
    val context = LocalContext.current
    val pm = context.packageManager
    var iconPackDialog by remember { mutableStateOf(false) }
    val onEdit = actions::edit

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { actions.close() }
            ) {
                @Suppress("DEPRECATION")
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro", tint = colors.onWallpaper)
            }
            Spacer(Modifier.width(8.dp))
            Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, color = colors.onWallpaper)
        }

        Group("Configurazione") {
            StatusRow("Launcher predefinito", status.isDefaultLauncher, "Onda è la tua home", "Imposta Onda come home") { actions.requestDefault() }
            StatusRow("Notifiche e musica in home", status.notificationAccess, "Accesso alle notifiche attivo", "Concedi l'accesso alle notifiche") { actions.notificationAccess() }
            StatusRow("Calendario", status.calendarPermission, "Mostra il prossimo evento", "Consenti la lettura del calendario") { actions.calendarPermission() }
        }

        Group("Colori e stile") {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Colori dinamici (Material You)", "Prende i colori dallo sfondo", settings.dynamicColor) { v -> onEdit { it.copy(dynamicColor = v) } }
            }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Label("Colore d'accento")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    AccentChoices.forEach { argb ->
                        val selected = settings.accent == argb
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(BorderStroke(if (selected) 3.dp else 0.dp, colors.onWallpaper), CircleShape)
                                .clickable { onEdit { it.copy(accent = argb) } }
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Label("Carattere")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FontChoice.entries.forEach { f ->
                    FilterChip(selected = settings.font == f, onClick = { onEdit { it.copy(font = f) } }, label = { Text(f.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Label("Dimensione testo")
            Slider(value = settings.textScale, onValueChange = { v -> onEdit { it.copy(textScale = v) } }, valueRange = 0.8f..1.3f, steps = 4)
            Label("Oscuramento sfondo")
            Slider(value = settings.wallpaperDim, onValueChange = { v -> onEdit { it.copy(wallpaperDim = v) } }, valueRange = 0f..0.8f)
            TextButton(onClick = { actions.wallpaper() }) { Text("Cambia sfondo…") }
        }

        Group("Icone") {
            SwitchRow("Mostra icone", null, settings.showIcons) { v -> onEdit { it.copy(showIcons = v) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SwitchRow("Icone a tema", "Icone monocromatiche nei colori del tema (se l'app le supporta)", settings.themedIcons) { v -> onEdit { it.copy(themedIcons = v) } }
            }
            val packLabel = remember(settings.iconPack) {
                if (settings.iconPack.isBlank()) "Predefinito di sistema"
                else runCatching { pm.getApplicationLabel(pm.getApplicationInfo(settings.iconPack, 0)).toString() }.getOrDefault(settings.iconPack)
            }
            ClickRow("Icon pack", packLabel) { iconPackDialog = true }
            Label("Dimensione icone")
            Slider(value = settings.iconSize, onValueChange = { v -> onEdit { it.copy(iconSize = v) } }, valueRange = 0.75f..1.4f)
        }

        Group("Home") {
            SwitchRow("Orologio e data", null, settings.showClock) { v -> onEdit { it.copy(showClock = v) } }
            SwitchRow("Prossimo evento", "Dal calendario, nelle prossime 24 ore", settings.showCalendar) { v -> onEdit { it.copy(showCalendar = v) } }
            SwitchRow("Player musicale", "Controlli della musica in riproduzione", settings.showMedia) { v -> onEdit { it.copy(showMedia = v) } }
            SwitchRow("Alfabeto a sinistra", "Modalità mancini (il lato opposto resta attivo ma invisibile)", settings.leftHanded) { v -> onEdit { it.copy(leftHanded = v) } }
            SwitchRow("Nascondi alfabeto in home", "Resta attivo e visibile accanto all'elenco delle app", settings.hideAlphabet) { v -> onEdit { it.copy(hideAlphabet = v) } }
            SwitchRow("Nascondi nomi dei preferiti", "Solo icone, per mettere in risalto lo sfondo", settings.hideFavoriteNames) { v -> onEdit { it.copy(hideFavoriteNames = v) } }
            SwitchRow("Nascondi barra di stato", null, settings.hideStatusBar) { v -> onEdit { it.copy(hideStatusBar = v) } }
        }

        Group("Gesti e feedback") {
            Label("Scorri verso il basso")
            GestureChips(settings.swipeDown) { g -> onEdit { it.copy(swipeDown = g) } }
            Label("Scorri verso l'alto")
            GestureChips(settings.swipeUp) { g -> onEdit { it.copy(swipeUp = g) } }
            SwitchRow(
                "Doppio tocco per bloccare",
                if (status.accessibilityEnabled) "Tocca due volte uno spazio vuoto della home" else "Richiede di attivare il servizio di accessibilità di Onda",
                settings.doubleTapLock,
            ) { v ->
                onEdit { it.copy(doubleTapLock = v) }
                if (v && !status.accessibilityEnabled) actions.accessibility()
            }
            SwitchRow("Vibrazione", "Feedback aptico su scroller e pressioni lunghe", settings.haptics) { v -> onEdit { it.copy(haptics = v) } }
            Text(
                "Scorri a destra su un'app: pannello rapido · Tieni premuto: opzioni · Bordi dello schermo: alfabeto (anche il lato senza lettere)",
                color = colors.onWallpaperMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Group("Widget") {
            if (settings.widgetIds.isEmpty()) {
                Text("Nessun widget in home.", color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodyMedium)
            }
            settings.widgetIds.forEach { id ->
                val info = widgets.infoFor(id)
                val label: String? = info?.let { i -> runCatching { i.loadLabel(pm) }.getOrNull() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(label ?: "Widget #$id", color = colors.onWallpaper, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.removeWidget(id) }) { Text("Rimuovi") }
                }
            }
            FilledTonalButton(onClick = { actions.addWidget() }, modifier = Modifier.padding(top = 6.dp)) { Text("Aggiungi widget") }
        }

        Group("App nascoste") {
            if (hiddenApps.isEmpty()) {
                Text("Nessuna app nascosta. Tieni premuta un'app per nasconderla.", color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodyMedium)
            }
            hiddenApps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Image(app.icon, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = colors.onWallpaper, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.unhide(app) }) { Text("Mostra") }
                }
            }
        }

        Group("Backup") {
            Text("Salva preferiti, nomi, nascoste e aspetto in un file, e ripristinali su un altro telefono.", color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                FilledTonalButton(onClick = { actions.exportBackup() }) { Text("Esporta") }
                OutlinedButton(onClick = { actions.importBackup() }) { Text("Ripristina") }
            }
        }

        Text(
            "Onda 0.3 · launcher minimale",
            color = colors.onWallpaperMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 8.dp),
        )
    }

    if (iconPackDialog) {
        val packs = remember { IconPacks.installed(context) }
        AlertDialog(
            onDismissRequest = { iconPackDialog = false },
            title = { Text("Icon pack") },
            text = {
                Column {
                    ChoiceRow("Predefinito di sistema", settings.iconPack.isBlank()) {
                        onEdit { it.copy(iconPack = "") }; iconPackDialog = false
                    }
                    packs.forEach { p ->
                        ChoiceRow(p.label, settings.iconPack == p.packageName) {
                            onEdit { it.copy(iconPack = p.packageName) }; iconPackDialog = false
                        }
                    }
                    if (packs.isEmpty()) {
                        Text(
                            "Nessun icon pack installato. Scaricane uno dal Play Store (compatibile con Nova/ADW).",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { iconPackDialog = false }) { Text("Chiudi") } },
        )
    }
}

@Composable
private fun GestureChips(selected: com.gabrielepiccione.onda.data.GestureAction, onPick: (com.gabrielepiccione.onda.data.GestureAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
        com.gabrielepiccione.onda.data.GestureAction.entries.forEach { g ->
            FilterChip(selected = selected == g, onClick = { onPick(g) }, label = { Text(g.label) })
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = LocalOndaColors.current
    Text(
        title,
        color = colors.accent,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.panel)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalOndaColors.current.onWallpaper, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
}

@Composable
private fun SwitchRow(text: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text, color = colors.onWallpaper, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(text: String, value: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(text, color = colors.onWallpaper, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = colors.accent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, koText: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (ok) colors.accentContainer else MaterialTheme.colorScheme.errorContainer)
        ) {
            if (ok) Icon(Icons.Filled.Check, null, tint = colors.onAccentContainer, modifier = Modifier.size(16.dp))
            else Text("!", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onWallpaper, style = MaterialTheme.typography.bodyLarge)
            Text(if (ok) okText else koText, color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
