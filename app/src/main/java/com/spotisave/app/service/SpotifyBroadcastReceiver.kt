package com.spotisave.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Empfängt native Spotify-Broadcasts:
 * - "com.spotify.music.metadatachanged"
 * - "com.spotify.music.playbackstatechanged"
 * - "com.spotify.music.queuechanged"
 */
class SpotifyBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SpotifyReceiver"

        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"

        // Spotify Broadcast Extras
        const val EXTRA_ID = "id"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_TRACK = "track"
        const val EXTRA_LENGTH = "length"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_PLAYBACK_POSITION = "playbackPosition"
        const val EXTRA_TIME_SENT = "timeSent"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action == null) return

        val action = intent.action
        val timeSent = intent.getLongExtra(EXTRA_TIME_SENT, System.currentTimeMillis())
        Log.d(TAG, "Spotify Broadcast empfangen: $action (Zeit: $timeSent)")

        when (action) {
            ACTION_METADATA_CHANGED -> {
                val trackUri = intent.getStringExtra(EXTRA_ID)
                val trackTitle = intent.getStringExtra(EXTRA_TRACK)
                val artist = intent.getStringExtra(EXTRA_ARTIST)
                val album = intent.getStringExtra(EXTRA_ALBUM)
                val lengthMs = intent.getIntExtra(EXTRA_LENGTH, 0).toLong().takeIf { it > 0L }
                    ?: intent.getLongExtra(EXTRA_LENGTH, 0L)
                val contextUri = intent.getStringExtra("context_uri")
                    ?: intent.getStringExtra("context")
                    ?: intent.getStringExtra("com.spotify.music.extra.CONTEXT_URI")
                val contextTitle = intent.getStringExtra("context_title")
                    ?: intent.getStringExtra("context_name")
                    ?: intent.getStringExtra("com.spotify.music.extra.CONTEXT_TITLE")

                PlaybackTracker.onMetadataChanged(
                    context = context,
                    trackUri = trackUri,
                    trackTitle = trackTitle,
                    artist = artist,
                    album = album,
                    durationMs = lengthMs,
                    contextUri = contextUri,
                    contextTitle = contextTitle,
                    source = "Broadcast"
                )
            }

            ACTION_PLAYBACK_STATE_CHANGED -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
                val positionMs = intent.getIntExtra(EXTRA_PLAYBACK_POSITION, -1).takeIf { it >= 0 }?.toLong()
                    ?: intent.getLongExtra(EXTRA_PLAYBACK_POSITION, 0L)
                val trackUri = intent.getStringExtra(EXTRA_ID)

                PlaybackTracker.onPlaybackStateChanged(
                    context = context,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    source = "Broadcast",
                    trackUri = trackUri
                )
            }

            ACTION_QUEUE_CHANGED -> {
                Log.d(TAG, "Spotify Warteschlange geändert")
            }
        }
    }
}
