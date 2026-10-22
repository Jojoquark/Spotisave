package com.spotisave.app.data.model

import org.json.JSONObject

/**
 * Repräsentiert einen einzelnen persistierten Spotify-Hörverlaufseintrag.
 * Im Gegensatz zu Bookmarks (die pro Playlist den letzten Stand speichern)
 * wird in der Listening-History jeder gespielte Track, jedes Weiterhören und
 * jede Session dauerhaft akkumuliert und ausgewertet.
 */
data class HistoryTrackEntry(
    val id: String,                         // Eindeutige ID (z.B. "${trackUri}_${timestamp}")
    val trackUri: String,                   // Spotify Track URI (z.B. spotify:track:...)
    val trackTitle: String,                 // Titel des Tracks
    val artist: String,                     // Interpret(en)
    val album: String = "Spotify",          // Albumname
    val contextUri: String? = null,         // z.B. spotify:playlist:... oder spotify:album:...
    val contextTitle: String = "Spotify",   // Playlist- oder Albumtitel
    val coverUrl: String? = null,           // Cover-Bild URL
    val durationMs: Long = 0L,              // Gesamtdauer des Titels in ms
    val listenedMs: Long = 0L,              // Tatsächlich gehörte Zeit in ms
    val timestamp: Long = System.currentTimeMillis(), // Zeitpunkt des Hörens
    val source: String = "Spotify"          // "Broadcast" | "MediaSession" | "WebApi"
) {
    val isCollection: Boolean
        get() = contextUri?.startsWith("spotify:collection") == true ||
                contextUri?.contains(":collection") == true ||
                contextTitle.equals("Lieblingssongs", ignoreCase = true) ||
                contextTitle.equals("Liked Songs", ignoreCase = true)

    val isPlaylist: Boolean
        get() = contextUri?.startsWith("spotify:playlist:") == true || isCollection

    val isAlbum: Boolean
        get() = !isCollection && (contextUri?.startsWith("spotify:album:") == true)

    /**
     * Konvertiert den Verlaufseintrag in ein startbares Bookmark für 1-Click Resume.
     */
    fun toBookmark(): Bookmark {
        val isCollectionEntry = isCollection
        val hasDistinctPlaylistTitle = contextTitle.isNotBlank() &&
            contextTitle != "Spotify" &&
            !contextTitle.equals(album, ignoreCase = true) &&
            !contextTitle.equals(trackTitle, ignoreCase = true)
        val effectiveContextUri = when {
            isCollectionEntry -> contextUri ?: "spotify:collection:tracks"
            !contextUri.isNullOrBlank() -> contextUri
            hasDistinctPlaylistTitle -> {
                val slug = contextTitle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
                "spotify:playlist:local_$slug"
            }
            else -> null
        }
        val bmId = when {
            isCollectionEntry -> effectiveContextUri ?: "spotify:collection:tracks"
            !effectiveContextUri.isNullOrBlank() -> effectiveContextUri
            album.isNotBlank() && album != "Spotify" && contextTitle.equals(album, ignoreCase = true) -> {
                val normalized = album.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
                "album:$normalized"
            }
            else -> trackUri
        }
        return Bookmark(
            id = bmId,
            contextUri = effectiveContextUri,
            contextTitle = contextTitle,
            trackTitle = trackTitle,
            artist = artist,
            trackUri = trackUri,
            coverUrl = coverUrl,
            positionMs = listenedMs.coerceIn(0L, durationMs.takeIf { it > 0 } ?: listenedMs),
            durationMs = durationMs,
            timestamp = timestamp,
            source = source
        )
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("trackUri", trackUri)
            put("trackTitle", trackTitle)
            put("artist", artist)
            put("album", album)
            if (!contextUri.isNullOrBlank()) put("contextUri", contextUri)
            put("contextTitle", contextTitle)
            if (!coverUrl.isNullOrBlank()) put("coverUrl", coverUrl)
            put("durationMs", durationMs)
            put("listenedMs", listenedMs)
            put("timestamp", timestamp)
            put("source", source)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): HistoryTrackEntry {
            val trackUri = obj.optString("trackUri", obj.optString("track_uri", ""))
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val id = obj.optString("id", "${trackUri}_$timestamp")
            return HistoryTrackEntry(
                id = id,
                trackUri = trackUri,
                trackTitle = obj.optString("trackTitle", obj.optString("track_title", "Unbekannter Titel")),
                artist = obj.optString("artist", "Unbekannter Interpret"),
                album = obj.optString("album", "Spotify"),
                contextUri = obj.optString("contextUri", obj.optString("context_uri", null)),
                contextTitle = obj.optString("contextTitle", obj.optString("context_title", "Spotify")),
                coverUrl = obj.optString("coverUrl", obj.optString("cover_url", null)),
                durationMs = obj.optLong("durationMs", obj.optLong("duration_ms", 0L)),
                listenedMs = obj.optLong("listenedMs", obj.optLong("listened_ms", 0L)),
                timestamp = timestamp,
                source = obj.optString("source", "Spotify")
            )
        }
    }
}
