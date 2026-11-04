package com.gabrielepiccione.onda.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.gabrielepiccione.onda.service.OndaNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val packageName: String,
)

/** Sessione multimediale attiva (Spotify, YouTube Music, podcast…), letta grazie all'accesso alle notifiche. */
class MediaRepository(private val context: Context) {

    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, OndaNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private var controller: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    /** Da chiamare quando l'accesso alle notifiche è concesso. */
    fun start() {
        if (started) return
        started = runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            pick(currentSessions())
        }.isSuccess
    }

    private fun currentSessions(): List<MediaController> =
        runCatching { msm.getActiveSessions(component) }.getOrNull().orEmpty()

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        if (c == null || meta == null) {
            _media.value = null
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank()) {
            _media.value = null
            return
        }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val previous = _media.value
        _media.value = MediaInfo(
            title = title,
            artist = artist,
            // Riutilizziamo la copertina se è la stessa traccia, per non ricreare bitmap a ogni tick
            art = if (previous?.title == title && previous.art != null) previous.art else art?.asImageBitmap(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    fun open() {
        val c = controller ?: return
        val pi = c.sessionActivity
        if (pi != null) {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            runCatching { pi.send(context, 0, null, null, null, null, options) }
        } else {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
