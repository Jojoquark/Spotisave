package com.spotisave.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.spotisave.app.data.repository.BookmarkRepository
import com.spotisave.app.service.SpotifyForegroundService

class SpotisaveApp : Application() {

    companion object {
        private const val TAG = "SpotisaveApp"
        private const val PREFS_NAME = "spotisave_prefs"
        private const val KEY_AUTO_TRACKING = "auto_tracking_enabled"

        fun isAutoTrackingEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_TRACKING, true)
        }

        fun setAutoTrackingEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AUTO_TRACKING, enabled).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Spotisave Anwendung gestartet")

        // Initialisiere Repository & SQLite
        BookmarkRepository.initialize(this)

        // Starte Hintergrunddienst standardmäßig, falls aktiviert
        if (isAutoTrackingEnabled(this)) {
            try {
                SpotifyForegroundService.start(this)
            } catch (e: Exception) {
                Log.w(TAG, "Konnte ForegroundService beim App-Start nicht sofort starten: ${e.message}")
            }
        }
    }
}
