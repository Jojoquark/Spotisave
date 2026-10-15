package com.spotisave.app.data.repository

import android.content.Context
import com.spotisave.app.data.db.BookmarkDatabaseHelper
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zentrales Repository für Lese- und Schreibzugriffe auf Lesezeichen und den Live-Wiedergabestatus.
 * Stellt reaktive StateFlows für Jetpack Compose bereit und synchronisiert mit SQLite.
 */
class BookmarkRepository private constructor(context: Context) {

    private val dbHelper = BookmarkDatabaseHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackStateModel?>(null)
    val activePlayback: StateFlow<PlaybackStateModel?> = _activePlayback.asStateFlow()

    init {
        reloadBookmarks()
    }

    fun reloadBookmarks() {
        scope.launch {
            val list = dbHelper.getAllBookmarks()
            _bookmarks.value = list
        }
    }

    fun saveBookmark(bookmark: Bookmark) {
        scope.launch {
            dbHelper.insertOrUpdate(bookmark)
            val updated = dbHelper.getAllBookmarks()
            _bookmarks.value = updated
        }
    }

    fun updateActivePlayback(playback: PlaybackStateModel) {
        _activePlayback.value = playback
    }

    fun deleteBookmark(id: String) {
        scope.launch {
            dbHelper.deleteBookmark(id)
            val updated = dbHelper.getAllBookmarks()
            _bookmarks.value = updated
        }
    }

    fun clearAll() {
        scope.launch {
            dbHelper.clearAll()
            _bookmarks.value = emptyList()
            _activePlayback.value = null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BookmarkRepository? = null

        fun initialize(context: Context): BookmarkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkRepository(context).also { INSTANCE = it }
            }
        }

        fun getInstance(): BookmarkRepository {
            return INSTANCE ?: error("BookmarkRepository wurde noch nicht initialisiert. Bitte SpotisaveApp.onCreate abwarten.")
        }
    }
}
