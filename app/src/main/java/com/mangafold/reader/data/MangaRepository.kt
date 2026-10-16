package com.mangafold.reader.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private const val PREFS_NAME = "mangafold_prefs"
private const val KEY_LIBRARY = "library_json"
private const val KEY_RTL_READING = "rtl_reading"

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private val ARCHIVE_EXTENSIONS = setOf("cbz", "zip")

class MangaRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _library = MutableStateFlow(loadFromPrefs())
    val library = _library.asStateFlow()

    private val _rtlReading = MutableStateFlow(prefs.getBoolean(KEY_RTL_READING, false))
    val rtlReading = _rtlReading.asStateFlow()

    fun setRtlReading(value: Boolean) {
        prefs.edit().putBoolean(KEY_RTL_READING, value).apply()
        _rtlReading.value = value
    }

    private fun loadFromPrefs(): List<Manga> {
        val raw = prefs.getString(KEY_LIBRARY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val uri = Uri.parse(obj.getString("uri"))
                // Si el permiso persistente se perdió (p. ej. se borró el archivo), lo saltamos.
                Manga(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    uri = uri,
                    type = MangaType.valueOf(obj.getString("type")),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<Manga>) {
        val arr = JSONArray()
        list.forEach { manga ->
            val obj = JSONObject()
            obj.put("id", manga.id)
            obj.put("title", manga.title)
            obj.put("uri", manga.uri.toString())
            obj.put("type", manga.type.name)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_LIBRARY, arr.toString()).apply()
    }

    fun addFolder(uri: Uri) {
        persistPermission(uri)
        val doc = DocumentFile.fromTreeUri(context, uri)
        val title = doc?.name ?: "Manga"
        addManga(Manga(id = idFor(uri), title = title, uri = uri, type = MangaType.FOLDER))
    }

    fun addArchive(uri: Uri) {
        persistPermission(uri)
        val title = queryDisplayName(context.contentResolver, uri) ?: "Manga"
        addManga(Manga(id = idFor(uri), title = title.substringBeforeLast('.'), uri = uri, type = MangaType.ARCHIVE))
    }

    /**
     * Algunos proveedores de documentos (ciertos gestores de archivos, nubes,
     * etc.) no permiten un permiso persistente aunque vengan de
     * ACTION_OPEN_DOCUMENT; sin este try/catch esa SecurityException tiraba
     * la app entera al elegir un archivo así.
     */
    private fun persistPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Seguimos igual: el permiso temporal de esta sesión alcanza para
            // leerlo ahora mismo, aunque no sobreviva a un reinicio de la app.
        }
    }

    private fun addManga(manga: Manga) {
        _library.update { current ->
            if (current.any { it.id == manga.id }) current
            else (current + manga).also { persist(it) }
        }
    }

    fun removeManga(manga: Manga) {
        _library.update { current ->
            current.filterNot { it.id == manga.id }.also { persist(it) }
        }
    }

    suspend fun loadPages(manga: Manga): List<PageSource> = withContext(Dispatchers.IO) {
        try {
            when (manga.type) {
                MangaType.FOLDER -> loadFolderPages(manga.uri)
                MangaType.ARCHIVE -> CbzExtractor.extract(context, manga.uri, manga.id).map { PageSource.FromFile(it) }
            }
        } catch (e: Exception) {
            // Permiso revocado, archivo movido/borrado, zip corrupto, etc.:
            // mejor una biblioteca vacía que una app que se cierra.
            emptyList()
        }
    }

    private fun loadFolderPages(treeUri: Uri): List<PageSource> {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return doc.listFiles()
            .filter { it.isFile && IMAGE_EXTENSIONS.contains(it.name?.substringAfterLast('.', "")?.lowercase() ?: "") }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name ?: "" })
            .map { PageSource.FromUri(it.uri) }
    }

    private fun idFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    companion object {
        fun isArchiveExtension(name: String) = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS
    }
}
