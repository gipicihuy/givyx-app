package com.givy.downloader.updater

import com.givy.downloader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the update check found. */
sealed class UpdateCheckResult {
    data class Available(val apkDownloadUrl: String, val commitSha: String) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks the app's own GitHub Releases page (tag "latest", published by
 * .github/workflows/build.yml on every push to main) for a newer build than
 * the one currently installed.
 *
 * Comparison is done by commit SHA rather than a version number, since the
 * release always reuses the "latest" tag: the workflow writes the commit SHA
 * into the release body, and the running app knows its own SHA via
 * [BuildConfig.GIT_COMMIT] (set by CI with `-PgitSha=...`).
 */
class UpdateChecker {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/tags/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("Gagal cek update: HTTP ${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val body = json.optString("body", "")
                val remoteSha = Regex("commit ([0-9a-fA-F]{7,40})").find(body)?.groupValues?.get(1)

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                if (remoteSha == null || apkUrl == null) {
                    return@withContext UpdateCheckResult.Error("Format release tidak dikenali.")
                }

                val localSha = BuildConfig.GIT_COMMIT
                val isUpToDate = localSha != "local" &&
                    (remoteSha.startsWith(localSha) || localSha.startsWith(remoteSha))

                if (isUpToDate) {
                    UpdateCheckResult.UpToDate
                } else {
                    UpdateCheckResult.Available(apkDownloadUrl = apkUrl, commitSha = remoteSha)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Gagal cek update.")
        }
    }
}
