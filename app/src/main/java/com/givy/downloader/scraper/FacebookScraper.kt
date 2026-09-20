package com.givy.downloader.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
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
            fetchFromFdownloader(resolvedUrl)
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

    private fun fetchFromFdownloader(facebookUrl: String): ScraperResult {
        val payload = """{"url":"$facebookUrl"}"""
        val mediaType = "application/json".toMediaType()

        val request = Request.Builder()
            .url("https://fdownloader.vn/api/download")
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Locale", "en")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
            .header("Origin", "https://fdownloader.vn")
            .header("Referer", "https://fdownloader.vn/")
            .post(payload.toRequestBody(mediaType))
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Gagal menghubungi server: HTTP ${resp.code}")
            }

            val body = resp.body?.string().orEmpty()
            val json = try {
                org.json.JSONObject(body)
            } catch (_: Exception) {
                throw Exception("Respons tidak valid dari server.")
            }

            if (!json.optBoolean("success", false)) {
                throw Exception("Server gagal memproses URL.")
            }

            val html = json.optString("html", "")
            if (html.isBlank()) {
                throw Exception("Tidak ada data video yang ditemukan.")
            }

            val doc = Jsoup.parse(html)

            val thumbnailUrl = doc.select("img.media-result__thumb").firstOrNull()
                ?.attr("src")
                ?.takeIf { it.isNotBlank() }

            val title = doc.select("h3.media-result__title").firstOrNull()
                ?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Video Facebook"

            val options = doc.select("a.btn-download").mapNotNull { link ->
                val href = link.attr("href")
                if (href.isBlank() || !href.startsWith("http")) return@mapNotNull null

                val parent = link.closest(".media-item") ?: return@mapNotNull null
                val badge = parent.select(".badge").firstOrNull()?.text()?.trim().orEmpty()
                val quality = parent.select(".media-item__quality").firstOrNull()?.text()?.trim().orEmpty()
                val ext = parent.select(".media-item__ext").firstOrNull()?.text()?.trim().orEmpty()

                val isRender = link.hasClass("btn-render")
                if (isRender) return@mapNotNull null

                val label = buildString {
                    append(badge.ifBlank { quality.ifBlank { ext.ifBlank { "Video" } } })
                    if (quality.isNotBlank() && quality != badge) append(" ($quality)")
                    if (ext.isNotBlank()) append(" .$ext")
                }

                MediaOption(
                    id = "fb-${href.hashCode()}",
                    label = label,
                    quality = quality.ifBlank { badge },
                    isAudioOnly = badge.contains("MP3", ignoreCase = true),
                    mediaUrl = href
                )
            }

            if (options.isEmpty()) {
                throw Exception("Tidak ada link download yang tersedia. Video mungkin butuh render/merge.")
            }

            val ordered = options.sortedBy { opt ->
                when {
                    opt.isAudioOnly -> 3
                    opt.quality.contains("HD", ignoreCase = true) -> 0
                    opt.quality.contains("FHD", ignoreCase = true) -> 0
                    else -> 1
                }
            }

            return ScraperResult.Success(
                title = title,
                thumbnailUrl = thumbnailUrl,
                options = ordered
            )
        }
    }
}
