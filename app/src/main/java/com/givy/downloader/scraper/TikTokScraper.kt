package com.givy.downloader.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Contract the rest of the app (UI + downloader) talks to.
 *
 * The downloader module never imports anything TikTok-specific — it only
 * depends on this interface and on [ScraperResult]. That keeps "get the
 * direct media URL" and "download bytes to storage" fully decoupled, so you
 * can swap, rewrite, or update your scraper without touching the download
 * pipeline or the UI at all.
 */
interface TikTokScraper {
    /**
     * Resolve a public TikTok share/video URL into a direct, downloadable
     * media URL.
     *
     * @param tiktokUrl the raw URL the user typed/pasted into the app.
     * @return [ScraperResult.Success] with a direct media URL, or
     *         [ScraperResult.Error] with a user-facing message.
     */
    suspend fun resolve(tiktokUrl: String): ScraperResult
}

/**
 * One downloadable option returned by TikTokIO for a given video (there's
 * usually a watermark version, an HD/no-watermark version, and an audio-only
 * version).
 */
private data class MediaOption(
    val quality: String,   // "HD" | "Normal" | "Watermark"
    val type: String,      // "video" | "audio"
    val label: String,
    val url: String
)

/**
 * Scraper backed by tiktokio.com's public resolver endpoint.
 *
 * Ported from a Node.js script (node-fetch + cheerio) to Kotlin
 * (OkHttp + Jsoup, the JVM equivalent of cheerio) so it can run natively
 * inside the Android app without a JS runtime.
 * Original script credit: febry.is-a.dev (github: vandebry10-star).
 */
class TikTokIoScraper : TikTokScraper {

    private val baseUrl = "https://tiktokio.com"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun resolve(tiktokUrl: String): ScraperResult = withContext(Dispatchers.IO) {
        if (!tiktokUrl.contains("tiktok.com")) {
            return@withContext ScraperResult.Error("URL yang dimasukkan bukan URL TikTok yang valid.")
        }

        try {
            val payload = JSONObject().apply {
                put("prefix", "tiktokio.com")
                put("vid", tiktokUrl)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/v1/tk/html")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .header("Referer", "$baseUrl/id/")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ScraperResult.Error(
                        "Gagal menghubungi TikTokIO: HTTP ${response.code}"
                    )
                }

                val html = response.body?.string().orEmpty()
                val doc = Jsoup.parse(html)

                val title = doc.select(".video-info h3").firstOrNull()?.text()?.trim().orEmpty()

                val medias = doc.select("a.download-btn").mapNotNull { el ->
                    val href = el.attr("href")
                    if (href.isBlank() || !href.startsWith("http")) return@mapNotNull null

                    val classes = el.className()
                    val type = if (classes.contains("download-btn-purple")) "audio" else "video"
                    val quality = when {
                        classes.contains("download-btn-green") -> "HD"
                        classes.contains("download-btn-gray") -> "Watermark"
                        else -> "Normal"
                    }
                    MediaOption(quality = quality, type = type, label = el.text().trim(), url = href)
                }

                if (medias.isEmpty()) {
                    return@withContext ScraperResult.Error(
                        "Gagal mengambil media. Pastikan URL video publik."
                    )
                }

                // Prefer the best non-watermarked video; fall back gracefully.
                val chosen = medias.firstOrNull { it.type == "video" && it.quality == "HD" }
                    ?: medias.firstOrNull { it.type == "video" && it.quality == "Normal" }
                    ?: medias.firstOrNull { it.type == "video" }
                    ?: medias.first()

                val fileName = title.ifBlank { "givy_${System.currentTimeMillis()}" }

                ScraperResult.Success(
                    mediaUrl = chosen.url,
                    suggestedFileName = fileName,
                    isAudioOnly = chosen.type == "audio"
                )
            }
        } catch (e: Exception) {
            ScraperResult.Error(
                e.message ?: "Terjadi kesalahan saat memproses media dari TikTokIO.",
                e
            )
        }
    }
}

/**
 * Single place that decides which [TikTokScraper] implementation the app
 * uses. Point this at a different class if you swap the backend later —
 * nothing else in the app needs to change.
 */
object ScraperProvider {
    fun get(): TikTokScraper = TikTokIoScraper()
}
