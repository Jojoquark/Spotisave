package com.spotisave.app.data.api

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Spotify PKCE OAuth Authentifizierungs-Manager (RFC 7636).
 * Verwaltet Client-ID, Code Verifier, Code Challenge, Token-Austausch und automatischen Refresh.
 */
class SpotifyAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SpotifyAuthManager"
        private const val PREFS_NAME = "spotisave_spotify_auth"

        const val KEY_CLIENT_ID = "sp_client_id"
        const val KEY_CODE_VERIFIER = "sp_code_verifier"
        const val KEY_ACCESS_TOKEN = "sp_access_token"
        const val KEY_REFRESH_TOKEN = "sp_refresh_token"
        const val KEY_TOKEN_EXPIRY = "sp_token_expiry"

        const val DEFAULT_REDIRECT_URI = "spotisave://callback"
        const val SCOPES = "user-read-playback-state user-modify-playback-state playlist-read-private playlist-read-collaborative user-read-recently-played user-top-read user-library-read"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"

        private const val ALLOWED_VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        @Volatile
        private var INSTANCE: SpotifyAuthManager? = null

        fun getInstance(context: Context): SpotifyAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Erzeugt einen kryptografisch sicheren Code-Verifier gemäß RFC 7636.
         */
        fun generateCodeVerifier(length: Int = 64): String {
            val random = SecureRandom()
            val sb = java.lang.StringBuilder(length)
            for (i in 0 until length) {
                val index = random.nextInt(ALLOWED_VERIFIER_CHARS.length)
                sb.append(ALLOWED_VERIFIER_CHARS[index])
            }
            return sb.toString()
        }

        /**
         * Erzeugt die SHA-256 Code-Challenge im Base64URL-Format ohne Padding.
         */
        fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }

        /**
         * Baut die vollständige Autorisierungs-URL für den externen Browser / Custom Tab zusammen.
         */
        fun buildAuthorizeUrl(
            clientId: String,
            codeChallenge: String,
            redirectUri: String = DEFAULT_REDIRECT_URI,
            scopes: String = SCOPES
        ): String {
            val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
            val encodedScopes = URLEncoder.encode(scopes, "UTF-8")
            return "$AUTH_ENDPOINT?client_id=$clientId&response_type=code&redirect_uri=$encodedRedirect&code_challenge_method=S256&code_challenge=$codeChallenge&scope=$encodedScopes"
        }
    }

    fun getClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getTokenExpiry(): Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

    fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        val refreshToken = getRefreshToken()
        return !token.isNullOrBlank() || !refreshToken.isNullOrBlank()
    }

    fun isTokenExpired(): Boolean {
        val expiry = getTokenExpiry()
        // 60 Sekunden Sicherheitspuffer vor tatsächlichem Ablauf
        return System.currentTimeMillis() > (expiry - 60_000L)
    }

    fun prepareAuthorization(clientId: String, redirectUri: String = DEFAULT_REDIRECT_URI): String {
        saveClientId(clientId)
        val verifier = generateCodeVerifier(64)
        prefs.edit().putString(KEY_CODE_VERIFIER, verifier).apply()
        val challenge = generateCodeChallenge(verifier)
        return buildAuthorizeUrl(clientId, challenge, redirectUri)
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryMs)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
        Log.i(TAG, "Spotify Tokens erfolgreich gespeichert (Gültig bis: $expiryMs)")
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_CODE_VERIFIER)
            .apply()
        Log.i(TAG, "Spotify Tokens gelöscht (Abgemeldet)")
    }

    /**
     * Tauscht den Authorization Code via PKCE gegen Access & Refresh Tokens ein.
     */
    suspend fun exchangeCode(
        code: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): Result<String> = withContext(Dispatchers.IO) {
        val clientId = getClientId() ?: return@withContext Result.failure(
            IllegalStateException("Keine Client ID hinterlegt")
        )
        val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null) ?: return@withContext Result.failure(
            IllegalStateException("Kein PKCE Code-Verifier vorhanden")
        )

        try {
            val url = URL(TOKEN_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val body = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                    "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optLong("expires_in", 3600L)

                saveTokens(accessToken, refreshToken, expiresIn)
                Result.success(accessToken)
            } else {
                Log.e(TAG, "Token-Exchange fehlgeschlagen ($responseCode): $responseText")
                val errorMsg = try {
                    val errorJson = JSONObject(responseText)
                    errorJson.optString("error_description", errorJson.optString("error", "HTTP $responseCode"))
                } catch (_: Exception) {
                    "HTTP $responseCode"
                }
                Result.failure(Exception("Token-Exchange fehlgeschlagen: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Netzwerkfehler beim Token-Exchange", e)
            Result.failure(e)
        }
    }

    /**
     * Erneuert das Access-Token mit dem hinterlegten Refresh-Token.
     */
    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val clientId = getClientId() ?: return@withContext Result.failure(
            IllegalStateException("Keine Client ID hinterlegt")
        )
        val refreshToken = getRefreshToken() ?: return@withContext Result.failure(
            IllegalStateException("Kein Refresh-Token vorhanden")
        )

        try {
            val url = URL(TOKEN_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val body = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                val accessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optLong("expires_in", 3600L)

                saveTokens(accessToken, newRefreshToken, expiresIn)
                Result.success(accessToken)
            } else {
                Log.e(TAG, "Token Refresh fehlgeschlagen ($responseCode): $responseText")
                if (responseCode == 400 || responseCode == 401) {
                    clearTokens()
                }
                Result.failure(Exception("Token Refresh fehlgeschlagen: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Netzwerkfehler beim Token-Refresh", e)
            Result.failure(e)
        }
    }

    /**
     * Liefert ein gültiges Access-Token. Falls abgelaufen, wird automatisch refreshed.
     */
    suspend fun getValidAccessToken(): String? {
        val currentToken = getAccessToken()
        if (currentToken != null && !isTokenExpired()) {
            return currentToken
        }
        val refreshResult = refreshAccessToken()
        return refreshResult.getOrNull()
    }
}
