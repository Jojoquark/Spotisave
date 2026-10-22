package com.spotisave.app.data.api

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val apkDownloadUrl: String? = null,
    val htmlUrl: String = "https://github.com/Jojoquark/Spotisave/releases"
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/Jojoquark/Spotisave/releases/latest"
    const val GITHUB_RELEASES_WEB = "https://github.com/Jojoquark/Spotisave/releases"

    /**
     * Vergleicht zwei Versionsstrings (z.B. "v1.2.0" und "1.1.0").
     * Gibt true zurück, wenn latest strikt neuer als current ist.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")

        val latestParts = cleanLatest.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }

        if (latestParts.isEmpty() || currentParts.isEmpty()) {
            return cleanLatest > cleanCurrent && cleanLatest != cleanCurrent
        }

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Parst ein GitHub-Release-JSON in AppUpdateInfo.
     */
    fun parseReleaseJson(jsonString: String, currentVersion: String): AppUpdateInfo {
        val json = JSONObject(jsonString)
        val tagName = json.optString("tag_name", "").trim()
        val releaseTitle = json.optString("name", tagName).trim()
        val body = json.optString("body", "").trim()
        val htmlUrl = json.optString("html_url", GITHUB_RELEASES_WEB).trim()

        var apkUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
        }

        val isNewer = tagName.isNotBlank() && isNewerVersion(tagName, currentVersion)
        return AppUpdateInfo(
            isUpdateAvailable = isNewer,
            latestVersion = tagName.ifEmpty { currentVersion },
            currentVersion = currentVersion,
            releaseTitle = releaseTitle,
            releaseNotes = body,
            apkDownloadUrl = apkUrl ?: htmlUrl,
            htmlUrl = htmlUrl
        )
    }

    /**
     * Prüft via GitHub REST-API auf eine neuere SpotiSave-Version.
     */
    suspend fun checkForUpdates(currentVersion: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_RELEASES_API)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "SpotiSave-Android-App/$currentVersion")
            }

            val code = conn.responseCode
            if (code == 200) {
                val stream = conn.inputStream
                val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                val info = parseReleaseJson(text, currentVersion)
                Result.success(info)
            } else if (code == 404) {
                // Noch keine formalen Releases veröffentlicht
                Result.success(
                    AppUpdateInfo(
                        isUpdateAvailable = false,
                        latestVersion = currentVersion,
                        currentVersion = currentVersion,
                        releaseNotes = "Keine neueren Updates verfügbar."
                    )
                )
            } else {
                Result.failure(Exception("HTTP $code: ${conn.responseMessage}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update-Check fehlgeschlagen: ${e.message}")
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Startet den APK-Download über Androids systemeigenen DownloadManager oder öffnet die Releases-Seite.
     */
    fun startDownload(context: Context, downloadUrl: String, fileName: String = "spotisave-update.apk") {
        try {
            val uri = Uri.parse(downloadUrl)
            if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    val request = DownloadManager.Request(uri).apply {
                        setTitle("SpotiSave Update")
                        setDescription("Lade neueste SpotiSave-Version herunter...")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                        setMimeType("application/vnd.android.package-archive")
                    }
                    dm.enqueue(request)
                    Toast.makeText(context, "Download gestartet... Siehe Benachrichtigungen", Toast.LENGTH_LONG).show()
                    return
                }
            }

            // Fallback auf Browser / GitHub Releases
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Konnte Download nicht starten", e)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_WEB)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Konnte Download-Link nicht öffnen", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
