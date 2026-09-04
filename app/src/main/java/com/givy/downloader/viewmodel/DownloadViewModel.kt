package com.givy.downloader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.givy.downloader.downloader.DownloadResult
import com.givy.downloader.downloader.FileDownloader
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
    data class Downloading(val progress: Int) : DownloadUiState() // -1 = indeterminate
    data class Success(val uri: Uri, val fileName: String) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    // Swap ScraperProvider.get() for your own implementation once it's ready —
    // nothing here needs to change.
    private val scraper = ScraperProvider.get()
    private val downloader = FileDownloader(application)

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun startDownload(rawUrl: String) {
        val url = rawUrl.trim()

        if (url.isEmpty()) {
            _uiState.value = DownloadUiState.Error("URL tidak boleh kosong.")
            return
        }
        if (!isLikelyUrl(url)) {
            _uiState.value = DownloadUiState.Error("URL tidak valid. Pastikan link TikTok lengkap (https://...).")
            return
        }

        viewModelScope.launch {
            _uiState.value = DownloadUiState.Resolving

            when (val result = scraper.resolve(url)) {
                is ScraperResult.Error -> {
                    _uiState.value = DownloadUiState.Error(result.message)
                }
                is ScraperResult.Success -> {
                    _uiState.value = DownloadUiState.Downloading(-1)
                    val downloadResult = downloader.download(
                        url = result.mediaUrl,
                        fileName = result.suggestedFileName,
                        isAudioOnly = result.isAudioOnly
                    ) { progress ->
                        _uiState.value = DownloadUiState.Downloading(progress)
                    }

                    _uiState.value = when (downloadResult) {
                        is DownloadResult.Success ->
                            DownloadUiState.Success(downloadResult.savedUri, downloadResult.fileName)
                        is DownloadResult.Error ->
                            DownloadUiState.Error(downloadResult.message)
                    }
                }
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
