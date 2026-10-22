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
}

