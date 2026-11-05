/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.ContentRegionGroupsKey
import com.ruben.spotitube.constants.RegionGroup
import com.ruben.spotitube.constants.parseRegionGroups
import com.ruben.spotitube.constants.serializeRegionGroups
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.pages.ChartsPage
import com.ruben.spotitube.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

enum class ChartsError {
    NO_DATA,
    NETWORK_ERROR
}

@HiltViewModel
class ChartsViewModel @Inject constructor(
    @ApplicationContext val context: Context,
) : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage: StateFlow<ChartsPage?> = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<ChartsError?>(null)
    val error: StateFlow<ChartsError?> = _error.asStateFlow()

    private val _groups = MutableStateFlow<List<RegionGroup>>(emptyList())
    val groups: StateFlow<List<RegionGroup>> = _groups.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { parseRegionGroups(it[ContentRegionGroupsKey], context) }
                .distinctUntilChanged()
                .collect { newGroups ->
                    _groups.value = newGroups
                    loadCharts()
                }
        }
    }

    fun setGroups(list: List<RegionGroup>) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ContentRegionGroupsKey] = serializeRegionGroups(list)
            }
        }
    }

    fun loadCharts() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            val currentGroups = _groups.value
            if (currentGroups.isEmpty()) {
                _error.value = ChartsError.NO_DATA
                _isLoading.value = false
                return@launch
            }

            val sections = mutableListOf<ChartsPage.ChartSection>()
            val semaphore = Semaphore(3)
            var anySuccessfulRequest = false

            currentGroups.forEach { group ->
                if (group.codes.isNotEmpty()) {
                    val deferreds = group.codes.map { code ->
                        async {
                            semaphore.withPermit {
                                runCatching { YouTube.getChartsPage(country = code).getOrNull() }.getOrNull()
                            }
                        }
                    }
                    val results = deferreds.awaitAll().filterNotNull()
                    if (results.isNotEmpty()) {
                        anySuccessfulRequest = true
                    }
                    val groupItems = results.flatMap { page -> page.sections.flatMap { it.items } }
                        .distinctBy { it.id }

                    if (groupItems.isNotEmpty()) {
                        sections.add(
                            ChartsPage.ChartSection(
                                title = group.name,
                                items = groupItems,
                                chartType = ChartsPage.ChartType.TOP
                            )
                        )
                        _chartsPage.value = ChartsPage(sections = ArrayList(sections), continuation = null)
                    }
                }
            }

            if (sections.isEmpty()) {
                _error.value = if (!anySuccessfulRequest) {
                    ChartsError.NETWORK_ERROR
                } else {
                    ChartsError.NO_DATA
                }
            }

            _isLoading.value = false
        }
    }
}
