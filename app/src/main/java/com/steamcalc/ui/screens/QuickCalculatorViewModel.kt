package com.steamcalc.ui.screens

import androidx.lifecycle.ViewModel
import com.steamcalc.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the Quick Calculator screen (Mode A — Required Spray).
 */
data class QuickCalcUiState(
    // Input fields
    val steamFlow: String = "600",
    val steamPressure: String = "167",
    val steamTemp: String = "540",
    val targetTemp: String = "520",
    val sprayTemp: String = "230",
    val initialMetalTemp: String = "540",

    // Result
    val result: SteadyStateResult? = null,
    val error: String? = null,
    val isCalculating: Boolean = false
)

class QuickCalculatorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QuickCalcUiState())
    val uiState: StateFlow<QuickCalcUiState> = _uiState.asStateFlow()

    fun updateSteamFlow(value: String) {
        _uiState.update { it.copy(steamFlow = value, error = null, result = null) }
    }

    fun updateSteamPressure(value: String) {
        _uiState.update { it.copy(steamPressure = value, error = null, result = null) }
    }

    fun updateSteamTemp(value: String) {
        _uiState.update { it.copy(steamTemp = value, error = null, result = null) }
    }

    fun updateTargetTemp(value: String) {
        _uiState.update { it.copy(targetTemp = value, error = null, result = null) }
    }

    fun updateSprayTemp(value: String) {
        _uiState.update { it.copy(sprayTemp = value, error = null, result = null) }
    }

    fun updateInitialMetalTemp(value: String) {
        _uiState.update { it.copy(initialMetalTemp = value, error = null, result = null) }
    }

    fun calculate() {
        _uiState.update { it.copy(isCalculating = true, error = null, result = null) }

        try {
            val input = SteadyStateInput(
                steamFlowTH = _uiState.value.steamFlow.toDouble(),
                steamPressureBar = _uiState.value.steamPressure.toDouble(),
                steamTempC = _uiState.value.steamTemp.toDouble(),
                targetTempC = _uiState.value.targetTemp.toDouble(),
                sprayTempC = _uiState.value.sprayTemp.toDouble(),
                initialMetalTempC = _uiState.value.initialMetalTemp.toDouble()
            )

            val validation = SteadyStateSprayCalculator.validate(input)
            when (validation) {
                is ValidationResult.Invalid -> {
                    _uiState.update {
                        it.copy(isCalculating = false, error = validation.message)
                    }
                    return
                }
                is ValidationResult.Valid -> { /* proceed */ }
            }

            val result = SteadyStateSprayCalculator.calculate(input)
            _uiState.update {
                it.copy(isCalculating = false, result = result)
            }
        } catch (e: NumberFormatException) {
            _uiState.update {
                it.copy(isCalculating = false, error = "Please enter valid numbers for all fields.")
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isCalculating = false, error = "Calculation error: ${e.message}")
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null, error = null) }
    }
}
