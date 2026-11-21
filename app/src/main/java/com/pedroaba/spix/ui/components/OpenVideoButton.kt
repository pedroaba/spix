package com.pedroaba.spix.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pedroaba.spix.playback.LocalPlayerAccess

@Composable
fun OpenVideoButton(value: String) {
    val playback = LocalPlayerAccess.current
    TextButton(onClick = { playback?.vm?.open(value); playback?.open?.invoke() }, enabled = playback != null) { Text("Reproduzir") }
}
