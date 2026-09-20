package com.givy.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VideoFile
import com.givy.downloader.ui.theme.FacebookIcon
import com.givy.downloader.ui.theme.TikTokIcon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.givy.downloader.scraper.MediaOption
import com.givy.downloader.ui.theme.GivyDownloaderTheme
import com.givy.downloader.ui.theme.GivyError
import com.givy.downloader.ui.theme.GivyOnSurfaceMuted
import com.givy.downloader.ui.theme.GivySuccess
import com.givy.downloader.ui.theme.GivySurface
import com.givy.downloader.ui.theme.GivySurfaceVariant
import com.givy.downloader.viewmodel.DownloadUiState
import com.givy.downloader.viewmodel.DownloadViewModel
import com.givy.downloader.viewmodel.UpdateUiState
import com.givy.downloader.viewmodel.UpdateViewModel

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GivyDownloaderScreen(
    viewModel: DownloadViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    var url by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdateSilently()
    }

    val isResolving = uiState is DownloadUiState.Resolving
    val isDownloading = uiState is DownloadUiState.Downloading
    val isBusy = isResolving || isDownloading

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            UpdateBanner(
                state = updateState,
                onUpdateClick = { downloadUrl -> updateViewModel.downloadAndInstall(downloadUrl) },
                onInstallClick = { path -> updateViewModel.promptInstall(path) },
                onDismiss = { updateViewModel.dismiss() }
            )

            BrandHeader()

            Spacer(modifier = Modifier.height(32.dp))

            SectionLabel(text = "TAUTAN")
            Spacer(modifier = Modifier.height(10.dp))

            UrlInput(
                value = url,
                onValueChange = { url = it },
                onClear = { url = "" },
                enabled = !isBusy
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnalyzeButton(
                onClick = { viewModel.resolveLink(url) },
                enabled = !isBusy && url.isNotBlank(),
                isResolving = isResolving
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = uiState !is DownloadUiState.Idle,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
            ) {
                StatusPanel(
                    uiState = uiState,
                    onDismiss = { viewModel.reset() },
                    onPickOption = { option, title -> viewModel.downloadOption(option, title) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SectionLabel(text = "DIDUKUNG")
            Spacer(modifier = Modifier.height(12.dp))

            SupportedPlatforms()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "G",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "GIVY DOWNLOADER",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tempel link TikTok atau Facebook, pilih kualitas, unduh.",
            style = MaterialTheme.typography.bodyMedium,
            color = GivyOnSurfaceMuted
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = GivyOnSurfaceMuted,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "https://www.tiktok.com/... atau facebook.com/...",
                color = GivyOnSurfaceMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = GivyOnSurfaceMuted
            )
        },
        trailingIcon = {
            if (value.isNotEmpty() && enabled) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Bersihkan",
                        tint = GivyOnSurfaceMuted
                    )
                }
            }
        },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GivySurface,
            unfocusedContainerColor = GivySurface,
            disabledContainerColor = GivySurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            disabledTextColor = MaterialTheme.colorScheme.onBackground
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun AnalyzeButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isResolving: Boolean
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isResolving) "MEMPROSES..." else "AMBIL MEDIA",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedPlatforms() {
    val platforms = listOf(
        "TikTok" to TikTokIcon,
        "Facebook" to FacebookIcon
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        platforms.forEach { (name, icon) ->
            Surface(
                modifier = Modifier,
                shape = RoundedCornerShape(4.dp),
                color = GivySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    state: UpdateUiState,
    onUpdateClick: (String) -> Unit,
    onInstallClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Hidden -> Unit

        is UpdateUiState.Available -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = GivySurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update tersedia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Outlined.Clear, contentDescription = "Tutup")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onUpdateClick(state.downloadUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "UPDATE",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        is UpdateUiState.Downloading -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = GivySurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (state.progress >= 0) "Mengunduh update... ${state.progress}%" else "Mengunduh update...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        is UpdateUiState.ReadyToInstall -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = GivySurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Update siap dipasang",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onInstallClick(state.filePath) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        "INSTALL",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        is UpdateUiState.Error -> Unit
    }
}

@Composable
private fun StatusPanel(
    uiState: DownloadUiState,
    onDismiss: () -> Unit,
    onPickOption: (MediaOption, String) -> Unit
) {
    when (uiState) {
        is DownloadUiState.Idle -> Unit

        is DownloadUiState.Resolving -> StatusCard(accentColor = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Mengambil info media dari link kamu...",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is DownloadUiState.Preview -> PreviewCard(
            title = uiState.title,
            thumbnailUrl = uiState.thumbnailUrl,
            options = uiState.options,
            onPickOption = { option -> onPickOption(option, uiState.title) },
            onDismiss = onDismiss
        )

        is DownloadUiState.Downloading -> StatusCard(accentColor = MaterialTheme.colorScheme.primary) {
            Column {
                Text(
                    text = "Mengunduh: ${uiState.optionLabel}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { uiState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${uiState.progress}%",
                            color = GivyOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        is DownloadUiState.Success -> StatusCard(accentColor = GivySuccess) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = GivySuccess
                    )
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
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Clear, contentDescription = "Tutup")
                }
            }
        }

        is DownloadUiState.Error -> StatusCard(accentColor = GivyError) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = GivyError
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Gagal",
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
                    Icon(imageVector = Icons.Outlined.Clear, contentDescription = "Tutup")
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    thumbnailUrl: String?,
    options: List<MediaOption>,
    onPickOption: (MediaOption) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = GivySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(GivySurfaceVariant)
            ) {
                if (thumbnailUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(thumbnailUrl),
                        contentDescription = "Thumbnail video",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Tutup",
                        tint = Color.White
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${options.size} pilihan kualitas tersedia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GivyOnSurfaceMuted
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PILIH KUALITAS",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = GivyOnSurfaceMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onPickOption(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = when {
                                option.isAudioOnly -> Icons.Outlined.AudioFile
                                option.isImage -> Icons.Outlined.Image
                                option.quality == "HD" -> Icons.Outlined.HighQuality
                                else -> Icons.Outlined.VideoFile
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.label,
                            modifier = Modifier.weight(1f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = GivySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
