@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.NotificationItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Icona dell'app: normale oppure "a tema" (monocromatica sui colori Material You). */
@Composable
fun AppIcon(app: AppInfo, size: Dp, themed: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalOndaColors.current
    val mono = app.mono
    if (themed && mono != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.accentContainer)
        ) {
            Image(
                bitmap = mono,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.onAccentContainer),
                modifier = Modifier.size(size),
            )
        }
    } else {
        Image(bitmap = app.icon, contentDescription = null, modifier = modifier.size(size))
    }
}

/** Modifier che rimpicciolisce leggermente l'elemento mentre è premuto (feedback tattile visivo). */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Riga grande della home: icona, nome e anteprima delle notifiche.
 * Scorrendo verso destra si apre l'app associata (azione di scorrimento) oppure si espandono le notifiche.
 */
@Composable
fun FavoriteRow(
    app: AppInfo,
    swipeTarget: AppInfo?,
    notifications: List<NotificationItem>,
    showIcon: Boolean,
    themedIcons: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: () -> Unit,
    hideName: Boolean = false,
    iconScale: Float = 1f,
    onNotificationClick: (NotificationItem) -> Unit,
    onNotificationDismiss: (NotificationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOndaColors.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    var expanded by rememberSaveable(app.key) { mutableStateOf(false) }

    val offsetX = remember { Animatable(0f) }
    val triggerPx = with(density) { 96.dp.toPx() }
    val maxPx = with(density) { 140.dp.toPx() }
    var armed by remember { mutableStateOf(false) }

    val latest = notifications.firstOrNull()
    val canSwipe = true

    Box(modifier.fillMaxWidth()) {
        // Sfondo rivelato dallo scorrimento
        if (offsetX.value > 1f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (armed) colors.accentContainer else colors.panel)
                    .padding(start = 20.dp)
            ) {
                val p = (offsetX.value / triggerPx).coerceIn(0f, 1f)
                Box(Modifier.graphicsLayer { alpha = p; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p }) {
                    if (swipeTarget != null) {
                        AppIcon(swipeTarget, 32.dp, themedIcons)
                    } else {
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = colors.onAccentContainer)
                    }
                }
            }
        }

        Column(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pressScale(interaction)
                .clip(RoundedCornerShape(28.dp))
                .then(if (offsetX.value > 1f) Modifier.background(colors.scrim) else Modifier)
                .draggable(
                    enabled = canSwipe,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val next = (offsetX.value + delta * if (offsetX.value > triggerPx) 0.35f else 1f).coerceIn(0f, maxPx)
                            offsetX.snapTo(next)
                            val nowArmed = next >= triggerPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDragStopped = {
                        if (armed) {
                            onSwipe()
                        }
                        armed = false
                        offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                    },
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick,
                    onLongClick = {
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize(Motion.bouncy())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    Box {
                        AppIcon(app, 44.dp * iconScale, themedIcons)
                        val dot by animateFloatAsState(if (latest != null) 1f else 0f, Motion.bouncy(), label = "dot")
                        if (dot > 0.01f) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot.coerceIn(0f, 1f) }
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(colors.scrim)
                                    .padding(2.5.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent)
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                }
                if (hideName && showIcon) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = app.label,
                        color = colors.onWallpaper,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!showIcon && latest != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
                }
            }

            // Anteprima notifiche: l'ultima sempre, le altre quando la riga è espansa
            AnimatedVisibility(
                visible = latest != null,
                enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
            ) {
                val shown = if (expanded) notifications.take(6) else notifications.take(1)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = if (showIcon) 44.dp * iconScale + 18.dp else 0.dp, top = 8.dp),
                ) {
                    shown.forEach { n ->
                        key(n.key) {
                            NotificationCard(
                                item = n,
                                extraCount = if (!expanded && notifications.size > 1) notifications.size - 1 else 0,
                                onClick = { onNotificationClick(n) },
                                onDismiss = { onNotificationDismiss(n) },
                                onToggleExpand = { expanded = !expanded },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    extraCount: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val colors = LocalOndaColors.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && item.clearable) {
                onDismiss(); true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = item.clearable,
        enableDismissFromEndToStart = item.clearable,
        backgroundContent = {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.panel.copy(alpha = 0.85f))
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
        ) {
            Column(Modifier.weight(1f)) {
                if (item.title.isNotBlank()) {
                    Text(
                        item.title,
                        color = colors.onWallpaper,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.text.isNotBlank()) {
                    Text(
                        item.text,
                        color = colors.onWallpaperMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (extraCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentContainer)
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("+$extraCount", color = colors.onAccentContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (item.clearable) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Ignora", tint = colors.onWallpaperMuted, modifier = Modifier.size(16.dp))
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
    themedIcons: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = LocalOndaColors.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(24.dp))
            .background(if (highlighted) colors.accentContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (showIcon) {
            AppIcon(app, 36.dp, themedIcons)
            Spacer(Modifier.width(16.dp))
        }
        Text(
            app.label,
            color = colors.onWallpaper,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (app.isWorkProfile) {
            Text(
                "Lavoro",
                color = colors.onAccentContainer,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (hasNotification) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
        }
    }
}
