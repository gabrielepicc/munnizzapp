@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.Folder
import com.gabrielepiccione.onda.data.NotificationItem
import com.gabrielepiccione.onda.data.WidgetManager

/** Cartella in home: chiusa mostra nome e mini-icone; toccandola si espande con app e widget. */
@Composable
fun FolderRow(
    folder: Folder,
    apps: List<AppInfo>,
    widgets: WidgetManager,
    notifications: Map<String, List<NotificationItem>>,
    showIcons: Boolean,
    themedIcons: Boolean,
    iconScale: Float,
    onApp: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOndaColors.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    var open by rememberSaveable(folder.id) { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, Motion.bouncy(), label = "arrow")
    val unread = apps.count { notifications[it.packageName].orEmpty().isNotEmpty() }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (open) colors.panel.copy(alpha = 0.85f) else androidx.compose.ui.graphics.Color.Transparent)
            .animateContentSize(Motion.bouncy())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .combinedClickable(
                    onClick = { open = !open },
                    onLongClick = {
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Mini-icone sovrapposte (fino a 3)
            if (showIcons) {
                Box(Modifier.size(44.dp * iconScale)) {
                    val shown = apps.take(3)
                    if (shown.isEmpty()) {
                        Box(
                            Modifier
                                .size(44.dp * iconScale)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.accentContainer)
                        )
                    }
                    shown.forEachIndexed { i, a ->
                        AppIcon(
                            a,
                            28.dp * iconScale,
                            themedIcons,
                            Modifier.offset(x = (8 * i).dp * iconScale, y = (8 * i).dp * iconScale),
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
            }
            Text(
                folder.name,
                color = colors.onWallpaper,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unread > 0 && !open) {
                Text(
                    "$unread",
                    color = colors.onAccentContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Chiudi" else "Apri",
                tint = colors.onWallpaperMuted,
                modifier = Modifier.graphicsLayer { rotationZ = arrow },
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                folder.widgets.forEach { id -> HostedWidget(id, widgets) }
                apps.forEach { app ->
                    AppListRow(
                        app = app,
                        hasNotification = notifications[app.packageName].orEmpty().isNotEmpty(),
                        showIcon = showIcons,
                        themedIcons = themedIcons,
                        onClick = { onApp(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
                if (apps.isEmpty() && folder.widgets.isEmpty()) {
                    Text(
                        "Cartella vuota. Tieni premuta un'app e scegli «Sposta in cartella», oppure tieni premuta la cartella per aggiungere un widget.",
                        color = colors.onWallpaperMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/** Opzioni della cartella: rinomina, sposta, aggiungi/rimuovi widget, elimina. */
@Composable
fun FolderSheet(
    folder: Folder,
    widgets: WidgetManager,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalOndaColors.current
    var renaming by remember { mutableStateOf(false) }
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panel,
        contentColor = colors.onWallpaper,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(folder.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetRow(Icons.Filled.Edit, "Rinomina") { renaming = true }
            if (canMoveUp) SheetRow(Icons.Filled.KeyboardArrowUp, "Sposta su") { onMove(-1) }
            if (canMoveDown) SheetRow(Icons.Filled.KeyboardArrowDown, "Sposta giù") { onMove(1) }
            SheetRow(Icons.Filled.Add, "Aggiungi widget alla cartella") { onAddWidget(); onDismiss() }
            folder.widgets.forEach { id ->
                val label = widgets.infoFor(id)?.let { runCatching { it.loadLabel(pm) }.getOrNull() } ?: "Widget"
                SheetRow(Icons.Filled.Delete, "Rimuovi widget «$label»") { onRemoveWidget(id) }
            }
            SheetRow(Icons.Filled.Delete, "Elimina cartella (le app restano in home)") { onDelete(); onDismiss() }
        }
    }
    if (renaming) {
        NameDialog("Rinomina cartella", folder.name, onDismiss = { renaming = false }) { onRename(it); renaming = false }
    }
}

/** Scelta della cartella di destinazione per un'app. */
@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    currentFolderId: String?,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sposta in cartella") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    DialogChoice("+ Nuova cartella…", false) { creating = true }
                }
                if (currentFolderId != null) {
                    item { DialogChoice("Fuori dalle cartelle (in home)", false) { onPick(null) } }
                }
                items(folders.size) { i ->
                    val f = folders[i]
                    DialogChoice(f.name, f.id == currentFolderId) { onPick(f.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
    if (creating) {
        NameDialog("Nuova cartella", "", onDismiss = { creating = false }) { onCreate(it); creating = false }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun DialogChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) colors.accent else colors.onWallpaper,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onWallpaper)
    }
}
