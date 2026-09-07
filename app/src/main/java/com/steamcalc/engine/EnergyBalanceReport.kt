package com.steamcalc.engine

/**
 * Detailed energy balance breakdown showing the full calculation chain:
 * ```
 * burner heat → heat to platen → platen thermal response
 * → steam heat load → required cooling → spray-water enthalpy → required spray flow
 * ```
 *
 * This is displayed on the "Energy Balance / Why?" screen for any result,
 * providing full transparency into how the spray number was produced.
 */
data class EnergyBalanceReport(
    // ─── Inputs ───────────────────────────────────────────────────────
    val steamFlowTH: Double,
    val steamPressureBar: Double,
    val steamInletTempC: Double,
    val targetOutletTempC: Double,
    val sprayWaterTempC: Double,
    val metalMassKg: Double,
    val metalSpecificHeat: Double,
    val burnerHeatPerBurnerMW: Double,
    val numberOfBurners: Int,
    val burnerToPlatenFraction: Double,
    val currentMetalTempC: Double,
    val calculationMode: CalculationMode,

    // ─── Step 1: Burner heat → platen ─────────────────────────────────
    val totalBurnerHeatMW: Double,
    val heatToPlatenMW: Double,  // = η · totalBurnerHeat

    // ─── Step 2: Steam-side enthalpies ────────────────────────────────
    val hSteamInletKJkg: Double,   // h(P, T_steam_inlet) — from platen
    val hTargetOutletKJkg: Double, // h(P, T_target)
    val hSprayWaterKJkg: Double,   // h(P, T_spray)

    // ─── Step 3: Required cooling ─────────────────────────────────────
    val requiredSprayFlowTH: Double,
    val heatRemovedBySprayMW: Double,

    // ─── Step 4: Net heat balance ─────────────────────────────────────
    val netHeatToMetalMW: Double,

    // ─── Step 5: Metal temperature response ───────────────────────────
    val metalTemperatureChangeRate: Double, // °C/s

    // ─── Warnings ─────────────────────────────────────────────────────
    val warnings: List<String> = emptyList()
) {
    /**
     * Format the full report as a human-readable string for display.
     */
    fun formatReport(): String = buildString {
        appendLine("═══════════════════════════════════════════")
        appendLine("  ENERGY BALANCE REPORT")
        appendLine("  Model: ${if (calculationMode == CalculationMode.STEADY_STATE) "Steady-State" else "Transient"}")
        appendLine("═══════════════════════════════════════════")
        appendLine()

        appendLine("── INPUT CONDITIONS ──")
        appendLine("  Steam flow:        ${"%.1f".format(steamFlowTH)} t/h")
        appendLine("  Steam pressure:    ${"%.1f".format(steamPressureBar)} bar")
        appendLine("  Steam inlet temp:  ${"%.1f".format(steamInletTempC)} °C")
        appendLine("  Target outlet:     ${"%.1f".format(targetOutletTempC)} °C")
        appendLine("  Spray water temp:  ${"%.1f".format(sprayWaterTempC)} °C")
        appendLine()

        if (calculationMode == CalculationMode.TRANSIENT) {
            appendLine("── PLATEN & BURNER ──")
            appendLine("  Metal mass:        ${"%.0f".format(metalMassKg)} kg")
            appendLine("  Metal Cp:          ${"%.1f".format(metalSpecificHeat)} J/(kg·K)")
            appendLine("  Burner heat:       ${"%.1f".format(burnerHeatPerBurnerMW)} MW × ${numberOfBurners} burners")
            appendLine("  η (to platen):     ${"%.4f".format(burnerToPlatenFraction)}")
            appendLine("  Current metal T:   ${"%.1f".format(currentMetalTempC)} °C")
            appendLine()
        }

        appendLine("── STEP 1: BURNER → PLATEN HEAT ──")
        appendLine("  Total burner MW:   ${"%.2f".format(totalBurnerHeatMW)} MW")
        appendLine("  Heat to platen:    ${"%.2f".format(heatToPlatenMW)} MW")
        appendLine()

        appendLine("── STEP 2: STEAM-SIDE ENTHALPIES ──")
        appendLine("  h_steam(P,T_in):   ${"%.2f".format(hSteamInletKJkg)} kJ/kg")
        appendLine("  h_target(P,T_out): ${"%.2f".format(hTargetOutletKJkg)} kJ/kg")
        appendLine("  h_spray(P,T_spray):${"%.2f".format(hSprayWaterKJkg)} kJ/kg")
        appendLine()

        appendLine("── STEP 3: REQUIRED COOLING ──")
        appendLine("  Δh (steam→target): ${"%.2f".format(hSteamInletKJkg - hTargetOutletKJkg)} kJ/kg")
        appendLine("  Δh (target→spray): ${"%.2f".format(hTargetOutletKJkg - hSprayWaterKJkg)} kJ/kg")
        appendLine("  Spray flow:        ${"%.2f".format(requiredSprayFlowTH)} t/h")
        appendLine("  Heat removed:      ${"%.2f".format(heatRemovedBySprayMW)} MW")
        appendLine()

        appendLine("── STEP 4: NET HEAT BALANCE ──")
        appendLine("  Q_in (to platen):  ${"%.2f".format(heatToPlatenMW)} MW")
        appendLine("  Q_out (by spray):  ${"%.2f".format(heatRemovedBySprayMW)} MW")
        appendLine("  Q_net:             ${"%.2f".format(netHeatToMetalMW)} MW")
        appendLine()

        if (calculationMode == CalculationMode.TRANSIENT) {
            appendLine("── STEP 5: METAL RESPONSE ──")
            appendLine("  dT_metal/dt:       ${"%.6f".format(metalTemperatureChangeRate)} °C/s")
            appendLine("  dT_metal/dt:       ${"%.4f".format(metalTemperatureChangeRate * 60)} °C/min")
            appendLine()
        }

        if (warnings.isNotEmpty()) {
            appendLine("── WARNINGS ──")
            warnings.forEach { w -> appendLine("  ⚠ $w") }
        }

        appendLine("═══════════════════════════════════════════")
    }

    companion object {
        /**
         * Build an energy balance report from a steady-state calculation result.
         */
        fun fromSteadyState(result: SteadyStateResult, input: SteadyStateInput): EnergyBalanceReport {
            val totalBurnerMW = 0.0 // No burner in steady-state
            val heatToPlaten = 0.0
            val heatRemoved = result.requiredSprayFlowTH *
                    (result.hOutKJkg - result.hSprayKJkg) / 3600.0
            val netHeat = totalBurnerMW - heatRemoved

            return EnergyBalanceReport(
                steamFlowTH = input.steamFlowTH,
                steamPressureBar = input.steamPressureBar,
                steamInletTempC = input.steamTempC,
                targetOutletTempC = input.targetTempC,
                sprayWaterTempC = input.sprayTempC,
                metalMassKg = 0.0,
                metalSpecificHeat = 0.0,
                burnerHeatPerBurnerMW = 0.0,
                numberOfBurners = 0,
                burnerToPlatenFraction = 0.0,
                currentMetalTempC = input.steamTempC,
                calculationMode = CalculationMode.STEADY_STATE,
                totalBurnerHeatMW = totalBurnerMW,
                heatToPlatenMW = heatToPlaten,
                hSteamInletKJkg = result.hSteamKJkg,
                hTargetOutletKJkg = result.hOutKJkg,
                hSprayWaterKJkg = result.hSprayKJkg,
                requiredSprayFlowTH = result.requiredSprayFlowTH,
                heatRemovedBySprayMW = heatRemoved,
                netHeatToMetalMW = netHeat,
                metalTemperatureChangeRate = 0.0,
                warnings = result.warnings
            )
        }

        /**
         * Build an energy balance report from a specific transient step.
         */
        fun fromTransientStep(
            step: TransientStep,
            input: TransientInput,
            result: TransientResult
        ): EnergyBalanceReport {
            val thermalCap = PlatenThermalInertiaModel.thermalCapacitance(input)
            val dtRate = step.netHeatMW * 1e6 / thermalCap  // °C/s

            return EnergyBalanceReport(
                steamFlowTH = input.steamFlowTH,
                steamPressureBar = input.steamPressureBar,
                steamInletTempC = input.steamTempC,
                targetOutletTempC = input.targetTempC,
                sprayWaterTempC = input.sprayTempC,
                metalMassKg = input.platenMetalMassKg,
                metalSpecificHeat = input.metalSpecificHeat,
                burnerHeatPerBurnerMW = input.burnerHeatMW,
                numberOfBurners = input.numberOfBurners,
                burnerToPlatenFraction = input.burnerToPlatenFraction,
                currentMetalTempC = step.metalTempC,
                calculationMode = CalculationMode.TRANSIENT,
                totalBurnerHeatMW = step.burnerHeatInMW,
                heatToPlatenMW = step.burnerHeatInMW,
                hSteamInletKJkg = try {
                    SteamProperties.enthalpyPT(input.steamPressureBar, step.steamInletTempC)
                } catch (_: Exception) { 0.0 },
                hTargetOutletKJkg = try {
                    SteamProperties.enthalpyPT(input.steamPressureBar, input.targetTempC)
                } catch (_: Exception) { 0.0 },
                hSprayWaterKJkg = try {
                    SteamProperties.enthalpyPT(input.steamPressureBar, input.sprayTempC)
                } catch (_: Exception) { 0.0 },
                requiredSprayFlowTH = step.requiredSprayFlowTH,
                heatRemovedBySprayMW = step.sprayHeatOutMW,
                netHeatToMetalMW = step.netHeatMW,
                metalTemperatureChangeRate = dtRate,
                warnings = result.warnings
            )
        }
    }
}
