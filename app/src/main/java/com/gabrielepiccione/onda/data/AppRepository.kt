package com.gabrielepiccione.onda.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Elenco delle app avviabili per tutti i profili (personale + lavoro),
 * aggiornato automaticamente su installazioni, rimozioni e aggiornamenti.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<RawApp>>(emptyList())
    val apps: StateFlow<List<RawApp>> = _apps.asStateFlow()

    private val iconSizePx: Int =
        (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)

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
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val myUser = Process.myUserHandle()
            val result = mutableListOf<RawApp>()
            for (user in launcherApps.profiles) {
                val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                for (info in activities) {
                    if (info.componentName.packageName == context.packageName) continue
                    result += info.toRawApp(user, user != myUser)
                }
            }
            _apps.value = result
        }
    }

    private fun LauncherActivityInfo.toRawApp(user: UserHandle, work: Boolean): RawApp {
        val serial = userManager.getSerialNumberForUser(user)
        val drawable = runCatching { getBadgedIcon(0) }.getOrNull() ?: getIcon(0)
        val bitmap = drawable.toBitmap(iconSizePx, iconSizePx)
        return RawApp(
            key = "${componentName.flattenToShortString()}#$serial",
            component = componentName,
            user = user,
            label = label?.toString()?.trim().orEmpty().ifEmpty { componentName.packageName },
            icon = bitmap.asImageBitmap(),
            isWorkProfile = work,
        )
    }

    // --- Azioni ---

    fun launch(app: AppInfo, bounds: android.graphics.Rect? = null) {
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

    fun startShortcut(shortcut: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(shortcut, null, null) }
    }
}
