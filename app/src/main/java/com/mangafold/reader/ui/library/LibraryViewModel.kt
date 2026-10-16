package com.mangafold.reader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.mangafold.reader.data.Manga
import com.mangafold.reader.data.MangaRepository
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MangaRepository(application)
    val library: StateFlow<List<Manga>> = repository.library

    fun addFolder(uri: Uri) = repository.addFolder(uri)

    fun addArchive(uri: Uri) = repository.addArchive(uri)

    fun removeManga(manga: Manga) = repository.removeManga(manga)
}
