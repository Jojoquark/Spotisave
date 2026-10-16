package com.spotisave.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Repräsentiert eine gespeicherte Spotify-Hörposition (Bookmark).
 * Ermöglicht 1-Click-Wiederaufnahme von Playlisten, Alben oder Tracks
 * an genau der Position (in Millisekunden), an der pausiert oder gehört wurde.
 */
data class Bookmark(
    val id: String,                         // Eindeutige ID (meist trackUri oder contextUri)
    val contextUri: String? = null,         // z.B. spotify:playlist:... oder spotify:album:...
    val contextTitle: String = "Spotify",   // Playlist- oder Albumtitel
    val trackTitle: String,                 // Titel des Tracks / Hörbuchkapitels
    val artist: String,                     // Interpret / Autor / Sprecher
    val trackUri: String,                   // Spotify Track URI (z.B. spotify:track:...)
    val coverUrl: String? = null,           // Cover-Bild URL falls verfügbar
    val positionMs: Long,                   // Gespeicherte Hörposition in Millisekunden
    val durationMs: Long,                   // Gesamtdauer des Titels in Millisekunden
    val timestamp: Long = System.currentTimeMillis(), // Zeitstempel der letzten Erfassung
    val source: String = "Spotify"          // "Broadcast" | "MediaSession" | "Manuell"
) {
    val isPlaylist: Boolean
        get() = id.startsWith("spotify:playlist:") || contextUri?.startsWith("spotify:playlist:") == true

    val isAlbum: Boolean
        get() = id.startsWith("album:") || contextUri?.startsWith("spotify:album:") == true

    val displayContextTitle: String
        get() = when {
            contextTitle.isNotBlank() && contextTitle != "Spotify" -> contextTitle
            isPlaylist -> "Playlist"
            isAlbum -> "Album"
            else -> ""
        }

    /**
     * Berechnet den relativen Fortschritt (0.0f bis 1.0f) für Progress-Bars.
     */
    val progressFraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            if (!contextUri.isNullOrBlank()) put("contextUri", contextUri)
            put("contextTitle", contextTitle)
            put("trackTitle", trackTitle)
            put("artist", artist)
            put("trackUri", trackUri)
            if (!coverUrl.isNullOrBlank()) put("coverUrl", coverUrl)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
            put("timestamp", timestamp)
            put("source", source)
        }
    }

    companion object {
        fun exportBookmarksToJson(bookmarks: List<Bookmark>): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())
            val array = JSONArray()
            for (bm in bookmarks) {
                array.put(bm.toJsonObject())
            }
            root.put("bookmarks", array)
            return root.toString()
        }

        fun importBookmarksFromJson(jsonString: String): List<Bookmark> {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return emptyList()

            val result = mutableListOf<Bookmark>()
            try {
                val entries = mutableListOf<Pair<String?, JSONObject>>()
                when {
                    trimmed.startsWith("[") -> {
                        val arr = JSONArray(trimmed)
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i)
                            if (item != null) entries.add(null to item)
                        }
                    }
                    trimmed.startsWith("{") -> {
                        val obj = JSONObject(trimmed)
                        if (obj.has("bookmarks") && obj.opt("bookmarks") is JSONArray) {
                            val arr = obj.getJSONArray("bookmarks")
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i)
                                if (item != null) entries.add(null to item)
                            }
                        } else if (
                            (obj.has("id") || obj.has("playlist_id")) &&
                            (obj.has("trackTitle") || obj.has("track_name") || obj.has("trackUri") || obj.has("track_uri"))
                        ) {
                            entries.add(null to obj)
                        } else {
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val child = obj.optJSONObject(k)
                                if (child != null) entries.add(k to child)
                            }
                        }
                    }
                    else -> return emptyList()
                }

                for ((fallbackKey, item) in entries) {
                    val rawId = item.optString("id", item.optString("playlist_id", fallbackKey ?: "")).trim()
                    val trackTitle = item.optString("trackTitle", item.optString("track_name", "")).trim()
                    val artist = item.optString("artist", item.optString("artist_name", "")).trim()
                    val trackUri = item.optString("trackUri", item.optString("track_uri", "")).trim()

                    val rawContextUri = item.optString("contextUri", item.optString("context_uri", "")).trim()
                    val contextUri = if (rawContextUri.isEmpty() || rawContextUri == "null") null else rawContextUri

                    // Normalize web-app raw playlist IDs to canonical spotify:playlist:<id> so future playback updates in-place
                    val id = when {
                        rawId.isEmpty() && !contextUri.isNullOrBlank() -> contextUri
                        contextUri != null && contextUri.startsWith("spotify:playlist:") &&
                            rawId == contextUri.removePrefix("spotify:playlist:") -> contextUri
                        else -> rawId
                    }

                    if (id.isEmpty() || trackTitle.isEmpty() || trackUri.isEmpty()) continue

                    val contextTitle = item.optString("contextTitle", item.optString("playlist_name", "Spotify")).trim()
                        .ifEmpty { "Spotify" }

                    val rawCoverUrl = item.optString(
                        "coverUrl",
                        item.optString("cover_url", item.optString("playlist_image", ""))
                    ).trim()
                    val coverUrl = if (rawCoverUrl.isEmpty() || rawCoverUrl == "null") null else rawCoverUrl

                    val positionMs = item.optLong("positionMs", item.optLong("progress_ms", 0L)).coerceAtLeast(0L)
                    val durationMs = item.optLong("durationMs", item.optLong("duration_ms", 0L)).coerceAtLeast(0L)
                    val timestamp = item.optLong("timestamp", item.optLong("updated_at", System.currentTimeMillis()))
                    val source = item.optString("source", "Backup").trim().ifEmpty { "Backup" }

                    result.add(
                        Bookmark(
                            id = id,
                            contextUri = contextUri,
                            contextTitle = contextTitle,
                            trackTitle = trackTitle,
                            artist = artist.ifEmpty { "Unbekannter Künstler" },
                            trackUri = trackUri,
                            coverUrl = coverUrl,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
                            source = source
                        )
                    )
                }
            } catch (e: Exception) {
                return emptyList()
            }
            return result
        }
    }
}
