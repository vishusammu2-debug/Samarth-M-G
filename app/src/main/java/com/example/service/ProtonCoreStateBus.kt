package com.example.service

import com.example.domain.OrbVisualState
import com.example.domain.ResolvedPerformanceTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProtonCoreStateBus {
    private val _orbState = MutableStateFlow(OrbVisualState.IDLE)
    val orbState: StateFlow<OrbVisualState> = _orbState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _performanceTier = MutableStateFlow(ResolvedPerformanceTier.BALANCED)
    val performanceTier: StateFlow<ResolvedPerformanceTier> = _performanceTier.asStateFlow()

    private val _memoryPressureActive = MutableStateFlow(false)
    val memoryPressureActive: StateFlow<Boolean> = _memoryPressureActive.asStateFlow()

    fun updateOrbState(state: OrbVisualState) {
        _orbState.value = state
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level.coerceIn(0f, 1f)
    }

    fun updatePerformanceTier(tier: ResolvedPerformanceTier) {
        _performanceTier.value = tier
    }

    fun notifyMemoryPressure(active: Boolean) {
        _memoryPressureActive.value = active
    }
}
