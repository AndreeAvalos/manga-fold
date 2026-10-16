package com.mangafold.reader.data

import android.net.Uri

enum class MangaType { FOLDER, ARCHIVE }

data class Manga(
    val id: String,
    val title: String,
    val uri: Uri,
    val type: MangaType,
)

/**
 * Una página ya resuelta a algo que Coil puede cargar directamente:
 * un Uri de SAF (carpeta de imágenes) o un File extraído de un .cbz/.zip.
 */
sealed class PageSource {
    data class FromUri(val uri: Uri) : PageSource()
    data class FromFile(val file: java.io.File) : PageSource()

    val model: Any
        get() = when (this) {
            is FromUri -> uri
            is FromFile -> file
        }
}
