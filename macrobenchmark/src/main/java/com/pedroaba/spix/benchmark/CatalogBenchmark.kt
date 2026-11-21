package com.pedroaba.spix.benchmark

import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/** Runs only against the separate synthetic catalog, never the personal installation. */
class CatalogBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val target = "com.pedroaba.spix.benchmark"
    @Test fun catalogScroll() {
        val args = InstrumentationRegistry.getArguments()
        val count = args.getString("catalogSize", "1000")!!.toInt()
        val screen = args.getString("catalogScreen", "home")!!
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $target/com.pedroaba.spix.benchmark.SeedActivity --ei count $count")
        check(device.wait(Until.hasObject(By.text("Início")), 30000)) { "Fixture did not start" }
        val memory = mutableListOf<String>()
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(FrameTimingMetric()),
            iterations = 3,
            compilationMode = CompilationMode.Full(),
            setupBlock = {
                pressHome()
                startActivityAndWait(Intent().setClassName(target, "com.pedroaba.spix.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                device.waitForIdle()
                val label = when (screen) { "library" -> "Biblioteca"; "downloads" -> "Downloads"; else -> null }
                label?.let { device.findObject(By.text(it))?.click(); device.waitForIdle() }
                if (screen == "downloads") {
                    device.findObject(By.textStartsWith("Concluídos"))?.click()
                    device.waitForIdle()
                }
                // Move past the first screen outside the measured interval to warm renderer and thumbnails.
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 20)
                device.waitForIdle()
            },
        ) {
            repeat(8) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
                device.waitForIdle()
            }
            memory += device.executeShellCommand("dumpsys meminfo $target")
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            val pss = memory.mapNotNull { Regex("TOTAL PSS:\\s+(\\d+)").find(it)?.groupValues?.get(1) }
            putString("catalogPssKiB", pss.joinToString(","))
            memory.forEachIndexed { index, report -> putString("catalogMemory$index", report) }
        })
    }
}
