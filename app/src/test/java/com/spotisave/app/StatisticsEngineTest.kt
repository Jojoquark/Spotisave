package com.spotisave.app

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.stats.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class StatisticsEngineTest {

    private fun createSampleBookmarks(): List<Bookmark> {
        val now = System.currentTimeMillis()
        return listOf(
            Bookmark(
                id = "bm1",
                contextUri = "spotify:playlist:lofi_beats",
                contextTitle = "Lo-Fi Focus",
                trackTitle = "Coffee Morning",
                artist = "ChillHop Beats",
                trackUri = "spotify:track:111",
                positionMs = 120_000L, // 2 min
                durationMs = 180_000L,
                timestamp = now - 300_000L // vor 5 Min.
            ),
            Bookmark(
                id = "bm2",
                contextUri = "spotify:playlist:lofi_beats",
                contextTitle = "Lo-Fi Focus",
                trackTitle = "Rainy Afternoon",
                artist = "ChillHop Beats",
                trackUri = "spotify:track:222",
                positionMs = 90_000L, // 1.5 min
                durationMs = 150_000L,
                timestamp = now - 3_600_000L // vor 1 Std.
            ),
            Bookmark(
                id = "bm3",
                contextUri = "spotify:playlist:synthwave_night",
                contextTitle = "Synthwave Run",
                trackTitle = "Neon Drive",
                artist = "Kavinsky",
                trackUri = "spotify:track:333",
                positionMs = 240_000L, // 4 min
                durationMs = 280_000L,
                timestamp = now - 86_400_000L // Gestern
            ),
            Bookmark(
                id = "bm4",
                contextUri = "spotify:album:daft_punk_discovery",
                contextTitle = "Discovery",
                trackTitle = "Harder, Better, Faster, Stronger",
                artist = "Daft Punk",
                trackUri = "spotify:track:444",
                positionMs = 220_000L, // 3.66 min
                durationMs = 224_000L,
                timestamp = now - 172_800_000L // vor 2 Tagen
            )
        )
    }

    @Test
    fun testTotalListeningTimeAndFormatting() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // 120k + 90k + 240k + 220k = 670k ms = 11.16 min
        assertEquals(670_000L, stats.totalListeningTimeMs)
        assertEquals("11 Min.", stats.formattedTotalTime)

        // Mit aktivem Playback (neuer Track nicht in Bookmarks)
        val active = PlaybackStateModel(
            trackUri = "spotify:track:live_new",
            trackTitle = "Active Live Song",
            artist = "Daft Punk",
            positionMs = 30_000L,
            durationMs = 200_000L,
            isPlaying = true
        )
        val statsWithActive = StatisticsEngine.calculateStats(bookmarks, active)
        assertEquals(700_000L, statsWithActive.totalListeningTimeMs)
    }

    @Test
    fun testDoubleCountingPreventionWithActivePlayback() {
        val bookmarks = createSampleBookmarks()
        // Aktives Playback spielt denselben Track wie bm1 (spotify:track:111), aber weiter fortgeschritten (135s statt 120s)
        val activePlayingSameTrack = PlaybackStateModel(
            trackUri = "spotify:track:111",
            trackTitle = "Coffee Morning",
            artist = "ChillHop Beats",
            contextUri = "spotify:playlist:lofi_beats",
            positionMs = 135_000L,
            durationMs = 180_000L,
            isPlaying = true
        )

        val stats = StatisticsEngine.calculateStats(bookmarks, activePlayingSameTrack)
        // Sollte bm1 auf 135k aktualisieren: 135k + 90k + 240k + 220k = 685k ms (NICHT 670k + 135k = 805k)
        assertEquals(685_000L, stats.totalListeningTimeMs)
    }

    @Test
    fun testTotalTrackedPlaylistsCount() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // 3 unique contexts: lofi_beats, synthwave_night, daft_punk_discovery
        assertEquals(3, stats.totalTrackedPlaylists)
        assertEquals(4, stats.totalBookmarksCount)
        assertEquals(4, stats.totalTracksCount)
    }

    @Test
    fun testAverageTrackDurationAndTotalTracks() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // (180_000 + 150_000 + 280_000 + 224_000) / 4 = 834_000 / 4 = 208_500 ms = 3:28 min
        assertEquals(4, stats.totalTracksCount)
        assertEquals(208_500L, stats.averageTrackDurationMs)
        assertEquals("3:28", stats.formattedAvgTrackDuration)
    }

    @Test
    fun testTopPlaylistDetectionAndBookmarkPreservation() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // bm3 has highest positionMs (240_000L) -> "Synthwave Run"
        assertEquals("Synthwave Run", stats.topPlaylistTitle)
        assertEquals("Kavinsky", stats.topPlaylistArtist)
        assertEquals(240_000L, stats.topPlaylistProgressMs)

        // Top Bookmark must be preserved intact without data loss
        assertNotNull(stats.topBookmark)
        assertEquals("bm3", stats.topBookmark?.id)
        assertEquals("Neon Drive", stats.topBookmark?.trackTitle)
        assertEquals("spotify:track:333", stats.topBookmark?.trackUri)
    }

    @Test
    fun testTopAlbumCalculation() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // bm4 is in spotify:album:daft_punk_discovery with title "Discovery"
        assertNotNull(stats.topAlbum)
        assertEquals("Discovery", stats.topAlbum?.title)
        assertEquals("Daft Punk", stats.topAlbum?.artist)
        assertEquals(220_000L, stats.topAlbum?.totalTimeMs)
        assertEquals(1, stats.topAlbum?.playCount)
        assertTrue(stats.topAlbum?.formattedTotalTime?.contains("Min.") == true)

        // Empty state check
        val emptyStats = StatisticsEngine.calculateStats(emptyList())
        assertNull(emptyStats.topAlbum)
    }

    @Test
    fun testTopSongsCalculationAndRanking() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        assertEquals(4, stats.topSongs.size)

        // Rank #1: highest totalTimeMs is bm3 ("Neon Drive", 240_000 ms)
        val rank1 = stats.topSongs[0]
        assertEquals("Neon Drive", rank1.title)
        assertEquals("Kavinsky", rank1.artist)
        assertEquals(240_000L, rank1.totalTimeMs)
        assertEquals(1, rank1.playCount)
        assertEquals("spotify:track:333", rank1.trackUri)
        // 1-Click Resume capability verified:
        assertNotNull(rank1.bookmark)
        assertEquals("bm3", rank1.bookmark.id)

        // Rank #2: bm4 ("Harder, Better, Faster, Stronger", 220_000 ms)
        assertEquals("Harder, Better, Faster, Stronger", stats.topSongs[1].title)
    }

    @Test
    fun testHistoryChronologyAndRelativeFormatting() {
        val now = System.currentTimeMillis()
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        assertEquals(4, stats.history.size)

        // Chronological order: most recent first (bm1 is newest timestamp)
        val newest = stats.history[0]
        assertEquals("Coffee Morning", newest.trackTitle)
        assertEquals("ChillHop Beats", newest.artist)
        assertNotNull(newest.bookmark)
        assertEquals("bm1", newest.bookmark.id)

        // Relative time formatting checks
        assertEquals("Gerade eben", StatisticsEngine.formatRelativeTime(now - 10_000L, now))
        assertEquals("vor 5 Min.", StatisticsEngine.formatRelativeTime(now - 300_000L, now))
        assertEquals("vor 2 Std.", StatisticsEngine.formatRelativeTime(now - 7_200_000L, now))
        assertEquals("Gestern", StatisticsEngine.formatRelativeTime(now - 86_400_000L, now))
        assertEquals("vor 3 Tagen", StatisticsEngine.formatRelativeTime(now - 259_200_000L, now))
    }

    @Test
    fun testTopArtistsRankingAndMultiArtistSplitting() {
        val bookmarks = createSampleBookmarks() + Bookmark(
            id = "bm5",
            contextUri = "spotify:album:collab",
            contextTitle = "Collab Album",
            trackTitle = "Starboy",
            artist = "The Weeknd feat. Daft Punk",
            trackUri = "spotify:track:555",
            positionMs = 180_000L,
            durationMs = 230_000L,
            timestamp = System.currentTimeMillis()
        )
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // Daft Punk should have 2 mentions (bm4 + bm5 collaboration)
        val daftPunkStat = stats.topArtists.find { it.name == "Daft Punk" }
        assertNotNull("Daft Punk parsed from collaboration", daftPunkStat)
        assertEquals(2, daftPunkStat?.count)
    }

    @Test
    fun testWeeklyActivityStructureAndZeroBaselines() {
        val bookmarks = createSampleBookmarks()
        val activity = StatisticsEngine.calculateWeeklyActivity(bookmarks)

        assertEquals(7, activity.size)
        val dayNames = activity.map { it.dayName }
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), dayNames)

        for (day in activity) {
            assertTrue(day.fraction in 0f..1.0f)
            assertTrue(day.minutes >= 0)
            if (day.minutes == 0) {
                assertEquals(0f, day.fraction, 0.001f)
            } else {
                assertTrue(day.fraction >= 0.12f)
            }
        }
    }

    @Test
    fun testPersonaAndCompletionRateCalculation() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks)

        // Persona & Peak Hour
        assertNotNull(stats.listeningPersona)
        assertTrue(stats.listeningPersona.isNotBlank())
        assertTrue(stats.peakHourRange.contains("Uhr"))

        // Completion Rate: (120/180 + 90/150 + 240/280 + 220/224) / 4 = (66.6% + 60% + 85.7% + 98.2%) / 4 = 77.6%
        assertTrue("Completion rate > 60%", stats.completionRatePercent in 60..95)
        assertEquals("Fokus-Durchhörer 🎧", stats.completionRateLabel)
    }

    @Test
    fun testCalculateStatsWithComprehensiveListeningHistory() {
        val now = System.currentTimeMillis()
        // Situation: In SQLite bookmarks exist only 2 bookmarks (1 per playlist)
        val activeBookmarks = listOf(
            Bookmark(
                id = "spotify:playlist:lofi_study",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                trackTitle = "Track 5 (Active)",
                artist = "Lofi Artist",
                trackUri = "spotify:track:5",
                positionMs = 150_000L,
                durationMs = 200_000L,
                timestamp = now
            ),
            Bookmark(
                id = "spotify:playlist:coding_flow",
                contextUri = "spotify:playlist:coding_flow",
                contextTitle = "Coding Flow",
                trackTitle = "Track B (Active)",
                artist = "Synth Master",
                trackUri = "spotify:track:b",
                positionMs = 180_000L,
                durationMs = 240_000L,
                timestamp = now - 60_000L
            )
        )

        // But in listening_history all 7 songs previously played in these playlists are logged!
        val historyEntries = listOf(
            HistoryTrackEntry(
                id = "hist_1",
                trackUri = "spotify:track:1",
                trackTitle = "Track 1",
                artist = "Lofi Artist",
                album = "Study Beats Vol 1",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                durationMs = 180_000L,
                listenedMs = 180_000L,
                timestamp = now - 500_000L
            ),
            HistoryTrackEntry(
                id = "hist_2",
                trackUri = "spotify:track:2",
                trackTitle = "Track 2",
                artist = "Lofi Artist",
                album = "Study Beats Vol 1",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                durationMs = 190_000L,
                listenedMs = 190_000L,
                timestamp = now - 400_000L
            ),
            HistoryTrackEntry(
                id = "hist_3",
                trackUri = "spotify:track:3",
                trackTitle = "Track 3",
                artist = "Lofi Artist",
                album = "Study Beats Vol 1",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                durationMs = 200_000L,
                listenedMs = 200_000L,
                timestamp = now - 300_000L
            ),
            HistoryTrackEntry(
                id = "hist_4",
                trackUri = "spotify:track:4",
                trackTitle = "Track 4",
                artist = "Lofi Artist",
                album = "Study Beats Vol 1",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                durationMs = 210_000L,
                listenedMs = 210_000L,
                timestamp = now - 200_000L
            ),
            HistoryTrackEntry(
                id = "hist_5",
                trackUri = "spotify:track:5",
                trackTitle = "Track 5 (Active)",
                artist = "Lofi Artist",
                album = "Study Beats Vol 1",
                contextUri = "spotify:playlist:lofi_study",
                contextTitle = "Lo-Fi Study",
                durationMs = 200_000L,
                listenedMs = 150_000L,
                timestamp = now - 100_000L
            ),
            HistoryTrackEntry(
                id = "hist_a",
                trackUri = "spotify:track:a",
                trackTitle = "Track A",
                artist = "Synth Master",
                album = "Cyberpunk 2099",
                contextUri = "spotify:playlist:coding_flow",
                contextTitle = "Coding Flow",
                durationMs = 240_000L,
                listenedMs = 240_000L,
                timestamp = now - 80_000L
            ),
            HistoryTrackEntry(
                id = "hist_b",
                trackUri = "spotify:track:b",
                trackTitle = "Track B (Active)",
                artist = "Synth Master",
                album = "Cyberpunk 2099",
                contextUri = "spotify:playlist:coding_flow",
                contextTitle = "Coding Flow",
                durationMs = 240_000L,
                listenedMs = 180_000L,
                timestamp = now - 60_000L
            )
        )

        val stats = StatisticsEngine.calculateStats(
            bookmarks = activeBookmarks,
            history = historyEntries
        )

        // All 7 tracks evaluated instead of only 2!
        assertEquals(7, stats.totalTracksCount)
        assertEquals(7, stats.totalBookmarksCount)

        // Total listening time: 180k + 190k + 200k + 210k + 150k + 240k + 180k = 1,350,000 ms = 22.5 min = 22 Min.
        assertEquals(1_350_000L, stats.totalListeningTimeMs)
        assertEquals("22 Min.", stats.formattedTotalTime)

        // Top Album evaluated across all tracks
        assertNotNull(stats.topAlbum)
        assertEquals("Study Beats Vol 1", stats.topAlbum?.title)
        assertEquals(5, stats.topAlbum?.playCount)

        // History list contains all 7 entries sorted descending
        assertEquals(7, stats.history.size)
        assertEquals("Track B (Active)", stats.history.first().trackTitle)
    }

    @Test
    fun testHistoryTrackEntryToBookmarkConversion() {
        val entry = HistoryTrackEntry(
            id = "entry_1",
            trackUri = "spotify:track:abc",
            trackTitle = "Electric Feel",
            artist = "MGMT",
            album = "Oracular Spectacular",
            contextUri = "spotify:playlist:indie_party",
            contextTitle = "Indie Party",
            durationMs = 230_000L,
            listenedMs = 115_000L
        )

        val bookmark = entry.toBookmark()
        assertEquals("spotify:playlist:indie_party", bookmark.id)
        assertEquals("spotify:playlist:indie_party", bookmark.contextUri)
        assertEquals("Indie Party", bookmark.contextTitle)
        assertEquals("Electric Feel", bookmark.trackTitle)
        assertEquals("spotify:track:abc", bookmark.trackUri)
        assertEquals(115_000L, bookmark.positionMs)
        assertEquals(230_000L, bookmark.durationMs)
    }

    @Test
    fun testPlaceholderFiltering() {
        assertTrue(StatisticsEngine.isPlaceholderTrack("spotify:track:active", "Track"))
        assertTrue(StatisticsEngine.isPlaceholderTrack("spotify:track:local_123", "Track"))
        assertTrue(StatisticsEngine.isPlaceholderTrack("spotify:track:notification", "Track"))
        assertTrue(StatisticsEngine.isPlaceholderTrack(null, "Track"))
        assertTrue(StatisticsEngine.isPlaceholderTrack("spotify:track:123", null))
        assertTrue(StatisticsEngine.isPlaceholderTrack("spotify:track:123", "Unbekannter Titel"))
        assertFalse(StatisticsEngine.isPlaceholderTrack("spotify:track:123", "Valid Song"))
    }

    @Test
    fun testGenericQueueFiltering() {
        assertTrue(StatisticsEngine.isGenericQueueOrCorrupted("Warteschlange"))
        assertTrue(StatisticsEngine.isGenericQueueOrCorrupted("Queue"))
        assertTrue(StatisticsEngine.isGenericQueueOrCorrupted("wiedergabeschlange"))
        assertTrue(StatisticsEngine.isGenericQueueOrCorrupted("Playing from playlist"))
        assertFalse(StatisticsEngine.isGenericQueueOrCorrupted("My Playlist"))
    }

    @Test
    fun testTimeOfDayDistribution() {
        val cal = Calendar.getInstance(Locale.GERMANY)
        cal.set(Calendar.HOUR_OF_DAY, 8)
        val mTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 14)
        val aTime = cal.timeInMillis

        val history = listOf(
            HistoryTrackEntry(
                id = "1",
                trackUri = "spotify:track:1",
                trackTitle = "T1",
                artist = "A1",
                album = "Alb1",
                contextUri = "spotify:playlist:p1",
                contextTitle = "P1",
                durationMs = 180_000L,
                listenedMs = 120_000L,
                timestamp = mTime
            ),
            HistoryTrackEntry(
                id = "2",
                trackUri = "spotify:track:2",
                trackTitle = "T2",
                artist = "A2",
                album = "Alb2",
                contextUri = "spotify:playlist:p2",
                contextTitle = "P2",
                durationMs = 300_000L,
                listenedMs = 240_000L,
                timestamp = aTime
            )
        )

        val dist = StatisticsEngine.calculateTimeOfDayDistribution(emptyList(), history)
        assertEquals(4, dist.size)
        val morning = dist.find { it.slotName == "Morgen" }
        val afternoon = dist.find { it.slotName == "Nachmittag" }

        assertEquals(2, morning?.minutes)
        assertEquals(4, afternoon?.minutes)
        assertTrue(afternoon?.isPeak == true)
        assertFalse(morning?.isPeak == true)
    }

    @Test
    fun testTopPlaylistsRanking() {
        val history = listOf(
            HistoryTrackEntry(id = "1", trackUri = "spotify:track:1", trackTitle = "T1", artist = "A1", album = "Alb1", contextUri = "spotify:playlist:p1", contextTitle = "P1", durationMs = 200_000L, listenedMs = 180_000L),
            HistoryTrackEntry(id = "2", trackUri = "spotify:track:2", trackTitle = "T2", artist = "A2", album = "Alb2", contextUri = "spotify:playlist:p1", contextTitle = "P1", durationMs = 200_000L, listenedMs = 200_000L),
            HistoryTrackEntry(id = "3", trackUri = "spotify:track:3", trackTitle = "T3", artist = "A3", album = "Alb3", contextUri = "spotify:playlist:p2", contextTitle = "P2", durationMs = 200_000L, listenedMs = 60_000L),
            HistoryTrackEntry(id = "4", trackUri = "spotify:track:active", trackTitle = "T4", artist = "A4", album = "Queue", contextUri = "spotify:playlist:queue", contextTitle = "Warteschlange", durationMs = 200_000L, listenedMs = 200_000L)
        )

        val top = StatisticsEngine.calculateTopPlaylists(emptyList(), history)
        assertEquals(2, top.size)
        assertEquals("P1", top[0].title)
        assertEquals(380_000L, top[0].totalTimeMs)
        assertEquals(95, top[0].completionRate)
        assertEquals("P2", top[1].title)
        assertEquals(30, top[1].completionRate)
    }
}
