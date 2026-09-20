package com.givy.downloader.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FacebookScraper {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isFacebookUrl(url: String): Boolean {
        return url.contains("facebook.com") || url.contains("fb.watch")
    }

    suspend fun resolve(facebookUrl: String): ScraperResult = withContext(Dispatchers.IO) {
        if (!isFacebookUrl(facebookUrl)) {
            return@withContext ScraperResult.Error("URL yang dimasukkan bukan URL Facebook yang valid.")
        }

        try {
            val resolvedUrl = resolveShortUrl(facebookUrl)
            fetchFromF4Facebook(resolvedUrl)
        } catch (e: Exception) {
            ScraperResult.Error(
                e.message ?: "Terjadi kesalahan saat memproses video Facebook.",
                e
            )
        }
    }

    private fun resolveShortUrl(url: String): String {
        if (!url.contains("fb.watch")) return url
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun fetchFromF4Facebook(facebookUrl: String): ScraperResult {
        val payload = """{"url":"$facebookUrl"}"""
        val mediaType = "application/json".toMediaType()

        val request = Request.Builder()
            .url("https://f4facebook.com/download")
            .header("Content-Type", "application/json")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
            .header("Origin", "https://f4facebook.com")
            .header("Referer", "https://f4facebook.com/")
            .post(payload.toRequestBody(mediaType))
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Gagal menghubungi server: HTTP ${resp.code}")
            }

            val body = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw Exception("Respons tidak valid dari server.")
            }

            val status = json.optString("status", "")
            if (status != "success") {
                val error = json.optString("error", "Server gagal memproses URL.")
                throw Exception(error)
            }

            val downloadUrl = json.optString("download_url", "")
            if (downloadUrl.isBlank()) {
                throw Exception("Tidak ada link download yang ditemukan.")
            }

            val meta = json.optJSONObject("meta")
            val title = meta?.optString("title", "")?.takeIf { it.isNotBlank() }
                ?: "Video Facebook"
            val thumbnail = meta?.optString("thumbnail", "")?.takeIf { it.isNotBlank() }

            val options = listOf(
                MediaOption(
                    id = "fb-video",
                    label = "Video (MP4)",
                    quality = "HD",
                    isAudioOnly = false,
                    mediaUrl = downloadUrl
                )
            )

            return ScraperResult.Success(
                title = title,
                thumbnailUrl = thumbnail,
                options = options
            )
        }
    }
}
