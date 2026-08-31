package com.engvocab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.update.InstallOutcome
import com.engvocab.app.update.UpdateService
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val reviewsToday: Int = 0,
    val streakDays: Int = 0,
    val isLoading: Boolean = true,
)

/** A follow-up the auto-update check wants to surface on Home, since it runs silently otherwise. */
enum class UpdateBanner { NEEDS_INSTALL_PERMISSION, CHECK_FAILED }

class HomeViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val updateService: UpdateService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _updateBanner = MutableStateFlow<UpdateBanner?>(null)
    val updateBanner: StateFlow<UpdateBanner?> = _updateBanner.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.selectedLanguage.collect { language -> refresh(language) }
        }
        maybeAutoUpdate()
    }

    fun dismissUpdateBanner() {
        _updateBanner.value = null
    }

    fun openInstallPermissionSettings() {
        updateService.openInstallPermissionSettings()
    }

    /** Silently checks for, downloads, and prompts to install a newer build - at most once every ~20h. */
    private fun maybeAutoUpdate() {
        viewModelScope.launch {
            if (!settingsRepository.autoCheckForUpdates.first()) return@launch
            val lastCheck = settingsRepository.lastUpdateCheckAt.first() ?: 0L
            if (System.currentTimeMillis() - lastCheck < AUTO_UPDATE_CHECK_INTERVAL_MS) return@launch

            settingsRepository.setLastUpdateCheckAt(System.currentTimeMillis())
            val info = updateService.checkForUpdate() ?: return@launch
            updateService.downloadAndInstall(info.apkUrl)
                .onSuccess { outcome ->
                    if (outcome == InstallOutcome.NeedsInstallPermission) {
                        _updateBanner.value = UpdateBanner.NEEDS_INSTALL_PERMISSION
                    }
                }
                .onFailure { _updateBanner.value = UpdateBanner.CHECK_FAILED }
        }
    }

    fun refresh() {
        viewModelScope.launch { refresh(settingsRepository.selectedLanguage.first()) }
    }

    fun setLanguage(language: TargetLanguage) {
        viewModelScope.launch { settingsRepository.setSelectedLanguage(language) }
    }

    private suspend fun refresh(language: TargetLanguage) {
        val due = cardRepository.countDue(language)
        val total = cardRepository.observeTotalCount(language).first()
        val today = cardRepository.reviewsToday()
        val streak = cardRepository.currentStreakDays()
        _uiState.value = HomeUiState(
            language = language,
            dueCount = due,
            totalCount = total,
            reviewsToday = today,
            streakDays = streak,
            isLoading = false,
        )
    }

    private companion object {
        const val AUTO_UPDATE_CHECK_INTERVAL_MS = 20 * 60 * 60 * 1000L
    }
}
