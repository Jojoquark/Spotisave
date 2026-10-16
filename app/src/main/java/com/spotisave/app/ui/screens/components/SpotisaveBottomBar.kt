package com.spotisave.app.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary

/**
 * Apple HIG Translucent Glass Bottom Navigation Bar.
 * Schaltet zwischen 'Playlists' und 'Statistiken' mit haptischem Feedback und dezentem Glow um.
 */
@Composable
fun SpotisaveBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(Color(0xF0101012)) // Apple Translucent Glass Dark
            .border(
                width = 0.5.dp,
                color = HairlineBorder,
                shape = barShape
            )
            .navigationBarsPadding()
            .testTag("spotisave_bottom_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                title = "Playlists",
                icon = Icons.Default.QueueMusic,
                isSelected = selectedTab == 0,
                tag = "tab_playlists",
                onClick = {
                    if (selectedTab != 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onTabSelected(0)
                }
            )

            BottomBarItem(
                title = "Statistiken",
                icon = Icons.Default.BarChart,
                isSelected = selectedTab == 1,
                tag = "tab_stats",
                onClick = {
                    if (selectedTab != 1) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onTabSelected(1)
                }
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .widthIn(min = 104.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) SpotifyGreen.copy(alpha = 0.12f) else Color.Transparent)
            .semantics(mergeDescendants = true) {
                selected = isSelected
            }
            .testTag(tag)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) SpotifyGreen else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) TextPrimary else TextSecondary
            )
        }
    }
}
