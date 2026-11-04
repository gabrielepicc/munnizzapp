package com.gabrielepiccione.onda.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gabrielepiccione.onda.data.NotificationStore

class OndaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.listener = this
        NotificationStore.setConnected(true)
        refresh()
    }

    override fun onListenerDisconnected() {
        if (NotificationStore.listener === this) NotificationStore.listener = null
        NotificationStore.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationStore.publish(active, packageName)
    }
}
