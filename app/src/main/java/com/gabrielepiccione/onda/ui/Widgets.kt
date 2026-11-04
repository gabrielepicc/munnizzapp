@file:OptIn(ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.gabrielepiccione.onda.data.WidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Widget ospitati in home, uno sotto l'altro. */
@Composable
fun WidgetsArea(widgetIds: List<Int>, widgets: WidgetManager, modifier: Modifier = Modifier) {
    if (widgetIds.isEmpty()) return
    Column(modifier) {
        widgetIds.forEach { id -> HostedWidget(id, widgets) }
    }
}

@Composable
fun HostedWidget(id: Int, widgets: WidgetManager) {
    val info = remember(id) { widgets.infoFor(id) } ?: return
    val density = LocalDensity.current
    val minHeightDp = with(density) { info.minHeight.toDp() }.coerceAtLeast(72.dp)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
    ) {
        val widthDp = maxWidth
        val heightDp = minHeightDp.coerceAtMost(320.dp)
        AndroidView(
            factory = { ctx -> widgets.createView(ctx, id) ?: View(ctx) },
            update = { view ->
                if (view is android.appwidget.AppWidgetHostView) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.value, heightDp.value)))
                    } else {
                        @Suppress("DEPRECATION")
                        view.updateAppWidgetSize(
                            null,
                            widthDp.value.toInt(), heightDp.value.toInt(),
                            widthDp.value.toInt(), heightDp.value.toInt(),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp),
        )
    }
}

/** Elenco dei widget installati, eventualmente filtrato per una sola app. */
@Composable
fun WidgetPickerSheet(
    widgets: WidgetManager,
    packageFilter: String?,
    onPick: (WidgetManager.Provider) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalOndaColors.current
    val providers by produceState(initialValue = emptyList<WidgetManager.Provider>(), packageFilter) {
        value = withContext(Dispatchers.IO) { widgets.providers(packageFilter) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panel,
        contentColor = colors.onWallpaper,
    ) {
        Text(
            "Aggiungi widget",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (providers.isEmpty()) {
            Text(
                if (packageFilter != null) "Questa app non ha widget." else "Caricamento…",
                color = colors.onWallpaperMuted,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            items(providers, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { p ->
                val preview by produceState<ImageBitmap?>(initialValue = null, p) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val d = widgets.previewOf(p) ?: return@runCatching null
                            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 300
                            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 150
                            val scale = (600f / w).coerceAtMost(1f)
                            d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
                        }.getOrNull()
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(p) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(p.appLabel, color = colors.onWallpaperMuted, fontSize = 12.sp)
                    Text(p.label, fontSize = 17.sp)
                    preview?.let {
                        Image(
                            it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.panelBorder),
                        )
                    }
                }
            }
        }
    }
}
