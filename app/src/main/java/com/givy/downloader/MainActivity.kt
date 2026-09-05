package com.givy.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.givy.downloader.ui.theme.GivyDownloaderTheme
import com.givy.downloader.ui.theme.GivyError
import com.givy.downloader.ui.theme.GivyOnSurfaceMuted
import com.givy.downloader.ui.theme.GivySuccess
import com.givy.downloader.ui.theme.GivySurface
import com.givy.downloader.viewmodel.DownloadUiState
import com.givy.downloader.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GivyDownloaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GivyDownloaderScreen()
                }
            }
        }
    }
}

@Composable
fun GivyDownloaderScreen(viewModel: DownloadViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var url by remember { mutableStateOf("") }

    val isBusy = uiState is DownloadUiState.Resolving || uiState is DownloadUiState.Downloading

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Givy Downloader",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tempel link TikTok, unduh videonya.",
                style = MaterialTheme.typography.bodyMedium,
                color = GivyOnSurfaceMuted
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://www.tiktok.com/@user/video/...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Link, contentDescription = null)
                },
                trailingIcon = {
                    if (url.isNotEmpty() && !isBusy) {
                        IconButton(onClick = { url = "" }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                singleLine = true,
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GivySurface,
                    unfocusedContainerColor = GivySurface,
                    disabledContainerColor = GivySurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startDownload(url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isBusy && url.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel(uiState),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            StatusPanel(uiState = uiState, onDismiss = { viewModel.reset() })
        }
    }
}

private fun statusLabel(state: DownloadUiState): String = when (state) {
    is DownloadUiState.Idle -> "Download"
    is DownloadUiState.Resolving -> "Memproses link..."
    is DownloadUiState.Downloading -> if (state.progress >= 0) "Mengunduh ${state.progress}%" else "Mengunduh..."
    is DownloadUiState.Success -> "Download"
    is DownloadUiState.Error -> "Coba Lagi"
}

@Composable
private fun StatusPanel(uiState: DownloadUiState, onDismiss: () -> Unit) {
    when (uiState) {
        is DownloadUiState.Idle -> Unit

        is DownloadUiState.Resolving -> StatusCard(borderColor = MaterialTheme.colorScheme.outline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Mengambil sumber video dari link kamu...",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is DownloadUiState.Downloading -> StatusCard(borderColor = MaterialTheme.colorScheme.outline) {
            Column {
                Text(
                    text = "Sedang mengunduh...",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { uiState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.progress}%",
                        color = GivyOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        is DownloadUiState.Success -> StatusCard(borderColor = GivySuccess) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = GivySuccess)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Berhasil disimpan",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.fileName,
                            color = GivyOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Tutup")
                }
            }
        }

        is DownloadUiState.Error -> StatusCard(borderColor = GivyError) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = GivyError)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Gagal mengunduh",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.message,
                            color = GivyOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Tutup")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GivySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
