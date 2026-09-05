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
 * depends on this interface and on [ScraperResult]/[MediaOption]. That keeps
 * "get the direct media URL(s)" and "download bytes to storage" fully
 * decoupled, so you can swap, rewrite, or update your scraper without
 * touching the download pipeline or the UI at all.
 */
interface TikTokScraper {
    /**
     * Resolve a public TikTok share/video URL into its metadata and every
     * downloadable variant available.
     *
     * @param tiktokUrl the raw URL the user typed/pasted into the app.
     * @return [ScraperResult.Success] with title/thumbnail/options, or
     *         [ScraperResult.Error] with a user-facing message.
     */
    suspend fun resolve(tiktokUrl: String): ScraperResult
}

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
                val thumbnailUrl = doc.select(".video-info img").firstOrNull()?.attr("src")
                    ?.takeIf { it.isNotBlank() }

                val options = doc.select("a.download-btn").mapIndexedNotNull { index, el ->
                    val href = el.attr("href")
                    if (href.isBlank() || !href.startsWith("http")) return@mapIndexedNotNull null

                    val classes = el.className()
                    val isAudio = classes.contains("download-btn-purple")
                    val quality = when {
                        classes.contains("download-btn-green") -> "HD"
                        classes.contains("download-btn-gray") -> "Watermark"
                        else -> "Normal"
                    }
                    val label = when {
                        isAudio -> "Audio (MP3)"
                        quality == "HD" -> "HD - Tanpa Watermark"
                        quality == "Watermark" -> "Video - Dengan Watermark"
                        else -> "Video - Normal"
                    }

                    MediaOption(
                        id = "$quality-$index",
                        label = label,
                        quality = quality,
                        isAudioOnly = isAudio,
                        mediaUrl = href
                    )
                }

                // Slideshow ("slide foto") posts don't have a video at all — tiktokio
                // renders each photo as an <img> inside the result instead of a
                // download-btn link. We pick those up separately by scanning for
                // image tags in the likely slideshow containers and treating each
                // one as its own downloadable option, since there's no single
                // "video-btn"-style link to grab like there is for videos.
                //
                // NOTE: this is a best-effort selector based on how these clone
                // sites commonly render slideshow results — I couldn't load
                // tiktokio.com live from this sandbox to confirm the exact markup
                // for a slide post, so if it doesn't pick up photos on a real
                // slideshow link, the selector below is the first place to adjust
                // (inspect the actual response HTML for the img/container classes).
                val slidePhotoOptions = doc.select(
                    ".video-info img, .photo-list img, .images img, [class*=slide] img, [class*=photo] img"
                )
                    .mapNotNull { img ->
                        (img.attr("data-src").ifBlank { img.attr("src") })
                            .takeIf { it.isNotBlank() && it.startsWith("http") }
                    }
                    .filterNot { it == thumbnailUrl } // don't re-list the cover thumbnail as a "photo"
                    .distinct()
                    .mapIndexed { index, photoUrl ->
                        MediaOption(
                            id = "photo-$index",
                            label = "Foto ${index + 1}",
                            quality = "Foto",
                            isAudioOnly = false,
                            isImage = true,
                            mediaUrl = photoUrl
                        )
                    }

                val allOptions = options + slidePhotoOptions

                if (allOptions.isEmpty()) {
                    return@withContext ScraperResult.Error(
                        "Gagal mengambil media. Pastikan URL video publik."
                    )
                }

                // Nicer default ordering: best video quality first, photos after
                // video options, audio last.
                val ordered = allOptions.sortedBy { opt ->
                    when {
                        opt.isAudioOnly -> 3
                        opt.isImage -> 2
                        opt.quality == "HD" -> 0
                        else -> 1
                    }
                }

                ScraperResult.Success(
                    title = title.ifBlank { "Video TikTok" },
                    thumbnailUrl = thumbnailUrl,
                    options = ordered
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
