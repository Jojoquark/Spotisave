package com.spotisave.app.ui.screens.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.data.model.PlaybackStateModel
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.liquidGlassSurface
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusLivePlaying
import com.spotisave.app.ui.theme.StatusPaused
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.util.TimeFormatter

/**
 * Top Hero-Card nach Apple Human Interface Guidelines:
 * Zeigt das aktuell oder zuletzt gehörte Spotify-Element mit Live-Status,
 * Fortschrittsbalken und prominentem 1-Click "Fortfahren"-Button.
 */
@Composable
fun HeroCard(
    activePlayback: PlaybackStateModel?,
    latestBookmark: Bookmark?,
    onResumeClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Bevorzuge aktive Live-Wiedergabe falls vorhanden, sonst das neueste gespeicherte Bookmark
    val isLive = activePlayback != null && activePlayback.trackTitle.isNotBlank()
    val trackTitle = if (isLive) activePlayback!!.trackTitle else latestBookmark?.trackTitle ?: ""
    val artist = if (isLive) activePlayback!!.artist else latestBookmark?.artist ?: ""
    val positionMs = if (isLive) activePlayback!!.positionMs else latestBookmark?.positionMs ?: 0L
    val durationMs = if (isLive) activePlayback!!.durationMs else latestBookmark?.durationMs ?: 0L
    val isPlaying = activePlayback?.isPlaying == true
    val trackUri = if (isLive) activePlayback!!.trackUri else latestBookmark?.trackUri ?: ""

    val contextUri = if (isLive) activePlayback?.contextUri else latestBookmark?.contextUri
    val rawContextTitle = if (isLive) activePlayback?.contextTitle else latestBookmark?.contextTitle
    val album = if (isLive) activePlayback!!.album else latestBookmark?.contextTitle ?: "Spotify"

    val isPlaylist = contextUri?.startsWith("spotify:playlist:") == true ||
        latestBookmark?.isPlaylist == true
    val isAlbum = contextUri?.startsWith("spotify:album:") == true ||
        latestBookmark?.isAlbum == true

    val displayContextTitle = when {
        !rawContextTitle.isNullOrBlank() && rawContextTitle != "Spotify" -> rawContextTitle
        latestBookmark != null && latestBookmark.displayContextTitle.isNotBlank() -> latestBookmark.displayContextTitle
        isPlaylist -> "Playlist"
        album.isNotBlank() && album != "Spotify" -> album
        else -> ""
    }

    val progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Pulsierende Animation für den Live-Indikator
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val borderBrush = if (isPlaying) LiquidGlassDefaults.AccentSpecularRim else LiquidGlassDefaults.SpecularRimBrush

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = LiquidGlassDefaults.HeroShape,
                surfaceColor = LiquidGlassDefaults.GlassSurfaceElevated,
                borderBrush = borderBrush,
                borderWidth = 1.dp
            )
            .padding(20.dp)
            .testTag("hero_card")
    ) {
        Column {
            // Oberer Badge-Bereich (Status & Kontext)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status-Punkt
                    val statusColor = when {
                        isPlaying -> StatusLivePlaying
                        isLive -> StatusPaused
                        else -> TextTertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .then(if (isPlaying) Modifier.scale(pulseScale) else Modifier)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isPlaying -> "LIVE WIEDERGABE"
                            isLive -> "PAUSIERT"
                            else -> "ZULETZT GEHÖRT"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlaying) StatusLivePlaying else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Playlist / Album Badge
                if (isPlaylist) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyGreen.copy(alpha = 0.15f))
                            .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (displayContextTitle.isNotBlank() && displayContextTitle != "Playlist") "PLAYLIST • $displayContextTitle" else "PLAYLIST",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (displayContextTitle.isNotBlank() && displayContextTitle != "Spotify") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardElevated)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isAlbum) Icons.Default.Album else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isAlbum) Color(0xFF64D2FF) else TextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAlbum) "ALBUM • $displayContextTitle" else displayContextTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAlbum) Color(0xFF64D2FF) else TextTertiary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Haupt-Infozeile mit Apple HIG Squircle Artwork-Anker + Titel & Interpret
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork / Cover Image squircle (Apple HIG Liquid Glass Style)
                AsyncCoverImage(
                    coverUrl = activePlayback?.coverUrl ?: latestBookmark?.coverUrl,
                    fallbackIcon = when {
                        isPlaylist -> Icons.Default.QueueMusic
                        isAlbum -> Icons.Default.Album
                        else -> Icons.Default.MusicNote
                    },
                    iconTint = when {
                        isPlaylist -> SpotifyGreen
                        isAlbum -> Color(0xFF64D2FF)
                        isPlaying -> StatusLivePlaying
                        else -> TextSecondary
                    },
                    iconSize = 26.dp,
                    contentDescription = "Cover Artwork",
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Track Titel
                    Text(
                        text = trackTitle.ifBlank { "Bereit für Spotify" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Interpret
                    Text(
                        text = artist.ifBlank { "Starte Musik auf deinem Gerät" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Explizite Herkunftszeile für Playlist / Album
            if (isPlaylist && displayContextTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (displayContextTitle != "Playlist") "Playlist: $displayContextTitle" else "Aus gespeicherter Spotify Playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (album.isNotBlank() && album != "Spotify" && !isPlaylist) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Album: $album",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fortschrittsbalken
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SpotifyGreen,
                trackColor = SurfaceCardElevated,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Zeit-Beschriftung (Tabellarisch nach HIG mit fontFeatureSettings = "tnum")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeFormatter.formatTime(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = if (durationMs > 0L) TimeFormatter.formatTime(durationMs) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Großer 1-Click "Fortfahren" Button
            val hasValidTrack = trackUri.isNotBlank() || latestBookmark != null
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val targetBookmark = if (isLive && trackUri.isNotBlank()) {
                        latestBookmark?.copy(
                            trackTitle = trackTitle,
                            artist = artist,
                            trackUri = trackUri,
                            contextUri = contextUri ?: latestBookmark.contextUri,
                            contextTitle = if (displayContextTitle.isNotBlank()) displayContextTitle else latestBookmark.contextTitle,
                            positionMs = positionMs,
                            durationMs = if (durationMs > 0) durationMs else latestBookmark.durationMs
                        ) ?: Bookmark(
                            id = contextUri ?: if (album.isNotBlank() && album != "Spotify") "album:${album.lowercase().replace(" ", "_")}" else trackUri,
                            contextUri = contextUri,
                            contextTitle = if (displayContextTitle.isNotBlank()) displayContextTitle else "Spotify",
                            trackTitle = trackTitle,
                            artist = artist,
                            trackUri = trackUri,
                            positionMs = positionMs,
                            durationMs = durationMs
                        )
                    } else {
                        latestBookmark ?: Bookmark(
                            id = contextUri ?: trackUri,
                            contextUri = contextUri,
                            contextTitle = if (displayContextTitle.isNotBlank()) displayContextTitle else "Spotify",
                            trackTitle = trackTitle,
                            artist = artist,
                            trackUri = trackUri,
                            positionMs = positionMs,
                            durationMs = durationMs
                        )
                    }
                    onResumeClick(targetBookmark)
                },
                enabled = hasValidTrack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("hero_resume_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = CanvasBlack,
                    disabledContainerColor = SurfaceCardElevated,
                    disabledContentColor = TextMuted
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Fortfahren",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fortfahren",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
