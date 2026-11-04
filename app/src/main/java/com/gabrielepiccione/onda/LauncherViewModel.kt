package com.gabrielepiccione.onda

import android.app.Application
import android.content.pm.ShortcutInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.CalendarEvent
import com.gabrielepiccione.onda.data.ContactHit
import com.gabrielepiccione.onda.data.ContactsRepository
import com.gabrielepiccione.onda.data.NotificationItem
import com.gabrielepiccione.onda.data.NotificationStore
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.normalizedForSearch
import com.gabrielepiccione.onda.util.Calculator
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
) {
    private val byKey: Map<String, AppInfo> by lazy { (allApps + hiddenApps).associateBy { it.key } }
    fun app(key: String?): AppInfo? = key?.let { byKey[it] }
}

data class SearchResults(
    val query: String = "",
    val calc: String? = null,
    val apps: List<AppInfo> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ondaApp = app as OndaApp
    val appsRepo = ondaApp.apps
    val settingsRepo = ondaApp.settings
    val widgets = ondaApp.widgets
    val media = ondaApp.media
    val calendar = ondaApp.calendar
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
                mono = r.mono,
                isWorkProfile = r.isWorkProfile,
                isFavorite = r.key in settings.favorites,
                isHidden = r.key in settings.hidden,
                swipeTarget = settings.swipeActions[r.key],
            )
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }

        val byKey = all.associateBy { it.key }
        LauncherState(
            allApps = all.filterNot { it.isHidden },
            favorites = settings.favorites.mapNotNull { byKey[it] }.filterNot { it.isHidden },
            hiddenApps = all.filter { it.isHidden },
            notifications = notifs.filterKeys { it !in settings.mutedNotifications },
            settings = settings,
            loaded = raw.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    // --- Calendario ---
    private val _nextEvent = MutableStateFlow<CalendarEvent?>(null)
    val nextEvent: StateFlow<CalendarEvent?> = _nextEvent.asStateFlow()

    fun refreshCalendar() {
        viewModelScope.launch { _nextEvent.value = calendar.nextEvent() }
    }

    // --- Tasto Home premuto: chiude pannelli e torna in cima ---
    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed
    fun onHomePressed() { _homePressed.tryEmit(Unit) }

    // --- Ricerca ---
    private val _search = MutableStateFlow(SearchResults())
    val search: StateFlow<SearchResults> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun updateSearch(query: String) {
        searchJob?.cancel()
        _search.value = _search.value.copy(query = query, apps = filterApps(query), calc = Calculator.evaluate(query))
        searchJob = viewModelScope.launch {
            delay(120)
            _search.value = _search.value.copy(contacts = contacts.search(query))
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchResults()
    }

    /** Ricerca app: prefisso > inizio parola > iniziali > contiene. Include le app nascoste. */
    private fun filterApps(query: String): List<AppInfo> {
        val q = query.normalizedForSearch().trim()
        if (q.isEmpty()) return emptyList()
        val s = state.value
        return (s.allApps + s.hiddenApps).mapNotNull { app ->
            val label = app.label.normalizedForSearch()
            val words = label.split(' ', '-', '.', '_', '(', ')').filter { it.isNotEmpty() }
            val initials = words.joinToString("") { it.take(1) }
            val score = when {
                label.startsWith(q) -> 0
                words.any { it.startsWith(q) } -> 1
                q.length >= 2 && initials.startsWith(q) -> 2
                label.contains(q) -> 3
                q.length >= 3 && app.packageName.contains(q) -> 4
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
    fun setSwipeAction(app: AppInfo, target: AppInfo?) = settingsRepo.setSwipeAction(app.key, target?.key)
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
            if (settingsRepo.current.onboardingDone) return@launch
            val apps = state.value.allApps.ifEmpty { return@launch }
            val wanted = listOf("dialer", "phone", "messag", "camera", "chrome", "browser", "whatsapp", "photos", "gallery")
            val picks = wanted.mapNotNull { w -> apps.firstOrNull { it.packageName.contains(w, ignoreCase = true) } }
                .distinctBy { it.key }
                .take(6)
            settingsRepo.edit { it.copy(favorites = picks.map { a -> a.key }, onboardingDone = true) }
        }
    }
}
