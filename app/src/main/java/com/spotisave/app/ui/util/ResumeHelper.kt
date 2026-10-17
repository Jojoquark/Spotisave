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
import com.spotisave.app.service.PlaybackTracker
import com.spotisave.app.service.SpotifyNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ResumeHelper {

    private const val TAG = "ResumeHelper"

    /**
     * Bestimmt die optimale Ziel-URI für einen nativen Android ACTION_VIEW Intent:
     * Bevorzugt die konkrete Track-/Episoden-URI gegenüber einer Playlist-URI,
     * da Spotify bei einer reinen Playlist-URI lediglich die Playlist-Übersicht öffnet
     * statt den gespeicherten Song abzuspielen. Der Playlist-Kontext bleibt über
     * PlaybackTracker.onResumeInitiated vollständig erhalten.
     */
    fun resolveNativeTargetUri(bookmark: Bookmark): String {
        val hasValidTrackUri = bookmark.trackUri.isNotBlank() &&
            bookmark.trackUri != "spotify:track:active" &&
            bookmark.trackUri != "spotify:track:notification" &&
            (bookmark.trackUri.startsWith("spotify:track:") || bookmark.trackUri.startsWith("spotify:episode:"))

        if (hasValidTrackUri) {
            return bookmark.trackUri
        }
        val ctx = bookmark.contextUri
        if (!ctx.isNullOrBlank() && ctx.startsWith("spotify:") && !ctx.startsWith("spotify:playlist:local_")) {
            return ctx
        }
        return bookmark.trackUri
    }

    /**
     * Führt die 1-Click-Wiederaufnahme aus (Hybrid-Modus):
     * 1. Sichert zuerst evtl. laufende andere Playlist und aktiviert das Resume-Schutzfenster.
     * 2. Falls Spotify Web API autorisiert ist: PUT /me/player/play mit context_uri + offset + position_ms.
     * 3. Falls MediaController bereits auf demselben Song steht: Direktes seekTo & play ohne Reset auf 0:00.
     * 4. Primärer nativer Intent an Spotify App mit mehrstufiger Seek-Verifikation.
     * 5. Robuster Fallback auf Spotify Web Player, falls die App nicht auflösbar ist.
     */
    fun resumeBookmark(context: Context, bookmark: Bookmark) {
        Log.i(TAG, "1-Click Resume für '${bookmark.trackTitle}' (${bookmark.id}) an Position ${bookmark.positionMs} ms")

        // 0. Aktuellen Stand einer evtl. laufenden anderen Playlist sichern & Schutzfenster aktivieren
        PlaybackTracker.onResumeInitiated(context, bookmark)

        val authManager = SpotifyAuthManager.getInstance(context)
        if (authManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val apiClient = SpotifyApiClient.getInstance(context)
                    var result = withContext(Dispatchers.IO) {
                        apiClient.resumePlayback(
                            contextUri = bookmark.contextUri,
                            trackUri = bookmark.trackUri,
                            positionMs = bookmark.positionMs
                        )
                    }

                    // Falls Spotify auf dem Smartphone im Ruhezustand war (404 No Active Device),
                    // lokalen Player kurz aufwecken und Web API erneut versuchen
                    if (result.isFailure) {
                        try {
                            SpotifyNotificationListenerService.currentMediaController?.transportControls?.play()
                        } catch (_: Exception) {}
                        delay(750L)
                        result = withContext(Dispatchers.IO) {
                            apiClient.resumePlayback(
                                contextUri = bookmark.contextUri,
                                trackUri = bookmark.trackUri,
                                positionMs = bookmark.positionMs
                            )
                        }
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

                        // Sicherstellen, dass die Zielposition auch nach dem Track-Load eingehalten wird
                        if (bookmark.positionMs > 0L) {
                            ensureSeekPosition(bookmark, apiClient)
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
     * Überprüft mehrstufig, ob der Ziel-Track im MediaController geladen ist, und setzt
     * die exakte Hörposition (positionMs), falls Spotify den Titel bei 0:00 gestartet hat.
     */
    private fun ensureSeekPosition(bookmark: Bookmark, apiClient: SpotifyApiClient? = null) {
        if (bookmark.positionMs <= 0L) return
        CoroutineScope(Dispatchers.Main).launch {
            val hasCanonicalTrackUri = bookmark.trackUri.isNotBlank() &&
                bookmark.trackUri != "spotify:track:active" &&
                bookmark.trackUri != "spotify:track:notification"

            for (attempt in 1..12) {
                delay(380L)
                val controller = SpotifyNotificationListenerService.currentMediaController
                if (controller != null) {
                    val meta = controller.metadata
                    val currentUri = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                        ?: meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                    val currentTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)

                    val trackMatches = (hasCanonicalTrackUri && currentUri == bookmark.trackUri) ||
                        (!currentTitle.isNullOrBlank() && currentTitle.equals(bookmark.trackTitle, ignoreCase = true))

                    if (trackMatches) {
                        val livePos = SpotifyNotificationListenerService.getActivePlaybackPosition()
                        if (livePos < bookmark.positionMs - 3000L || livePos > bookmark.positionMs + 15_000L) {
                            try {
                                controller.transportControls.seekTo(bookmark.positionMs)
                                controller.transportControls.play()
                                Log.d(TAG, "ensureSeekPosition (Versuch $attempt): MediaController seekTo(${bookmark.positionMs})")
                            } catch (_: Exception) {}

                            if (apiClient != null && attempt >= 2) {
                                withContext(Dispatchers.IO) {
                                    apiClient.seekToPosition(bookmark.positionMs)
                                }
                            }
                        } else {
                            // Zielposition erfolgreich erreicht!
                            try {
                                controller.transportControls.play()
                            } catch (_: Exception) {}
                            return@launch
                        }
                    }
                } else if (apiClient != null && attempt == 3) {
                    withContext(Dispatchers.IO) {
                        apiClient.seekToPosition(bookmark.positionMs)
                    }
                    return@launch
                }
            }
        }
    }

    /**
     * Nativer Intent & MediaController Fallback ohne Web API Abhängigkeit.
     */
    fun executeNativeResume(context: Context, bookmark: Bookmark) {
        val mediaController = SpotifyNotificationListenerService.currentMediaController
        val currentTrackUri = mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val currentTrackTitle = mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

        val hasCanonicalTrackUri = bookmark.trackUri.isNotBlank() &&
            bookmark.trackUri != "spotify:track:active" &&
            bookmark.trackUri != "spotify:track:notification"

        val isAlreadyCurrentTrack = mediaController != null && (
            (hasCanonicalTrackUri && currentTrackUri == bookmark.trackUri) ||
                (!currentTrackTitle.isNullOrBlank() && currentTrackTitle.equals(bookmark.trackTitle, ignoreCase = true))
            )

        if (isAlreadyCurrentTrack && mediaController != null) {
            // Direkt im laufenden Player anspringen ohne Re-Initialization
            try {
                mediaController.transportControls.seekTo(bookmark.positionMs)
                mediaController.transportControls.play()
                Log.d(TAG, "MediaController seekTo & play direkt ausgeführt")
            } catch (e: Exception) {
                Log.w(TAG, "MediaController seekTo fehlgeschlagen: ${e.message}")
            }

            if (bookmark.positionMs > 0L) {
                ensureSeekPosition(bookmark, null)
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

        // Primärer nativer Intent an Spotify App:
        // Startet gezielt den gespeicherten Track (resolveNativeTargetUri), damit Spotify nicht bloß
        // die Playlist-Übersicht öffnet, und springt anschließend via ensureSeekPosition an positionMs.
        val targetUriString = resolveNativeTargetUri(bookmark)

        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUriString)).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(spotifyIntent)

            // Sobald der Ziel-Track in der MediaSession geladen ist, an die exakte Hörposition springen
            if (bookmark.positionMs > 0L) {
                ensureSeekPosition(bookmark, null)
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
