package com.gabrielepiccione.onda.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Colonna di lettere sul bordo. Trascinando, le lettere vicine al dito si gonfiano e
 * si spostano verso il centro con un profilo gaussiano che insegue il dito con una molla.
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
    val hapticsOn = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var selected by remember { mutableIntStateOf(-1) }
    val intensity = remember { Animatable(0f) }
    val waveY = remember { Animatable(0f) }      // posizione animata del picco (px)

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
                        scope.launch { waveY.snapTo(down.position.y) }
                        scope.launch { intensity.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }

                        fun update(y: Float) {
                            scope.launch {
                                waveY.animateTo(y, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f))
                            }
                            val idx = (y / step).toInt().coerceIn(0, currentLetters.lastIndex)
                            if (idx != selected) {
                                selected = idx
                                if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        scope.launch { intensity.animateTo(0f, tween(320, easing = Motion.Standard)) }
                    }
                }
        )

        val maxShiftPx = with(density) { 52.dp.toPx() }
        val sigma = 2.4f
        val k = intensity.value
        val waveCenter = waveY.value / step - 0.5f
        val letterHalf = with(density) { 7.sp.toPx() }

        letters.forEachIndexed { i, letter ->
            val d = i - waveCenter
            val wave = exp(-(d * d) / (2 * sigma * sigma)) * k
            val isSel = i == selected
            Text(
                text = letter.toString(),
                color = if (isSel) colors.accent else colors.onWallpaperMuted.copy(alpha = 0.6f + 0.4f * wave),
                fontSize = 11.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val shift = (maxShiftPx * wave).roundToInt()
                        IntOffset(if (leftSide) shift else -shift, (i * step + step / 2f - letterHalf).roundToInt())
                    }
                    .graphicsLayer {
                        val scale = 1f + 1.2f * wave
                        scaleX = scale; scaleY = scale
                    }
            )
        }

        // Bolla della lettera selezionata (forma Material "pill" che segue il dito)
        if (selected in letters.indices && k > 0.02f) {
            val bubbleSize = 68.dp
            val bubblePx = with(density) { bubbleSize.toPx() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val dx = with(density) { (if (leftSide) 104.dp else (-104).dp).roundToPx() }
                        IntOffset(dx, (waveY.value - bubblePx / 2f).roundToInt())
                    }
                    .requiredSize(bubbleSize)
                    .graphicsLayer {
                        alpha = k.coerceIn(0f, 1f)
                        val s = 0.5f + 0.5f * k
                        scaleX = s; scaleY = s
                    }
                    .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = if (leftSide) 12 else 50, bottomEndPercent = if (leftSide) 50 else 12))
                    .background(colors.accentContainer)
            ) {
                Text(
                    text = letters[selected].toString(),
                    color = colors.onAccentContainer,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
