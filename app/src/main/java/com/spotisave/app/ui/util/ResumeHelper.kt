package com.spotisave.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.service.SpotifyNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ResumeHelper {

    private const val TAG = "ResumeHelper"

    /**
     * Führt die 1-Click-Wiederaufnahme aus:
     * 1. Steuert via MediaController (falls aktiv) die Hörposition direkt an ohne Reset auf 0:00.
     * 2. Öffnet die native Spotify-App per Intent (spotify:track:... bzw. spotify:playlist:...).
     * 3. Fallback auf Spotify Web Player, falls die native App nicht erreichbar ist.
     */
    fun resumeBookmark(context: Context, bookmark: Bookmark) {
        Log.i(TAG, "1-Click Resume für '${bookmark.trackTitle}' an Position ${bookmark.positionMs} ms")

        val mediaController = SpotifyNotificationListenerService.currentMediaController
        val currentTrackUri = mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)

        val isAlreadyCurrentTrack = mediaController != null && (currentTrackUri == bookmark.trackUri)

        if (isAlreadyCurrentTrack && mediaController != null) {
            // Direkt im laufenden Player anspringen ohne Re-Initialization
            try {
                mediaController.transportControls.seekTo(bookmark.positionMs)
                mediaController.transportControls.play()
                Log.d(TAG, "MediaController seekTo & play direkt ausgeführt")
            } catch (e: Exception) {
                Log.w(TAG, "MediaController seekTo fehlgeschlagen: ${e.message}")
            }

            // Spotify im Vordergrund öffnen ohne den Titel zurückzusetzen
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    Toast.makeText(
                        context,
                        "Spotify wird fortgesetzt bei ${TimeFormatter.formatTime(bookmark.positionMs)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Konnte Spotify nicht per LaunchIntent öffnen: ${e.message}")
                }
            }
        }

        // Primärer nativer Intent an Spotify App (für Playlist oder abweichenden Track)
        val targetUriString = if (!bookmark.contextUri.isNullOrBlank() && bookmark.contextUri.startsWith("spotify:")) {
            bookmark.contextUri
        } else {
            bookmark.trackUri
        }

        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUriString)).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(spotifyIntent)

            // Falls MediaController gekoppelt ist, verzögert an exakte Hörposition springen sobald geladen
            if (bookmark.positionMs > 0L) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(700L)
                    try {
                        SpotifyNotificationListenerService.currentMediaController?.transportControls?.seekTo(bookmark.positionMs)
                        SpotifyNotificationListenerService.currentMediaController?.transportControls?.play()
                    } catch (_: Exception) {}
                }
            }

            Toast.makeText(
                context,
                "Spotify wird fortgesetzt bei ${TimeFormatter.formatTime(bookmark.positionMs)}",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Spotify App nicht installiert oder Paket nicht auflösbar. Starte Web-Fallback.")
            val trackId = bookmark.trackUri.removePrefix("spotify:track:")
            val webUrl = if (trackId.isNotBlank() && !trackId.startsWith("http")) {
                "https://open.spotify.com/track/$trackId"
            } else {
                "https://open.spotify.com"
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Konnte Spotify nicht öffnen", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Starten von Spotify", e)
            Toast.makeText(context, "Fehler beim Öffnen von Spotify", Toast.LENGTH_SHORT).show()
        }
    }
}
