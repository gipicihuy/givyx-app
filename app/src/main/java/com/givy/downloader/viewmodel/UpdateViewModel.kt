package com.givy.downloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.givy.downloader.updater.ApkInstaller
import com.givy.downloader.updater.UpdateChecker
import com.givy.downloader.updater.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the "update available" banner shown at the top of the screen. */
sealed class UpdateUiState {
    data object Hidden : UpdateUiState()
    data class Available(val downloadUrl: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data class ReadyToInstall(val filePath: String) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = UpdateChecker()
    private val installer = ApkInstaller(application)

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** Call once when the screen first appears — silent, no UI unless an update exists. */
    fun checkForUpdateSilently() {
        viewModelScope.launch {
            when (val result = checker.checkForUpdate()) {
                is UpdateCheckResult.Available ->
                    _updateState.value = UpdateUiState.Available(result.apkDownloadUrl)
                is UpdateCheckResult.UpToDate, is UpdateCheckResult.Error ->
                    _updateState.value = UpdateUiState.Hidden // fail quiet — this is a background check
            }
        }
    }

    fun downloadAndInstall(url: String) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(progress = -1)
            val apkFile = installer.downloadUpdate(url) { progress ->
                _updateState.value = UpdateUiState.Downloading(progress)
            }

            _updateState.value = if (apkFile != null) {
                UpdateUiState.ReadyToInstall(apkFile.absolutePath)
            } else {
                UpdateUiState.Error("Gagal mengunduh update.")
            }
        }
    }

    fun promptInstall(filePath: String) {
        installer.promptInstall(java.io.File(filePath))
    }

    fun dismiss() {
        _updateState.value = UpdateUiState.Hidden
    }
}
