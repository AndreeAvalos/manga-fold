package com.mangafold.reader.ui.reader

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Pager que simula una hoja de papel pasando de página, al estilo de un libro
 * físico: la página activa gira en el eje Y desde el borde que se está
 * arrastrando, con una sombra dinámica en el pliegue y la hoja de debajo
 * asomando. No es un modelo físico de papel, pero da la sensación de "hoja"
 * en vez de un simple deslizamiento plano.
 *
 * Soporta modo libro (dos páginas visibles, pivote en el lomo central) y modo
 * una sola página (pivote en el borde que corresponda según la dirección).
 */
@Composable
fun PageCurlPager(
    pageCount: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onTapPrevious: () -> Unit = { if (currentPage > 0) onPageChange(currentPage - 1) },
    onTapNext: () -> Unit = { if (currentPage < pageCount - 1) onPageChange(currentPage + 1) },
    pageContent: @Composable (pageIndex: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // progress > 0  -> arrastrando hacia la derecha (vuelve a la página anterior)
    // progress < 0  -> arrastrando hacia la izquierda (avanza a la siguiente página)
    var progress by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }

    val animatable = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(progress, dragging) {
        if (!dragging) {
            animatable.snapTo(progress)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, currentPage, pageCount) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    // Los toques en el tercio izquierdo/derecho pasan de página al
                    // instante (navegación rápida); el gesto de arrastre, detectado
                    // en paralelo sobre el mismo flujo de eventos, es el que dibuja
                    // la animación de la hoja.
                    launch {
                        detectTapGestures(onTap = { offset ->
                            val fraction = offset.x / size.width.coerceAtLeast(1)
                            when {
                                fraction < 0.3f -> onTapPrevious()
                                fraction > 0.7f -> onTapNext()
                            }
                        })
                    }
                    launch {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val threshold = 0.35f
                                scope.launch {
                                    when {
                                        progress <= -threshold && currentPage < pageCount - 1 -> {
                                            animatable.animateTo(-1f, tween(220))
                                            onPageChange(currentPage + 1)
                                            progress = 0f
                                            animatable.snapTo(0f)
                                        }
                                        progress >= threshold && currentPage > 0 -> {
                                            animatable.animateTo(1f, tween(220))
                                            onPageChange(currentPage - 1)
                                            progress = 0f
                                            animatable.snapTo(0f)
                                        }
                                        else -> {
                                            animatable.animateTo(0f, tween(220))
                                            progress = 0f
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { animatable.animateTo(0f, tween(220)) }
                                progress = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (widthPx <= 0f) return@detectHorizontalDragGestures
                                val delta = dragAmount / widthPx
                                var next = progress + delta
                                if (next < 0f && currentPage >= pageCount - 1) next = 0f
                                if (next > 0f && currentPage <= 0) next = 0f
                                progress = next.coerceIn(-1f, 1f)
                            },
                        )
                    }
                }
            }
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        val animatedProgress = if (dragging) progress else animatable.value

        // Página de abajo: la que se va a revelar (siguiente si se avanza,
        // anterior si se retrocede). Se dibuja estática debajo.
        val revealIndex = when {
            animatedProgress < 0f -> currentPage + 1
            animatedProgress > 0f -> currentPage - 1
            else -> null
        }
        if (revealIndex != null && revealIndex in 0 until pageCount) {
            Box(Modifier.fillMaxSize()) { pageContent(revealIndex) }
        }

        // Página activa, con el efecto de hoja curvándose sobre el eje Y.
        val rotation = animatedProgress * -150f
        val pivotFraction = if (animatedProgress <= 0f) 0f else 1f
        val shadowAlpha = animatedProgress.absoluteValue * 0.6f

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    cameraDistance = 24f * density.density
                    rotationY = rotation
                    transformOrigin = TransformOrigin(pivotFraction, 0.5f)
                }
                .drawWithContent {
                    drawContent()
                    if (shadowAlpha > 0f) {
                        val fromLeft = animatedProgress < 0f
                        val brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = shadowAlpha),
                                Color.Transparent,
                            ),
                            startX = if (fromLeft) size.width else 0f,
                            endX = if (fromLeft) size.width * 0.55f else size.width * 0.45f,
                        )
                        drawRect(brush)
                    }
                },
        ) {
            pageContent(currentPage)
        }
    }
}

private fun tween(durationMillis: Int) =
    androidx.compose.animation.core.tween<Float>(durationMillis = durationMillis)
