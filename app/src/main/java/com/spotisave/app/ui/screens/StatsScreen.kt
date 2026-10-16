package com.spotisave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.stats.AlbumStat
import com.spotisave.app.data.stats.ArtistStat
import com.spotisave.app.data.stats.DayActivity
import com.spotisave.app.data.stats.HistoryEntry
import com.spotisave.app.data.stats.ListeningStats
import com.spotisave.app.data.stats.TopTrackStat
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.util.TimeFormatter

/**
 * Statistiken & Insights Dashboard.
 * Zeigt Gesamthörzeit, Track-Anzahl, Durchschnittsdauer,
 * Lieblings-Album, Meistgehörte Songs mit 1-Click Resume,
 * chronologische Historie mit relativen Zeitstempeln,
 * Lieblings-Künstler, Wochentagsverteilung, Hör-Persönlichkeit und Durchhör-Quote.
 */
@Composable
fun StatsScreen(
    stats: ListeningStats,
    onResumePlaylist: (Bookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Titel & Unterzeile
        item {
            Column {
                Text(
                    text = "Statistiken & Einblicke",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live-Analyse deiner Hörgewohnheiten & Fokus-Muster",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Hinweis-Banner für leeren Zustand (Apple HIG Empty State)
        if (stats.totalBookmarksCount == 0 && stats.totalTracksCount == 0) {
            item {
                StatsEmptyNoticeCard()
            }
        }

        // 1. KPI-Grid (Zeile 1: Hörzeit & Tracks)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Gesamte Hörzeit",
                    value = stats.formattedTotalTime,
                    subtitle = "akkumulierte Zeit",
                    icon = Icons.Default.Schedule,
                    iconTint = SpotifyGreen,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Track-Anzahl",
                    value = "${stats.totalTracksCount} Titel",
                    subtitle = if (stats.totalBookmarksCount > 0) "${stats.totalBookmarksCount} Lesezeichen" else "gespeicherte Songs",
                    icon = Icons.Default.MusicNote,
                    iconTint = AppleSystemGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. KPI-Grid (Zeile 2: Ø Dauer & Playlists)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Ø Track-Dauer",
                    value = stats.formattedAvgTrackDuration,
                    subtitle = "Durchschnitt pro Song",
                    icon = Icons.Default.Timer,
                    iconTint = Color(0xFF64D2FF),
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Playlists & Alben",
                    value = "${stats.totalTrackedPlaylists}",
                    subtitle = "aktive Stationen",
                    icon = Icons.Default.QueueMusic,
                    iconTint = Color(0xFFFF9F0A),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Lieblings-Album (Top / most listened album)
        if (stats.topAlbum != null) {
            item {
                TopAlbumCard(album = stats.topAlbum)
            }
        }

        // 4. Meistgehörte Songs / Top-Songs Ranking (mit 1-Click Resume)
        if (stats.topSongs.isNotEmpty()) {
            item {
                TopSongsCard(
                    topSongs = stats.topSongs,
                    onResume = onResumePlaylist
                )
            }
        }

        // 5. Lieblings-Künstler Ranking
        if (stats.topArtists.isNotEmpty()) {
            item {
                TopArtistsCard(artists = stats.topArtists)
            }
        }

        // 6. Hör-Persönlichkeit & Fokus Insights
        item {
            InsightsPersonaCard(
                persona = stats.listeningPersona,
                peakHours = stats.peakHourRange,
                completionRate = stats.completionRatePercent,
                completionLabel = stats.completionRateLabel,
                peakDay = stats.peakDayName,
                peakDayMinutes = stats.peakDayMinutes
            )
        }

        // 7. Wöchentliche Aktivität (Balkendiagramm Mo-So)
        item {
            WeeklyActivityCard(activities = stats.weeklyActivity)
        }

        // 8. Top Playlist mit Fortschritt & 1-Click Resume
        if (!stats.topPlaylistTitle.isNullOrBlank()) {
            item {
                TopPlaylistCard(
                    stats = stats,
                    onResume = {
                        val bmToResume = stats.topBookmark ?: Bookmark(
                            id = stats.topPlaylistContextUri ?: stats.topPlaylistTrackUri ?: "top_pl",
                            contextUri = stats.topPlaylistContextUri,
                            contextTitle = stats.topPlaylistTitle ?: "Playlist",
                            trackTitle = stats.topPlaylistTitle ?: "Titel",
                            artist = stats.topPlaylistArtist ?: "Spotify",
                            trackUri = stats.topPlaylistTrackUri ?: "",
                            coverUrl = stats.topPlaylistCover,
                            positionMs = stats.topPlaylistProgressMs,
                            durationMs = stats.topPlaylistDurationMs
                        )
                        onResumePlaylist(bmToResume)
                    }
                )
            }
        }

        // 9. Genaue Historie (Chronologische Listening History)
        if (stats.history.isNotEmpty()) {
            item {
                HistoryCard(
                    history = stats.history,
                    onResume = onResumePlaylist
                )
            }
        }
    }
}

@Composable
private fun StatsEmptyNoticeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag("stats_empty_notice_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Noch keine Hördaten erfasst",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sobald du Musik in Spotify abspielst, erscheinen hier automatisch dein Lieblings-Album, deine Top-Songs und deine Wochen-Trends.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun TopAlbumCard(album: AlbumStat) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = Color(0xFFFF9F0A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lieblings-Album",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCardElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${album.playCount} Tracks",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9F0A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art Container
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = Color(0xFFFF9F0A),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.artist,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${album.formattedTotalTime} Gesamthörzeit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SpotifyGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun TopSongsCard(
    topSongs: List<TopTrackStat>,
    onResume: (Bookmark) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Meistgehörte Songs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Top ${topSongs.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topSongs.forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Badge
                        Text(
                            text = "#${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (index == 0) SpotifyGreen else TextSecondary,
                            modifier = Modifier.width(26.dp)
                        )

                        // Artwork icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (index == 0) SpotifyGreen else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Song Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "${song.artist} • ${song.contextTitle}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.formattedTotalTime} gehört • Dauer ${song.formattedDuration}",
                                fontSize = 10.sp,
                                color = SpotifyGreen
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 1-Click Resume Button (Apple HIG 44pt Touch Target)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SurfaceCardElevated)
                                .clickable { onResume(song.bookmark) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Wiedergeben",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    history: List<HistoryEntry>,
    onResume: (Bookmark) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF64D2FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Genaue Historie",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Zuletzt gehört",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.take(8).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceCardElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.relativeTime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SpotifyGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.bookmark.isPlaylist) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = if (item.bookmark.isPlaylist && item.contextTitle.isNotBlank() && item.contextTitle != "Spotify") "Playlist: ${item.contextTitle}" else item.contextTitle,
                                        fontSize = 10.sp,
                                        color = if (item.bookmark.isPlaylist) SpotifyGreen else TextTertiary,
                                        fontWeight = if (item.bookmark.isPlaylist) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.trackTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${item.artist} (${item.formattedDuration})",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        // 44dp 1-Click Fortfahren Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SurfaceCardElevated)
                                .clickable { onResume(item.bookmark) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Wiedergeben",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsPersonaCard(
    persona: String,
    peakHours: String,
    completionRate: Int,
    completionLabel: String,
    peakDay: String,
    peakDayMinutes: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hör-Persönlichkeit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = persona,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2 Spalten: Primäre Hörzeit & Aktivster Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Primäre Hörzeit",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = peakHours,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Top-Wochentag",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (peakDayMinutes > 0) "$peakDay (${peakDayMinutes}m)" else peakDay,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Durchhör-Quote (Completion Rate)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCardElevated)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Durchhör-Quote",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "$completionRate% ($completionLabel)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (completionRate.toFloat() / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp)),
                    color = SpotifyGreen,
                    trackColor = CanvasBlack
                )
            }
        }
    }
}

@Composable
private fun WeeklyActivityCard(activities: List<DayActivity>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wöchentliche Aktivität",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7 Tage Balkendiagramm
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                activities.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Minutenwert über dem Balken
                        Text(
                            text = if (day.minutes > 0) "${day.minutes}m" else "0",
                            fontSize = 9.sp,
                            color = TextTertiary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Vertikaler Balken mit flachem Baseline-Dot für inaktive Tage
                        val barHeight = if (day.minutes > 0) {
                            (day.fraction * 70f).coerceIn(10f, 70f).dp
                        } else {
                            3.dp
                        }
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        day.fraction >= 0.8f -> SpotifyGreen
                                        day.minutes > 0 -> SurfaceCardElevated
                                        else -> SurfaceCardElevated.copy(alpha = 0.35f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Wochentag
                        Text(
                            text = day.dayName,
                            fontSize = 11.sp,
                            fontWeight = if (day.fraction >= 0.8f) FontWeight.Bold else FontWeight.Medium,
                            color = if (day.fraction >= 0.8f) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPlaylistCard(
    stats: ListeningStats,
    onResume: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Text(
                text = "Top-Playlist / Fokus-Station",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Artwork
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stats.topPlaylistTitle ?: "Playlist",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = stats.topPlaylistArtist ?: "Spotify",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Position: ${TimeFormatter.formatTime(stats.topPlaylistProgressMs)}",
                        fontSize = 11.sp,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 1-Click Play Button (Apple HIG 44pt Touch Target)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable { onResume() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Fortsetzen",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopArtistsCard(artists: List<ArtistStat>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD60A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lieblings-Künstler",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (artists.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardElevated)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "👑 ${artists.first().name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                artists.forEachIndexed { index, artist ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (index == 0) SpotifyGreen else TextSecondary,
                            modifier = Modifier.width(26.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = artist.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${artist.count} Titel",
                                    fontSize = 11.sp,
                                    color = TextTertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = artist.percentage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (index == 0) SpotifyGreen else AppleSystemGreen,
                                trackColor = SurfaceCardElevated
                            )
                        }
                    }
                }
            }
        }
    }
}
