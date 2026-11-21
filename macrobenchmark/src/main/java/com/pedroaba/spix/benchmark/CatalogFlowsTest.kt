package com.pedroaba.spix.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises every catalog entry point against synthetic files in the isolated app. */
class CatalogFlowsTest {
    @Test fun searchPlaylistEditorAndThousandItemPlayer() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val target = "com.pedroaba.spix.benchmark"
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $target/com.pedroaba.spix.benchmark.SeedActivity --ei count 1000")
        assertTrue(device.wait(Until.hasObject(By.text("1000 itens")), 30000))
        device.findObject(By.desc("Buscar músicas")).click()
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5000))
        device.findObject(By.clazz("android.widget.EditText")).text = "0999"
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("Vídeo 0999")), 5000))
        device.pressBack()
        device.waitForIdle()
        device.findObject(By.text("Biblioteca")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Playlist 1")), 5000))
        device.findObject(By.text("Playlist 1")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Editar")), 5000))
        assertTrue(device.wait(Until.hasObject(By.text("250 itens disponíveis offline")), 5000))
        device.findObject(By.text("Editar")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Salvar")), 5000))
        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        assertEquals(2, fields.size)
        fields.last().text = "0999"
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("Vídeo 0999")), 5000))
        device.findObject(By.text("Cancelar")).click()
        device.pressBack()
        device.waitForIdle()
        device.findObject(By.text("Início")).click()
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("Vídeo 0000")), 5000))
        device.findObject(By.textStartsWith("Vídeo 0000")).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("de 1000 vídeos")), 15000))
        // Keep the two-second local fixture from advancing while checking the queue.
        device.findObject(By.desc("Pausar"))?.click()
        repeat(4) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 2, 20)
            device.waitForIdle()
        }
        assertTrue(device.hasObject(By.textContains("de 1000 vídeos")))
        device.findObject(By.desc("Opções de reprodução"))?.click()
        device.findObject(By.text("Encerrar reprodução"))?.click()
        device.executeShellCommand("am force-stop $target")
    }
}
