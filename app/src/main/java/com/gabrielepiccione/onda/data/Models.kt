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
    val isWorkProfile: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val mutedNotifications: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Long = 0xFF7FE3D1,
    val showIcons: Boolean = true,
    val textScale: Float = 1f,
    val showClock: Boolean = true,
    val wallpaperDim: Float = 0.25f,
    val leftHanded: Boolean = false,
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
