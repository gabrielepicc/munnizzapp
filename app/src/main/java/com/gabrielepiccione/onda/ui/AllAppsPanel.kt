package com.gabrielepiccione.onda.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.NotificationItem

data class AppSection(val letter: Char, val apps: List<AppInfo>, val firstIndex: Int)

/** Raggruppa le app (già ordinate) per lettera e calcola l'indice d'inizio di ogni sezione. */
fun buildSections(apps: List<AppInfo>): List<AppSection> {
    val grouped = LinkedHashMap<Char, MutableList<AppInfo>>()
    // '#' sempre in testa
    apps.filter { it.section == '#' }.takeIf { it.isNotEmpty() }?.let { grouped['#'] = it.toMutableList() }
    apps.filter { it.section != '#' }.forEach { grouped.getOrPut(it.section) { mutableListOf() }.add(it) }
    var index = 0
    return grouped.map { (letter, list) ->
        AppSection(letter, list, index).also { index += 1 + list.size }
    }
}

@Composable
fun AllAppsPanel(
    sections: List<AppSection>,
    listState: LazyListState,
    activeLetter: Char?,
    notifications: Map<String, List<NotificationItem>>,
    showIcons: Boolean,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOndaColors.current
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            item(key = "h_${section.letter}") {
                Text(
                    text = section.letter.toString(),
                    color = if (section.letter == activeLetter) colors.accent else colors.onWallpaperMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            section.apps.forEach { app ->
                item(key = app.key) {
                    AppListRow(
                        app = app,
                        hasNotification = notifications[app.packageName].orEmpty().isNotEmpty(),
                        showIcon = showIcons,
                        highlighted = section.letter == activeLetter,
                        onClick = { onClick(app) },
                        onLongClick = { onLongClick(app) },
                    )
                }
            }
        }
    }
}
