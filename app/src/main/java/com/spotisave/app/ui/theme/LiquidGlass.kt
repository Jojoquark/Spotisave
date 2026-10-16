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

    // Transluzente Glas-Flächenfarben
    val GlassSurface = Color(0xCC141418)         // 80% Deckkraft Dunkelglas
    val GlassSurfaceElevated = Color(0xDD1E1E24) // 87% Deckkraft für gehobene Karten
    val GlassSurfacePressed = Color(0xEE26262E)  // Feedback bei Berührung
    val GlassFloatingBar = Color(0xF2101014)     // 95% Dichte für schwebende Navigation Bars

    // Spekulare Lichtkanten (Lichteinfall von oben links nach unten rechts)
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
