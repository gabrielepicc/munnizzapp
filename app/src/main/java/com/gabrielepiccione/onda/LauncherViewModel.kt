package com.gabrielepiccione.onda

import android.app.Application
import android.content.pm.ShortcutInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.ContactHit
import com.gabrielepiccione.onda.data.ContactsRepository
import com.gabrielepiccione.onda.data.NotificationItem
import com.gabrielepiccione.onda.data.NotificationStore
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.normalizedForSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class LauncherState(
    val allApps: List<AppInfo> = emptyList(),     // visibili, ordinate A-Z
    val favorites: List<AppInfo> = emptyList(),   // nell'ordine scelto
    val hiddenApps: List<AppInfo> = emptyList(),
    val notifications: Map<String, List<NotificationItem>> = emptyMap(),
    val settings: Settings = Settings(),
    val loaded: Boolean = false,
)

data class SearchResults(
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ondaApp = app as OndaApp
    val appsRepo = ondaApp.apps
    val settingsRepo = ondaApp.settings
    val widgets = ondaApp.widgets
    val contacts = ContactsRepository(app)

    private val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    val state: StateFlow<LauncherState> = combine(
        appsRepo.apps,
        settingsRepo.settings,
        NotificationStore.byPackage,
    ) { raw, settings, notifs ->
        val all = raw.map { r ->
            AppInfo(
                key = r.key,
                component = r.component,
                user = r.user,
                label = settings.renames[r.key] ?: r.label,
                originalLabel = r.label,
                icon = r.icon,
                isWorkProfile = r.isWorkProfile,
                isFavorite = r.key in settings.favorites,
                isHidden = r.key in settings.hidden,
            )
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }

        val byKey = all.associateBy { it.key }
        val visibleNotifs = notifs.filterKeys { it !in settings.mutedNotifications }
        LauncherState(
            allApps = all.filterNot { it.isHidden },
            favorites = settings.favorites.mapNotNull { byKey[it] }.filterNot { it.isHidden },
            hiddenApps = all.filter { it.isHidden },
            notifications = visibleNotifs,
            settings = settings,
            loaded = raw.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    // --- Eventi "tasto Home premuto" per chiudere pannelli e tornare in cima ---
    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed
    fun onHomePressed() { _homePressed.tryEmit(Unit) }

    // --- Ricerca ---
    private val _search = MutableStateFlow(SearchResults())
    val search: StateFlow<SearchResults> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun updateSearch(query: String) {
        searchJob?.cancel()
        val apps = filterApps(query)
        _search.value = _search.value.copy(query = query, apps = apps)
        searchJob = viewModelScope.launch {
            delay(120)
            val hits = contacts.search(query)
            _search.value = _search.value.copy(contacts = hits)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchResults()
    }

    /** Ricerca app: prefisso > iniziali delle parole > contiene. Include le app nascoste. */
    private fun filterApps(query: String): List<AppInfo> {
        val q = query.normalizedForSearch().trim()
        if (q.isEmpty()) return emptyList()
        val s = state.value
        val pool = s.allApps + s.hiddenApps
        return pool.mapNotNull { app ->
            val label = app.label.normalizedForSearch()
            val initials = label.split(' ', '-', '.', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
            val score = when {
                label.startsWith(q) -> 0
                label.split(' ').any { it.startsWith(q) } -> 1
                initials.startsWith(q) -> 2
                label.contains(q) -> 3
                app.packageName.contains(q) -> 4
                else -> null
            }
            score?.let { it to app }
        }.sortedWith(compareBy<Pair<Int, AppInfo>> { it.first }.thenBy { it.second.label.lowercase() })
            .map { it.second }
            .take(8)
    }

    // --- Azioni sulle app ---
    fun launch(app: AppInfo) = appsRepo.launch(app)
    fun toggleFavorite(app: AppInfo) = settingsRepo.toggleFavorite(app.key)
    fun moveFavorite(app: AppInfo, delta: Int) = settingsRepo.moveFavorite(app.key, delta)
    fun setHidden(app: AppInfo, hidden: Boolean) = settingsRepo.setHidden(app.key, hidden)
    fun rename(app: AppInfo, label: String?) = settingsRepo.rename(app.key, label)
    fun toggleMuted(app: AppInfo) = settingsRepo.toggleMuted(app.packageName)
    fun shortcuts(app: AppInfo): List<ShortcutInfo> = appsRepo.shortcutsFor(app)
    fun startShortcut(s: ShortcutInfo) = appsRepo.startShortcut(s)
    fun updateSettings(transform: (Settings) -> Settings) = settingsRepo.edit(transform)

    fun removeWidget(id: Int) {
        widgets.deleteId(id)
        settingsRepo.removeWidget(id)
    }

    /** Al primo avvio mettiamo qualche app tra i preferiti per non partire da una home vuota. */
    fun seedFavoritesIfNeeded() {
        viewModelScope.launch {
            val s = settingsRepo.current
            if (s.onboardingDone) return@launch
            val apps = state.value.allApps.ifEmpty { return@launch }
            val wanted = listOf("phone", "dialer", "messag", "camera", "chrome", "browser", "whatsapp", "gallery", "photos")
            val picks = wanted.mapNotNull { w -> apps.firstOrNull { it.packageName.contains(w, ignoreCase = true) } }
                .distinctBy { it.key }
                .take(6)
            settingsRepo.edit { it.copy(favorites = picks.map { a -> a.key }, onboardingDone = true) }
        }
    }
}
