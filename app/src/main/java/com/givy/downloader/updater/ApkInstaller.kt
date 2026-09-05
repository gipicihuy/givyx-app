package com.givy.downloader.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads an update APK to app-private cache and hands it to the system
 * installer. This is the closest a sideloaded (non-Play-Store) app can get
 * to "auto update": the user still taps the standard Android install
 * confirmation once, but there's no manual browser download or file-manager
 * hunting involved.
 */
class ApkInstaller(private val context: Context) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param onProgress 0..100, or -1 if the size isn't known yet.
     * @return the downloaded APK file, or null on failure.
     */
    suspend fun downloadUpdate(url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(updatesDir, "givy-update.apk")

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    val total = body.contentLength()
                    var copied = 0L

                    outFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copied += read
                                onProgress(
                                    if (total > 0) ((copied * 100) / total).toInt().coerceIn(0, 100) else -1
                                )
                            }
                        }
                    }
                }
                outFile
            } catch (_: Exception) {
                null
            }
        }

    /** Launches the system package installer for the given downloaded APK file. */
    fun promptInstall(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
