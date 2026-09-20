package com.givy.downloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GivyTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 3.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.5.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp
    )
)
