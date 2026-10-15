package com.spotisave.app.ui.screens.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusPaused
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextMuted
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary

/**
 * Modal Bottom Sheet zur Konfiguration und Status-Inspektion der Hintergrunddienste.
 * Bietet native iOS Sheet-Optik, Umschalter für den Foreground Service und Berechtigungs-Links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBottomSheet(
    isServiceRunning: Boolean,
    isNotificationListenerGranted: Boolean,
    totalBookmarks: Int,
    activeSource: String,
    onToggleService: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Hintergrund-Synchronisation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Spotisave sichert Spotify-Hörpositionen kontinuierlich im Hintergrund, damit nichts verloren geht.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Hintergrunddienst Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCardElevated)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vordergrunddienst",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isServiceRunning) "Aktiv (Hintergrund-Schutz an)" else "Inaktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) AppleSystemGreen else TextTertiary
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { onToggleService(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasBlack,
                            checkedTrackColor = SpotifyGreen,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Notification Listener Permission
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCardElevated)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spotify MediaSession Erkennung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isNotificationListenerGranted) "Berechtigung erteilt (Empfohlen)" else "Zugriff erforderlich",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isNotificationListenerGranted) AppleSystemGreen else StatusPaused
                            )
                        }

                        Icon(
                            imageVector = if (isNotificationListenerGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isNotificationListenerGranted) AppleSystemGreen else StatusPaused,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (!isNotificationListenerGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Zugriff in Einstellungen erlauben", color = SpotifyGreen)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Akku-Optimierung
            if (!isBatteryIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Akku-Leistungsoptimierung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Für absolute Zuverlässigkeit im Standby empfiehlt sich die Ausnahme von der Akku-Drosselung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Akkuschonung für Spotisave deaktivieren", color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Statistiken
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gespeicherte Bookmarks: $totalBookmarks",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = "Quelle: $activeSource",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
