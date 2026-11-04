package com.gabrielepiccione.onda.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Elenco delle app avviabili per tutti i profili (personale + lavoro),
 * aggiornato su installazioni/rimozioni e quando cambia l'icon pack.
 */
class AppRepository(
    private val context: Context,
    private val settings: StateFlow<Settings>,
) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<RawApp>>(emptyList())
    val apps: StateFlow<List<RawApp>> = _apps.asStateFlow()

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val iconSizePx: Int = (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(56)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = reload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
        scope.launch {
            settings.map { it.iconPack }.distinctUntilChanged().drop(1).collect { reload() }
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val pack = IconPacks.load(context, settings.value.iconPack)
            val myUser = Process.myUserHandle()
            val result = mutableListOf<RawApp>()
            for (user in launcherApps.profiles) {
                val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                for (info in activities) {
                    if (info.componentName.packageName == context.packageName) continue
                    result += runCatching { info.toRawApp(user, user != myUser, pack) }.getOrNull() ?: continue
                }
            }
            _apps.value = result
        }
    }

    private fun LauncherActivityInfo.toRawApp(user: UserHandle, work: Boolean, pack: IconPacks.Loaded?): RawApp {
        val serial = userManager.getSerialNumberForUser(user)
        val base = getIcon(densityDpi)
        val packIcon = pack?.iconFor(componentName, densityDpi)
        val drawable = packIcon ?: base
        val badged = if (work) context.packageManager.getUserBadgedIcon(drawable, user) else drawable
        return RawApp(
            key = "${componentName.flattenToShortString()}#$serial",
            component = componentName,
            user = user,
            label = label?.toString()?.trim().orEmpty().ifEmpty { componentName.packageName },
            icon = badged.toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
            mono = if (packIcon == null) monochromeOf(base) else null,
            isWorkProfile = work,
        )
    }

    /** Estrae il livello monocromatico di un'icona adattiva (Android 13+) come maschera alpha. */
    private fun monochromeOf(d: android.graphics.drawable.Drawable): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val adaptive = d as? AdaptiveIconDrawable ?: return null
        val mono = adaptive.monochrome ?: return null
        val size = iconSizePx
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // I livelli adattivi sono il 150% dell'area visibile: centriamo la parte utile
        val inset = size / 4
        val old = Rect(mono.bounds)
        mono.setBounds(-inset, -inset, size + inset, size + inset)
        mono.draw(canvas)
        mono.bounds = old
        return bmp.asImageBitmap()
    }

    // --- Azioni ---

    fun launch(app: AppInfo, bounds: Rect? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, null) }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    /** Scorciatoie dell'app (richiede di essere il launcher predefinito). */
    fun shortcutsFor(app: AppInfo): List<ShortcutInfo> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching { launcherApps.getShortcuts(query, app.user) }
            .getOrNull()
            .orEmpty()
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .take(5)
    }

    fun shortcutIcon(shortcut: ShortcutInfo): ImageBitmap? = runCatching {
        launcherApps.getShortcutIconDrawable(shortcut, densityDpi)?.toBitmap(iconSizePx / 2, iconSizePx / 2)?.asImageBitmap()
    }.getOrNull()

    fun startShortcut(shortcut: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(shortcut, null, null) }
    }
}
