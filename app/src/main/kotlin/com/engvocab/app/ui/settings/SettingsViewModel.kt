package com.engvocab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.update.InstallOutcome
import com.engvocab.app.update.UpdateInfo
import com.engvocab.app.update.UpdateService
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
    data class Downloading(val info: UpdateInfo) : UpdateCheckState
    data class NeedsInstallPermission(val info: UpdateInfo) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateService: UpdateService,
) : ViewModel() {
    val desiredRetention: StateFlow<Double> = settingsRepository.desiredRetention
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.9)

    val autoEnrichEnabled: StateFlow<Boolean> = settingsRepository.autoEnrichEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val selectedLanguage: StateFlow<TargetLanguage> = settingsRepository.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetLanguage.ENGLISH)

    val cloudflareAccountId: StateFlow<String> = settingsRepository.cloudflareAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val cloudflareDatabaseId: StateFlow<String> = settingsRepository.cloudflareDatabaseId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val cloudflareApiToken: StateFlow<String> = settingsRepository.cloudflareApiToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val autoCheckForUpdates: StateFlow<Boolean> = settingsRepository.autoCheckForUpdates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    fun setAutoCheckForUpdates(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckForUpdates(value) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            settingsRepository.setLastUpdateCheckAt(System.currentTimeMillis())
            val info = updateService.checkForUpdate()
            _updateCheckState.value = if (info != null) UpdateCheckState.Available(info) else UpdateCheckState.UpToDate
        }
    }

    fun installUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Downloading(info)
            updateService.downloadAndInstall(info.apkUrl)
                .onSuccess { outcome ->
                    _updateCheckState.value = when (outcome) {
                        InstallOutcome.Launched -> UpdateCheckState.Idle
                        InstallOutcome.NeedsInstallPermission -> UpdateCheckState.NeedsInstallPermission(info)
                    }
                }
                .onFailure { e -> _updateCheckState.value = UpdateCheckState.Failed(e.message ?: "Download failed") }
        }
    }

    fun openInstallPermissionSettings() {
        updateService.openInstallPermissionSettings()
    }

    fun setDesiredRetention(value: Double) {
        viewModelScope.launch { settingsRepository.setDesiredRetention(value) }
    }

    fun setAutoEnrichEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoEnrichEnabled(value) }
    }

    fun setSelectedLanguage(language: TargetLanguage) {
        viewModelScope.launch { settingsRepository.setSelectedLanguage(language) }
    }

    fun setCloudflareAccountId(value: String) {
        viewModelScope.launch { settingsRepository.setCloudflareAccountId(value) }
    }

    fun setCloudflareDatabaseId(value: String) {
        viewModelScope.launch { settingsRepository.setCloudflareDatabaseId(value) }
    }

    fun setCloudflareApiToken(value: String) {
        viewModelScope.launch { settingsRepository.setCloudflareApiToken(value) }
    }
}
