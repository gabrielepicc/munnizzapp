@file:OptIn(ExperimentalFoundationApi::class)

package com.gabrielepiccione.onda.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.NotificationItem

/** Riga grande della home: nome dell'app e, sotto, l'ultima notifica. */
@Composable
fun FavoriteRow(
    app: AppInfo,
    notifications: List<NotificationItem>,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onNotificationDismiss: (NotificationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOndaColors.current
    val latest = notifications.firstOrNull()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Box {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(42.dp))
                    if (latest != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = app.label,
                color = colors.onWallpaper,
                fontSize = 25.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!showIcon && latest != null) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(colors.accent))
            }
        }
        if (latest != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = if (showIcon) 58.dp else 0.dp, top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.panel.copy(alpha = 0.55f))
                    .clickable { onNotificationClick(latest) }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    if (latest.title.isNotBlank()) {
                        Text(
                            latest.title,
                            color = colors.onWallpaper,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (latest.text.isNotBlank()) {
                        Text(
                            latest.text,
                            color = colors.onWallpaperMuted,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (notifications.size > 1) {
                    Text(
                        "+${notifications.size - 1}",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                if (latest.clearable) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onNotificationDismiss(latest) }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Ignora", tint = colors.onWallpaperMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Riga compatta per l'elenco completo e i risultati di ricerca. */
@Composable
fun AppListRow(
    app: AppInfo,
    hasNotification: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) colors.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (showIcon) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            app.label,
            color = colors.onWallpaper,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app.isWorkProfile) {
            Text("lavoro", color = colors.onWallpaperMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (hasNotification) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
        }
    }
}
