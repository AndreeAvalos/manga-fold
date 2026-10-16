package com.mangafold.reader.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

/**
 * Extrae las imágenes de un .cbz/.zip a una carpeta de caché propia de la app
 * (una sola vez por archivo) para poder acceder a cada página como un File
 * normal, en el orden correcto.
 */
object CbzExtractor {

    fun extract(context: Context, archiveUri: Uri, cacheKey: String): List<File> {
        val cacheDir = File(context.cacheDir, "manga_cache/$cacheKey")
        val marker = File(cacheDir, ".done")

        if (cacheDir.exists() && marker.exists()) {
            return cacheDir.listFiles { f -> f.extension.lowercase() in IMAGE_EXTENSIONS }
                ?.sortedWith(compareBy(NaturalOrderComparator) { it.name })
                ?: emptyList()
        }

        cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        context.contentResolver.openInputStream(archiveUri)?.use { rawStream ->
            ZipInputStream(rawStream).use { zip ->
                var entry = zip.nextEntry
                var index = 0
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                        if (ext in IMAGE_EXTENSIONS) {
                            // Nombre plano y con padding para que el orden alfabético
                            // coincida con el orden de extracción si el nombre original
                            // no trae números con ceros a la izquierda.
                            val safeName = "%05d_%s".format(index, entry.name.substringAfterLast('/'))
                            val outFile = File(cacheDir, safeName)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            index++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        marker.createNewFile()

        return cacheDir.listFiles { f -> f.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedWith(compareBy(NaturalOrderComparator) { it.name })
            ?: emptyList()
    }
}
