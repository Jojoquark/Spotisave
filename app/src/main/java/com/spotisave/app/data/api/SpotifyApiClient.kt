package com.spotisave.app.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.stats.ArtistStat

data class SpotifyTrack(
    val id: String,
    val name: String,
    val uri: String,
    val durationMs: Long,
    val artistName: String,
    val albumName: String,
    val coverUrl: String?
)

data class SpotifyPlaybackState(
    val isPlaying: Boolean,
    val progressMs: Long,
    val track: SpotifyTrack?,
    val contextUri: String?,
    val contextType: String?
)

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val coverUrl: String?
)

data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean
)

/**
 * Spotify Web API Client für native Android-Integration.
 * Bietet Abfragen für /me/player, Playlist-Metadaten und 1-Click Resume mit Device-Fallback.
 */
class SpotifyApiClient(private val context: Context) {

    private val authManager: SpotifyAuthManager = SpotifyAuthManager.getInstance(context)

    companion object {
        private const val TAG = "SpotifyApiClient"
        private const val BASE_URL = "https://api.spotify.com/v1"

        @Volatile
        private var INSTANCE: SpotifyApiClient? = null

        fun getInstance(context: Context): SpotifyApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyApiClient(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Parst ein Spotify /me/player JSON-Objekt sicher und robust.
         */
        fun parsePlaybackState(json: JSONObject): SpotifyPlaybackState {
            val isPlaying = json.optBoolean("is_playing", false)
            val progressMs = json.optLong("progress_ms", 0L)

            val contextObj = json.optJSONObject("context")
            val contextUri = contextObj?.optString("uri", null)
            val contextType = contextObj?.optString("type", null)

            val itemObj = json.optJSONObject("item")
            val track = if (itemObj != null) {
                val trackId = itemObj.optString("id", "")
                val trackName = itemObj.optString("name", "Unbekannter Titel")
                val trackUri = itemObj.optString("uri", "")
                val durationMs = itemObj.optLong("duration_ms", 0L)

                // Interpret / Show
                var artistName = "Unbekannter Interpret"
                val artistsArray = itemObj.optJSONArray("artists")
                if (artistsArray != null && artistsArray.length() > 0) {
                    val names = mutableListOf<String>()
                    for (i in 0 until artistsArray.length()) {
                        val a = artistsArray.optJSONObject(i)
                        val n = a?.optString("name", "") ?: ""
                        if (n.isNotBlank()) names.add(n)
                    }
                    if (names.isNotEmpty()) {
                        artistName = names.joinToString(", ")
                    }
                } else {
                    val showObj = itemObj.optJSONObject("show")
                    if (showObj != null) {
                        artistName = showObj.optString("name", artistName)
                    }
                }

                // Album & Cover
                var albumName = "Spotify"
                var coverUrl: String? = null
                val albumObj = itemObj.optJSONObject("album")
                if (albumObj != null) {
                    albumName = albumObj.optString("name", albumName)
                    val imagesArray = albumObj.optJSONArray("images")
                    if (imagesArray != null && imagesArray.length() > 0) {
                        coverUrl = imagesArray.optJSONObject(0)?.optString("url", null)
                    }
                } else {
                    val showObj = itemObj.optJSONObject("show")
                    if (showObj != null) {
                        albumName = showObj.optString("name", "Podcast")
                        val showImages = showObj.optJSONArray("images")
                        if (showImages != null && showImages.length() > 0) {
                            coverUrl = showImages.optJSONObject(0)?.optString("url", null)
                        }
                    }
                }

                SpotifyTrack(
                    id = trackId,
                    name = trackName,
                    uri = trackUri,
                    durationMs = durationMs,
                    artistName = artistName,
                    albumName = albumName,
                    coverUrl = coverUrl
                )
            } else null

            return SpotifyPlaybackState(
                isPlaying = isPlaying,
                progressMs = progressMs,
                track = track,
                contextUri = contextUri,
                contextType = contextType
            )
        }

        /**
         * Parst die Playlist-Informationen aus /v1/playlists/{id}.
         */
        fun parsePlaylistInfo(json: JSONObject, fallbackId: String): SpotifyPlaylistInfo {
            val name = json.optString("name", "Playlist ($fallbackId)")
            var coverUrl: String? = null
            val images = json.optJSONArray("images")
            if (images != null && images.length() > 0) {
                coverUrl = images.optJSONObject(0)?.optString("url", null)
            }
            return SpotifyPlaylistInfo(
                id = fallbackId,
                name = name,
                coverUrl = coverUrl
            )
        }

        /**
         * Parst die Liste verfügbarer Geräte aus /v1/me/player/devices.
         */
        fun parseDevices(json: JSONObject): List<SpotifyDevice> {
            val list = mutableListOf<SpotifyDevice>()
            val devicesArray = json.optJSONArray("devices") ?: return list
            for (i in 0 until devicesArray.length()) {
                val d = devicesArray.optJSONObject(i) ?: continue
                list.add(
                    SpotifyDevice(
                        id = d.optString("id", ""),
                        name = d.optString("name", "Unbekanntes Gerät"),
                        type = d.optString("type", "Speaker"),
                        isActive = d.optBoolean("is_active", false)
                    )
                )
            }
            return list
        }

        /**
         * Baut das JSON-Payload für PUT /me/player/play.
         */
        fun buildPlayPayload(contextUri: String?, trackUri: String, positionMs: Long): String {
            val json = JSONObject()
            if (!contextUri.isNullOrBlank()) {
                json.put("context_uri", contextUri)
                val offset = JSONObject()
                offset.put("uri", trackUri)
                json.put("offset", offset)
            } else {
                val urisArray = JSONArray()
                urisArray.put(trackUri)
                json.put("uris", urisArray)
            }
            json.put("position_ms", positionMs.coerceAtLeast(0L))
            return json.toString()
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private suspend fun sendRequest(
        endpoint: String,
        method: String = "GET",
        payload: String? = null
    ): HttpResponse = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext HttpResponse(401, "{\"error\": \"Unauthorized - Kein Access-Token\"}")

        fun doHttp(authToken: String): HttpResponse {
            var conn: HttpURLConnection? = null
            return try {
                val url = URL(if (endpoint.startsWith("http")) endpoint else "$BASE_URL$endpoint")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Authorization", "Bearer $authToken")
                    setRequestProperty("Accept", "application/json")
                    if (payload != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }

                if (payload != null) {
                    OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payload) }
                }

                val code = conn.responseCode
                val text = if (code == 204 || code == 205) {
                    ""
                } else {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    if (stream != null) {
                        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else ""
                }

                HttpResponse(code, text)
            } catch (e: Exception) {
                Log.w(TAG, "HTTP-Anfrage fehlgeschlagen ($method $endpoint): ${e.message}")
                HttpResponse(0, "{\"error\": \"${e.message ?: "Netzwerkfehler"}\"}")
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }

        var response = doHttp(token)

        // Automatische 401 Re-Auth
        if (response.code == 401) {
            val refreshResult = authManager.refreshAccessToken()
            val newToken = refreshResult.getOrNull()
            if (newToken != null) {
                response = doHttp(newToken)
            }
        }

        response
    }

    /**
     * Ruft den aktuellen Player-Status von Spotify ab (/me/player).
     */
    suspend fun getNowPlaying(): Result<SpotifyPlaybackState?> {
        return try {
            val resp = sendRequest("/me/player?additional_types=track,episode")
            when (resp.code) {
                200 -> {
                    val json = JSONObject(resp.body)
                    Result.success(parsePlaybackState(json))
                }
                204 -> Result.success(null) // Keine aktive Wiedergabe
                else -> Result.failure(Exception("Spotify API Fehler ${resp.code}: ${resp.body}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fragt den zuletzt gespielten Titel ab (/me/player/recently-played?limit=1).
     */
    suspend fun getRecentlyPlayed(): Result<SpotifyPlaybackState?> {
        return try {
            val resp = sendRequest("/me/player/recently-played?limit=1")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val item0 = items.optJSONObject(0)
                    val trackObj = item0?.optJSONObject("track")
                    val contextObj = item0?.optJSONObject("context")

                    if (trackObj != null) {
                        val simJson = JSONObject()
                        simJson.put("is_playing", false)
                        simJson.put("progress_ms", 0)
                        simJson.put("item", trackObj)
                        if (contextObj != null) {
                            simJson.put("context", contextObj)
                        }
                        return Result.success(parsePlaybackState(simJson))
                    }
                }
                Result.success(null)
            } else {
                Result.failure(Exception("Fehler beim Abrufen von recently-played: HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruft Titel und Cover einer Playlist ab.
     */
    suspend fun getPlaylistInfo(playlistId: String): Result<SpotifyPlaylistInfo> {
        return try {
            val resp = sendRequest("/playlists/$playlistId?fields=name,images")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                Result.success(parsePlaylistInfo(json, playlistId))
            } else {
                Result.failure(Exception("Playlist $playlistId konnte nicht geladen werden: HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruft alle verfügbaren Spotify Connect-Geräte ab.
     */
    suspend fun getDevices(): Result<List<SpotifyDevice>> {
        return try {
            val resp = sendRequest("/me/player/devices")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                Result.success(parseDevices(json))
            } else {
                Result.failure(Exception("Geräte konnten nicht abgefragt werden: HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 1-Click Wiederaufnahme über Spotify Web API (PUT /me/player/play).
     * Unterstützt automatischen Fallback bei 404 (Geräte-Suche) und 400 (Track-Direktaufruf).
     */
    suspend fun resumePlayback(
        contextUri: String?,
        trackUri: String,
        positionMs: Long
    ): Result<Boolean> {
        return try {
            val payload = buildPlayPayload(contextUri, trackUri, positionMs)
            var resp = sendRequest("/me/player/play", method = "PUT", payload = payload)

            var targetDeviceId: String? = null

            // 1. Fallback: 404 No Active Device -> Geräte suchen und auf aktivem/erstmöglichem Gerät starten
            if (resp.code == 404) {
                Log.d(TAG, "PUT /me/player/play gab 404 zurück -> Prüfe verfügbare Geräte")
                val devicesResult = getDevices()
                val devices = devicesResult.getOrNull()
                if (!devices.isNullOrEmpty()) {
                    val target = devices.firstOrNull { it.isActive } ?: devices.first()
                    targetDeviceId = target.id
                    resp = sendRequest(
                        "/me/player/play?device_id=$targetDeviceId",
                        method = "PUT",
                        payload = payload
                    )
                }
            }

            // 2. Fallback: 400 Bad Request -> Fallback auf direkte Track-URI
            if (resp.code == 400 && trackUri.isNotBlank()) {
                Log.d(TAG, "PUT /me/player/play gab 400 zurück -> Fallback auf Einzeltitel")
                val fallbackPayload = JSONObject().apply {
                    val arr = JSONArray()
                    arr.put(trackUri)
                    put("uris", arr)
                    put("position_ms", positionMs.coerceAtLeast(0L))
                }.toString()

                val endpoint = if (targetDeviceId != null) {
                    "/me/player/play?device_id=$targetDeviceId"
                } else {
                    "/me/player/play"
                }
                resp = sendRequest(endpoint, method = "PUT", payload = fallbackPayload)
            }

            if (resp.code in 200..204) {
                Log.i(TAG, "Wiedergabe via Web API erfolgreich gestartet!")
                Result.success(true)
            } else {
                Log.w(TAG, "Web API Resume nicht erfolgreich (${resp.code}): ${resp.body}")
                Result.failure(Exception("HTTP ${resp.code}: ${resp.body}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unerwarteter Fehler bei resumePlayback", e)
            Result.failure(e)
        }
    }

    /**
     * Ruft die letzten bis zu 50 gespielten Titel des Nutzers ab (/me/player/recently-played?limit=50).
     */
    suspend fun getRecentlyPlayedTracks(limit: Int = 50): Result<List<HistoryTrackEntry>> {
        return try {
            val clampedLimit = limit.coerceIn(1, 50)
            val resp = sendRequest("/me/player/recently-played?limit=$clampedLimit")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                val items = json.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<HistoryTrackEntry>()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoFallback = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val trackObj = item.optJSONObject("track") ?: continue
                    val contextObj = item.optJSONObject("context")
                    val playedAtStr = item.optString("played_at", "")
                    val timestamp = try {
                        isoFormat.parse(playedAtStr)?.time
                            ?: isoFallback.parse(playedAtStr)?.time
                            ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    val trackUri = trackObj.optString("uri", "")
                    val trackTitle = trackObj.optString("name", "Unbekannter Titel")
                    val durationMs = trackObj.optLong("duration_ms", 0L)

                    // Interpret
                    val artistsArr = trackObj.optJSONArray("artists")
                    val artistNames = mutableListOf<String>()
                    if (artistsArr != null) {
                        for (a in 0 until artistsArr.length()) {
                            val name = artistsArr.optJSONObject(a)?.optString("name") ?: ""
                            if (name.isNotBlank()) artistNames.add(name)
                        }
                    }
                    val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unbekannter Interpret"

                    // Album & Cover
                    var albumName = "Spotify"
                    var coverUrl: String? = null
                    val albumObj = trackObj.optJSONObject("album")
                    if (albumObj != null) {
                        albumName = albumObj.optString("name", albumName)
                        val imagesArr = albumObj.optJSONArray("images")
                        if (imagesArr != null && imagesArr.length() > 0) {
                            coverUrl = imagesArr.optJSONObject(0)?.optString("url")
                        }
                    }

                    val contextUri = contextObj?.optString("uri")
                    val contextType = contextObj?.optString("type")
                    val contextTitle = when {
                        contextType == "playlist" -> "Playlist"
                        contextType == "album" -> albumName
                        else -> "Spotify"
                    }

                    val entryId = "${trackUri}_$timestamp"
                    list.add(
                        HistoryTrackEntry(
                            id = entryId,
                            trackUri = trackUri,
                            trackTitle = trackTitle,
                            artist = artist,
                            album = albumName,
                            contextUri = contextUri,
                            contextTitle = contextTitle,
                            coverUrl = coverUrl,
                            durationMs = durationMs,
                            listenedMs = durationMs,
                            timestamp = timestamp,
                            source = "WebApiHistory"
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${resp.code}: ${resp.body}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei getRecentlyPlayedTracks", e)
            Result.failure(e)
        }
    }

    /**
     * Ruft die Top-Tracks des Nutzers ab (/me/top/tracks).
     */
    suspend fun getTopTracks(timeRange: String = "short_term", limit: Int = 50): Result<List<SpotifyTrack>> {
        return try {
            val clampedLimit = limit.coerceIn(1, 50)
            val resp = sendRequest("/me/top/tracks?time_range=$timeRange&limit=$clampedLimit")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                val items = json.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<SpotifyTrack>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val trackId = item.optString("id", "")
                    val trackName = item.optString("name", "Unbekannter Titel")
                    val trackUri = item.optString("uri", "")
                    val durationMs = item.optLong("duration_ms", 0L)

                    val artistsArr = item.optJSONArray("artists")
                    val artistNames = mutableListOf<String>()
                    if (artistsArr != null) {
                        for (a in 0 until artistsArr.length()) {
                            val n = artistsArr.optJSONObject(a)?.optString("name") ?: ""
                            if (n.isNotBlank()) artistNames.add(n)
                        }
                    }
                    val artistName = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unbekannter Interpret"

                    var albumName = "Spotify"
                    var coverUrl: String? = null
                    val albumObj = item.optJSONObject("album")
                    if (albumObj != null) {
                        albumName = albumObj.optString("name", albumName)
                        val imagesArr = albumObj.optJSONArray("images")
                        if (imagesArr != null && imagesArr.length() > 0) {
                            coverUrl = imagesArr.optJSONObject(0)?.optString("url")
                        }
                    }

                    list.add(
                        SpotifyTrack(
                            id = trackId,
                            name = trackName,
                            uri = trackUri,
                            durationMs = durationMs,
                            artistName = artistName,
                            albumName = albumName,
                            coverUrl = coverUrl
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${resp.code}: ${resp.body}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei getTopTracks", e)
            Result.failure(e)
        }
    }

    /**
     * Ruft die Top-Künstler des Nutzers ab (/me/top/artists).
     */
    suspend fun getTopArtists(timeRange: String = "short_term", limit: Int = 20): Result<List<ArtistStat>> {
        return try {
            val clampedLimit = limit.coerceIn(1, 50)
            val resp = sendRequest("/me/top/artists?time_range=$timeRange&limit=$clampedLimit")
            if (resp.code == 200) {
                val json = JSONObject(resp.body)
                val items = json.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<ArtistStat>()
                val totalCount = items.length().coerceAtLeast(1)
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val name = item.optString("name", "")
                    if (name.isNotBlank()) {
                        val weight = totalCount - i
                        list.add(
                            ArtistStat(
                                name = name,
                                count = weight,
                                percentage = (weight.toFloat() / (totalCount * (totalCount + 1) / 2f)).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${resp.code}: ${resp.body}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei getTopArtists", e)
            Result.failure(e)
        }
    }
}
