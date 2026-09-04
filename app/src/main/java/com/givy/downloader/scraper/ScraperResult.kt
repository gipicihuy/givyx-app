package com.givy.downloader.scraper

/**
 * The output of a [TikTokScraper] call.
 *
 * This is the ONLY thing the downloader module knows about. It has no idea how
 * the direct media URL was obtained (API call, HTML scraping, third-party
 * service, etc.) — that is entirely the scraper's responsibility.
 */
sealed class ScraperResult {

    /**
     * @param mediaUrl direct, ready-to-download URL to the video (or audio) file.
     *                 Must be a URL a plain HTTP GET can stream bytes from
     *                 (i.e. already resolved past any redirects/signing your
     *                 scraper needs to do).
     * @param suggestedFileName file name to save on device, WITHOUT extension
     *                          is fine too (the downloader will infer one from
     *                          the response content-type if missing).
     * @param isAudioOnly set true if [mediaUrl] points to an audio-only track.
     */
    data class Success(
        val mediaUrl: String,
        val suggestedFileName: String = "givy_${System.currentTimeMillis()}",
        val isAudioOnly: Boolean = false
    ) : ScraperResult()

    /**
     * @param message human-readable reason, shown directly in the UI's error state.
     * @param cause optional original exception for logging/debugging.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ScraperResult()
}
