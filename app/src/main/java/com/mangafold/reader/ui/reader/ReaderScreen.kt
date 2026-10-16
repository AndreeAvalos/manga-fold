package com.mangafold.reader.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.mangafold.reader.data.Manga
import com.mangafold.reader.data.PageSource
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

/** Por debajo de este ancho se trata como "una sola pantalla" (plegado o un
 *  móvil normal); por encima, como pantalla grande desplegada: se muestran
 *  dos páginas lado a lado, aprovechando ambas mitades del panel interior. */
private val SPREAD_WIDTH_BREAKPOINT = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    manga: Manga,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(manga.id) { viewModel.load(manga) }

    val uiState by viewModel.uiState.collectAsState()
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            val isSpread = maxWidth >= SPREAD_WIDTH_BREAKPOINT
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
                        isSpread = isSpread,
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
        if (isSpread) {
            // Dos páginas, cada una con su propio giro independiente con
            // bisagra en el centro (el lomo), en vez de que toda la doble
            // página se mueva como un solo bloque rígido.
            Row(Modifier.fillMaxSize()) {
                SpreadSlot(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    groupCount = groupCount,
                    groupIndex = groupIndex,
                    onGroupChange = { groupIndex = it },
                    reverseDirection = reverseDirection,
                    // Sin invertir: el lado derecho avanza. Invertido (manga):
                    // el lado izquierdo avanza. La bisagra, en cambio, siempre
                    // queda en el lomo central sin importar quién avanza:
                    // esta es la mitad izquierda, así que su lomo es su
                    // borde derecho (pivotOverride = 1f).
                    isForwardSlot = reverseDirection,
                    pivotOverride = 1f,
                    pageOf = { group -> if (reverseDirection) group.getOrNull(1) else group.getOrNull(0) },
                    groups = groups,
                )
                SpreadSlot(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    groupCount = groupCount,
                    groupIndex = groupIndex,
                    onGroupChange = { groupIndex = it },
                    reverseDirection = reverseDirection,
                    isForwardSlot = !reverseDirection,
                    // Mitad derecha: su lomo es su borde izquierdo.
                    pivotOverride = 0f,
                    pageOf = { group -> if (reverseDirection) group.getOrNull(0) else group.getOrNull(1) },
                    groups = groups,
                )
            }
        } else {
            PageCurlPager(
                pageCount = groupCount,
                currentPage = groupIndex,
                onPageChange = { groupIndex = it },
                modifier = Modifier.fillMaxSize(),
                reverseDirection = reverseDirection,
                turningPageContent = { index ->
                    PageImage(groups.getOrNull(index)?.getOrNull(0), Modifier.fillMaxSize(), zoomable = false)
                },
            ) { index ->
                PageImage(groups.getOrNull(index)?.getOrNull(0), Modifier.fillMaxSize(), zoomable = true)
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

/**
 * Una sola mitad del spread, con su propio [PageCurlPager]: solo deja pasar
 * en el sentido que le corresponde ([isForwardSlot]), para que tocar un lado
 * del libro solo gire esa página y no las dos a la vez.
 */
@Composable
private fun SpreadSlot(
    modifier: Modifier,
    groupCount: Int,
    groupIndex: Int,
    onGroupChange: (Int) -> Unit,
    reverseDirection: Boolean,
    isForwardSlot: Boolean,
    pivotOverride: Float,
    pageOf: (List<PageSource>) -> PageSource?,
    groups: List<List<PageSource>>,
) {
    PageCurlPager(
        pageCount = groupCount,
        currentPage = groupIndex,
        onPageChange = onGroupChange,
        modifier = modifier,
        reverseDirection = reverseDirection,
        allowForward = isForwardSlot,
        allowBackward = !isForwardSlot,
        pivotOverride = pivotOverride,
        turningPageContent = { index ->
            PageImage(pageOf(groups.getOrNull(index).orEmpty()), Modifier.fillMaxSize(), zoomable = false)
        },
    ) { index ->
        PageImage(pageOf(groups.getOrNull(index).orEmpty()), Modifier.fillMaxSize(), zoomable = true)
    }
}

@Composable
private fun PageImage(page: PageSource?, modifier: Modifier, zoomable: Boolean = true) {
    if (page == null) {
        Box(modifier.background(Color.Black))
        return
    }
    if (zoomable) {
        ZoomableAsyncImage(
            model = page.model,
            contentDescription = null,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        // Versión barata (sin gestos de zoom/pan) para cuando esta imagen se
        // instancia muchas veces por segundo durante la animación de pasar
        // página.
        AsyncImage(
            model = page.model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
