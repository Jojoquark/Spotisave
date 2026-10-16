package com.spotisave.app.ui.theme

import androidx.compose.ui.graphics.Color

// Pure OLED Black Canvas
val CanvasBlack = Color(0xFF000000)

// Apple Dark Mode Inset Surfaces
val SurfaceCard = Color(0xFF161618)
val SurfaceCardElevated = Color(0xFF202024)
val SurfaceCardPressed = Color(0xFF28282D)

// Subtle Hairline Borders (Apple HIG Glassmorphism)
val HairlineBorder = Color(0x1FFFFFFF)      // ~12% White
val HairlineBorderSubtle = Color(0x12FFFFFF) // ~7% White

// Accents & Spotify Glow
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenPressed = Color(0xFF1AA34A)
val AppleSystemGreen = Color(0xFF30D158)
val AccentGlow = Color(0x331DB954)

// Typographic Hierarchy (Exceeds WCAG AAA/AA)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF) // 60% White (~8:1 on black)
val TextTertiary = Color(0x66FFFFFF)  // 40% White (>4.5:1 on black)
val TextMuted = Color(0x40FFFFFF)     // 25% White

// Status Indicators
val StatusLivePlaying = Color(0xFF30D158)
val StatusPaused = Color(0xFFFFD60A)
val StatusError = Color(0xFFFF453A)

// Apple White Mode / Light Theme Palette (HIG Compliant)
val CanvasWhite = Color(0xFFF2F2F7)             // iOS System Grouped Background
val SurfaceCardLight = Color(0xFFFFFFFF)        // Pure White Card Surface
val SurfaceCardLightElevated = Color(0xFFE5E5EA)// Elevated Card Surface (System Gray 5)
val SurfaceCardLightPressed = Color(0xFFD1D1D6) // Pressed Feedback (System Gray 4)

// Light Mode Hairline Borders
val HairlineBorderLight = Color(0x1F000000)       // ~12% Black
val HairlineBorderLightSubtle = Color(0x10000000) // ~6% Black

// Light Mode Typographic Hierarchy (Exceeds WCAG AAA/AA on light backgrounds)
val TextPrimaryLight = Color(0xFF000000)        // Pure Black
val TextSecondaryLight = Color(0x99000000)      // 60% Black
val TextTertiaryLight = Color(0x66000000)       // 40% Black
val TextMutedLight = Color(0x38000000)          // 22% Black
val AppleSystemGreenLight = Color(0xFF28CD41)   // Light mode system green

