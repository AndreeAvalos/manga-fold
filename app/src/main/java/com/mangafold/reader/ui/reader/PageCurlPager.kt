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
import androidx.compose.runtime.mutableStateOf
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

private const val STRIP_COUNT = 12

/**
 * Pager que simula una hoja de papel curvándose al pasar de página: la
 * franja que se va "despegando" se dibuja como varias tiras verticales que
 * se van comprimiendo de forma gradual (no de golpe), dando sensación de
 * curva en vez de una tapa dura girando. Se puede agarrar tocando cualquier
 * punto del lado izquierdo o derecho de la pantalla.
 *
 * A diferencia de un intento anterior con rotación 3D real (rotationY +
 * cámara en perspectiva), aquí solo se comprime el ANCHO (scaleX): la
 * perspectiva real encogía también el ALTO cerca de los 90°, dejando la
 * página chica y flotando en vez de ocupar toda la altura. Con solo scaleX
 * eso no puede pasar nunca.
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
    /** Si es false, este pager ignora los gestos que avanzarían de página
     *  (se usa en el modo de dos páginas: cada mitad solo gira en un sentido). */
    allowForward: Boolean = true,
    /** Igual que [allowForward] pero para retroceder. */
    allowBackward: Boolean = true,
    /**
     * Fija la bisagra en un borde concreto (0f = izquierda, 1f = derecha) en
     * vez de deducirla del sentido del giro. Se usa en el modo de dos
     * páginas: cada mitad debe girar siempre sobre el lomo central, sin
     * importar si ese lado avanza o retrocede.
     */
    pivotOverride: Float? = null,
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
    // Evita que dos gestos disparen dos cambios de página casi a la vez.
    var committing by remember { mutableStateOf(false) }

    // t: 0 = plana, -1 = pasada del todo hacia la siguiente, +1 = pasada del
    // todo hacia la anterior. Única fuente de verdad del render.
    val t = remember { Animatable(0f) }

    suspend fun settle() {
        if (committing) return
        committing = true
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
        committing = false
    }

    suspend fun animatedTurn(forward: Boolean) {
        if (committing) return
        if ((forward && currentPage >= pageCount - 1) || (!forward && currentPage <= 0)) return
        committing = true
        direction = if (forward) 1 else -1
        t.snapTo(0f)
        t.animateTo(if (forward) -1f else 1f, tween(280))
        onPageChange(if (forward) currentPage + 1 else currentPage - 1)
        t.snapTo(0f)
        direction = 0
        committing = false
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        val value = t.value
        val foldProgress = kotlin.math.abs(value).coerceIn(0f, 1f)

        if ((direction != 0 || value != 0f) && foldProgress > 0f) {
            val forward = if (value != 0f) value < 0f else direction == 1
            val targetIndex = if (forward) currentPage + 1 else currentPage - 1

            // Página de destino, a pantalla completa debajo de todo: se
            // revela conforme la banda de arriba se va comprimiendo.
            if (targetIndex in 0 until pageCount) {
                Box(Modifier.fillMaxSize()) { turning(targetIndex) }
            }

            // El pliegue avanza desde el borde fijado (o el contrario al
            // sentido del giro) hacia el otro extremo, según cuánto se haya
            // arrastrado. flatWidthPx = lo que de la página actual sigue
            // totalmente plano; curlWidthPx = la banda que se está curvando.
            val pivotAtStart = pivotOverride ?: if (forward) 0f else 1f
            val curlWidthPx = widthPx * foldProgress
            val flatWidthPx = widthPx - curlWidthPx

            if (flatWidthPx > 0.5f) {
                val flatStartPx = if (pivotAtStart == 0f) 0f else curlWidthPx
                PageWindow(
                    startPx = flatStartPx,
                    widthPxSlice = flatWidthPx,
                    fullWidthPx = widthPx,
                    density = density,
                ) {
                    turning(currentPage)
                }
            }

            if (curlWidthPx > 0.5f) {
                val curlStartPx = if (pivotAtStart == 0f) flatWidthPx else 0f
                val sliceWidthPx = curlWidthPx / STRIP_COUNT

                Box(Modifier.fillMaxSize()) {
                    for (i in 0 until STRIP_COUNT) {
                        // Tira 0 = pegada a la parte plana (casi sin
                        // comprimir), tira N-1 = en la punta que se curva
                        // (más comprimida), para que se vea como una curva
                        // continua y no como un giro de golpe.
                        // i=0 siempre queda pegada a la parte plana y i=N-1
                        // siempre es la punta que se curva, sin importar de
                        // qué lado esté el lomo (ya lo resuelve sliceStartPx
                        // más abajo), así que esta relación no depende de
                        // pivotAtStart.
                        val u = (i + 0.5f) / STRIP_COUNT
                        val scale = cos(Math.toRadians(u * 90.0)).toFloat().coerceAtLeast(0.04f)
                        val shadeAlpha = u * 0.5f

                        val sliceStartPx = curlStartPx + if (pivotAtStart == 0f) {
                            i * sliceWidthPx
                        } else {
                            curlWidthPx - (i + 1) * sliceWidthPx
                        }
                        // Mantiene la tira pegada a su vecina ya comprimida,
                        // anclando la compresión al lado más cercano al
                        // pliegue en vez de al centro de la tira.
                        val anchorAtRight = pivotAtStart != 0f

                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(with(density) { sliceWidthPx.toDp() })
                                .offset(x = with(density) { sliceStartPx.toDp() })
                                .graphicsLayer {
                                    scaleX = scale
                                    transformOrigin = TransformOrigin(if (anchorAtRight) 1f else 0f, 0.5f)
                                }
                                .drawWithContent {
                                    drawContent()
                                    if (shadeAlpha > 0f) {
                                        drawRect(Color.Black.copy(alpha = shadeAlpha))
                                    }
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

                // Sombra suave de la hoja que se curva sobre la página de
                // debajo, justo en el borde que se está despegando.
                val shadowWidthPx = (widthPx * 0.05f).coerceAtMost(curlWidthPx)
                val shadowStartPx = if (pivotAtStart == 0f) {
                    curlStartPx + curlWidthPx - shadowWidthPx
                } else {
                    curlStartPx
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { shadowWidthPx.toDp() })
                        .offset(x = with(density) { shadowStartPx.toDp() })
                        .drawWithContent {
                            val brush = Brush.horizontalGradient(
                                colors = if (pivotAtStart == 0f) {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                                } else {
                                    listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
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
                                        fraction < 0.3f -> {
                                            val forward = reverseDirection
                                            if ((forward && allowForward) || (!forward && allowBackward)) {
                                                animatedTurn(forward)
                                            }
                                        }
                                        fraction > 0.7f -> {
                                            val forward = !reverseDirection
                                            if ((forward && allowForward) || (!forward && allowBackward)) {
                                                animatedTurn(forward)
                                            }
                                        }
                                    }
                                }
                            })
                        }
                        launch {
                            detectHorizontalDragGestures(
                                onDragStart = { offset: Offset ->
                                    if (committing) return@detectHorizontalDragGestures
                                    direction = when {
                                        allowForward && !allowBackward -> if (currentPage < pageCount - 1) 1 else 0
                                        allowBackward && !allowForward -> if (currentPage > 0) -1 else 0
                                        else -> {
                                            val startFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                            val wantsForward = if (reverseDirection) startFraction < 0.5f else startFraction >= 0.5f
                                            when {
                                                wantsForward && currentPage < pageCount - 1 -> 1
                                                !wantsForward && currentPage > 0 -> -1
                                                else -> 0
                                            }
                                        }
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

/** Recorta un trozo horizontal (ventana) del contenido completo de una
 *  página, mostrando solo [startPx, startPx+widthPxSlice). */
@Composable
private fun PageWindow(
    startPx: Float,
    widthPxSlice: Float,
    fullWidthPx: Float,
    density: androidx.compose.ui.unit.Density,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(with(density) { widthPxSlice.toDp() })
            .offset(x = with(density) { startPx.toDp() })
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(density) { fullWidthPx.toDp() })
                .offset { IntOffset(-startPx.roundToInt(), 0) },
        ) {
            content()
        }
    }
}
