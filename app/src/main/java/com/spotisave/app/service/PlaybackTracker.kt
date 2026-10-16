package com.spotisave.app.service

import android.content.Context
import android.util.Log
import com.spotisave.app.data.api.SpotifyApiClient
import com.spotisave.app.data.api.SpotifyAuthManager
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Zentraler Logik-Handler für die Spotify-Wiedergabeerfassung.
 * Führt Datenströme aus BroadcastReceiver und NotificationListenerService zusammen
 * und persistiert Lesezeichen bei Positionsänderungen, Pausen oder Trackwechseln
 * sowie lückenlose Listening-History aller gespielten Titel.
 */
object PlaybackTracker {

    private const val TAG = "PlaybackTracker"
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Zwischengespeicherte Metadaten mit @Volatile für threadsichere Background-Erfassung
    @Volatile private var lastTrackUri: String = ""
    @Volatile private var lastTrackTitle: String = ""
    @Volatile private var lastArtist: String = ""
    @Volatile private var lastAlbum: String = ""
    @Volatile private var lastContextUri: String? = null
    @Volatile private var lastContextTitle: String? = null
    @Volatile private var lastCoverUrl: String? = null
    @Volatile private var lastDurationMs: Long = 0L
    @Volatile private var lastPositionMs: Long = 0L
    @Volatile private var lastIsPlaying: Boolean = false
    @Volatile private var lastSaveTimeMs: Long = 0L
    @Volatile private var activeSessionHistoryId: String? = null

    // In-Memory Cache für Playlist-Namen und Playlist-Cover (verhindert redundante API-Aufrufe)
    private val playlistNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val playlistCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun onMetadataChanged(
        context: Context,
        trackUri: String?,
        trackTitle: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        contextUri: String? = null,
        contextTitle: String? = null,
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
            activeSessionHistoryId = "${trackUri}_${System.currentTimeMillis()}"
        }

        lastTrackUri = trackUri ?: lastTrackUri
        lastTrackTitle = trackTitle ?: "Unbekannter Titel"
        lastArtist = artist ?: "Unbekannter Interpret"
        lastAlbum = album ?: "Spotify"
        if (!contextUri.isNullOrBlank()) {
            lastContextUri = contextUri
            val cached = playlistNameCache[contextUri]
            if (!cached.isNullOrBlank()) {
                lastContextTitle = cached
            }
            val cachedCover = playlistCoverCache[contextUri]
            if (!cachedCover.isNullOrBlank()) {
                lastCoverUrl = cachedCover
            }
        }
        if (!contextTitle.isNullOrBlank() && contextTitle != "Spotify") {
            lastContextTitle = contextTitle
            if (!lastContextUri.isNullOrBlank()) {
                playlistNameCache[lastContextUri!!] = contextTitle
            }
        }
        if (!coverUrl.isNullOrBlank()) {
            val isPlaylistWithCustomCover = lastContextUri?.startsWith("spotify:playlist:") == true && playlistCoverCache.containsKey(lastContextUri)
            if (!isPlaylistWithCustomCover || lastCoverUrl.isNullOrBlank()) {
                lastCoverUrl = coverUrl
            }
        }
        if (durationMs > 0) {
            lastDurationMs = durationMs
        }

        updateRepositoryState(context, source)

        // Web-API Enrichment: wenn contextUri fehlt ODER wenn contextUri eine Playlist ist, deren Name noch fehlt
        val needsPlaylistName = !lastContextUri.isNullOrBlank() &&
            lastContextUri!!.startsWith("spotify:playlist:") &&
            (lastContextTitle.isNullOrBlank() || lastContextTitle == "Spotify" || lastContextTitle == "Playlist")

        if (lastContextUri.isNullOrBlank() || needsPlaylistName) {
            enrichFromSpotifyApi(context)
        }
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
        contextTitle: String? = null,
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
            contextTitle = contextTitle,
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
                contextTitle = lastContextTitle,
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

        val isPlaylist = !lastContextUri.isNullOrBlank() && lastContextUri!!.startsWith("spotify:playlist:")
        val isAlbum = !lastContextUri.isNullOrBlank() && lastContextUri!!.startsWith("spotify:album:")

        val resolvedContextTitle = when {
            !lastContextTitle.isNullOrBlank() && lastContextTitle != "Spotify" -> lastContextTitle!!
            !lastContextUri.isNullOrBlank() && playlistNameCache.containsKey(lastContextUri) -> playlistNameCache[lastContextUri]!!
            isPlaylist -> "Playlist"
            lastAlbum.isNotBlank() && lastAlbum != "Spotify" -> lastAlbum
            isAlbum -> "Album"
            else -> "Spotify"
        }

        val bookmark = Bookmark(
            id = bookmarkId,
            contextUri = lastContextUri,
            contextTitle = resolvedContextTitle,
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
            if (!lastContextUri.isNullOrBlank() && lastTrackUri.isNotBlank() && lastTrackUri != bookmarkId) {
                repository.deleteBookmark(lastTrackUri)
            }
            repository.saveBookmark(bookmark)

            // Vollständigen Verlaufseintrag dauerhaft in listening_history persistieren
            val sessionId = activeSessionHistoryId ?: "${lastTrackUri}_${System.currentTimeMillis()}"
            activeSessionHistoryId = sessionId
            val historyEntry = HistoryTrackEntry(
                id = sessionId,
                trackUri = lastTrackUri,
                trackTitle = lastTrackTitle,
                artist = lastArtist,
                album = lastAlbum,
                contextUri = lastContextUri,
                contextTitle = resolvedContextTitle,
                coverUrl = lastCoverUrl,
                durationMs = lastDurationMs,
                listenedMs = maxOf(lastPositionMs, 1000L),
                timestamp = System.currentTimeMillis(),
                source = source
            )
            repository.recordHistoryEntry(historyEntry)

            lastSaveTimeMs = System.currentTimeMillis()
            Log.i(TAG, "Bookmark & History gespeichert: '${bookmark.trackTitle}' bei ${bookmark.positionMs} ms ($source)")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Bookmarks/History", e)
        }
    }

    fun enrichFromSpotifyApi(context: Context) {
        val authManager = SpotifyAuthManager.getInstance(context)
        if (!authManager.isLoggedIn()) return
        trackerScope.launch {
            try {
                val apiClient = SpotifyApiClient.getInstance(context)

                // 1. Wenn lastContextUri bereits bekannt ist und eine Playlist ist:
                val curContextUri = lastContextUri
                if (!curContextUri.isNullOrBlank() && curContextUri.startsWith("spotify:playlist:")) {
                    val plId = curContextUri.removePrefix("spotify:playlist:")
                    val cached = playlistNameCache[curContextUri]
                    val cachedCover = playlistCoverCache[curContextUri]
                    if (!cached.isNullOrBlank() && !cachedCover.isNullOrBlank()) {
                        lastContextTitle = cached
                        lastCoverUrl = cachedCover
                    } else {
                        val plInfo = apiClient.getPlaylistInfo(plId).getOrNull()
                        if (plInfo != null) {
                            playlistNameCache[curContextUri] = plInfo.name
                            lastContextTitle = plInfo.name
                            if (!plInfo.coverUrl.isNullOrBlank()) {
                                playlistCoverCache[curContextUri] = plInfo.coverUrl
                                lastCoverUrl = plInfo.coverUrl
                            }
                            val repository = BookmarkRepository.initialize(context)
                            repository.updateBookmarkContextTitle(curContextUri, plInfo.name, plInfo.coverUrl)
                            updateRepositoryState(context, "WebApiEnrich")
                            flushBookmark(context, "WebApiEnrich")
                        }
                    }
                }

                // 2. Nun Live-Status von /me/player abfragen (erfasst contextUri falls im Broadcast gefehlt)
                val stateResult = apiClient.getNowPlaying()
                val state = stateResult.getOrNull()
                if (state != null && state.track != null) {
                    val apiTrack = state.track
                    val apiContextUri = state.contextUri
                    if (!apiContextUri.isNullOrBlank()) {
                        lastContextUri = apiContextUri
                    }
                    if (!apiTrack.coverUrl.isNullOrBlank() && lastCoverUrl.isNullOrBlank()) {
                        lastCoverUrl = apiTrack.coverUrl
                    }
                    if (!apiContextUri.isNullOrBlank() && apiContextUri.startsWith("spotify:playlist:")) {
                        val plId = apiContextUri.removePrefix("spotify:playlist:")
                        val cached = playlistNameCache[apiContextUri]
                        val cachedCover = playlistCoverCache[apiContextUri]
                        if (!cached.isNullOrBlank() && !cachedCover.isNullOrBlank()) {
                            lastContextTitle = cached
                            lastCoverUrl = cachedCover
                        } else {
                            val plInfo = apiClient.getPlaylistInfo(plId).getOrNull()
                            if (plInfo != null) {
                                playlistNameCache[apiContextUri] = plInfo.name
                                lastContextTitle = plInfo.name
                                if (!plInfo.coverUrl.isNullOrBlank()) {
                                    playlistCoverCache[apiContextUri] = plInfo.coverUrl
                                    lastCoverUrl = plInfo.coverUrl
                                }
                                val repository = BookmarkRepository.initialize(context)
                                repository.updateBookmarkContextTitle(apiContextUri, plInfo.name, plInfo.coverUrl)
                            }
                        }
                    }
                    updateRepositoryState(context, "WebApiEnrich")
                    if (lastPositionMs > 1000L || lastIsPlaying) {
                        flushBookmark(context, "WebApiEnrich")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "API-Enrichment übersprungen: ${e.message}")
            }
        }
    }
}

