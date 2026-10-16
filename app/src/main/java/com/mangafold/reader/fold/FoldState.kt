package com.mangafold.reader.fold

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.map

/**
 * Postura actual del dispositivo plegable (pensado para el OnePlus Open,
 * pero funciona con cualquier plegable que exponga [FoldingFeature]).
 */
sealed class FoldPosture {
    /** No es plegable, o está completamente plano / cerrado como un móvil normal. */
    data object Flat : FoldPosture()

    /** Medio abierto en forma de libro: la bisagra queda vertical, ideal para spread de 2 páginas. */
    data class Book(val hingeBounds: Rect) : FoldPosture()

    /** Medio abierto en forma de mesa (tent/tabletop): la bisagra queda horizontal. */
    data class Tabletop(val hingeBounds: Rect) : FoldPosture()
}

@Composable
fun rememberFoldPosture(): State<FoldPosture> {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val defaultState = remember { mutableStateOf<FoldPosture>(FoldPosture.Flat) }
    if (activity == null) return defaultState

    val layoutInfoFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { info -> toPosture(info) }
    }

    return layoutInfoFlow.collectAsStateWithLifecycle(initialValue = FoldPosture.Flat)
}

private fun toPosture(info: WindowLayoutInfo): FoldPosture {
    val folding = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        ?: return FoldPosture.Flat

    val bounds = Rect(
        folding.bounds.left.toFloat(),
        folding.bounds.top.toFloat(),
        folding.bounds.right.toFloat(),
        folding.bounds.bottom.toFloat(),
    )

    if (folding.state == FoldingFeature.State.FLAT) return FoldPosture.Flat

    return when (folding.orientation) {
        FoldingFeature.Orientation.VERTICAL -> FoldPosture.Book(bounds)
        FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.Tabletop(bounds)
        else -> FoldPosture.Flat
    }
}
