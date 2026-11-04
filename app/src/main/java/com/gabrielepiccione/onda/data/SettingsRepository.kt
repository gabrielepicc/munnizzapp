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

    // --- Cartelle ---

    fun createFolder(name: String, firstApp: String?): String {
        val id = "f" + System.currentTimeMillis().toString(36)
        edit { s ->
            val apps = listOfNotNull(firstApp)
            s.copy(
                folders = s.folders + (id to Folder(id, name.ifBlank { "Cartella" }, apps)),
                favorites = (s.favorites - apps.toSet()) + (FOLDER_PREFIX + id),
            )
        }
        return id
    }

    fun renameFolder(id: String, name: String) = edit { s ->
        val f = s.folders[id] ?: return@edit s
        s.copy(folders = s.folders + (id to f.copy(name = name.ifBlank { f.name })))
    }

    /** Elimina la cartella; le app tornano tra i preferiti. Restituisce i widget da rilasciare. */
    fun deleteFolder(id: String): List<Int> {
        val widgets = current.folders[id]?.widgets.orEmpty()
        edit { s ->
            val f = s.folders[id] ?: return@edit s
            val list = s.favorites.toMutableList()
            val at = list.indexOf(FOLDER_PREFIX + id).coerceAtLeast(0)
            list.remove(FOLDER_PREFIX + id)
            list.addAll(at.coerceAtMost(list.size), f.apps.filter { it !in list })
            s.copy(folders = s.folders - id, favorites = list)
        }
        return widgets
    }

    /** Sposta un'app in una cartella (null = fuori da tutte, tra i preferiti). */
    fun moveAppToFolder(appKey: String, folderId: String?) = edit { s ->
        val cleaned = s.folders.mapValues { (_, f) -> f.copy(apps = f.apps - appKey) }
        if (folderId == null) {
            s.copy(folders = cleaned, favorites = if (appKey in s.favorites) s.favorites else s.favorites + appKey)
        } else {
            val target = cleaned[folderId] ?: return@edit s
            s.copy(
                folders = cleaned + (folderId to target.copy(apps = target.apps + appKey)),
                favorites = s.favorites - appKey,
            )
        }
    }

    fun addWidgetToFolder(folderId: String, widgetId: Int) = edit { s ->
        val f = s.folders[folderId] ?: return@edit s
        s.copy(folders = s.folders + (folderId to f.copy(widgets = f.widgets + widgetId)))
    }

    fun removeWidgetFromFolder(folderId: String, widgetId: Int) = edit { s ->
        val f = s.folders[folderId] ?: return@edit s
        s.copy(folders = s.folders + (folderId to f.copy(widgets = f.widgets - widgetId)))
    }

    fun setAppWidget(appKey: String, widgetId: Int?) = edit { s ->
        s.copy(appWidgets = if (widgetId == null) s.appWidgets - appKey else s.appWidgets + (appKey to widgetId))
    }

    fun recordLaunch(appKey: String) = edit { s ->
        s.copy(launchCounts = s.launchCounts + (appKey to (s.launchCounts[appKey] ?: 0) + 1))
    }
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
        put("folders", JSONArray().apply {
            s.folders.values.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id); put("name", f.name)
                    put("apps", JSONArray(f.apps)); put("widgets", JSONArray(f.widgets))
                })
            }
        })
        put("appWidgets", JSONObject(s.appWidgets as Map<*, *>))
        put("launchCounts", JSONObject(s.launchCounts as Map<*, *>))
        put("swipeDown", s.swipeDown.name)
        put("swipeUp", s.swipeUp.name)
        put("hideStatusBar", s.hideStatusBar)
        put("hideAlphabet", s.hideAlphabet)
        put("hideFavoriteNames", s.hideFavoriteNames)
        put("iconSize", s.iconSize.toDouble())
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
            folders = o.optJSONArray("folders")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.associate { f ->
                    val id = f.getString("id")
                    id to Folder(
                        id = id,
                        name = f.optString("name", "Cartella"),
                        apps = f.optJSONArray("apps")?.let { x -> (0 until x.length()).map { x.getString(it) } } ?: emptyList(),
                        widgets = f.optJSONArray("widgets")?.let { x -> (0 until x.length()).map { x.getInt(it) } } ?: emptyList(),
                    )
                }
            } ?: emptyMap(),
            appWidgets = o.optJSONObject("appWidgets")?.let { m -> m.keys().asSequence().associateWith { m.getInt(it) } } ?: emptyMap(),
            launchCounts = o.optJSONObject("launchCounts")?.let { m -> m.keys().asSequence().associateWith { m.getInt(it) } } ?: emptyMap(),
            swipeDown = GestureAction.entries.firstOrNull { it.name == o.optString("swipeDown") } ?: d.swipeDown,
            swipeUp = GestureAction.entries.firstOrNull { it.name == o.optString("swipeUp") } ?: d.swipeUp,
            hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
            hideAlphabet = o.optBoolean("hideAlphabet", d.hideAlphabet),
            hideFavoriteNames = o.optBoolean("hideFavoriteNames", d.hideFavoriteNames),
            iconSize = o.optDouble("iconSize", d.iconSize.toDouble()).toFloat(),
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
