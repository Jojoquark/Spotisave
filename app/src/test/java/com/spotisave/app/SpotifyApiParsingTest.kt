package com.spotisave.app

import com.spotisave.app.data.api.SpotifyApiClient
import com.spotisave.app.data.api.SpotifyDevice
import com.spotisave.app.data.api.SpotifyPlaybackState
import com.spotisave.app.data.api.SpotifyPlaylistInfo
import com.spotisave.app.data.api.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-kompatible Unit-Tests für Spotify API Datenmodelle und Payload-Generierung.
 * Vermeidet direkte Instanziierung von android.jar JSONObject Stubs auf der Host-JVM.
 */
class SpotifyApiParsingTest {

    @Test
    fun testSpotifyPlaybackStateDataModel() {
        val track = SpotifyTrack(
            id = "4iV5W9uYEdYUVa79Axb7Rh",
            name = "Silver Springs",
            uri = "spotify:track:4iV5W9uYEdYUVa79Axb7Rh",
            durationMs = 288000L,
            artistName = "Fleetwood Mac, Stevie Nicks",
            albumName = "Rumours",
            coverUrl = "https://i.scdn.co/image/ab67616d0000b273"
        )

        val state = SpotifyPlaybackState(
            isPlaying = true,
            progressMs = 64500L,
            contextUri = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            contextType = "playlist",
            track = track
        )

        assertTrue(state.isPlaying)
        assertEquals(64500L, state.progressMs)
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", state.contextUri)
        assertEquals("playlist", state.contextType)

        assertNotNull(state.track)
        assertEquals("Silver Springs", state.track?.name)
        assertEquals("Fleetwood Mac, Stevie Nicks", state.track?.artistName)
        assertEquals("Rumours", state.track?.albumName)
        assertEquals("https://i.scdn.co/image/ab67616d0000b273", state.track?.coverUrl)
    }

    @Test
    fun testSpotifyPlaybackStatePodcastEpisode() {
        val episode = SpotifyTrack(
            id = "episode_xyz",
            name = "Folge 142: Deep Dive",
            uri = "spotify:episode:episode_xyz",
            durationMs = 3600000L,
            artistName = "Tech Talk Podcast",
            albumName = "Tech Talk Podcast",
            coverUrl = "https://i.scdn.co/image/podcast_cover.jpg"
        )

        val state = SpotifyPlaybackState(
            isPlaying = false,
            progressMs = 120000L,
            contextUri = null,
            contextType = null,
            track = episode
        )

        assertFalse(state.isPlaying)
        assertEquals(120000L, state.progressMs)
        assertNull(state.contextUri)

        assertNotNull(state.track)
        assertEquals("Folge 142: Deep Dive", state.track?.name)
        assertEquals("Tech Talk Podcast", state.track?.artistName)
        assertEquals("Tech Talk Podcast", state.track?.albumName)
        assertEquals("https://i.scdn.co/image/podcast_cover.jpg", state.track?.coverUrl)
    }

    @Test
    fun testPlaylistInfoModel() {
        val plInfo = SpotifyPlaylistInfo(
            id = "pl_123",
            name = "Coding Focus Beats",
            coverUrl = "https://i.scdn.co/image/playlist_cover.png"
        )

        assertEquals("pl_123", plInfo.id)
        assertEquals("Coding Focus Beats", plInfo.name)
        assertEquals("https://i.scdn.co/image/playlist_cover.png", plInfo.coverUrl)
    }

    @Test
    fun testDevicesModel() {
        val devPhone = SpotifyDevice(
            id = "dev_phone",
            name = "Pixel 8",
            type = "Smartphone",
            isActive = false
        )
        val devSpeaker = SpotifyDevice(
            id = "dev_speaker",
            name = "Wohnzimmer Sonos",
            type = "Speaker",
            isActive = true
        )

        val devices = listOf(devPhone, devSpeaker)
        assertEquals(2, devices.size)
        assertEquals("dev_phone", devices[0].id)
        assertFalse(devices[0].isActive)

        assertEquals("dev_speaker", devices[1].id)
        assertTrue(devices[1].isActive)
    }

    @Test
    fun testBuildPlayPayloadWithContextAndOffset() {
        val payloadStr = SpotifyApiClient.buildPlayPayload(
            contextUri = "spotify:playlist:focus",
            trackUri = "spotify:track:track1",
            positionMs = 45000L
        )

        assertTrue("Payload muss context_uri enthalten", payloadStr.contains("\"context_uri\":\"spotify:playlist:focus\""))
        assertTrue("Payload muss position_ms enthalten", payloadStr.contains("\"position_ms\":45000"))
        assertTrue("Payload muss offset mit track uri enthalten", payloadStr.contains("\"offset\":{\"uri\":\"spotify:track:track1\"}"))
        assertFalse("Playlist Payload darf kein uris Array enthalten", payloadStr.contains("\"uris\":"))
    }

    @Test
    fun testBuildPlayPayloadSingleTrackWithoutContext() {
        val payloadStr = SpotifyApiClient.buildPlayPayload(
            contextUri = null,
            trackUri = "spotify:track:single",
            positionMs = 15000L
        )

        assertFalse("Single Track Payload darf kein context_uri enthalten", payloadStr.contains("\"context_uri\":"))
        assertFalse("Single Track Payload darf kein offset enthalten", payloadStr.contains("\"offset\":"))
        assertTrue("Single Track Payload muss position_ms enthalten", payloadStr.contains("\"position_ms\":15000"))
        assertTrue("Single Track Payload muss uris Array enthalten", payloadStr.contains("\"uris\":[\"spotify:track:single\"]"))
    }
}
