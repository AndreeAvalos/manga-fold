package com.mangafold.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF1C1B2E)
private val InkSurface = Color(0xFF2A2840)
private val Paper = Color(0xFFF3ECD9)
private val Accent = Color(0xFFC0392B)

private val MangaFoldColors = darkColorScheme(
    primary = Accent,
    onPrimary = Paper,
    background = Ink,
    onBackground = Paper,
    surface = InkSurface,
    onSurface = Paper,
    secondary = Paper,
)

@Composable
fun MangaFoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MangaFoldColors,
        content = content,
    )
}
