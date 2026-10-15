package com.spotisave.app

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.stats.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsEngineTest {

    private fun createSampleBookmarks(): List<Bookmark> {
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
                timestamp = System.currentTimeMillis()
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
                timestamp = System.currentTimeMillis()
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
                timestamp = System.currentTimeMillis() - 86400000L
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
                timestamp = System.currentTimeMillis() - 172800000L
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
    fun testBoostCalculation() {
        val bookmarks = createSampleBookmarks()
        val (score, level, message) = StatisticsEngine.calculateBoost(bookmarks, 670_000L)

        assertTrue("Boost score between 68 and 99", score in 68..99)
        assertTrue("Boost level contains flow or focus", level.contains("Focus") || level.contains("Flow") || level.contains("Zone") || level.contains("Vibes"))
        assertNotNull(message)
        assertTrue(message.isNotBlank())
    }

    @Test
    fun testGrillMeRoastGenerationAndCycling() {
        val bookmarks = createSampleBookmarks()
        val stats = StatisticsEngine.calculateStats(bookmarks, roastIndex = 0)

        assertNotNull(stats.currentRoast)
        assertTrue(stats.currentRoast.isNotBlank())
        assertTrue(stats.allRoasts.size >= 2)

        // Cycle through roasts
        val statsRoast1 = StatisticsEngine.calculateStats(bookmarks, roastIndex = 1)
        assertNotNull(statsRoast1.currentRoast)

        // Empty state roasts
        val emptyStats = StatisticsEngine.calculateStats(emptyList())
        assertTrue(emptyStats.currentRoast.contains("leer") || emptyStats.currentRoast.contains("Play") || emptyStats.currentRoast.contains("Spotify"))
    }
}
