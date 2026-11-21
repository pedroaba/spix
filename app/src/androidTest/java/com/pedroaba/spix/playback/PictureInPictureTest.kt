package com.pedroaba.spix.playback

import android.app.ActivityManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pedroaba.spix.MainActivity
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PictureInPictureTest {
    @Test fun floatingVideoKeepsItsPlayerAndTracksAcrossHomeAndReturn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".lab"))
        val fixture = File(context.cacheDir, "pip-fixture.mp4")
        instrumentation.context.assets.open("media-fixture.mp4").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun awaitMain(message: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                var done = false
                onMain { done = condition() }
                if (done) return
                Thread.sleep(100)
            }
            fail(message)
        }
        fun launch(openPlayer: Boolean = false) {
            // Launch like the launcher, including when the test process has no visible task.
            val action = if (openPlayer) " -a $OPEN_PLAYER" else ""
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -W -n ${context.packageName}/${MainActivity::class.java.name}$action"
            )).use { it.readBytes() }
        }
        fun playerView(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) repeat(view.childCount) { index ->
                playerView(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        fun findControl(node: android.view.accessibility.AccessibilityNodeInfo?, label: String): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.contains(label) == true) return node
            repeat(node.childCount) { index -> findControl(node.getChild(index), label)?.let { return it } }
            return null
        }
        fun assertTimelineAtVideoEdge(view: PlayerView) {
            instrumentation.waitForIdleSync()
            val slider = requireNotNull(findControl(instrumentation.uiAutomation.rootInActiveWindow, "Posição da reprodução"))
            val timeline = android.graphics.Rect().also(slider::getBoundsInScreen)
            val video = android.graphics.Rect()
            onMain { requireNotNull(view.videoSurfaceView).getGlobalVisibleRect(video) }
            assertTrue("Timeline must meet the video edge: $timeline vs $video", kotlin.math.abs(timeline.bottom - video.bottom) <= 2)
            val minimumHeight = (56 * view.resources.displayMetrics.density).toInt()
            assertTrue("Timeline touch area $timeline must be at least $minimumHeight px", timeline.height() >= minimumHeight)
        }
        launch(true)
        var activity: MainActivity? = null
        awaitMain("Activity did not resume") {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
            activity != null
        }
        val owner = requireNotNull(activity)
        lateinit var vm: PlaybackViewModel
        onMain { vm = ViewModelProvider(owner)[PlaybackViewModel::class.java] }
        awaitMain("Media controller did not connect") { vm.controller != null }
        val controller = requireNotNull(vm.controller)
        var rebufferCount = 0
        var videoDisableCount = 0
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) rebufferCount++
            }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) videoDisableCount++
            }
        }
        try {
            onMain {
                vm.video(true)
                controller.setMediaItem(MediaItem.fromUri(Uri.fromFile(fixture)))
                controller.repeatMode = Player.REPEAT_MODE_ONE
                controller.prepare()
                controller.play()
            }
            awaitMain("Fixture did not play") { controller.isPlaying && playerView(owner.window.decorView) != null }
            lateinit var originalView: PlayerView
            onMain {
                originalView = requireNotNull(playerView(owner.window.decorView))
                controller.addListener(listener)
            }
            assertTimelineAtVideoEdge(originalView)
            if (InstrumentationRegistry.getArguments().getString("timelinePreview") == "true") {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.getExternalFilesDir(null), "timeline-preview.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            repeat(3) { iteration ->
                instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
                awaitMain("Home did not open PiP") { owner.isInPictureInPictureMode && controller.isPlaying }
                awaitMain("PiP did not hide the normal player interface") {
                    val view = playerView(owner.window.decorView)
                    view?.contentDescription == "Vídeo em janela flutuante" && view.height >= owner.window.decorView.height * 0.9f
                }
                Thread.sleep(700)
                onMain {
                    assertSame("PiP must retain the video view", originalView, playerView(owner.window.decorView))
                    assertFalse(controller.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
                }
                if (iteration == 0) {
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(context.getExternalFilesDir(null), "pip-preview.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                launch()
                awaitMain("PiP did not expand") { !owner.isInPictureInPictureMode && controller.isPlaying }
                Thread.sleep(700)
                onMain { assertSame(originalView, playerView(owner.window.decorView)) }
            }
            awaitMain("Normal player did not regain focus") {
                owner.hasWindowFocus() && originalView.contentDescription == "Vídeo" &&
                    originalView.height < owner.window.decorView.height * 0.6f
            }
            val videoBounds = android.graphics.Rect()
            onMain { originalView.getGlobalVisibleRect(videoBounds) }
            instrumentation.uiAutomation.executeShellCommand("input tap ${videoBounds.centerX()} ${videoBounds.centerY()}").close()
            Thread.sleep(500)
            var fullscreenButton = findControl(instrumentation.uiAutomation.rootInActiveWindow, "Tela cheia")
            while (fullscreenButton != null && !fullscreenButton.isClickable) fullscreenButton = fullscreenButton.parent
            assertNotNull("Fullscreen control must be accessible", fullscreenButton)
            assertTrue(requireNotNull(fullscreenButton).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            awaitMain("Fullscreen did not expand the video") { originalView.height >= owner.window.decorView.height * 0.8f }
            onMain { assertSame(originalView, playerView(owner.window.decorView)) }
            assertTimelineAtVideoEdge(originalView)
            instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
            awaitMain("Fullscreen did not enter PiP") {
                owner.isInPictureInPictureMode && playerView(owner.window.decorView)?.contentDescription == "Vídeo em janela flutuante"
            }
            launch()
            awaitMain("Fullscreen did not restore after PiP") { !owner.isInPictureInPictureMode && controller.isPlaying }
            onMain { assertSame(originalView, playerView(owner.window.decorView)); owner.onBackPressedDispatcher.onBackPressed() }
            onMain {
                controller.removeListener(listener)
                assertEquals("Transitions must not rebuffer", 0, rebufferCount)
                assertEquals("Transitions must not disable video", 0, videoDisableCount)
                vm.video(false)
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
            Thread.sleep(700)
            onMain { assertFalse(owner.isInPictureInPictureMode); assertTrue(controller.isPlaying) }
            launch()
            awaitMain("Activity did not resume for paused-video test") { owner.hasWindowFocus() }
            onMain { vm.video(true); controller.pause() }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
            Thread.sleep(700)
            onMain { assertFalse(owner.isInPictureInPictureMode); assertFalse(controller.playWhenReady) }
            launch()
            awaitMain("Activity did not resume for task removal") { owner.hasWindowFocus() }
            onMain { controller.play() }
            awaitMain("Playback did not resume") { controller.isPlaying }
            context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
            awaitMain("Removing the task did not stop playback") {
                !controller.isConnected || (!controller.playWhenReady && controller.mediaItemCount == 0)
            }
        } finally {
            onMain {
                if (controller.isConnected) { controller.removeListener(listener); vm.stop() }
            }
            fixture.delete()
        }
    }

    @Test fun extremeAndInvalidRatiosStayWithinAndroidLimits() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 0.01f, 100f, 9f / 16f, 16f / 9f).forEach {
            val ratio = pictureInPictureAspect(it).toFloat()
            assertTrue(ratio >= 1f / 2.39f && ratio <= 2.39f)
        }
    }
}
