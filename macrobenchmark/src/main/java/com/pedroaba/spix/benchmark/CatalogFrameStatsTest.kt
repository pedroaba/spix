package com.pedroaba.spix.benchmark

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-checks HWUI counters without Perfetto's tracing overhead on the physical device. */
class CatalogFrameStatsTest {
    @Test fun catalogScrollWithoutTracing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val target = "com.pedroaba.spix.benchmark"
        val count = InstrumentationRegistry.getArguments().getString("catalogSize", "1000")!!.toInt()
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("cmd package compile -m speed -f $target")
        device.executeShellCommand("am start -W -n $target/com.pedroaba.spix.benchmark.SeedActivity --ei count $count")
        assertTrue(device.wait(Until.hasObject(By.text("Início")), 30000))
        val memory = mutableListOf<String>()
        val frames = mutableListOf<String>()
        repeat(3) {
            device.executeShellCommand("am start -W -f 0x10008000 -n $target/com.pedroaba.spix.MainActivity")
            device.waitForIdle()
            fun swipe() {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4, 20)
                device.waitForIdle()
            }
            swipe()
            device.executeShellCommand("dumpsys gfxinfo $target reset")
            repeat(8) { swipe() }
            frames += device.executeShellCommand("dumpsys gfxinfo $target")
            memory += device.executeShellCommand("dumpsys meminfo $target")
        }
        instrumentation.sendStatus(2, Bundle().apply {
            putString("catalogPssKiB", memory.mapNotNull { Regex("TOTAL PSS:\\s+(\\d+)").find(it)?.groupValues?.get(1) }.joinToString(","))
            putString("catalogJankPercent", frames.mapNotNull { Regex("Janky frames: \\d+ \\(([0-9.]+)%\\)").find(it)?.groupValues?.get(1) }.joinToString(","))
            frames.forEachIndexed { index, report -> putString("catalogFrameStats$index", report) }
            memory.forEachIndexed { index, report -> putString("catalogMemory$index", report) }
        })
        device.executeShellCommand("am force-stop $target")
    }
}
