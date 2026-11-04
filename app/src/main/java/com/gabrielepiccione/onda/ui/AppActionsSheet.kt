@file:OptIn(ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.AppInfo

data class AppActions(
    val shortcuts: List<ShortcutInfo>,
    val isMuted: Boolean,
    val favoriteIndex: Int,
    val favoriteCount: Int,
    val onShortcut: (ShortcutInfo) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onMove: (Int) -> Unit,
    val onRename: (String?) -> Unit,
    val onToggleHidden: () -> Unit,
    val onToggleMuted: () -> Unit,
    val onAddWidget: () -> Unit,
    val onInfo: () -> Unit,
    val onUninstall: () -> Unit,
)

@Composable
fun AppActionsSheet(app: AppInfo, actions: AppActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf(false) }
    val colors = LocalOndaColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        contentColor = colors.onWallpaper,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    if (app.label != app.originalLabel) {
                        Text(app.originalLabel, fontSize = 13.sp, color = colors.onWallpaperMuted)
                    }
                }
            }

            if (actions.shortcuts.isNotEmpty()) {
                actions.shortcuts.forEach { sc ->
                    ActionRow(Icons.Filled.PlayArrow, (sc.shortLabel ?: sc.longLabel ?: "").toString()) {
                        actions.onShortcut(sc); onDismiss()
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 24.dp), color = colors.panelBorder)
            }

            ActionRow(Icons.Filled.Star, if (app.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") {
                actions.onToggleFavorite(); onDismiss()
            }
            if (app.isFavorite && actions.favoriteIndex > 0) {
                ActionRow(Icons.Filled.KeyboardArrowUp, "Sposta su") { actions.onMove(-1) }
            }
            if (app.isFavorite && actions.favoriteIndex in 0 until actions.favoriteCount - 1) {
                ActionRow(Icons.Filled.KeyboardArrowDown, "Sposta giù") { actions.onMove(1) }
            }
            ActionRow(Icons.Filled.Edit, "Rinomina") { renaming = true }
            ActionRow(Icons.Filled.Lock, if (app.isHidden) "Mostra nell'elenco" else "Nascondi") {
                actions.onToggleHidden(); onDismiss()
            }
            ActionRow(Icons.Filled.Notifications, if (actions.isMuted) "Mostra notifiche in home" else "Non mostrare notifiche in home") {
                actions.onToggleMuted(); onDismiss()
            }
            ActionRow(Icons.Filled.Add, "Aggiungi un widget di questa app") {
                actions.onAddWidget(); onDismiss()
            }
            ActionRow(Icons.Filled.Info, "Info app") { actions.onInfo(); onDismiss() }
            ActionRow(Icons.Filled.Delete, "Disinstalla") { actions.onUninstall(); onDismiss() }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(app.label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rinomina") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(app.originalLabel) })
            },
            confirmButton = {
                TextButton(onClick = { actions.onRename(text); renaming = false }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { actions.onRename(null); renaming = false }) { Text("Ripristina") }
            },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 16.sp, color = colors.onWallpaper)
    }
}
