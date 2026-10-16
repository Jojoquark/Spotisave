package com.spotisave.app.data.repository

import android.content.Context
import com.spotisave.app.data.db.BookmarkDatabaseHelper
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry
import com.spotisave.app.data.model.PlaybackStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zentrales Repository für Lese- und Schreibzugriffe auf Lesezeichen, den Live-Wiedergabestatus
 * sowie den vollständigen kontinuierlichen Hörverlauf (Listening History).
 * Stellt reaktive StateFlows für Jetpack Compose bereit und synchronisiert mit SQLite.
 */
class BookmarkRepository private constructor(context: Context) {

    private val dbHelper = BookmarkDatabaseHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _listeningHistory = MutableStateFlow<List<HistoryTrackEntry>>(emptyList())
    val listeningHistory: StateFlow<List<HistoryTrackEntry>> = _listeningHistory.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackStateModel?>(null)
    val activePlayback: StateFlow<PlaybackStateModel?> = _activePlayback.asStateFlow()

    init {
        reloadBookmarks()
        reloadHistory()
    }

    fun reloadBookmarks() {
        scope.launch {
            val list = dbHelper.getAllBookmarks()
            _bookmarks.value = list
        }
    }

    fun reloadHistory() {
        scope.launch {
            val list = dbHelper.getAllHistory()
            _listeningHistory.value = list
        }
    }

    fun saveBookmark(bookmark: Bookmark) {
        scope.launch {
            dbHelper.insertOrUpdate(bookmark)
            val updated = dbHelper.getAllBookmarks()
            _bookmarks.value = updated
        }
    }

    fun recordHistoryEntry(entry: HistoryTrackEntry) {
        scope.launch {
            dbHelper.insertOrUpdateHistory(entry)
            val updated = dbHelper.getAllHistory()
            _listeningHistory.value = updated
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

    fun updateBookmarkContextTitle(idOrContextUri: String, contextTitle: String, coverUrl: String? = null) {
        scope.launch {
            val all = dbHelper.getAllBookmarks()
            val existing = all.find { it.id == idOrContextUri || it.contextUri == idOrContextUri }
            if (existing != null) {
                val updated = existing.copy(
                    contextTitle = contextTitle,
                    coverUrl = coverUrl ?: existing.coverUrl
                )
                dbHelper.insertOrUpdate(updated)
                _bookmarks.value = dbHelper.getAllBookmarks()
            }
        }
    }

    fun clearAll() {
        scope.launch {
            dbHelper.clearAll()
            _bookmarks.value = emptyList()
            _listeningHistory.value = emptyList()
            _activePlayback.value = null
        }
    }

    fun exportBookmarksJson(): String {
        val current = _bookmarks.value.ifEmpty { dbHelper.getAllBookmarks() }
        return Bookmark.exportBookmarksToJson(current)
    }

    fun importBookmarksJson(jsonString: String, onResult: (Int) -> Unit = {}) {
        val parsed = Bookmark.importBookmarksFromJson(jsonString)
        if (parsed.isEmpty()) {
            onResult(0)
            return
        }
        scope.launch {
            val importedCount = dbHelper.importBookmarks(parsed)
            _bookmarks.value = dbHelper.getAllBookmarks()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(importedCount)
            }
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
