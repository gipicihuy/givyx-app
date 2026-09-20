package com.givy.downloader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.givy.downloader.downloader.DownloadResult
import com.givy.downloader.downloader.FileDownloader
import com.givy.downloader.scraper.FacebookScraper
import com.givy.downloader.scraper.MediaOption
import com.givy.downloader.scraper.ScraperProvider
import com.givy.downloader.scraper.ScraperResult
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

    data class Downloading(val progress: Int, val optionLabel: String) : DownloadUiState()
    data class Success(val uri: Uri, val fileName: String) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val tiktokScraper = ScraperProvider.get()
    private val facebookScraper = FacebookScraper()
    private val downloader = FileDownloader(application)

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun resolveLink(rawUrl: String) {
        val url = rawUrl.trim()

        if (url.isEmpty()) {
            _uiState.value = DownloadUiState.Error("URL tidak boleh kosong.")
            return
        }
        if (!isLikelyUrl(url)) {
            _uiState.value = DownloadUiState.Error(
                "URL tidak valid. Paste link TikTok atau Facebook yang lengkap (https://...)."
            )
            return
        }

        val isFacebook = facebookScraper.isFacebookUrl(url)
        val isTikTok = url.contains("tiktok.com")

        if (!isFacebook && !isTikTok) {
            _uiState.value = DownloadUiState.Error(
                "URL tidak dikenali. Mendukung TikTok dan Facebook."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = DownloadUiState.Resolving

            val result = if (isFacebook) {
                facebookScraper.resolve(url)
            } else {
                tiktokScraper.resolve(url)
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

    fun downloadOption(option: MediaOption, suggestedFileName: String) {
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
                isImage = option.isImage
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
