package com.mangafold.reader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt

private const val STRIP_COUNT = 10
private const val MAX_CURL_ANGLE_DEG = 100f

/**
 * Pager que simula una hoja de papel real pasando de página: el pliegue
 * aparece justo donde pones el dedo (no hace falta arrancar desde la
 * esquina) y la parte que se despega se dibuja como una banda de tiras
 * verticales con rotación y compresión graduales, imitando la curvatura de
 * un cilindro de papel en vez de un simple giro plano.
 *
 * `t` (0..1, 0 = plana / 1 = pasada del todo) es la ÚNICA fuente de verdad
 * del render, tanto en el arrastre en vivo como en el acabado animado: así
 * no hay un cambio brusco de variable al soltar el dedo que produzca un
 * parpadeo.
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
     * animando: se instancia muchas veces por segundo (una por tira de la
     * curvatura), así que debe ser barata. Si se omite, se reusa [pageContent].
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
    // Última fracción (0f..1f) del dedo conocida de forma síncrona; se usa
    // para decidir al soltar, sin depender de que la animación ya haya
    // procesado el último snapTo.
    var lastTouchFraction by remember { mutableFloatStateOf(0f) }

    val t = remember { Animatable(0f) }

    fun foldFractionFor(forward: Boolean, tValue: Float) =
        if (forward) 1f - tValue else tValue

    fun tFor(forward: Boolean, foldFraction: Float) =
        if (forward) 1f - foldFraction else foldFraction

    suspend fun commitTurn(forward: Boolean) {
        val target = if (forward) currentPage + 1 else currentPage - 1
        if (target !in 0 until pageCount) {
            t.animateTo(0f, tween(180))
            direction = 0
            return
        }
        t.animateTo(1f, tween(220))
        onPageChange(target)
        t.snapTo(0f)
        direction = 0
    }

    suspend fun cancelTurn() {
        t.animateTo(0f, tween(180))
        direction = 0
    }

    suspend fun animatedTurn(forward: Boolean) {
        if ((forward && currentPage >= pageCount - 1) || (!forward && currentPage <= 0)) return
        direction = if (forward) 1 else -1
        t.snapTo(0f)
        commitTurn(forward)
    }

    Box(modifier = modifier.fillMaxSize()) {
        val isForward = direction == 1
        val foldFraction = if (direction == 0) 1f else foldFractionFor(isForward, t.value)

        if (direction != 0) {
            val targetIndex = if (isForward) currentPage + 1 else currentPage - 1

            // Página de destino, a pantalla completa debajo de todo: se va
            // revelando a medida que las tiras de arriba se comprimen.
            if (targetIndex in 0 until pageCount) {
                Box(Modifier.fillMaxSize()) { turning(targetIndex) }
            }

            val foldPx = widthPx * foldFraction

            // Parte de la página actual que sigue plana (todavía no se dobló).
            val flatWidthPx = if (isForward) foldPx else widthPx - foldPx
            if (flatWidthPx > 0.5f) {
                val flatStartPx = if (isForward) 0f else foldPx
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { flatWidthPx.toDp() })
                        .offset(x = with(density) { flatStartPx.toDp() })
                        .clipToBounds(),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { widthPx.toDp() })
                            .offset { IntOffset(-flatStartPx.roundToInt(), 0) },
                    ) {
                        turning(currentPage)
                    }
                }
            }

            // Banda que se está curvando: tiras finas con rotación y
            // compresión graduales, para que se vea como papel curvándose y
            // no como un giro plano de golpe.
            val curlWidthPx = widthPx - flatWidthPx
            if (curlWidthPx > 0.5f) {
                val curlStartPx = if (isForward) foldPx else 0f
                val sliceWidthPx = curlWidthPx / STRIP_COUNT

                Box(Modifier.fillMaxSize()) {
                    for (i in 0 until STRIP_COUNT) {
                        // Tira 0 = pegada al pliegue (casi plana), tira N-1 =
                        // en la punta que se despega (más girada y comprimida).
                        val u = (i + 0.5f) / STRIP_COUNT
                        val angleDeg = u * MAX_CURL_ANGLE_DEG
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val foreshorten = cos(angleRad).toFloat().coerceAtLeast(0.08f)
                        val shade = 1f - (u * 0.55f)

                        val sliceStartPx = curlStartPx + if (isForward) {
                            i * sliceWidthPx
                        } else {
                            curlWidthPx - (i + 1) * sliceWidthPx
                        }

                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(with(density) { sliceWidthPx.toDp() })
                                .offset(x = with(density) { sliceStartPx.toDp() })
                                .graphicsLayer {
                                    cameraDistance = 16f * density.density
                                    rotationY = if (isForward) angleDeg else -angleDeg
                                    scaleX = foreshorten
                                    transformOrigin = TransformOrigin(
                                        if (isForward) 0f else 1f,
                                        0.5f,
                                    )
                                }
                                .clipToBounds()
                                .drawWithContent {
                                    drawContent()
                                    drawRect(Color.Black.copy(alpha = (1f - shade) * 0.7f))
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(with(density) { widthPx.toDp() })
                                    .offset { IntOffset(-sliceStartPx.roundToInt(), 0) },
                            ) {
                                turning(currentPage)
                            }
                        }
                    }
                }

                // Sombra suave proyectada por la hoja que se levanta sobre la
                // página de abajo, justo en el pliegue.
                val shadowWidthPx = (widthPx * 0.06f).coerceAtMost(curlWidthPx)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { shadowWidthPx.toDp() })
                        .offset(
                            x = with(density) {
                                (if (isForward) curlStartPx else curlStartPx + curlWidthPx - shadowWidthPx).toDp()
                            },
                        )
                        .drawWithContent {
                            val brush = Brush.horizontalGradient(
                                colors = if (isForward) {
                                    listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                                } else {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                },
                            )
                            drawRect(brush)
                        },
                )
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
                                    // El pliegue arranca justo donde tocaste, no en un extremo
                                    // fijo: así se puede empezar a pasar la hoja desde cualquier punto.
                                    lastTouchFraction = startFraction
                                    if (direction != 0) {
                                        val forward = direction == 1
                                        scope.launch { t.snapTo(tFor(forward, startFraction)) }
                                    }
                                },
                                onDragEnd = {
                                    if (direction == 0) return@detectHorizontalDragGestures
                                    val forward = direction == 1
                                    val finishedT = tFor(forward, lastTouchFraction)
                                    scope.launch {
                                        t.snapTo(finishedT)
                                        if (finishedT > 0.5f) commitTurn(forward) else cancelTurn()
                                    }
                                },
                                onDragCancel = {
                                    if (direction == 0) return@detectHorizontalDragGestures
                                    scope.launch { cancelTurn() }
                                },
                                onHorizontalDrag = { change, _ ->
                                    if (direction == 0 || widthPx <= 0f) return@detectHorizontalDragGestures
                                    change.consume()
                                    val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                    lastTouchFraction = fraction
                                    val forward = direction == 1
                                    scope.launch { t.snapTo(tFor(forward, fraction)) }
                                },
                            )
                        }
                    }
                }
                .onSizeChanged { widthPx = it.width.toFloat() },
        )
    }
}
