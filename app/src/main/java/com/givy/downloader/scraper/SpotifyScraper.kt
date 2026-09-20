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
 * Scraper for Spotify tracks/episodes.
 *
 * Detects Spotify URLs (open.spotify.com/track/..., spotify.link/...),
 * fetches metadata via Spotify's oEmbed API, then uses a third-party
 * service to resolve the actual download URL.
 */
class SpotifyScraper {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val spotifyUrlPattern = Pattern.compile(
        """(?:https?://)?(?:open\.spotify\.com|spotify\.link)/(track|episode|playlist|album)/([a-zA-Z0-9]+)"""
    )

    /**
     * Checks if a URL is a Spotify link.
     */
    fun isSpotifyUrl(url: String): Boolean {
        return spotifyUrlPattern.matcher(url).find()
    }

    /**
     * Resolves a Spotify URL into metadata and download options.
     */
    suspend fun resolve(spotifyUrl: String): ScraperResult = withContext(Dispatchers.IO) {
        try {
            // Extract type and ID from URL
            val matcher = spotifyUrlPattern.matcher(spotifyUrl)
            if (!matcher.find()) {
                return@withContext ScraperResult.Error("URL Spotify tidak valid.")
            }

            val contentType = matcher.group(1) ?: "track"
            val contentId = matcher.group(2) ?: ""

            if (contentType == "playlist" || contentType == "album") {
                return@withContext ScraperResult.Error(
                    "Playlist dan album belum didukung. Kirim link lagu/episode individual."
                )
            }

            // Resolve spotify.link redirects to get the full open.spotify.com URL
            val resolvedUrl = resolveSpotifyLink(spotifyUrl)

            // Get metadata via oEmbed
            val metadata = fetchMetadata(resolvedUrl)
            val title = metadata["title"] ?: "Spotify $contentType"
            val thumbnailUrl = metadata["thumbnail"]
            val artist = metadata["artist"] ?: ""

            val displayTitle = if (artist.isNotBlank()) "$artist - $title" else title

            // Get download URL from third-party service
            val downloadUrl = resolveDownloadUrl(resolvedUrl)

            if (downloadUrl.isNullOrBlank()) {
                return@withContext ScraperResult.Error(
                    "Gagal mendapatkan link download. Coba lagi nanti."
                )
            }

            val options = listOf(
                MediaOption(
                    id = "spotify-audio",
                    label = "Audio (MP3)",
                    quality = "High Quality",
                    isAudioOnly = true,
                    mediaUrl = downloadUrl
                )
            )

            ScraperResult.Success(
                title = displayTitle,
                thumbnailUrl = thumbnailUrl,
                options = options
            )
        } catch (e: Exception) {
            ScraperResult.Error(
                e.message ?: "Terjadi kesalahan saat memproses link Spotify.",
                e
            )
        }
    }

    /**
     * Resolves spotify.link short URLs to full open.spotify.com URLs.
     */
    private fun resolveSpotifyLink(url: String): String {
        if (!url.contains("spotify.link")) return url

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
     * Fetches track metadata from Spotify's oEmbed API.
     */
    private fun fetchMetadata(spotifyUrl: String): Map<String, String?> {
        return try {
            val oembedUrl = "https://open.spotify.com/oembed?url=$spotifyUrl"
            val request = Request.Builder()
                .url(oembedUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap()

                val body = response.body?.string() ?: return@use emptyMap()
                val json = JSONObject(body)

                val title = json.optString("title", null)
                val thumbnailUrl = json.optJSONObject("thumbnail_url")
                    ?.optString("url", null)
                    ?: json.optString("thumbnail_url", null)

                // oEmbed title format is usually "Artist - Track Name"
                val parts = title?.split(" - ", limit = 2) ?: listOf(title.orEmpty())

                mapOf(
                    "title" to (parts.getOrNull(1) ?: parts.getOrElse(0) { "" }),
                    "artist" to parts.getOrElse(0) { "" },
                    "thumbnail" to thumbnailUrl
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Uses a third-party service to resolve the actual download URL.
     * Falls back to multiple services if the primary one fails.
     */
    private fun resolveDownloadUrl(spotifyUrl: String): String? {
        // Try multiple services in order
        val services = listOf(
            ::resolveViaSpotifyDown,
            ::resolveViaSpotDL
        )

        for (service in services) {
            val result = service(spotifyUrl)
            if (!result.isNullOrBlank()) return result
        }

        return null
    }

    /**
     * Resolves download URL via spotifydown.com API.
     */
    private fun resolveViaSpotifyDown(spotifyUrl: String): String? {
        return try {
            // Step 1: POST to spotifydown.com to get download links
            val postBody = """{"url":"$spotifyUrl"}"""
            val postRequest = Request.Builder()
                .url("https://spotifydown.com/api/download")
                .header("Content-Type", "application/json")
                .header("Origin", "https://spotifydown.com")
                .header("Referer", "https://spotifydown.com/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .post(postBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)

                // The API returns download links
                val links = json.optJSONArray("links")
                if (links != null && links.length() > 0) {
                    links.getJSONObject(0).optString("url", null)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves download URL via spotdl API (alternative service).
     */
    private fun resolveViaSpotDL(spotifyUrl: String): String? {
        return try {
            // Use savetube.me API as an alternative
            val postBody = """{"url":"$spotifyUrl","format":"mp3"}"""
            val postRequest = Request.Builder()
                .url("https://api.savetube.me/info")
                .header("Content-Type", "application/json")
                .header("Origin", "https://savetube.me")
                .header("Referer", "https://savetube.me/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                .post(postBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@use null

                val downloadUrl = data.optString("download_url", null)
                downloadUrl
            }
        } catch (_: Exception) {
            null
        }
    }
}
