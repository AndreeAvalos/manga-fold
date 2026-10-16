package com.mangafold.reader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
 * Pager con un giro de página al estilo revista: la hoja gira en 3D sobre su
 * propio eje (como una tapa dura, no como papel flexible), en dos tramos
 * — 0°→90° se ve la página actual achicándose por la perspectiva, 90°→180°
 * aparece la página siguiente terminando el giro — y se puede agarrar
 * tocando cualquier punto del lado izquierdo o derecho de la pantalla, no
 * solo una esquina.
 */
@Composable
fun PageCurlPager(
    pageCount: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** true = lectura derecha→izquierda (manga clásico): el lado derecho retrocede. */
    reverseDirection: Boolean = false,
    /**
     * Versión ligera (sin zoom/pan) usada mientras la página se está
     * animando. Si se omite, se reusa [pageContent].
     */
    turningPageContent: (@Composable (pageIndex: Int) -> Unit)? = null,
    pageContent: @Composable (pageIndex: Int) -> Unit,
) {
    val turning = turningPageContent ?: pageContent
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var widthPx by remember { mutableFloatStateOf(1f) }
    // +1 = avanzando a la siguiente página, -1 = volviendo a la anterior, 0 = en reposo.
    var direction by remember { mutableIntStateOf(0) }

    // t: 0 = plana, -1 = pasada del todo hacia la siguiente, +1 = pasada del
    // todo hacia la anterior. Única fuente de verdad del render.
    val t = remember { Animatable(0f) }

    suspend fun settle() {
        val value = t.value
        when {
            value <= -0.5f && currentPage < pageCount - 1 -> {
                t.animateTo(-1f, tween(260))
                onPageChange(currentPage + 1)
                t.snapTo(0f)
            }
            value >= 0.5f && currentPage > 0 -> {
                t.animateTo(1f, tween(260))
                onPageChange(currentPage - 1)
                t.snapTo(0f)
            }
            else -> t.animateTo(0f, tween(200))
        }
        direction = 0
    }

    suspend fun animatedTurn(forward: Boolean) {
        if ((forward && currentPage >= pageCount - 1) || (!forward && currentPage <= 0)) return
        direction = if (forward) 1 else -1
        t.snapTo(0f)
        t.animateTo(if (forward) -1f else 1f, tween(280))
        onPageChange(if (forward) currentPage + 1 else currentPage - 1)
        t.snapTo(0f)
        direction = 0
    }

    Box(modifier = modifier.fillMaxSize()) {
        val value = t.value
        // Ángulo de giro ya en el signo correcto para graphicsLayer: positivo
        // cuando se avanza con el pivote a la izquierda (coincide con la
        // convención ya probada en el resto de la app).
        val signedAngle = value * -180f

        if (direction != 0 || value != 0f) {
            val forward = if (value != 0f) value < 0f else direction == 1
            val targetIndex = if (forward) currentPage + 1 else currentPage - 1

            // Página de destino, siempre debajo a pantalla completa: se
            // revela conforme la página que gira se va viendo de canto.
            if (targetIndex in 0 until pageCount) {
                Box(Modifier.fillMaxSize()) { turning(targetIndex) }
            }

            // Pivote: el lado contrario al que se está pasando (la "costura").
            val pivotFraction = if (forward) 0f else 1f

            if (signedAngle.absoluteValue <= 90f) {
                // Primer tramo: la página actual gira mostrando su frente.
                FlippingPage(
                    rotationY = signedAngle,
                    pivotFraction = pivotFraction,
                    density = density,
                ) {
                    turning(currentPage)
                }
            } else if (targetIndex in 0 until pageCount) {
                // Segundo tramo: ya pasó los 90°, termina de girar la
                // página siguiente/anterior hasta quedar plana.
                val remaining = if (forward) signedAngle - 180f else signedAngle + 180f
                FlippingPage(
                    rotationY = remaining,
                    pivotFraction = pivotFraction,
                    density = density,
                ) {
                    turning(targetIndex)
                }
            }
        } else {
            // En reposo: una sola instancia normal (permite pellizcar/zoom).
            pageContent(currentPage)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, currentPage, pageCount, reverseDirection) {
                    if (!enabled) return@pointerInput
                    coroutineScope {
                        launch {
                            detectTapGestures(onTap = { offset ->
                                val fraction = offset.x / size.width.coerceAtLeast(1)
                                scope.launch {
                                    when {
                                        fraction < 0.3f -> animatedTurn(forward = reverseDirection)
                                        fraction > 0.7f -> animatedTurn(forward = !reverseDirection)
                                    }
                                }
                            })
                        }
                        launch {
                            detectHorizontalDragGestures(
                                onDragStart = { offset: Offset ->
                                    val startFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                    val wantsForward = if (reverseDirection) startFraction < 0.5f else startFraction >= 0.5f
                                    direction = when {
                                        wantsForward && currentPage < pageCount - 1 -> 1
                                        !wantsForward && currentPage > 0 -> -1
                                        else -> 0
                                    }
                                },
                                onDragEnd = {
                                    if (direction == 0) return@detectHorizontalDragGestures
                                    scope.launch { settle() }
                                },
                                onDragCancel = {
                                    if (direction == 0) return@detectHorizontalDragGestures
                                    scope.launch { settle() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (direction == 0 || widthPx <= 0f) return@detectHorizontalDragGestures
                                    change.consume()
                                    val delta = dragAmount / widthPx
                                    val minT = if (direction == 1) -1f else 0f
                                    val maxT = if (direction == 1) 0f else 1f
                                    val next = (t.value + delta).coerceIn(minT, maxT)
                                    scope.launch { t.snapTo(next) }
                                },
                            )
                        }
                    }
                }
                .onSizeChanged { widthPx = it.width.toFloat() },
        )
    }
}

/** Una página plana girando en 3D sobre [pivotFraction], con sombreado según
 *  cuánto se haya girado para dar sensación de volumen. */
@Composable
private fun FlippingPage(
    rotationY: Float,
    pivotFraction: Float,
    density: androidx.compose.ui.unit.Density,
    content: @Composable () -> Unit,
) {
    val shadeAlpha = (rotationY.absoluteValue / 90f).coerceIn(0f, 1f) * 0.45f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                cameraDistance = 12f * density.density
                this.rotationY = rotationY
                transformOrigin = TransformOrigin(pivotFraction, 0.5f)
            }
            .drawWithContent {
                drawContent()
                if (shadeAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = shadeAlpha))
                }
            },
    ) {
        content()
    }
}
