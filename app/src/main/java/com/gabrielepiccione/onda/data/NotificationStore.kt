package com.gabrielepiccione.onda.data

import android.app.ActivityOptions
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.gabrielepiccione.onda.service.OndaNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notifiche attive raggruppate per pacchetto, alimentate dal NotificationListenerService. */
object NotificationStore {

    private val _byPackage = MutableStateFlow<Map<String, List<NotificationItem>>>(emptyMap())
    val byPackage: StateFlow<Map<String, List<NotificationItem>>> = _byPackage.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal var listener: OndaNotificationListener? = null

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _byPackage.value = emptyMap()
    }

    internal fun publish(active: Array<StatusBarNotification>, ownPackage: String) {
        _byPackage.value = active
            .asSequence()
            .filter { it.packageName != ownPackage }
            .filter { sbn ->
                val n = sbn.notification
                // Saltiamo i riassunti di gruppo e le notifiche "di servizio" senza contenuto
                (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 || active.count { it.groupKey == sbn.groupKey } == 1
            }
            .mapNotNull { it.toItem() }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sortedByDescending { it.postTime } }
    }

    private fun StatusBarNotification.toItem(): NotificationItem? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        if (title.isBlank() && text.isBlank()) return null
        val ongoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        // Le notifiche persistenti senza testo (es. servizi in primo piano) le ignoriamo
        if (ongoing && text.isBlank()) return null
        return NotificationItem(
            key = key,
            packageName = packageName,
            title = title.trim(),
            text = text.trim().replace('\n', ' '),
            postTime = postTime,
            contentIntent = notification.contentIntent,
            autoCancel = (notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            clearable = isClearable,
        )
    }

    fun isPermissionGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun permissionIntent(context: Context): android.content.Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, OndaNotificationListener::class.java).flattenToString()
            )
        } else {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Apre la notifica come se fosse stata toccata nella tendina. */
    fun open(context: Context, item: NotificationItem) {
        val pi = item.contentIntent
        if (pi != null) {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            runCatching { pi.send(context, 0, null, null, null, null, options) }
        }
        if (item.autoCancel) dismiss(item)
    }

    fun dismiss(item: NotificationItem) {
        if (item.clearable) runCatching { listener?.cancelNotification(item.key) }
    }

    fun dismissAll(packageName: String) {
        _byPackage.value[packageName].orEmpty().forEach(::dismiss)
    }
}
