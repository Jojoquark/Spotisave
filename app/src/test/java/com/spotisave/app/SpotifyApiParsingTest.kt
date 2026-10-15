package com.spotisave.app

import com.spotisave.app.data.api.SpotifyApiClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyApiParsingTest {

    @Test
    fun testParsePlaybackStateTrackAndPlaylistContext() {
        val jsonString = """
            {
                "is_playing": true,
                "progress_ms": 64500,
                "context": {
                    "type": "playlist",
                    "uri": "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"
                },
                "item": {
                    "id": "4iV5W9uYEdYUVa79Axb7Rh",
                    "name": "Silver Springs",
                    "uri": "spotify:track:4iV5W9uYEdYUVa79Axb7Rh",
                    "duration_ms": 288000,
                    "artists": [
                        { "name": "Fleetwood Mac" },
                        { "name": "Stevie Nicks" }
                    ],
                    "album": {
                        "name": "Rumours",
                        "images": [
                            { "url": "https://i.scdn.co/image/ab67616d0000b273e8b066f70c206551210d902b" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val state = SpotifyApiClient.parsePlaybackState(json)

        assertTrue(state.isPlaying)
        assertEquals(64500L, state.progressMs)
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", state.contextUri)
        assertEquals("playlist", state.contextType)

        assertNotNull(state.track)
        assertEquals("Silver Springs", state.track?.name)
        assertEquals("Fleetwood Mac, Stevie Nicks", state.track?.artistName)
        assertEquals("Rumours", state.track?.albumName)
        assertEquals("https://i.scdn.co/image/ab67616d0000b273e8b066f70c206551210d902b", state.track?.coverUrl)
    }

    @Test
    fun testParsePlaybackStatePodcastEpisode() {
        val jsonString = """
            {
                "is_playing": false,
                "progress_ms": 120000,
                "context": null,
                "item": {
                    "id": "episode_xyz",
                    "name": "Folge 142: Deep Dive",
                    "uri": "spotify:episode:episode_xyz",
                    "duration_ms": 3600000,
                    "show": {
                        "name": "Tech Talk Podcast",
                        "images": [
                            { "url": "https://i.scdn.co/image/podcast_cover.jpg" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val state = SpotifyApiClient.parsePlaybackState(json)

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
    fun testParsePlaylistInfo() {
        val jsonString = """
            {
                "name": "Coding Focus Beats",
                "images": [
                    { "url": "https://i.scdn.co/image/playlist_cover.png" }
                ]
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val plInfo = SpotifyApiClient.parsePlaylistInfo(json, "pl_123")

        assertEquals("pl_123", plInfo.id)
        assertEquals("Coding Focus Beats", plInfo.name)
        assertEquals("https://i.scdn.co/image/playlist_cover.png", plInfo.coverUrl)
    }

    @Test
    fun testParseDevices() {
        val jsonString = """
            {
                "devices": [
                    {
                        "id": "dev_phone",
                        "name": "Pixel 8",
                        "type": "Smartphone",
                        "is_active": false
                    },
                    {
                        "id": "dev_speaker",
                        "name": "Wohnzimmer Sonos",
                        "type": "Speaker",
                        "is_active": true
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val devices = SpotifyApiClient.parseDevices(json)

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

        val json = JSONObject(payloadStr)
        assertEquals("spotify:playlist:focus", json.getString("context_uri"))
        assertEquals(45000L, json.getLong("position_ms"))
        assertEquals("spotify:track:track1", json.getJSONObject("offset").getString("uri"))
        assertFalse(json.has("uris"))
    }

    @Test
    fun testBuildPlayPayloadSingleTrackWithoutContext() {
        val payloadStr = SpotifyApiClient.buildPlayPayload(
            contextUri = null,
            trackUri = "spotify:track:single",
            positionMs = 15000L
        )

        val json = JSONObject(payloadStr)
        assertFalse(json.has("context_uri"))
        assertFalse(json.has("offset"))
        assertEquals(15000L, json.getLong("position_ms"))
        val uris = json.getJSONArray("uris")
        assertEquals(1, uris.length())
        assertEquals("spotify:track:single", uris.getString(0))
    }
}
