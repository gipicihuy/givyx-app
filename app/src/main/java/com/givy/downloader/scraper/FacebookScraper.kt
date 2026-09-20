package com.givy.downloader.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Scraper for Facebook videos.
 *
 * Detects Facebook video URLs (facebook.com/watch, fb.watch, share/v/...),
 * then uses a third-party service to resolve the actual download URL.
 */
class FacebookScraper {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val facebookUrlPattern = Pattern.compile(
        """(?:https?://)?(?:www\.|m\.|web\.)?(?:facebook\.com|fb\.watch)/(?:watch|share/v/|.*?/videos/|\w+/videos/|reel/|story/)?"""
    )

    fun isFacebookUrl(url: String): Boolean {
        return url.contains("facebook.com") || url.contains("fb.watch")
    }

    suspend fun resolve(facebookUrl: String): ScraperResult = withContext(Dispatchers.IO) {
        if (!isFacebookUrl(facebookUrl)) {
            return@withContext ScraperResult.Error("URL yang dimasukkan bukan URL Facebook yang valid.")
        }

        try {
            // Resolve fb.watch short URLs to full facebook.com URLs
            val resolvedUrl = resolveFbWatch(facebookUrl)

            // Try multiple services
            val services = listOf(
                ::resolveViaSnapsave,
                ::resolveViaSaveFrom
            )

            for (service in services) {
                val result = service(resolvedUrl)
                if (result != null) {
                    return@withContext result
                }
            }

            ScraperResult.Error("Gagal mengambil video. Pastikan video publik dan coba lagi.")
        } catch (e: Exception) {
            ScraperResult.Error(
                e.message ?: "Terjadi kesalahan saat memproses video Facebook.",
                e
            )
        }
    }

    /**
     * Resolves fb.watch short URLs to full facebook.com URLs.
     */
    private fun resolveFbWatch(url: String): String {
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

    /**
     * Resolves download URL via snapsave.app API.
     */
    private fun resolveViaSnapsave(facebookUrl: String): ScraperResult? {
        return try {
            val request = Request.Builder()
                .url("https://snapsave.app/action.php")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .header("Origin", "https://snapsave.app")
                .header("Referer", "https://snapsave.app/")
                .post("url=$facebookUrl".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val html = response.body?.string().orEmpty()

                // Parse the HTML response to extract video URLs
                // Snapsave returns a page with video links embedded
                val videoUrls = mutableListOf<String>()

                // Look for video URLs in the response
                val videoPattern = Pattern.compile("""(https?://[^"'\s]+\.mp4[^"'\s]*)""")
                val matcher = videoPattern.matcher(html)
                while (matcher.find()) {
                    val url = matcher.group(1)
                    if (url != null && !url.contains("thumbnail") && !url.contains("preview")) {
                        videoUrls.add(url)
                    }
                }

                if (videoUrls.isEmpty()) {
                    // Try alternative pattern for data attributes
                    val altPattern = Pattern.compile("""data-src="(https?://[^"]+\.mp4[^"]*)"""")
                    val altMatcher = altPattern.matcher(html)
                    while (altMatcher.find()) {
                        val url = altMatcher.group(1)
                        if (url != null) videoUrls.add(url)
                    }
                }

                if (videoUrls.isEmpty()) return null

                val options = videoUrls.distinct().mapIndexed { index, url ->
                    val quality = when {
                        url.contains("720") || url.contains("hd") -> "HD"
                        url.contains("360") || url.contains("sd") -> "SD"
                        else -> "Normal"
                    }
                    MediaOption(
                        id = "video-$index",
                        label = "Video ($quality)",
                        quality = quality,
                        isAudioOnly = false,
                        mediaUrl = url
                    )
                }

                ScraperResult.Success(
                    title = "Video Facebook",
                    thumbnailUrl = null,
                    options = options
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves download URL via savefrom.net API.
     */
    private fun resolveViaSaveFrom(facebookUrl: String): ScraperResult? {
        return try {
            val request = Request.Builder()
                .url("https://worker.sf-tools.com/savefrom.php")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .header("Origin", "https://savefrom.net")
                .header("Referer", "https://savefrom.net/")
                .post("sf_url=$facebookUrl&sf_submit=&new=2&lang=en&app=&country=en&os=Linux&browser=Chrome&channel=main&sf-ui-request-provider=savefrom".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val body = response.body?.string().orEmpty()

                // Parse JSON response
                val json = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    return null
                }

                val url = json.optString("url", null)
                    ?: json.optJSONObject("data")?.optString("url", null)
                    ?: return null

                val quality = json.optJSONObject("data")?.optString("quality", "Normal") ?: "Normal"

                ScraperResult.Success(
                    title = "Video Facebook",
                    thumbnailUrl = json.optJSONObject("data")?.optString("thumb"),
                    options = listOf(
                        MediaOption(
                            id = "video-0",
                            label = "Video ($quality)",
                            quality = quality,
                            isAudioOnly = false,
                            mediaUrl = url
                        )
                    )
                )
            }
        } catch (_: Exception) {
            null
        }
    }

}
