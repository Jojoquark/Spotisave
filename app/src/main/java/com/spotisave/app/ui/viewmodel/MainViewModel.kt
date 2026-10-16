package com.spotisave.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotisave.app.SpotisaveApp
import com.spotisave.app.data.api.SpotifyApiClient
import com.spotisave.app.data.api.SpotifyAuthManager
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.repository.BookmarkRepository
import com.spotisave.app.data.stats.ListeningStats
import com.spotisave.app.data.stats.StatisticsEngine
import com.spotisave.app.service.PlaybackTracker
import com.spotisave.app.service.SpotifyForegroundService
import com.spotisave.app.service.SpotifyNotificationListenerService
import com.spotisave.app.ui.util.ResumeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookmarkRepository.initialize(application)
    private val authManager = SpotifyAuthManager.getInstance(application)
    private val apiClient = SpotifyApiClient.getInstance(application)

    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks
    val listeningHistory: StateFlow<List<HistoryTrackEntry>> = repository.listeningHistory
    val activePlayback: StateFlow<PlaybackStateModel?> = repository.activePlayback
    val isServiceRunning: StateFlow<Boolean> = SpotifyForegroundService.isRunning

    // Tab Navigation: 0 = Playlists, 1 = Statistiken
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Spotify PKCE OAuth State
    private val _isSpotifyLoggedIn = MutableStateFlow(authManager.isLoggedIn())
    val isSpotifyLoggedIn: StateFlow<Boolean> = _isSpotifyLoggedIn.asStateFlow()

    private val _spotifyClientId = MutableStateFlow(authManager.getClientId() ?: "")
    val spotifyClientId: StateFlow<String> = _spotifyClientId.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    val listeningStats: StateFlow<ListeningStats> = combine(
        bookmarks,
        repository.listeningHistory,
        activePlayback
    ) { bmList, histList, playback ->
        StatisticsEngine.calculateStats(
            bookmarks = bmList,
            history = histList,
            activePlayback = playback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsEngine.calculateStats(emptyList(), emptyList(), null)
    )

    private val _isNotificationListenerGranted = MutableStateFlow(
        SpotifyNotificationListenerService.isPermissionGranted(application)
    )
    val isNotificationListenerGranted: StateFlow<Boolean> = _isNotificationListenerGranted.asStateFlow()

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun checkPermissions() {
        _isNotificationListenerGranted.value =
            SpotifyNotificationListenerService.isPermissionGranted(getApplication())
        _isSpotifyLoggedIn.value = authManager.isLoggedIn()
        _spotifyClientId.value = authManager.getClientId() ?: ""
    }

    fun toggleService(context: Context, enable: Boolean) {
        SpotisaveApp.setAutoTrackingEnabled(context, enable)
        if (enable) {
            SpotifyForegroundService.start(context)
        } else {
            SpotifyForegroundService.stop(context)
        }
    }

    fun resumeBookmark(context: Context, bookmark: Bookmark) {
        ResumeHelper.resumeBookmark(context, bookmark)
    }

    fun deleteBookmark(bookmark: Bookmark) {
        repository.deleteBookmark(bookmark.id)
    }

    fun exportBookmarksToJson(): String {
        return repository.exportBookmarksJson()
    }

    fun importBookmarksFromJson(jsonString: String, onComplete: (Int) -> Unit = {}) {
        repository.importBookmarksJson(jsonString) { count ->
            onComplete(count)
            if (count > 0) {
                resolveMissingPlaylistTitles()
            }
        }
    }

    fun setClientId(clientId: String) {
        _spotifyClientId.value = clientId.trim()
        authManager.saveClientId(clientId.trim())
    }

    fun startAuthFlow(context: Context, clientId: String) {
        if (clientId.isBlank()) {
            Toast.makeText(context, "Bitte gib deine Spotify Client ID ein", Toast.LENGTH_SHORT).show()
            return
        }
        setClientId(clientId)
        val authUrl = authManager.prepareAuthorization(clientId)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Konnte Browser nicht öffnen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    init {
        viewModelScope.launch {
            bookmarks.collect { list ->
                if (list.any { it.isPlaylist && (it.contextTitle.isBlank() || it.contextTitle == "Spotify" || it.contextTitle == "Playlist") }) {
                    resolveMissingPlaylistTitles()
                }
            }
        }
        if (authManager.isLoggedIn()) {
            syncSpotifyHistory()
        }
    }

    fun handleAuthCode(code: String) {
        viewModelScope.launch {
            _authStatusMessage.value = "Token-Austausch läuft..."
            val result = authManager.exchangeCode(code)
            if (result.isSuccess) {
                _isSpotifyLoggedIn.value = true
                _authStatusMessage.value = "Erfolgreich mit Spotify verbunden!"
                refreshNowPlayingFromApi()
                resolveMissingPlaylistTitles()
                syncSpotifyHistory()
            } else {
                _authStatusMessage.value = "Fehler: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun handleAuthError(error: String) {
        _authStatusMessage.value = "Anmeldung abgebrochen: $error"
    }

    fun logoutSpotify() {
        authManager.clearTokens()
        _isSpotifyLoggedIn.value = false
        _authStatusMessage.value = "Spotify-Konto getrennt (Lokal-Modus aktiv)"
    }

    fun refreshNowPlayingFromApi() {
        if (!authManager.isLoggedIn()) return
        viewModelScope.launch {
            try {
                val stateResult = apiClient.getNowPlaying()
                val state = stateResult.getOrNull()
                if (state != null && state.track != null) {
                    val track = state.track
                    val app = getApplication<Application>()
                    var resolvedContextTitle: String? = null

                    if (!state.contextUri.isNullOrBlank() && state.contextUri.startsWith("spotify:playlist:")) {
                        val plId = state.contextUri.removePrefix("spotify:playlist:")
                        val plInfo = apiClient.getPlaylistInfo(plId).getOrNull()
                        if (plInfo != null) {
                            resolvedContextTitle = plInfo.name
                        }
                    }

                    PlaybackTracker.onMetadataChanged(
                        context = app,
                        trackUri = track.uri,
                        trackTitle = track.name,
                        artist = track.artistName,
                        album = track.albumName,
                        durationMs = track.durationMs,
                        contextUri = state.contextUri,
                        contextTitle = resolvedContextTitle,
                        coverUrl = track.coverUrl,
                        source = "SpotifyWebApi"
                    )
                    PlaybackTracker.onPlaybackStateChanged(
                        context = app,
                        isPlaying = state.isPlaying,
                        positionMs = state.progressMs,
                        source = "SpotifyWebApi"
                    )
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "API Fetch Now Playing Fehler: ${e.message}")
            }
        }
    }

    fun resolveMissingPlaylistTitles() {
        if (!authManager.isLoggedIn()) return
        viewModelScope.launch {
            try {
                val currentBookmarks = bookmarks.value
                for (bm in currentBookmarks) {
                    val uri = bm.contextUri
                    if (!uri.isNullOrBlank() && uri.startsWith("spotify:playlist:") &&
                        (bm.contextTitle.isBlank() || bm.contextTitle == "Spotify" || bm.contextTitle == "Playlist")
                    ) {
                        val plId = uri.removePrefix("spotify:playlist:")
                        val plInfo = apiClient.getPlaylistInfo(plId).getOrNull()
                        if (plInfo != null && plInfo.name.isNotBlank()) {
                            repository.updateBookmarkContextTitle(bm.id, plInfo.name, plInfo.coverUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Fehler bei resolveMissingPlaylistTitles: ${e.message}")
            }
        }
    }

    /**
     * Synchronisiert den Spotify-Hörverlauf (/me/player/recently-played) und speichert neue Tracks in der History DB.
     */
    fun syncSpotifyHistory() {
        if (!authManager.isLoggedIn()) return
        viewModelScope.launch {
            try {
                val result = apiClient.getRecentlyPlayedTracks(50)
                val tracks = result.getOrNull()
                if (!tracks.isNullOrEmpty()) {
                    for (t in tracks) {
                        repository.recordHistoryEntry(t)
                    }
                    Log.i("MainViewModel", "Spotify-History synchronisiert: ${tracks.size} Titel aktualisiert")
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Fehler bei syncSpotifyHistory: ${e.message}")
            }
        }
    }
}
