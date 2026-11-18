package com.inofarm.jogja.core.design

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's own light/dark choice. It ignores the system theme, and light is the default. */
enum class ThemeMode(val stored: String) {
    LIGHT("light"), DARK("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.stored == value } ?: LIGHT
    }
}

interface ThemeModeStorage {
    fun read(): String?
    fun write(value: String)
}

/** A device setting, shared by every account on the phone and kept across logout. */
class ThemePreference(private val storage: ThemeModeStorage) {
    private val state = MutableStateFlow(ThemeMode.fromStored(storage.read()))
    val mode: StateFlow<ThemeMode> = state.asStateFlow()

    fun select(mode: ThemeMode) {
        if (state.value == mode) return
        storage.write(mode.stored)
        state.value = mode
    }
}

class SharedPreferencesThemeStorage(context: Context) : ThemeModeStorage {
    private val preferences = context.applicationContext.getSharedPreferences("inofarm_display", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("theme_mode", null)
    override fun write(value: String) = preferences.edit { putString("theme_mode", value) }
}

@Immutable
class FarmThemeControl(val mode: ThemeMode, val select: (ThemeMode) -> Unit) {
    val dark: Boolean get() = mode == ThemeMode.DARK
}

/** The single source of dark mode for every composable. Never read isSystemInDarkTheme() directly. */
val LocalFarmTheme = staticCompositionLocalOf { FarmThemeControl(ThemeMode.LIGHT) {} }
