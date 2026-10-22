package com.spotisave.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream

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
        var lastNotificationContextTitle: String? = null
            private set

        @Volatile
        var lastNotificationTrackTitle: String? = null
            private set

        @Volatile
        private var lastNotificationContextType: String? = null

        /**
         * Liefert den Kontext-Titel der Benachrichtigung NUR DANN zurück, wenn der aktuell
         * angefragte Songtitel mit dem Titel der Benachrichtigung übereinstimmt.
         * Verhindert strikt, dass ein veralteter Playlist-Titel auf nachfolgende Tracks überschwappt!
         */
        fun getContextTitleForTrack(trackTitle: String?): String? {
            val notifTitle = lastNotificationContextTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val notifTrack = lastNotificationTrackTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val cleanTrack = trackTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val isExact = cleanTrack.equals(notifTrack, ignoreCase = true)
            val isPrefixOrSuffix = if (!isExact && cleanTrack.length >= 4 && notifTrack.length >= 4) {
                cleanTrack.startsWith(notifTrack, ignoreCase = true) || notifTrack.startsWith(cleanTrack, ignoreCase = true)
            } else false
            return if (isExact || isPrefixOrSuffix) {
                notifTitle
            } else {
                null
            }
        }

        fun setNotificationContextForTesting(
            contextTitle: String?,
            trackTitle: String?,
            contextType: String? = null
        ) {
            lastNotificationContextTitle = contextTitle
            lastNotificationTrackTitle = trackTitle
            lastNotificationContextType = contextType
        }

        /**
         * Filtert generische Warteschlangen- und System-Bezeichnungen heraus.
         * Spotify setzt auf Android oft den MediaSession queueTitle auf "Warteschlange" bzw. "Queue".
         * Dieser darf NIEMALS als Playlist-Name interpretiert werden!
         */
        fun isGenericQueueOrLabel(raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            val lower = raw.trim().lowercase()
            return lower in listOf(
                "warteschlange", "queue", "play queue", "wiedergabeschlange",
                "in der warteschlange", "file d'attente", "cola", "coda",
                "kø", "kö", "wiedergabeliste", "playlist", "album", "spotify",
                "musik", "music", "podcast", "titel in der warteschlange",
                "next in queue", "wiedergabe aus playlist", "playing from playlist",
                "wiedergabe aus album", "playing from album", "aus playlist",
                "aus album", "from playlist", "from album"
            ) || lower.startsWith("wiedergabeschlange") ||
                lower.startsWith("in der warteschlange") ||
                lower.startsWith("titel in der warteschlange")
        }

        /**
         * Erkennt explizite Kontext-Typen ("playlist" oder "album") anhand typischer Spotify-Präfixe.
         */
        fun detectContextTypeFromText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val lower = raw.trim().lowercase()
            val playlistPrefixes = listOf(
                "playlist • ", "playlist: ", "playlist - ", "aus playlist ",
                "from playlist ", "playing from playlist ", "wiedergabe aus playlist ",
                "wiedergabeliste • ", "wiedergabeliste: ", "wiedergabe aus playlist\n",
                "playing from playlist\n", "aus playlist\n", "from playlist\n"
            )
            if (playlistPrefixes.any { lower.startsWith(it) } ||
                lower == "wiedergabe aus playlist" || lower == "playing from playlist" ||
                lower == "aus playlist" || lower == "from playlist" ||
                lower == "playlist" || lower == "wiedergabeliste"
            ) return "playlist"

            val albumPrefixes = listOf(
                "album • ", "album: ", "album - ", "aus album ",
                "from album ", "playing from album ", "wiedergabe aus album ",
                "wiedergabe aus album\n", "playing from album\n", "aus album\n", "from album\n"
            )
            if (albumPrefixes.any { lower.startsWith(it) } ||
                lower == "wiedergabe aus album" || lower == "playing from album" ||
                lower == "aus album" || lower == "from album" ||
                lower == "album"
            ) return "album"
            return null
        }

        /**
         * Bereinigt Text aus Notification-Extras um Playlist- oder Albumnamen zu extrahieren.
         */
        fun extractContextTitleFromText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim()
            if (isGenericQueueOrLabel(cleaned)) return null

            // Mehrzeilige Notification-Strings aufteilen (z.B. "Wiedergabe aus Playlist\nFrankreich ...")
            if (cleaned.contains("\n")) {
                val lines = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                for (line in lines) {
                    val candidate = extractContextTitleFromText(line)
                    if (!candidate.isNullOrBlank()) return candidate
                }
                return null
            }

            val lower = cleaned.lowercase()
            if (lower == "spotify" || lower == "musik" || lower == "music") return null

            val prefixes = listOf(
                "wiedergabe aus playlist: ", "wiedergabe aus playlist • ", "wiedergabe aus playlist - ", "wiedergabe aus playlist ",
                "playing from playlist: ", "playing from playlist • ", "playing from playlist ",
                "aus playlist: ", "aus playlist • ", "aus playlist - ", "aus playlist ",
                "from playlist: ", "from playlist • ", "from playlist ",
                "playlist: ", "playlist • ", "playlist - ", "wiedergabeliste: ", "wiedergabeliste • ",
                "wiedergabe aus: ", "playing from: ",
                "wiedergabe aus album: ", "wiedergabe aus album • ", "wiedergabe aus album - ", "wiedergabe aus album ",
                "playing from album: ", "playing from album • ", "playing from album ",
                "aus album: ", "aus album • ", "aus album - ", "aus album ",
                "from album: ", "from album • ", "from album ",
                "album: ", "album • ", "album - "
            )
            for (prefix in prefixes) {
                if (lower.startsWith(prefix)) {
                    val candidate = cleaned.substring(prefix.length).trim()
                    if (candidate.isNotBlank() && !isGenericQueueOrLabel(candidate)) {
                        return candidate
                    }
                }
            }
            return if (!isGenericQueueOrLabel(cleaned)) cleaned else null
        }

        /**
         * Speichert ein Cover-Bitmap lokal im Cache-Verzeichnis und gibt den file:// URI-String zurück.
         */
        fun saveCoverBitmapToCache(context: Context, key: String, bitmap: Bitmap): String? {
            return try {
                val dir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
                val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                val file = File(dir, "art_${safeKey}_${key.hashCode()}.jpg")
                if (!file.exists() || file.length() == 0L) {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
                "file://${file.absolutePath}"
            } catch (e: Exception) {
                null
            }
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
        val contextTitleExtra = extras.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: extras.getString("context_title")
            ?: extras.getString("playlist_name")
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val candidateTitle = extractContextTitleFromText(contextTitleExtra)
            ?: extractContextTitleFromText(subText)
            ?: extractContextTitleFromText(summaryText)
            ?: extractContextTitleFromText(infoText)
            ?: extractContextTitleFromText(bigText)
        val candidateType = detectContextTypeFromText(contextTitleExtra)
            ?: detectContextTypeFromText(subText)
            ?: detectContextTypeFromText(summaryText)
            ?: detectContextTypeFromText(infoText)
            ?: detectContextTypeFromText(bigText)

        val notifTrackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()

        lastNotificationContextTitle = candidateTitle
        lastNotificationContextType = candidateType
        lastNotificationTrackTitle = notifTrackTitle

        val notifContextUri = extras.getString("context_uri")
            ?: extras.getString("com.spotify.music.extra.CONTEXT_URI")

        val notifArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_LARGE_ICON)
        }

        val notifCoverUrl = if (notifArt != null) {
            saveCoverBitmapToCache(applicationContext, notifContextUri ?: "notif_art", notifArt)
        } else null

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
                    coverUrl = notifCoverUrl,
                    source = "NotificationFallback",
                    contextTypeHint = candidateType
                )
            }
        }
        PlaybackTracker.notifyPlaybackTriggered(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == SPOTIFY_PACKAGE_NAME) {
            Log.d(TAG, "Spotify Benachrichtigung entfernt -> Letzten Stand sichern")
            if (!PlaybackTracker.isWithinResumeGrace()) {
                PlaybackTracker.flushBookmark(applicationContext, "NotificationRemoved")
            }
            lastNotificationContextTitle = null
            lastNotificationContextType = null
            lastNotificationTrackTitle = null
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

                override fun onQueueTitleChanged(title: CharSequence?) {
                    val qTitle = title?.toString()
                    if (!qTitle.isNullOrBlank() && !isGenericQueueOrLabel(qTitle)) {
                        handleMetadata(controller, controller.metadata)
                    }
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

        val onlineCoverUrl = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val localArtBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val coverUrl = if (!onlineCoverUrl.isNullOrBlank() && (onlineCoverUrl.startsWith("http://") || onlineCoverUrl.startsWith("https://"))) {
            onlineCoverUrl
        } else if (localArtBitmap != null) {
            saveCoverBitmapToCache(applicationContext, mediaUri ?: title ?: "cover", localArtBitmap)
        } else {
            onlineCoverUrl
        }

        val rawQueueTitle = controller.queueTitle?.toString()?.trim()?.takeIf {
            !isGenericQueueOrLabel(it) && !it.equals("Spotify", ignoreCase = true)
        }

        val matchingNotifContextTitle = getContextTitleForTrack(title)
        val matchingNotifContextType = if (matchingNotifContextTitle != null) lastNotificationContextType else null

        val contextUri = controller.extras?.getString("context_uri")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_URI")
            ?: controller.playbackState?.extras?.getString("context_uri")
            ?: controller.playbackState?.extras?.getString("com.spotify.music.extra.CONTEXT_URI")

        val rawCandidateTitle = controller.extras?.getString("context_title")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: controller.extras?.getString("context_name")
            ?: controller.extras?.getString("playlist_name")
            ?: matchingNotifContextTitle
            ?: controller.playbackState?.extras?.getString("context_title")
            ?: controller.playbackState?.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: rawQueueTitle

        val contextTitle = extractContextTitleFromText(rawCandidateTitle)

        val effectiveContextType = matchingNotifContextType
            ?: if (!contextTitle.isNullOrBlank()) "playlist" else null

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
            contextTypeHint = effectiveContextType
        )
        PlaybackTracker.notifyPlaybackTriggered(applicationContext)
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

        val onlineCoverUrl = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val localArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val coverUrl = if (!onlineCoverUrl.isNullOrBlank() && (onlineCoverUrl.startsWith("http://") || onlineCoverUrl.startsWith("https://"))) {
            onlineCoverUrl
        } else if (localArtBitmap != null) {
            saveCoverBitmapToCache(applicationContext, mediaUri ?: title ?: "cover", localArtBitmap)
        } else {
            onlineCoverUrl
        }

        val rawQueueTitle = controller.queueTitle?.toString()?.trim()?.takeIf {
            !isGenericQueueOrLabel(it) && !it.equals("Spotify", ignoreCase = true)
        }

        val matchingNotifContextTitle = getContextTitleForTrack(title)
        val matchingNotifContextType = if (matchingNotifContextTitle != null) lastNotificationContextType else null

        val contextUri = controller.extras?.getString("context_uri")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_URI")
            ?: state.extras?.getString("context_uri")
            ?: state.extras?.getString("com.spotify.music.extra.CONTEXT_URI")

        val rawCandidateTitle = controller.extras?.getString("context_title")
            ?: controller.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: controller.extras?.getString("context_name")
            ?: controller.extras?.getString("playlist_name")
            ?: matchingNotifContextTitle
            ?: state.extras?.getString("context_title")
            ?: state.extras?.getString("com.spotify.music.extra.CONTEXT_TITLE")
            ?: rawQueueTitle

        val contextTitle = extractContextTitleFromText(rawCandidateTitle)

        val effectiveContextType = matchingNotifContextType
            ?: if (!contextTitle.isNullOrBlank()) "playlist" else null

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
            contextTypeHint = effectiveContextType
        )
        PlaybackTracker.notifyPlaybackTriggered(applicationContext)
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
