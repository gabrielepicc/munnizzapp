package com.gabrielepiccione.onda

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.gabrielepiccione.onda.service.OndaAccessibilityService
import com.gabrielepiccione.onda.ui.HomeCallbacks
import com.gabrielepiccione.onda.ui.HomeScreen
import com.gabrielepiccione.onda.ui.HomeStatus
import com.gabrielepiccione.onda.ui.LocalOndaColors
import com.gabrielepiccione.onda.ui.OndaTheme
import com.gabrielepiccione.onda.ui.WidgetTarget
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gabrielepiccione.onda.util.SystemActions
import java.time.LocalDate

class MainActivity : ComponentActivity(), HomeCallbacks {

    private val vm: LauncherViewModel by viewModels()
    private val ondaApp: OndaApp get() = application as OndaApp
    private val widgets: WidgetManager get() = ondaApp.widgets

    private var status by mutableStateOf(HomeStatus(false, false, false, false, false))

    /** Id del widget in attesa di consenso/configurazione. */
    private var pendingWidgetId = -1
    private var pendingTarget: WidgetTarget = WidgetTarget.Home

    private val contactsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshStatus()
        if (granted) vm.updateSearch(vm.search.value.query)
    }

    private val calendarLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshStatus()
        if (granted) vm.refreshCalendar()
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(vm.settingsRepo.exportJson().toByteArray()) }
        }.isSuccess
        toast(if (ok) "Backup salvato" else "Impossibile salvare il backup")
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val ok = text != null && vm.settingsRepo.importJson(text)
        toast(if (ok) "Impostazioni ripristinate" else "File di backup non valido")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING, -1) ?: -1

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            OndaTheme(state.settings) {
                val dark = LocalOndaColors.current.dark
                val hideBar = state.settings.hideStatusBar
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                        if (hideBar) {
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(WindowInsetsCompat.Type.statusBars())
                        } else {
                            show(WindowInsetsCompat.Type.statusBars())
                        }
                    }
                }
                HomeScreen(vm = vm, callbacks = this, status = status)
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
        refreshStatus()
        if (status.notificationAccess) ondaApp.media.start()
        vm.refreshCalendar()
    }

    private fun refreshStatus() {
        status = HomeStatus(
            notificationAccess = NotificationStore.isPermissionGranted(this),
            isDefaultLauncher = SystemActions.isDefaultLauncher(this),
            contactsPermission = vm.contacts.hasPermission(),
            calendarPermission = vm.calendar.hasPermission(),
            accessibilityEnabled = OndaAccessibilityService.isEnabled,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tasto Home premuto mentre il launcher è già in primo piano
        if (intent.action == Intent.ACTION_MAIN) vm.onHomePressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingWidgetId)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---------------- HomeCallbacks ----------------

    override fun requestContactsPermission() = contactsLauncher.launch(Manifest.permission.READ_CONTACTS)

    override fun requestCalendarPermission() = calendarLauncher.launch(Manifest.permission.READ_CALENDAR)

    override fun requestDefaultLauncher() = SystemActions.requestDefaultLauncher(this)

    override fun openNotificationAccess() {
        runCatching { startActivity(NotificationStore.permissionIntent(this)) }
    }

    override fun openAccessibilitySettings() {
        toast("Attiva «Onda – blocco schermo»")
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    override fun exportBackup() = exportLauncher.launch("onda-backup-${LocalDate.now()}.json")

    override fun importBackup() = importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))

    @Suppress("DEPRECATION")
    override fun pickWidget(provider: WidgetManager.Provider, target: WidgetTarget) {
        val id = widgets.allocateId()
        pendingWidgetId = id
        pendingTarget = target
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
        when (val t = pendingTarget) {
            WidgetTarget.Home -> vm.settingsRepo.addWidget(id)
            is WidgetTarget.InFolder -> vm.settingsRepo.addWidgetToFolder(t.folderId, id)
            is WidgetTarget.ForApp -> {
                vm.settingsRepo.current.appWidgets[t.appKey]?.let { old -> widgets.deleteId(old) }
                vm.settingsRepo.setAppWidget(t.appKey, id)
            }
        }
        pendingWidgetId = -1
        pendingTarget = WidgetTarget.Home
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
