package com.spotisave.app.ui.util

import java.util.Locale

object TimeFormatter {

    /**
     * Formatiert Millisekunden in tabellarische Zeitanzeige (z.B. 1:05 oder 1:01:05).
     */
    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1000L
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        val minutes = totalMinutes % 60L
        val hours = totalMinutes / 60L

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Formatiert einen Zeitstempel relativ zur Gegenwart auf Deutsch.
     */
    fun formatRelativeTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "unbekannt"
        val diffMs = System.currentTimeMillis() - timestampMs
        if (diffMs < 0L) return "gerade eben"

        val seconds = diffMs / 1000L
        return when {
            seconds < 5L -> "gerade eben"
            seconds < 60L -> "vor ${seconds}s"
            seconds < 3600L -> "vor ${seconds / 60L} Min."
            seconds < 86400L -> "vor ${seconds / 3600L} Std."
            else -> "vor ${seconds / 86400L} Tagen"
        }
    }

    /**
     * Formatiert Millisekunden in Stunden und Minuten für Statistiken (z.B. "2 Std. 45 Min.").
     */
    fun formatHoursAndMinutes(ms: Long): String {
        if (ms <= 0L) return "0 Min."
        val totalMinutes = ms / 60000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0) {
            if (minutes > 0) "${hours} Std. ${minutes} Min." else "${hours} Std."
        } else {
            "${minutes} Min."
        }
    }
}

