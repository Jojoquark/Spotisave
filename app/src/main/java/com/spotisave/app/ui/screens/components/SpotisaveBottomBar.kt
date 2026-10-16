package com.spotisave.app.ui.screens.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.spotisave.app.ui.theme.GlassEffectContainer
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextMutedLight
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight

/**
 * Apple Modern Floating Bubble Tab Bar (Liquid Glass Architecture).
 *
 * Spezifikation nach Apple HIG & Liquid Glass Guidelines:
 * - Detached Geometry: Schwebt als freistehende Kapsel 16-24pt über dem Home-Indicator / Navigation Bar.
 * - Liquid Glass Material: Transluzenter Brechungskörper (0xF0101012 im Dark Mode bzw. 0xEBFCFCFD im White Mode).
 * - Specular Rim Highlight: Brechende Dual-Gradient Randkontur.
 * - Meniscus Gleam: Oberflächen-Wölbungsreflex.
 * - Morphing Spring Pill: Flüssiger Feder-Übergang des aktiven Indikators zwischen 'Playlists' und 'Statistiken'.
 * - 44pt+ Touch-Target Konformität.
 */
@Composable
fun SpotisaveBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val barShape = LiquidGlassDefaults.PillShape

    // Apple Liquid Glass Oberflächen-Farbe (behält Color(0xF0101012) für Dark Mode bei)
    val barSurfaceColor = if (isDark) Color(0xF0101012) else LiquidGlassDefaults.GlassFloatingBarLight
    val rimBrush = if (isDark) LiquidGlassDefaults.SpecularRimBrush else LiquidGlassDefaults.SpecularRimLightBrush
    val meniscusBrush = if (isDark) LiquidGlassDefaults.MeniscusGleamBrush else LiquidGlassDefaults.MeniscusGleamLightBrush
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.12f)

    GlassEffectContainer(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
            .testTag("spotisave_bottom_bar")
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Freistehende Liquid Glass Kapsel (Floating Bubble)
            Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = barShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .clip(barShape)
                .background(barSurfaceColor)
                .background(meniscusBrush)
                .border(
                    width = 0.75.dp,
                    brush = rimBrush,
                    shape = barShape
                )
                .padding(6.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val totalWidth = maxWidth
                val tabWidth = totalWidth / 2

                // Flüssig animierter Morphing Spring Pill Indikator
                val indicatorOffset by animateFloatAsState(
                    targetValue = if (selectedTab == 0) 0f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "floating_bubble_pill_spring"
                )

                // Aktiver Auswahl-Hintergrund mit weichem Glanz
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * indicatorOffset)
                        .width(tabWidth)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(SpotifyGreen.copy(alpha = if (isDark) 0.16f else 0.12f))
                        .border(
                            width = 0.5.dp,
                            brush = LiquidGlassDefaults.AccentSpecularRim,
                            shape = RoundedCornerShape(26.dp)
                        )
                )

                // Tab Items
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingBarTabItem(
                        title = "Playlists",
                        icon = Icons.Default.QueueMusic,
                        isSelected = selectedTab == 0,
                        isDark = isDark,
                        tag = "tab_playlists",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (selectedTab != 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onTabSelected(0)
                        }
                    )

                    FloatingBarTabItem(
                        title = "Statistiken",
                        icon = Icons.Default.BarChart,
                        isSelected = selectedTab == 1,
                        isDark = isDark,
                        tag = "tab_stats",
                        modifier = Modifier.weight(1f),
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
    }
}
}

@Composable
private fun FloatingBarTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = when {
        isSelected -> if (isDark) TextPrimary else TextPrimaryLight
        isDark -> TextSecondary
        else -> TextSecondaryLight
    }
    val iconTint = if (isSelected) SpotifyGreen else (if (isDark) TextMuted else TextMutedLight)

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
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
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

