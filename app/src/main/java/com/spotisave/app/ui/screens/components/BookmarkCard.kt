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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusError
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(16.dp))
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
                // Artwork / Icon squircle (Apple HIG Inset Style)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (bookmark.id.startsWith("album:")) Icons.Default.Album else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Linker Bereich: Titel, Interpret, Playlist
                Column(modifier = Modifier.weight(1f)) {
                    // Kontext/Playlist falls abweichend
                    if (bookmark.contextTitle.isNotBlank() && bookmark.contextTitle != "Spotify") {
                        Text(
                            text = bookmark.contextTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = bookmark.trackTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = bookmark.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
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
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Bookmark löschen",
                            tint = TextTertiary,
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
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResumeClick(bookmark)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Hörposition fortfahren",
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
                trackColor = SurfaceCardElevated,
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
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = TimeFormatter.formatRelativeTime(bookmark.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
