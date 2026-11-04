package com.gabrielepiccione.onda.data

import android.app.PendingIntent
import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import java.text.Normalizer

/** App installata così come la restituisce LauncherApps, prima delle personalizzazioni. */
data class RawApp(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: ImageBitmap,
    /** Maschera monocromatica per le icone a tema (Android 13+), se l'app la fornisce. */
    val mono: ImageBitmap?,
    val isWorkProfile: Boolean,
)

/** App pronta per la UI, con nome personalizzato applicato. */
data class AppInfo(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val originalLabel: String,
    val icon: ImageBitmap,
    val mono: ImageBitmap?,
    val isWorkProfile: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    /** Chiave dell'app aperta scorrendo la riga verso destra, se impostata. */
    val swipeTarget: String?,
) {
    val packageName: String get() = component.packageName
    val section: Char get() = sectionOf(label)
}

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
)

data class ContactHit(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
)

data class CalendarEvent(
    val title: String,
    val begin: Long,
    val allDay: Boolean,
    val eventId: Long,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Azione associata a uno swipe verticale in home. */
enum class GestureAction(val label: String) {
    SEARCH("Ricerca"),
    NOTIFICATIONS("Tendina notifiche"),
    ALL_APPS("Tutte le app"),
    NONE("Nessuna"),
}

/** Cartella in home: contiene app (per chiave) e widget (per id). */
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
    val widgets: List<Int> = emptyList(),
)

/** Prefisso delle voci "cartella" nell'elenco dei preferiti. */
const val FOLDER_PREFIX = "folder:"

enum class FontChoice(val label: String) {
    SYSTEM("Sistema"),
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono"),
    ROUNDED("Corsivo"),
}

data class Settings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val swipeActions: Map<String, String> = emptyMap(),
    val folders: Map<String, Folder> = emptyMap(),
    /** Widget mostrato nel pannello dell'app (swipe sulla riga): chiave app -> id widget. */
    val appWidgets: Map<String, Int> = emptyMap(),
    val launchCounts: Map<String, Int> = emptyMap(),
    val swipeDown: GestureAction = GestureAction.SEARCH,
    val swipeUp: GestureAction = GestureAction.NOTIFICATIONS,
    val hideStatusBar: Boolean = false,
    val hideAlphabet: Boolean = false,
    val hideFavoriteNames: Boolean = false,
    val iconSize: Float = 1f,
    val mutedNotifications: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: Long = 0xFF7FE3D1,
    val font: FontChoice = FontChoice.SYSTEM,
    val iconPack: String = "",
    val themedIcons: Boolean = false,
    val showIcons: Boolean = true,
    val textScale: Float = 1f,
    val showClock: Boolean = true,
    val showCalendar: Boolean = true,
    val showMedia: Boolean = true,
    val wallpaperDim: Float = 0.2f,
    val leftHanded: Boolean = false,
    val doubleTapLock: Boolean = false,
    val haptics: Boolean = true,
    val widgetIds: List<Int> = emptyList(),
    val onboardingDone: Boolean = false,
)

/** Lettera di sezione: maiuscola senza accenti, '#' per numeri e simboli. */
fun sectionOf(label: String): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .uppercase()
        .firstOrNull() ?: return '#'
    return if (plain in 'A'..'Z') plain else '#'
}

/** Normalizzazione per la ricerca: minuscolo, senza accenti. */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
