package com.pedroaba.spix.domain

enum class ThemeMode(val label: String) {
    System("Sistema"), Light("Claro"), Dark("Escuro");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val navigationAnimations: Boolean = true,
    val homeCards: Boolean = false,
    val videoFolderUri: String? = null,
)

data class StorageLocation(
    val name: String,
    val description: String,
    val isCustom: Boolean,
    val available: Boolean = true,
)
