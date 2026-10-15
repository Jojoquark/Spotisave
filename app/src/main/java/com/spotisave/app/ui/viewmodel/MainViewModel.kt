package com.spotisave.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotisave.app.SpotisaveApp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.data.repository.BookmarkRepository
import com.spotisave.app.service.SpotifyForegroundService
import com.spotisave.app.service.SpotifyNotificationListenerService
import com.spotisave.app.ui.util.ResumeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookmarkRepository.initialize(application)

    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks
    val activePlayback: StateFlow<PlaybackStateModel?> = repository.activePlayback
    val isServiceRunning: StateFlow<Boolean> = SpotifyForegroundService.isRunning

    private val _isNotificationListenerGranted = MutableStateFlow(
        SpotifyNotificationListenerService.isPermissionGranted(application)
    )
    val isNotificationListenerGranted: StateFlow<Boolean> = _isNotificationListenerGranted.asStateFlow()

    fun checkPermissions() {
        _isNotificationListenerGranted.value =
            SpotifyNotificationListenerService.isPermissionGranted(getApplication())
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
}
