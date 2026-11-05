package com.ruben.spotitube.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

enum class ChartsError {
    NO_DATA,
    NETWORK_ERROR
}

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage: StateFlow<ChartsPage?> = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<ChartsError?>(null)
    val error: StateFlow<ChartsError?> = _error.asStateFlow()

    fun loadCharts() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            val sections = mutableListOf<ChartsPage.ChartSection>()

            // 1. España (ES) primero
            val spainResult = runCatching { YouTube.getChartsPage(country = "ES").getOrNull() }.getOrNull()
            if (spainResult != null && spainResult.sections.isNotEmpty()) {
                val spainItems = spainResult.sections.flatMap { it.items }.distinctBy { it.id }
                if (spainItems.isNotEmpty()) {
                    sections.add(
                        ChartsPage.ChartSection(
                            title = "ESPAÑA",
                            items = spainItems,
                            chartType = ChartsPage.ChartType.TOP
                        )
                    )
                    _chartsPage.value = ChartsPage(sections = ArrayList(sections), continuation = null)
                }
            }

            // 2. Europa y Latinoamérica en paralelo (concurrencia máx. 3)
            val semaphore = Semaphore(3)
            val europeCountries = listOf("GB", "DE", "FR", "IT", "NL", "PL")
            val latamCountries = listOf("MX", "AR", "CO", "CL", "PE", "BR")

            val europeDeferreds = europeCountries.map { code ->
                async {
                    semaphore.withPermit {
                        runCatching { YouTube.getChartsPage(country = code).getOrNull() }.getOrNull()
                    }
                }
            }

            val latamDeferreds = latamCountries.map { code ->
                async {
                    semaphore.withPermit {
                        runCatching { YouTube.getChartsPage(country = code).getOrNull() }.getOrNull()
                    }
                }
            }

            val europeResults = europeDeferreds.awaitAll().filterNotNull()
            val europeItems = europeResults.flatMap { page -> page.sections.flatMap { it.items } }.distinctBy { it.id }
            if (europeItems.isNotEmpty()) {
                sections.add(
                    ChartsPage.ChartSection(
                        title = "EUROPA",
                        items = europeItems,
                        chartType = ChartsPage.ChartType.TOP
                    )
                )
                _chartsPage.value = ChartsPage(sections = ArrayList(sections), continuation = null)
            }

            val latamResults = latamDeferreds.awaitAll().filterNotNull()
            val latamItems = latamResults.flatMap { page -> page.sections.flatMap { it.items } }.distinctBy { it.id }
            if (latamItems.isNotEmpty()) {
                sections.add(
                    ChartsPage.ChartSection(
                        title = "LATINOAMÉRICA",
                        items = latamItems,
                        chartType = ChartsPage.ChartType.TOP
                    )
                )
                _chartsPage.value = ChartsPage(sections = ArrayList(sections), continuation = null)
            }

            if (sections.isEmpty()) {
                _error.value = if (spainResult == null && europeResults.isEmpty() && latamResults.isEmpty()) {
                    ChartsError.NETWORK_ERROR
                } else {
                    ChartsError.NO_DATA
                }
            }

            _isLoading.value = false
        }
    }
}
