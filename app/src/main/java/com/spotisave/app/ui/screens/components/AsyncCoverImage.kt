package com.spotisave.app.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotisave.app.ui.theme.HairlineBorderLight
import com.spotisave.app.ui.theme.HairlineBorderSubtle
import com.spotisave.app.ui.theme.LocalIsDarkTheme
import com.spotisave.app.ui.theme.SpotifyGreen
import com.spotisave.app.ui.theme.SurfaceCardElevated
import com.spotisave.app.ui.theme.SurfaceCardLightElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thread-sicherer In-Memory LRU Cache für Cover-Artworks.
 * Verhindert Flackern und wiederholte Netzwerkabfragen beim Scrollen.
 */
object CoverImageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 12).coerceAtLeast(1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

/**
 * Apple HIG / Liquid Glass Async Cover Image Loader.
 * Lädt Cover-Artworks für Playlists, Alben und Tracks asynchron im Hintergrund,
 * cacht diese im Speicher und fällt bei Ladezeit oder Netzwerkunterbrechung
 * nahtlos auf ein hochauflösendes Vektor-Icon im passenden Squircle zurück.
 */
@Composable
fun AsyncCoverImage(
    coverUrl: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    iconTint: Color = SpotifyGreen,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null,
    backgroundColor: Color = if (LocalIsDarkTheme.current) SurfaceCardElevated else SurfaceCardLightElevated,
    borderColor: Color = if (LocalIsDarkTheme.current) HairlineBorderSubtle else HairlineBorderLight
) {
    var bitmap by remember(coverUrl) {
        mutableStateOf(coverUrl?.let { CoverImageCache.get(it) })
    }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = CoverImageCache.get(coverUrl)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                if (coverUrl.startsWith("file://") || coverUrl.startsWith("/")) {
                    val filePath = if (coverUrl.startsWith("file://")) coverUrl.removePrefix("file://") else coverUrl
                    val file = java.io.File(filePath)
                    if (file.exists() && file.canRead()) {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                        if (decoded != null) {
                            CoverImageCache.put(coverUrl, decoded)
                            bitmap = decoded
                            return@withContext
                        }
                    }
                }

                val url = URL(coverUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "SpotiSave-Android/1.1.0")
                }
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val stream = conn.inputStream
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (decoded != null) {
                        CoverImageCache.put(coverUrl, decoded)
                        bitmap = decoded
                    }
                }
                conn.disconnect()
            } catch (ignored: Exception) {
                // Bei Netzwerkfehlern oder Offline-Betrieb: sanfter Fallback auf Icon
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(0.75.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
