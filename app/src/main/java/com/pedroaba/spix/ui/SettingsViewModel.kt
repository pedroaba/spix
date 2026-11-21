package com.pedroaba.spix.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroaba.spix.data.SettingsRepository
import com.pedroaba.spix.domain.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings
    val location = repository.location
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init { refreshLocation() }

    fun refreshLocation() { viewModelScope.launch { repository.refreshLocation() } }
    fun setTheme(theme: ThemeMode) = save { repository.setTheme(theme) }
    fun setAnimations(enabled: Boolean) = save { repository.setAnimations(enabled) }
    fun setHomeCards(enabled: Boolean) = save { repository.setHomeCards(enabled) }
    fun selectFolder(uri: Uri) = save { repository.selectFolder(uri); messages.send("Pasta dos vídeos atualizada.") }
    fun useDefaultFolder() = save { repository.useDefaultFolder(); messages.send("A pasta do Spix será usada nos próximos downloads.") }
    fun pickerUnavailable() { viewModelScope.launch { messages.send("Não foi possível abrir o seletor de pastas neste dispositivo.") } }

    private fun save(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { messages.send("Não foi possível salvar. Confira o acesso à pasta e tente novamente.") }
            finally { mutableBusy.value = false }
        }
    }
}
