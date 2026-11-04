package com.gabrielepiccione.onda.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.data.CalendarEvent
import com.gabrielepiccione.onda.data.MediaInfo
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Orologio grande con cifre che scorrono al cambio di minuto, data e prossimo evento. */
@Composable
fun ClockHeader(
    event: CalendarEvent?,
    onTime: () -> Unit,
    onDate: () -> Unit,
    onEvent: (CalendarEvent) -> Unit,
) {
    val colors = LocalOndaColors.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 30)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale))

    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onTime)) {
            time.forEachIndexed { i, ch ->
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(Motion.offsetEnter) { it / 2 } + fadeIn(Motion.enter()))
                            .togetherWith(slideOutVertically(Motion.offsetExit) { -it / 2 } + fadeOut(Motion.exit()))
                            .using(SizeTransform(clip = false))
                    },
                    label = "digit$i",
                ) { c ->
                    Text(
                        c.toString(),
                        color = colors.onWallpaper,
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
            color = colors.onWallpaperMuted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDate).padding(vertical = 2.dp),
        )
        if (event != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentContainer)
                    .clickable { onEvent(event) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.DateRange, null, tint = colors.onAccentContainer, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${eventWhen(event, locale)} · ${event.title}",
                    color = colors.onAccentContainer,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun eventWhen(e: CalendarEvent, locale: Locale): String {
    if (e.allDay) return "Oggi"
    val start = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
    val hour = start.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return if (start.toLocalDate() == LocalDate.now()) hour else "Domani $hour"
}

/** Mini-player per la sessione multimediale attiva. */
@Composable
fun MediaCard(
    media: MediaInfo,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.panel.copy(alpha = 0.9f))
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            val art = media.art
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            } else {
                Icon(Icons.Filled.PlayArrow, null, tint = colors.onAccentContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.title, color = colors.onWallpaper, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.artist.isNotBlank()) {
                Text(media.artist, color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaButton(onPrev) { Icon(Icons.Filled.KeyboardArrowLeft, "Precedente", tint = colors.onWallpaper) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(if (media.playing) 14.dp else 22.dp))
                .background(colors.accent)
                .clickable(onClick = onPlayPause)
        ) {
            AnimatedContent(targetState = media.playing, label = "playpause") { playing ->
                if (playing) PauseGlyph(colors.onAccent) else Icon(Icons.Filled.PlayArrow, "Riproduci", tint = colors.onAccent)
            }
        }
        MediaButton(onNext) { Icon(Icons.Filled.KeyboardArrowRight, "Successivo", tint = colors.onWallpaper) }
    }
}

@Composable
private fun MediaButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick)
    ) { content() }
}

/** Icona "pausa" disegnata con due barre (non presente nel set di icone core). */
@Composable
private fun PauseGlyph(color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 4.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.size(width = 4.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}
