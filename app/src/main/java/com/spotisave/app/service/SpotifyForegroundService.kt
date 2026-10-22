package com.spotisave.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.spotisave.app.MainActivity
import com.spotisave.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vordergrunddienst (Foreground Service) für unterbrechungsfreies Hintergrund-Tracking.
 * Stellt sicher, dass Spotify-Broadcasts und Positionsaktualisierungen auch dann
 * zuverlässig erfasst werden, wenn das Telefon mit ausgeschaltetem Bildschirm in der Tasche liegt.
 */
class SpotifyForegroundService : Service() {

    private var broadcastReceiver: SpotifyBroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    companion object {
        private const val TAG = "SpotifyService"
        const val CHANNEL_ID = "spotisave_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.spotisave.app.action.START"
        const val ACTION_STOP = "com.spotisave.app.action.STOP"
        const val ACTION_TRIGGER_SYNC = "com.spotisave.app.action.TRIGGER_SYNC"

        const val ACTIVE_POLL_INTERVAL_MS = 4_000L   // 4 Sekunden während aktiver Wiedergabe (3-5s Bereich)
        const val IDLE_POLL_INTERVAL_MS = 300_000L   // 5 Minuten im Ruhezustand (300 Sekunden)

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SpotifyForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpotifyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerSync(context: Context) {
            val intent = Intent(context, SpotifyForegroundService::class.java).apply {
                action = ACTION_TRIGGER_SYNC
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.d(TAG, "Konnte triggerSync nicht starten: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerDynamicReceiver()
        acquireWakeLock()
        _isRunning.value = true
        Log.i(TAG, "SpotifyForegroundService gestartet")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TRIGGER_SYNC) {
            serviceScope.launch {
                PlaybackTracker.syncFromAuthoritativeApi(applicationContext)
            }
            return START_STICKY
        }

        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startPeriodicPositionTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ausführen von startForeground: ${e.message}", e)
        }

        return START_STICKY
    }

    private fun startPeriodicPositionTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            // Initial sofort einen Sync ausführen
            try {
                PlaybackTracker.syncFromAuthoritativeApi(applicationContext)
            } catch (e: Exception) {
                Log.d(TAG, "Initialer Sync Fehler: ${e.message}")
            }

            while (isActive) {
                val isPlayingNow = PlaybackTracker.isCurrentlyPlaying()
                val currentDelayMs = if (isPlayingNow) ACTIVE_POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS
                delay(currentDelayMs)

                try {
                    val authManager = com.spotisave.app.data.api.SpotifyAuthManager.getInstance(applicationContext)
                    if (authManager.isLoggedIn()) {
                        PlaybackTracker.syncFromAuthoritativeApi(applicationContext)
                    } else {
                        val currentPos = SpotifyNotificationListenerService.getActivePlaybackPosition()
                        if (currentPos > 0L) {
                            PlaybackTracker.syncPeriodicPosition(applicationContext, currentPos)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Poller Fehler: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
        unregisterDynamicReceiver()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        _isRunning.value = false
        Log.i(TAG, "SpotifyForegroundService beendet")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerDynamicReceiver() {
        if (broadcastReceiver == null) {
            broadcastReceiver = SpotifyBroadcastReceiver()
            val filter = IntentFilter().apply {
                addAction(SpotifyBroadcastReceiver.ACTION_METADATA_CHANGED)
                addAction(SpotifyBroadcastReceiver.ACTION_PLAYBACK_STATE_CHANGED)
                addAction(SpotifyBroadcastReceiver.ACTION_QUEUE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(broadcastReceiver, filter)
            }
            Log.d(TAG, "Dynamischer SpotifyBroadcastReceiver registriert")
        }
    }

    private fun unregisterDynamicReceiver() {
        broadcastReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Deregistrieren des Receivers", e)
            }
            broadcastReceiver = null
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "spotisave:foreground_tracking"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L /* 10 Minuten Schutz */)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Konnte WakeLock nicht anfordern", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Freigeben des WakeLocks", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
