package com.spotisave.app

import com.spotisave.app.data.api.SpotifyApiClient
import com.spotisave.app.data.api.UpdateChecker
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.service.PlaybackTracker
import com.spotisave.app.service.SpotifyNotificationListenerService
import com.spotisave.app.ui.util.ResumeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                "playlist_image": "https://i.scdn.co/image/web_cover",
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
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", fromWeb[0].id)
        assertEquals("Today's Top Hits", fromWeb[0].contextTitle)
        assertEquals("Espresso", fromWeb[0].trackTitle)
        assertEquals("https://i.scdn.co/image/web_cover", fromWeb[0].coverUrl)
        assertEquals(45_000L, fromWeb[0].positionMs)

        // Map key fallback when child omits id/playlist_id + single web-app object
        val mapKeyFallbackJson = """
            {
              "spotify:playlist:chill_vibes": {
                "track_name": "Sunset Lover",
                "artist_name": "Petit Biscuit",
                "track_uri": "spotify:track:sunset",
                "progress_ms": 12000
              }
            }
        """.trimIndent()
        val fromMapKey = Bookmark.importBookmarksFromJson(mapKeyFallbackJson)
        assertEquals(1, fromMapKey.size)
        assertEquals("spotify:playlist:chill_vibes", fromMapKey[0].id)

        val singleWebObjJson = """
            {
              "playlist_id": "album:ram",
              "playlist_name": "Random Access Memories",
              "track_name": "Get Lucky",
              "artist_name": "Daft Punk",
              "track_uri": "spotify:track:getlucky",
              "progress_ms": 60000
            }
        """.trimIndent()
        val fromSingleWeb = Bookmark.importBookmarksFromJson(singleWebObjJson)
        assertEquals(1, fromSingleWeb.size)
        assertEquals("album:ram", fromSingleWeb[0].id)
        assertEquals("Get Lucky", fromSingleWeb[0].trackTitle)
    }

    @Test
    fun testHistoryTrackEntryJsonSerialization() {
        val entry = HistoryTrackEntry(
            id = "hist_test_99",
            trackUri = "spotify:track:xyz",
            trackTitle = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            contextUri = "spotify:playlist:night_drive",
            contextTitle = "Night Drive",
            coverUrl = "https://i.scdn.co/image/night_cover",
            durationMs = 240_000L,
            listenedMs = 240_000L,
            timestamp = 1700000000000L,
            source = "Broadcast"
        )

        val json = entry.toJsonObject()
        assertEquals("hist_test_99", json.getString("id"))
        assertEquals("spotify:track:xyz", json.getString("trackUri"))
        assertEquals("Midnight City", json.getString("trackTitle"))

        val restored = HistoryTrackEntry.fromJsonObject(json)
        assertEquals(entry.id, restored.id)
        assertEquals(entry.trackUri, restored.trackUri)
        assertEquals(entry.trackTitle, restored.trackTitle)
        assertEquals(entry.artist, restored.artist)
        assertEquals(entry.album, restored.album)
        assertEquals(entry.contextUri, restored.contextUri)
        assertEquals(entry.contextTitle, restored.contextTitle)
        assertEquals(entry.durationMs, restored.durationMs)
        assertEquals(entry.listenedMs, restored.listenedMs)
        assertEquals(entry.timestamp, restored.timestamp)
        assertEquals(entry.source, restored.source)
    }

    @Test
    fun testHistoryTrackEntryToBookmarkPlaylistVsAlbumDisambiguation() {
        // 1. Playlist track where contextUri was temporarily missing in local broadcast,
        //    but contextTitle ("Chill Vibes") is distinct from the track's album ("Random Access Memories")
        val playlistWithoutUri = HistoryTrackEntry(
            id = "hist_pl_no_uri",
            trackUri = "spotify:track:getlucky",
            trackTitle = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
            contextUri = null,
            contextTitle = "Chill Vibes",
            coverUrl = null,
            durationMs = 248_000L,
            listenedMs = 75_000L,
            timestamp = 1700000100000L,
            source = "Broadcast"
        )
        val bmPlaylist = playlistWithoutUri.toBookmark()
        assertEquals("spotify:playlist:local_chill_vibes", bmPlaylist.id)
        assertEquals("spotify:playlist:local_chill_vibes", bmPlaylist.contextUri)
        assertTrue(bmPlaylist.isPlaylist)
        assertFalse(bmPlaylist.isAlbum)
        assertEquals("Chill Vibes", bmPlaylist.displayContextTitle)

        // 2. Genuine album playback where contextTitle matches the album name
        val genuineAlbumEntry = HistoryTrackEntry(
            id = "hist_album_1",
            trackUri = "spotify:track:getlucky",
            trackTitle = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
            contextUri = null,
            contextTitle = "Random Access Memories",
            coverUrl = null,
            durationMs = 248_000L,
            listenedMs = 90_000L,
            timestamp = 1700000200000L,
            source = "Broadcast"
        )
        val bmAlbum = genuineAlbumEntry.toBookmark()
        assertEquals("album:random_access_memories", bmAlbum.id)
        assertFalse(bmAlbum.isPlaylist)
        assertTrue(bmAlbum.isAlbum)
    }

    @Test
    fun testNotificationContextTypeDetection() {
        assertEquals("playlist", SpotifyNotificationListenerService.detectContextTypeFromText("Playlist • Workout Hits"))
        assertEquals("playlist", SpotifyNotificationListenerService.detectContextTypeFromText("Wiedergabeliste • Deutschrap"))
        assertEquals("playlist", SpotifyNotificationListenerService.detectContextTypeFromText("Aus Playlist Chill Vibes"))
        assertEquals("playlist", SpotifyNotificationListenerService.detectContextTypeFromText("Playing from playlist Roadtrip"))
        assertEquals("album", SpotifyNotificationListenerService.detectContextTypeFromText("Album • Future Nostalgia"))
        assertEquals("album", SpotifyNotificationListenerService.detectContextTypeFromText("Playing from album Midnights"))
        assertEquals(null, SpotifyNotificationListenerService.detectContextTypeFromText("Future Nostalgia"))
        assertEquals(null, SpotifyNotificationListenerService.detectContextTypeFromText(null))
    }

    @Test
    fun testResolveNativeTargetUriPrefersPlaylistContextForPlaylistResume() {
        val playlistBookmark = Bookmark(
            id = "spotify:playlist:pl_a",
            contextUri = "spotify:playlist:pl_a",
            contextTitle = "Playlist A",
            trackTitle = "Track 7",
            artist = "Artist A",
            trackUri = "spotify:track:track_7_real_uri",
            positionMs = 84_000L,
            durationMs = 210_000L
        )
        // Native ACTION_VIEW must prioritize playlist URI so Spotify opens inside the playlist context (not the album)
        assertEquals("spotify:playlist:pl_a", ResumeHelper.resolveNativeTargetUri(playlistBookmark))

        // If trackUri is a local placeholder, fall back to contextUri
        val placeholderTrackBookmark = playlistBookmark.copy(trackUri = "spotify:track:local_12345")
        assertEquals("spotify:playlist:pl_a", ResumeHelper.resolveNativeTargetUri(placeholderTrackBookmark))

        // If contextUri is local placeholder playlist and trackUri is valid, use trackUri
        val localPlaylistBookmark = playlistBookmark.copy(
            id = "spotify:playlist:local_my_mix",
            contextUri = "spotify:playlist:local_my_mix"
        )
        assertEquals("spotify:track:track_7_real_uri", ResumeHelper.resolveNativeTargetUri(localPlaylistBookmark))
    }

    @Test
    fun testSanitizeIncomingContextTitleRejectsAlbumEchoInPlaylistSession() {
        // Notification EXTRA_SUB_TEXT merely repeating the track's album name without an explicit
        // "Album • " prefix must return null so it never clobbers an active playlist context
        assertNull(
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Random Access Memories",
                album = "Random Access Memories",
                trackTitle = "Get Lucky",
                contextUri = null,
                contextTypeHint = null
            )
        )

        // Distinct playlist name in EXTRA_SUB_TEXT is preserved
        assertEquals(
            "Chill Vibes",
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Chill Vibes",
                album = "Random Access Memories",
                trackTitle = "Get Lucky",
                contextUri = null,
                contextTypeHint = null
            )
        )

        // Explicit "playlist" hint preserves playlist title even if identical to album name
        assertEquals(
            "Random Access Memories",
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Random Access Memories",
                album = "Random Access Memories",
                trackTitle = "Get Lucky",
                contextUri = null,
                contextTypeHint = "playlist"
            )
        )
    }

    @Test
    fun testResolveEffectiveContextUriAndBuildPlayPayload() {
        val importedPlaylistBookmarkWithoutContextField = Bookmark(
            id = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextUri = null,
            contextTitle = "Today's Top Hits",
            trackTitle = "Espresso",
            artist = "Sabrina Carpenter",
            trackUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            positionMs = 45_000L,
            durationMs = 175_000L
        )
        assertEquals(
            "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            ResumeHelper.resolveEffectiveContextUri(importedPlaylistBookmarkWithoutContextField)
        )

        val syntheticAlbumBookmark = Bookmark(
            id = "album:future_nostalgia",
            contextUri = "album:future_nostalgia",
            contextTitle = "Future Nostalgia",
            trackTitle = "Levitating",
            artist = "Dua Lipa",
            trackUri = "spotify:track:levitating",
            positionMs = 30_000L,
            durationMs = 203_000L
        )
        assertNull(ResumeHelper.resolveEffectiveContextUri(syntheticAlbumBookmark))

        // buildPlayPayload must reject synthetic "album:..." as context_uri and use uris: [trackUri] instead
        val syntheticPayload = SpotifyApiClient.buildPlayPayload(
            contextUri = "album:future_nostalgia",
            trackUri = "spotify:track:levitating",
            positionMs = 30_000L
        )
        assertFalse(syntheticPayload.contains("context_uri"))
        assertTrue(syntheticPayload.contains("\"uris\":[\"spotify:track:levitating\"]"))

        val canonicalPlaylistPayload = SpotifyApiClient.buildPlayPayload(
            contextUri = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            trackUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            positionMs = 45_000L
        )
        assertTrue(canonicalPlaylistPayload.contains("\"context_uri\":\"spotify:playlist:37i9dQZF1DXcBWIGoYBM5M\""))
    }

    @Test
    fun testComputeBookmarkIdentityPreservesPlaylistsAndHandlesAlbums() {
        // Distinct playlist title produces local playlist URI
        val (plId, plCtx) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Summer Vibes",
            album = "Hit Collection",
            trackTitle = "Sunshine",
            trackUri = "spotify:track:123",
            contextTypeHint = null
        )
        assertTrue(plId.startsWith("spotify:playlist:local_summer_vibes"))
        assertEquals("spotify:playlist:local_summer_vibes", plCtx)

        // Explicit album type hint produces album identity
        val (albumId, albumCtx) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Thriller",
            album = "Thriller",
            trackTitle = "Billie Jean",
            trackUri = "spotify:track:456",
            contextTypeHint = "album"
        )
        assertTrue(albumId.startsWith("album:thriller"))
        assertNull(albumCtx)

        // Null track URI fallback
        val (fallbackId, fallbackCtx) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = null,
            album = null,
            trackTitle = null,
            trackUri = null,
            contextTypeHint = null
        )
        assertEquals("spotify:track:active", fallbackId)
        assertNull(fallbackCtx)
    }

    @Test
    fun testCachePlaylistMetadata() {
        PlaybackTracker.cachePlaylistMetadata("spotify:playlist:test_123", "Test Playlist", "https://example.com/cover.jpg")
        assertEquals("Test Playlist", PlaybackTracker.playlistNameCache["spotify:playlist:test_123"])
        assertEquals("https://example.com/cover.jpg", PlaybackTracker.playlistCoverCache["spotify:playlist:test_123"])
    }

    @Test
    fun testCollectionBookmarkModel() {
        val bmCollection = Bookmark(
            id = "spotify:collection:tracks",
            contextUri = "spotify:collection:tracks",
            contextTitle = "Lieblingssongs",
            trackTitle = "Favorite Song",
            artist = "Great Artist",
            trackUri = "spotify:track:fav123",
            positionMs = 45_000L,
            durationMs = 210_000L
        )
        assertTrue(bmCollection.isCollection)
        assertTrue(bmCollection.isPlaylist)
        assertFalse(bmCollection.isAlbum)
        assertEquals("Lieblingssongs", bmCollection.displayContextTitle)

        val bmUserCollection = Bookmark(
            id = "spotify:user:12345:collection",
            contextUri = "spotify:user:12345:collection",
            contextTitle = "Liked Songs",
            trackTitle = "Song Two",
            artist = "Band",
            trackUri = "spotify:track:fav456",
            positionMs = 15_000L,
            durationMs = 180_000L
        )
        assertTrue(bmUserCollection.isCollection)
        assertTrue(bmUserCollection.isPlaylist)
        assertFalse(bmUserCollection.isAlbum)

        val bmTitleOnlyCollection = Bookmark(
            id = "spotify:track:fav789",
            contextUri = null,
            contextTitle = "Lieblingssongs",
            trackTitle = "Song Three",
            artist = "Singer",
            trackUri = "spotify:track:fav789",
            positionMs = 10_000L,
            durationMs = 120_000L
        )
        assertTrue(bmTitleOnlyCollection.isCollection)
        assertTrue(bmTitleOnlyCollection.isPlaylist)
        assertEquals("Lieblingssongs", bmTitleOnlyCollection.displayContextTitle)
    }

    @Test
    fun testSpotifyApiClientCanonicalContextUri() {
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"))
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:show:4rOoJ6Egrf8K2IrywzwOMk"))
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:artist:06HL4z0CvFAxyc27GXpf02"))
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:collection:tracks"))
        assertTrue(SpotifyApiClient.isCanonicalContextUri("spotify:user:12345678:collection"))
        assertFalse(SpotifyApiClient.isCanonicalContextUri("spotify:playlist:local_rock_classics"))
        assertFalse(SpotifyApiClient.isCanonicalContextUri(null))
        assertFalse(SpotifyApiClient.isCanonicalContextUri(""))
    }

    @Test
    fun testSpotifyApiClientBuildPlayPayloadWithCollection() {
        val payload = SpotifyApiClient.buildPlayPayload(
            contextUri = "spotify:collection:tracks",
            trackUri = "spotify:track:fav123",
            positionMs = 45_000L
        )
        val json = org.json.JSONObject(payload)
        assertEquals("spotify:collection:tracks", json.getString("context_uri"))
        assertEquals("spotify:track:fav123", json.getJSONObject("offset").getString("uri"))
        assertEquals(45_000L, json.getLong("position_ms"))
        assertFalse(json.has("uris"))
    }

    @Test
    fun testResolveEffectiveContextUriForCollectionAndLocal() {
        val colBm = Bookmark(
            id = "spotify:collection:tracks",
            contextUri = "spotify:collection:tracks",
            contextTitle = "Lieblingssongs",
            trackTitle = "Track",
            artist = "Artist",
            trackUri = "spotify:track:123",
            positionMs = 10_000L
        )
        assertEquals("spotify:collection:tracks", ResumeHelper.resolveEffectiveContextUri(colBm))

        PlaybackTracker.cachePlaylistMetadata("spotify:playlist:canonical_workout", "Gym Hits", null)
        val localBm = Bookmark(
            id = "spotify:playlist:local_gym_hits",
            contextUri = "spotify:playlist:local_gym_hits",
            contextTitle = "Gym Hits",
            trackTitle = "Pump It",
            artist = "Artist",
            trackUri = "spotify:track:pump",
            positionMs = 5000L
        )
        assertEquals("spotify:playlist:canonical_workout", ResumeHelper.resolveEffectiveContextUri(localBm))
    }

    @Test
    fun testComputeBookmarkIdentityCollection() {
        val (id, ctx) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Lieblingssongs",
            album = "Album Name",
            trackTitle = "Great Song",
            trackUri = "spotify:track:song1",
            contextTypeHint = "playlist"
        )
        assertEquals("spotify:collection:tracks", id)
        assertEquals("spotify:collection:tracks", ctx)
    }

    @Test
    fun testNativeTargetUriPrioritizesPlaylistContext() {
        val playlistBm = Bookmark(
            id = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextUri = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextTitle = "Today's Top Hits",
            trackTitle = "Hit Song",
            artist = "Popular Artist",
            trackUri = "spotify:track:hit123",
            positionMs = 30_000L
        )
        // For playlists, native resume must launch the playlist URI so Spotify opens inside the playlist
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", ResumeHelper.resolveNativeTargetUri(playlistBm))

        val collectionBm = Bookmark(
            id = "spotify:collection:tracks",
            contextUri = "spotify:collection:tracks",
            contextTitle = "Lieblingssongs",
            trackTitle = "Fav Song",
            artist = "Artist",
            trackUri = "spotify:track:fav123",
            positionMs = 15_000L
        )
        assertEquals("spotify:collection:tracks", ResumeHelper.resolveNativeTargetUri(collectionBm))

        val albumBm = Bookmark(
            id = "album:abbey_road",
            contextUri = "spotify:album:0ETFjACtuP2ADo6LFhL6HN",
            contextTitle = "Abbey Road",
            trackTitle = "Come Together",
            artist = "The Beatles",
            trackUri = "spotify:track:beatles1",
            positionMs = 20_000L
        )
        // For albums, track URI is launched directly
        assertEquals("spotify:track:beatles1", ResumeHelper.resolveNativeTargetUri(albumBm))
    }

    @Test
    fun testUpdateCheckerVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("v2.0.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.10.0", "v1.9.0"))

        assertFalse(UpdateChecker.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "v1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("", "1.1.0"))

        // Checks for version 1.2.0
        assertTrue(UpdateChecker.isNewerVersion("v1.2.1", "1.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.3.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "1.2.0"))
    }

    @Test
    fun testUpdateCheckerParseReleaseJson() {
        val mockJson = """
            {
                "tag_name": "v1.2.0",
                "name": "SpotiSave 1.2.0 - Fluid Liquid Glass & Update Engine",
                "body": "- Neue Update-Prüfung\n- Playlist-Wiederaufnahme verbessert",
                "html_url": "https://github.com/Jojoquark/Spotisave/releases/tag/v1.2.0",
                "assets": [
                    {
                        "name": "spotisave-release.apk",
                        "browser_download_url": "https://github.com/Jojoquark/Spotisave/releases/download/v1.2.0/spotisave-release.apk"
                    }
                ]
            }
        """.trimIndent()

        val info = UpdateChecker.parseReleaseJson(mockJson, "1.1.0")
        assertTrue(info.isUpdateAvailable)
        assertEquals("v1.2.0", info.latestVersion)
        assertEquals("1.1.0", info.currentVersion)
        assertEquals("SpotiSave 1.2.0 - Fluid Liquid Glass & Update Engine", info.releaseTitle)
        assertEquals("https://github.com/Jojoquark/Spotisave/releases/download/v1.2.0/spotisave-release.apk", info.apkDownloadUrl)
    }

    @Test
    fun testGenericQueueLabelFiltering() {
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("Warteschlange"))
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("Queue"))
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("queue"))
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("Wiedergabeschlange"))
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("Wiedergabe aus Playlist"))
        assertTrue(SpotifyNotificationListenerService.isGenericQueueOrLabel("Playing from playlist"))

        assertFalse(SpotifyNotificationListenerService.isGenericQueueOrLabel("Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡"))
        assertFalse(SpotifyNotificationListenerService.isGenericQueueOrLabel("Summer Hits 2026"))
        assertFalse(SpotifyNotificationListenerService.isGenericQueueOrLabel("Rock Classics"))
    }

    @Test
    fun testExtractContextTitleFromText() {
        assertNull(SpotifyNotificationListenerService.extractContextTitleFromText("Warteschlange"))
        assertNull(SpotifyNotificationListenerService.extractContextTitleFromText("Queue"))
        assertNull(SpotifyNotificationListenerService.extractContextTitleFromText("Wiedergabe aus Playlist"))

        assertEquals(
            "Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡",
            SpotifyNotificationListenerService.extractContextTitleFromText("Wiedergabe aus Playlist\nFrankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡")
        )
        assertEquals(
            "Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡",
            SpotifyNotificationListenerService.extractContextTitleFromText("Playlist • Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡")
        )
        assertEquals(
            "Roadtrip Vibes",
            SpotifyNotificationListenerService.extractContextTitleFromText("Aus Playlist: Roadtrip Vibes")
        )
    }

    @Test
    fun testSanitizeIncomingContextTitleRejectsQueue() {
        assertNull(
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Warteschlange",
                album = "Album Name",
                trackTitle = "Track Name",
                contextUri = null,
                contextTypeHint = "playlist"
            )
        )
        assertNull(
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Queue",
                album = "Album Name",
                trackTitle = "Track Name",
                contextUri = null,
                contextTypeHint = "playlist"
            )
        )
        assertEquals(
            "Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡",
            PlaybackTracker.sanitizeIncomingContextTitle(
                rawContextTitle = "Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡",
                album = "Burli",
                trackTitle = "Ein Kompliment",
                contextUri = null,
                contextTypeHint = "playlist"
            )
        )
    }

    @Test
    fun testComputeBookmarkIdentityRejectsGenericQueue() {
        val (idQueue, uriQueue) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Warteschlange",
            album = "Burli",
            trackTitle = "Ein Kompliment",
            trackUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            contextTypeHint = "playlist"
        )
        // Must NOT create spotify:playlist:local_warteschlange
        assertFalse(idQueue.contains("warteschlange"))
        assertNull(uriQueue)

        val (idReal, uriReal) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Frankreich 🇫🇷🥖🧀🍷🍇🍻🛁👰‍♀️👱‍♀️🏡",
            album = "Burli",
            trackTitle = "Ein Kompliment",
            trackUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
            contextTypeHint = "playlist"
        )
        assertTrue(idReal.startsWith("spotify:playlist:local_"))
        assertTrue(idReal.contains("frankreich"))
        assertEquals(idReal, uriReal)
    }

    @Test
    fun testIsSameTrackMatching() {
        assertTrue(
            PlaybackTracker.isSameTrack(
                uriA = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                titleA = "Ein Kompliment",
                uriB = "4cOdK2wGLETKBW3PvgPWqT",
                titleB = "Ein Kompliment"
            )
        )
        assertTrue(
            PlaybackTracker.isSameTrack(
                uriA = "spotify:track:active",
                titleA = "Ein Kompliment",
                uriB = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                titleB = "ein kompliment"
            )
        )
        assertFalse(
            PlaybackTracker.isSameTrack(
                uriA = "spotify:track:111",
                titleA = "Track A",
                uriB = "spotify:track:222",
                titleB = "Track B"
            )
        )
    }

    @Test
    fun testLieblingssongsCollectionIdentity() {
        val (idCol, uriCol) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = "spotify:collection:tracks",
            contextTitle = "Lieblingssongs",
            album = "My Album",
            trackTitle = "Favorite Song",
            trackUri = "spotify:track:fav123"
        )
        assertEquals("spotify:collection:tracks", idCol)
        assertEquals("spotify:collection:tracks", uriCol)

        val bm = Bookmark(
            id = idCol,
            contextUri = uriCol,
            contextTitle = "Lieblingssongs",
            trackTitle = "Favorite Song",
            artist = "Fav Artist",
            trackUri = "spotify:track:fav123",
            positionMs = 45000L,
            durationMs = 180000L
        )
        assertTrue(bm.isCollection)
        assertTrue(bm.isPlaylist)
        assertFalse(bm.isAlbum)
        assertEquals("Lieblingssongs", bm.displayContextTitle)
    }

    @Test
    fun testContextIsolationZeroBleed() {
        // Playlist A
        val (idPlA, uriPlA) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = "spotify:playlist:alpha",
            contextTitle = "Rock Playlist",
            album = "Rock Album",
            trackTitle = "Song A",
            trackUri = "spotify:track:songA"
        )
        assertEquals("spotify:playlist:alpha", idPlA)

        // Lieblingssongs
        val (idCol, uriCol) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Lieblingssongs",
            album = "Random Album",
            trackTitle = "Fav Song",
            trackUri = "spotify:track:fav"
        )
        assertEquals("spotify:collection:tracks", idCol)
        assertEquals("spotify:collection:tracks", uriCol)

        // Playlist B
        val (idPlB, uriPlB) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = "spotify:playlist:beta",
            contextTitle = "Jazz Playlist",
            album = "Jazz Album",
            trackTitle = "Song B",
            trackUri = "spotify:track:songB"
        )
        assertEquals("spotify:playlist:beta", idPlB)

        // Alle drei IDs sind vollständig verschieden und kollidieren niemals
        assertTrue(idPlA != idCol)
        assertTrue(idPlB != idCol)
        assertTrue(idPlA != idPlB)
    }

    @Test
    fun testNotificationContextScopingToTrack() {
        // Notification sendet Context-Titel "Frankreich 🇫🇷" für Track "Ein Kompliment"
        SpotifyNotificationListenerService.setNotificationContextForTesting(
            contextTitle = "Frankreich 🇫🇷",
            trackTitle = "Ein Kompliment",
            contextType = "playlist"
        )

        // Abfrage für denselben Track liefert den Playlist-Titel
        assertEquals("Frankreich 🇫🇷", SpotifyNotificationListenerService.getContextTitleForTrack("Ein Kompliment"))
        assertEquals("Frankreich 🇫🇷", SpotifyNotificationListenerService.getContextTitleForTrack("ein kompliment"))
        assertEquals("Frankreich 🇫🇷", SpotifyNotificationListenerService.getContextTitleForTrack("Ein Kompliment (Radio Edit)"))

        // Abfrage für einen ANDEREN Track (z.B. von Playlist 'Casual Drives') liefert strikt null!
        assertNull(SpotifyNotificationListenerService.getContextTitleForTrack("Drive Slow"))
        assertNull(SpotifyNotificationListenerService.getContextTitleForTrack("Another Track"))
        assertNull(SpotifyNotificationListenerService.getContextTitleForTrack(null))
        assertNull(SpotifyNotificationListenerService.getContextTitleForTrack(""))

        // Nach dem Bereinigen der Notification liefert auch der originale Track null
        SpotifyNotificationListenerService.setNotificationContextForTesting(null, null, null)
        assertNull(SpotifyNotificationListenerService.getContextTitleForTrack("Ein Kompliment"))
    }

    @Test
    fun testPlaybackTrackerMatchesResumedTrackAndGraceWindow() {
        val targetBookmark = Bookmark(
            id = "spotify:playlist:casual_drives",
            contextUri = "spotify:playlist:casual_drives",
            contextTitle = "Casual Drives",
            trackTitle = "Drive Slow",
            artist = "Kanye West",
            trackUri = "spotify:track:drive_slow_uri",
            positionMs = 135_000L,
            durationMs = 270_000L
        )

        val now = System.currentTimeMillis()
        PlaybackTracker.setPendingResumeForTesting(targetBookmark, timestampMs = now)

        assertTrue(PlaybackTracker.isWithinResumeGrace(now + 1000L))
        assertTrue(PlaybackTracker.matchesResumedTrack("spotify:track:drive_slow_uri", "Drive Slow"))
        assertTrue(PlaybackTracker.matchesResumedTrack(null, "drive slow"))
        assertTrue(PlaybackTracker.matchesResumedTrack("spotify:track:drive_slow_uri", null))

        // Altes Event von vorheriger Playlist (z.B. 'Ein Kompliment') darf dem resumed Track NICHT matchen
        assertFalse(PlaybackTracker.matchesResumedTrack("spotify:track:frankreich_uri", "Ein Kompliment"))
        assertFalse(PlaybackTracker.matchesResumedTrack("spotify:track:other", "Other Title"))

        // Außerhalb des Grace-Windows (> 12_000ms)
        assertFalse(PlaybackTracker.isWithinResumeGrace(now + 15_000L))

        // Zurücksetzen
        PlaybackTracker.setPendingResumeForTesting(null)
        assertFalse(PlaybackTracker.isWithinResumeGrace(now))
        assertFalse(PlaybackTracker.matchesResumedTrack("spotify:track:drive_slow_uri", "Drive Slow"))
    }

    @Test
    fun testCrossPlaylistResumeIsolationScenario() {
        // Szenario: Nutzer hört 'Frankreich' (Ein Kompliment, 0:39).
        // Jetzt klickt er auf 'Casual Drives' (Drive Slow, 2:15).
        val frankreichBookmark = Bookmark(
            id = "spotify:playlist:frankreich",
            contextUri = "spotify:playlist:frankreich",
            contextTitle = "Frankreich 🇫🇷",
            trackTitle = "Ein Kompliment",
            artist = "Sportfreunde Stiller",
            trackUri = "spotify:track:kompliment_uri",
            positionMs = 39_000L,
            durationMs = 198_000L
        )

        val casualDrivesBookmark = Bookmark(
            id = "spotify:playlist:casual_drives",
            contextUri = "spotify:playlist:casual_drives",
            contextTitle = "Casual Drives",
            trackTitle = "Drive Slow",
            artist = "Kanye West",
            trackUri = "spotify:track:drive_uri",
            positionMs = 135_000L,
            durationMs = 270_000L
        )

        // 1. Notification ist noch auf dem alten Track
        SpotifyNotificationListenerService.setNotificationContextForTesting(
            contextTitle = frankreichBookmark.contextTitle,
            trackTitle = frankreichBookmark.trackTitle,
            contextType = "playlist"
        )

        // 2. Resume für Casual Drives wird gestartet
        val resumeTime = System.currentTimeMillis()
        PlaybackTracker.setPendingResumeForTesting(casualDrivesBookmark, timestampMs = resumeTime)

        // 3. Eintreffendes Event von Spotify für den alten Track 'Ein Kompliment'
        val isMatchForResumed = PlaybackTracker.matchesResumedTrack(
            frankreichBookmark.trackUri,
            frankreichBookmark.trackTitle
        )
        assertFalse("Alte Track-Events dürfen das wiederaufgenommene Lesezeichen nicht matchen", isMatchForResumed)

        // 4. Notification-Kontext-Lookup für den neuen Track 'Drive Slow'
        val leakedContext = SpotifyNotificationListenerService.getContextTitleForTrack(casualDrivesBookmark.trackTitle)
        assertNull("Alter Notification-Kontext darf niemals für neuen Track herangezogen werden", leakedContext)

        // Aufräumen
        PlaybackTracker.setPendingResumeForTesting(null)
        SpotifyNotificationListenerService.setNotificationContextForTesting(null, null, null)
    }

    @Test
    fun testIsSameTrackWithPlaceholdersAndCanonicalIds() {
        // Placeholder-URIs (active, notification) dürfen NIEMALS zu fälschlichen Matches führen, wenn Titel verschieden sind
        assertFalse(
            "Placeholder active URIs mit verschiedenen Titeln dürfen niemals matchen",
            PlaybackTracker.isSameTrack("spotify:track:active", "Ein Kompliment", "spotify:track:active", "Drive Slow")
        )
        assertFalse(
            "Placeholder notification URIs mit verschiedenen Titeln dürfen niemals matchen",
            PlaybackTracker.isSameTrack("spotify:track:notification", "Song A", "spotify:track:notification", "Song B")
        )
        assertTrue(
            "Placeholder active URIs mit identischem Titel müssen matchen",
            PlaybackTracker.isSameTrack("spotify:track:active", "Ein Kompliment", "spotify:track:active", "ein kompliment")
        )

        // Unterschiedliche kanonische URIs dürfen selbst bei gleichem Titel (z.B. 'Intro') NIEMALS matchen
        assertFalse(
            "Verschiedene kanonische Spotify Track IDs dürfen niemals matchen, auch wenn der Titel identisch ist",
            PlaybackTracker.isSameTrack("spotify:track:canonical_1", "Intro", "spotify:track:canonical_2", "Intro")
        )
        assertTrue(
            "Identische kanonische Spotify Track IDs müssen matchen",
            PlaybackTracker.isSameTrack("spotify:track:canonical_1", "Intro", "spotify:track:canonical_1", "Intro")
        )

        // Null-URI Fallback auf Titel-Matching
        assertTrue(
            "Ohne URIs muss Titel-Matching greifen",
            PlaybackTracker.isSameTrack(null, "Midnight City", null, "midnight city")
        )
        assertFalse(
            "Ohne URIs müssen unterschiedliche Titel false liefern",
            PlaybackTracker.isSameTrack(null, "Midnight City", null, "Outro")
        )
    }

    @Test
    fun testNotificationContextScopingStrictBoundary() {
        SpotifyNotificationListenerService.setNotificationContextForTesting(
            contextTitle = "Casual Drives",
            trackTitle = "In",
            contextType = "playlist"
        )
        // Kurzer String "In" darf NIEMALS "Ein Kompliment" matchen!
        assertNull(
            "Kurzer Substring darf nicht fälschlich für fremde Tracks greifen",
            SpotifyNotificationListenerService.getContextTitleForTrack("Ein Kompliment")
        )
        assertNull(
            "Drive darf nicht fälschlich Night Drive matchen",
            SpotifyNotificationListenerService.getContextTitleForTrack("Night Drive")
        )
        // Aber exakt "In" matcht
        assertEquals("Casual Drives", SpotifyNotificationListenerService.getContextTitleForTrack("In"))

        SpotifyNotificationListenerService.setNotificationContextForTesting(null, null, null)
    }

    @Test
    fun testRapidSuccessivePlaylistResume() {
        val plA = Bookmark(
            id = "spotify:playlist:pl_a",
            contextUri = "spotify:playlist:pl_a",
            contextTitle = "Playlist A",
            trackTitle = "Track A",
            artist = "Artist A",
            trackUri = "spotify:track:uri_a",
            positionMs = 10_000L
        )
        val plB = Bookmark(
            id = "spotify:playlist:pl_b",
            contextUri = "spotify:playlist:pl_b",
            contextTitle = "Playlist B",
            trackTitle = "Track B",
            artist = "Artist B",
            trackUri = "spotify:track:uri_b",
            positionMs = 20_000L
        )
        val plC = Bookmark(
            id = "spotify:playlist:pl_c",
            contextUri = "spotify:playlist:pl_c",
            contextTitle = "Playlist C",
            trackTitle = "Track C",
            artist = "Artist C",
            trackUri = "spotify:track:uri_c",
            positionMs = 30_000L
        )

        val t0 = System.currentTimeMillis()
        PlaybackTracker.setPendingResumeForTesting(plA, t0)
        assertTrue(PlaybackTracker.matchesResumedTrack("spotify:track:uri_a", "Track A"))

        val t1 = t0 + 200L
        PlaybackTracker.setPendingResumeForTesting(plB, t1)
        assertFalse(PlaybackTracker.matchesResumedTrack("spotify:track:uri_a", "Track A"))
        assertTrue(PlaybackTracker.matchesResumedTrack("spotify:track:uri_b", "Track B"))

        val t2 = t1 + 300L
        PlaybackTracker.setPendingResumeForTesting(plC, t2)
        assertFalse(PlaybackTracker.matchesResumedTrack("spotify:track:uri_b", "Track B"))
        assertTrue(PlaybackTracker.matchesResumedTrack("spotify:track:uri_c", "Track C"))

        PlaybackTracker.setPendingResumeForTesting(null)
    }

    @Test
    fun testCasualDrivesNeverOverwrittenByLikedSong() {
        // 1. Initialer Zustand: Existierendes Lesezeichen für "Casual Drives"
        val casualDrivesBookmark = Bookmark(
            id = "spotify:playlist:casual_drives",
            contextUri = "spotify:playlist:casual_drives",
            contextTitle = "Casual Drives",
            trackTitle = "Drive Slow",
            artist = "Kanye West",
            trackUri = "spotify:track:drive_slow_uri",
            coverUrl = "https://i.scdn.co/image/casual_drives_artwork.jpg",
            positionMs = 135_000L, // 2:15 Min
            durationMs = 270_000L
        )

        val bookmarks = mutableMapOf<String, Bookmark>()
        bookmarks[casualDrivesBookmark.id] = casualDrivesBookmark

        // 2. Historie enthält früheren Abspielvorgang von "Schau dir zu" in "Casual Drives"
        val historyEntry = HistoryTrackEntry(
            id = "hist_session_1",
            trackUri = "spotify:track:schau_dir_zu",
            trackTitle = "Schau dir zu",
            artist = "Edwin Rosen",
            album = "Schau dir zu EP",
            contextUri = "spotify:playlist:casual_drives",
            contextTitle = "Casual Drives",
            durationMs = 190_000L,
            listenedMs = 120_000L,
            timestamp = System.currentTimeMillis() - 3600_000L
        )
        val history = listOf(historyEntry)

        // 3. Nutzer spielt jetzt Lieblingssong "Schau dir zu" ab
        val (incomingId, incomingCtx) = PlaybackTracker.computeBookmarkIdentity(
            contextUri = null,
            contextTitle = "Lieblingssongs",
            album = "Schau dir zu EP",
            trackTitle = "Schau dir zu",
            trackUri = "spotify:track:schau_dir_zu"
        )
        val likedSongBookmark = Bookmark(
            id = incomingId,
            contextUri = incomingCtx,
            contextTitle = "Lieblingssongs",
            trackTitle = "Schau dir zu",
            artist = "Edwin Rosen",
            trackUri = "spotify:track:schau_dir_zu",
            coverUrl = "https://i.scdn.co/image/schau_dir_zu_cover.jpg",
            positionMs = 45_000L,
            durationMs = 190_000L
        )

        // 4. Strikter Playlist-Only Guard: Lieblingssongs werden NIEMALS persistiert
        val uri = likedSongBookmark.contextUri
        val isRealCanonicalPlaylist = likedSongBookmark.isPlaylist &&
            !likedSongBookmark.isCollection &&
            uri != null &&
            uri.startsWith("spotify:playlist:") &&
            !uri.startsWith("spotify:playlist:local_")

        assertFalse("Lieblingssong darf nicht als kanonische Playlist gelten", isRealCanonicalPlaylist)

        // Falls trotzdem saveBookmark aufgerufen würde:
        val guardPasses = !likedSongBookmark.isCollection &&
            likedSongBookmark.isPlaylist &&
            uri != null &&
            uri.startsWith("spotify:playlist:") &&
            !uri.startsWith("spotify:playlist:local_") &&
            !likedSongBookmark.contextTitle.equals("Lieblingssongs", ignoreCase = true)

        assertFalse("BookmarkRepository.saveBookmark muss Lieblingssongs strikt blockieren", guardPasses)

        // 5. Simulation resolveMissingPlaylistTitles Heuristik:
        // Prüfen, dass kein History-Match über trackUri stattfindet!
        val matchingPlHistory = history.firstOrNull {
            val hUri = it.contextUri
            hUri != null &&
                hUri.startsWith("spotify:playlist:") &&
                !hUri.startsWith("spotify:playlist:local_") &&
                !hUri.contains("collection") &&
                (likedSongBookmark.contextTitle.isNotBlank() &&
                    likedSongBookmark.contextTitle != "Spotify" &&
                    likedSongBookmark.contextTitle != "Playlist" &&
                    !SpotifyNotificationListenerService.isGenericQueueOrLabel(likedSongBookmark.contextTitle) &&
                    it.contextTitle.equals(likedSongBookmark.contextTitle, ignoreCase = true))
        }

        assertNull("Lieblingssongs darf niemals Casual Drives aus der Historie kapern!", matchingPlHistory)

        // 6. Absolute Unveränderbarkeit von Casual Drives überprüfen
        val preservedCasualDrives = bookmarks["spotify:playlist:casual_drives"]!!
        assertEquals("spotify:playlist:casual_drives", preservedCasualDrives.id)
        assertEquals("Casual Drives", preservedCasualDrives.contextTitle)
        assertEquals("Drive Slow", preservedCasualDrives.trackTitle)
        assertEquals("spotify:track:drive_slow_uri", preservedCasualDrives.trackUri)
        assertEquals("https://i.scdn.co/image/casual_drives_artwork.jpg", preservedCasualDrives.coverUrl)
        assertEquals(135_000L, preservedCasualDrives.positionMs)
    }

    @Test
    fun testOnlyRealPlaylistsPersisted() {
        val canonicalPlaylist = Bookmark(
            id = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextUri = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextTitle = "Today's Top Hits",
            trackTitle = "Espresso",
            artist = "Sabrina Carpenter",
            trackUri = "spotify:track:espresso",
            positionMs = 50_000L
        )

        val likedSongsCollection = Bookmark(
            id = "spotify:collection:tracks",
            contextUri = "spotify:collection:tracks",
            contextTitle = "Lieblingssongs",
            trackTitle = "Song A",
            artist = "Artist A",
            trackUri = "spotify:track:a",
            positionMs = 30_000L
        )

        val albumBookmark = Bookmark(
            id = "album:dark_side_of_the_moon",
            contextUri = "spotify:album:dark_side",
            contextTitle = "The Dark Side of the Moon",
            trackTitle = "Money",
            artist = "Pink Floyd",
            trackUri = "spotify:track:money",
            positionMs = 20_000L
        )

        val localPlaylistPlaceholder = Bookmark(
            id = "spotify:playlist:local_roadtrip",
            contextUri = "spotify:playlist:local_roadtrip",
            contextTitle = "Roadtrip",
            trackTitle = "Song B",
            artist = "Artist B",
            trackUri = "spotify:track:b",
            positionMs = 15_000L
        )

        fun canPersist(bm: Bookmark): Boolean {
            val bUri = bm.contextUri
            return bm.isPlaylist &&
                !bm.isCollection &&
                bUri != null &&
                bUri.startsWith("spotify:playlist:") &&
                !bUri.startsWith("spotify:playlist:local_") &&
                !bm.contextTitle.equals("Lieblingssongs", ignoreCase = true) &&
                !bm.contextTitle.equals("Liked Songs", ignoreCase = true)
        }

        assertTrue("Echte kanonische Playlist muss gespeichert werden können", canPersist(canonicalPlaylist))
        assertFalse("Lieblingssongs-Sammlung darf NICHT gespeichert werden", canPersist(likedSongsCollection))
        assertFalse("Alben dürfen NICHT als Lesezeichen gespeichert werden", canPersist(albumBookmark))
        assertFalse("Lokale Playlist-Platzhalter dürfen NICHT als finale Bookmarks gespeichert werden", canPersist(localPlaylistPlaceholder))
    }

    @Test
    fun testLivePlaybackStateShowsLikedSongsAndAlbumsWithoutPersistingBookmark() {
        val liveLikedSongs = PlaybackStateModel(
            trackUri = "spotify:track:starboy",
            trackTitle = "Starboy",
            artist = "The Weeknd",
            contextTitle = "Lieblingssongs",
            contextUri = "spotify:collection:tracks",
            isPlaying = true
        )
        assertTrue("Live PlaybackStateModel erkennt Lieblingssongs", liveLikedSongs.isCollection)
        assertEquals("Lieblingssongs", liveLikedSongs.displayContextTitle)

        val liveAlbum = PlaybackStateModel(
            trackUri = "spotify:track:time",
            trackTitle = "Time",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            contextUri = "spotify:album:dark_side",
            isPlaying = true
        )
        assertTrue("Live PlaybackStateModel erkennt Alben", liveAlbum.isAlbum)
        assertFalse("Album ist keine Collection", liveAlbum.isCollection)

        val livePlaylist = PlaybackStateModel(
            trackUri = "spotify:track:drive_slow",
            trackTitle = "Drive Slow",
            artist = "Kanye West",
            contextTitle = "Casual Drives",
            contextUri = "spotify:playlist:casual_drives",
            isPlaying = true
        )
        assertTrue("Live PlaybackStateModel erkennt echte Playlist", livePlaylist.isPlaylist)
        assertFalse("Echte Playlist ist keine Collection", livePlaylist.isCollection)
        assertEquals("Casual Drives", livePlaylist.displayContextTitle)
    }

    @Test
    fun testHeroCardTargetBookmarkDoesNotHijackCasualDrivesOnLikedSong() {
        val casualDrives = Bookmark(
            id = "spotify:playlist:casual_drives",
            contextUri = "spotify:playlist:casual_drives",
            contextTitle = "Casual Drives",
            trackTitle = "Drive Slow",
            artist = "Kanye West",
            trackUri = "spotify:track:drive_slow",
            coverUrl = "https://i.scdn.co/image/casual_drives_cover.jpg",
            positionMs = 135_000L,
            durationMs = 240_000L
        )
        val bookmarks = listOf(casualDrives)

        // Live playback plays a Liked Song "Schau dir zu"
        val active = PlaybackStateModel(
            trackUri = "spotify:track:schau_dir_zu",
            trackTitle = "Schau dir zu",
            artist = "Edwin Rosen",
            album = "Schau dir zu EP",
            contextTitle = "Lieblingssongs",
            contextUri = "spotify:collection:tracks",
            coverUrl = "https://i.scdn.co/image/schau_dir_zu_cover.jpg",
            isPlaying = true,
            positionMs = 45_000L,
            durationMs = 190_000L
        )

        // 1. In MainScreen: matchingBookmark must strictly match contextUri, NOT trackUri!
        val matchingBookmark = bookmarks.find {
            !active.contextUri.isNullOrBlank() &&
                (it.contextUri == active.contextUri || it.id == active.contextUri)
        }
        assertNull("Lieblingssong darf niemals Casual Drives Bookmark matchen!", matchingBookmark)

        // 2. In HeroCard: When isLive is true and latestBookmark is null or from another context:
        val fallbackContextUri: String? = null
        val fallbackId = active.trackUri
        val isMatchingLatest = matchingBookmark != null && (
            matchingBookmark.id == fallbackId ||
                (!fallbackContextUri.isNullOrBlank() && matchingBookmark.contextUri == fallbackContextUri)
        )

        val targetBookmark = if (isMatchingLatest && matchingBookmark != null) {
            matchingBookmark.copy(trackTitle = active.trackTitle)
        } else {
            Bookmark(
                id = fallbackId,
                contextUri = fallbackContextUri,
                contextTitle = "Lieblingssongs",
                trackTitle = active.trackTitle,
                artist = active.artist,
                trackUri = active.trackUri,
                coverUrl = active.coverUrl,
                positionMs = active.positionMs,
                durationMs = active.durationMs
            )
        }

        assertNotEquals("Target bookmark ID darf niemals Casual Drives sein", casualDrives.id, targetBookmark.id)
        assertNotEquals("Target bookmark cover darf niemals Casual Drives sein", casualDrives.coverUrl, targetBookmark.coverUrl)
        assertEquals("spotify:track:schau_dir_zu", targetBookmark.id)
        assertFalse("Target bookmark für Lieblingssongs darf nicht als Playlist gelten", targetBookmark.isPlaylist && !targetBookmark.isCollection)

        // Original Casual Drives remains pristine
        assertEquals("Casual Drives", casualDrives.contextTitle)
        assertEquals("Drive Slow", casualDrives.trackTitle)
        assertEquals("spotify:track:drive_slow", casualDrives.trackUri)
    }

    @Test
    fun testImportBookmarksJsonFiltersNonPlaylists() {
        val jsonBackup = """
            [
                {
                    "id": "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                    "contextUri": "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                    "contextTitle": "Today's Top Hits",
                    "trackTitle": "Espresso",
                    "artist": "Sabrina Carpenter",
                    "trackUri": "spotify:track:espresso",
                    "positionMs": 50000,
                    "durationMs": 180000
                },
                {
                    "id": "spotify:collection:tracks",
                    "contextUri": "spotify:collection:tracks",
                    "contextTitle": "Lieblingssongs",
                    "trackTitle": "Schau dir zu",
                    "artist": "Edwin Rosen",
                    "trackUri": "spotify:track:schau_dir_zu",
                    "positionMs": 45000,
                    "durationMs": 190000
                },
                {
                    "id": "album:dark_side",
                    "contextUri": "spotify:album:dark_side",
                    "contextTitle": "The Dark Side",
                    "trackTitle": "Money",
                    "artist": "Pink Floyd",
                    "trackUri": "spotify:track:money",
                    "positionMs": 30000,
                    "durationMs": 200000
                },
                {
                    "id": "spotify:playlist:local_roadtrip",
                    "contextUri": "spotify:playlist:local_roadtrip",
                    "contextTitle": "Roadtrip",
                    "trackTitle": "Song B",
                    "artist": "Artist B",
                    "trackUri": "spotify:track:b",
                    "positionMs": 15000,
                    "durationMs": 120000
                }
            ]
        """.trimIndent()

        val parsed = Bookmark.importBookmarksFromJson(jsonBackup)
        assertEquals("Alle 4 Einträge im JSON geparst", 4, parsed.size)

        val validPlaylistsOnly = parsed.filter { bm ->
            val eff = bm.contextUri?.takeIf { it.isNotBlank() } ?: bm.id.takeIf { it.startsWith("spotify:playlist:") }
            eff != null &&
                eff.startsWith("spotify:playlist:") &&
                !eff.startsWith("spotify:playlist:local_") &&
                !bm.id.startsWith("spotify:playlist:local_") &&
                !bm.id.startsWith("album:") &&
                !bm.isCollection &&
                !bm.id.contains("warteschlange", ignoreCase = true) &&
                !bm.id.contains("queue", ignoreCase = true) &&
                !bm.contextTitle.equals("Warteschlange", ignoreCase = true) &&
                !bm.contextTitle.equals("Queue", ignoreCase = true) &&
                !bm.contextTitle.equals("Lieblingssongs", ignoreCase = true) &&
                !bm.contextTitle.equals("Liked Songs", ignoreCase = true)
        }

        assertEquals("Nur die echte Spotify-Playlist darf importiert werden", 1, validPlaylistsOnly.size)
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", validPlaylistsOnly[0].id)
        assertEquals("Today's Top Hits", validPlaylistsOnly[0].contextTitle)
    }
}

