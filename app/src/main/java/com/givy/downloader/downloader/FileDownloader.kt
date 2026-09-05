package com.givy.downloader.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
 *
 * Speed: when the server supports HTTP range requests (most video CDNs do),
 * the file is split into [PARALLEL_CONNECTIONS] chunks and downloaded
 * concurrently to a temp file — the same trick dedicated download managers
 * use — then copied into shared storage in one fast local disk-to-disk pass.
 * Falls back to a single streamed connection if range requests aren't
 * supported or anything about the split path fails.
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
     *                    or -1 if progress can't be determined yet.
     */
    suspend fun download(
        url: String,
        fileName: String,
        isAudioOnly: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "givy_dl_${System.currentTimeMillis()}.tmp")
        try {
            val probe = probeServer(url)

            val downloadedOk = if (probe != null && probe.acceptsRanges && probe.contentLength >= MIN_SIZE_FOR_PARALLEL) {
                runCatching {
                    downloadInParallel(url, tempFile, probe.contentLength, onProgress)
                }.getOrElse {
                    // Fall back to a plain sequential stream if the split download
                    // fails for any reason (flaky range support, server hiccup, etc.)
                    downloadSequential(url, tempFile, probe.contentLength, onProgress)
                }
            } else {
                downloadSequential(url, tempFile, probe?.contentLength ?: -1L, onProgress)
            }

            if (!downloadedOk || !tempFile.exists() || tempFile.length() == 0L) {
                return@withContext DownloadResult.Error("Gagal mengunduh file (koneksi terputus atau file kosong).")
            }

            val contentType = probe?.contentType.orEmpty()
            val extension = guessExtension(contentType, isAudioOnly)
            val safeName = sanitizeFileName(fileName) + extension

            val resolvedUri = createMediaStoreEntry(safeName, isAudioOnly, contentType)
                ?: return@withContext DownloadResult.Error("Gagal membuat entri file di storage.")

            context.contentResolver.openOutputStream(resolvedUri)?.use { output: OutputStream ->
                tempFile.inputStream().use { input -> input.copyTo(output, bufferSize = COPY_BUFFER_BYTES) }
            } ?: return@withContext DownloadResult.Error("Tidak bisa menulis ke storage (izin ditolak?).")

            finalizePending(resolvedUri)
            DownloadResult.Success(resolvedUri, safeName)
        } catch (e: IOException) {
            DownloadResult.Error("Koneksi gagal: ${e.message ?: "unknown network error"}", e)
        } catch (e: Exception) {
            DownloadResult.Error("Gagal mengunduh: ${e.message ?: "unknown error"}", e)
        } finally {
            tempFile.delete()
        }
    }

    private data class ServerProbe(
        val contentLength: Long,
        val acceptsRanges: Boolean,
        val contentType: String
    )

    /** Cheap request to learn size/range-support before deciding the download strategy. */
    private fun probeServer(url: String): ServerProbe? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val acceptsRanges = response.code == 206 ||
                    response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                val total = response.header("Content-Range")
                    ?.substringAfterLast("/")
                    ?.toLongOrNull()
                    ?: response.body?.contentLength()?.takeIf { it > 0 }
                    ?: -1L
                ServerProbe(contentLength = total, acceptsRanges = acceptsRanges, contentType = contentType)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Splits the file into concurrent range-request chunks for higher throughput. */
    private suspend fun downloadInParallel(
        url: String,
        outFile: File,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ): Boolean = coroutineScope {
        RandomAccessFile(outFile, "rw").use { raf -> raf.setLength(totalBytes) }

        val chunkSize = totalBytes / PARALLEL_CONNECTIONS
        val downloaded = AtomicLong(0L)

        val jobs = (0 until PARALLEL_CONNECTIONS).map { index ->
            val start = index * chunkSize
            val end = if (index == PARALLEL_CONNECTIONS - 1) totalBytes - 1 else (start + chunkSize - 1)
            async(Dispatchers.IO) {
                downloadRange(url, outFile, start, end) { bytesJustRead ->
                    val soFar = downloaded.addAndGet(bytesJustRead)
                    onProgress(((soFar * 100) / totalBytes).toInt().coerceIn(0, 100))
                }
            }
        }

        jobs.awaitAll().all { it }
    }

    private fun downloadRange(
        url: String,
        outFile: File,
        start: Long,
        end: Long,
        onBytesRead: (Long) -> Unit
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false

            RandomAccessFile(outFile, "rw").use { raf ->
                raf.seek(start)
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        onBytesRead(read.toLong())
                    }
                }
            }
        }
        return true
    }

    /** Plain single-connection streamed download — the reliable fallback path. */
    private fun downloadSequential(
        url: String,
        outFile: File,
        knownTotalBytes: Long,
        onProgress: (Int) -> Unit
    ): Boolean {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false

            val totalBytes = knownTotalBytes.takeIf { it > 0 } ?: body.contentLength()
            var bytesCopied = 0L

            outFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            onProgress(((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100))
                        } else {
                            onProgress(-1)
                        }
                    }
                }
            }
        }
        return true
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
            val givyDir = File(dir, "Givy").apply { if (!exists()) mkdirs() }
            values.put(MediaStore.MediaColumns.DATA, File(givyDir, fileName).absolutePath)
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

    private companion object {
        const val PARALLEL_CONNECTIONS = 4
        const val MIN_SIZE_FOR_PARALLEL = 2 * 1024 * 1024L // 2 MB — below this, splitting isn't worth the overhead
        const val COPY_BUFFER_BYTES = 64 * 1024 // 64 KB — fewer syscalls than the previous 8 KB default
    }
}
