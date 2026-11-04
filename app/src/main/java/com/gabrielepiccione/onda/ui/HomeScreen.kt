@file:OptIn(ExperimentalMaterial3Api::class)

package com.gabrielepiccione.onda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielepiccione.onda.LauncherViewModel
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.NotificationStore
import com.gabrielepiccione.onda.util.SystemActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Overlay { NONE, ALL_APPS, SEARCH, SETTINGS }

/** Azioni che richiedono l'Activity (permessi, risultati di altre schermate). */
interface HomeCallbacks {
    fun requestContactsPermission()
    fun requestDefaultLauncher()
    fun openNotificationAccess()
    fun pickWidget(provider: com.gabrielepiccione.onda.data.WidgetManager.Provider)
}

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    callbacks: HomeCallbacks,
    notificationAccess: Boolean,
    isDefaultLauncher: Boolean,
    contactsPermission: Boolean,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val settings = state.settings
    val colors = LocalOndaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var actionsFor by remember { mutableStateOf<AppInfo?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var widgetFilter by remember { mutableStateOf<String?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    val homeList = rememberLazyListState()
    val allAppsList = rememberLazyListState()
    val sections = remember(state.allApps) { buildSections(state.allApps) }
    val letters = remember(sections) { sections.map { it.letter } }

    fun closeAll() {
        overlay = Overlay.NONE
        activeLetter = null
        vm.clearSearch()
    }

    fun launchApp(app: AppInfo) {
        vm.launch(app)
        // Chiudiamo i pannelli con un attimo di ritardo per non vedere il "salto" prima dell'animazione d'apertura
        scope.launch { delay(350); closeAll() }
    }

    LaunchedEffect(state.loaded) { if (state.loaded) vm.seedFavoritesIfNeeded() }

    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            if (overlay == Overlay.NONE && actionsFor == null) homeList.animateScrollToItem(0)
            closeAll()
            actionsFor = null
            homeMenu = false
            widgetPicker = false
        }
    }

    BackHandler(enabled = overlay != Overlay.NONE) { closeAll() }

    // Gesti in home: tirare giù = notifiche, tirare su = ricerca
    val threshold = with(density) { 72.dp.toPx() }
    val gestureConnection = remember(threshold) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pull += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val p = pull
                pull = 0f
                when {
                    p > threshold || (p > threshold / 3 && available.y > 2500f) -> SystemActions.expandNotifications(context)
                    p < -threshold || (p < -threshold / 3 && available.y < -2500f) -> overlay = Overlay.SEARCH
                }
                return Velocity.Zero
            }
        }
    }

    val dimColor = if (colors.dark) Color.Black else Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(dimColor.copy(alpha = settings.wallpaperDim))
    ) {
        val bars = WindowInsets.systemBars.asPaddingValues()
        val sidePad = 44.dp

        // ---------------- HOME ----------------
        LazyColumn(
            state = homeList,
            contentPadding = PaddingValues(
                start = if (settings.leftHanded) sidePad else 12.dp,
                end = if (settings.leftHanded) 12.dp else sidePad,
                top = bars.calculateTopPadding() + 24.dp,
                bottom = bars.calculateBottomPadding() + 32.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(gestureConnection)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { homeMenu = true }) }
        ) {
            if (settings.showClock) {
                item(key = "clock") { ClockHeader(onTime = { SystemActions.openAlarms(context) }, onDate = { SystemActions.openCalendar(context) }) }
            }
            if (settings.widgetIds.isNotEmpty()) {
                item(key = "widgets") { WidgetsArea(settings.widgetIds, vm.widgets, Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) }
            }
            item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
            items(state.favorites, key = { "f_" + it.key }) { app ->
                FavoriteRow(
                    app = app,
                    notifications = state.notifications[app.packageName].orEmpty(),
                    showIcon = settings.showIcons,
                    onClick = { launchApp(app) },
                    onLongClick = { actionsFor = app },
                    onNotificationClick = { NotificationStore.open(context, it) },
                    onNotificationDismiss = { NotificationStore.dismiss(it) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.loaded && state.favorites.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Trascina sulle lettere a lato per trovare un'app, poi tienila premuta e scegli «Aggiungi ai preferiti».",
                        color = colors.onWallpaperMuted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (!notificationAccess) {
                item(key = "notif_hint") {
                    HintChip(Icons.Filled.Notifications, "Mostra le notifiche qui") { callbacks.openNotificationAccess() }
                }
            }
            if (!isDefaultLauncher) {
                item(key = "default_hint") {
                    HintChip(Icons.Filled.Settings, "Imposta Onda come home") { callbacks.requestDefaultLauncher() }
                }
            }
            item(key = "search_hint") {
                HintChip(Icons.Filled.Search, "Cerca") { overlay = Overlay.SEARCH }
            }
        }

        // ---------------- TUTTE LE APP ----------------
        AnimatedVisibility(
            visible = overlay == Overlay.ALL_APPS,
            enter = fadeIn() + slideInHorizontally { if (settings.leftHanded) -it / 6 else it / 6 },
            exit = fadeOut() + slideOutHorizontally { if (settings.leftHanded) -it / 6 else it / 6 },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.9f))
                    .blockTouches()
            ) {
                AllAppsPanel(
                    sections = sections,
                    listState = allAppsList,
                    activeLetter = activeLetter,
                    notifications = state.notifications,
                    showIcons = settings.showIcons,
                    onClick = { launchApp(it) },
                    onLongClick = { actionsFor = it },
                    contentPadding = PaddingValues(
                        start = if (settings.leftHanded) sidePad + 8.dp else 12.dp,
                        end = if (settings.leftHanded) 12.dp else sidePad + 8.dp,
                        top = bars.calculateTopPadding() + 56.dp,
                        bottom = bars.calculateBottomPadding() + 24.dp,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp)
                        .fillMaxWidth()
                ) {
                    Text("Tutte le app", color = colors.onWallpaper, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    RoundIcon(Icons.Filled.Search) { overlay = Overlay.SEARCH }
                    RoundIcon(Icons.Filled.Close) { closeAll() }
                }
            }
        }

        // ---------------- SCROLLER A ONDA ----------------
        if (overlay == Overlay.NONE || overlay == Overlay.ALL_APPS) {
            AlphabetWaveScroller(
                letters = letters,
                leftSide = settings.leftHanded,
                modifier = Modifier
                    .align(if (settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(top = bars.calculateTopPadding() + 64.dp, bottom = bars.calculateBottomPadding() + 48.dp)
                    .padding(horizontal = 4.dp),
                onTouchStart = { overlay = Overlay.ALL_APPS },
                onLetter = { letter ->
                    activeLetter = letter
                    sections.firstOrNull { it.letter == letter }?.let { s ->
                        scope.launch { allAppsList.scrollToItem(s.firstIndex) }
                    }
                },
                onTouchEnd = { /* il pannello resta aperto per scegliere l'app */ },
            )
        }

        // ---------------- RICERCA ----------------
        AnimatedVisibility(
            visible = overlay == Overlay.SEARCH,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            SearchPanel(
                results = search,
                showIcons = settings.showIcons,
                contactsPermission = contactsPermission,
                onQueryChange = vm::updateSearch,
                onApp = { launchApp(it) },
                onAppLongClick = { actionsFor = it },
                onContact = { vm.contacts.open(it); scope.launch { delay(350); closeAll() } },
                onWebSearch = { SystemActions.webSearch(context, it); scope.launch { delay(350); closeAll() } },
                onRequestContacts = callbacks::requestContactsPermission,
                onClose = { closeAll() },
            )
        }

        // ---------------- IMPOSTAZIONI ----------------
        AnimatedVisibility(
            visible = overlay == Overlay.SETTINGS,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 },
        ) {
            SettingsScreen(
                settings = settings,
                hiddenApps = state.hiddenApps,
                isDefaultLauncher = isDefaultLauncher,
                notificationAccess = notificationAccess,
                widgets = vm.widgets,
                onEdit = vm::updateSettings,
                onRequestDefault = callbacks::requestDefaultLauncher,
                onNotificationAccess = callbacks::openNotificationAccess,
                onAddWidget = { widgetFilter = null; widgetPicker = true },
                onRemoveWidget = vm::removeWidget,
                onUnhide = { vm.setHidden(it, false) },
                onWallpaper = { SystemActions.openWallpaperPicker(context) },
                onClose = { closeAll() },
            )
        }
    }

    // ---------------- FOGLI MODALI ----------------
    actionsFor?.let { app ->
        val favIndex = settings.favorites.indexOf(app.key)
        val shortcuts = remember(app.key) { vm.shortcuts(app) }
        AppActionsSheet(
            app = app,
            actions = AppActions(
                shortcuts = shortcuts,
                isMuted = app.packageName in settings.mutedNotifications,
                favoriteIndex = favIndex,
                favoriteCount = state.favorites.size,
                onShortcut = vm::startShortcut,
                onToggleFavorite = { vm.toggleFavorite(app) },
                onMove = { delta ->
                    vm.moveFavorite(app, delta)
                    actionsFor = app // resta aperto per spostamenti multipli
                },
                onRename = { vm.rename(app, it) },
                onToggleHidden = { vm.setHidden(app, !app.isHidden) },
                onToggleMuted = { vm.toggleMuted(app) },
                onAddWidget = { widgetFilter = app.packageName; widgetPicker = true },
                onInfo = { vm.appsRepo.openAppInfo(app) },
                onUninstall = { SystemActions.uninstall(context, app) },
            ),
            onDismiss = { actionsFor = null },
        )
    }

    if (homeMenu) {
        ModalBottomSheet(
            onDismissRequest = { homeMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.panel,
            contentColor = colors.onWallpaper,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                MenuRow(Icons.Filled.Settings, "Impostazioni") { homeMenu = false; overlay = Overlay.SETTINGS }
                MenuRow(Icons.Filled.Add, "Aggiungi widget") { homeMenu = false; widgetFilter = null; widgetPicker = true }
                MenuRow(Icons.Filled.Search, "Cerca") { homeMenu = false; overlay = Overlay.SEARCH }
                MenuRow(Icons.Filled.Notifications, "Cambia sfondo") { homeMenu = false; SystemActions.openWallpaperPicker(context) }
            }
        }
    }

    if (widgetPicker) {
        WidgetPickerSheet(
            widgets = vm.widgets,
            packageFilter = widgetFilter,
            onPick = { p -> widgetPicker = false; callbacks.pickWidget(p) },
            onDismiss = { widgetPicker = false },
        )
    }
}

@Composable
private fun ClockHeader(onTime: () -> Unit, onDate: () -> Unit) {
    val colors = LocalOndaColors.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            val msToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(msToNextMinute + 50)
        }
    }
    val locale = Locale.getDefault()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            color = colors.onWallpaper,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.clickable(onClick = onTime),
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
            color = colors.onWallpaperMuted,
            fontSize = 17.sp,
            modifier = Modifier.clickable(onClick = onDate),
        )
    }
}

@Composable
private fun HintChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.panel.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = colors.onWallpaper, fontSize = 14.sp)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = colors.onWallpaper)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Icon(icon, null, tint = colors.accent)
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 16.sp)
    }
}
