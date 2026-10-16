package com.spotisave.app.ui.screens.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotisave.app.data.api.SpotifyAuthManager
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

/**
 * Spotify Web API & Client ID Konfigurationsdialog.
 * Ermöglicht PKCE-Anmeldung, Redirect-URI Kopieren und Statusprüfung.
 */
@Composable
fun SpotifyApiSettingsDialog(
    isLoggedIn: Boolean,
    initialClientId: String,
    authStatusMessage: String?,
    onConnectClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var clientIdText by remember { mutableStateOf(initialClientId) }
    var copiedRedirect by remember { mutableStateOf(false) }

    val redirectUri = SpotifyAuthManager.DEFAULT_REDIRECT_URI // spotisave://callback

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Spotify Web API",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status-Pille
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLoggedIn) AppleSystemGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLoggedIn) "API Verbunden" else "Lokal (Kein Token)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoggedIn) AppleSystemGreen else TextSecondary
                        )
                    }
                }

                if (!authStatusMessage.isNullOrBlank()) {
                    Text(
                        text = authStatusMessage,
                        fontSize = 12.sp,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Client ID Eingabefeld
                OutlinedTextField(
                    value = clientIdText,
                    onValueChange = { clientIdText = it },
                    label = { Text("Spotify Client ID", color = TextSecondary) },
                    singleLine = true,
                    placeholder = { Text("32-stelliger Hex-Code", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spotify_client_id_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = SpotifyGreen
                    )
                )

                // Redirect URI Infobox mit 1-Click Kopieren (44dp Touch-Target nach Apple HIG)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = "Redirect URI für Spotify Dashboard:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = redirectUri,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyGreen
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .testTag("copy_redirect_uri_button")
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Redirect URI", redirectUri))
                                    copiedRedirect = true
                                    Toast.makeText(context, "Redirect URI kopiert!", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (copiedRedirect) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Redirect URI kopieren",
                                tint = if (copiedRedirect) AppleSystemGreen else TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Verbinden / Abmelden Button (Apple HIG >= 48dp Höhe & WCAG AAA Kontrast)
                if (isLoggedIn) {
                    Button(
                        onClick = onLogoutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("spotify_disconnect_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Verbindung trennen", color = StatusError, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onConnectClick(clientIdText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("spotify_connect_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = CanvasBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = CanvasBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mit Spotify verbinden", color = CanvasBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    )
}
