package com.pedroaba.spix.benchmark

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import com.pedroaba.spix.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only packaged in the isolated .benchmark application, never in the personal app. */
class SeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName.endsWith(".benchmark"))
        val count = intent.getIntExtra("count", 1000).coerceIn(0, 5000)
        Thread {
            try {
                deleteDatabase("spix.db")
                val covers = File(filesDir, "fixture-covers").apply { mkdirs() }
                val video = File(filesDir, "fixture.mp4")
                if (!video.exists()) assets.open("fixture.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
                val bitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 58f; color = Color.WHITE }
                val rows = JSONArray()
                repeat(count) { i ->
                    val id = "bench%06d".format(i)
                    val cover = File(covers, "$id.jpg")
                    if (!cover.exists()) {
                        canvas.drawColor(Color.rgb((i * 71) % 210, (i * 43) % 210, (i * 97) % 210))
                        canvas.drawText("Spix $i", 24f, 160f, paint)
                        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    }
                    rows.put(JSONObject().put("id", id).put("url", "https://www.youtube.com/watch?v=$id")
                        .put("at", count - i).put("status", "Completed").put("progress", 100)
                        .put("localUri", android.net.Uri.fromFile(video).toString()).put("duration", "0:02")
                        .put("metadata", JSONObject().put("title", "Vídeo %04d · Acervo de desempenho".format(i))
                            .put("author", "Artista ${i % 20}").put("thumbnail", android.net.Uri.fromFile(cover).toString())))
                }
                bitmap.recycle()
                getSharedPreferences("spix", MODE_PRIVATE).edit().putString("requests", rows.toString()).commit()
                val playlists = JSONArray()
                repeat(16) { index ->
                    playlists.put(JSONObject().put("id", "fixture-playlist-$index").put("name", "Playlist ${index + 1}")
                        .put("videos", JSONArray((0 until count).filter { it % 4 == index % 4 }.map { "bench%06d".format(it) })))
                }
                getSharedPreferences("spix_playlists", MODE_PRIVATE).edit().putString("items", playlists.toString()).commit()
                getSharedPreferences("spix_settings", MODE_PRIVATE).edit().putString("theme", "Light").putBoolean("navigation_animations", true).commit()
                runOnUiThread { startActivity(Intent(this, MainActivity::class.java)); finish() }
            } catch (failure: Throwable) {
                android.util.Log.e("SpixFixture", "Could not seed benchmark fixture", failure)
                runOnUiThread { finish() }
            }
        }.start()
    }
}
