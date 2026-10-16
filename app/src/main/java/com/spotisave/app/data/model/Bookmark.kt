package com.spotisave.app.data.model

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
}
