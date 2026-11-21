package com.pedroaba.spix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.pedroaba.spix.playback.PlayerAccess
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pedroaba.spix.playback.LocalPlayerAccess

@Composable
fun OpenVideoButton(value: String) {
    val playback = LocalPlayerAccess.current
    TextButton(onClick = { playback?.play(value) }, enabled = playback != null) { Text("Reproduzir") }
}

/** Makes available media playable from the whole card, including keyboard and accessibility activation. */
@Composable
fun Modifier.playVideoOnClick(value: String?): Modifier {
    val playback = LocalPlayerAccess.current
    return if (value == null || playback == null) this else clickable(
        role = Role.Button,
        onClickLabel = "Reproduzir",
        onClick = { playback.play(value) },
    )
}

private fun PlayerAccess.play(value: String) {
    vm.open(value)
    open()
}
