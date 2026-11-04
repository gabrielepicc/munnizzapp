package com.gabrielepiccione.onda.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Colonna di lettere sul bordo dello schermo. Trascinando il dito le lettere vicine
 * si gonfiano e si spostano verso il centro con un profilo gaussiano ("onda").
 *
 * @param letters lettere disponibili, in ordine
 * @param leftSide true se la colonna sta a sinistra (modalità mancini)
 * @param onLetter chiamata ogni volta che cambia la lettera sotto il dito
 * @param onTouchStart / onTouchEnd inizio e fine del trascinamento
 */
@Composable
fun AlphabetWaveScroller(
    letters: List<Char>,
    leftSide: Boolean,
    modifier: Modifier = Modifier,
    onTouchStart: () -> Unit = {},
    onLetter: (Char) -> Unit,
    onTouchEnd: () -> Unit = {},
) {
    if (letters.isEmpty()) return
    val colors = LocalOndaColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var touchY by remember { mutableFloatStateOf(-1f) }
    var selected by remember { mutableIntStateOf(-1) }
    val intensity = remember { Animatable(0f) }

    val currentLetters by rememberUpdatedState(letters)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnStart by rememberUpdatedState(onTouchStart)
    val currentOnEnd by rememberUpdatedState(onTouchEnd)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        val n = letters.size
        val step = heightPx / n

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(heightPx, n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnStart()
                        scope.launch { intensity.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }

                        fun update(y: Float) {
                            touchY = y
                            val idx = (y / step).toInt().coerceIn(0, currentLetters.lastIndex)
                            if (idx != selected) {
                                selected = idx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnLetter(currentLetters[idx])
                            }
                        }
                        update(down.position.y)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            update(change.position.y)
                            change.consume()
                        }
                        currentOnEnd()
                        selected = -1
                        scope.launch { intensity.animateTo(0f, tween(260)) }
                    }
                }
        )

        val maxShiftPx = with(density) { 56.dp.toPx() }
        val sigma = 2.2f // ampiezza dell'onda in "numero di lettere"
        val waveCenter = if (touchY >= 0f) touchY / step - 0.5f else -100f
        val k = intensity.value

        letters.forEachIndexed { i, letter ->
            val d = i - waveCenter
            val wave = exp(-(d * d) / (2 * sigma * sigma)) * k
            val shift = (maxShiftPx * wave).roundToInt()
            val scale = 1f + 1.1f * wave
            val isSel = i == selected
            Text(
                text = letter.toString(),
                color = if (isSel) colors.accent else colors.onWallpaperMuted.copy(alpha = 0.55f + 0.45f * wave),
                fontSize = 11.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val x = if (leftSide) shift else -shift
                        val y = (i * step + step / 2f - with(density) { 8.sp.toPx() }).roundToInt()
                        IntOffset(x, y)
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }

        // Bolla con la lettera selezionata, a lato del dito
        if (selected in letters.indices && k > 0.05f) {
            val bubbleSize = 64.dp
            val bubblePx = with(density) { bubbleSize.toPx() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        val dx = with(density) { (if (leftSide) 96.dp else (-96).dp).roundToPx() }
                        IntOffset(dx, (touchY - bubblePx / 2f).roundToInt())
                    }
                    .align(Alignment.TopCenter)
                    .requiredSize(bubbleSize)
                    .graphicsLayer { alpha = k; scaleX = 0.6f + 0.4f * k; scaleY = 0.6f + 0.4f * k }
                    .clip(CircleShape)
                    .background(colors.accent)
            ) {
                Text(
                    text = letters[selected].toString(),
                    color = colors.scrim,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
