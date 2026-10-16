package com.mangafold.reader.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mangafold.reader.data.Manga
import com.mangafold.reader.data.PageSource
import com.mangafold.reader.fold.FoldPosture
import com.mangafold.reader.fold.rememberFoldPosture
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    manga: Manga,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(manga.id) { viewModel.load(manga) }

    val uiState by viewModel.uiState.collectAsState()
    val foldPosture by rememberFoldPosture()
    val rtlReading by viewModel.rtlReading.collectAsState()

    ImmersiveMode()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(manga.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleRtlReading() }) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = if (rtlReading) {
                                "Lectura derecha a izquierda (estilo manga). Toca para cambiar"
                            } else {
                                "Lectura izquierda a derecha. Toca para cambiar"
                            },
                        )
                    }
                    Text(
                        text = if (rtlReading) "D→I" else "I→D",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReaderUiState.Empty -> {
                    Text(
                        text = "No se encontraron páginas legibles en este manga.",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }
                is ReaderUiState.Ready -> {
                    MangaPager(
                        pages = state.pages,
                        isSpread = foldPosture is FoldPosture.Book,
                        reverseDirection = rtlReading,
                    )
                }
            }
        }
    }
}

/**
 * Oculta la barra de estado y de navegación mientras esta pantalla está
 * visible (pantalla completa real para leer), y las vuelve a mostrar al
 * salir del lector.
 */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun MangaPager(pages: List<PageSource>, isSpread: Boolean, reverseDirection: Boolean) {
    val groups: List<List<PageSource>> = remember(pages, isSpread) {
        if (isSpread) pages.chunked(2) else pages.map { listOf(it) }
    }
    var groupIndex by remember(groups) { mutableIntStateOf(0) }
    val groupCount = groups.size

    Box(Modifier.fillMaxSize()) {
        PageCurlPager(
            pageCount = groupCount,
            currentPage = groupIndex,
            onPageChange = { groupIndex = it },
            modifier = Modifier.fillMaxSize(),
            reverseDirection = reverseDirection,
        ) { index ->
            val group = groups.getOrNull(index).orEmpty()
            if (isSpread) {
                // En lectura derecha→izquierda el spread también se invierte:
                // la página más adelantada va a la izquierda.
                val left = if (reverseDirection) group.getOrNull(1) else group.getOrNull(0)
                val right = if (reverseDirection) group.getOrNull(0) else group.getOrNull(1)
                Row(Modifier.fillMaxSize()) {
                    PageImage(left, Modifier.weight(1f).fillMaxHeight())
                    PageImage(right, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                PageImage(group.getOrNull(0), Modifier.fillMaxSize())
            }
        }

        Text(
            text = "${groupIndex + 1} / $groupCount",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
    }
}

@Composable
private fun PageImage(page: PageSource?, modifier: Modifier) {
    if (page == null) {
        Box(modifier.background(Color.Black))
        return
    }
    ZoomableAsyncImage(
        model = page.model,
        contentDescription = null,
        modifier = modifier.fillMaxWidth(),
    )
}
