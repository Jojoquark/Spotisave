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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.stats.ArtistStat
import com.spotisave.app.data.stats.DayActivity
import com.spotisave.app.data.stats.ListeningStats
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusError
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.util.TimeFormatter

/**
 * Statistiken & Insights Dashboard.
 * Zeigt Gesamthörzeit, Track-Anzahl, Top-Playlist, Top-Interpreten,
 * Wöchentliche Aktivitäts-Balken, Hör-Persönlichkeit, Durchhör-Quote,
 * sowie die /boost und /grill-me Features.
 */
@Composable
fun StatsScreen(
    stats: ListeningStats,
    onResumePlaylist: (Bookmark) -> Unit,
    onCycleRoast: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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

        // 1. KPI-Karten (2 Spalten: Hörzeit & Playlists)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Gesamte Hörzeit",
                    value = stats.formattedTotalTime,
                    subtitle = "über alle Lesezeichen",
                    icon = Icons.Default.Schedule,
                    iconTint = SpotifyGreen,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Playlists",
                    value = "${stats.totalTrackedPlaylists}",
                    subtitle = if (stats.totalBookmarksCount > 0) "${stats.totalBookmarksCount} Lesezeichen" else "aktive Stationen",
                    icon = Icons.Default.QueueMusic,
                    iconTint = AppleSystemGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. /boost Energy & Flow Card
        item {
            BoostCard(
                score = stats.boostScore,
                level = stats.boostLevel,
                message = stats.boostMessage
            )
        }

        // 3. /grill-me Roast My Taste Card
        item {
            GrillMeCard(
                roast = stats.currentRoast,
                onNextRoast = onCycleRoast
            )
        }

        // 4. Hör-Persönlichkeit & Fokus Insights
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

        // 5. Wöchentliche Aktivität (Balkendiagramm Mo-So)
        item {
            WeeklyActivityCard(activities = stats.weeklyActivity)
        }

        // 6. Top Playlist mit Fortschritt & 1-Click Resume
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

        // 7. Top-Interpreten Ranking
        if (stats.topArtists.isNotEmpty()) {
            item {
                TopArtistsCard(artists = stats.topArtists)
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
                fontSize = 20.sp,
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
private fun BoostCard(
    score: Int,
    level: String,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1E2818),
                        SurfaceCard
                    )
                )
            )
            .border(1.dp, Color(0x331DB954), RoundedCornerShape(20.dp))
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "/boost Flow-Index",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = level,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SpotifyGreen
                        )
                    }
                }

                // Großer Score Gauge
                Text(
                    text = "$score%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = SpotifyGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (score.toFloat() / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SpotifyGreen,
                trackColor = SurfaceCardElevated
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GrillMeCard(
    roast: String,
    onNextRoast: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2A1414),
                        SurfaceCard
                    )
                )
            )
            .border(1.dp, Color(0x44FF453A), RoundedCornerShape(20.dp))
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(StatusError.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "/grill-me Roast",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .clickable { onNextRoast() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Neuer Roast",
                            tint = StatusError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Grill mich! 🔥",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000))
                    .border(0.5.dp, HairlineBorderSubtle, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "\"$roast\"",
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 18.sp,
                    color = TextPrimary
                )
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
            Text(
                text = "Top-Interpreten",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

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
                            color = SpotifyGreen,
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
                                    fontWeight = FontWeight.Medium,
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
                                color = SpotifyGreen,
                                trackColor = SurfaceCardElevated
                            )
                        }
                    }
                }
            }
        }
    }
}
