package com.pedroaba.spix.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackTimeline(
    vm: PlaybackViewModel,
    mediaId: String?,
    duration: Long,
    showControls: Boolean,
    modifier: Modifier = Modifier,
) {
    val position by vm.position.collectAsStateWithLifecycle()
    var preview by remember(mediaId, duration) { mutableStateOf<Float?>(null) }
    val maximum = duration.coerceAtLeast(1).toFloat()
    val value = (preview ?: position.toFloat()).coerceIn(0f, maximum)
    val enabled = mediaId != null && duration > 0
    val played = Color(0xFFFF0033).copy(alpha = if (enabled) 1f else .38f)

    val touchHeight = 56.dp
    val thumbRadius = 5.dp
    val thickness = if (showControls || preview != null) 3.dp else 2.dp
    Slider(
        value = value,
        onValueChange = { preview = it },
        onValueChangeFinished = {
            preview?.let { vm.seek(it.roundToLong()) }
            preview = null
        },
        valueRange = 0f..maximum,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(touchHeight).semantics {
            contentDescription = "Posição da reprodução"
            stateDescription = playbackTime(value.roundToLong())
        },
        thumb = {
            Canvas(Modifier.width(thumbRadius * 2).height(touchHeight)) {
                if (enabled && (showControls || preview != null)) {
                    drawCircle(played, thumbRadius.toPx(), Offset(center.x, size.height - thickness.toPx() / 2))
                }
            }
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(touchHeight)) {
                val rtl = layoutDirection == LayoutDirection.Rtl
                val start = if (rtl) size.width else 0f
                val end = if (rtl) 0f else size.width
                val fraction = (state.value / maximum).coerceIn(0f, 1f)
                val stroke = thickness.toPx()
                val y = size.height - stroke / 2
                val inset = thumbRadius.toPx()
                val outerStart = if (rtl) size.width + inset else -inset
                val outerEnd = if (rtl) -inset else size.width + inset
                drawLine(Color.White.copy(alpha = .32f), Offset(outerStart, y), Offset(outerEnd, y), stroke)
                if (fraction > 0f) {
                    val progress = if (fraction == 1f) outerEnd else start + (end - start) * fraction
                    drawLine(played, Offset(outerStart, y), Offset(progress, y), stroke)
                }
            }
        },
    )
}
