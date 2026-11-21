package com.pedroaba.spix.download

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.MainActivity
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit real-network smoke test, isolated from the personal installation. */
class BackgroundDownloadTest {
    @Test fun failedVideoClearsItsProgressNotification() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SpixApplication
        check(app.packageName.endsWith(".lab"))
        app.repository.awaitReady()
        app.downloadScheduler.awaitStartup()
        delay(1000)
        check(!app.repository.hasQueued())
        val id = "background-failure-smoke"
        check(app.repository.get(id) == null)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertTrue("Grant lab POST_NOTIFICATIONS before this test", manager.areNotificationsEnabled())
        var published = false
        try {
            app.repository.enqueue(VideoMetadata(id, "not-a-valid-url", "Teste de falha", "Spix", null))
            withTimeout(60_000) {
                VideoTransferTask(app, "failure-smoke") { info ->
                    manager.notify(info.notificationId, info.notification)
                    published = true
                }.run(id)
            }
            assertTrue(published)
            assertEquals(DownloadStatus.Failed, app.repository.get(id)?.status)
            assertFalse(manager.activeNotifications.any { it.notification.channelId == "downloads" })
        } finally {
            app.repository.removeRequest(id)
        }
    }

    @Test fun twoVideosFinishAfterHomeScreenOffAndTaskRemovalAndRemoveNotification() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realDownloads") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SpixApplication
        check(app.packageName.endsWith(".lab"))
        val ids = listOf("background-smoke-1", "background-smoke-2")
        app.repository.awaitReady()
        check(!app.repository.hasQueued()) { "Lab queue must be idle" }
        check(ids.all { app.repository.get(it) == null })
        try {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(2000)
            val videos = ids.map { VideoMetadata(it, "https://www.youtube.com/watch?v=jNQXAC9IVRw", "Teste de segundo plano · $it", "Spix", null) }
            app.repository.enqueueBatch(videos)
            app.downloadScheduler.startBatch(ids)
            withTimeout(30_000) {
                while (app.repository.get(ids.first())?.status == DownloadStatus.Queued) delay(200)
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            val tasks = app.getSystemService(android.app.ActivityManager::class.java).appTasks
            assertTrue("Lab app must have a task to dismiss", tasks.isNotEmpty())
            tasks.forEach { it.finishAndRemoveTask() }
            withTimeout(300_000) {
                while (ids.any { app.repository.get(it)?.status != DownloadStatus.Completed }) {
                    ids.forEach { assertNotEquals("Transfer failed: ${app.repository.get(it)?.error}", DownloadStatus.Failed, app.repository.get(it)?.status) }
                    delay(1000)
                }
            }
            withTimeout(10_000) {
                val manager = app.getSystemService(NotificationManager::class.java)
                while (manager.activeNotifications.any { it.notification.channelId == "downloads" }) delay(200)
            }
            ids.forEach { assertNotNull(app.repository.get(it)?.localUri) }
        } finally {
            app.downloadScheduler.cancelAll()
            ids.forEach { id ->
                app.repository.get(id)?.localUri?.let { app.videoStorage.deleteVideo(Uri.parse(it)) }
                app.repository.removeRequest(id)
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
        }
    }
}
