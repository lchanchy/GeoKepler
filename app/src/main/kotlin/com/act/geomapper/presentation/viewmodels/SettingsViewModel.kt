package com.act.geomapper.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.settings.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setDistanceUnit(unit: DistanceUnit)  = viewModelScope.launch { repo.setDistanceUnit(unit) }
    fun setAreaUnit(unit: AreaUnit)          = viewModelScope.launch { repo.setAreaUnit(unit) }
    fun setLanguage(lang: AppLanguage)       = viewModelScope.launch { repo.setLanguage(lang) }
    fun setDarkMode(dark: Boolean)           = viewModelScope.launch { repo.setDarkMode(dark) }
    fun setCoordFormat(fmt: CoordFormat)         = viewModelScope.launch { repo.setCoordFormat(fmt) }
    fun setRellenoPoligonos(valor: Boolean)      = viewModelScope.launch { repo.setRellenoPoligonos(valor) }
}
