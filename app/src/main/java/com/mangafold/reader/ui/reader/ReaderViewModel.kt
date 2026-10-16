package com.mangafold.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangafold.reader.data.Manga
import com.mangafold.reader.data.MangaRepository
import com.mangafold.reader.data.PageSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReaderUiState {
    data object Loading : ReaderUiState()
    data class Ready(val pages: List<PageSource>) : ReaderUiState()
    data object Empty : ReaderUiState()
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MangaRepository(application)

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    fun load(manga: Manga) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            val pages = repository.loadPages(manga)
            _uiState.value = if (pages.isEmpty()) ReaderUiState.Empty else ReaderUiState.Ready(pages)
            _currentPage.value = 0
        }
    }

    fun goTo(page: Int) {
        _currentPage.value = page
    }
}
