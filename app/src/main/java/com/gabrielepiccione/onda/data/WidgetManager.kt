package com.gabrielepiccione.onda.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable

/** Ospita i widget di sistema mostrati in home. */
class WidgetManager(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host: AppWidgetHost = AppWidgetHost(context, HOST_ID)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    data class Provider(
        val info: AppWidgetProviderInfo,
        val label: String,
        val appLabel: String,
    ) {
        val packageName: String get() = info.provider.packageName
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** Tutti i provider di widget installati, per tutti i profili. */
    fun providers(packageFilter: String? = null): List<Provider> {
        val pm = context.packageManager
        return launcherApps.profiles
            .flatMap { user -> runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList()) }
            .filter { packageFilter == null || it.provider.packageName == packageFilter }
            .map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                Provider(info, info.loadLabel(pm) ?: appLabel, appLabel)
            }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun previewOf(provider: Provider): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        return runCatching { provider.info.loadPreviewImage(context, density) }.getOrNull()
            ?: runCatching { provider.info.loadIcon(context, density) }.getOrNull()
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    /** Prova a collegare il widget senza chiedere conferma; false se serve il consenso dell'utente. */
    fun bindIfAllowed(id: Int, provider: Provider): Boolean =
        runCatching {
            manager.bindAppWidgetIdIfAllowed(id, provider.info.profile, provider.info.provider, null)
        }.getOrDefault(false)

    fun bindPermissionIntent(id: Int, provider: Provider): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.info.profile)

    fun infoFor(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = infoFor(id) ?: return null
        return runCatching { host.createView(viewContext, id, info) }.getOrNull()
    }

    companion object {
        const val HOST_ID = 0x0DA
    }
}
