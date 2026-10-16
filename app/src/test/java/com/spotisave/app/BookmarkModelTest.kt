package com.spotisave.app

import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkModelTest {

    @Test
    fun testBookmarkProgressFraction() {
        val bmNormal = Bookmark(
            id = "test-1",
            contextTitle = "Playlist",
            trackTitle = "Track",
            artist = "Artist",
            trackUri = "spotify:track:123",
            positionMs = 30_000L,
            durationMs = 60_000L
        )
        assertEquals(0.5f, bmNormal.progressFraction, 0.001f)

        val bmZeroDuration = Bookmark(
            id = "test-2",
            contextTitle = "Live",
            trackTitle = "Live",
            artist = "Artist",
            trackUri = "spotify:track:123",
            positionMs = 10_000L,
            durationMs = 0L
        )
        assertEquals(0f, bmZeroDuration.progressFraction, 0.001f)

        val bmOverflow = Bookmark(
            id = "test-3",
            contextTitle = "Album",
            trackTitle = "Track",
            artist = "Artist",
            trackUri = "spotify:track:123",
            positionMs = 70_000L,
            durationMs = 60_000L
        )
        assertEquals(1.0f, bmOverflow.progressFraction, 0.001f)
    }

    @Test
    fun testPlaybackStateModelFraction() {
        val state = PlaybackStateModel(
            trackUri = "spotify:track:abc",
            trackTitle = "Song",
            artist = "Band",
            contextUri = "spotify:playlist:pop_hits",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273",
            positionMs = 45_000L,
            durationMs = 180_000L,
            isPlaying = true
        )
        assertEquals(0.25f, state.progressFraction, 0.001f)
        assertEquals("spotify:playlist:pop_hits", state.contextUri)
        assertEquals("https://i.scdn.co/image/ab67616d0000b273", state.coverUrl)
    }

    @Test
    fun testPerPlaylistBookmarkIdentity() {
        val albumName = "The Dark Side of the Moon"
        val normalizedAlbum = albumName.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val bookmarkId = "album:$normalizedAlbum"
        assertEquals("album:the_dark_side_of_the_moon", bookmarkId)

        val track1 = Bookmark(
            id = bookmarkId,
            contextTitle = albumName,
            trackTitle = "Speak to Me",
            artist = "Pink Floyd",
            trackUri = "spotify:track:track1",
            positionMs = 60_000L,
            durationMs = 68_000L
        )

        // Track 2 in the same album maintains the same bookmark ID so it updates the album's bookmark
        val track2 = track1.copy(
            trackTitle = "Breathe (In the Air)",
            trackUri = "spotify:track:track2",
            positionMs = 12_000L,
            durationMs = 163_000L
        )

        assertEquals(track1.id, track2.id)
        assertEquals("spotify:track:track2", track2.trackUri)
        assertEquals(12_000L, track2.positionMs)
    }

    @Test
    fun testPlaylistAndAlbumHelpers() {
        val playlistBm = Bookmark(
            id = "spotify:playlist:top50",
            contextUri = "spotify:playlist:top50",
            contextTitle = "Top 50 - Deutschland",
            trackTitle = "Komet",
            artist = "Udo Lindenberg & Apache 207",
            trackUri = "spotify:track:komet",
            positionMs = 40_000L,
            durationMs = 167_000L
        )
        assertEquals(true, playlistBm.isPlaylist)
        assertEquals(false, playlistBm.isAlbum)
        assertEquals("Top 50 - Deutschland", playlistBm.displayContextTitle)

        val albumBm = Bookmark(
            id = "album:future_nostalgia",
            contextUri = "spotify:album:future_nostalgia",
            contextTitle = "Future Nostalgia",
            trackTitle = "Levitating",
            artist = "Dua Lipa",
            trackUri = "spotify:track:levitating",
            positionMs = 30_000L,
            durationMs = 203_000L
        )
        assertEquals(false, albumBm.isPlaylist)
        assertEquals(true, albumBm.isAlbum)
        assertEquals("Future Nostalgia", albumBm.displayContextTitle)

        // Test fallback when contextTitle is generic Spotify
        val genericPlaylistBm = playlistBm.copy(contextTitle = "Spotify")
        assertEquals("Playlist", genericPlaylistBm.displayContextTitle)

        // PlaybackStateModel helpers
        val liveState = PlaybackStateModel(
            trackUri = "spotify:track:123",
            trackTitle = "Song",
            artist = "Artist",
            contextUri = "spotify:playlist:summer_vibes",
            contextTitle = "Summer Vibes",
            album = "Summer Vibes 2024"
        )
        assertEquals(true, liveState.isPlaylist)
        assertEquals(false, liveState.isAlbum)
        assertEquals("Summer Vibes", liveState.displayContextTitle)
    }

    @Test
    fun testBookmarkJsonExportAndImport() {
        val original = listOf(
            Bookmark(
                id = "spotify:playlist:top50",
                contextUri = "spotify:playlist:top50",
                contextTitle = "Top 50 - Deutschland",
                trackTitle = "Komet",
                artist = "Udo Lindenberg & Apache 207",
                trackUri = "spotify:track:komet",
                coverUrl = "https://i.scdn.co/image/cover1",
                positionMs = 42_500L,
                durationMs = 167_000L,
                timestamp = 1700000000000L,
                source = "SpotifyWebApi"
            ),
            Bookmark(
                id = "album:future_nostalgia",
                contextUri = null,
                contextTitle = "Future Nostalgia",
                trackTitle = "Levitating",
                artist = "Dua Lipa",
                trackUri = "spotify:track:levitating",
                coverUrl = null,
                positionMs = 88_000L,
                durationMs = 203_000L,
                timestamp = 1700000050000L,
                source = "MediaSession"
            )
        )

        val exportedJson = Bookmark.exportBookmarksToJson(original)
        val restored = Bookmark.importBookmarksFromJson(exportedJson)

        assertEquals(2, restored.size)
        assertEquals("spotify:playlist:top50", restored[0].id)
        assertEquals("Top 50 - Deutschland", restored[0].contextTitle)
        assertEquals(42_500L, restored[0].positionMs)
        assertEquals("https://i.scdn.co/image/cover1", restored[0].coverUrl)
        assertEquals(null, restored[1].contextUri)
        assertEquals(null, restored[1].coverUrl)

        // Edge cases: empty input, invalid JSON, web-app format
        assertEquals(0, Bookmark.importBookmarksFromJson("").size)
        assertEquals(0, Bookmark.importBookmarksFromJson("   ").size)
        assertEquals(0, Bookmark.importBookmarksFromJson("not-json-at-all").size)

        val webAppJson = """
            {
              "37i9dQZF1DXcBWIGoYBM5M": {
                "playlist_id": "37i9dQZF1DXcBWIGoYBM5M",
                "playlist_name": "Today's Top Hits",
                "track_name": "Espresso",
                "artist_name": "Sabrina Carpenter",
                "track_uri": "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                "context_uri": "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                "progress_ms": 45000,
                "updated_at": 1700000100000
              }
            }
        """.trimIndent()
        val fromWeb = Bookmark.importBookmarksFromJson(webAppJson)
        assertEquals(1, fromWeb.size)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", fromWeb[0].id)
        assertEquals("Today's Top Hits", fromWeb[0].contextTitle)
        assertEquals("Espresso", fromWeb[0].trackTitle)
        assertEquals(45_000L, fromWeb[0].positionMs)
    }
}
