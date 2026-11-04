package com.gabrielepiccione.onda

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielepiccione.onda.data.NotificationStore
import com.gabrielepiccione.onda.data.WidgetManager
import com.gabrielepiccione.onda.ui.HomeCallbacks
import com.gabrielepiccione.onda.ui.HomeScreen
import com.gabrielepiccione.onda.ui.LocalOndaColors
import com.gabrielepiccione.onda.ui.OndaTheme
import com.gabrielepiccione.onda.util.SystemActions

class MainActivity : ComponentActivity(), HomeCallbacks {

    private val vm: LauncherViewModel by viewModels()
    private val widgets: WidgetManager get() = (application as OndaApp).widgets

    private var notificationAccess by mutableStateOf(false)
    private var isDefaultLauncher by mutableStateOf(false)
    private var contactsPermission by mutableStateOf(false)

    /** Id del widget in attesa di consenso/configurazione. */
    private var pendingWidgetId = -1

    private val contactsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsPermission = granted
        if (granted) vm.updateSearch(vm.search.value.query)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING, -1) ?: -1

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            OndaTheme(state.settings) {
                val dark = LocalOndaColors.current.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                HomeScreen(
                    vm = vm,
                    callbacks = this,
                    notificationAccess = notificationAccess,
                    isDefaultLauncher = isDefaultLauncher,
                    contactsPermission = contactsPermission,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = NotificationStore.isPermissionGranted(this)
        isDefaultLauncher = SystemActions.isDefaultLauncher(this)
        contactsPermission = vm.contacts.hasPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Premuto il tasto Home mentre il launcher è già in primo piano
        if (intent.action == Intent.ACTION_MAIN) vm.onHomePressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingWidgetId)
    }

    // ---------------- HomeCallbacks ----------------

    override fun requestContactsPermission() {
        contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    override fun requestDefaultLauncher() {
        SystemActions.requestDefaultLauncher(this)
    }

    override fun openNotificationAccess() {
        runCatching { startActivity(NotificationStore.permissionIntent(this)) }
    }

    @Suppress("DEPRECATION")
    override fun pickWidget(provider: WidgetManager.Provider) {
        val id = widgets.allocateId()
        pendingWidgetId = id
        if (widgets.bindIfAllowed(id, provider)) {
            configureOrAdd(id)
        } else {
            runCatching { startActivityForResult(widgets.bindPermissionIntent(id, provider), REQ_BIND) }
                .onFailure { cancelPending() }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureOrAdd(id: Int) {
        val info = widgets.infoFor(id)
        if (info == null) {
            cancelPending(); return
        }
        if (info.configure != null) {
            val started = runCatching {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            }.isSuccess
            if (started) return
        }
        addPending(id)
    }

    private fun addPending(id: Int) {
        vm.settingsRepo.addWidget(id)
        pendingWidgetId = -1
    }

    private fun cancelPending() {
        if (pendingWidgetId != -1) widgets.deleteId(pendingWidgetId)
        pendingWidgetId = -1
    }

    @Deprecated("Usato per i flussi dei widget che richiedono requestCode")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK && id != -1) {
                pendingWidgetId = id
                configureOrAdd(id)
            } else cancelPending()
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK && id != -1) addPending(id) else cancelPending()
        }
    }

    private companion object {
        const val REQ_BIND = 4101
        const val REQ_CONFIGURE = 4102
        const val KEY_PENDING = "pending_widget"
    }
}
