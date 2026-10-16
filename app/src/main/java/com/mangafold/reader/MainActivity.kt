package com.mangafold.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangafold.reader.data.Manga
import com.mangafold.reader.ui.library.LibraryScreen
import com.mangafold.reader.ui.library.LibraryViewModel
import com.mangafold.reader.ui.reader.ReaderScreen
import com.mangafold.reader.ui.reader.ReaderViewModel
import com.mangafold.reader.ui.theme.MangaFoldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MangaFoldTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaFoldNavHost()
                }
            }
        }
    }
}

private sealed class Screen {
    data object Library : Screen()
    data class Reader(val manga: Manga) : Screen()
}

@androidx.compose.runtime.Composable
private fun MangaFoldNavHost() {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    when (val current = screen) {
        is Screen.Library -> {
            val viewModel: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = viewModel,
                onOpenManga = { manga -> screen = Screen.Reader(manga) },
            )
        }
        is Screen.Reader -> {
            val viewModel: ReaderViewModel = viewModel()
            ReaderScreen(
                manga = current.manga,
                viewModel = viewModel,
                onBack = { screen = Screen.Library },
            )
        }
    }
}
