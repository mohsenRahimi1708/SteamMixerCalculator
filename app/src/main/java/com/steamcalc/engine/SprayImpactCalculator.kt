package com.steamcalc.engine

import com.hummeling.if97.OutOfRangeException

/**
 * Spray Impact calculator (Mode B — Forward calculation).
 *
 * Given a spray flow the user actually intends to apply, calculates the
 * resulting outlet temperature and temperature drop. This is a direct
 * forward calculation — no target temperature input, no root-finding.
 *
 * Governing equation:
 * ```
 * h_out = (ṁ_s · h_s + ṁ_w · h_w) / (ṁ_s + ṁ_w)
 * T_out = t_ph(P, h_out)    ← direct backward-equation call
 * ΔT = T_steam − T_out
 * ```
 */
object SprayImpactCalculator {

    /**
     * Validate inputs for spray impact calculation.
     */
    fun validate(input: SprayImpactInput): ValidationResult {
        if (input.steamFlowTH <= 0) {
            return ValidationResult.Invalid("Steam mass flow must be positive (got ${input.steamFlowTH} t/h)")
        }
        if (input.steamPressureBar < 20 || input.steamPressureBar > 170) {
            return ValidationResult.Invalid("Steam pressure must be 20–170 bar (got ${input.steamPressureBar})")
        }
        if (input.steamTempC < 200 || input.steamTempC > 560) {
            return ValidationResult.Invalid("Steam temperature must be 200–560 °C (got ${input.steamTempC})")
        }
        if (input.sprayFlowTH < 0) {
            return ValidationResult.Invalid("Spray flow must be non-negative (got ${input.sprayFlowTH} t/h)")
        }
        if (input.sprayFlowTH > input.steamFlowTH * 2) {
            return ValidationResult.Invalid(
                "Spray flow (${input.sprayFlowTH} t/h) exceeds 2× steam flow — this is likely an input error."
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
     * Calculate the resulting outlet temperature for a given spray flow.
     *
     * @param input spray impact calculation inputs
     * @return [SprayImpactResult] with outlet temperature and full assumption set
     * @throws OutOfRangeException from IF97 library
     */
    @Throws(OutOfRangeException::class)
    fun calculate(input: SprayImpactInput): SprayImpactResult {
        val p = input.steamPressureBar
        val warnings = mutableListOf<String>()

        // Get enthalpies from IF97 library
        val hSteam = SteamProperties.enthalpyPT(p, input.steamTempC)    // kJ/kg — Region 2
        val hSpray = SteamProperties.enthalpyPT(p, input.sprayTempC)    // kJ/kg — Region 1

        // Zero spray case
        if (input.sprayFlowTH <= 0) {
            warnings.add("Zero spray flow — outlet temperature equals steam inlet temperature.")
            return SprayImpactResult(
                outletTempC = input.steamTempC,
                temperatureDropC = 0.0,
                outletEnthalpyKJkg = hSteam,
                assumptions = AssumptionSet(
                    model = CalculationMode.SPRAY_IMPACT,
                    steamFlowTH = input.steamFlowTH,
                    steamPressureBar = input.steamPressureBar,
                    steamTempC = input.steamTempC,
                    targetTempC = null,
                    sprayTempC = input.sprayTempC,
                    sprayFlowTH = input.sprayFlowTH,
                    initialMetalTempC = input.initialMetalTempC
                ),
                warnings = warnings,
                hSteamKJkg = hSteam,
                hSprayKJkg = hSpray,
                combinedFlowTH = input.steamFlowTH
            )
        }

        // Forward energy balance: h_out = (ṁ_s · h_s + ṁ_w · h_w) / (ṁ_s + ṁ_w)
        val combinedFlow = input.steamFlowTH + input.sprayFlowTH
        val hOut = (input.steamFlowTH * hSteam + input.sprayFlowTH * hSpray) / combinedFlow

        // Backward equation: T_out = t_ph(P, h_out)
        val tSat = SteamProperties.saturationTemperature(p)
        val tOut = SteamProperties.temperaturePH(p, hOut)

        // Temperature drop
        val deltaT = input.steamTempC - tOut

        // Warn if outlet is below saturation temperature (wet steam)
        if (tOut <= tSat) {
            warnings.add(
                "Outlet temperature (${"%.1f".format(tOut)} °C) falls below saturation temperature " +
                "(${"%.1f".format(tSat)} °C at ${input.steamPressureBar} bar). " +
                "This indicates wet steam / full quenching — outside this model's valid output range."
            )
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

        return SprayImpactResult(
            outletTempC = tOut,
            temperatureDropC = deltaT,
            outletEnthalpyKJkg = hOut,
            assumptions = AssumptionSet(
                model = CalculationMode.SPRAY_IMPACT,
                steamFlowTH = input.steamFlowTH,
                steamPressureBar = input.steamPressureBar,
                steamTempC = input.steamTempC,
                targetTempC = null,
                sprayTempC = input.sprayTempC,
                sprayFlowTH = input.sprayFlowTH,
                initialMetalTempC = input.initialMetalTempC
            ),
            warnings = warnings,
            hSteamKJkg = hSteam,
            hSprayKJkg = hSpray,
            combinedFlowTH = combinedFlow
        )
    }
}
