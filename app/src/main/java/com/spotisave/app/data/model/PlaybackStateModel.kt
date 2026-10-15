package com.spotisave.app.data.model

/**
 * Aktueller Live-Wiedergabestatus von Spotify (aus BroadcastReceiver oder MediaSession).
 */
data class PlaybackStateModel(
    val trackUri: String,
    val trackTitle: String,
    val artist: String,
    val album: String = "",
    val contextUri: String? = null,
    val coverUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Broadcast" // "Broadcast" oder "MediaSession"
) {
    val progressFraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
