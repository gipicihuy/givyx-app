package com.givy.downloader.scraper

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
 * ============================================================================
 *  PASTE YOUR OWN TIKTOK SCRAPER HERE
 * ============================================================================
 *
 * This is the single integration point for your scraper. Replace the body of
 * [resolve] below with your own logic (API call, HTML parsing, headless
 * client, whatever you use). You do NOT need to touch any other file in this
 * project — [MainActivity]/[com.givy.downloader.viewmodel.DownloadViewModel]
 * already call this class through the [TikTokScraper] interface above.
 *
 * Requirements for whatever you put here:
 *   1. It must be a `suspend` function (do network/parsing work with
 *      coroutines — e.g. wrap blocking calls in `withContext(Dispatchers.IO)`).
 *   2. On success, return `ScraperResult.Success(mediaUrl = "...")` with a
 *      direct URL that a plain HTTP GET can download (no extra auth headers
 *      needed downstream — resolve those yourself before returning, or extend
 *      ScraperResult if you need to pass custom headers through).
 *   3. On failure, return `ScraperResult.Error("readable message")` instead of
 *      throwing — the UI reads this directly and shows it to the user.
 *   4. If your scraper needs a secret (API key, PAT, cookie, etc.), do NOT
 *      hardcode it here. See the "Secrets" note below.
 *
 * --------------------------------------------------------------------------
 * Secrets / credentials your scraper needs (API keys, tokens, cookies, etc.):
 *   - NEVER commit them as string literals in this file.
 *   - Put them in `local.properties` (already git-ignored) as e.g.
 *         TIKTOK_SCRAPER_TOKEN=xxxxx
 *     then read them via BuildConfig — add this to app/build.gradle.kts:
 *
 *         val localProps = java.util.Properties().apply {
 *             val f = rootProject.file("local.properties")
 *             if (f.exists()) load(f.inputStream())
 *         }
 *         android {
 *             defaultConfig {
 *                 buildConfigField(
 *                     "String", "TIKTOK_SCRAPER_TOKEN",
 *                     "\"${localProps.getProperty("TIKTOK_SCRAPER_TOKEN", "")}\""
 *                 )
 *             }
 *             buildFeatures { buildConfig = true }
 *         }
 *
 *     Then use `BuildConfig.TIKTOK_SCRAPER_TOKEN` down here.
 *   - For CI (GitHub Actions), add the same value as a repository Secret and
 *     write it into local.properties (or pass as a Gradle -P property) in a
 *     build step — never put it directly in the workflow YAML or commit it.
 * ============================================================================
 */
class YourTikTokScraper : TikTokScraper {
    override suspend fun resolve(tiktokUrl: String): ScraperResult {
        // TODO: replace with your real implementation.
        return ScraperResult.Error(
            "Scraper belum diisi. Tempel implementasi kamu di " +
                "YourTikTokScraper.resolve() (file TikTokScraper.kt)."
        )
    }
}

/**
 * Single place that decides which [TikTokScraper] implementation the app
 * uses. Point this at your class once it's ready — nothing else needs to
 * change.
 */
object ScraperProvider {
    fun get(): TikTokScraper = YourTikTokScraper()
}
