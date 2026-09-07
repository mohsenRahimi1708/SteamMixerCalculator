package com.steamcalc.engine

import com.hummeling.if97.OutOfRangeException

/**
 * Platen superheater metal thermal inertia model.
 *
 * The platen superheater's metal mass is a real thermal capacitance that
 * responds on its own timescale, separate from the steam-side steady-state balance.
 *
 * ```
 * C_metal = M_metal · Cp_metal
 * dT_metal/dt = Q_net / (M_metal · Cp_metal)
 * ```
 *
 * where:
 * - `M_metal`: platen tube metal mass (editable, default ≈ 62,600 kg)
 * - `Cp_metal`: metal specific heat (editable, default for alloy steel)
 * - `Q_net = Q_burner_to_platen − Q_removed_by_spray`
 * - `Q_burner_to_platen = η_platen · Q_burner`
 * - `η_platen`: fraction of burner heat absorbed by platen (editable, 0.0–1.0)
 */
object PlatenThermalInertiaModel {

    /** Convert spray flow from t/h to kg/s. 1 t/h = 1000/3600 kg/s. */
    private const val TPH_TO_KGPS = 1000.0 / 3600.0

    /**
     * Validate transient input parameters.
     */
    fun validate(input: TransientInput): ValidationResult {
        if (input.steamFlowTH <= 0) {
            return ValidationResult.Invalid("Steam mass flow must be positive (got ${input.steamFlowTH} t/h)")
        }
        if (input.steamPressureBar < 20 || input.steamPressureBar > 170) {
            return ValidationResult.Invalid("Steam pressure must be 20–170 bar (got ${input.steamPressureBar})")
        }
        if (input.steamTempC < 200 || input.steamTempC > 560) {
            return ValidationResult.Invalid("Steam temperature must be 200–560 °C (got ${input.steamTempC})")
        }
        if (input.targetTempC > input.steamTempC) {
            return ValidationResult.Invalid(
                "Target temperature (${input.targetTempC} °C) must be ≤ steam inlet temperature (${input.steamTempC} °C)"
            )
        }
        if (input.sprayTempC < 0 || input.sprayTempC > 600) {
            return ValidationResult.Invalid("Spray water temperature must be 0–600 °C (got ${input.sprayTempC})")
        }
        if (input.initialMetalTempC < 0 || input.initialMetalTempC > 700) {
            return ValidationResult.Invalid("Initial metal temperature must be 0–700 °C (got ${input.initialMetalTempC})")
        }
        if (input.platenMetalMassKg <= 0) {
            return ValidationResult.Invalid("Platen metal mass must be positive (got ${input.platenMetalMassKg} kg)")
        }
        if (input.metalSpecificHeat <= 0) {
            return ValidationResult.Invalid("Metal specific heat must be positive (got ${input.metalSpecificHeat} J/(kg·K))")
        }
        if (input.burnerHeatMW <= 0) {
            return ValidationResult.Invalid("Burner heat must be positive (got ${input.burnerHeatMW} MW)")
        }
        if (input.numberOfBurners <= 0) {
            return ValidationResult.Invalid("Number of burners must be positive (got ${input.numberOfBurners})")
        }
        if (input.burnerToPlatenFraction < 0 || input.burnerToPlatenFraction > 1) {
            return ValidationResult.Invalid(
                "Burner-to-platen fraction must be 0.0–1.0 (got ${input.burnerToPlatenFraction})"
            )
        }
        if (input.timeStepS <= 0) {
            return ValidationResult.Invalid("Time step must be positive (got ${input.timeStepS} s)")
        }
        if (input.simulationDurationS <= 0) {
            return ValidationResult.Invalid("Simulation duration must be positive (got ${input.simulationDurationS} s)")
        }

        // Validate steam is superheated at initial conditions
        if (!SteamProperties.isSuperheated(input.steamPressureBar, input.steamTempC)) {
            val tSat = SteamProperties.saturationTemperature(input.steamPressureBar)
            return ValidationResult.Invalid(
                "Steam at ${input.steamTempC} °C, ${input.steamPressureBar} bar is outside superheated region " +
                "(T_sat ≈ ${"%.1f".format(tSat)} °C)"
            )
        }

        return ValidationResult.Valid(input)
    }

    /**
     * Compute the total burner heat input to the platen metal.
     */
    fun totalBurnerHeatMW(input: TransientInput): Double =
        input.burnerToPlatenFraction * input.numberOfBurners * input.burnerHeatMW

    /**
     * Compute the thermal capacitance of the platen metal.
     */
    fun thermalCapacitance(input: TransientInput): Double =
        input.platenMetalMassKg * input.metalSpecificHeat

    /**
     * Compute the required spray flow (t/h) to maintain target outlet temperature
     * given the current metal temperature.
     */
    @Throws(OutOfRangeException::class)
    fun requiredSprayFlow(input: TransientInput, currentMetalTempC: Double): Double {
        val p = input.steamPressureBar

        val hSteamIn = SteamProperties.enthalpyPT(p, currentMetalTempC)
        val hOut = SteamProperties.enthalpyPT(p, input.targetTempC)
        val hSpray = SteamProperties.enthalpyPT(p, input.sprayTempC)

        val denominator = hOut - hSpray
        if (denominator <= 0) return Double.POSITIVE_INFINITY

        return input.steamFlowTH * (hSteamIn - hOut) / denominator
    }

    /**
     * Compute the heat removed by spray water in MW.
     */
    @Throws(OutOfRangeException::class)
    fun sprayHeatRemovalMW(sprayFlowTH: Double, input: TransientInput): Double {
        if (sprayFlowTH <= 0) return 0.0

        val p = input.steamPressureBar
        val hOut = SteamProperties.enthalpyPT(p, input.targetTempC)
        val hSpray = SteamProperties.enthalpyPT(p, input.sprayTempC)

        return sprayFlowTH * (hOut - hSpray) / 3600.0
    }

    /**
     * Compute the change in metal temperature for one time step.
     */
    fun deltaMetalTemp(netHeatMW: Double, input: TransientInput, dtS: Double): Double {
        val thermalCap = thermalCapacitance(input)
        return netHeatMW * 1e6 * dtS / thermalCap
    }
}
