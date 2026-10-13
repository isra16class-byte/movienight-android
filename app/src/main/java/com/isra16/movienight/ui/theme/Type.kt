package com.isra16.movienight.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.isra16.movienight.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

/**
 * Fuente del título de las pantallas de entrada (Fase 8, paso 3; PROVISIONAL hasta el paso 6): Playfair Display, licencia OFL
 * (ver `third_party/fonts/playfair-display/`). Es un archivo variable y solo se usa su peso por defecto (400).
 */
internal val TitleFontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))

/** Estilo del título de login, registro y recuperar contraseña. */
internal val AuthTitleStyle = TextStyle(
    fontFamily = TitleFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp,
)
