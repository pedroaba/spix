package com.pedroaba.spix.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.*
import com.pedroaba.spix.data.*
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CatalogViewModel(private val repository: SpixRepository) : ViewModel() {
    val query = MutableStateFlow(LibraryQuery())
    // Filtering/sorting/loading is performed by Room, never inside composition.
    private val debouncedQuery = query.debounce { if (it.search.isEmpty()) 0 else 250 }.distinctUntilChanged()
    val library = debouncedQuery.flatMapLatest(repository::library).cachedIn(viewModelScope)
    val libraryCount = debouncedQuery.flatMapLatest(repository::libraryCount).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val counts = repository.counts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogCounts(0, 0, 0, 0))
    val active = repository.activeDownload.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val downloadFilter = MutableStateFlow(DownloadFilter.Active)
    // Each filter is its own stable ordered result; no page-boundary separators with repeated keys.
    val downloads = downloadFilter.flatMapLatest { repository.downloads(it) }.cachedIn(viewModelScope)

}
@Composable
fun catalogViewModel(repository: SpixRepository, key: String = "catalog"): CatalogViewModel = viewModel(key = key, factory = viewModelFactory {
    initializer { CatalogViewModel(repository) }
})
