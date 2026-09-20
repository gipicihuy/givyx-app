package com.givy.downloader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.givy.downloader.downloader.DownloadResult
import com.givy.downloader.downloader.FileDownloader
import com.givy.downloader.scraper.MediaOption
import com.givy.downloader.scraper.ScraperProvider
import com.givy.downloader.scraper.ScraperResult
import com.givy.downloader.scraper.SpotifyScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the UI needs to render at any given moment.
 */
sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data object Resolving : DownloadUiState()

    /** Link resolved: show thumbnail/title and let the user pick a quality. */
    data class Preview(
        val title: String,
        val thumbnailUrl: String?,
        val options: List<MediaOption>
    ) : DownloadUiState()

    data class Downloading(val progress: Int, val optionLabel: String) : DownloadUiState() // progress -1 = indeterminate
    data class Success(val uri: Uri, val fileName: String) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val tiktokScraper = ScraperProvider.get()
    private val spotifyScraper = SpotifyScraper()
    private val downloader = FileDownloader(application)

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    /** Auto-detected platform type for UI hints. */
    private var detectedPlatform: Platform = Platform.Unknown

    enum class Platform { TikTok, Spotify, Unknown }

    /** Step 1: resolve the link into a preview + list of quality options. */
    fun resolveLink(rawUrl: String) {
        val url = rawUrl.trim()

        if (url.isEmpty()) {
            _uiState.value = DownloadUiState.Error("URL tidak boleh kosong.")
            return
        }
        if (!isLikelyUrl(url)) {
            _uiState.value = DownloadUiState.Error(
                "URL tidak valid. Paste link TikTok atau Spotify yang lengkap (https://...)."
            )
            return
        }

        // Auto-detect platform
        detectedPlatform = when {
            spotifyScraper.isSpotifyUrl(url) -> Platform.Spotify
            url.contains("tiktok.com") -> Platform.TikTok
            else -> Platform.Unknown
        }

        viewModelScope.launch {
            _uiState.value = DownloadUiState.Resolving

            val result = when (detectedPlatform) {
                Platform.Spotify -> spotifyScraper.resolve(url)
                Platform.TikTok -> tiktokScraper.resolve(url)
                Platform.Unknown -> {
                    // Try TikTok as fallback
                    tiktokScraper.resolve(url)
                }
            }

            _uiState.value = when (result) {
                is ScraperResult.Error -> DownloadUiState.Error(result.message)
                is ScraperResult.Success -> DownloadUiState.Preview(
                    title = result.title,
                    thumbnailUrl = result.thumbnailUrl,
                    options = result.options
                )
            }
        }
    }

    /** Step 2: user picked a quality option from the preview — download it. */
    fun downloadOption(option: MediaOption, suggestedFileName: String) {
        // Slideshow posts have several photo options that would otherwise all
        // share the same base file name (from the post's caption) — append a
        // distinguishing suffix so each photo lands as its own file instead
        // of relying on the OS to auto-number colliding names.
        val fileName = if (option.isImage) {
            "$suggestedFileName-${option.label.lowercase().replace(" ", "-")}"
        } else {
            suggestedFileName
        }

        viewModelScope.launch {
            _uiState.value = DownloadUiState.Downloading(progress = -1, optionLabel = option.label)

            val downloadResult = downloader.download(
                url = option.mediaUrl,
                fileName = fileName,
                isAudioOnly = option.isAudioOnly,
                isImage = option.isImage,
                isSpotify = detectedPlatform == Platform.Spotify
            ) { progress ->
                _uiState.value = DownloadUiState.Downloading(progress = progress, optionLabel = option.label)
            }

            _uiState.value = when (downloadResult) {
                is DownloadResult.Success ->
                    DownloadUiState.Success(downloadResult.savedUri, downloadResult.fileName)
                is DownloadResult.Error ->
                    DownloadUiState.Error(downloadResult.message)
            }
        }
    }

    fun reset() {
        _uiState.value = DownloadUiState.Idle
    }

    private fun isLikelyUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}
