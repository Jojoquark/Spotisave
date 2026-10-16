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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import com.spotisave.app.ui.screens.components.SpotisaveBottomBar
import com.spotisave.app.ui.screens.components.SpotifyApiSettingsDialog
import com.spotisave.app.ui.screens.components.StatusBottomSheet
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusError
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarks.collectAsState()
    val activePlayback by viewModel.activePlayback.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isNotificationGranted by viewModel.isNotificationListenerGranted.collectAsState()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val isSpotifyLoggedIn by viewModel.isSpotifyLoggedIn.collectAsState()
    val spotifyClientId by viewModel.spotifyClientId.collectAsState()
    val authStatusMessage by viewModel.authStatusMessage.collectAsState()
    val listeningStats by viewModel.listeningStats.collectAsState()

    var showStatusSheet by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack)
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
                            isSpotifyLoggedIn -> "Spotisave, Spotify Web API verbunden"
                            isServiceRunning -> "Spotisave, Hintergrunddienst aktiv"
                            else -> "Spotisave, Hintergrunddienst inaktiv"
                        }
                    }
                ) {
                    Text(
                        text = "Spotisave",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
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
                    // Spotify Web API Connect Button (44dp Touch-Target)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSpotifyLoggedIn) SurfaceCardElevated else SurfaceCard)
                            .border(
                                1.dp,
                                if (isSpotifyLoggedIn) SpotifyGreen else HairlineBorder,
                                CircleShape
                            )
                            .testTag("header_api_button")
                            .clickable { showApiDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Spotify API Einstellungen",
                            tint = if (isSpotifyLoggedIn) SpotifyGreen else TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Diagnose / Hintergrunddienst-Status Button (44dp Touch-Target)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardElevated)
                            .border(1.dp, HairlineBorder, CircleShape)
                            .testTag("header_status_button")
                            .clickable { showStatusSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Hintergrund-Status öffnen",
                            tint = TextPrimary,
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
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp, top = 8.dp),
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

                    // Abschnitts-Überschrift
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gespeicherte Hörpositionen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            if (bookmarks.isNotEmpty()) {
                                Text(
                                    text = "${bookmarks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceCardElevated)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Liste aller gespeicherten Bookmarks
                    if (bookmarks.isEmpty()) {
                        item {
                            EmptyStateCard()
                        }
                    } else {
                        items(
                            items = bookmarks,
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
