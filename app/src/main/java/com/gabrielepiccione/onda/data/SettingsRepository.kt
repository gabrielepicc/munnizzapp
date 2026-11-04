package com.gabrielepiccione.onda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Preferenze persistenti del launcher (SharedPreferences + StateFlow). */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("onda_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    fun edit(transform: (Settings) -> Settings) {
        _settings.update(transform)
        save(_settings.value)
    }

    // --- Operazioni di comodo ---

    fun toggleFavorite(key: String) = edit { s ->
        if (key in s.favorites) s.copy(favorites = s.favorites - key)
        else s.copy(favorites = s.favorites + key)
    }

    fun moveFavorite(key: String, delta: Int) = edit { s ->
        val list = s.favorites.toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return@edit s
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.removeAt(from)
        list.add(to, key)
        s.copy(favorites = list)
    }

    fun setHidden(key: String, hidden: Boolean) = edit { s ->
        s.copy(
            hidden = if (hidden) s.hidden + key else s.hidden - key,
            favorites = if (hidden) s.favorites - key else s.favorites,
        )
    }

    fun rename(key: String, newLabel: String?) = edit { s ->
        val map = s.renames.toMutableMap()
        if (newLabel.isNullOrBlank()) map.remove(key) else map[key] = newLabel.trim()
        s.copy(renames = map)
    }

    fun toggleMuted(packageName: String) = edit { s ->
        s.copy(
            mutedNotifications = if (packageName in s.mutedNotifications) s.mutedNotifications - packageName
            else s.mutedNotifications + packageName
        )
    }

    fun addWidget(id: Int) = edit { it.copy(widgetIds = it.widgetIds + id) }
    fun removeWidget(id: Int) = edit { it.copy(widgetIds = it.widgetIds - id) }

    // --- Serializzazione ---

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            favorites = prefs.getString(K_FAV, null)?.split(SEP)?.filter { it.isNotBlank() } ?: d.favorites,
            hidden = prefs.getStringSet(K_HIDDEN, null)?.toSet() ?: d.hidden,
            renames = prefs.getString(K_RENAMES, null)?.let(::decodeMap) ?: d.renames,
            mutedNotifications = prefs.getStringSet(K_MUTED, null)?.toSet() ?: d.mutedNotifications,
            themeMode = prefs.getString(K_THEME, null)
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: d.themeMode,
            accent = prefs.getLong(K_ACCENT, d.accent),
            showIcons = prefs.getBoolean(K_ICONS, d.showIcons),
            textScale = prefs.getFloat(K_TEXT, d.textScale),
            showClock = prefs.getBoolean(K_CLOCK, d.showClock),
            wallpaperDim = prefs.getFloat(K_DIM, d.wallpaperDim),
            leftHanded = prefs.getBoolean(K_LEFT, d.leftHanded),
            widgetIds = prefs.getString(K_WIDGETS, null)
                ?.split(SEP)?.mapNotNull { it.toIntOrNull() } ?: d.widgetIds,
            onboardingDone = prefs.getBoolean(K_ONBOARD, d.onboardingDone),
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putString(K_FAV, s.favorites.joinToString(SEP))
            .putStringSet(K_HIDDEN, s.hidden)
            .putString(K_RENAMES, encodeMap(s.renames))
            .putStringSet(K_MUTED, s.mutedNotifications)
            .putString(K_THEME, s.themeMode.name)
            .putLong(K_ACCENT, s.accent)
            .putBoolean(K_ICONS, s.showIcons)
            .putFloat(K_TEXT, s.textScale)
            .putBoolean(K_CLOCK, s.showClock)
            .putFloat(K_DIM, s.wallpaperDim)
            .putBoolean(K_LEFT, s.leftHanded)
            .putString(K_WIDGETS, s.widgetIds.joinToString(SEP))
            .putBoolean(K_ONBOARD, s.onboardingDone)
            .apply()
    }

    private fun encodeMap(map: Map<String, String>): String = JSONObject(map as Map<*, *>).toString()

    private fun decodeMap(json: String): Map<String, String> = runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }.getOrDefault(emptyMap())

    private companion object {
        const val SEP = "\n"
        const val K_FAV = "favorites"
        const val K_HIDDEN = "hidden"
        const val K_RENAMES = "renames"
        const val K_MUTED = "muted"
        const val K_THEME = "theme"
        const val K_ACCENT = "accent"
        const val K_ICONS = "show_icons"
        const val K_TEXT = "text_scale"
        const val K_CLOCK = "show_clock"
        const val K_DIM = "wallpaper_dim"
        const val K_LEFT = "left_handed"
        const val K_WIDGETS = "widgets"
        const val K_ONBOARD = "onboarding_done"
    }
}
