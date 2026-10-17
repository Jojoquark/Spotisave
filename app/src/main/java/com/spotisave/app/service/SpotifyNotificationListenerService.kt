package com.spotisave.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback- und Dauer-Erkennungsdienst auf Basis von Androids NotificationListenerService.
 * Ist systemweit von aggressiven OEM-Akkusparmaßnahmen (Doze / App Standby) ausgenommen.
 * Erfasst Metadaten und PlaybackState direkt aus der Spotify MediaSession.
 */
class SpotifyNotificationListenerService : NotificationListenerService() {

    private var activeMediaController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null

    companion object {
        private const val TAG = "SpotifyNotifListener"
        const val SPOTIFY_PACKAGE_NAME = "com.spotify.music"

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        @Volatile
        var currentMediaController: MediaController? = null
            private set

        @Volatile
        private var lastNotificationContextTitle: String? = null

        @Volatile
        private var lastNotificationContextType: String? = null

        /**
         * Erkennt explizite Kontext-Typen ("playlist" oder "album") anhand typischer Spotify-Präfixe.
         */
        fun detectContextTypeFromText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val lower = raw.trim().lowercase()
            val playlistPrefixes = listOf(
                "playlist • ", "playlist: ", "playlist - ", "aus playlist ",
                "from playlist ", "playing from playlist ", "wiedergabe aus playlist ",
                "wiedergabeliste • ", "wiedergabeliste: "
            )
            if (playlistPrefixes.any { lower.startsWith(it) }) return "playlist"

            val albumPrefixes = listOf(
                "album • ", "album: ", "album - ", "aus album ",
                "from album ", "playing from album ", "wiedergabe aus album "
            )
            if (albumPrefixes.any { lower.startsWith(it) }) return "album"
            return null
        }

        /**
         * Bereinigt Text aus Notification-Extras um Playlist- oder Albumnamen zu extrahieren.
         */
        fun extractContextTitleFromText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim()
            val lower = cleaned.lowercase()
            if (lower == "spotify" || lower == "musik" || lower == "music") return null

            val playlistPrefixes = listOf(
                "playlist • ", "playlist: ", "playlist - ", "aus playlist ",
                "from playlist ", "playing from playlist ", "wiedergabe aus playlist ",
                "wiedergabeliste • ", "wiedergabeliste: ",
                "album • ", "album: ", "album - ", "aus album ",
                "from album ", "playing from album ", "wiedergabe aus album "
            )
            for (prefix in playlistPrefixes) {
                if (lower.startsWith(prefix)) {
                    val title = cleaned.substring(prefix.length).trim()
                    if (title.isNotBlank()) return title
                }
            }
            return cleaned
        }

        /**
         * Prüft, ob der Nutzer die Notification-Listener-Berechtigung in den Android-Einstellungen erteilt hat.
         */
        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, SpotifyNotificationListenerService::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }

        /**
         * Berechnet die aktuelle Live-Position unter Berücksichtigung der seit dem letzten Update verstrichenen Zeit.
         */
        fun computeCurrentPosition(state: PlaybackState?, durationMs: Long): Long {
            if (state == null) return 0L
            val basePos = state.position
            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            if (isPlaying && state.lastPositionUpdateTime > 0L) {
                val timeDelta = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
                val calculated = basePos + (timeDelta * speed).toLong()
                return if (durationMs > 0L) calculated.coerceIn(0L, durationMs) else calculated.coerceAtLeast(0L)
            }
            return basePos.coerceAtLeast(0L)
        }

        /**
         * Liest die tatsächliche Live-Hörposition aus der aktiven Spotify MediaSession.
         */
        fun getActivePlaybackPosition(): Long {
            val controller = currentMediaController ?: return 0L
            val state = controller.playbackState ?: return 0L
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            return computeCurrentPosition(state, duration)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        Log.i(TAG, "NotificationListenerService verbunden")
        scanActiveSpotifyNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
        releaseMediaController()
        Log.i(TAG, "NotificationListenerService getrennt")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != SPOTIFY_PACKAGE_NAME) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extrahiere potentiellen Playlist-Namen & Typ aus Notification-Extras
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        val candidateTitle = extractContextTitleFromText(subText)
            ?: extractContextTitleFromText(summaryText)
            ?: extractContextTitleFromText(infoText)
        val candidateType = detectContextTypeFromText(subText)
            ?: detectContextTypeFromText(summaryText)
            ?: detectContextTypeFromText(infoText)

        lastNotificationContextTitle = candidateTitle
        lastNotificationContextType = candidateType

        val notifContextUri = extras.getString("context_uri")
            ?: extras.getString("com.spotify.music.extra.CONTEXT_URI")

        // 1. Suche MediaSession Token
        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }

        if (token != null) {
            attachMediaController(token)
        } else {
            // Fallback auf Titel & Text aus Notification Extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (!title.isNullOrBlank()) {
                PlaybackTracker.onMetadataChanged(
                    context = applicationContext,
                    trackUri = "spotify:track:notification",
                    trackTitle = title,
                    artist = text ?: "Spotify",
                    album = subText ?: "Spotify",
                    durationMs = 0L,
                    contextUri = notifContextUri,
                    contextTitle = candidateTitle,
                    source = "NotificationFallback",
                    contextTypeHint = candidateType
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == SPOTIFY_PACKAGE_NAME) {
            Log.d(TAG, "Spotify Benachrichtigung entfernt -> Letzten Stand sichern")
            PlaybackTracker.flushBookmark(applicationContext, "NotificationRemoved")
            releaseMediaController()
        }
    }

    private fun scanActiveSpotifyNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                if (sbn.packageName == SPOTIFY_PACKAGE_NAME) {
                    onNotificationPosted(sbn)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Scannen aktiver Benachrichtigungen", e)
        }
    }

    private fun attachMediaController(token: MediaSession.Token) {
        if (activeMediaController?.sessionToken == token) return

        releaseMediaController()

        try {
            val controller = MediaController(applicationContext, token)
            activeMediaController = controller
            currentMediaController = controller

            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handlePlaybackState(controller, state)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handleMetadata(controller, metadata)
                }

                override fun onSessionDestroyed() {
                    releaseMediaController()
                }
            }

            controller.registerCallback(callback)
            controllerCallback = callback

            // Sofort initial auslesen
            handleMetadata(controller, controller.metadata)
            handlePlaybackState(controller, controller.playbackState)
            Log.i(TAG, "MediaController erfolgreich an Spotify MediaSession gekoppelt")
        } catch (e: Exception) {
            Log.e(TAG, "Konnte MediaController nicht initialisieren", e)
        }
    }

    private fun handleMetadata(controller: MediaController, metadata: MediaMetadata?) {
        if (metadata == null) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val mediaUri = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)

        val coverUrl = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val contextUri = controller.extras?.getString("context_uri")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_URI")
            ?: controller.playbackState?.extras?.getString("context_uri")
            ?: controller.playbackState?.extras?.getString("com.spotify.music.extra.CONTEXT_URI")

        val contextTitle = controller.extras?.getString("context_title")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: controller.extras?.getString("context_name")
            ?: controller.extras?.getString("playlist_name")
            ?: controller.extras?.getString("queue_title")
            ?: controller.playbackState?.extras?.getString("context_title")
            ?: controller.playbackState?.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: lastNotificationContextTitle

        val playbackState = controller.playbackState
        val position = computeCurrentPosition(playbackState, duration)
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        PlaybackTracker.onMediaSessionUpdate(
            context = applicationContext,
            trackUri = mediaUri ?: "spotify:track:active",
            trackTitle = title ?: "Unbekannter Titel",
            artist = artist ?: "Unbekannter Interpret",
            album = album ?: "Spotify",
            positionMs = position,
            durationMs = duration,
            isPlaying = isPlaying,
            contextUri = contextUri,
            contextTitle = contextTitle,
            coverUrl = coverUrl,
            contextTypeHint = lastNotificationContextType
        )
    }

    private fun handlePlaybackState(controller: MediaController, state: PlaybackState?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackState.STATE_PLAYING

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)

        val coverUrl = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val contextUri = controller.extras?.getString("context_uri")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_URI")
            ?: state.extras?.getString("context_uri")
            ?: state.extras?.getString("com.spotify.music.extra.CONTEXT_URI")

        val contextTitle = controller.extras?.getString("context_title")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: controller.extras?.getString("context_name")
            ?: controller.extras?.getString("playlist_name")
            ?: controller.extras?.getString("queue_title")
            ?: state.extras?.getString("context_title")
            ?: state.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: lastNotificationContextTitle

        val position = computeCurrentPosition(state, duration)

        PlaybackTracker.onMediaSessionUpdate(
            context = applicationContext,
            trackUri = mediaUri ?: "spotify:track:active",
            trackTitle = title ?: "Unbekannter Titel",
            artist = artist ?: "Unbekannter Interpret",
            album = album ?: "Spotify",
            positionMs = position,
            durationMs = duration,
            isPlaying = isPlaying,
            contextUri = contextUri,
            contextTitle = contextTitle,
            coverUrl = coverUrl,
            contextTypeHint = lastNotificationContextType
        )
    }

    private fun releaseMediaController() {
        try {
            controllerCallback?.let { activeMediaController?.unregisterCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Freigeben des MediaControllers", e)
        }
        controllerCallback = null
        activeMediaController = null
        currentMediaController = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaController()
    }
}
