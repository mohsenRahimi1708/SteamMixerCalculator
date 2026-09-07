package com.steamcalc.engine

import com.hummeling.if97.OutOfRangeException

/**
 * Steady-state spray water attemperator mixing-point energy balance (Mode A — Required Spray).
 *
 * Governing equation (steady-state, no pressure drop):
 * ```
 * ṁ_steam · h_steam(P, T_steam) + ṁ_spray · h_water(P, T_spray)
 *     = (ṁ_steam + ṁ_spray) · h_out
 * ```
 *
 * Solved for required spray flow given a target outlet enthalpy:
 * ```
 * ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)
 * ```
 *
 * All enthalpy values come from the IF97 library — never from a constant-Cp shortcut.
 */
object SteadyStateSprayCalculator {

    /**
     * Validate inputs for steady-state calculation.
     */
    fun validate(input: SteadyStateInput): ValidationResult {
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

        // Validate that steam is in superheated region
        if (!SteamProperties.isSuperheated(input.steamPressureBar, input.steamTempC)) {
            val tSat = SteamProperties.saturationTemperature(input.steamPressureBar)
            return ValidationResult.Invalid(
                "Steam at ${input.steamTempC} °C, ${input.steamPressureBar} bar is outside superheated region " +
                "(T_sat ≈ ${"%.1f".format(tSat)} °C). Steam must be superheated."
            )
        }

        // Validate spray water is in compressed liquid region
        val tSat = SteamProperties.saturationTemperature(input.steamPressureBar)
        if (input.sprayTempC >= tSat) {
            return ValidationResult.Invalid(
                "Spray water at ${input.sprayTempC} °C exceeds saturation temperature " +
                "(T_sat ≈ ${"%.1f".format(tSat)} °C at ${input.steamPressureBar} bar). " +
                "Spray water must be subcooled liquid."
            )
        }

        return ValidationResult.Valid(input)
    }

    /**
     * Calculate the required spray water flow rate for steady-state mixing.
     *
     * @param input steady-state calculation inputs
     * @return [SteadyStateResult] with spray flow and full assumption set,
     *         or throws on IF97 out-of-range errors
     */
    @Throws(OutOfRangeException::class)
    fun calculate(input: SteadyStateInput): SteadyStateResult {
        val p = input.steamPressureBar
        val warnings = mutableListOf<String>()

        // Get enthalpies from IF97 library
        val hSteam = SteamProperties.enthalpyPT(p, input.steamTempC)        // kJ/kg — Region 2
        val hSpray = SteamProperties.enthalpyPT(p, input.sprayTempC)        // kJ/kg — Region 1
        val hOut = SteamProperties.enthalpyPT(p, input.targetTempC)         // kJ/kg — Region 2

        // Energy balance: ṁ_w = ṁ_s · (h_s - h_out) / (h_out - h_w)
        val denominator = hOut - hSpray
        val sprayFlowTH: Double
        if (denominator <= 0) {
            sprayFlowTH = Double.POSITIVE_INFINITY
            warnings.add(
                "Spray water enthalpy (${"%.2f".format(hSpray)} kJ/kg) ≥ target outlet enthalpy " +
                "(${"%.2f".format(hOut)} kJ/kg). Cooling is not possible with these spray conditions."
            )
        } else {
            sprayFlowTH = input.steamFlowTH * (hSteam - hOut) / denominator
        }

        if (sprayFlowTH <= 0) {
            warnings.add(
                "No cooling spray required — target temperature (${input.targetTempC} °C) " +
                "equals or exceeds steam inlet temperature (${input.steamTempC} °C)."
            )
        }

        // Check for wet steam condition (over-spray)
        if (sprayFlowTH > 0 && sprayFlowTH.isFinite()) {
            val hOutActual = (input.steamFlowTH * hSteam + sprayFlowTH * hSpray) /
                    (input.steamFlowTH + sprayFlowTH)
            val tSat = SteamProperties.saturationTemperature(p)
            val hAtTsat = SteamProperties.enthalpyPT(p, tSat + 0.01)
            if (hOutActual < hAtTsat) {
                warnings.add(
                    "Predicted outlet enthalpy would fall below saturation enthalpy " +
                    "at ${input.steamPressureBar} bar (T_sat ≈ ${"%.1f".format(tSat)} °C). " +
                    "This indicates wet steam / over-spray condition."
                )
            }
        }

        // Thermal shock advisory (Section 4)
        val metalSteamGap = input.steamTempC - input.initialMetalTempC
        if (metalSteamGap > THERMAL_SHOCK_THRESHOLD_C) {
            warnings.add(
                "Steam temperature significantly exceeds current platen metal temperature " +
                "(ΔT = ${"%.1f".format(metalSteamGap)} °C > ${"%.0f".format(THERMAL_SHOCK_THRESHOLD_C)} °C threshold) — " +
                "expect a real response lag beyond this steady-state estimate; consider the transient model instead."
            )
        }

        val temperatureDrop = input.steamTempC - input.targetTempC
        val combinedFlow = input.steamFlowTH + (if (sprayFlowTH.isFinite()) sprayFlowTH else 0.0)

        return SteadyStateResult(
            requiredSprayFlowTH = sprayFlowTH,
            temperatureDropC = temperatureDrop,
            outletTempC = input.targetTempC,
            assumptions = AssumptionSet(
                model = CalculationMode.STEADY_STATE,
                steamFlowTH = input.steamFlowTH,
                steamPressureBar = input.steamPressureBar,
                steamTempC = input.steamTempC,
                targetTempC = input.targetTempC,
                sprayTempC = input.sprayTempC,
                sprayFlowTH = sprayFlowTH,
                initialMetalTempC = input.initialMetalTempC
            ),
            warnings = warnings,
            hSteamKJkg = hSteam,
            hSprayKJkg = hSpray,
            hOutKJkg = hOut,
            combinedFlowTH = combinedFlow
        )
    }
}
