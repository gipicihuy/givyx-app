package com.givy.downloader.scraper

/**
 * One downloadable variant of the resolved media (e.g. "HD, no watermark",
 * "Watermarked", "Audio only"). The UI shows these as pickable options after
 * a link is resolved, instead of downloading the first thing found.
 *
 * @param id stable-enough identifier to key the option in the UI (e.g. "HD-video").
 * @param label human-readable text shown on the option button, e.g. "HD (No Watermark)".
 * @param quality free-form quality tag from the source ("HD" | "Normal" | "Watermark" | ...).
 * @param isAudioOnly true if this option is an audio-only track.
 * @param mediaUrl direct, ready-to-download URL — a plain HTTP GET must be able to stream it.
 */
data class MediaOption(
    val id: String,
    val label: String,
    val quality: String,
    val isAudioOnly: Boolean,
    val mediaUrl: String
)

/**
 * The output of a [TikTokScraper] call.
 *
 * This is the ONLY thing the downloader/UI knows about. It has no idea how
 * the direct media URL(s) were obtained (API call, HTML scraping, third-party
 * service, etc.) — that is entirely the scraper's responsibility.
 */
sealed class ScraperResult {

    /**
     * @param title video caption/title, used as the default file name and shown in the preview.
     * @param thumbnailUrl preview image URL, or null if the source didn't provide one.
     * @param options every downloadable variant the scraper found. Must not be empty —
     *                 return [Error] instead if nothing downloadable was found.
     */
    data class Success(
        val title: String,
        val thumbnailUrl: String?,
        val options: List<MediaOption>
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
