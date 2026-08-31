package com.steamcalc.calculator

import com.steamcalc.if97.IF97Region1
import com.steamcalc.if97.IF97Region2
import com.steamcalc.if97.IF97Common

/**
 * Steam Mixer Calculator — spray water attemperator mixing-point energy balance.
 *
 * Governing equation (steady-state, no pressure drop):
 *   ṁ_steam · h_steam(P, T_steam) + ṁ_spray · h_water(P, T_spray)
 *       = (ṁ_steam + ṁ_spray) · h_out
 *
 * Solve for T_out such that h_steam(P, T_out) = h_out
 * using bisection over T ∈ [T_sat(P), 600 °C].
 */
object SteamMixerCalculator {

    /** Temperature tolerance for bisection, °C */
    private const val TOLERANCE = 0.001

    /** Maximum bisection iterations */
    private const val MAX_ITER = 100

    /** Conversion factor */
    private const val DEG_TO_K = 273.15

    /**
     * Input validation result.
     */
    sealed class ValidationResult {
        data class Valid(
            val steamFlow: Double,
            val steamPressure: Double,
            val steamTemp: Double,
            val sprayFlow: Double,
            val sprayTemp: Double
        ) : ValidationResult()

        data class Invalid(val message: String) : ValidationResult()
    }

    /**
     * Calculation result.
     */
    data class CalculationResult(
        val outletTempC: Double,
        val deltaT: Double,
        val combinedFlow: Double,
        val warning: String? = null
    )

    /**
     * Validate all inputs.
     * Spray water pressure equals steam pressure.
     */
    fun validate(
        steamFlowTH: Double,
        steamPressureBar: Double,
        steamTempC: Double,
        sprayFlowTH: Double,
        sprayTempC: Double
    ): ValidationResult {
        if (steamFlowTH < 300.0 || steamFlowTH > 1000.0) {
            return ValidationResult.Invalid("Steam mass flow must be 300–1000 t/h (got $steamFlowTH)")
        }
        if (steamPressureBar < 20.0 || steamPressureBar > 170.0) {
            return ValidationResult.Invalid("Steam pressure must be 20–170 bar (got $steamPressureBar)")
        }
        if (steamTempC < 200.0 || steamTempC > 560.0) {
            return ValidationResult.Invalid("Steam temperature must be 200–560 °C (got $steamTempC)")
        }
        if (sprayFlowTH <= 0.0) {
            return ValidationResult.Invalid("Spray water mass flow must be positive (got $sprayFlowTH)")
        }
        if (sprayTempC < 0.0 || sprayTempC > 600.0) {
            return ValidationResult.Invalid("Spray water temperature must be 0–600 °C (got $sprayTempC)")
        }

        val steamPressureMPa = steamPressureBar / 10.0
        val steamTempK = steamTempC + DEG_TO_K
        val sprayTempK = sprayTempC + DEG_TO_K

        // Validate steam is in Region 2 (superheated)
        if (!IF97Region2.isValid(steamPressureMPa, steamTempK)) {
            val tSat = IF97Common.saturationTemperature(steamPressureMPa)
            val tSatC = tSat - DEG_TO_K
            return ValidationResult.Invalid(
                "Invalid input: steam at ${steamTempC}°C, ${steamPressureBar} bar " +
                "is outside superheated steam region (T_sat = ${"%.1f".format(tSatC)}°C)"
            )
        }

        // Validate spray water is in Region 1 (compressed liquid)
        // Spraying into a region where T_spray < T_sat is fine — it's subcooled liquid
        // But T_spray must be > 0°C and within Region 1 bounds
        if (sprayTempK < 273.15 || sprayTempK > 623.15) {
            return ValidationResult.Invalid(
                "Spray water temperature outside Region 1 validity (273.15–623.15 K)"
            )
        }

        return ValidationResult.Valid(
            steamFlowTH, steamPressureBar, steamTempC,
            sprayFlowTH, sprayTempC
        )
    }

    /**
     * Perform the mixing calculation.
     *
     * @return [CalculationResult] on success, or null on error.
     */
    fun calculate(
        steamFlowTH: Double,
        steamPressureBar: Double,
        steamTempC: Double,
        sprayFlowTH: Double,
        sprayTempC: Double
    ): CalculationResult? {
        val P_MPa = steamPressureBar / 10.0
        val T_steam_K = steamTempC + DEG_TO_K
        val T_spray_K = sprayTempC + DEG_TO_K

        // Compute enthalpies
        val hSteam = IF97Region2.enthalpy(P_MPa, T_steam_K)
        val hSpray = IF97Region1.enthalpy(P_MPa, T_spray_K)

        // Energy balance
        val hOut = (steamFlowTH * hSteam + sprayFlowTH * hSpray) / (steamFlowTH + sprayFlowTH)

        // Bisection: find T_out such that h_steam(P, T_out) = h_out
        val tSatK = IF97Common.saturationTemperature(P_MPa)
        var tLow = tSatK + 0.001  // just above saturation
        var tHigh = 873.15        // 600 °C

        // Check that h_out is achievable in Region 2
        val hAtTsat = IF97Region2.enthalpy(P_MPa, tLow)
        val hAt600C = IF97Region2.enthalpy(P_MPa, tHigh)

        if (hOut < hAtTsat) {
            // Outlet would be wet steam — full quenching
            val combinedFlow = steamFlowTH + sprayFlowTH
            return CalculationResult(
                outletTempC = Double.NaN,
                deltaT = Double.NaN,
                combinedFlow = combinedFlow,
                warning = "Warning: outlet temperature would fall below T_sat(${"%.1f".format(steamPressureBar)} bar) " +
                        "= ${"%.1f".format(tSatK - DEG_TO_K)}°C. " +
                        "This indicates full quenching / wet steam — outside the valid superheated mixing model."
            )
        }

        if (hOut > hAt600C) {
            // h_out exceeds valid range — use 600°C as limit
            tHigh = 873.15
        }

        // Bisection search
        for (iter in 0 until MAX_ITER) {
            val tMid = (tLow + tHigh) / 2.0
            val hMid = IF97Region2.enthalpy(P_MPa, tMid)

            if (Math.abs(hMid - hOut) < 0.01) break  // 0.01 kJ/kg tolerance

            if (hMid < hOut) {
                tLow = tMid
            } else {
                tHigh = tMid
            }

            if (tHigh - tLow < TOLERANCE) break
        }

        val tOutK = (tLow + tHigh) / 2.0
        val tOutC = tOutK - DEG_TO_K

        return CalculationResult(
            outletTempC = tOutC,
            deltaT = steamTempC - tOutC,
            combinedFlow = steamFlowTH + sprayFlowTH,
            warning = null
        )
    }
}
