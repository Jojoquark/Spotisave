package com.spotisave.app.data.stats

import com.spotisave.app.data.model.Bookmark

data class AlbumStat(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val playCount: Int,
    val totalTimeMs: Long,
    val formattedTotalTime: String
)

data class TopTrackStat(
    val trackUri: String,
    val title: String,
    val artist: String,
    val contextTitle: String,
    val coverUrl: String?,
    val playCount: Int,
    val durationMs: Long,
    val totalTimeMs: Long,
    val formattedDuration: String,
    val formattedTotalTime: String,
    val bookmark: Bookmark
)

data class HistoryEntry(
    val id: String,
    val trackTitle: String,
    val artist: String,
    val contextTitle: String,
    val durationMs: Long,
    val formattedDuration: String,
    val timestamp: Long,
    val relativeTime: String,
    val coverUrl: String?,
    val bookmark: Bookmark
)

data class ArtistStat(
    val name: String,
    val count: Int,
    val percentage: Float // 0.0f .. 1.0f
)

data class DayActivity(
    val dayName: String, // "Mo", "Di", "Mi", ...
    val minutes: Int,
    val fraction: Float  // 0.0f .. 1.0f relative to peak day
)

data class TimeOfDayStat(
    val slotName: String, // "Morgen", "Nachmittag", "Abend", "Nacht"
    val slotRange: String, // z.B. "06:00 – 12:00 Uhr"
    val minutes: Int,
    val percentage: Float, // 0.0f .. 1.0f
    val isPeak: Boolean
)

data class PlaylistStat(
    val contextUri: String,
    val title: String,
    val coverUrl: String?,
    val playCount: Int,
    val totalTimeMs: Long,
    val formattedTotalTime: String,
    val completionRate: Int, // 0..100%
    val bookmark: Bookmark
)

data class ListeningStats(
    val totalListeningTimeMs: Long,
    val formattedTotalTime: String,
    val totalTrackedPlaylists: Int,
    val totalBookmarksCount: Int,
    val totalTracksCount: Int,
    val averageTrackDurationMs: Long,
    val formattedAvgTrackDuration: String,
    val topBookmark: Bookmark?,
    val topPlaylistTitle: String?,
    val topPlaylistCover: String?,
    val topPlaylistArtist: String?,
    val topPlaylistTrackUri: String?,
    val topPlaylistContextUri: String?,
    val topPlaylistProgressMs: Long,
    val topPlaylistDurationMs: Long,
    val topAlbum: AlbumStat?,
    val topSongs: List<TopTrackStat>,
    val history: List<HistoryEntry>,
    val topArtists: List<ArtistStat>,
    val weeklyActivity: List<DayActivity>,
    val peakDayName: String,
    val peakDayMinutes: Int,
    val listeningPersona: String,
    val peakHourRange: String,
    val completionRatePercent: Int,
    val completionRateLabel: String,
    val timeOfDayDistribution: List<TimeOfDayStat> = emptyList(),
    val topPlaylists: List<PlaylistStat> = emptyList(),
    val morningMinutes: Int = 0,
    val afternoonMinutes: Int = 0,
    val eveningMinutes: Int = 0,
    val nightMinutes: Int = 0,
    val peakTimeOfDaySlot: String = "Nachmittag"
)
