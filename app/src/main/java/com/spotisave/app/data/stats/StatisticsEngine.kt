package com.spotisave.app.data.stats

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.ui.util.TimeFormatter
import java.util.Calendar
import java.util.Locale

data class AlbumStat(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val playCount: Int,
    val totalTimeMs: Long,
    val formattedTotalTime: String
)

data class TopTrackStat(
    val trackUri: String,
    val title: String,
    val artist: String,
    val contextTitle: String,
    val coverUrl: String?,
    val playCount: Int,
    val durationMs: Long,
    val totalTimeMs: Long,
    val formattedDuration: String,
    val formattedTotalTime: String,
    val bookmark: Bookmark
)

data class HistoryEntry(
    val id: String,
    val trackTitle: String,
    val artist: String,
    val contextTitle: String,
    val durationMs: Long,
    val formattedDuration: String,
    val timestamp: Long,
    val relativeTime: String,
    val coverUrl: String?,
    val bookmark: Bookmark
)

data class ArtistStat(
    val name: String,
    val count: Int,
    val percentage: Float // 0.0f .. 1.0f
)

data class DayActivity(
    val dayName: String, // "Mo", "Di", "Mi", ...
    val minutes: Int,
    val fraction: Float  // 0.0f .. 1.0f relative to peak day
)

data class ListeningStats(
    val totalListeningTimeMs: Long,
    val formattedTotalTime: String,
    val totalTrackedPlaylists: Int,
    val totalBookmarksCount: Int,
    val totalTracksCount: Int,
    val averageTrackDurationMs: Long,
    val formattedAvgTrackDuration: String,
    val topBookmark: Bookmark?,
    val topPlaylistTitle: String?,
    val topPlaylistCover: String?,
    val topPlaylistArtist: String?,
    val topPlaylistTrackUri: String?,
    val topPlaylistContextUri: String?,
    val topPlaylistProgressMs: Long,
    val topPlaylistDurationMs: Long,
    val topAlbum: AlbumStat?,
    val topSongs: List<TopTrackStat>,
    val history: List<HistoryEntry>,
    val topArtists: List<ArtistStat>,
    val weeklyActivity: List<DayActivity>,
    val peakDayName: String,
    val peakDayMinutes: Int,
    val listeningPersona: String,
    val peakHourRange: String,
    val completionRatePercent: Int,
    val completionRateLabel: String
)

object StatisticsEngine {

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
     * vollumfänglich ausgewertet anstatt nur die ~2 aktiven Playlist-Lesezeichen.
     */
    fun calculateStats(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry>,
        activePlayback: PlaybackStateModel? = null,
        spotifyTopArtists: List<ArtistStat> = emptyList()
    ): ListeningStats {
        val hasHistory = history.isNotEmpty()

        // 1. Gesamte Hörzeit akkumulieren
        var totalMs = 0L
        if (hasHistory) {
            totalMs = history.sumOf { it.listenedMs.coerceAtLeast(0L) }
            if (activePlayback != null && activePlayback.isPlaying) {
                val latestHistory = history.maxByOrNull { it.timestamp }
                val isMatchingLatest = latestHistory != null &&
                    latestHistory.trackUri.isNotBlank() &&
                    latestHistory.trackUri == activePlayback.trackUri &&
                    (System.currentTimeMillis() - latestHistory.timestamp < 15_000L)

                if (isMatchingLatest) {
                    val additional = (activePlayback.positionMs - latestHistory!!.listenedMs).coerceAtLeast(0L)
                    totalMs += additional
                } else {
                    totalMs += activePlayback.positionMs.coerceAtLeast(0L)
                }
            }
        } else {
            val matchingActiveBm = if (activePlayback != null && activePlayback.isPlaying) {
                bookmarks.find {
                    (!it.trackUri.isNullOrBlank() && it.trackUri == activePlayback.trackUri) ||
                    (!it.contextUri.isNullOrBlank() && it.contextUri == activePlayback.contextUri)
                }
            } else null

            for (b in bookmarks) {
                if (b == matchingActiveBm) {
                    totalMs += maxOf(b.positionMs, activePlayback?.positionMs ?: 0L).coerceAtLeast(0L)
                } else {
                    totalMs += b.positionMs.coerceAtLeast(0L)
                }
            }

            if (activePlayback != null && activePlayback.isPlaying && matchingActiveBm == null) {
                totalMs += activePlayback.positionMs.coerceAtLeast(0L)
            }
        }

        val formattedTime = TimeFormatter.formatHoursAndMinutes(totalMs)

        // 2. Anzahl verfolgter Playlists & Alben
        val uniquePlaylists = if (hasHistory) {
            (history.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() } ?: it.contextTitle.takeIf { t -> t != "Spotify" }
            } + bookmarks.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() } ?: it.contextTitle.takeIf { t -> t != "Spotify" }
            }).toSet()
        } else {
            bookmarks.mapNotNull {
                it.contextUri?.takeIf { uri -> uri.isNotBlank() } ?: it.contextTitle.takeIf { t -> t != "Spotify" }
            }.toSet()
        }
        val totalPlaylistsCount = uniquePlaylists.size

        // Track-Anzahl & Durchschnittliche Track-Dauer
        val uniqueTracks = if (hasHistory) {
            (history.map { if (it.trackUri.isNotBlank()) it.trackUri else it.trackTitle } +
             bookmarks.map { if (it.trackUri.isNotBlank()) it.trackUri else it.trackTitle }).toSet()
        } else {
            bookmarks.map { if (it.trackUri.isNotBlank()) it.trackUri else it.trackTitle }.toSet()
        }
        val totalTracksCount = uniqueTracks.size

        val validDurations = if (hasHistory) {
            (history.map { it.durationMs } + bookmarks.map { it.durationMs }).filter { it > 0L }
        } else {
            bookmarks.filter { it.durationMs > 0L }.map { it.durationMs }
        }
        val avgTrackDurationMs = if (validDurations.isNotEmpty()) validDurations.average().toLong() else 0L
        val formattedAvgDuration = if (avgTrackDurationMs > 0L) TimeFormatter.formatTime(avgTrackDurationMs) else "0:00"

        // 3. Top-Playlist & Bookmark (prominentes Fortsetzen)
        val topBm = bookmarks.maxByOrNull { it.positionMs }
            ?: history.firstOrNull()?.toBookmark()
            ?: bookmarks.firstOrNull()
        val topTitle = topBm?.contextTitle?.takeIf { it != "Spotify" } ?: topBm?.trackTitle
        val topCover = topBm?.coverUrl
        val topArtist = topBm?.artist
        val topTrackUri = topBm?.trackUri
        val topContextUri = topBm?.contextUri
        val topProgressMs = topBm?.positionMs ?: 0L
        val topDurationMs = topBm?.durationMs ?: 0L

        // 4. Lieblings-Album (Top / most listened album)
        val topAlbum = calculateTopAlbum(bookmarks, history)

        // 5. Meistgehörte Songs / Top-Songs Ranking (mit 1-Click Resume)
        val topSongs = calculateTopSongs(bookmarks, history)

        // 6. Genaue Historie (Chronologische Listening History mit 'vor X Min.')
        val historyList = calculateHistory(bookmarks, history)

        // 7. Top-Interpreten Ranking (mit Splitting bei Kollaborationen/Duos)
        val artistCounts = mutableMapOf<String, Int>()
        val artistSource = if (hasHistory) {
            history.map { it.artist }
        } else {
            bookmarks.map { it.artist }
        }
        for (artistString in artistSource) {
            val raw = artistString.trim()
            if (raw.isNotBlank() && raw != "Unbekannter Interpret") {
                val splitNames = raw.split(Regex("[,&]|\\sfeat\\.\\s|\\sft\\.\\s")).map { it.trim() }.filter { it.isNotBlank() }
                for (a in splitNames) {
                    artistCounts[a] = (artistCounts[a] ?: 0) + 1
                }
            }
        }

        // Falls Spotify Top-Artists übergeben wurden, diese bei Bedarf einbeziehen/bevorzugen
        val sortedArtists = if (spotifyTopArtists.isNotEmpty()) {
            spotifyTopArtists.take(5)
        } else {
            val totalArtistMentions = artistCounts.values.sum().coerceAtLeast(1)
            artistCounts.entries
                .sortedByDescending { it.value }
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
        val weeklyActivity = calculateWeeklyActivity(bookmarks, history)
        val peakDayIdx = weeklyActivity.indices.maxByOrNull { weeklyActivity[it].minutes } ?: 0
        val peakDay = weeklyActivity[peakDayIdx]
        val peakDayName = if (peakDay.minutes > 0) peakDay.dayName else "Noch offen"
        val peakDayMins = peakDay.minutes

        // 9. Hör-Persönlichkeit (Persona) & Primäre Tageszeit
        val (listeningPersona, peakHourRange) = calculatePersonaAndHours(bookmarks, history)

        // 10. Durchhör-Quote (Completion Rate)
        val (completionRatePercent, completionRateLabel) = calculateCompletionRate(bookmarks, history)

        return ListeningStats(
            totalListeningTimeMs = totalMs,
            formattedTotalTime = formattedTime,
            totalTrackedPlaylists = totalPlaylistsCount,
            totalBookmarksCount = if (hasHistory) history.size else bookmarks.size,
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
            completionRateLabel = completionRateLabel
        )
    }

    /**
     * Berechnet das Lieblings-Album (nach akkumulierter Hörzeit und Titelanzahl).
     */
    fun calculateTopAlbum(
        bookmarks: List<Bookmark>,
        history: List<HistoryTrackEntry> = emptyList()
    ): AlbumStat? {
        if (history.isNotEmpty()) {
            val valid = history.filter {
                (it.album.isNotBlank() && it.album != "Spotify") ||
                (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && it.contextUri?.startsWith("spotify:playlist:") != true)
            }.ifEmpty { history }

            val grouped = valid.groupBy {
                when {
                    it.album.isNotBlank() && it.album != "Spotify" -> it.album
                    it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" -> it.contextTitle
                    else -> "Spotify Album"
                }
            }

            val topEntry = grouped.maxByOrNull { entry ->
                entry.value.sumOf { it.listenedMs }
            } ?: return null

            val totalTime = topEntry.value.sumOf { it.listenedMs }
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

        if (bookmarks.isEmpty()) return null

        val albumBookmarks = bookmarks.filter {
            it.contextUri?.startsWith("spotify:album:") == true ||
            it.id.startsWith("album:") ||
            (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" && it.contextUri?.startsWith("spotify:playlist:") != true)
        }
        val candidates = if (albumBookmarks.isNotEmpty()) albumBookmarks else bookmarks.filter { it.contextTitle.isNotBlank() && it.contextTitle != "Spotify" }.ifEmpty { bookmarks }

        val grouped = candidates.groupBy {
            if (it.contextTitle.isNotBlank() && it.contextTitle != "Spotify") it.contextTitle
            else "Spotify Album"
        }

        val topEntry = grouped.maxByOrNull { entry ->
            entry.value.sumOf { it.positionMs }
        } ?: return null

        val totalTime = topEntry.value.sumOf { it.positionMs }
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
        if (history.isNotEmpty()) {
            val grouped = history.groupBy {
                if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}"
            }

            return grouped.map { (_, entries) ->
                val latest = entries.maxByOrNull { it.timestamp } ?: entries.first()
                val playCount = entries.size
                val totalTimeMs = entries.sumOf { it.listenedMs }
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
                    contextTitle = if (latest.contextTitle.isNotBlank() && latest.contextTitle != "Spotify") latest.contextTitle else "Single",
                    coverUrl = latest.coverUrl,
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

        if (bookmarks.isEmpty()) return emptyList()

        val grouped = bookmarks.groupBy {
            if (it.trackUri.isNotBlank()) it.trackUri else "${it.trackTitle} - ${it.artist}"
        }

        return grouped.map { (_, bms) ->
            val bestBm = bms.maxByOrNull { it.positionMs } ?: bms.first()
            val playCount = bms.size
            val totalTimeMs = bms.sumOf { it.positionMs }
            val durationMs = bestBm.durationMs
            TopTrackStat(
                trackUri = bestBm.trackUri,
                title = bestBm.trackTitle,
                artist = bestBm.artist,
                contextTitle = if (bestBm.contextTitle.isNotBlank() && bestBm.contextTitle != "Spotify") bestBm.contextTitle else "Single",
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
        if (history.isNotEmpty()) {
            return history
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
                        contextTitle = if (entry.contextTitle.isNotBlank() && entry.contextTitle != "Spotify") entry.contextTitle else "Spotify",
                        durationMs = entry.durationMs,
                        formattedDuration = TimeFormatter.formatTime(entry.durationMs),
                        timestamp = entry.timestamp,
                        relativeTime = formatRelativeTime(entry.timestamp, now),
                        coverUrl = entry.coverUrl,
                        bookmark = matchingBm
                    )
                }
        }

        return bookmarks
            .sortedByDescending { it.timestamp }
            .take(limit)
            .map { bm ->
                HistoryEntry(
                    id = bm.id,
                    trackTitle = bm.trackTitle,
                    artist = bm.artist,
                    contextTitle = if (bm.contextTitle.isNotBlank() && bm.contextTitle != "Spotify") bm.contextTitle else "Spotify",
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

        if (history.isNotEmpty()) {
            for (h in history) {
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
                val mins = (h.listenedMs / 60000L).toInt()
                minutesPerDay[index] += mins
            }
        } else {
            for (b in bookmarks) {
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
                val mins = (b.positionMs / 60000L).toInt()
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
        val sourceTimestamps = if (history.isNotEmpty()) {
            history.map { it.timestamp }
        } else {
            bookmarks.map { it.timestamp }
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
        val pairs = if (history.isNotEmpty()) {
            history.filter { it.durationMs > 0L }.map { it.listenedMs to it.durationMs }
        } else {
            bookmarks.filter { it.durationMs > 0L }.map { it.positionMs to it.durationMs }
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
