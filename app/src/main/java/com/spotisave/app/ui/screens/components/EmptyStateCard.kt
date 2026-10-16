package com.spotisave.app.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.ui.theme.HairlineBorderLightSubtle
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLight
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight

/**
 * Ansprechende Empty-State Ansicht nach Apple HIG Prinzipien.
 */
@Composable
fun EmptyStateCard(
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cardBg = if (isDark) SurfaceCard else SurfaceCardLight
    val elevatedBg = if (isDark) SurfaceCardElevated else SurfaceCardLightElevated
    val borderCol = if (isDark) HairlineBorderSubtle else HairlineBorderLightSubtle
    val titleCol = if (isDark) TextPrimary else TextPrimaryLight
    val bodyCol = if (isDark) TextSecondary else TextSecondaryLight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(20.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(elevatedBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Keine gespeicherten Hörpositionen",
                style = MaterialTheme.typography.titleMedium,
                color = titleCol,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Starte eine Playlist, ein Hörbuch oder einen Podcast in Spotify. SpotiSave erfasst deinen Fortschritt automatisch im Hintergrund, selbst wenn dein Handy in der Tasche gesperrt ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = bodyCol,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
