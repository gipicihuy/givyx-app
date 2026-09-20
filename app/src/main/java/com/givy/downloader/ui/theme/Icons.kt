package com.givy.downloader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TikTokIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TikTok",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(24.0f, 3.389f)
            curveTo(22.639f, 2.028f, 21.082f, 1.261f, 19.282f, 1.261f)
            horizontalLineTo(3.389f)
            curveTo(2.028f, 1.261f, 0.6f, 2.028f, 0.6f, 3.389f)
            verticalLineTo(20.611f)
            curveTo(0.6f, 21.972f, 2.028f, 24.0f, 3.389f, 24.0f)
            horizontalLineTo(20.611f)
            curveTo(21.972f, 24.0f, 24.0f, 21.972f, 24.0f, 20.611f)
            verticalLineTo(3.389f)
            close()

            moveTo(19.883f, 10.467f)
            curveTo(18.349f, 10.517f, 16.929f, 9.956f, 15.658f, 9.077f)
            verticalLineTo(15.161f)
            curveTo(15.658f, 17.866f, 13.802f, 20.029f, 11.172f, 20.635f)
            curveTo(7.604f, 21.453f, 4.604f, 19.231f, 4.223f, 16.144f)
            curveTo(3.825f, 13.055f, 5.569f, 10.308f, 8.587f, 9.697f)
            curveTo(9.175f, 9.575f, 9.796f, 9.547f, 10.255f, 9.559f)
            verticalLineTo(12.82f)
            curveTo(10.135f, 12.786f, 10.007f, 12.763f, 9.882f, 12.755f)
            curveTo(8.699f, 12.547f, 7.528f, 13.139f, 6.96f, 14.216f)
            curveTo(6.393f, 15.294f, 6.695f, 16.555f, 7.627f, 17.291f)
            curveTo(8.424f, 17.915f, 9.302f, 18.0f, 10.195f, 17.584f)
            curveTo(11.088f, 17.168f, 11.648f, 16.391f, 11.754f, 15.378f)
            curveTo(11.771f, 13.927f, 11.771f, 13.284f, 11.771f, 13.284f)
            verticalLineTo(6.312f)
            horizontalLineTo(13.427f)
            curveTo(13.427f, 6.312f, 13.427f, 7.451f, 13.427f, 7.451f)
            verticalLineTo(8.319f)
            curveTo(14.107f, 7.239f, 14.827f, 7.194f, 15.572f, 7.194f)
            curveTo(18.259f, 7.194f, 19.883f, 8.918f, 19.883f, 11.494f)
            verticalLineTo(10.467f)
            close()
        }
    }.build()
}

val FacebookIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Facebook",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(22.0f, 12.0f)
            curveTo(22.0f, 6.477f, 17.523f, 2.0f, 12.0f, 2.0f)
            curveTo(6.477f, 2.0f, 2.0f, 6.477f, 2.0f, 12.0f)
            curveTo(2.0f, 16.991f, 5.657f, 21.128f, 10.438f, 21.879f)
            verticalLineTo(14.891f)
            horizontalLineTo(7.898f)
            verticalLineTo(12.0f)
            horizontalLineTo(10.438f)
            verticalLineTo(9.797f)
            curveTo(10.438f, 7.291f, 11.93f, 5.906f, 14.215f, 5.906f)
            curveTo(15.309f, 5.906f, 16.453f, 6.102f, 16.453f, 6.102f)
            verticalLineTo(8.562f)
            horizontalLineTo(15.192f)
            curveTo(13.95f, 8.562f, 13.563f, 9.333f, 13.563f, 10.124f)
            verticalLineTo(12.0f)
            horizontalLineTo(16.336f)
            lineTo(15.893f, 14.891f)
            horizontalLineTo(13.563f)
            verticalLineTo(21.879f)
            curveTo(18.343f, 21.128f, 22.0f, 16.991f, 22.0f, 12.0f)
            close()
        }
    }.build()
}
