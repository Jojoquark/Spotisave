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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotisave.app.data.api.AppUpdateInfo
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.LiquidGlassDefaults
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextPrimaryLight
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextSecondaryLight
import com.spotisave.app.ui.theme.TextTertiary
import com.spotisave.app.ui.theme.TextTertiaryLight

/**
 * Apple Liquid Glass Update-Modal zur Anzeige verfügbarer SpotiSave-Releases.
 * Entworfen nach Design-Director, Impeccable Craft Floor und Emil Kowalski Motion-Regeln.
 */
@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDownloadClick: (AppUpdateInfo) -> Unit,
    onDismissRequest: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current
    val dialogShape = RoundedCornerShape(24.dp)

    val textPrimaryCol = if (isDark) TextPrimary else TextPrimaryLight
    val textSecondaryCol = if (isDark) TextSecondary else TextSecondaryLight
    val textTertiaryCol = if (isDark) TextTertiary else TextTertiaryLight
    val surfaceColor = if (isDark) LiquidGlassDefaults.GlassSurfaceElevated else LiquidGlassDefaults.GlassSurfaceLightElevated
    val rimBrush = if (isDark) LiquidGlassDefaults.SpecularRimBrush else LiquidGlassDefaults.SpecularRimLightBrush
    val meniscusBrush = if (isDark) LiquidGlassDefaults.MeniscusGleamBrush else LiquidGlassDefaults.MeniscusGleamLightBrush

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(dialogShape)
                .background(surfaceColor)
                .background(meniscusBrush)
                .border(1.dp, rimBrush, dialogShape)
                .padding(22.dp)
                .testTag("update_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header-Zeile mit Icon, Titel und Schließen-Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen.copy(alpha = 0.15f))
                                .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Update verfügbar",
                                style = MaterialTheme.typography.titleMedium,
                                color = textPrimaryCol,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${updateInfo.currentVersion} → ${updateInfo.latestVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = textTertiaryCol,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release-Titel
                if (updateInfo.releaseTitle.isNotBlank()) {
                    Text(
                        text = updateInfo.releaseTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textPrimaryCol,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Changelog / Versionshinweise (scrollbar bei langen Texten)
                if (updateInfo.releaseNotes.isNotBlank()) {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) CanvasBlack.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color(0xFFE5E5EA).copy(alpha = 0.6f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondaryCol,
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Text(
                        text = "Eine neue Version von SpotiSave mit Verbesserungen bei der Wiedergabe und Stabilität steht bereit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondaryCol
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Aktions-Buttons: Download (Primär) & Später (Sekundär)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Später",
                            style = MaterialTheme.typography.labelLarge,
                            color = textTertiaryCol,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDownloadClick(updateInfo)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = CanvasBlack
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("btn_download_update")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = CanvasBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelLarge,
                            color = CanvasBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
