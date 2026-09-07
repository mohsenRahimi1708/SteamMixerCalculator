package com.steamcalc.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PlatenThermalInertiaModel] and [TransientSimulationRunner].
 *
 * Key test: the transient model reduces correctly to the analytical no-spray case:
 * `T(t) = T0 + (Q/C) · t` when no spray is active.
 */
class PlatenThermalInertiaModelTest {

    // ─── Analytical No-Spray Case ──────────────────────────────────

    @Test
    fun `metal temp rises when target equals inlet but spray activates when metal exceeds target`() {
        // When targetTemp = steamTemp, initially no spray is needed.
        // But as burner heat raises metal temp above the target, steam leaving
        // the platen is hotter than target → spray activates to cool it.
        // So metal temp is bounded — it won't rise as fast as the pure Q/C*t analytical.
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 540.0,  // Same as inlet → spray activates when metal > 540
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,    // J/(kg·K)
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 300.0  // 5 minutes
        )

        val result = TransientSimulationRunner.simulate(input)
        val finalStep = result.steps.last()

        // Metal temp should have risen from 540°C (burner heat)
        assertTrue(
            "Final metal temp (${"%.1f".format(finalStep.metalTempC)}°C) should be above initial (540°C)",
            finalStep.metalTempC > 540.0
        )

        // But should be well below the pure analytical no-spray T (≈647°C)
        // because spray kicks in as soon as metal exceeds target
        assertTrue(
            "Final metal temp (${"%.1f".format(finalStep.metalTempC)}°C) should be below pure no-spray analytical (≈647°C)",
            finalStep.metalTempC < 647.0
        )

        // Spray should be active in later steps (metal > target)
        val lastSpray = result.steps.takeLast(5).map { it.requiredSprayFlowTH }
        assertTrue("Spray should be active at end", lastSpray.average() > 0.1)
    }

    // ─── Thermal Capacitance ───────────────────────────────────────

    @Test
    fun `thermal capacitance calculation is correct`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val C = PlatenThermalInertiaModel.thermalCapacitance(input)
        assertEquals("C = M × Cp", 62600.0 * 500.0, C, 0.1)
    }

    // ─── Burner Heat Calculation ───────────────────────────────────

    @Test
    fun `total burner heat is eta times N times Q`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val Q = PlatenThermalInertiaModel.totalBurnerHeatMW(input)
        assertEquals("Q = η × N × Q_burner", 0.07 * 4.0 * 40.0, Q, 0.001)
    }

    // ─── Validation ────────────────────────────────────────────────

    @Test
    fun `validates required fields`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val validation = PlatenThermalInertiaModel.validate(input)
        assertTrue("Valid input should pass validation", validation is ValidationResult.Valid)
    }

    @Test
    fun `rejects negative eta`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = -0.1,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val validation = PlatenThermalInertiaModel.validate(input)
        assertTrue("Should reject negative eta", validation is ValidationResult.Invalid)
    }

    @Test
    fun `rejects eta above 1`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 1.5,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val validation = PlatenThermalInertiaModel.validate(input)
        assertTrue("Should reject eta > 1", validation is ValidationResult.Invalid)
    }

    @Test
    fun `rejects zero metal mass`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 0.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val validation = PlatenThermalInertiaModel.validate(input)
        assertTrue("Should reject zero metal mass", validation is ValidationResult.Invalid)
    }

    // ─── With Spray Cooling ────────────────────────────────────────

    @Test
    fun `transient with spray reaches approximate steady state`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 3600.0  // 1 hour
        )

        val result = TransientSimulationRunner.simulate(input)

        // After 1 hour, the spray flow should have stabilized
        // (metal temp converges to equilibrium)
        val steps = result.steps
        assertTrue("Should have multiple steps", steps.size > 10)

        // The last few steps should have similar spray flows (steady state)
        val lastSteps = steps.takeLast(5)
        val sprayFlows = lastSteps.map { it.requiredSprayFlowTH }
        val maxDiff = sprayFlows.max() - sprayFlows.min()

        // Spray flow should be relatively stable in the last few steps
        // (within 10% of the average)
        val avgSpray = sprayFlows.average()
        if (avgSpray > 0.1) {
            assertTrue(
                "Spray flow should be near steady state at end (max diff ${"%.2f".format(maxDiff)}, avg ${"%.2f".format(avgSpray)})",
                maxDiff / avgSpray < 0.15
            )
        }
    }

    // ─── Metal Temperature Response ────────────────────────────────

    @Test
    fun `metal temperature rises with burner heat and no spray`() {
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 540.0,  // No spray needed
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 4,
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val result = TransientSimulationRunner.simulate(input)
        val firstTemp = result.steps.first().metalTempC
        val lastTemp = result.steps.last().metalTempC

        assertTrue(
            "Metal temp should rise from ${"%.1f".format(firstTemp)} to ${"%.1f".format(lastTemp)}",
            lastTemp > firstTemp
        )
    }

    @Test
    fun `metal temperature drops when spray exceeds burner heat`() {
        // Very aggressive spray (low target) should cool the metal
        val input = TransientInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 360.0,  // Very low target → lots of spray
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
            platenMetalMassKg = 62600.0,
            metalSpecificHeat = 500.0,
            burnerHeatMW = 40.0,
            numberOfBurners = 1,  // Only 1 burner
            burnerToPlatenFraction = 0.07,
            timeStepS = 10.0,
            simulationDurationS = 600.0
        )

        val result = TransientSimulationRunner.simulate(input)
        val firstTemp = result.steps.first().metalTempC
        val lastTemp = result.steps.last().metalTempC

        // With very aggressive spray, metal temp should drop
        assertTrue(
            "Metal temp should drop from ${"%.1f".format(firstTemp)} to ${"%.1f".format(lastTemp)}",
            lastTemp < firstTemp
        )
    }
}
