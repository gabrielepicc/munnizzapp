package com.gabrielepiccione.onda.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Servizio di accessibilità usato SOLO per bloccare lo schermo con il doppio tocco in home.
 * Non legge il contenuto dello schermo (nessun evento sottoscritto).
 */
class OndaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: OndaAccessibilityService? = null

        val isEnabled: Boolean get() = instance != null

        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}
