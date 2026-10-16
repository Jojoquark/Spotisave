package com.spotisave.app.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusError
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.theme.TextTertiaryLight
import com.spotisave.app.ui.util.TimeFormatter

/**
 * Individuelle Lesezeichen-Karte für die Liste aller gespeicherten Hörpositionen.
 * Entworfen nach Apple HIG: Inset-Design, 44px Mindest-Touch-Targets und saubere Typografie.
 */
@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onResumeClick: (Bookmark) -> Unit,
    onDeleteClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val cardShape = RoundedCornerShape(16.dp)

    val textPrimaryCol = if (isDark) TextPrimary else TextPrimaryLight
    val textSecondaryCol = if (isDark) TextSecondary else TextSecondaryLight
    val textTertiaryCol = if (isDark) TextTertiary else TextTertiaryLight
    val surfaceElevatedCol = if (isDark) SurfaceCardElevated else SurfaceCardLightElevated
    val surfaceColor = if (isDark) LiquidGlassDefaults.GlassSurface else LiquidGlassDefaults.GlassSurfaceLight
    val rimBrush = if (isDark) LiquidGlassDefaults.SpecularRimSubtle else LiquidGlassDefaults.SpecularRimLightSubtle
    val meniscusBrush = if (isDark) LiquidGlassDefaults.MeniscusGleamBrush else LiquidGlassDefaults.MeniscusGleamLightBrush

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(surfaceColor)
            .background(meniscusBrush)
            .border(0.75.dp, rimBrush, cardShape)
            .testTag("bookmark_card_${bookmark.id}")
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onResumeClick(bookmark)
            }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork / Cover Image squircle (Apple HIG Liquid Glass Style)
                AsyncCoverImage(
                    coverUrl = bookmark.coverUrl,
                    fallbackIcon = when {
                        bookmark.isPlaylist -> Icons.Default.QueueMusic
                        bookmark.isAlbum -> Icons.Default.Album
                        else -> Icons.Default.MusicNote
                    },
                    iconTint = if (bookmark.isPlaylist) SpotifyGreen else if (bookmark.isAlbum) androidx.compose.ui.graphics.Color(0xFF64D2FF) else textSecondaryCol,
                    contentDescription = if (bookmark.isPlaylist) "Playlist Cover" else "Album Cover",
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Linker Bereich: Titel, Interpret, Playlist
                Column(modifier = Modifier.weight(1f)) {
                    // Expliziter Playlist- / Album-Header
                    if (bookmark.isPlaylist) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Playlist: ${if (bookmark.contextTitle.isNotBlank() && bookmark.contextTitle != "Spotify") bookmark.contextTitle else "Gespeicherte Playlist"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (bookmark.isAlbum && bookmark.contextTitle.isNotBlank() && bookmark.contextTitle != "Spotify") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF64D2FF),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Album: ${bookmark.contextTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color(0xFF64D2FF),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (bookmark.contextTitle.isNotBlank() && bookmark.contextTitle != "Spotify") {
                        Text(
                            text = bookmark.contextTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = textTertiaryCol,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = bookmark.trackTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = textPrimaryCol,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = bookmark.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondaryCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Rechter Bereich: Aktionsbuttons mit mind. 44dp Touch-Target
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Löschen-Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteClick(bookmark)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("bookmark_delete_${bookmark.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Bookmark löschen: ${bookmark.trackTitle}",
                            tint = textTertiaryCol,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 1-Click Play Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen)
                            .testTag("bookmark_resume_${bookmark.id}")
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResumeClick(bookmark)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Hörposition fortfahren: ${bookmark.trackTitle}",
                            tint = CanvasBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fortschrittsbalken
            LinearProgressIndicator(
                progress = { bookmark.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SpotifyGreen,
                trackColor = surfaceElevatedCol,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Zeit- und Datumsangaben
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${TimeFormatter.formatTime(bookmark.positionMs)} von ${TimeFormatter.formatTime(bookmark.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondaryCol,
                    fontSize = 12.sp
                )
                Text(
                    text = TimeFormatter.formatRelativeTime(bookmark.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textTertiaryCol,
                    fontSize = 12.sp
                )
            }
        }
    }
}
