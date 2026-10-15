package com.spotisave.app.service

import android.content.Context
import android.util.Log
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.repository.BookmarkRepository

/**
 * Zentraler Logik-Handler für die Spotify-Wiedergabeerfassung.
 * Führt Datenströme aus BroadcastReceiver und NotificationListenerService zusammen
 * und persistiert Lesezeichen bei Positionsänderungen, Pausen oder Trackwechseln.
 */
object PlaybackTracker {

    private const val TAG = "PlaybackTracker"

    // Zwischengespeicherte Metadaten mit @Volatile für threadsichere Background-Erfassung
    @Volatile private var lastTrackUri: String = ""
    @Volatile private var lastTrackTitle: String = ""
    @Volatile private var lastArtist: String = ""
    @Volatile private var lastAlbum: String = ""
    @Volatile private var lastContextUri: String? = null
    @Volatile private var lastCoverUrl: String? = null
    @Volatile private var lastDurationMs: Long = 0L
    @Volatile private var lastPositionMs: Long = 0L
    @Volatile private var lastIsPlaying: Boolean = false
    @Volatile private var lastSaveTimeMs: Long = 0L

    fun onMetadataChanged(
        context: Context,
        trackUri: String?,
        trackTitle: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        contextUri: String? = null,
        coverUrl: String? = null,
        source: String = "Broadcast"
    ) {
        if (trackUri.isNullOrBlank() && trackTitle.isNullOrBlank()) return

        val isNewTrack = lastTrackUri.isNotBlank() && trackUri != null && lastTrackUri != trackUri

        // Falls sich der Track geändert hat und der vorherige Track eine signifikante Position hatte:
        if (isNewTrack && lastPositionMs > 3000L) {
            flushBookmark(context, "Trackwechsel")
        }

        // Bei Trackwechsel MUSS die Position für den neuen Track zurückgesetzt werden (verhindert Position-Bleed)
        if (isNewTrack) {
            lastPositionMs = 0L
        }

        lastTrackUri = trackUri ?: lastTrackUri
        lastTrackTitle = trackTitle ?: "Unbekannter Titel"
        lastArtist = artist ?: "Unbekannter Interpret"
        lastAlbum = album ?: "Spotify"
        if (!contextUri.isNullOrBlank()) {
            lastContextUri = contextUri
        }
        if (!coverUrl.isNullOrBlank()) {
            lastCoverUrl = coverUrl
        }
        if (durationMs > 0) {
            lastDurationMs = durationMs
        }

        updateRepositoryState(context, source)
    }

    fun onPlaybackStateChanged(
        context: Context,
        isPlaying: Boolean,
        positionMs: Long,
        source: String = "Broadcast"
    ) {
        val wasPlaying = lastIsPlaying
        lastIsPlaying = isPlaying
        if (positionMs >= 0) {
            lastPositionMs = positionMs
        }

        updateRepositoryState(context, source)

        val now = System.currentTimeMillis()

        // 1. Wenn pausiert wird: Sofort Lesezeichen sichern!
        if (wasPlaying && !isPlaying) {
            Log.d(TAG, "Wiedergabe pausiert bei $positionMs ms -> Sichern")
            flushBookmark(context, source)
        }
        // 2. Regelmäßige Zwischensicherung während der Wiedergabe (alle 10s)
        else if (isPlaying && (now - lastSaveTimeMs > 10_000L) && positionMs > 2000L) {
            flushBookmark(context, source)
        }
    }

    fun onMediaSessionUpdate(
        context: Context,
        trackUri: String?,
        trackTitle: String?,
        artist: String?,
        album: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        contextUri: String? = null,
        coverUrl: String? = null
    ) {
        onMetadataChanged(
            context = context,
            trackUri = trackUri,
            trackTitle = trackTitle,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contextUri = contextUri,
            coverUrl = coverUrl,
            source = "MediaSession"
        )
        onPlaybackStateChanged(
            context = context,
            isPlaying = isPlaying,
            positionMs = positionMs,
            source = "MediaSession"
        )
    }

    /**
     * Ticker-Update aus dem ForegroundService für kontinuierliche Positionsspeicherung in der Hosentasche.
     */
    fun syncPeriodicPosition(context: Context, positionMs: Long) {
        if (!lastIsPlaying || positionMs <= 0L) return
        lastPositionMs = positionMs
        updateRepositoryState(context, "PeriodicSync")
        val now = System.currentTimeMillis()
        if (now - lastSaveTimeMs >= 10_000L && positionMs > 2000L) {
            flushBookmark(context, "PeriodicSync")
        }
    }

    private fun updateRepositoryState(context: Context, source: String) {
        try {
            val repository = BookmarkRepository.initialize(context)
            val model = PlaybackStateModel(
                trackUri = lastTrackUri,
                trackTitle = lastTrackTitle,
                artist = lastArtist,
                album = lastAlbum,
                contextUri = lastContextUri,
                coverUrl = lastCoverUrl,
                positionMs = lastPositionMs,
                durationMs = lastDurationMs,
                isPlaying = lastIsPlaying,
                source = source
            )
            repository.updateActivePlayback(model)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Aktualisieren des Wiedergabestatus", e)
        }
    }

    fun flushBookmark(context: Context, source: String) {
        if (lastTrackTitle.isBlank() || lastTrackUri.isBlank()) return

        // Per-Playlist/Album Bookmark-ID: Playlists und Alben haben genau 1 aktives Lesezeichen,
        // das bei neuen Tracks aktualisiert wird anstatt duplizierte Zeilen anzuhäufen.
        val bookmarkId = when {
            !lastContextUri.isNullOrBlank() -> lastContextUri!!
            lastAlbum.isNotBlank() && lastAlbum != "Spotify" -> {
                val normalized = lastAlbum.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
                "album:$normalized"
            }
            else -> lastTrackUri
        }

        val bookmark = Bookmark(
            id = bookmarkId,
            contextUri = lastContextUri,
            contextTitle = if (lastAlbum.isNotBlank() && lastAlbum != "Spotify") lastAlbum else "Spotify",
            trackTitle = lastTrackTitle,
            artist = lastArtist,
            trackUri = lastTrackUri,
            coverUrl = lastCoverUrl,
            positionMs = lastPositionMs,
            durationMs = lastDurationMs,
            timestamp = System.currentTimeMillis(),
            source = source
        )

        try {
            val repository = BookmarkRepository.initialize(context)
            repository.saveBookmark(bookmark)
            lastSaveTimeMs = System.currentTimeMillis()
            Log.i(TAG, "Bookmark gespeichert: '${bookmark.trackTitle}' bei ${bookmark.positionMs} ms ($source)")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Bookmarks", e)
        }
    }
}
