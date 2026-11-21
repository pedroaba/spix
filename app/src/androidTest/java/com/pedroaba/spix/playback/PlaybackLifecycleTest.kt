package com.pedroaba.spix.playback

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.MainActivity
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PlaybackLifecycleTest {
    @Test fun homeAndScreenOffKeepPlayingButRemovingTaskClearsPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".lab"))
        val fixture = File(context.cacheDir, "playback-lifecycle.mp4")
        instrumentation.context.assets.open("media-fixture.mp4").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        fun launch() {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
        }
        fun awaitMain(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                var done = false
                instrumentation.runOnMainSync { done = condition() }
                if (done) return
                Thread.sleep(100)
            }
            fail("Player did not reach expected lifecycle state")
        }
        launch()
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync {
            future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        val controller = requireNotNull(future).get(15, TimeUnit.SECONDS)
        try {
            for (paused in listOf(false, true)) {
                launch()
                instrumentation.runOnMainSync {
                    controller.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(fixture)))
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.prepare()
                    controller.play()
                }
                awaitMain { controller.isPlaying }
                if (paused) {
                    instrumentation.runOnMainSync { controller.pause() }
                    awaitMain { !controller.playWhenReady }
                } else {
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
                    Thread.sleep(500)
                    awaitMain { controller.isPlaying }
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
                    Thread.sleep(500)
                    awaitMain { controller.isPlaying }
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
                }
                val tasks = context.getSystemService(ActivityManager::class.java).appTasks
                assertTrue("Lab app must have a task to dismiss", tasks.isNotEmpty())
                tasks.forEach { it.finishAndRemoveTask() }
                awaitMain { !controller.isConnected || (!controller.playWhenReady && controller.mediaItemCount == 0 && controller.playbackState == Player.STATE_IDLE) }
                launch()
                instrumentation.runOnMainSync {
                    assertEquals(0, controller.mediaItemCount)
                    assertFalse(controller.playWhenReady)
                }
            }
        } finally {
            instrumentation.runOnMainSync { MediaController.releaseFuture(requireNotNull(future)) }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
            fixture.delete()
        }
    }
}
