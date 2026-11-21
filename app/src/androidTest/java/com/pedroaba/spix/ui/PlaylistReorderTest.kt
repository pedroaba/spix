package com.pedroaba.spix.ui

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.MainActivity
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.domain.VideoMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistReorderTest {
    @Test fun draggingAndMenuActionsChangeTheSavedOrder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SpixApplication
        check(app.packageName.endsWith(".lab"))
        val automation = instrumentation.uiAutomation
        val playlistId = "reorder-test"
        val titles = listOf("Vídeo Alfa", "Vídeo Beta", "Vídeo Gama")
        val ids = listOf("reorder-a", "reorder-b", "reorder-c")
        val pending = "reorder-pending"

        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }

        fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            repeat(node.childCount) { index -> find(node.getChild(index), label)?.let { return it } }
            return null
        }

        suspend fun node(label: String): AccessibilityNodeInfo = withTimeoutOrNull(8_000) {
            while (true) {
                find(automation.rootInActiveWindow, label)?.let { return@withTimeoutOrNull it }
                delay(100)
            }
            null
        } ?: error("No accessible node for $label")

        fun tap(target: AccessibilityNodeInfo) {
            var button = target
            while (!button.isClickable) button = requireNotNull(button.parent)
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }

        try {
            app.repository.enqueueBatch(ids.zip(titles).map { (id, title) ->
                VideoMetadata(id, "https://example.com/$id", title, "Teste", null)
            })
            ids.forEach { id ->
                app.repository.updateDownload(id) { it.copy(status = DownloadStatus.Completed, localUri = "content://fixture/$id") }
            }
            app.localPlaylists.save(playlistId, "Playlist de teste", listOf(ids[0], pending, ids[1], ids[2]))
            shell("am start -W -n ${app.packageName}/${MainActivity::class.java.name}")
            tap(node("Playlists"))
            node("Suas playlists")
            tap(node("Playlist de teste"))

            val source = Rect().also(node("Reordenar ${titles[2]}")::getBoundsInScreen)
            val target = Rect().also(node("Reordenar ${titles[0]}")::getBoundsInScreen)
            val x = source.centerX().toFloat()
            val startY = source.centerY().toFloat()
            val endY = target.top + target.height() / 4f
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(automation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }
            inject(MotionEvent.ACTION_DOWN, startY)
            delay(ViewConfiguration.getLongPressTimeout().toLong() + 150)
            repeat(12) { step ->
                inject(MotionEvent.ACTION_MOVE, startY + (endY - startY) * (step + 1) / 12f)
                delay(20)
            }
            inject(MotionEvent.ACTION_UP, endY)
            val expected = listOf(ids[2], ids[0], pending, ids[1])
            withTimeout(10_000) {
                while (app.localPlaylists.get(playlistId)?.videoIds != expected) delay(100)
            }
            assertEquals("content://fixture/${ids[2]}", app.localPlaylists.firstAvailableUri(playlistId))
            suspend fun assertNewOrderIsVisible() {
                val updated = withTimeoutOrNull(8_000) {
                    while (true) {
                        val root = automation.rootInActiveWindow
                        val first = find(root, "Reordenar ${titles[2]}")
                        val second = find(root, "Reordenar ${titles[0]}")
                        if (first != null && second != null &&
                            Rect().also(first::getBoundsInScreen).top < Rect().also(second::getBoundsInScreen).top
                        ) return@withTimeoutOrNull true
                        delay(100)
                    }
                    false
                }
                assertTrue("The moved video must appear first", updated == true)
            }

            assertNewOrderIsVisible()
            tap(node("Voltar para playlists"))
            node("Suas playlists")
            tap(node("Playlist de teste"))
            assertNewOrderIsVisible()

            tap(node("Opções de ${titles[2]}"))
            tap(node("Mover para baixo"))
            val movedDown = listOf(ids[0], ids[2], pending, ids[1])
            withTimeout(10_000) {
                while (app.localPlaylists.get(playlistId)?.videoIds != movedDown) delay(100)
            }
            tap(node("Opções de ${titles[2]}"))
            tap(node("Mover para cima"))
            withTimeout(10_000) {
                while (app.localPlaylists.get(playlistId)?.videoIds != expected) delay(100)
            }
            assertNewOrderIsVisible()
        } finally {
            app.localPlaylists.delete(playlistId)
            ids.forEach { app.repository.removeRequest(it) }
        }
    }
}
