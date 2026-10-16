package com.spotisave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.TextTertiary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.model.Bookmark
import com.spotisave.app.ui.screens.components.BookmarkCard
import com.spotisave.app.ui.screens.components.EmptyStateCard
import com.spotisave.app.ui.screens.components.HeroCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.spotisave.app.ui.screens.components.SpotisaveBottomBar
import com.spotisave.app.ui.screens.components.SpotifyApiSettingsDialog
import com.spotisave.app.ui.screens.components.StatusBottomSheet
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.CanvasWhite
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.HairlineBorderLight
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusError
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLight
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextMutedLight
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight
import com.spotisave.app.ui.viewmodel.MainViewModel


@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val bookmarks by viewModel.bookmarks.collectAsState()
    val filteredBookmarks by viewModel.filteredBookmarks.collectAsState()
    val onlyPlaylistsFilter by viewModel.onlyPlaylistsFilter.collectAsState()
    val activePlayback by viewModel.activePlayback.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isNotificationGranted by viewModel.isNotificationListenerGranted.collectAsState()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val isSpotifyLoggedIn by viewModel.isSpotifyLoggedIn.collectAsState()
    val spotifyClientId by viewModel.spotifyClientId.collectAsState()
    val authStatusMessage by viewModel.authStatusMessage.collectAsState()
    val listeningStats by viewModel.listeningStats.collectAsState()
    val isWhiteMode by viewModel.isWhiteMode.collectAsState()

    var showStatusSheet by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    val canvasBg = if (isWhiteMode) CanvasWhite else CanvasBlack
    val textPrimaryColor = if (isWhiteMode) TextPrimaryLight else TextPrimary
    val textSecondaryColor = if (isWhiteMode) TextSecondaryLight else TextSecondary
    val headerCardBg = if (isWhiteMode) SurfaceCardLightElevated else SurfaceCardElevated
    val headerBorderBrush = if (isWhiteMode) HairlineBorderLight else HairlineBorder

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Apple HIG Glass Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = when {
                            isSpotifyLoggedIn -> "SpotiSave, Spotify Web API verbunden"
                            isServiceRunning -> "SpotiSave, Hintergrunddienst aktiv"
                            else -> "SpotiSave, Hintergrunddienst inaktiv"
                        }
                    }
                ) {
                    Text(
                        text = "SpotiSave",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    // Service & API Status Indikator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSpotifyLoggedIn -> SpotifyGreen
                                    isServiceRunning -> AppleSystemGreen
                                    else -> TextMuted
                                }
                            )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // White Mode / Dark Mode Schnell-Umschalter (44dp Apple HIG Touch-Target)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(headerCardBg)
                            .border(1.dp, headerBorderBrush, CircleShape)
                            .testTag("header_theme_toggle")
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleWhiteMode()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isWhiteMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Design wechseln (Hell/Dunkel)",
                            tint = textPrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Spotify Web API Connect Button (44dp Touch-Target)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSpotifyLoggedIn) headerCardBg else (if (isWhiteMode) SurfaceCardLight else SurfaceCard))
                            .border(
                                1.dp,
                                if (isSpotifyLoggedIn) SpotifyGreen else headerBorderBrush,
                                CircleShape
                            )
                            .testTag("header_api_button")
                            .clickable { showApiDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Spotify API Einstellungen",
                            tint = if (isSpotifyLoggedIn) SpotifyGreen else textPrimaryColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Diagnose / Hintergrunddienst-Status Button (44dp Touch-Target)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(headerCardBg)
                            .border(1.dp, headerBorderBrush, CircleShape)
                            .testTag("header_status_button")
                            .clickable { showStatusSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Hintergrund-Status öffnen",
                            tint = textPrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Tab-basierter Hauptinhalt
            if (selectedTab == 0) {
                // TAB 1: Playlists & Zuletzt gehört
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 108.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // Top Hero Card "Zuletzt gehört"
                    item {
                        val matchingBookmark = if (activePlayback != null) {
                            bookmarks.find { it.trackUri == activePlayback?.trackUri || (it.contextUri != null && it.contextUri == activePlayback?.contextUri) }
                        } else null
                        val latest = matchingBookmark ?: bookmarks.firstOrNull()
                        HeroCard(
                            activePlayback = activePlayback,
                            latestBookmark = latest,
                            onResumeClick = { bm ->
                                viewModel.resumeBookmark(context, bm)
                            }
                        )
                    }

                    // Abschnitts-Überschrift & Apple HIG Liquid Glass Filter-Bar
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gespeicherte Hörpositionen",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                val playlistCount = bookmarks.count { it.isPlaylist }
                                Text(
                                    text = if (onlyPlaylistsFilter) "$playlistCount Playlists" else "${bookmarks.size} Gespeichert",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceCardElevated)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Apple HIG Liquid Glass Segmented Filter Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceCardElevated)
                                    .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val playlistCount = bookmarks.count { it.isPlaylist }

                                // Segment 1: Alle
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (!onlyPlaylistsFilter) LiquidGlassDefaults.GlassSurface else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .then(
                                            if (!onlyPlaylistsFilter) Modifier.border(0.75.dp, LiquidGlassDefaults.SpecularRimSubtle, RoundedCornerShape(9.dp)) else Modifier
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setOnlyPlaylistsFilter(false)
                                        }
                                        .padding(vertical = 8.dp)
                                        .testTag("filter_chip_all"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Alle",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (!onlyPlaylistsFilter) FontWeight.Bold else FontWeight.Medium,
                                            color = if (!onlyPlaylistsFilter) TextPrimary else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${bookmarks.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (!onlyPlaylistsFilter) SpotifyGreen else TextTertiary
                                        )
                                    }
                                }

                                // Segment 2: Nur Playlists
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (onlyPlaylistsFilter) LiquidGlassDefaults.GlassSurface else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .then(
                                            if (onlyPlaylistsFilter) Modifier.border(0.75.dp, LiquidGlassDefaults.SpecularRimSubtle, RoundedCornerShape(9.dp)) else Modifier
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setOnlyPlaylistsFilter(true)
                                        }
                                        .padding(vertical = 8.dp)
                                        .testTag("filter_chip_playlists"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = if (onlyPlaylistsFilter) SpotifyGreen else TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Nur Playlists",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (onlyPlaylistsFilter) FontWeight.Bold else FontWeight.Medium,
                                            color = if (onlyPlaylistsFilter) SpotifyGreen else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$playlistCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (onlyPlaylistsFilter) SpotifyGreen else TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Liste aller bzw. gefilterten Bookmarks
                    if (filteredBookmarks.isEmpty()) {
                        item {
                            if (onlyPlaylistsFilter && bookmarks.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(LiquidGlassDefaults.GlassSurface)
                                        .border(0.75.dp, LiquidGlassDefaults.SpecularRimSubtle, RoundedCornerShape(18.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Keine Playlist-Hörstände",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Aktuell sind keine gespeicherten Stände aus einer Spotify-Playlist vorhanden.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        TextButton(
                                            onClick = { viewModel.setOnlyPlaylistsFilter(false) }
                                        ) {
                                            Text(
                                                text = "Alle Hörstände anzeigen",
                                                color = SpotifyGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                EmptyStateCard()
                            }
                        }
                    } else {
                        items(
                            items = filteredBookmarks,
                            key = { it.id }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                onResumeClick = { bm ->
                                    viewModel.resumeBookmark(context, bm)
                                },
                                onDeleteClick = { bm ->
                                    bookmarkToDelete = bm
                                }
                            )
                        }
                    }
                }
            } else {
                // TAB 2: Statistiken & Insights Dashboard
                StatsScreen(
                    stats = listeningStats,
                    onResumePlaylist = { bm ->
                        viewModel.resumeBookmark(context, bm)
                    }
                )
            }
        }

        // Apple HIG Translucent Bottom Navigation Bar
        SpotisaveBottomBar(
            selectedTab = selectedTab,
            onTabSelected = { viewModel.selectTab(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 2-Step Safe Delete Confirmation Dialog
        bookmarkToDelete?.let { bm ->
            AlertDialog(
                onDismissRequest = { bookmarkToDelete = null },
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = {
                    Text(
                        text = "Hörposition löschen?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Möchtest du das gespeicherte Lesezeichen für \"${bm.trackTitle}\" wirklich entfernen?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBookmark(bm)
                            bookmarkToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = StatusError)
                    ) {
                        Text(text = "Löschen", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { bookmarkToDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text(text = "Abbrechen")
                    }
                }
            )
        }

        // Status & Hintergrund-Synchronisation BottomSheet
        if (showStatusSheet) {
            StatusBottomSheet(
                isServiceRunning = isServiceRunning,
                isNotificationListenerGranted = isNotificationGranted,
                totalBookmarks = bookmarks.size,
                activeSource = activePlayback?.source ?: "Bereit",
                onlyPlaylistsFilter = onlyPlaylistsFilter,
                onToggleOnlyPlaylists = { viewModel.setOnlyPlaylistsFilter(it) },
                isWhiteMode = isWhiteMode,
                onToggleWhiteMode = { viewModel.setWhiteMode(it) },
                onToggleService = { enable ->
                    viewModel.toggleService(context, enable)
                },
                onExportJson = {
                    viewModel.exportBookmarksToJson()
                },
                onImportJson = { json, onDone ->
                    viewModel.importBookmarksFromJson(json, onDone)
                },
                onDismissRequest = {
                    showStatusSheet = false
                    viewModel.checkPermissions()
                }
            )

        }

        // Spotify Web API Settings Dialog
        if (showApiDialog) {
            SpotifyApiSettingsDialog(
                isLoggedIn = isSpotifyLoggedIn,
                initialClientId = spotifyClientId,
                authStatusMessage = authStatusMessage,
                onConnectClick = { id ->
                    viewModel.startAuthFlow(context, id)
                },
                onLogoutClick = {
                    viewModel.logoutSpotify()
                },
                onDismissRequest = {
                    showApiDialog = false
                    viewModel.checkPermissions()
                }
            )
        }
    }
}
