package com.pedroaba.spix.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pedroaba.spix.domain.VideoPreviewState

@Composable
fun VideoThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(modifier.background(containerColor), contentAlignment = Alignment.Center) {
        Icon(SpixIcons.Library, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) AsyncImage(model = url, contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = contentScale)
    }
}

@Composable
fun VideoPreview(state: VideoPreviewState, motion: Boolean, onRetry: () -> Unit) {
    AnimatedVisibility(
        visible = state !is VideoPreviewState.Idle,
        enter = if (motion) fadeIn(tween(180)) + expandVertically(tween(260)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(120)) + shrinkVertically(tween(220)) else ExitTransition.None,
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { when (it) {
                VideoPreviewState.Idle -> "idle"
                is VideoPreviewState.Loading -> "loading:${it.videoId}"
                is VideoPreviewState.Ready -> "ready:${it.video.videoId}"
                is VideoPreviewState.PlaylistReady -> "playlist:${it.playlist.playlistId}"
                is VideoPreviewState.Failed -> "failed:${it.videoId}"
            } },
            transitionSpec = {
                if (motion) (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(280)) { it / 24 })
                    .togetherWith(fadeOut(tween(140)))
                else EnterTransition.None.togetherWith(ExitTransition.None).using(
                    SizeTransform { _, _ -> tween(0) }
                )
            },
            label = "videoPreview",
        ) { displayed -> VideoPreviewContent(displayed, motion, onRetry) }
    }
}

@Composable
private fun VideoPreviewContent(state: VideoPreviewState, motion: Boolean, onRetry: () -> Unit) {
    when (state) {
        VideoPreviewState.Idle -> Unit
        is VideoPreviewState.Loading -> VideoPreviewSkeleton(motion)
        is VideoPreviewState.Failed -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(SpixIcons.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Column {
                    Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text("Tentar novamente", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }
        is VideoPreviewState.PlaylistReady -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                VideoThumbnail(state.playlist.videos.firstOrNull()?.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.playlist.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Playlist · ${state.playlist.videos.size} ${if (state.playlist.videos.size == 1) "vídeo" else "vídeos"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Todos os vídeos disponíveis serão adicionados aos downloads.", style = MaterialTheme.typography.bodyMedium)
                    if (state.playlist.unavailableCount > 0) Text("Itens indisponíveis: ${state.playlist.unavailableCount}.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        is VideoPreviewState.Ready -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                VideoThumbnail(state.video.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.video.title, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                    Text(state.video.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text("Vídeo com áudio · até 720p", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewSkeleton(motion: Boolean) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = motion && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val progress: State<Float>? = if (animate) {
        val transition = rememberInfiniteTransition(label = "videoSkeleton")
        transition.animateFloat(
            initialValue = -1f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer",
        )
    } else null
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    fun Modifier.placeholder() = clip(RoundedCornerShape(6.dp)).background(base).drawWithCache {
        val brush = Brush.linearGradient(listOf(base, highlight, base), Offset.Zero, Offset(size.width, size.height))
        onDrawBehind {
            // Read animation state during drawing; no recomposition or shader allocation per frame.
            progress?.let { state -> translate(left = size.width * state.value) { drawRect(brush) } }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Buscando informações do YouTube"
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).placeholder())
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.92f).height(20.dp).placeholder())
                Box(Modifier.fillMaxWidth(0.66f).height(20.dp).placeholder())
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.4f).height(16.dp).placeholder())
                Box(Modifier.fillMaxWidth(0.26f).height(12.dp).placeholder())
            }
        }
    }
}
