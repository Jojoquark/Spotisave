package com.spotisave.app.data.model

/**
 * Aktueller Live-Wiedergabestatus von Spotify (aus BroadcastReceiver oder MediaSession).
 */
data class PlaybackStateModel(
    val trackUri: String,
    val trackTitle: String,
    val artist: String,
    val album: String = "",
    val contextTitle: String? = null,
    val contextUri: String? = null,
    val coverUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Broadcast" // "Broadcast" oder "MediaSession"
) {
    val isCollection: Boolean
        get() = contextUri?.startsWith("spotify:collection") == true ||
                contextUri?.contains(":collection") == true ||
                contextTitle?.equals("Lieblingssongs", ignoreCase = true) == true ||
                contextTitle?.equals("Liked Songs", ignoreCase = true) == true

    val isPlaylist: Boolean
        get() = contextUri?.startsWith("spotify:playlist:") == true || isCollection

    val isAlbum: Boolean
        get() = !isCollection && (contextUri?.startsWith("spotify:album:") == true)

    val displayContextTitle: String
        get() = when {
            isCollection -> if (!contextTitle.isNullOrBlank() && contextTitle != "Spotify") contextTitle else "Lieblingssongs"
            !contextTitle.isNullOrBlank() && contextTitle != "Spotify" -> contextTitle
            isPlaylist -> "Playlist"
            album.isNotBlank() && album != "Spotify" -> album
            else -> ""
        }

    val progressFraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
