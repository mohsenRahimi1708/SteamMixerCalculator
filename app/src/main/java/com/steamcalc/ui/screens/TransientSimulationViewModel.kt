package com.steamcalc.ui.screens

import androidx.lifecycle.ViewModel
import com.steamcalc.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the Transient Simulation screen.
 */
data class TransientUiState(
    // Steam side
    val steamFlow: String = "600",
    val steamPressure: String = "167",
    val steamTemp: String = "540",
    val targetTemp: String = "520",
    val sprayTemp: String = "230",

    // Initial metal temperature (mandatory)
    val initialMetalTemp: String = "540",

    // Platen metal
    val metalMass: String = "62600",
    val metalCp: String = "500",

    // Burner
    val burnerHeat: String = "40",
    val burnerCount: String = "4",
    val eta: String = "0.07",

    // Simulation
    val timeStep: String = "10",
    val duration: String = "600",

    // Result
    val result: TransientResult? = null,
    val error: String? = null,
    val isSimulating: Boolean = false
)

class TransientSimulationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TransientUiState())
    val uiState: StateFlow<TransientUiState> = _uiState.asStateFlow()

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

    fun updateMetalMass(value: String) {
        _uiState.update { it.copy(metalMass = value, error = null, result = null) }
    }

    fun updateMetalCp(value: String) {
        _uiState.update { it.copy(metalCp = value, error = null, result = null) }
    }

    fun updateBurnerHeat(value: String) {
        _uiState.update { it.copy(burnerHeat = value, error = null, result = null) }
    }

    fun updateBurnerCount(value: String) {
        _uiState.update { it.copy(burnerCount = value, error = null, result = null) }
    }

    fun updateEta(value: String) {
        _uiState.update { it.copy(eta = value, error = null, result = null) }
    }

    fun updateTimeStep(value: String) {
        _uiState.update { it.copy(timeStep = value, error = null, result = null) }
    }

    fun updateDuration(value: String) {
        _uiState.update { it.copy(duration = value, error = null, result = null) }
    }

    fun simulate() {
        _uiState.update { it.copy(isSimulating = true, error = null, result = null) }

        try {
            val input = TransientInput(
                steamFlowTH = _uiState.value.steamFlow.toDouble(),
                steamPressureBar = _uiState.value.steamPressure.toDouble(),
                steamTempC = _uiState.value.steamTemp.toDouble(),
                targetTempC = _uiState.value.targetTemp.toDouble(),
                sprayTempC = _uiState.value.sprayTemp.toDouble(),
                initialMetalTempC = _uiState.value.initialMetalTemp.toDouble(),
                platenMetalMassKg = _uiState.value.metalMass.toDouble(),
                metalSpecificHeat = _uiState.value.metalCp.toDouble(),
                burnerHeatMW = _uiState.value.burnerHeat.toDouble(),
                numberOfBurners = _uiState.value.burnerCount.toInt(),
                burnerToPlatenFraction = _uiState.value.eta.toDouble(),
                timeStepS = _uiState.value.timeStep.toDouble(),
                simulationDurationS = _uiState.value.duration.toDouble()
            )

            val validation = PlatenThermalInertiaModel.validate(input)
            when (validation) {
                is ValidationResult.Invalid -> {
                    _uiState.update {
                        it.copy(isSimulating = false, error = validation.message)
                    }
                    return
                }
                is ValidationResult.Valid -> { /* proceed */ }
            }

            val result = TransientSimulationRunner.simulate(input)
            _uiState.update {
                it.copy(isSimulating = false, result = result)
            }
        } catch (e: NumberFormatException) {
            _uiState.update {
                it.copy(isSimulating = false, error = "Please enter valid numbers for all fields.")
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isSimulating = false, error = "Simulation error: ${e.message}")
            }
        }
    }
}
