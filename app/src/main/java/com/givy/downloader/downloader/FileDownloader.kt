package com.givy.downloader.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Result of a completed (or failed) download.
 */
sealed class DownloadResult {
    data class Success(val savedUri: Uri, val fileName: String) : DownloadResult()
    data class Error(val message: String, val cause: Throwable? = null) : DownloadResult()
}

/**
 * Downloads a direct media URL to the device's shared storage (Movies/Givy or
 * Music/Givy folder) and reports progress along the way.
 *
 * Fully independent from any scraper: it only ever sees the plain URL that a
 * [com.givy.downloader.scraper.ScraperResult.Success] produced. It has zero
 * knowledge of TikTok, HTML, or how that URL was obtained.
 */
class FileDownloader(private val context: Context) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param url direct download URL (from the scraper).
     * @param fileName desired file name, without extension is fine.
     * @param isAudioOnly if true, saves under Music/Givy with a .mp3 fallback
     *                     extension; otherwise Movies/Givy with .mp4 fallback.
     * @param onProgress called on a background thread with a value in 0..100,
     *                    or -1 if the server didn't report a content length
     *                    (indeterminate progress).
     */
    suspend fun download(
        url: String,
        fileName: String,
        isAudioOnly: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error(
                        "Server balas error ${response.code} saat mengunduh file."
                    )
                }

                val body = response.body ?: return@withContext DownloadResult.Error(
                    "Response kosong dari server."
                )

                val contentType = body.contentType()?.toString().orEmpty()
                val extension = guessExtension(contentType, isAudioOnly)
                val safeName = sanitizeFileName(fileName) + extension

                val resolvedUri = createMediaStoreEntry(safeName, isAudioOnly, contentType)
                    ?: return@withContext DownloadResult.Error(
                        "Gagal membuat entri file di storage."
                    )

                val totalBytes = body.contentLength()
                var bytesCopied = 0L

                context.contentResolver.openOutputStream(resolvedUri)?.use { output: OutputStream ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((bytesCopied * 100) / totalBytes).toInt()
                                onProgress(percent.coerceIn(0, 100))
                            } else {
                                onProgress(-1)
                            }
                        }
                    }
                } ?: return@withContext DownloadResult.Error(
                    "Tidak bisa menulis ke storage (izin ditolak?)."
                )

                finalizePending(resolvedUri)
                DownloadResult.Success(resolvedUri, safeName)
            }
        } catch (e: IOException) {
            DownloadResult.Error("Koneksi gagal: ${e.message ?: "unknown network error"}", e)
        } catch (e: Exception) {
            DownloadResult.Error("Gagal mengunduh: ${e.message ?: "unknown error"}", e)
        }
    }

    private fun createMediaStoreEntry(
        fileName: String,
        isAudioOnly: Boolean,
        mimeType: String
    ): Uri? {
        val collection: Uri
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            if (mimeType.isNotBlank()) put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (isAudioOnly) {
                Environment.DIRECTORY_MUSIC + "/Givy"
            } else {
                Environment.DIRECTORY_MOVIES + "/Givy"
            }
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            collection = if (isAudioOnly) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = if (isAudioOnly) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
            val givyDir = java.io.File(dir, "Givy").apply { if (!exists()) mkdirs() }
            values.put(MediaStore.MediaColumns.DATA, java.io.File(givyDir, fileName).absolutePath)
            collection = if (isAudioOnly) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        return context.contentResolver.insert(collection, values)
    }

    private fun finalizePending(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun guessExtension(contentType: String, isAudioOnly: Boolean): String {
        return when {
            contentType.contains("mp4") -> ".mp4"
            contentType.contains("webm") -> ".webm"
            contentType.contains("mpeg") || contentType.contains("mp3") -> ".mp3"
            contentType.contains("m4a") -> ".m4a"
            isAudioOnly -> ".mp3"
            else -> ".mp4"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val withoutExtension = name.substringBeforeLast(".")
        return withoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank {
            "givy_${System.currentTimeMillis()}"
        }
    }
}
