package com.spotisave.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.HistoryTrackEntry

/**
 * SQLite Datenbank-Helfer für persistente, ACID-konforme Speicherung aller Spotify-Hörpositionen
 * und des vollständigen kontinuierlichen Hörverlaufs (Listening History).
 * Nutzt Standard Android SQLite ohne externe Codegenerierungs-Abhängigkeiten.
 */
class BookmarkDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "spotisave_bookmarks.db"
        const val DATABASE_VERSION = 2

        // Tabelle: Bookmarks (Aktiver Fortsetzungs-Stand je Playlist/Album)
        const val TABLE_NAME = "bookmarks"
        const val COL_ID = "id"
        const val COL_CONTEXT_URI = "context_uri"
        const val COL_CONTEXT_TITLE = "context_title"
        const val COL_TRACK_TITLE = "track_title"
        const val COL_ARTIST = "artist"
        const val COL_TRACK_URI = "track_uri"
        const val COL_COVER_URL = "cover_url"
        const val COL_POSITION_MS = "position_ms"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_SOURCE = "source"

        // Tabelle: Listening History (Vollständige Historie aller gespielten Titel)
        const val TABLE_HISTORY = "listening_history"
        const val COL_HIST_ID = "id"
        const val COL_HIST_TRACK_URI = "track_uri"
        const val COL_HIST_TRACK_TITLE = "track_title"
        const val COL_HIST_ARTIST = "artist"
        const val COL_HIST_ALBUM = "album"
        const val COL_HIST_CONTEXT_URI = "context_uri"
        const val COL_HIST_CONTEXT_TITLE = "context_title"
        const val COL_HIST_COVER_URL = "cover_url"
        const val COL_HIST_DURATION_MS = "duration_ms"
        const val COL_HIST_LISTENED_MS = "listened_ms"
        const val COL_HIST_TIMESTAMP = "timestamp"
        const val COL_HIST_SOURCE = "source"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createBookmarksQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY,
                $COL_CONTEXT_URI TEXT,
                $COL_CONTEXT_TITLE TEXT NOT NULL,
                $COL_TRACK_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_TRACK_URI TEXT NOT NULL,
                $COL_COVER_URL TEXT,
                $COL_POSITION_MS INTEGER NOT NULL,
                $COL_DURATION_MS INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SOURCE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createBookmarksQuery)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_timestamp ON $TABLE_NAME ($COL_TIMESTAMP DESC)")

        val createHistoryQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $COL_HIST_ID TEXT PRIMARY KEY,
                $COL_HIST_TRACK_URI TEXT NOT NULL,
                $COL_HIST_TRACK_TITLE TEXT NOT NULL,
                $COL_HIST_ARTIST TEXT NOT NULL,
                $COL_HIST_ALBUM TEXT,
                $COL_HIST_CONTEXT_URI TEXT,
                $COL_HIST_CONTEXT_TITLE TEXT,
                $COL_HIST_COVER_URL TEXT,
                $COL_HIST_DURATION_MS INTEGER NOT NULL,
                $COL_HIST_LISTENED_MS INTEGER NOT NULL,
                $COL_HIST_TIMESTAMP INTEGER NOT NULL,
                $COL_HIST_SOURCE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createHistoryQuery)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON $TABLE_HISTORY ($COL_HIST_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_track ON $TABLE_HISTORY ($COL_HIST_TRACK_URI)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createHistoryQuery = """
                CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                    $COL_HIST_ID TEXT PRIMARY KEY,
                    $COL_HIST_TRACK_URI TEXT NOT NULL,
                    $COL_HIST_TRACK_TITLE TEXT NOT NULL,
                    $COL_HIST_ARTIST TEXT NOT NULL,
                    $COL_HIST_ALBUM TEXT,
                    $COL_HIST_CONTEXT_URI TEXT,
                    $COL_HIST_CONTEXT_TITLE TEXT,
                    $COL_HIST_COVER_URL TEXT,
                    $COL_HIST_DURATION_MS INTEGER NOT NULL,
                    $COL_HIST_LISTENED_MS INTEGER NOT NULL,
                    $COL_HIST_TIMESTAMP INTEGER NOT NULL,
                    $COL_HIST_SOURCE TEXT NOT NULL
                )
            """.trimIndent()
            db.execSQL(createHistoryQuery)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON $TABLE_HISTORY ($COL_HIST_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_track ON $TABLE_HISTORY ($COL_HIST_TRACK_URI)")
        }
    }

    // --- Bookmarks Operations ---

    @Synchronized
    fun insertOrUpdate(bookmark: Bookmark) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, bookmark.id)
            put(COL_CONTEXT_URI, bookmark.contextUri)
            put(COL_CONTEXT_TITLE, bookmark.contextTitle)
            put(COL_TRACK_TITLE, bookmark.trackTitle)
            put(COL_ARTIST, bookmark.artist)
            put(COL_TRACK_URI, bookmark.trackUri)
            put(COL_COVER_URL, bookmark.coverUrl)
            put(COL_POSITION_MS, bookmark.positionMs)
            put(COL_DURATION_MS, bookmark.durationMs)
            put(COL_TIMESTAMP, bookmark.timestamp)
            put(COL_SOURCE, bookmark.source)
        }
        db.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun getAllBookmarks(): List<Bookmark> {
        val list = mutableListOf<Bookmark>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_ID)
            val ctxUriIdx = it.getColumnIndexOrThrow(COL_CONTEXT_URI)
            val ctxTitleIdx = it.getColumnIndexOrThrow(COL_CONTEXT_TITLE)
            val trackTitleIdx = it.getColumnIndexOrThrow(COL_TRACK_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val trackUriIdx = it.getColumnIndexOrThrow(COL_TRACK_URI)
            val coverUrlIdx = it.getColumnIndexOrThrow(COL_COVER_URL)
            val posIdx = it.getColumnIndexOrThrow(COL_POSITION_MS)
            val durIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val timeIdx = it.getColumnIndexOrThrow(COL_TIMESTAMP)
            val srcIdx = it.getColumnIndexOrThrow(COL_SOURCE)

            while (it.moveToNext()) {
                list.add(
                    Bookmark(
                        id = it.getString(idIdx),
                        contextUri = it.getString(ctxUriIdx),
                        contextTitle = it.getString(ctxTitleIdx),
                        trackTitle = it.getString(trackTitleIdx),
                        artist = it.getString(artistIdx),
                        trackUri = it.getString(trackUriIdx),
                        coverUrl = it.getString(coverUrlIdx),
                        positionMs = it.getLong(posIdx),
                        durationMs = it.getLong(durIdx),
                        timestamp = it.getLong(timeIdx),
                        source = it.getString(srcIdx)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getLatestBookmark(): Bookmark? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                return Bookmark(
                    id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                    contextUri = it.getString(it.getColumnIndexOrThrow(COL_CONTEXT_URI)),
                    contextTitle = it.getString(it.getColumnIndexOrThrow(COL_CONTEXT_TITLE)),
                    trackTitle = it.getString(it.getColumnIndexOrThrow(COL_TRACK_TITLE)),
                    artist = it.getString(it.getColumnIndexOrThrow(COL_ARTIST)),
                    trackUri = it.getString(it.getColumnIndexOrThrow(COL_TRACK_URI)),
                    coverUrl = it.getString(it.getColumnIndexOrThrow(COL_COVER_URL)),
                    positionMs = it.getLong(it.getColumnIndexOrThrow(COL_POSITION_MS)),
                    durationMs = it.getLong(it.getColumnIndexOrThrow(COL_DURATION_MS)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    source = it.getString(it.getColumnIndexOrThrow(COL_SOURCE))
                )
            }
        }
        return null
    }

    @Synchronized
    fun deleteBookmark(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun importBookmarks(bookmarks: List<Bookmark>): Int {
        if (bookmarks.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            for (bookmark in bookmarks) {
                val values = ContentValues().apply {
                    put(COL_ID, bookmark.id)
                    put(COL_CONTEXT_URI, bookmark.contextUri)
                    put(COL_CONTEXT_TITLE, bookmark.contextTitle)
                    put(COL_TRACK_TITLE, bookmark.trackTitle)
                    put(COL_ARTIST, bookmark.artist)
                    put(COL_TRACK_URI, bookmark.trackUri)
                    put(COL_COVER_URL, bookmark.coverUrl)
                    put(COL_POSITION_MS, bookmark.positionMs)
                    put(COL_DURATION_MS, bookmark.durationMs)
                    put(COL_TIMESTAMP, bookmark.timestamp)
                    put(COL_SOURCE, bookmark.source)
                }
                val rowId = db.insertWithOnConflict(
                    TABLE_NAME,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    // --- Listening History Operations ---

    @Synchronized
    fun insertOrUpdateHistory(entry: HistoryTrackEntry) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HIST_ID, entry.id)
            put(COL_HIST_TRACK_URI, entry.trackUri)
            put(COL_HIST_TRACK_TITLE, entry.trackTitle)
            put(COL_HIST_ARTIST, entry.artist)
            put(COL_HIST_ALBUM, entry.album)
            put(COL_HIST_CONTEXT_URI, entry.contextUri)
            put(COL_HIST_CONTEXT_TITLE, entry.contextTitle)
            put(COL_HIST_COVER_URL, entry.coverUrl)
            put(COL_HIST_DURATION_MS, entry.durationMs)
            put(COL_HIST_LISTENED_MS, entry.listenedMs)
            put(COL_HIST_TIMESTAMP, entry.timestamp)
            put(COL_HIST_SOURCE, entry.source)
        }
        db.insertWithOnConflict(
            TABLE_HISTORY,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun getAllHistory(limit: Int = 1000): List<HistoryTrackEntry> {
        val list = mutableListOf<HistoryTrackEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_HIST_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_HIST_ID)
            val trackUriIdx = it.getColumnIndexOrThrow(COL_HIST_TRACK_URI)
            val trackTitleIdx = it.getColumnIndexOrThrow(COL_HIST_TRACK_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_HIST_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_HIST_ALBUM)
            val ctxUriIdx = it.getColumnIndexOrThrow(COL_HIST_CONTEXT_URI)
            val ctxTitleIdx = it.getColumnIndexOrThrow(COL_HIST_CONTEXT_TITLE)
            val coverUrlIdx = it.getColumnIndexOrThrow(COL_HIST_COVER_URL)
            val durIdx = it.getColumnIndexOrThrow(COL_HIST_DURATION_MS)
            val listenedIdx = it.getColumnIndexOrThrow(COL_HIST_LISTENED_MS)
            val timeIdx = it.getColumnIndexOrThrow(COL_HIST_TIMESTAMP)
            val srcIdx = it.getColumnIndexOrThrow(COL_HIST_SOURCE)

            while (it.moveToNext()) {
                list.add(
                    HistoryTrackEntry(
                        id = it.getString(idIdx),
                        trackUri = it.getString(trackUriIdx),
                        trackTitle = it.getString(trackTitleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx) ?: "Spotify",
                        contextUri = it.getString(ctxUriIdx),
                        contextTitle = it.getString(ctxTitleIdx) ?: "Spotify",
                        coverUrl = it.getString(coverUrlIdx),
                        durationMs = it.getLong(durIdx),
                        listenedMs = it.getLong(listenedIdx),
                        timestamp = it.getLong(timeIdx),
                        source = it.getString(srcIdx)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun importHistory(entries: List<HistoryTrackEntry>): Int {
        if (entries.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            for (entry in entries) {
                val values = ContentValues().apply {
                    put(COL_HIST_ID, entry.id)
                    put(COL_HIST_TRACK_URI, entry.trackUri)
                    put(COL_HIST_TRACK_TITLE, entry.trackTitle)
                    put(COL_HIST_ARTIST, entry.artist)
                    put(COL_HIST_ALBUM, entry.album)
                    put(COL_HIST_CONTEXT_URI, entry.contextUri)
                    put(COL_HIST_CONTEXT_TITLE, entry.contextTitle)
                    put(COL_HIST_COVER_URL, entry.coverUrl)
                    put(COL_HIST_DURATION_MS, entry.durationMs)
                    put(COL_HIST_LISTENED_MS, entry.listenedMs)
                    put(COL_HIST_TIMESTAMP, entry.timestamp)
                    put(COL_HIST_SOURCE, entry.source)
                }
                val rowId = db.insertWithOnConflict(
                    TABLE_HISTORY,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    @Synchronized
    fun deleteHistoryEntry(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, "$COL_HIST_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun purgeCorruptedHistory(): Int {
        val db = writableDatabase
        val whereClause = """
            $COL_HIST_TRACK_URI LIKE '%:active%' OR
            $COL_HIST_TRACK_URI LIKE '%:notification%' OR
            $COL_HIST_TRACK_URI LIKE '%local_%' OR
            $COL_HIST_TRACK_URI LIKE 'spotify:local%' OR
            $COL_HIST_TRACK_TITLE = 'Unbekannter Titel' OR
            LOWER($COL_HIST_TRACK_TITLE) LIKE '%warteschlange%' OR
            LOWER($COL_HIST_TRACK_TITLE) LIKE '%queue%' OR
            LOWER($COL_HIST_CONTEXT_TITLE) LIKE '%warteschlange%' OR
            LOWER($COL_HIST_CONTEXT_TITLE) LIKE '%queue%' OR
            LOWER($COL_HIST_ID) LIKE '%warteschlange%' OR
            LOWER($COL_HIST_ID) LIKE '%queue%'
        """.trimIndent()
        return db.delete(TABLE_HISTORY, whereClause, null)
    }

    @Synchronized
    fun clearHistory(): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, null, null)
    }

    @Synchronized
    fun clearAll(): Int {
        val db = writableDatabase
        val bCount = db.delete(TABLE_NAME, null, null)
        val hCount = db.delete(TABLE_HISTORY, null, null)
        return bCount + hCount
    }
}
