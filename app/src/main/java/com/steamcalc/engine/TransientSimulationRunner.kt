package com.steamcalc.engine

import com.hummeling.if97.OutOfRangeException

/**
 * Time-stepped transient simulation that combines burner heat input,
 * platen metal thermal inertia, and spray-water demand.
 *
 * At each time step:
 * 1. Burner heat input to platen metal: `Q_in = η · N_burners · Q_per_burner`
 * 2. Steam leaving platen is at metal temperature (simplified heat transfer)
 * 3. Required spray flow to maintain target outlet temperature
 * 4. Heat removed by spray: `Q_spray = ṁ_w · (h_out - h_spray)`
 * 5. Net heat into metal: `Q_net = Q_in - Q_spray`
 * 6. Metal temperature update: `T(t+dt) = T(t) + Q_net · dt / (M · Cp)`
 */
object TransientSimulationRunner {

    @Throws(OutOfRangeException::class)
    fun simulate(input: TransientInput): TransientResult {
        val warnings = mutableListOf<String>()
        val steps = mutableListOf<TransientStep>()

        val dt = input.timeStepS
        val totalSteps = (input.simulationDurationS / dt).toInt()
        val totalBurnerHeat = PlatenThermalInertiaModel.totalBurnerHeatMW(input)

        // Initial conditions: metal at user-specified initial temperature
        var tMetal = input.initialMetalTempC

        for (step in 0..totalSteps) {
            val timeS = step * dt
            val tSteamIn = tMetal

            var sprayFlowTH = PlatenThermalInertiaModel.requiredSprayFlow(input, tSteamIn)
            val sprayFlowActual = if (sprayFlowTH.isFinite() && sprayFlowTH > 0) sprayFlowTH else 0.0

            val sprayHeatMW = PlatenThermalInertiaModel.sprayHeatRemovalMW(sprayFlowActual, input)
            val netHeatMW = totalBurnerHeat - sprayHeatMW

            steps.add(
                TransientStep(
                    timeS = timeS,
                    metalTempC = tMetal,
                    steamInletTempC = tSteamIn,
                    steamOutletTempC = input.targetTempC,
                    requiredSprayFlowTH = sprayFlowActual,
                    netHeatMW = netHeatMW,
                    burnerHeatInMW = totalBurnerHeat,
                    sprayHeatOutMW = sprayHeatMW
                )
            )

            if (step < totalSteps) {
                val dT = PlatenThermalInertiaModel.deltaMetalTemp(netHeatMW, input, dt)
                tMetal += dT
            }
        }

        val finalSteadySpray = try {
            PlatenThermalInertiaModel.requiredSprayFlow(input, tMetal)
        } catch (e: OutOfRangeException) {
            warnings.add("Could not compute final steady-state spray: ${e.message}")
            0.0
        }

        if (totalBurnerHeat <= 0) {
            warnings.add("No burner heat input — metal temperature will remain constant.")
        }

        return TransientResult(
            steps = steps,
            assumptions = AssumptionSet(
                model = CalculationMode.TRANSIENT,
                steamFlowTH = input.steamFlowTH,
                steamPressureBar = input.steamPressureBar,
                steamTempC = input.steamTempC,
                targetTempC = input.targetTempC,
                sprayTempC = input.sprayTempC,
                sprayFlowTH = null,
                initialMetalTempC = input.initialMetalTempC,
                platenMetalMassKg = input.platenMetalMassKg,
                metalSpecificHeat = input.metalSpecificHeat,
                burnerHeatMW = input.burnerHeatMW,
                numberOfBurners = input.numberOfBurners,
                burnerToPlatenFraction = input.burnerToPlatenFraction,
                currentMetalTempC = tMetal
            ),
            warnings = warnings,
            finalSteadyStateSprayTH = if (finalSteadySpray.isFinite() && finalSteadySpray > 0) finalSteadySpray else 0.0
        )
    }
}
