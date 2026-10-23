package com.spotisave.app.data.stats

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.ui.util.TimeFormatter
import java.util.Calendar
import java.util.Locale

object StatisticsEngine {

    /**
     * Prüft ob ein Track eine lokale Platzhalter-URI oder einen ungültigen Titel besitzt.
     * Platzhalter wie `spotify:track:active`, `:notification`, `local_` oder "Unbekannter Titel"
     * dürfen niemals Statistiken verfälschen.
     */
    fun isPlaceholderTrack(trackUri: String?, trackTitle: String?): Boolean {
        if (trackTitle.isNullOrBlank() || trackTitle.equals("Unbekannter Titel", ignoreCase = true)) return true
        if (trackUri.isNullOrBlank()) return true
        val clean = trackUri.trim()
        return clean == "spotify:track:active" ||
            clean.endsWith(":active") ||
            clean.endsWith(":notification") ||
            clean.startsWith("spotify:track:local_")
    }

    /**
     * Erkennt Warteschlangen-Labels und generische Spotify-Systemtexte ("Warteschlange", "Queue", etc.),
     * damit diese nicht als Playlists oder Alben gezählt werden.
     */
    fun isGenericQueueOrCorrupted(titleOrId: String?): Boolean {
        if (titleOrId.isNullOrBlank()) return false
        val clean = titleOrId.trim().lowercase(Locale.ROOT)
        return clean == "warteschlange" ||
            clean == "queue" ||
            clean == "wiedergabeschlange" ||
            clean.contains("warteschlange") ||
            clean.contains("queue") ||
            clean.startsWith("wiedergabe aus playlist") ||
            clean.startsWith("playing from playlist")
    }

    /**
     * Überladene Einstiegsmethode für reine Bookmark-Aufrufe (Rückwärtskompatibilität).
     */
    fun calculateStats(
        bookmarks: List<Bookmark>,
        activePlayback: PlaybackStateModel? = null
    ): ListeningStats = calculateStats(
        bookmarks = bookmarks,
        history = emptyList(),
        activePlayback = activePlayback,
        spotifyTopArtists = emptyList()
    )

    /**
     * Vollständige Berechnung aller Statistiken unter Einbeziehung der kontinuierlichen Listening-History.
     * Wenn `history` vorhanden ist, werden alle jemals gespielten Titel, Alben, Künstler und Hörzeiten
     * vollumfänglich ausgewertet. Platzhalter-Tracks und Warteschlangen-Artefakte werden vollständig gefiltert.
     */
    fun calculateStats(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry>,
        activePlayback: PlaybackStateModel? = null,
        spotifyTopArtists: List<ArtistStat> = emptyList()
    ): ListeningStats {
        // 0. Bereinigung aller Eingabedaten von Platzhalter-Tracks und Warteschlangen-Artefakten
        val cleanHistory = history.filter { h ->
            !isPlaceholderTrack(h.trackUri, h.trackTitle) &&
            !isGenericQueueOrCorrupted(h.contextTitle) &&
            !isGenericQueueOrCorrupted(h.album) &&
            !isGenericQueueOrCorrupted(h.id) &&
            h.durationMs >= 0L
        }

        val cleanBookmarks = bookmarks.filter { b ->
            !isPlaceholderTrack(b.trackUri, b.trackTitle) &&
            !isGenericQueueOrCorrupted(b.contextTitle) &&
            !isGenericQueueOrCorrupted(b.id) &&
            b.durationMs >= 0L
        }

        val cleanActivePlayback = activePlayback?.takeIf { a ->
            !isPlaceholderTrack(a.trackUri, a.trackTitle) &&
            !isGenericQueueOrCorrupted(a.contextTitle) &&
            !isGenericQueueOrCorrupted(a.album)
        }

        val hasHistory = cleanHistory.isNotEmpty()

        // 1. Gesamte Hörzeit akkumulieren (ohne Doppelzählung von aktivem Playback)
        var totalMs = 0L
        if (hasHistory) {
            totalMs = cleanHistory.sumOf { it.listenedMs.coerceAtLeast(0L) }
            if (cleanActivePlayback != null && cleanActivePlayback.isPlaying) {
                val latestHistory = cleanHistory.maxByOrNull { it.timestamp }
                val isMatchingLatest = latestHistory != null &&
                    latestHistory.trackUri.isNotBlank() &&
                    latestHistory.trackUri == cleanActivePlayback.trackUri &&
                    (System.currentTimeMillis() - latestHistory.timestamp < 15_000L)

                if (isMatchingLatest) {
                    val additional = (cleanActivePlayback.positionMs - latestHistory!!.listenedMs).coerceAtLeast(0L)
                    totalMs += additional
                } else {
                    totalMs += cleanActivePlayback.positionMs.coerceAtLeast(0L)
                }
            }
        } else {
            val matchingActiveBm = if (cleanActivePlayback != null && cleanActivePlayback.isPlaying) {
                cleanBookmarks.find {
                    (!it.trackUri.isNullOrBlank() && it.trackUri == cleanActivePlayback.trackUri) ||
                    (!it.contextUri.isNullOrBlank() && it.contextUri == cleanActivePlayback.contextUri)
                }
            } else null

            for (b in cleanBookmarks) {
                if (b == matchingActiveBm) {
                    totalMs += maxOf(b.positionMs, cleanActivePlayback?.positionMs ?: 0L).coerceAtLeast(0L)
                } else {
                    totalMs += b.positionMs.coerceAtLeast(0L)
                }
            }

            if (cleanActivePlayback != null && cleanActivePlayback.isPlaying && matchingActiveBm == null) {
                totalMs += cleanActivePlayback.positionMs.coerceAtLeast(0L)
            }
        }

        val formattedTime = TimeFormatter.formatHoursAndMinutes(totalMs)

        // 2. Anzahl verfolgter Playlists & Alben
        val uniquePlaylists = if (hasHistory) {
            (cleanHistory.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() && !isGenericQueueOrCorrupted(uri) }
                    ?: it.contextTitle.takeIf { t -> t != "Spotify" && !isGenericQueueOrCorrupted(t) }
            } + cleanBookmarks.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() && !isGenericQueueOrCorrupted(uri) }
                    ?: it.contextTitle.takeIf { t -> t != "Spotify" && !isGenericQueueOrCorrupted(t) }
            }).toSet()
        } else {
            cleanBookmarks.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() && !isGenericQueueOrCorrupted(uri) }
                    ?: it.contextTitle.takeIf { t -> t != "Spotify" && !isGenericQueueOrCorrupted(t) }
            }.toSet()
        }
        val totalPlaylistsCount = uniquePlaylists.size

        // Track-Anzahl & Durchschnittliche Track-Dauer
        val uniqueTracks = if (hasHistory) {
            (cleanHistory.map { if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}" } +
             cleanBookmarks.map { if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}" }).toSet()
        } else {
            cleanBookmarks.map { if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}" }.toSet()
        }
        val totalTracksCount = uniqueTracks.size

        val validDurations = if (hasHistory) {
            (cleanHistory.map { it.durationMs } + cleanBookmarks.map { it.durationMs }).filter { it > 0L }
        } else {
            cleanBookmarks.filter { it.durationMs > 0L }.map { it.durationMs }
        }
        val avgTrackDurationMs = if (validDurations.isNotEmpty()) validDurations.average().toLong() else 0L
        val formattedAvgDuration = if (avgTrackDurationMs > 0L) TimeFormatter.formatTime(avgTrackDurationMs) else "0:00"

        // 3. Top-Playlists Ranking mit Fortschritt, Hördauer & Completion Rate
        val topPlaylists = calculateTopPlaylists(cleanBookmarks, cleanHistory)

        // Primäres Top-Bookmark / Playlist für 1-Click Resume
        val primaryTopPlaylist = topPlaylists.firstOrNull()
        val topBm = primaryTopPlaylist?.bookmark
            ?: cleanBookmarks.maxByOrNull { it.positionMs }
            ?: cleanHistory.firstOrNull()?.toBookmark()
            ?: cleanBookmarks.firstOrNull()
        val topTitle = primaryTopPlaylist?.title ?: topBm?.contextTitle?.takeIf { it != "Spotify" && !isGenericQueueOrCorrupted(it) } ?: topBm?.trackTitle
        val topCover = primaryTopPlaylist?.coverUrl ?: topBm?.coverUrl
        val topArtist = primaryTopPlaylist?.bookmark?.artist ?: topBm?.artist
        val topTrackUri = primaryTopPlaylist?.bookmark?.trackUri ?: topBm?.trackUri
        val topContextUri = primaryTopPlaylist?.contextUri ?: topBm?.contextUri
        val topProgressMs = primaryTopPlaylist?.bookmark?.positionMs ?: topBm?.positionMs ?: 0L
        val topDurationMs = primaryTopPlaylist?.bookmark?.durationMs ?: topBm?.durationMs ?: 0L

        // 4. Lieblings-Album (nach akkumulierter Hörzeit und Titelanzahl)
        val topAlbum = calculateTopAlbum(bookmarks, history)

        // 5. Meistgehörte Songs / Top-Songs Ranking (mit 1-Click Resume)
        val topSongs = calculateTopSongs(bookmarks, history)

        // 6. Genaue Historie (Chronologische Listening History mit 'vor X Min.')
        val historyList = calculateHistory(cleanBookmarks, cleanHistory)

        // 7. Top-Interpreten Ranking (mit Splitting bei Kollaborationen/Duos)
        val artistCounts = mutableMapOf<String, Int>()
        val artistSource = if (hasHistory) {
            cleanHistory.map { it.artist }
        } else {
            cleanBookmarks.map { it.artist }
        }
        for (artistString in artistSource) {
            val raw = artistString.trim()
            if (raw.isNotBlank() && !raw.equals("Unbekannter Interpret", ignoreCase = true) && raw != "Spotify") {
                val splitNames = raw.split(Regex("[,&/]|\\sfeat\\.\\s|\\sft\\.\\s|\\sfeaturing\\s", RegexOption.IGNORE_CASE))
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("Unbekannter Interpret", ignoreCase = true) && it != "Spotify" }
                for (a in splitNames) {
                    artistCounts[a] = (artistCounts[a] ?: 0) + 1
                }
            }
        }

        val sortedArtists = if (spotifyTopArtists.isNotEmpty()) {
            spotifyTopArtists.take(5)
        } else {
            val totalArtistMentions = artistCounts.values.sum().coerceAtLeast(1)
            artistCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { (name, count) ->
                    ArtistStat(
                        name = name,
                        count = count,
                        percentage = (count.toFloat() / totalArtistMentions.toFloat()).coerceIn(0f, 1f)
                    )
                }
        }

        // 8. Wöchentliche Aktivität (Mo bis So) & Aktivster Tag
        val weeklyActivity = calculateWeeklyActivity(cleanBookmarks, cleanHistory)
        val peakDayIdx = weeklyActivity.indices.maxByOrNull { weeklyActivity[it].minutes } ?: 0
        val peakDay = weeklyActivity[peakDayIdx]
        val peakDayName = if (peakDay.minutes > 0) peakDay.dayName else "Noch offen"
        val peakDayMins = peakDay.minutes

        // 9. Tageszeiten-Verteilung (Morgen, Nachmittag, Abend, Nacht)
        val timeOfDayDistribution = calculateTimeOfDayDistribution(cleanBookmarks, cleanHistory)
        val morningMins = timeOfDayDistribution.find { it.slotName == "Morgen" }?.minutes ?: 0
        val afternoonMins = timeOfDayDistribution.find { it.slotName == "Nachmittag" }?.minutes ?: 0
        val eveningMins = timeOfDayDistribution.find { it.slotName == "Abend" }?.minutes ?: 0
        val nightMins = timeOfDayDistribution.find { it.slotName == "Nacht" }?.minutes ?: 0
        val peakSlotName = timeOfDayDistribution.find { it.isPeak }?.slotName ?: "Nachmittag"

        // Hör-Persönlichkeit (Persona) & Primäre Tageszeit
        val (listeningPersona, peakHourRange) = calculatePersonaAndHours(cleanBookmarks, cleanHistory)

        // 10. Durchhör-Quote (Completion Rate)
        val (completionRatePercent, completionRateLabel) = calculateCompletionRate(cleanBookmarks, cleanHistory)

        return ListeningStats(
            totalListeningTimeMs = totalMs,
            formattedTotalTime = formattedTime,
            totalTrackedPlaylists = totalPlaylistsCount,
            totalBookmarksCount = if (hasHistory) cleanHistory.size else cleanBookmarks.size,
            totalTracksCount = totalTracksCount,
            averageTrackDurationMs = avgTrackDurationMs,
            formattedAvgTrackDuration = formattedAvgDuration,
            topBookmark = topBm,
            topPlaylistTitle = topTitle,
            topPlaylistCover = topCover,
            topPlaylistArtist = topArtist,
            topPlaylistTrackUri = topTrackUri,
            topPlaylistContextUri = topContextUri,
            topPlaylistProgressMs = topProgressMs,
            topPlaylistDurationMs = topDurationMs,
            topAlbum = topAlbum,
            topSongs = topSongs,
            history = historyList,
            topArtists = sortedArtists,
            weeklyActivity = weeklyActivity,
            peakDayName = peakDayName,
            peakDayMinutes = peakDayMins,
            listeningPersona = listeningPersona,
            peakHourRange = peakHourRange,
            completionRatePercent = completionRatePercent,
            completionRateLabel = completionRateLabel,
            timeOfDayDistribution = timeOfDayDistribution,
            topPlaylists = topPlaylists,
            morningMinutes = morningMins,
            afternoonMinutes = afternoonMins,
            eveningMinutes = eveningMins,
            nightMinutes = nightMins,
            peakTimeOfDaySlot = peakSlotName
        )
    }

    /**
     * Berechnet die Tageszeiten-Verteilung auf die 4 Kern-Phasen:
     * - Morgen (06:00 – 12:00 Uhr)
     * - Nachmittag (12:00 – 18:00 Uhr)
     * - Abend (18:00 – 22:00 Uhr)
     * - Nacht (22:00 – 06:00 Uhr)
     */
    fun calculateTimeOfDayDistribution(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): List<TimeOfDayStat> {
        val slotMinutes = IntArray(4) // 0: Morgen, 1: Nachmittag, 2: Abend, 3: Nacht
        val cal = Calendar.getInstance(Locale.GERMANY)

        val cleanHistory = history.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }
        val cleanBookmarks = bookmarks.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }

        if (cleanHistory.isNotEmpty()) {
            for (h in cleanHistory) {
                cal.timeInMillis = h.timestamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val mins = (h.listenedMs / 60000L).toInt().coerceAtLeast(0)
                val idx = when (hour) {
                    in 6..11 -> 0
                    in 12..17 -> 1
                    in 18..21 -> 2
                    else -> 3
                }
                slotMinutes[idx] += mins
            }
        } else {
            for (b in cleanBookmarks) {
                cal.timeInMillis = b.timestamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val mins = (b.positionMs / 60000L).toInt().coerceAtLeast(0)
                val idx = when (hour) {
                    in 6..11 -> 0
                    in 12..17 -> 1
                    in 18..21 -> 2
                    else -> 3
                }
                slotMinutes[idx] += mins
            }
        }

        val totalMinutes = slotMinutes.sum().coerceAtLeast(1)
        val peakIdx = if (slotMinutes.any { it > 0 }) {
            slotMinutes.indices.maxByOrNull { slotMinutes[it] } ?: 1
        } else {
            1 // Standard: Nachmittag wenn noch keine Daten
        }

        val slotDefs = listOf(
            Triple("Morgen", "06:00 – 12:00 Uhr", 0),
            Triple("Nachmittag", "12:00 – 18:00 Uhr", 1),
            Triple("Abend", "18:00 – 22:00 Uhr", 2),
            Triple("Nacht", "22:00 – 06:00 Uhr", 3)
        )

        return slotDefs.map { (name, range, idx) ->
            val m = slotMinutes[idx]
            val pct = if (slotMinutes.sum() > 0) (m.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f) else 0f
            TimeOfDayStat(
                slotName = name,
                slotRange = range,
                minutes = m,
                percentage = pct,
                isPeak = (idx == peakIdx && slotMinutes[idx] > 0)
            )
        }
    }

    /**
     * Berechnet die meistgehörten Playlists mit Hördauer, Track-Anzahl, Completion Rate und 1-Click Resume Bookmark.
     */
    fun calculateTopPlaylists(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList(),
        limit: Int = 5
    ): List<PlaylistStat> {
        val cleanHistory = history.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle) &&
            (it.contextUri?.startsWith("spotify:playlist:") == true ||
                (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && it.contextUri?.startsWith("spotify:album:") != true))
        }

        if (cleanHistory.isNotEmpty()) {
            val grouped = cleanHistory.groupBy { entry ->
                when {
                    !entry.contextUri.isNullOrBlank() && entry.contextUri.startsWith("spotify:playlist:") -> entry.contextUri
                    entry.contextTitle.isNotBlank() && entry.contextTitle != "Spotify" -> entry.contextTitle
                    else -> "Playlist"
                }
            }

            return grouped.mapNotNull { (key, entries) ->
                val latest = entries.maxByOrNull { it.timestamp } ?: entries.first()
                val playCount = entries.size
                val totalTime = entries.sumOf { it.listenedMs.coerceAtLeast(0L) }
                val title = entries.firstOrNull { it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && !isGenericQueueOrCorrupted(it.contextTitle) }?.contextTitle ?: "Playlist"
                val cover = entries.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl

                val completionList = entries.filter { it.durationMs > 0L }.map {
                    ((it.listenedMs.toFloat() / it.durationMs.toFloat()) * 100f).coerceIn(0f, 100f)
                }
                val avgCompletion = if (completionList.isNotEmpty()) completionList.average().toInt().coerceIn(0, 100) else 0

                val matchingBm = bookmarks.find { it.id == key || it.contextUri == key }
                    ?: latest.toBookmark()

                PlaylistStat(
                    contextUri = key,
                    title = title,
                    coverUrl = cover ?: matchingBm.coverUrl,
                    playCount = playCount,
                    totalTimeMs = totalTime,
                    formattedTotalTime = TimeFormatter.formatHoursAndMinutes(totalTime),
                    completionRate = avgCompletion,
                    bookmark = matchingBm
                )
            }
            .sortedWith(compareByDescending<PlaylistStat> { it.totalTimeMs }.thenByDescending { it.playCount })
            .take(limit)
        }

        val playlistBookmarks = bookmarks.filter {
            it.isPlaylist &&
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }

        if (playlistBookmarks.isEmpty()) return emptyList()

        return playlistBookmarks
            .sortedByDescending { it.positionMs }
            .take(limit)
            .map { bm ->
                val completion = if (bm.durationMs > 0L) {
                    ((bm.positionMs.toFloat() / bm.durationMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
                } else 0
                PlaylistStat(
                    contextUri = bm.contextUri ?: bm.id,
                    title = bm.displayContextTitle,
                    coverUrl = bm.coverUrl,
                    playCount = 1,
                    totalTimeMs = bm.positionMs,
                    formattedTotalTime = TimeFormatter.formatHoursAndMinutes(bm.positionMs),
                    completionRate = completion,
                    bookmark = bm
                )
            }
    }

    /**
     * Berechnet das Lieblings-Album (nach akkumulierter Hörzeit und Titelanzahl).
     */
    fun calculateTopAlbum(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): AlbumStat? {
        val cleanHistory = history.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.album) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }

        if (cleanHistory.isNotEmpty()) {
            val valid = cleanHistory.filter {
                (it.album.isNotBlank() && it.album != "Spotify") ||
                (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && it.contextUri?.startsWith("spotify:playlist:") != true)
            }.ifEmpty { cleanHistory }

            val grouped = valid.groupBy {
                when {
                    it.album.isNotBlank() && it.album != "Spotify" -> it.album
                    it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" -> it.contextTitle
                    else -> "Spotify Album"
                }
            }

            val topEntry = grouped.maxByOrNull { entry ->
                entry.value.sumOf { it.listenedMs.coerceAtLeast(0L) }
            } ?: return null

            val totalTime = topEntry.value.sumOf { it.listenedMs.coerceAtLeast(0L) }
            val topArtist = topEntry.value.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: "Spotify"
            val cover = topEntry.value.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl

            return AlbumStat(
                title = topEntry.key,
                artist = topArtist,
                coverUrl = cover,
                playCount = topEntry.value.size,
                totalTimeMs = totalTime,
                formattedTotalTime = TimeFormatter.formatHoursAndMinutes(totalTime)
            )
        }

        val cleanBookmarks = bookmarks.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }
        if (cleanBookmarks.isEmpty()) return null

        val albumBookmarks = cleanBookmarks.filter {
            it.contextUri?.startsWith("spotify:album:") == true ||
            it.id.startsWith("album:") ||
            (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && it.contextUri?.startsWith("spotify:playlist:") != true)
        }
        val candidates = if (albumBookmarks.isNotEmpty()) albumBookmarks else cleanBookmarks.filter { it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" }.ifEmpty { cleanBookmarks }

        val grouped = candidates.groupBy {
            if (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify") it.contextTitle
            else "Spotify Album"
        }

        val topEntry = grouped.maxByOrNull { entry ->
            entry.value.sumOf { it.positionMs.coerceAtLeast(0L) }
        } ?: return null

        val totalTime = topEntry.value.sumOf { it.positionMs.coerceAtLeast(0L) }
        val topArtist = topEntry.value.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: "Spotify"
        val cover = topEntry.value.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl

        return AlbumStat(
            title = topEntry.key,
            artist = topArtist,
            coverUrl = cover,
            playCount = topEntry.value.size,
            totalTimeMs = totalTime,
            formattedTotalTime = TimeFormatter.formatHoursAndMinutes(totalTime)
        )
    }

    /**
     * Berechnet die meistgehörten Songs mit Ranking, Spieldauer und 1-Click Resume Bookmark.
     */
    fun calculateTopSongs(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList(),
        limit: Int = 5
    ): List<TopTrackStat> {
        val cleanHistory = history.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }

        if (cleanHistory.isNotEmpty()) {
            val grouped = cleanHistory.groupBy {
                if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}"
            }

            return grouped.map { (_, entries) ->
                val latest = entries.maxByOrNull { it.timestamp } ?: entries.first()
                val playCount = entries.size
                val totalTimeMs = entries.sumOf { it.listenedMs.coerceAtLeast(0L) }
                val durationMs = latest.durationMs

                val matchingBm = bookmarks.find {
                    it.trackUri == latest.trackUri &&
                        (latest.contextUri.isNullOrBlank() || it.contextUri == latest.contextUri || it.id == latest.contextUri)
                } ?: bookmarks.find { it.trackUri == latest.trackUri }
                    ?: latest.toBookmark()

                TopTrackStat(
                    trackUri = latest.trackUri,
                    title = latest.trackTitle,
                    artist = latest.artist,
                    contextTitle = if (latest.contextTitle.isNotBlank() && latest.contextTitle != "Spotify" && !isGenericQueueOrCorrupted(latest.contextTitle)) latest.contextTitle else "Single",
                    coverUrl = latest.coverUrl ?: matchingBm.coverUrl,
                    playCount = playCount,
                    durationMs = durationMs,
                    totalTimeMs = totalTimeMs,
                    formattedDuration = TimeFormatter.formatTime(durationMs),
                    formattedTotalTime = TimeFormatter.formatHoursAndMinutes(totalTimeMs),
                    bookmark = matchingBm
                )
            }
            .sortedWith(compareByDescending<TopTrackStat> { it.totalTimeMs }.thenByDescending { it.playCount })
            .take(limit)
        }

        val cleanBookmarks = bookmarks.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }
        if (cleanBookmarks.isEmpty()) return emptyList()

        val grouped = cleanBookmarks.groupBy {
            if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}"
        }

        return grouped.map { (_, bms) ->
            val bestBm = bms.maxByOrNull { it.positionMs } ?: bms.first()
            val playCount = bms.size
            val totalTimeMs = bms.sumOf { it.positionMs.coerceAtLeast(0L) }
            val durationMs = bestBm.durationMs
            TopTrackStat(
                trackUri = bestBm.trackUri,
                title = bestBm.trackTitle,
                artist = bestBm.artist,
                contextTitle = if (bestBm.contextTitle.isNotBlank() && bestBm.contextTitle != "Spotify" && !isGenericQueueOrCorrupted(bestBm.contextTitle)) bestBm.contextTitle else "Single",
                coverUrl = bestBm.coverUrl,
                playCount = playCount,
                durationMs = durationMs,
                totalTimeMs = totalTimeMs,
                formattedDuration = TimeFormatter.formatTime(durationMs),
                formattedTotalTime = TimeFormatter.formatHoursAndMinutes(totalTimeMs),
                bookmark = bestBm
            )
        }
        .sortedWith(compareByDescending<TopTrackStat> { it.totalTimeMs }.thenByDescending { it.playCount })
        .take(limit)
    }

    /**
     * Erstellt eine chronologische Listening History mit relativen Zeitangaben ('vor X Min.', 'Gestern').
     */
    fun calculateHistory(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList(),
        limit: Int = 15,
        now: Long = System.currentTimeMillis()
    ): List<HistoryEntry> {
        val cleanHistory = history.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }

        if (cleanHistory.isNotEmpty()) {
            return cleanHistory
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { entry ->
                    val matchingBm = bookmarks.find {
                        it.trackUri == entry.trackUri &&
                            (entry.contextUri.isNullOrBlank() || it.contextUri == entry.contextUri || it.id == entry.contextUri)
                    } ?: entry.toBookmark()
                    HistoryEntry(
                        id = entry.id,
                        trackTitle = entry.trackTitle,
                        artist = entry.artist,
                        contextTitle = if (entry.contextTitle.isNotBlank() && entry.contextTitle != "Spotify" && !isGenericQueueOrCorrupted(entry.contextTitle)) entry.contextTitle else "Spotify",
                        durationMs = entry.durationMs,
                        formattedDuration = TimeFormatter.formatTime(entry.durationMs),
                        timestamp = entry.timestamp,
                        relativeTime = formatRelativeTime(entry.timestamp, now),
                        coverUrl = entry.coverUrl ?: matchingBm.coverUrl,
                        bookmark = matchingBm
                    )
                }
        }

        val cleanBookmarks = bookmarks.filter {
            !isPlaceholderTrack(it.trackUri, it.trackTitle) &&
            !isGenericQueueOrCorrupted(it.contextTitle)
        }

        return cleanBookmarks
            .sortedByDescending { it.timestamp }
            .take(limit)
            .map { bm ->
                HistoryEntry(
                    id = bm.id,
                    trackTitle = bm.trackTitle,
                    artist = bm.artist,
                    contextTitle = if (bm.contextTitle.isNotBlank() && bm.contextTitle != "Spotify" && !isGenericQueueOrCorrupted(bm.contextTitle)) bm.contextTitle else "Spotify",
                    durationMs = bm.durationMs,
                    formattedDuration = TimeFormatter.formatTime(bm.durationMs),
                    timestamp = bm.timestamp,
                    relativeTime = formatRelativeTime(bm.timestamp, now),
                    coverUrl = bm.coverUrl,
                    bookmark = bm
                )
            }
    }

    /**
     * Formatiert Zeitstempel in relative deutsche Zeitangaben nach Apple HIG Richtlinien.
     */
    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1L -> "Gerade eben"
            minutes < 60L -> "vor $minutes Min."
            hours < 24L -> "vor $hours Std."
            days == 1L -> "Gestern"
            else -> "vor $days Tagen"
        }
    }

    /**
     * Berechnet die Aktivitätsverteilung über die 7 Wochentage (Mo-So).
     * Keine künstlich fabrizierten Minuten; exakte 0f Baseline bei inaktiven Tagen.
     */
    fun calculateWeeklyActivity(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): List<DayActivity> {
        val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
        val minutesPerDay = IntArray(7)

        val cal = Calendar.getInstance(Locale.GERMANY)

        val cleanHistory = history.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }
        val cleanBookmarks = bookmarks.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }

        if (cleanHistory.isNotEmpty()) {
            for (h in cleanHistory) {
                cal.timeInMillis = h.timestamp
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val index = when (dayOfWeek) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    Calendar.SUNDAY -> 6
                    else -> 0
                }
                val mins = (h.listenedMs / 60000L).toInt().coerceAtLeast(0)
                minutesPerDay[index] += mins
            }
        } else {
            for (b in cleanBookmarks) {
                cal.timeInMillis = b.timestamp
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val index = when (dayOfWeek) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    Calendar.SUNDAY -> 6
                    else -> 0
                }
                val mins = (b.positionMs / 60000L).toInt().coerceAtLeast(0)
                minutesPerDay[index] += mins
            }
        }

        val maxMinutes = (minutesPerDay.maxOrNull() ?: 0).coerceAtLeast(0)

        return days.mapIndexed { idx, name ->
            val m = minutesPerDay[idx]
            val fraction = if (maxMinutes > 0 && m > 0) {
                (m.toFloat() / maxMinutes.toFloat()).coerceIn(0.12f, 1f)
            } else {
                0f
            }
            DayActivity(
                dayName = name,
                minutes = m,
                fraction = fraction
            )
        }
    }

    /**
     * Berechnet den Hör-Typ (Persona) und die aktivste Zeitspanne.
     */
    fun calculatePersonaAndHours(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): Pair<String, String> {
        val cleanHistory = history.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }
        val cleanBookmarks = bookmarks.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }

        val sourceTimestamps = if (cleanHistory.isNotEmpty()) {
            cleanHistory.map { it.timestamp }
        } else {
            cleanBookmarks.map { it.timestamp }
        }

        if (sourceTimestamps.isEmpty()) {
            return Pair("Neugieriger Hörer 🎵", "Noch keine Zeiten erfasst")
        }

        val hourCounts = IntArray(24)
        val cal = Calendar.getInstance(Locale.GERMANY)
        for (ts in sourceTimestamps) {
            cal.timeInMillis = ts
            val h = cal.get(Calendar.HOUR_OF_DAY)
            hourCounts[h]++
        }

        var peakHour = 12
        var maxCount = -1
        for (h in 0 until 24) {
            if (hourCounts[h] > maxCount) {
                maxCount = hourCounts[h]
                peakHour = h
            }
        }

        val persona = when (peakHour) {
            in 22..23, in 0..4 -> "Nachteule 🦉"
            in 5..10 -> "Morgenmensch 🌅"
            in 11..16 -> "Fokus-Nachmittag ⚡️"
            else -> "Abend-Chiller 🌙"
        }

        val endHour = (peakHour + 2) % 24
        val rangeStr = String.format(Locale.GERMANY, "%02d:00 – %02d:00 Uhr", peakHour, endHour)
        return Pair(persona, rangeStr)
    }

    /**
     * Berechnet die durchschnittliche Durchhör-Quote (Completion Rate).
     */
    fun calculateCompletionRate(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): Pair<Int, String> {
        val cleanHistory = history.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }
        val cleanBookmarks = bookmarks.filter { !isPlaceholderTrack(it.trackUri, it.trackTitle) }

        val pairs = if (cleanHistory.isNotEmpty()) {
            cleanHistory.filter { it.durationMs > 0L }.map { it.listenedMs to it.durationMs }
        } else {
            cleanBookmarks.filter { it.durationMs > 0L }.map { it.positionMs to it.durationMs }
        }

        if (pairs.isEmpty()) return Pair(0, "Keine Daten 🎧")

        val sumPercentages = pairs.map { (pos, dur) ->
            ((pos.toFloat() / dur.toFloat()) * 100f).coerceIn(0f, 100f)
        }.sum()
        val avg = (sumPercentages / pairs.size).toInt().coerceIn(0, 100)

        val label = when {
            avg >= 65 -> "Fokus-Durchhörer 🎧"
            avg >= 35 -> "Playlist-Explorer 🚀"
            else -> "Track-Skipper ⚡️"
        }
        return Pair(avg, label)
    }
}
