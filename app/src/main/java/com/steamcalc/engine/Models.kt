package com.steamcalc.engine

import java.util.UUID
import java.time.Instant

// ─── Constants ────────────────────────────────────────────────────────────

/** Temperature threshold (°C) below which a thermal-shock advisory is shown */
const val THERMAL_SHOCK_THRESHOLD_C = 50.0

// ─── Input Data Classes ────────────────────────────────────────────────────

/**
 * Steady-state spray calculation inputs (Mode A — Required Spray).
 * All units match the UI: t/h, bar, °C.
 * Spray water pressure = steam pressure (no separate field).
 */
data class SteadyStateInput(
    val steamFlowTH: Double,       // t/h
    val steamPressureBar: Double,  // bar
    val steamTempC: Double,        // °C
    val targetTempC: Double,       // °C — target outlet temperature
    val sprayTempC: Double,        // °C — spray water temperature
    val initialMetalTempC: Double  // °C — initial platen metal temperature (mandatory)
)

/**
 * Spray Impact calculation inputs (Mode B — Forward calculation).
 * Given an actual spray flow, compute the resulting outlet temperature.
 */
data class SprayImpactInput(
    val steamFlowTH: Double,       // t/h
    val steamPressureBar: Double,  // bar
    val steamTempC: Double,        // °C
    val sprayFlowTH: Double,       // t/h — actual spray flow to apply
    val sprayTempC: Double,        // °C — spray water temperature
    val initialMetalTempC: Double  // °C — initial platen metal temperature (mandatory)
)

/**
 * Transient simulation input parameters.
 */
data class TransientInput(
    // Steam side
    val steamFlowTH: Double,       // t/h
    val steamPressureBar: Double,  // bar
    val steamTempC: Double,        // °C — initial steam inlet temp to platen
    val targetTempC: Double,       // °C — target outlet after spray
    val sprayTempC: Double,        // °C — spray water temperature
    val initialMetalTempC: Double, // °C — initial platen metal temperature (mandatory)
    // Platen metal
    val platenMetalMassKg: Double,     // kg (default ~62,600)
    val metalSpecificHeat: Double,     // J/(kg·K)
    // Burner
    val burnerHeatMW: Double,          // MW per burner (default 40)
    val numberOfBurners: Int,          // count
    val burnerToPlatenFraction: Double, // η (0.0–1.0)
    // Simulation
    val timeStepS: Double,             // seconds (default 10)
    val simulationDurationS: Double    // total seconds to simulate
)

// ─── Calculation Mode ──────────────────────────────────────────────────────

enum class CalculationMode { STEADY_STATE, SPRAY_IMPACT, TRANSIENT }

// ─── Output Data Classes ───────────────────────────────────────────────────

/**
 * Full assumption set — displayed alongside every result per §5 transparency requirement.
 */
data class AssumptionSet(
    val calculationId: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    val model: CalculationMode,
    val steamFlowTH: Double,
    val steamPressureBar: Double,
    val steamTempC: Double,
    val targetTempC: Double?,       // null for Mode B (no target)
    val sprayTempC: Double,
    val sprayFlowTH: Double?,       // null for Mode A (spray is the result)
    val initialMetalTempC: Double,
    val platenMetalMassKg: Double? = null,
    val metalSpecificHeat: Double? = null,
    val burnerHeatMW: Double? = null,
    val numberOfBurners: Int? = null,
    val burnerToPlatenFraction: Double? = null,
    val currentMetalTempC: Double? = null
)

/**
 * Steady-state spray calculation result (Mode A).
 */
data class SteadyStateResult(
    val requiredSprayFlowTH: Double,      // t/h (negative = no spray needed)
    val temperatureDropC: Double,         // °C
    val outletTempC: Double,              // °C
    val assumptions: AssumptionSet,
    val warnings: List<String> = emptyList(),
    // Detailed enthalpy chain for Energy Balance screen
    val hSteamKJkg: Double,
    val hSprayKJkg: Double,
    val hOutKJkg: Double,
    val combinedFlowTH: Double
)

/**
 * Spray Impact calculation result (Mode B — forward calculation).
 */
data class SprayImpactResult(
    val outletTempC: Double,              // °C — resulting outlet temperature
    val temperatureDropC: Double,         // °C — temperature drop
    val outletEnthalpyKJkg: Double,       // kJ/kg — outlet enthalpy
    val assumptions: AssumptionSet,
    val warnings: List<String> = emptyList(),
    // Detailed enthalpy chain
    val hSteamKJkg: Double,
    val hSprayKJkg: Double,
    val combinedFlowTH: Double
)

/**
 * One time-step snapshot in the transient simulation.
 */
data class TransientStep(
    val timeS: Double,
    val metalTempC: Double,
    val steamInletTempC: Double,  // = metal temp (platen outlet)
    val steamOutletTempC: Double, // = target (spray holds it)
    val requiredSprayFlowTH: Double,
    val netHeatMW: Double,
    val burnerHeatInMW: Double,
    val sprayHeatOutMW: Double
)

/**
 * Complete transient simulation result.
 */
data class TransientResult(
    val steps: List<TransientStep>,
    val assumptions: AssumptionSet,
    val warnings: List<String> = emptyList(),
    val finalSteadyStateSprayTH: Double
)

// ─── Validation ────────────────────────────────────────────────────────────

sealed class ValidationResult {
    data class Valid(val input: Any) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}
