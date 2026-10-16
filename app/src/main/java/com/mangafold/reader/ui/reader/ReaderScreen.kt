package com.mangafold.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(manga.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
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
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaPager(pages: List<PageSource>, isSpread: Boolean) {
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
        ) { index ->
            val group = groups.getOrNull(index).orEmpty()
            if (isSpread) {
                Row(Modifier.fillMaxSize()) {
                    PageImage(group.getOrNull(0), Modifier.weight(1f).fillMaxHeight())
                    PageImage(group.getOrNull(1), Modifier.weight(1f).fillMaxHeight())
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
