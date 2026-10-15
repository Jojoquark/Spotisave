package com.spotisave.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.spotisave.app.data.api.SpotifyApiClient
import com.spotisave.app.data.api.SpotifyAuthManager
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.service.SpotifyNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ResumeHelper {

    private const val TAG = "ResumeHelper"

    /**
     * Führt die 1-Click-Wiederaufnahme aus (Hybrid-Modus):
     * 1. Falls Spotify Web API autorisiert ist: PUT /me/player/play mit context_uri + offset + position_ms.
     * 2. Falls MediaController aktiv ist: Direktes seekTo & play im laufenden Player ohne Reset auf 0:00.
     * 3. Primärer nativer Intent an Spotify App (spotify:playlist:... bzw. spotify:track:...).
     * 4. Robuster Fallback auf Spotify Web Player, falls die App nicht auflösbar ist.
     */
    fun resumeBookmark(context: Context, bookmark: Bookmark) {
        Log.i(TAG, "1-Click Resume für '${bookmark.trackTitle}' an Position ${bookmark.positionMs} ms")

        val authManager = SpotifyAuthManager.getInstance(context)
        if (authManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val apiClient = SpotifyApiClient.getInstance(context)
                    val result = withContext(Dispatchers.IO) {
                        apiClient.resumePlayback(
                            contextUri = bookmark.contextUri,
                            trackUri = bookmark.trackUri,
                            positionMs = bookmark.positionMs
                        )
                    }

                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            "Wiedergabe via Spotify API fortgesetzt bei ${TimeFormatter.formatTime(bookmark.positionMs)}",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Optional: Spotify-App in den Vordergrund bringen
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(launchIntent) } catch (_: Exception) {}
                        }
                        return@launch
                    }

                    // Falls Web API fehlschlägt (z.B. Free User oder kein aktives Gerät): nativer Fallback
                    Log.w(TAG, "Web API Resume fehlgeschlagen, nutze nativen Intent-Fallback")
                    executeNativeResume(context, bookmark)
                } catch (e: Exception) {
                    Log.w(TAG, "Web API Ausnahme beim Resume, nutze nativen Fallback: ${e.message}")
                    executeNativeResume(context, bookmark)
                }
            }
        } else {
            // Nicht mit Web API angemeldet -> Sofort nativer Modus
            executeNativeResume(context, bookmark)
        }
    }

    /**
     * Nativer Intent & MediaController Fallback ohne Web API Abhängigkeit.
     */
    fun executeNativeResume(context: Context, bookmark: Bookmark) {
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
