package com.spotisave.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Liquid Glass Design System Tokens & Components.
 * Inspiriert von Apples Human Interface Guidelines (2025/2026 Liquid Glass Spezifikation)
 * und modernen AGSL/Shader-Glassmorphism Konzepten:
 * - OLED True Black Kontrast (#000000)
 * - Transluzente gefrostete Oberflächen (60-85% Dichte)
 * - Brechende Lichtkante / Specular Rim Highlight (Dual-Gradient)
 * - Meniscus-Lichtreflexion an der Oberkante
 * - Apple Squircle-Radien (24dp bis 32dp)
 * - Subtile haptische und visuelle Press-Physik
 */
object LiquidGlassDefaults {

    // Transluzente Glas-Flächenfarben (Dark Mode)
    val GlassSurface = Color(0xCC141418)         // 80% Deckkraft Dunkelglas
    val GlassSurfaceElevated = Color(0xDD1E1E24) // 87% Deckkraft für gehobene Karten
    val GlassSurfacePressed = Color(0xEE26262E)  // Feedback bei Berührung
    val GlassFloatingBar = Color(0xF2101014)     // 95% Dichte für schwebende Navigation Bars

    // Transluzente Glas-Flächenfarben (White Mode / Light Theme)
    val GlassSurfaceLight = Color(0xCCFFFFFF)         // 80% Frosted White Glass
    val GlassSurfaceLightElevated = Color(0xEEFFFFFF) // 93% Frosted White Card
    val GlassSurfaceLightPressed = Color(0xF5E5E5EA)  // Light Mode Touch Feedback
    val GlassFloatingBarLight = Color(0xEBFCFCFD)     // 92% Translucent White Floating Bubble

    // Spekulare Lichtkanten Dark Mode (Lichteinfall von oben links nach unten rechts)
    val SpecularRimBrush: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x40FFFFFF), // 25% Weißes Glanzlicht
            Color(0x18FFFFFF), // 9% Übergang
            Color(0x06FFFFFF)  // 2% Schattenkante
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val SpecularRimSubtle: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x28FFFFFF),
            Color(0x0EFFFFFF),
            Color(0x04FFFFFF)
        )
    )

    // Spekulare Lichtkanten White Mode (Präzise diffuse Brechung & Randreflex)
    val SpecularRimLightBrush: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x80FFFFFF), // Heller Lichtreflex am oberen Rand
            Color(0x22000000), // Kontrastreiche Randkontur
            Color(0x0E000000)  // Subtile Schattenunterkante
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val SpecularRimLightSubtle: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x50FFFFFF),
            Color(0x18000000),
            Color(0x08000000)
        )
    )

    val AccentSpecularRim: Brush = Brush.linearGradient(
        colors = listOf(
            SpotifyGreen.copy(alpha = 0.65f),
            SpotifyGreen.copy(alpha = 0.20f),
            Color(0x081DB954)
        )
    )

    // Meniskus-Lichtreflektion (Wölbungseffekt am oberen Rand)
    val MeniscusGleamBrush: Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x18FFFFFF),
            Color(0x00FFFFFF)
        ),
        startY = 0f,
        endY = 60f
    )

    val MeniscusGleamLightBrush: Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x40FFFFFF),
            Color(0x00FFFFFF)
        ),
        startY = 0f,
        endY = 60f
    )

    // Adaptive Token Resolver basierend auf Theme-Modus
    fun glassSurface(isDark: Boolean): Color = if (isDark) GlassSurface else GlassSurfaceLight
    fun glassSurfaceElevated(isDark: Boolean): Color = if (isDark) GlassSurfaceElevated else GlassSurfaceLightElevated
    fun glassFloatingBar(isDark: Boolean): Color = if (isDark) GlassFloatingBar else GlassFloatingBarLight
    fun specularRim(isDark: Boolean): Brush = if (isDark) SpecularRimBrush else SpecularRimLightBrush
    fun specularRimSubtle(isDark: Boolean): Brush = if (isDark) SpecularRimSubtle else SpecularRimLightSubtle
    fun meniscusGleam(isDark: Boolean): Brush = if (isDark) MeniscusGleamBrush else MeniscusGleamLightBrush

    // Typische Apple-Radien
    val CardRadius: Dp = 24.dp
    val HeroRadius: Dp = 28.dp
    val PillRadius: Dp = 32.dp
    val BadgeRadius: Dp = 12.dp

    val CardShape = RoundedCornerShape(CardRadius)
    val HeroShape = RoundedCornerShape(HeroRadius)
    val PillShape = RoundedCornerShape(PillRadius)
    val BadgeShape = RoundedCornerShape(BadgeRadius)
}

/**
 * Shared GlassEffectContainer für koordinierte Lichtberechnung nach Apple HIG.
 */
@Composable
fun GlassEffectContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier,
        content = content
    )
}


/**
 * Reusable Compose Modifier für authentische Liquid Glass Oberflächen.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = LiquidGlassDefaults.CardShape,
    surfaceColor: Color = LiquidGlassDefaults.GlassSurface,
    borderBrush: Brush = LiquidGlassDefaults.SpecularRimBrush,
    borderWidth: Dp = 0.75.dp
): Modifier = this
    .clip(shape)
    .background(surfaceColor)
    .background(LiquidGlassDefaults.MeniscusGleamBrush)
    .border(
        width = borderWidth,
        brush = borderBrush,
        shape = shape
    )

/**
 * Universelle Liquid Glass Container-Card nach Apple HIG mit nativer Berührungs-Physik.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = LiquidGlassDefaults.CardShape,
    surfaceColor: Color = LiquidGlassDefaults.GlassSurface,
    borderBrush: Brush = LiquidGlassDefaults.SpecularRimBrush,
    borderWidth: Dp = 0.75.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "glass_card_press_scale"
    )

    val currentSurfaceColor = if (isPressed && onClick != null) {
        LiquidGlassDefaults.GlassSurfacePressed
    } else {
        surfaceColor
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .then(clickableModifier)
            .liquidGlassSurface(
                shape = shape,
                surfaceColor = currentSurfaceColor,
                borderBrush = borderBrush,
                borderWidth = borderWidth
            ),
        content = content
    )
}
