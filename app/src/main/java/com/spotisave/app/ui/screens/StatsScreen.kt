package com.spotisave.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.spotisave.app.data.stats.PlaylistStat
import com.spotisave.app.data.stats.TimeOfDayStat
import com.spotisave.app.data.stats.TopTrackStat
import com.spotisave.app.ui.screens.components.AsyncCoverImage
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.theme.TextTertiaryLight
import com.spotisave.app.ui.util.TimeFormatter

private data class StatsTokens(
    val isDark: Boolean,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardElevated: Color,
    val glassSurface: Color,
    val meniscusGleam: androidx.compose.ui.graphics.Brush,
    val specularRim: androidx.compose.ui.graphics.Brush,
    val specularRimSubtle: androidx.compose.ui.graphics.Brush
)

@Composable
private fun rememberStatsTokens(): StatsTokens {
    val isDark = LocalIsDarkTheme.current
    return remember(isDark) {
        StatsTokens(
            isDark = isDark,
            textPrimary = if (isDark) TextPrimary else TextPrimaryLight,
            textSecondary = if (isDark) TextSecondary else TextSecondaryLight,
            textTertiary = if (isDark) TextTertiary else TextTertiaryLight,
            cardElevated = if (isDark) SurfaceCardElevated else SurfaceCardLightElevated,
            glassSurface = if (isDark) LiquidGlassDefaults.GlassSurface else LiquidGlassDefaults.GlassSurfaceLight,
            meniscusGleam = if (isDark) LiquidGlassDefaults.MeniscusGleamBrush else LiquidGlassDefaults.MeniscusGleamLightBrush,
            specularRim = if (isDark) LiquidGlassDefaults.SpecularRimBrush else LiquidGlassDefaults.SpecularRimLightBrush,
            specularRimSubtle = if (isDark) LiquidGlassDefaults.SpecularRimSubtle else LiquidGlassDefaults.SpecularRimLightSubtle
        )
    }
}

/**
 * Statistiken & Insights Dashboard.
 * Strukturierte Segment-Navigation ("Übersicht", "Top-Charts", "Verlauf")
 * vermeidet visuelle Überfrachtung nach Apple HIG & Liquid Glass Richtlinien.
 */
@Composable
fun StatsScreen(
    stats: ListeningStats,
    onResumePlaylist: (Bookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = rememberStatsTokens()
    val haptic = LocalHapticFeedback.current
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Übersicht, 1: Top-Charts, 2: Verlauf

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Titel & Unterzeile
        item {
            Column {
                Text(
                    text = "Statistiken & Einblicke",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live-Analyse deiner Hörgewohnheiten & Fokus-Muster",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }

        // 2. Apple HIG Segmented Control / Pill Switcher (Übersicht / Top-Charts / Verlauf)
        item {
            StatsSegmentedControl(
                selectedIndex = selectedSection,
                onSelect = { idx ->
                    if (selectedSection != idx) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedSection = idx
                    }
                }
            )
        }

        // Hinweis-Banner für leeren Zustand
        if (stats.totalBookmarksCount == 0 && stats.totalTracksCount == 0) {
            item {
                StatsEmptyNoticeCard()
            }
        }

        // --- SEGMENT 0: ÜBERSICHT ---
        if (selectedSection == 0) {
            // KPI-Grid 1: Hörzeit & Tracks
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

            // KPI-Grid 2: Ø Dauer & Playlists
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

            // Tageszeiten-Verteilung (Morgen, Nachmittag, Abend, Nacht)
            if (stats.timeOfDayDistribution.isNotEmpty()) {
                item {
                    TimeOfDayDistributionCard(
                        distribution = stats.timeOfDayDistribution,
                        peakSlot = stats.peakTimeOfDaySlot
                    )
                }
            }

            // Wöchentliche Aktivität (Mo-So Balkendiagramm)
            item {
                WeeklyActivityCard(activities = stats.weeklyActivity)
            }

            // Hör-Persönlichkeit & Insights
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

            // Lieblings-Album (falls vorhanden)
            if (stats.topAlbum != null) {
                item {
                    TopAlbumCard(album = stats.topAlbum)
                }
            }
        }

        // --- SEGMENT 1: TOP-CHARTS ---
        if (selectedSection == 1) {
            // Top Playlists Ranking mit 1-Click Resume
            if (stats.topPlaylists.isNotEmpty()) {
                item {
                    TopPlaylistsCard(
                        playlists = stats.topPlaylists,
                        onResume = onResumePlaylist
                    )
                }
            } else if (!stats.topPlaylistTitle.isNullOrBlank()) {
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

            // Meistgehörte Songs Ranking mit 1-Click Resume
            if (stats.topSongs.isNotEmpty()) {
                item {
                    TopSongsCard(
                        topSongs = stats.topSongs,
                        onResume = onResumePlaylist
                    )
                }
            }

            // Lieblings-Künstler Ranking mit Prozent-Balken
            if (stats.topArtists.isNotEmpty()) {
                item {
                    TopArtistsCard(artists = stats.topArtists)
                }
            }
        }

        // --- SEGMENT 2: VERLAUF ---
        if (selectedSection == 2) {
            if (stats.history.isNotEmpty()) {
                item {
                    HistoryCard(
                        history = stats.history,
                        onResume = onResumePlaylist
                    )
                }
            } else {
                item {
                    StatsEmptySectionCard(
                        title = "Noch kein Hörverlauf erfasst",
                        message = "Sobald du Titel in Spotify abspielst, wird deine chronologische Historie hier mit relativen Zeitangaben festgehalten."
                    )
                }
            }
        }
    }
}

/**
 * Apple HIG Segmented Control mit Liquid Glass Meniskus & haptischem Feedback.
 */
@Composable
private fun StatsSegmentedControl(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val tokens = rememberStatsTokens()
    val segments = listOf("Übersicht", "Top-Charts", "Verlauf")
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRimSubtle, shape)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val itemShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(itemShape)
                        .background(if (isSelected) tokens.cardElevated else Color.Transparent)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) tokens.textPrimary else tokens.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Tageszeiten-Verteilung Card (Morgen, Nachmittag, Abend, Nacht)
 */
@Composable
private fun TimeOfDayDistributionCard(
    distribution: List<TimeOfDayStat>,
    peakSlot: String
) {
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFFD60A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tageszeiten-Muster",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.cardElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Peak: $peakSlot",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                distribution.forEach { slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(96.dp)) {
                            Text(
                                text = slot.slotName,
                                fontSize = 12.sp,
                                fontWeight = if (slot.isPeak) FontWeight.Bold else FontWeight.Medium,
                                color = if (slot.isPeak) tokens.textPrimary else tokens.textSecondary
                            )
                            Text(
                                text = slot.slotRange.replace(" Uhr", ""),
                                fontSize = 10.sp,
                                color = tokens.textTertiary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${slot.minutes} Min.",
                                    fontSize = 11.sp,
                                    fontWeight = if (slot.isPeak) FontWeight.Bold else FontWeight.Normal,
                                    color = if (slot.isPeak) SpotifyGreen else tokens.textSecondary,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = "${(slot.percentage * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = tokens.textTertiary,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = slot.percentage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (slot.isPeak) SpotifyGreen else AppleSystemGreen,
                                trackColor = tokens.cardElevated
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Playlists Ranking mit 1-Click Resume, Fortschritt und Completion Rate.
 */
@Composable
private fun TopPlaylistsCard(
    playlists: List<PlaylistStat>,
    onResume: (Bookmark) -> Unit
) {
    val tokens = rememberStatsTokens()
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Top-Playlists",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "Top ${playlists.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                playlists.forEachIndexed { index, pl ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (index == 0) SpotifyGreen else tokens.textSecondary,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(26.dp)
                        )

                        AsyncCoverImage(
                            coverUrl = pl.coverUrl,
                            fallbackIcon = Icons.Default.QueueMusic,
                            iconTint = if (index == 0) SpotifyGreen else tokens.textTertiary,
                            iconSize = 22.dp,
                            contentDescription = pl.title,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${pl.formattedTotalTime} • ${pl.playCount} Titel",
                                fontSize = 11.sp,
                                color = SpotifyGreen,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 44dp 1-Click Fortsetzen Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (index == 0) SpotifyGreen else tokens.cardElevated)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResume(pl.bookmark)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Wiedergeben: ${pl.title}",
                                tint = if (index == 0) Color.Black else tokens.textPrimary,
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
private fun StatsEmptySectionCard(title: String, message: String) {
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRimSubtle, cardShape)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = tokens.textSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StatsEmptyNoticeCard() {
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRimSubtle, cardShape)
            .padding(16.dp)
            .testTag("stats_empty_notice_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.cardElevated),
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
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sobald du Musik in Spotify abspielst, erscheinen hier automatisch dein Lieblings-Album, deine Top-Songs und deine Wochen-Trends.",
                    fontSize = 12.sp,
                    color = tokens.textSecondary,
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
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: $value, $subtitle"
            }
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
                    color = tokens.textSecondary
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
                color = tokens.textPrimary,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = tokens.textTertiary
            )
        }
    }
}

@Composable
private fun TopAlbumCard(album: AlbumStat) {
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
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
                        color = tokens.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.cardElevated)
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
                AsyncCoverImage(
                    coverUrl = album.coverUrl,
                    fallbackIcon = Icons.Default.Album,
                    iconTint = Color(0xFFFF9F0A),
                    iconSize = 28.dp,
                    contentDescription = album.title,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.artist,
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
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
    val tokens = rememberStatsTokens()
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
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
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "Top ${topSongs.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topSongs.forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = if (index == 0) SpotifyGreen else tokens.textSecondary,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(26.dp)
                        )

                        AsyncCoverImage(
                            coverUrl = song.coverUrl,
                            fallbackIcon = Icons.Default.MusicNote,
                            iconTint = if (index == 0) SpotifyGreen else tokens.textTertiary,
                            iconSize = 20.dp,
                            contentDescription = song.title,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "${song.artist} • ${song.contextTitle}",
                                fontSize = 11.sp,
                                color = tokens.textSecondary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.formattedTotalTime} gehört • Dauer ${song.formattedDuration}",
                                fontSize = 10.sp,
                                color = SpotifyGreen,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tokens.cardElevated)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResume(song.bookmark)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Wiedergeben: ${song.title}",
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
    val tokens = rememberStatsTokens()
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
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
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "Zuletzt gehört",
                    fontSize = 11.sp,
                    color = tokens.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.take(15).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tokens.cardElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.relativeTime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SpotifyGreen,
                                        style = TextStyle(fontFeatureSettings = "tnum")
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
                                        color = if (item.bookmark.isPlaylist) SpotifyGreen else tokens.textTertiary,
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
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${item.artist} (${item.formattedDuration})",
                                fontSize = 11.sp,
                                color = tokens.textSecondary,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1
                            )
                        }

                        // 44dp 1-Click Fortfahren Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tokens.cardElevated)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResume(item.bookmark)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Wiedergeben: ${item.trackTitle}",
                                tint = tokens.textPrimary,
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
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
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
                        color = tokens.textPrimary
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
                        .background(tokens.cardElevated)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Primäre Hörzeit",
                            fontSize = 11.sp,
                            color = tokens.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = peakHours,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.cardElevated)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Top-Wochentag",
                            fontSize = 11.sp,
                            color = tokens.textSecondary
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
                    .background(tokens.cardElevated)
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
                        color = tokens.textSecondary
                    )
                    Text(
                        text = "$completionRate% ($completionLabel)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
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
                    trackColor = if (tokens.isDark) CanvasBlack else Color(0xFFE5E5EA)
                )
            }
        }
    }
}

@Composable
private fun WeeklyActivityCard(activities: List<DayActivity>) {
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                    color = tokens.textPrimary
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
                        Text(
                            text = if (day.minutes > 0) "${day.minutes}m" else "0",
                            fontSize = 9.sp,
                            color = tokens.textTertiary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

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
                                        day.minutes > 0 -> tokens.cardElevated
                                        else -> tokens.cardElevated.copy(alpha = 0.35f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = day.dayName,
                            fontSize = 11.sp,
                            fontWeight = if (day.fraction >= 0.8f) FontWeight.Bold else FontWeight.Medium,
                            color = if (day.fraction >= 0.8f) tokens.textPrimary else tokens.textSecondary
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
    val tokens = rememberStatsTokens()
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
            .padding(18.dp)
    ) {
        Column {
            Text(
                text = "Top-Playlist / Fokus-Station",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncCoverImage(
                    coverUrl = stats.topPlaylistCover,
                    fallbackIcon = Icons.Default.QueueMusic,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    iconTint = SpotifyGreen,
                    iconSize = 24.dp,
                    contentDescription = stats.topPlaylistTitle
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stats.topPlaylistTitle ?: "Playlist",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = stats.topPlaylistArtist ?: "Spotify",
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Position: ${TimeFormatter.formatTime(stats.topPlaylistProgressMs)}",
                        fontSize = 11.sp,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResume()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Fortsetzen: ${stats.topPlaylistTitle ?: "Playlist"}",
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
    val tokens = rememberStatsTokens()
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tokens.glassSurface)
            .background(tokens.meniscusGleam)
            .border(0.75.dp, tokens.specularRim, cardShape)
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
                            .background(tokens.cardElevated),
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
                        color = tokens.textPrimary
                    )
                }

                if (artists.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.cardElevated)
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
                            color = if (index == 0) SpotifyGreen else tokens.textSecondary,
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
                                    color = tokens.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${artist.count} Titel",
                                    fontSize = 11.sp,
                                    color = tokens.textTertiary
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
                                trackColor = tokens.cardElevated
                            )
                        }
                    }
                }
            }
        }
    }
}
