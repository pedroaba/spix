package com.pedroaba.spix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pedroaba.spix.ui.SpixApp
import com.pedroaba.spix.ui.theme.SpixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as SpixApplication
        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(if (app.settingsRepository.settings.value.theme.isDark(systemDark)) R.style.Theme_Spix_Dark else R.style.Theme_Spix_Light)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SpixTheme(darkTheme = dark) { SpixApp(app.repository, app.settingsRepository, app.metadataRepository, app.downloadScheduler) }
        }
    }
}
