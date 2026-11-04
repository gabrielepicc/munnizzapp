package com.gabrielepiccione.onda.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Primo evento non ancora concluso nelle prossime 24 ore. */
    suspend fun nextEvent(): CalendarEvent? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 6 * 60 * 60 * 1000L)
            ContentUris.appendId(it, now + 24 * 60 * 60 * 1000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                var allDayFallback: CalendarEvent? = null
                while (c.moveToNext()) {
                    val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    if (end < now) continue
                    val event = CalendarEvent(title, begin, allDay, c.getLong(4))
                    if (allDay) {
                        if (allDayFallback == null) allDayFallback = event
                    } else {
                        return@use event
                    }
                }
                allDayFallback
            }
        }.getOrNull()
    }

    fun open(event: CalendarEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
