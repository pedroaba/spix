package com.pedroaba.spix.playback

import android.util.Rational
import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

internal val LocalPictureInPictureSourceBounds = staticCompositionLocalOf<(Rect) -> Unit> { {} }

internal fun canEnterPictureInPicture(info: PlaybackInfo): Boolean =
    info.connected && info.mediaId != null && info.video && info.playing && info.error == null

/** Android accepts PiP ratios only between 1:2.39 and 2.39:1. */
internal fun pictureInPictureAspect(aspect: Float): Rational =
    Rational((videoAspect(aspect).coerceIn(0.4185f, 2.39f) * 10_000).roundToInt(), 10_000)
