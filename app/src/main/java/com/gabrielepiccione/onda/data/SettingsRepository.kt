package com.gabrielepiccione.onda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Preferenze persistenti del launcher, salvate come un unico JSON (comodo anche per backup/ripristino). */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("onda_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(prefs.getString(K_JSON, null)?.let(::fromJson) ?: Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    fun edit(transform: (Settings) -> Settings) {
        _settings.update(transform)
        prefs.edit().putString(K_JSON, toJson(_settings.value).toString()).apply()
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

    fun setSwipeAction(key: String, target: String?) = edit { s ->
        val map = s.swipeActions.toMutableMap()
        if (target == null) map.remove(key) else map[key] = target
        s.copy(swipeActions = map)
    }

    fun toggleMuted(packageName: String) = edit { s ->
        s.copy(
            mutedNotifications = if (packageName in s.mutedNotifications) s.mutedNotifications - packageName
            else s.mutedNotifications + packageName
        )
    }

    fun addWidget(id: Int) = edit { it.copy(widgetIds = it.widgetIds + id) }
    fun removeWidget(id: Int) = edit { it.copy(widgetIds = it.widgetIds - id) }

    // --- Backup ---

    /** JSON delle impostazioni (senza i widget, che sono legati a questo dispositivo). */
    fun exportJson(): String = toJson(current.copy(widgetIds = emptyList())).toString(2)

    fun importJson(json: String): Boolean {
        val parsed = runCatching { fromJson(json) }.getOrNull() ?: return false
        edit { parsed.copy(widgetIds = it.widgetIds, onboardingDone = true) }
        return true
    }

    // --- Serializzazione ---

    private fun toJson(s: Settings): JSONObject = JSONObject().apply {
        put("version", 2)
        put("favorites", JSONArray(s.favorites))
        put("hidden", JSONArray(s.hidden.toList()))
        put("renames", JSONObject(s.renames as Map<*, *>))
        put("swipeActions", JSONObject(s.swipeActions as Map<*, *>))
        put("muted", JSONArray(s.mutedNotifications.toList()))
        put("themeMode", s.themeMode.name)
        put("dynamicColor", s.dynamicColor)
        put("accent", s.accent)
        put("font", s.font.name)
        put("iconPack", s.iconPack)
        put("themedIcons", s.themedIcons)
        put("showIcons", s.showIcons)
        put("textScale", s.textScale.toDouble())
        put("showClock", s.showClock)
        put("showCalendar", s.showCalendar)
        put("showMedia", s.showMedia)
        put("wallpaperDim", s.wallpaperDim.toDouble())
        put("leftHanded", s.leftHanded)
        put("doubleTapLock", s.doubleTapLock)
        put("haptics", s.haptics)
        put("widgets", JSONArray(s.widgetIds))
        put("onboardingDone", s.onboardingDone)
    }

    private fun fromJson(json: String): Settings {
        val o = JSONObject(json)
        val d = Settings()
        fun strings(name: String): List<String> =
            o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        fun map(name: String): Map<String, String> =
            o.optJSONObject(name)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
        return Settings(
            favorites = strings("favorites"),
            hidden = strings("hidden").toSet(),
            renames = map("renames"),
            swipeActions = map("swipeActions"),
            mutedNotifications = strings("muted").toSet(),
            themeMode = ThemeMode.entries.firstOrNull { it.name == o.optString("themeMode") } ?: d.themeMode,
            dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
            accent = o.optLong("accent", d.accent),
            font = FontChoice.entries.firstOrNull { it.name == o.optString("font") } ?: d.font,
            iconPack = o.optString("iconPack", d.iconPack),
            themedIcons = o.optBoolean("themedIcons", d.themedIcons),
            showIcons = o.optBoolean("showIcons", d.showIcons),
            textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
            showClock = o.optBoolean("showClock", d.showClock),
            showCalendar = o.optBoolean("showCalendar", d.showCalendar),
            showMedia = o.optBoolean("showMedia", d.showMedia),
            wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
            leftHanded = o.optBoolean("leftHanded", d.leftHanded),
            doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
            haptics = o.optBoolean("haptics", d.haptics),
            widgetIds = o.optJSONArray("widgets")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
            onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone),
        )
    }

    private companion object {
        const val K_JSON = "settings_json"
    }
}
