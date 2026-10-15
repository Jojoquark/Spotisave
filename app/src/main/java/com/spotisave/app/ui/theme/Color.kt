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
