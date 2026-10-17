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
    @Volatile private var lastContextTypeHint: String? = null
    @Volatile private var lastCoverUrl: String? = null
    @Volatile private var lastDurationMs: Long = 0L
    @Volatile private var lastPositionMs: Long = 0L
    @Volatile private var lastIsPlaying: Boolean = false
    @Volatile private var lastSaveTimeMs: Long = 0L
    @Volatile private var activeSessionHistoryId: String? = null

    // Resume-Schutzfenster: verhindert, dass beim 1-Click-Resume einer anderen Playlist
    // ein initialer 0:00-Status oder ein verspätetes Pause-Event der vorherigen Playlist
    // den gespeicherten Stand überschreibt.
    private const val RESUME_GRACE_MS = 12_000L
    @Volatile private var pendingResumeBookmark: Bookmark? = null
    @Volatile private var pendingResumeTimestampMs: Long = 0L
    @Volatile private var activeResumedTrackUri: String? = null
    @Volatile private var activeResumedContextUri: String? = null
    @Volatile private var activeResumedContextTitle: String? = null
    @Volatile private var activeResumedCoverUrl: String? = null

    // In-Memory Cache für Playlist-Namen und Playlist-Cover (verhindert redundante API-Aufrufe)
    private val playlistNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val playlistCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun isWithinResumeGrace(now: Long = System.currentTimeMillis()): Boolean {
        val pending = pendingResumeBookmark ?: return false
        val elapsed = now - pendingResumeTimestampMs
        return elapsed in 0..RESUME_GRACE_MS && pending.trackUri.isNotBlank()
    }

    /**
     * Wird von ResumeHelper unmittelbar vor dem Starten eines Lesezeichens aufgerufen.
     * Sichert zuerst den aktuellen Hörstand einer evtl. laufenden anderen Playlist und
     * schützt das wiederaufgenommene Lesezeichen während des Ladevorgangs vor 0:00-Resets.
     */
    fun onResumeInitiated(context: Context, bookmark: Bookmark) {
        if (lastTrackUri.isNotBlank() && lastTrackUri != bookmark.trackUri && lastPositionMs > 3000L) {
            flushBookmark(context, "PreResumeFlush")
        }

        val now = System.currentTimeMillis()
        pendingResumeBookmark = bookmark
        pendingResumeTimestampMs = now

        val effectiveCtxUri = bookmark.contextUri
            ?: bookmark.id.takeIf { it.startsWith("spotify:playlist:") || it.startsWith("spotify:album:") }

        activeResumedTrackUri = bookmark.trackUri
        activeResumedContextUri = effectiveCtxUri
        activeResumedContextTitle = bookmark.contextTitle
        activeResumedCoverUrl = bookmark.coverUrl

        lastTrackUri = bookmark.trackUri
        lastTrackTitle = bookmark.trackTitle
        lastArtist = bookmark.artist
        lastContextUri = effectiveCtxUri
        lastContextTitle = bookmark.contextTitle
        lastContextTypeHint = when {
            bookmark.isPlaylist -> "playlist"
            bookmark.isAlbum -> "album"
            else -> null
        }
        if (!bookmark.coverUrl.isNullOrBlank()) {
            lastCoverUrl = bookmark.coverUrl
        }
        if (bookmark.positionMs > 0L) {
            lastPositionMs = bookmark.positionMs
        }
        if (bookmark.durationMs > 0L) {
            lastDurationMs = bookmark.durationMs
        }
        activeSessionHistoryId = "${bookmark.trackUri}_$now"

        if (!effectiveCtxUri.isNullOrBlank() && effectiveCtxUri.startsWith("spotify:playlist:")) {
            if (bookmark.contextTitle.isNotBlank() && bookmark.contextTitle != "Spotify" && bookmark.contextTitle != "Playlist") {
                playlistNameCache[effectiveCtxUri] = bookmark.contextTitle
            }
            if (!bookmark.coverUrl.isNullOrBlank()) {
                playlistCoverCache[effectiveCtxUri] = bookmark.coverUrl
            }
        }

        updateRepositoryState(context, "ResumeInitiated")
    }

    /**
     * Bestimmt eindeutig die Bookmark-ID und Context-URI, sodass Playlists niemals
     * fälschlicherweise als "album:..." gespeichert werden.
     */
    fun computeBookmarkIdentity(
        contextUri: String?,
        contextTitle: String?,
        album: String?,
        trackTitle: String?,
        trackUri: String,
        contextTypeHint: String? = null
    ): Pair<String, String?> {
        if (!contextUri.isNullOrBlank()) {
            return Pair(contextUri, contextUri)
        }

        val cleanCtxTitle = contextTitle?.trim()?.takeIf { it.isNotBlank() && it != "Spotify" && it != "Album" }
        val cleanAlbum = album?.trim()?.takeIf { it.isNotBlank() && it != "Spotify" }
        val cleanTrack = trackTitle?.trim()

        val hasDistinctPlaylistTitle = cleanCtxTitle != null &&
            !cleanCtxTitle.equals(cleanAlbum, ignoreCase = true) &&
            !cleanCtxTitle.equals(cleanTrack, ignoreCase = true)

        if (contextTypeHint == "playlist" || hasDistinctPlaylistTitle) {
            val rawName = cleanCtxTitle ?: "playlist"
            val slug = rawName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifEmpty { "playlist" }
            val localPlaylistUri = "spotify:playlist:local_$slug"
            return Pair(localPlaylistUri, localPlaylistUri)
        }

        if (contextTypeHint == "album" || cleanAlbum != null) {
            val albumSource = cleanAlbum ?: cleanCtxTitle ?: "album"
            val normalized = albumSource.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifEmpty { "album" }
            return Pair("album:$normalized", null)
        }

        return Pair(trackUri, null)
    }

    private fun resolveKnownPlaylistContextUri(
        context: Context,
        trackUri: String?,
        contextTitle: String?
    ): String? {
        val cleanTitle = contextTitle?.trim()?.takeIf {
            it.isNotBlank() && it != "Spotify" && it != "Playlist" && it != "Album"
        }

        if (cleanTitle != null) {
            val cachedMatch = playlistNameCache.entries.firstOrNull { (_, name) ->
                name.equals(cleanTitle, ignoreCase = true)
            }?.key
            if (!cachedMatch.isNullOrBlank()) return cachedMatch
        }

        return try {
            val repository = BookmarkRepository.initialize(context)
            val savedBookmarks = repository.bookmarks.value
            val historyEntries = repository.listeningHistory.value

            if (cleanTitle != null) {
                val bmByTitle = savedBookmarks.firstOrNull {
                    it.isPlaylist && it.contextTitle.equals(cleanTitle, ignoreCase = true)
                }
                if (bmByTitle != null) return bmByTitle.contextUri ?: bmByTitle.id

                val histByTitle = historyEntries.firstOrNull {
                    it.isPlaylist && it.contextTitle.equals(cleanTitle, ignoreCase = true)
                }
                if (histByTitle?.contextUri != null) return histByTitle.contextUri
            }

            if (!trackUri.isNullOrBlank()) {
                val bmByTrack = savedBookmarks.firstOrNull {
                    it.isPlaylist && it.trackUri == trackUri &&
                        (cleanTitle == null || it.contextTitle.equals(cleanTitle, ignoreCase = true))
                }
                if (bmByTrack != null) return bmByTrack.contextUri ?: bmByTrack.id

                val histByTrack = historyEntries.firstOrNull {
                    it.isPlaylist && it.trackUri == trackUri &&
                        (cleanTitle == null || it.contextTitle.equals(cleanTitle, ignoreCase = true))
                }
                if (histByTrack?.contextUri != null) return histByTrack.contextUri
            }
            null
        } catch (_: Exception) {
            null
        }
    }

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
        source: String = "Broadcast",
        contextTypeHint: String? = null
    ) {
        if (trackUri.isNullOrBlank() && trackTitle.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        if (isWithinResumeGrace(now)) {
            val resumed = pendingResumeBookmark
            if (resumed != null && !trackUri.isNullOrBlank() && trackUri != resumed.trackUri &&
                trackUri != "spotify:track:active" && trackUri != "spotify:track:notification"
            ) {
                Log.d(TAG, "Ignoriere veraltetes Metadaten-Event für $trackUri während Resume-Schutzfenster")
                return
            }
        }

        val isPlaceholderUri = trackUri == "spotify:track:active" || trackUri == "spotify:track:notification"
        val effectiveIncomingTrackUri = if (isPlaceholderUri && lastTrackUri.isNotBlank() && !lastTrackUri.endsWith(":active") && !lastTrackUri.endsWith(":notification")) {
            if (!trackTitle.isNullOrBlank() && trackTitle == lastTrackTitle) lastTrackUri else trackUri
        } else {
            trackUri
        }

        val isNewTrack = lastTrackUri.isNotBlank() &&
            !effectiveIncomingTrackUri.isNullOrBlank() &&
            lastTrackUri != effectiveIncomingTrackUri

        // Falls sich der Track geändert hat und der vorherige Track eine signifikante Position hatte:
        // Vor dem Ändern der Felder für die bisherige Playlist speichern!
        if (isNewTrack && lastPositionMs > 3000L) {
            flushBookmark(context, "Trackwechsel")
        }

        // Bei Trackwechsel MUSS die Position für den neuen Track zurückgesetzt werden (verhindert Position-Bleed)
        if (isNewTrack) {
            val resumed = pendingResumeBookmark
            if (isWithinResumeGrace(now) && resumed != null && resumed.trackUri == effectiveIncomingTrackUri) {
                lastPositionMs = resumed.positionMs
            } else {
                lastPositionMs = 0L
            }
            activeSessionHistoryId = "${effectiveIncomingTrackUri}_${System.currentTimeMillis()}"

            // Multi-Playlist-Isolierung bei Trackwechsel:
            // Verhindert, dass ein neuer Song aus Playlist B fälschlicherweise unter lastContextUri von Playlist A gespeichert wird!
            if (contextUri.isNullOrBlank()) {
                if (activeResumedTrackUri == effectiveIncomingTrackUri && !activeResumedContextUri.isNullOrBlank()) {
                    lastContextUri = activeResumedContextUri
                    lastContextTitle = activeResumedContextTitle ?: lastContextTitle
                    lastCoverUrl = activeResumedCoverUrl ?: coverUrl
                } else {
                    val sameExplicitContextTitle = !contextTitle.isNullOrBlank() &&
                        contextTitle != "Spotify" &&
                        contextTitle.equals(lastContextTitle, ignoreCase = true)

                    if (sameExplicitContextTitle && !lastContextUri.isNullOrBlank()) {
                        // Gleiche Playlist laut Notification/MediaSession bestätigt -> lastContextUri beibehalten
                    } else {
                        val resolvedUri = resolveKnownPlaylistContextUri(context, effectiveIncomingTrackUri, contextTitle)
                        lastContextUri = resolvedUri
                        if (resolvedUri == null) {
                            lastContextTitle = contextTitle?.takeIf { it.isNotBlank() && it != "Spotify" }
                            lastCoverUrl = coverUrl
                        } else {
                            lastContextTitle = playlistNameCache[resolvedUri]
                                ?: contextTitle?.takeIf { it.isNotBlank() && it != "Spotify" }
                            lastCoverUrl = playlistCoverCache[resolvedUri] ?: coverUrl
                        }
                    }
                    lastContextTypeHint = contextTypeHint
                }
            }
        }

        lastTrackUri = effectiveIncomingTrackUri ?: lastTrackUri
        lastTrackTitle = trackTitle ?: "Unbekannter Titel"
        lastArtist = artist ?: "Unbekannter Interpret"
        lastAlbum = album ?: "Spotify"

        if (!contextTypeHint.isNullOrBlank()) {
            lastContextTypeHint = contextTypeHint
        }

        if (!contextUri.isNullOrBlank()) {
            val contextChanged = lastContextUri != null && lastContextUri != contextUri
            lastContextUri = contextUri
            if (contextUri.startsWith("spotify:playlist:")) {
                lastContextTypeHint = "playlist"
            } else if (contextUri.startsWith("spotify:album:")) {
                lastContextTypeHint = "album"
            }
            val cached = playlistNameCache[contextUri]
            if (!cached.isNullOrBlank()) {
                lastContextTitle = cached
            } else if (contextChanged && contextTitle.isNullOrBlank()) {
                lastContextTitle = null
            }
            val cachedCover = playlistCoverCache[contextUri]
            if (!cachedCover.isNullOrBlank()) {
                lastCoverUrl = cachedCover
            } else if (contextChanged) {
                lastCoverUrl = coverUrl
            }
        } else if (lastContextUri.isNullOrBlank()) {
            val resolvedUri = resolveKnownPlaylistContextUri(context, lastTrackUri, contextTitle ?: lastContextTitle)
            if (!resolvedUri.isNullOrBlank()) {
                lastContextUri = resolvedUri
                lastContextTypeHint = "playlist"
                playlistNameCache[resolvedUri]?.let { lastContextTitle = it }
                playlistCoverCache[resolvedUri]?.let { lastCoverUrl = it }
            }
        }

        if (!contextTitle.isNullOrBlank() && contextTitle != "Spotify") {
            val titleChanged = !lastContextTitle.isNullOrBlank() && !lastContextTitle.equals(contextTitle, ignoreCase = true)
            lastContextTitle = contextTitle
            if (titleChanged && contextUri.isNullOrBlank()) {
                val resolvedForNewTitle = resolveKnownPlaylistContextUri(context, lastTrackUri, contextTitle)
                lastContextUri = resolvedForNewTitle
                lastCoverUrl = resolvedForNewTitle?.let { playlistCoverCache[it] } ?: coverUrl
            }
            if (!lastContextUri.isNullOrBlank() && lastContextUri!!.startsWith("spotify:playlist:")) {
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

        // Web-API Enrichment: bei jedem Trackwechsel, fehlender/lokaler contextUri oder fehlendem Playlist-Namen
        val needsPlaylistName = !lastContextUri.isNullOrBlank() &&
            lastContextUri!!.startsWith("spotify:playlist:") &&
            (lastContextTitle.isNullOrBlank() || lastContextTitle == "Spotify" || lastContextTitle == "Playlist" ||
                lastContextUri!!.startsWith("spotify:playlist:local_"))

        if (isNewTrack || lastContextUri.isNullOrBlank() || needsPlaylistName) {
            enrichFromSpotifyApi(context)
        }
    }

    fun onPlaybackStateChanged(
        context: Context,
        isPlaying: Boolean,
        positionMs: Long,
        source: String = "Broadcast",
        trackUri: String? = null
    ) {
        val now = System.currentTimeMillis()

        // Schutz gegen veraltete Broadcasts eines anderen Tracks
        if (!trackUri.isNullOrBlank() && lastTrackUri.isNotBlank() && trackUri != lastTrackUri) {
            if (!isPlaying || isWithinResumeGrace(now)) {
                Log.d(TAG, "Ignoriere PlaybackState für abweichenden Track $trackUri (aktiv: $lastTrackUri)")
                return
            }
        }

        // Resume-Schutzfenster: verhindert Überschreiben des Lesezeichens mit 0:00 bevor seekTo greift
        if (isWithinResumeGrace(now)) {
            val resumed = pendingResumeBookmark
            if (resumed != null) {
                if (resumed.positionMs > 3000L && positionMs < resumed.positionMs - 3000L) {
                    lastIsPlaying = isPlaying
                    lastPositionMs = resumed.positionMs
                    updateRepositoryState(context, source)
                    return
                } else if (isPlaying && positionMs >= resumed.positionMs - 3000L) {
                    pendingResumeBookmark = null
                }
            }
        }

        val wasPlaying = lastIsPlaying
        lastIsPlaying = isPlaying

        // Verhindert, dass ein plötzlicher 0ms-Reset beim Pausieren/Stoppen den echten Hörstand (z.B. 2:15) mit 0:00 überschreibt
        if (positionMs >= 0) {
            val isSuddenZeroOnPause = !isPlaying && positionMs <= 1000L && lastPositionMs > 3000L
            if (!isSuddenZeroOnPause) {
                lastPositionMs = positionMs
            }
        }

        updateRepositoryState(context, source)

        // 1. Wenn pausiert wird: Sofort Lesezeichen sichern!
        if (wasPlaying && !isPlaying) {
            Log.d(TAG, "Wiedergabe pausiert bei $lastPositionMs ms -> Sichern")
            flushBookmark(context, source)
        }
        // 2. Regelmäßige Zwischensicherung während der Wiedergabe (alle 10s)
        else if (isPlaying && (now - lastSaveTimeMs > 10_000L) && lastPositionMs > 2000L) {
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
        coverUrl: String? = null,
        contextTypeHint: String? = null
    ) {
        val prevTrackUri = lastTrackUri
        val prevPositionMs = lastPositionMs
        val isTrackSwitch = prevTrackUri.isNotBlank() &&
            !trackUri.isNullOrBlank() &&
            trackUri != "spotify:track:active" &&
            trackUri != "spotify:track:notification" &&
            prevTrackUri != trackUri

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
            source = "MediaSession",
            contextTypeHint = contextTypeHint
        )

        // Falls der Track gerade gewechselt hat, darf die veraltete PlaybackState-Position des vorherigen Tracks
        // nicht den 0ms-Reset von onMetadataChanged wieder überschreiben!
        val effectivePos = if (isTrackSwitch && !isWithinResumeGrace() && (positionMs == prevPositionMs || positionMs > 5000L)) {
            0L
        } else {
            positionMs
        }

        onPlaybackStateChanged(
            context = context,
            isPlaying = isPlaying,
            positionMs = effectivePos,
            source = "MediaSession",
            trackUri = lastTrackUri
        )
    }

    /**
     * Ticker-Update aus dem ForegroundService für kontinuierliche Positionsspeicherung in der Hosentasche.
     */
    fun syncPeriodicPosition(context: Context, positionMs: Long) {
        if (!lastIsPlaying || positionMs <= 0L) return
        if (isWithinResumeGrace()) {
            val resumed = pendingResumeBookmark
            if (resumed != null && resumed.positionMs > 3000L && positionMs < resumed.positionMs - 3000L) {
                return
            }
        }
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

        if (lastContextUri.isNullOrBlank()) {
            val resolved = resolveKnownPlaylistContextUri(context, lastTrackUri, lastContextTitle)
            if (!resolved.isNullOrBlank()) {
                lastContextUri = resolved
                lastContextTypeHint = "playlist"
            }
        }

        val (bookmarkId, effectiveContextUri) = computeBookmarkIdentity(
            contextUri = lastContextUri,
            contextTitle = lastContextTitle,
            album = lastAlbum,
            trackTitle = lastTrackTitle,
            trackUri = lastTrackUri,
            contextTypeHint = lastContextTypeHint
        )

        if (!effectiveContextUri.isNullOrBlank() && lastContextUri.isNullOrBlank()) {
            lastContextUri = effectiveContextUri
        }

        val isPlaylist = (!effectiveContextUri.isNullOrBlank() && effectiveContextUri.startsWith("spotify:playlist:")) ||
            bookmarkId.startsWith("spotify:playlist:")
        val isAlbum = (!effectiveContextUri.isNullOrBlank() && effectiveContextUri.startsWith("spotify:album:")) ||
            bookmarkId.startsWith("album:")

        val resolvedContextTitle = when {
            !lastContextTitle.isNullOrBlank() && lastContextTitle != "Spotify" -> lastContextTitle!!
            !effectiveContextUri.isNullOrBlank() && playlistNameCache.containsKey(effectiveContextUri) -> playlistNameCache[effectiveContextUri]!!
            isPlaylist -> "Playlist"
            lastAlbum.isNotBlank() && lastAlbum != "Spotify" -> lastAlbum
            isAlbum -> "Album"
            else -> "Spotify"
        }

        val bookmark = Bookmark(
            id = bookmarkId,
            contextUri = effectiveContextUri,
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
            if (isPlaylist) {
                repository.cleanupSupersededBookmarks(
                    canonicalId = bookmarkId,
                    trackUri = lastTrackUri,
                    album = lastAlbum,
                    contextTitle = resolvedContextTitle
                )
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
                contextUri = effectiveContextUri,
                contextTitle = resolvedContextTitle,
                coverUrl = lastCoverUrl,
                durationMs = lastDurationMs,
                listenedMs = maxOf(lastPositionMs, 1000L),
                timestamp = System.currentTimeMillis(),
                source = source
            )
            repository.recordHistoryEntry(historyEntry)

            lastSaveTimeMs = System.currentTimeMillis()
            Log.i(TAG, "Bookmark & History gespeichert: '${bookmark.trackTitle}' (${bookmark.id}) bei ${bookmark.positionMs} ms ($source)")
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

                // 1. Wenn lastContextUri bereits bekannt ist und eine echte Spotify-Playlist ist:
                val curContextUri = lastContextUri
                if (!curContextUri.isNullOrBlank() &&
                    curContextUri.startsWith("spotify:playlist:") &&
                    !curContextUri.startsWith("spotify:playlist:local_")
                ) {
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
                    // Nur anwenden, wenn /me/player auch zum aktuell verfolgten Track passt
                    if (lastTrackUri.isNotBlank() &&
                        lastTrackUri != "spotify:track:active" &&
                        lastTrackUri != "spotify:track:notification" &&
                        apiTrack.uri != lastTrackUri
                    ) {
                        return@launch
                    }

                    lastTrackUri = apiTrack.uri
                    val apiContextUri = state.contextUri
                        ?: if (activeResumedTrackUri == apiTrack.uri) activeResumedContextUri else null

                    if (!apiContextUri.isNullOrBlank()) {
                        val previousContextUri = lastContextUri
                        lastContextUri = apiContextUri
                        if (apiContextUri.startsWith("spotify:playlist:")) {
                            lastContextTypeHint = "playlist"
                        } else if (apiContextUri.startsWith("spotify:album:")) {
                            lastContextTypeHint = "album"
                            if (previousContextUri != apiContextUri || lastContextTitle.isNullOrBlank()) {
                                lastContextTitle = apiTrack.albumName.takeIf { it.isNotBlank() } ?: lastAlbum
                            }
                        }
                    }
                    if (!apiTrack.coverUrl.isNullOrBlank() && lastCoverUrl.isNullOrBlank()) {
                        lastCoverUrl = apiTrack.coverUrl
                    }
                    if (!apiContextUri.isNullOrBlank() &&
                        apiContextUri.startsWith("spotify:playlist:") &&
                        !apiContextUri.startsWith("spotify:playlist:local_")
                    ) {
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

