@file:OptIn(ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielepiccione.onda.HomeEntry
import com.gabrielepiccione.onda.LauncherViewModel
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.FOLDER_PREFIX
import com.gabrielepiccione.onda.data.Folder
import com.gabrielepiccione.onda.data.GestureAction
import com.gabrielepiccione.onda.data.NotificationStore
import com.gabrielepiccione.onda.data.WidgetManager
import com.gabrielepiccione.onda.service.OndaAccessibilityService
import com.gabrielepiccione.onda.util.SystemActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

enum class Overlay { NONE, ALL_APPS, SEARCH, SETTINGS }

/** Dove finisce un widget appena scelto. */
sealed interface WidgetTarget {
    data object Home : WidgetTarget
    data class InFolder(val folderId: String) : WidgetTarget
    data class ForApp(val appKey: String) : WidgetTarget
}

/** Azioni che richiedono l'Activity (permessi, risultati di altre schermate). */
interface HomeCallbacks {
    fun requestContactsPermission()
    fun requestCalendarPermission()
    fun requestDefaultLauncher()
    fun openNotificationAccess()
    fun openAccessibilitySettings()
    fun pickWidget(provider: WidgetManager.Provider, target: WidgetTarget)
    fun exportBackup()
    fun importBackup()
}

data class HomeStatus(
    val notificationAccess: Boolean,
    val isDefaultLauncher: Boolean,
    val contactsPermission: Boolean,
    val calendarPermission: Boolean,
    val accessibilityEnabled: Boolean,
)

@Composable
fun HomeScreen(vm: LauncherViewModel, callbacks: HomeCallbacks, status: HomeStatus) {
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val media by vm.media.media.collectAsStateWithLifecycle()
    val nextEvent by vm.nextEvent.collectAsStateWithLifecycle()
    val settings = state.settings
    val colors = LocalOndaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var actionsFor by remember { mutableStateOf<AppInfo?>(null) }
    var panelFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderSheet by remember { mutableStateOf<Folder?>(null) }
    var moveFor by remember { mutableStateOf<AppInfo?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var widgetFilter by remember { mutableStateOf<String?>(null) }
    var widgetTarget by remember { mutableStateOf<WidgetTarget>(WidgetTarget.Home) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    val homeList = rememberLazyListState()
    val allAppsList = rememberLazyListState()
    val sections = remember(state.allApps) { buildSections(state.allApps) }
    val letters = remember(sections) { sections.map { it.letter } }
    val iconScale = settings.iconSize

    fun closeAll() {
        overlay = Overlay.NONE
        vm.clearSearch()
    }

    fun launchApp(app: AppInfo) {
        vm.launch(app)
        scope.launch { delay(380); closeAll() }
    }

    fun openWidgetPicker(filter: String?, target: WidgetTarget) {
        widgetFilter = filter
        widgetTarget = target
        widgetPicker = true
    }

    fun perform(action: GestureAction) {
        when (action) {
            GestureAction.SEARCH -> overlay = Overlay.SEARCH
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.ALL_APPS -> overlay = Overlay.ALL_APPS
            GestureAction.NONE -> Unit
        }
    }

    LaunchedEffect(state.loaded) { if (state.loaded) vm.seedFavoritesIfNeeded() }

    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            if (overlay == Overlay.NONE && actionsFor == null && panelFor == null) homeList.animateScrollToItem(0)
            closeAll()
            actionsFor = null
            panelFor = null
            folderSheet = null
            homeMenu = false
            widgetPicker = false
        }
    }

    // Back predittivo (Android 14+): il pannello si rimpicciolisce seguendo il gesto
    PredictiveBackHandler(enabled = overlay != Overlay.NONE) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            closeAll()
        } catch (e: CancellationException) {
            // gesto annullato
        } finally {
            backProgress = 0f
        }
    }

    // ---------- Gesti verticali interattivi ----------
    // "pull" segue il dito quando la lista è già al bordo; rilasciando oltre la soglia parte l'azione.
    val threshold = with(density) { 96.dp.toPx() }
    val pull = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val currentSettings by rememberUpdatedState(settings)
    val gestureConnection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Se stiamo "tirando" e il dito torna indietro, consumiamo prima il tiro
                val p = pull.value
                if (source == NestedScrollSource.UserInput && p != 0f && sign(available.y) != sign(p)) {
                    val newP = if (abs(available.y) > abs(p)) 0f else p + available.y
                    val consumed = newP - p
                    scope.launch { pull.snapTo(newP) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val action = if (available.y > 0) currentSettings.swipeDown else currentSettings.swipeUp
                if (action == GestureAction.NONE) return Offset.Zero
                val next = pull.value + available.y
                scope.launch { pull.snapTo(next) }
                val nowArmed = abs(next) >= threshold
                if (nowArmed != armed) {
                    armed = nowArmed
                    if (currentSettings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val p = pull.value
                if (p == 0f) return Velocity.Zero
                val fire = abs(p) >= threshold || (abs(p) > threshold / 3 && abs(available.y) > 2200f && sign(available.y) == sign(p))
                armed = false
                if (fire) perform(if (p > 0) currentSettings.swipeDown else currentSettings.swipeUp)
                scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                return available
            }
        }
    }
    // Resistenza elastica: il contenuto si muove meno del dito
    fun rubber(p: Float): Float = sign(p) * sqrt(abs(p)) * 9f

    // Profondità: quando si apre un pannello la home arretra e sfuma
    // Profondità solo per ricerca e impostazioni; l'elenco app resta sullo stesso piano
    val depth by animateFloatAsState(
        if (overlay == Overlay.SEARCH || overlay == Overlay.SETTINGS) 1f else 0f,
        Motion.enter(450),
        label = "depth",
    )
    val listMode by animateFloatAsState(
        if (overlay == Overlay.ALL_APPS) 1f else 0f,
        androidx.compose.animation.core.tween(140, easing = Motion.Standard),
        label = "listMode",
    )
    val panelScale = 1f - 0.1f * backProgress
    val panelCorner = (32 * backProgress).dp

    val dimColor = if (colors.dark) Color.Black else Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(dimColor.copy(alpha = settings.wallpaperDim))
    ) {
        val bars = WindowInsets.systemBars.asPaddingValues()
        val letterSide = 48.dp
        val hiddenSide = 28.dp

        // ---------------- HOME ----------------
        LazyColumn(
            state = homeList,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
            contentPadding = PaddingValues(
                start = if (settings.leftHanded) letterSide else hiddenSide,
                end = if (settings.leftHanded) hiddenSide else letterSide,
                top = bars.calculateTopPadding() + 24.dp,
                bottom = bars.calculateBottomPadding() + 28.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.06f * depth
                    scaleX = s; scaleY = s
                    val pr = (abs(pull.value) / threshold).coerceIn(0f, 1f)
                    alpha = (1f - depth) * (1f - 0.35f * pr) * (1f - listMode)
                    translationY = rubber(pull.value)
                }
                .nestedScroll(gestureConnection)
                .pointerInput(settings.doubleTapLock) {
                    detectTapGestures(
                        onLongPress = {
                            if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            homeMenu = true
                        },
                        onDoubleTap = if (settings.doubleTapLock) {
                            { _: Offset -> if (!OndaAccessibilityService.lockScreen()) callbacks.openAccessibilitySettings() }
                        } else null,
                    )
                }
        ) {
            if (settings.showClock) {
                item(key = "clock") {
                    ClockHeader(
                        event = if (settings.showCalendar) nextEvent else null,
                        onTime = { SystemActions.openAlarms(context) },
                        onDate = { SystemActions.openCalendar(context) },
                        onEvent = { vm.calendar.open(it) },
                    )
                }
            }
            item(key = "media") {
                val m = media
                AnimatedVisibility(
                    visible = settings.showMedia && m != null,
                    enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                    exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
                ) {
                    if (m != null) {
                        MediaCard(
                            media = m,
                            onOpen = { vm.media.open() },
                            onPlayPause = { vm.media.playPause() },
                            onPrev = { vm.media.previous() },
                            onNext = { vm.media.next() },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (settings.widgetIds.isNotEmpty()) {
                item(key = "widgets") { WidgetsArea(settings.widgetIds, vm.widgets, Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
            }
            item(key = "spacer") { Spacer(Modifier.size(12.dp)) }
            items(state.home, key = { it.key }) { entry ->
                val itemMod = Modifier.animateItem(
                    fadeInSpec = Motion.enter(),
                    placementSpec = Motion.offsetEnter,
                    fadeOutSpec = Motion.exit(),
                )
                when (entry) {
                    is HomeEntry.App -> {
                        val app = entry.app
                        val target = state.app(app.swipeTarget)
                        FavoriteRow(
                            app = app,
                            swipeTarget = target,
                            notifications = state.notifications[app.packageName].orEmpty(),
                            showIcon = settings.showIcons,
                            themedIcons = settings.themedIcons,
                            hideName = settings.hideFavoriteNames,
                            iconScale = iconScale,
                            onClick = { launchApp(app) },
                            onLongClick = { actionsFor = app },
                            onSwipe = { if (target != null) launchApp(target) else panelFor = app },
                            onNotificationClick = { NotificationStore.open(context, it) },
                            onNotificationDismiss = { NotificationStore.dismiss(it) },
                            modifier = itemMod,
                        )
                    }
                    is HomeEntry.Group -> FolderRow(
                        folder = entry.folder,
                        apps = entry.apps,
                        widgets = vm.widgets,
                        notifications = state.notifications,
                        showIcons = settings.showIcons,
                        themedIcons = settings.themedIcons,
                        iconScale = iconScale,
                        onApp = { launchApp(it) },
                        onAppLongClick = { actionsFor = it },
                        onLongClick = { folderSheet = entry.folder },
                        modifier = itemMod,
                    )
                }
            }
            if (state.loaded && state.home.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Trascina sulle lettere a lato per trovare un'app, poi tienila premuta e scegli «Aggiungi ai preferiti».",
                        color = colors.onWallpaperMuted,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            if (!status.notificationAccess || !status.isDefaultLauncher) {
                item(key = "hints") {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!status.notificationAccess) {
                            HintChip(Icons.Filled.Notifications, "Notifiche qui") { callbacks.openNotificationAccess() }
                        }
                        if (!status.isDefaultLauncher) {
                            HintChip(Icons.Filled.Settings, "Imposta come home") { callbacks.requestDefaultLauncher() }
                        }
                    }
                }
            }
        }

        // Indicatore del gesto verticale (cresce col dito, si "accende" oltre la soglia)
        val pv = pull.value
        if (pv != 0f) {
            val action = if (pv > 0) settings.swipeDown else settings.swipeUp
            val progress = (abs(pv) / threshold).coerceIn(0f, 1.2f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(if (pv > 0) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(top = bars.calculateTopPadding() + 12.dp, bottom = bars.calculateBottomPadding() + 12.dp)
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        val s = 0.6f + 0.4f * progress.coerceIn(0f, 1f)
                        scaleX = s; scaleY = s
                        translationY = rubber(pv) * 0.5f
                    }
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (armed) colors.accent else colors.panelHigh)
            ) {
                Icon(
                    when (action) {
                        GestureAction.SEARCH -> Icons.Filled.Search
                        GestureAction.NOTIFICATIONS -> Icons.Filled.Notifications
                        GestureAction.ALL_APPS -> Icons.Filled.List
                        GestureAction.NONE -> Icons.Filled.Close
                    },
                    null,
                    tint = if (armed) colors.onAccent else colors.onWallpaper,
                )
            }
        }

        // ---------------- TUTTE LE APP (stessa pagina, nessun pannello) ----------------
        // L'elenco è sempre composto (pronto all'istante) e sostituisce i preferiti con una
        // dissolvenza breve, sopra lo stesso sfondo: si resta "nella stessa pagina".
        val appsFloor = remember(threshold) {
            object : NestedScrollConnection {
                var pullDown = 0f
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) pullDown += available.y
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    // In cima all'elenco, tirando giù si torna ai preferiti
                    if (pullDown > threshold || (pullDown > threshold / 3 && available.y > 2200f)) closeAll()
                    pullDown = 0f
                    return Velocity.Zero
                }
            }
        }
        AllAppsPanel(
            sections = sections,
            listState = allAppsList,
            activeLetter = null,
            notifications = state.notifications,
            showIcons = settings.showIcons,
            themedIcons = settings.themedIcons,
            onClick = { launchApp(it) },
            onLongClick = { actionsFor = it },
            contentPadding = PaddingValues(
                start = if (settings.leftHanded) letterSide else hiddenSide,
                end = if (settings.leftHanded) hiddenSide else letterSide,
                top = bars.calculateTopPadding() + 16.dp,
                bottom = bars.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier
                .zIndex(if (overlay == Overlay.ALL_APPS) 1f else -1f)
                .graphicsLayer { alpha = listMode }
                .nestedScroll(appsFloor),
        )

        // ---------------- SCROLLER (lettere visibili + lato opposto invisibile) ----------------
        val scrollerActive = overlay == Overlay.NONE || overlay == Overlay.ALL_APPS
        val lettersVisible = overlay == Overlay.ALL_APPS || !settings.hideAlphabet
        val scrollerAlpha by animateFloatAsState(if (scrollerActive) 1f else 0f, Motion.enter(250), label = "scroller")
        val scrollerPadding = Modifier.padding(
            top = bars.calculateTopPadding() + 72.dp,
            bottom = bars.calculateBottomPadding() + 40.dp,
        )
        val onLetter: (Char) -> Unit = { letter ->
            sections.firstOrNull { it.letter == letter }?.let { s ->
                scope.launch { allAppsList.scrollToItem(s.firstIndex) }
            }
        }
        if (scrollerAlpha > 0.01f) {
            AlphabetWaveScroller(
                letters = letters,
                leftSide = settings.leftHanded,
                showLetters = lettersVisible,
                modifier = Modifier
                    .align(if (settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                    .zIndex(2f)
                    .graphicsLayer { alpha = scrollerAlpha }
                    .then(scrollerPadding)
                    .padding(horizontal = 6.dp),
                onTouchStart = { overlay = Overlay.ALL_APPS },
                onLetter = onLetter,
            )
            AlphabetWaveScroller(
                letters = letters,
                leftSide = !settings.leftHanded,
                showLetters = false,
                modifier = Modifier
                    .align(if (settings.leftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                    .zIndex(2f)
                    .then(scrollerPadding),
                onTouchStart = { overlay = Overlay.ALL_APPS },
                onLetter = onLetter,
            )
        }

        // ---------------- RICERCA ----------------
        AnimatedVisibility(
            visible = overlay == Overlay.SEARCH,
            enter = fadeIn(Motion.enter(260)) + slideInVertically(Motion.offsetEnter) { -it / 12 },
            exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.offsetExit) { -it / 16 },
        ) {
            Box(
                Modifier
                    .graphicsLayer { scaleX = panelScale; scaleY = panelScale }
                    .clip(RoundedCornerShape(panelCorner))
            ) {
                SearchPanel(
                    results = search,
                    suggestions = state.suggestions,
                    showIcons = settings.showIcons,
                    themedIcons = settings.themedIcons,
                    contactsPermission = status.contactsPermission,
                    onQueryChange = vm::updateSearch,
                    onApp = { launchApp(it) },
                    onAppLongClick = { actionsFor = it },
                    onContact = { vm.contacts.open(it); scope.launch { delay(380); closeAll() } },
                    onWebSearch = { SystemActions.webSearch(context, it); scope.launch { delay(380); closeAll() } },
                    onRequestContacts = callbacks::requestContactsPermission,
                    onClose = { closeAll() },
                )
            }
        }

        // ---------------- IMPOSTAZIONI ----------------
        AnimatedVisibility(
            visible = overlay == Overlay.SETTINGS,
            enter = fadeIn(Motion.enter(300)) + slideInVertically(Motion.offsetEnter) { it / 8 },
            exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.offsetExit) { it / 12 },
        ) {
            Box(
                Modifier
                    .graphicsLayer { scaleX = panelScale; scaleY = panelScale }
                    .clip(RoundedCornerShape(panelCorner))
            ) {
                SettingsScreen(
                    settings = settings,
                    hiddenApps = state.hiddenApps,
                    status = SettingsStatus(
                        isDefaultLauncher = status.isDefaultLauncher,
                        notificationAccess = status.notificationAccess,
                        calendarPermission = status.calendarPermission,
                        accessibilityEnabled = status.accessibilityEnabled,
                    ),
                    widgets = vm.widgets,
                    actions = object : SettingsActions {
                        override fun edit(transform: (com.gabrielepiccione.onda.data.Settings) -> com.gabrielepiccione.onda.data.Settings) = vm.updateSettings(transform)
                        override fun requestDefault() = callbacks.requestDefaultLauncher()
                        override fun notificationAccess() = callbacks.openNotificationAccess()
                        override fun calendarPermission() = callbacks.requestCalendarPermission()
                        override fun accessibility() = callbacks.openAccessibilitySettings()
                        override fun addWidget() = openWidgetPicker(null, WidgetTarget.Home)
                        override fun removeWidget(id: Int) = vm.removeWidget(id)
                        override fun unhide(app: AppInfo) = vm.setHidden(app, false)
                        override fun wallpaper() = SystemActions.openWallpaperPicker(context)
                        override fun exportBackup() = callbacks.exportBackup()
                        override fun importBackup() = callbacks.importBackup()
                        override fun close() = closeAll()
                    },
                )
            }
        }
    }

    // ---------------- FOGLI MODALI ----------------
    panelFor?.let { app ->
        val shortcuts = remember(app.key) { vm.shortcuts(app) }
        AppPanelSheet(
            app = app,
            shortcuts = shortcuts,
            shortcutIcon = { vm.appsRepo.shortcutIcon(it) },
            notifications = state.notifications[app.packageName].orEmpty(),
            widgetId = settings.appWidgets[app.key],
            widgets = vm.widgets,
            themedIcons = settings.themedIcons,
            onOpenApp = { launchApp(app) },
            onShortcut = vm::startShortcut,
            onNotification = { NotificationStore.open(context, it) },
            onDismissNotification = { NotificationStore.dismiss(it) },
            onAddWidget = { openWidgetPicker(app.packageName, WidgetTarget.ForApp(app.key)) },
            onRemoveWidget = { vm.removeAppWidget(app) },
            onMoreOptions = { actionsFor = app },
            onDismiss = { panelFor = null },
        )
    }

    actionsFor?.let { app ->
        val favIndex = settings.favorites.indexOf(app.key)
        val shortcuts = remember(app.key) { vm.shortcuts(app) }
        val inFolder = settings.folders.values.firstOrNull { app.key in it.apps }
        AppActionsSheet(
            app = app,
            actions = AppActions(
                shortcuts = shortcuts,
                shortcutIcon = { vm.appsRepo.shortcutIcon(it) },
                swipeTarget = state.app(app.swipeTarget),
                allApps = state.allApps.filter { it.key != app.key },
                themedIcons = settings.themedIcons,
                onSetSwipe = { vm.setSwipeAction(app, it) },
                folderName = inFolder?.name,
                onMoveToFolder = { moveFor = app },
                isMuted = app.packageName in settings.mutedNotifications,
                favoriteIndex = favIndex,
                favoriteCount = settings.favorites.size,
                onShortcut = vm::startShortcut,
                onToggleFavorite = {
                    if (inFolder != null) vm.moveToFolder(app, null) else vm.toggleFavorite(app)
                },
                onMove = { delta -> vm.moveFavorite(app, delta) },
                onRename = { vm.rename(app, it) },
                onToggleHidden = { vm.setHidden(app, !app.isHidden) },
                onToggleMuted = { vm.toggleMuted(app) },
                onAddWidget = { openWidgetPicker(app.packageName, WidgetTarget.Home) },
                onInfo = { vm.appsRepo.openAppInfo(app) },
                onUninstall = { SystemActions.uninstall(context, app) },
            ),
            onDismiss = { actionsFor = null },
        )
    }

    moveFor?.let { app ->
        MoveToFolderDialog(
            folders = settings.folders.values.toList(),
            currentFolderId = settings.folders.values.firstOrNull { app.key in it.apps }?.id,
            onPick = { id -> vm.moveToFolder(app, id); moveFor = null },
            onCreate = { name ->
                vm.createFolder(name, app)
                moveFor = null
            },
            onDismiss = { moveFor = null },
        )
    }

    folderSheet?.let { f ->
        val live = settings.folders[f.id] ?: f
        val idx = settings.favorites.indexOf(FOLDER_PREFIX + f.id)
        FolderSheet(
            folder = live,
            widgets = vm.widgets,
            canMoveUp = idx > 0,
            canMoveDown = idx in 0 until settings.favorites.lastIndex,
            onRename = { vm.renameFolder(f.id, it) },
            onMove = { vm.moveEntry(FOLDER_PREFIX + f.id, it) },
            onAddWidget = { openWidgetPicker(null, WidgetTarget.InFolder(f.id)) },
            onRemoveWidget = { vm.removeFolderWidget(f.id, it) },
            onDelete = { vm.deleteFolder(f.id) },
            onDismiss = { folderSheet = null },
        )
    }

    if (homeMenu) {
        var newFolder by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { homeMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.panel,
            contentColor = colors.onWallpaper,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SheetRow(Icons.Filled.Settings, "Impostazioni") { homeMenu = false; overlay = Overlay.SETTINGS }
                SheetRow(Icons.Filled.Add, "Nuova cartella") { newFolder = true }
                SheetRow(Icons.Filled.Add, "Aggiungi widget") { homeMenu = false; openWidgetPicker(null, WidgetTarget.Home) }
                SheetRow(Icons.Filled.Search, "Cerca") { homeMenu = false; overlay = Overlay.SEARCH }
                SheetRow(Icons.Filled.Face, "Cambia sfondo") { homeMenu = false; SystemActions.openWallpaperPicker(context) }
            }
        }
        if (newFolder) {
            NameDialog("Nuova cartella", "", onDismiss = { newFolder = false }) { name ->
                vm.createFolder(name, null)
                newFolder = false
                homeMenu = false
            }
        }
    }

    if (widgetPicker) {
        WidgetPickerSheet(
            widgets = vm.widgets,
            packageFilter = widgetFilter,
            onPick = { p -> widgetPicker = false; callbacks.pickWidget(p, widgetTarget) },
            onDismiss = { widgetPicker = false },
        )
    }
}

@Composable
private fun HintChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.accentContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = colors.onAccentContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = colors.onAccentContainer, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = colors.onWallpaper)
    }
}
