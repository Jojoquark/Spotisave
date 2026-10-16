package com.spotisave.app.ui.screens.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotisave.app.ui.theme.AppleSystemGreen
import com.spotisave.app.ui.theme.CanvasBlack
import com.spotisave.app.ui.theme.HairlineBorder
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.StatusPaused
import com.spotisave.app.ui.theme.SurfaceCard
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.TextPrimary
import com.spotisave.app.ui.theme.TextSecondary
import com.spotisave.app.ui.theme.TextTertiary

/**
 * Modal Bottom Sheet zur Konfiguration, Status-Inspektion und Datensicherung.
 * Bietet native iOS Sheet-Optik, Umschalter für den Foreground Service, Berechtigungs-Links
 * sowie 1-Click JSON Backup & Restore über die Zwischenablage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBottomSheet(
    isServiceRunning: Boolean,
    isNotificationListenerGranted: Boolean,
    totalBookmarks: Int,
    activeSource: String,
    onToggleService: (Boolean) -> Unit,
    onExportJson: () -> String = { "[]" },
    onImportJson: (String, (Int) -> Unit) -> Unit = { _, _ -> },
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var backupStatusText by remember { mutableStateOf<String?>(null) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
                .testTag("status_bottom_sheet")
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
                        modifier = Modifier.testTag("foreground_service_switch"),
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
                            contentDescription = if (isNotificationListenerGranted) "Berechtigung aktiv" else "Berechtigung fehlt",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("notification_permission_button"),
                            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Zugriff in Einstellungen erlauben",
                                color = SpotifyGreen,
                                fontWeight = FontWeight.SemiBold
                            )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("battery_optimization_button"),
                            border = BorderStroke(1.dp, HairlineBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Akkuschonung für Spotisave deaktivieren",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Datensicherung (Backup & Restore)
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
                        text = "Datensicherung (Backup & Restore)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sichere deine gespeicherten Hörpositionen als JSON in die Zwischenablage oder stelle sie nach einer Neuinstallation wieder her.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val json = onExportJson()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Spotisave Backup", json))
                                backupStatusText = "Backup in Zwischenablage kopiert ($totalBookmarks Einträge)"
                                Toast.makeText(context, "Backup kopiert!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("backup_export_button"),
                            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Backup kopieren",
                                color = SpotifyGreen,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                                if (clipText.isBlank()) {
                                    backupStatusText = "Zwischenablage ist leer"
                                } else {
                                    onImportJson(clipText) { count ->
                                        backupStatusText = if (count > 0) {
                                            "$count Hörpositionen erfolgreich wiederhergestellt"
                                        } else {
                                            "Kein gültiges Spotisave-Backup in der Zwischenablage gefunden"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("backup_import_button"),
                            border = BorderStroke(1.dp, HairlineBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Wiederherstellen",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    backupStatusText?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleSystemGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Statistiken
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
