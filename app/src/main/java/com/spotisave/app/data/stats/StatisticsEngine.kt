package com.spotisave.app.data.stats

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.ui.util.TimeFormatter
import java.util.Calendar
import java.util.Locale

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
    val topBookmark: Bookmark?,
    val topPlaylistTitle: String?,
    val topPlaylistCover: String?,
    val topPlaylistArtist: String?,
    val topPlaylistTrackUri: String?,
    val topPlaylistContextUri: String?,
    val topPlaylistProgressMs: Long,
    val topPlaylistDurationMs: Long,
    val topArtists: List<ArtistStat>,
    val weeklyActivity: List<DayActivity>,
    val peakDayName: String,
    val peakDayMinutes: Int,
    val listeningPersona: String,
    val peakHourRange: String,
    val completionRatePercent: Int,
    val completionRateLabel: String,
    val boostScore: Int,
    val boostLevel: String,
    val boostMessage: String,
    val currentRoast: String,
    val allRoasts: List<String>
)

object StatisticsEngine {

    /**
     * Berechnet alle Statistiken, Top-Interpreten, Wochentagsverteilung,
     * den energetischen /boost Score, Hör-Persönlichkeit, Durchhör-Quote und /grill-me Roasts.
     */
    fun calculateStats(
        bookmarks: List<Bookmark>,
        activePlayback: PlaybackStateModel? = null,
        roastIndex: Int = 0
    ): ListeningStats {
        // 1. Gesamte Hörzeit akkumulieren ohne Doppelzählung des aktiven Playbacks
        var totalMs = 0L
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

        val formattedTime = TimeFormatter.formatHoursAndMinutes(totalMs)

        // 2. Anzahl verfolgter Playlists & Alben
        val uniquePlaylists = bookmarks.mapNotNull {
            it.contextUri?.takeIf { uri -> uri.isNotBlank() } ?: it.contextTitle.takeIf { t -> t != "Spotify" }
        }.toSet()
        val totalPlaylistsCount = uniquePlaylists.size

        // 3. Top-Playlist & Bookmark (nach höchstem Fortschritt)
        val topBm = bookmarks.maxByOrNull { it.positionMs } ?: bookmarks.firstOrNull()
        val topTitle = topBm?.contextTitle?.takeIf { it != "Spotify" } ?: topBm?.trackTitle
        val topCover = topBm?.coverUrl
        val topArtist = topBm?.artist
        val topTrackUri = topBm?.trackUri
        val topContextUri = topBm?.contextUri
        val topProgressMs = topBm?.positionMs ?: 0L
        val topDurationMs = topBm?.durationMs ?: 0L

        // 4. Top-Interpreten Ranking (mit Splitting bei Kollaborationen/Duos)
        val artistCounts = mutableMapOf<String, Int>()
        for (b in bookmarks) {
            val raw = b.artist.trim()
            if (raw.isNotBlank() && raw != "Unbekannter Interpret") {
                val splitNames = raw.split(Regex("[,&]|\\sfeat\\.\\s|\\sft\\.\\s")).map { it.trim() }.filter { it.isNotBlank() }
                for (a in splitNames) {
                    artistCounts[a] = (artistCounts[a] ?: 0) + 1
                }
            }
        }
        val totalArtistMentions = artistCounts.values.sum().coerceAtLeast(1)
        val sortedArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (name, count) ->
                ArtistStat(
                    name = name,
                    count = count,
                    percentage = (count.toFloat() / totalArtistMentions.toFloat()).coerceIn(0f, 1f)
                )
            }

        // 5. Wöchentliche Aktivität (Mo bis So) & Aktivster Tag
        val weeklyActivity = calculateWeeklyActivity(bookmarks)
        val peakDayIdx = weeklyActivity.indices.maxByOrNull { weeklyActivity[it].minutes } ?: 0
        val peakDay = weeklyActivity[peakDayIdx]
        val peakDayName = if (peakDay.minutes > 0) peakDay.dayName else "Noch offen"
        val peakDayMins = peakDay.minutes

        // 6. Hör-Persönlichkeit (Persona) & Primäre Tageszeit
        val (listeningPersona, peakHourRange) = calculatePersonaAndHours(bookmarks)

        // 7. Durchhör-Quote (Completion Rate)
        val (completionRatePercent, completionRateLabel) = calculateCompletionRate(bookmarks)

        // 8. /boost Berechnung (Energie, Fokus & Flow)
        val (boostScore, boostLevel, boostMsg) = calculateBoost(bookmarks, totalMs)

        // 9. /grill-me Roasts
        val allRoasts = generateRoasts(
            bookmarks = bookmarks,
            totalMs = totalMs,
            topArtist = sortedArtists.firstOrNull()?.name,
            topPlaylist = topTitle,
            completionRate = completionRatePercent,
            persona = listeningPersona
        )
        val safeIndex = if (allRoasts.isNotEmpty()) (roastIndex % allRoasts.size + allRoasts.size) % allRoasts.size else 0
        val currentRoast = allRoasts.getOrElse(safeIndex) {
            "Deine Hörgewohnheiten sind so unauffällig, dass selbst unser Algorithmus gähnt. 😴"
        }

        return ListeningStats(
            totalListeningTimeMs = totalMs,
            formattedTotalTime = formattedTime,
            totalTrackedPlaylists = totalPlaylistsCount,
            totalBookmarksCount = bookmarks.size,
            topBookmark = topBm,
            topPlaylistTitle = topTitle,
            topPlaylistCover = topCover,
            topPlaylistArtist = topArtist,
            topPlaylistTrackUri = topTrackUri,
            topPlaylistContextUri = topContextUri,
            topPlaylistProgressMs = topProgressMs,
            topPlaylistDurationMs = topDurationMs,
            topArtists = sortedArtists,
            weeklyActivity = weeklyActivity,
            peakDayName = peakDayName,
            peakDayMinutes = peakDayMins,
            listeningPersona = listeningPersona,
            peakHourRange = peakHourRange,
            completionRatePercent = completionRatePercent,
            completionRateLabel = completionRateLabel,
            boostScore = boostScore,
            boostLevel = boostLevel,
            boostMessage = boostMsg,
            currentRoast = currentRoast,
            allRoasts = allRoasts
        )
    }

    /**
     * Berechnet die Aktivitätsverteilung über die 7 Wochentage (Mo-So).
     * Keine künstlich fabrizierten Minuten; exakte 0f Baseline bei inaktiven Tagen.
     */
    fun calculateWeeklyActivity(bookmarks: List<Bookmark>): List<DayActivity> {
        val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
        val minutesPerDay = IntArray(7)

        val cal = Calendar.getInstance(Locale.GERMANY)

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
    fun calculatePersonaAndHours(bookmarks: List<Bookmark>): Pair<String, String> {
        if (bookmarks.isEmpty()) {
            return Pair("Neugieriger Hörer 🎵", "Noch keine Zeiten erfasst")
        }
        val hourCounts = IntArray(24)
        val cal = Calendar.getInstance(Locale.GERMANY)
        for (b in bookmarks) {
            cal.timeInMillis = b.timestamp
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
    fun calculateCompletionRate(bookmarks: List<Bookmark>): Pair<Int, String> {
        val valid = bookmarks.filter { it.durationMs > 0L }
        if (valid.isEmpty()) return Pair(0, "Keine Daten 🎧")

        val sumPercentages = valid.map {
            ((it.positionMs.toFloat() / it.durationMs.toFloat()) * 100f).coerceIn(0f, 100f)
        }.sum()
        val avg = (sumPercentages / valid.size).toInt().coerceIn(0, 100)

        val label = when {
            avg >= 65 -> "Fokus-Durchhörer 🎧"
            avg >= 35 -> "Playlist-Explorer 🚀"
            else -> "Track-Skipper ⚡️"
        }
        return Pair(avg, label)
    }

    /**
     * Berechnet den /boost Flow-Score (65% bis 99%) und motivierende Highlights.
     */
    fun calculateBoost(bookmarks: List<Bookmark>, totalMs: Long): Triple<Int, String, String> {
        if (bookmarks.isEmpty()) {
            return Triple(
                72,
                "⚡️ Warm-up Phase",
                "Starte Spotify und baue dein Momentum auf! Jeder Track steigert deinen Fokus."
            )
        }

        // Berechne Score anhand von Vielfalt und Hörzeit
        val baseScore = 75
        val timeBonus = ((totalMs / 1800000L) * 3).toInt().coerceAtMost(15) // +3 pro 30 Min
        val countBonus = (bookmarks.size * 2).coerceAtMost(10)
        val score = (baseScore + timeBonus + countBonus).coerceIn(68, 99)

        val (level, message) = when {
            score >= 90 -> Pair(
                "⚡️ Peak Flow - Höchste Energie",
                "Deine Playlists halten dich im ultimativen Flow! Der Rhythmus synchronisiert perfekt mit deiner Spitzenleistung."
            )
            score >= 80 -> Pair(
                "🚀 Turbo Focus - Volle Produktivität",
                "Hervorragender Fokus! Deine Musik minimiert Ablenkungen und hält deinen Antrieb auf Maximalstufe."
            )
            score >= 70 -> Pair(
                "🔥 Deep Zone - Stetiger Drive",
                "Gleichmäßiger Beat und stabiles Tempo. Du bist eingegroovt und machst kontinuierlich Fortschritte."
            )
            else -> Pair(
                "🎧 Good Vibes - Soundcheck",
                "Gute Basis! Lass die Tracks weiterlaufen, um in die High-Energy Zone zu beschleunigen."
            )
        }

        return Triple(score, level, message)
    }

    /**
     * Generiert witzige, treffsichere /grill-me Roasts basierend auf den Hörerdaten.
     */
    fun generateRoasts(
        bookmarks: List<Bookmark>,
        totalMs: Long,
        topArtist: String?,
        topPlaylist: String?,
        completionRate: Int = 50,
        persona: String? = null
    ): List<String> {
        val roasts = mutableListOf<String>()

        if (bookmarks.isEmpty()) {
            roasts.add("Noch keine Hörpositionen gespeichert? Dein Spotify ist so leer wie der Kühlschrank eines Studenten am Monatsende. 🌭")
            roasts.add("Hast du Angst vor Musik oder weißt du einfach nicht, wie man 'Play' drückt? Wir warten... 👀")
            return roasts
        }

        if (!topArtist.isNullOrBlank()) {
            roasts.add("Du hast auffällig viele Titel von '$topArtist' gespeichert. Weiß '$topArtist' eigentlich schon von seiner neuen unbezahlten Vollzeit-Begleitung? 💀")
            roasts.add("Dein Top-Interpret ist '$topArtist'. Suchst du Musik oder eine emotionale Ersatztherapie auf Krankenkassenkosten? 🛋️")
        }

        if (!topPlaylist.isNullOrBlank()) {
            roasts.add("Du klammerst dich an '$topPlaylist' wie an eine Decke beim Horrorfilm. Hast du Angst vor neuen Songs oder ist das dein Überlebens-Soundtrack? 🔥")
        }

        val totalHours = totalMs / 3600000L
        if (totalHours >= 2L) {
            roasts.add("Über $totalHours Stunden reine Lesezeichen-Hörzeit! Deine In-Ears sind wahrscheinlich schon anatomisch mit deinem Trommelfell verschmolzen. 🎧")
        }

        if (bookmarks.size in 1..2) {
            roasts.add("Ganze ${bookmarks.size} gespeicherte Hörpositionen... Dein Musikhorizont hat in etwa den Bewegungsradius eines Goldfischs im Einmachglas. 🐟")
        } else if (bookmarks.size >= 8) {
            roasts.add("Du hast ${bookmarks.size} verschiedene Playlists angefangen und keine beendet. Musikalische Bindungsängste im Endstadium! 🏃‍♂️")
        }

        if (completionRate < 35) {
            roasts.add("Deine Durchhör-Quote liegt bei mickrigen $completionRate%. Du hast die musikalische Aufmerksamkeitsspanne einer Eintagsfliege auf Red Bull! ⚡️")
        }

        if (persona != null && persona.contains("Nachteule")) {
            roasts.add("Du pumpst mitten in der Nacht Musik in deine Ohren. Arbeitest du an einem geheimen Startup oder versuchst du nur, deine Schlaflosigkeit zu vertonen? 🦉")
        }

        // Kreative allgemeine Roasts
        roasts.add("Du pausierst Songs mitten im Drop und wunderst dich, warum dein Tag chaotisch verläuft. ⚡️")
        roasts.add("Deine Playlist heißt wahrscheinlich 'Fokus', aber nach 45 Sekunden wechselst du dreimal den Track. Wir sehen alles. 👀")
        roasts.add("Dieser Mix aus Genres schreit förmlich nach innerem Konflikt. Ein DJ würde bei diesem Übergang weinend den Raum verlassen. 💀")

        return roasts
    }
}
