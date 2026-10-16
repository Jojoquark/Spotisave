package com.spotisave.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.spotisave.app.data.model.Bookmark

/**
 * SQLite Datenbank-Helfer für persistente, ACID-konforme Speicherung aller Spotify-Hörpositionen.
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
        const val DATABASE_VERSION = 1

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
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
        db.execSQL(createQuery)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_timestamp ON $TABLE_NAME ($COL_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

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

    @Synchronized
    fun clearAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null)
    }
}
