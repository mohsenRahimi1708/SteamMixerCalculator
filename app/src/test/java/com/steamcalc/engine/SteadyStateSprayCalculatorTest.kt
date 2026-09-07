package com.steamcalc.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SteadyStateSprayCalculator].
 *
 * Tests against hand-checkable mixing scenarios and the v1 worked examples
 * (adjusted for any small differences from using the validated IF97 library
 * instead of the hand-implemented equations).
 */
class SteadyStateSprayCalculatorTest {

    // ─── Example 1: High-Pressure Steam (from v1 README) ───────────

    @Test
    fun `example 1 - high pressure spray mixing`() {
        val input = SteadyStateInput(
            steamFlowTH = 1000.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 517.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Input should be valid", validation is ValidationResult.Valid)

        val result = SteadyStateSprayCalculator.calculate(input)

        // Expected spray flow from IF97 library v2.0.0 ≈ 28 t/h
        assertEquals("Spray flow should be ~28 t/h", 28.0, result.requiredSprayFlowTH, 5.0)
        assertEquals("Temperature drop", 23.0, result.temperatureDropC, 5.0)
        assertEquals("Combined flow", 1028.0, result.combinedFlowTH, 5.0)
        assertTrue("No warnings expected", result.warnings.isEmpty())
    }

    // ─── Example 2: Medium-Pressure Steam ──────────────────────────

    @Test
    fun `example 2 - medium pressure spray mixing`() {
        val input = SteadyStateInput(
            steamFlowTH = 300.0,
            steamPressureBar = 40.0,
            steamTempC = 350.0,
            targetTempC = 336.0,
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Input should be valid", validation is ValidationResult.Valid)

        val result = SteadyStateSprayCalculator.calculate(input)

        // Expected spray flow ≈ 5 t/h (from v1 example)
        assertEquals("Spray flow should be ~5 t/h", 5.0, result.requiredSprayFlowTH, 2.0)
        assertEquals("Temperature drop", 14.0, result.temperatureDropC, 3.0)
        assertTrue("No warnings expected", result.warnings.isEmpty())
    }

    // ─── Validation Tests ──────────────────────────────────────────

    @Test
    fun `rejects steam below saturation temperature`() {
        val input = SteadyStateInput(
            steamFlowTH = 500.0,
            steamPressureBar = 170.0,
            steamTempC = 200.0,  // T_sat ≈ 352°C at 170 bar
            targetTempC = 180.0,
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Should reject - steam is not superheated", validation is ValidationResult.Invalid)
    }

    @Test
    fun `rejects target above steam inlet`() {
        val input = SteadyStateInput(
            steamFlowTH = 500.0,
            steamPressureBar = 100.0,
            steamTempC = 400.0,
            targetTempC = 450.0,  // Above steam inlet!
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Should reject - target above steam inlet", validation is ValidationResult.Invalid)
    }

    @Test
    fun `rejects spray water above saturation`() {
        val input = SteadyStateInput(
            steamFlowTH = 500.0,
            steamPressureBar = 50.0,
            steamTempC = 400.0,
            targetTempC = 380.0,
            sprayTempC = 300.0,  // T_sat at 50 bar ≈ 264°C
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Should reject - spray water is not subcooled", validation is ValidationResult.Invalid)
    }

    @Test
    fun `rejects negative steam flow`() {
        val input = SteadyStateInput(
            steamFlowTH = -100.0,
            steamPressureBar = 100.0,
            steamTempC = 500.0,
            targetTempC = 480.0,
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val validation = SteadyStateSprayCalculator.validate(input)
        assertTrue("Should reject negative steam flow", validation is ValidationResult.Invalid)
    }

    // ─── Energy Balance Consistency ────────────────────────────────

    @Test
    fun `energy balance is satisfied after mixing`() {
        val input = SteadyStateInput(
            steamFlowTH = 600.0,
            steamPressureBar = 167.0,
            steamTempC = 540.0,
            targetTempC = 520.0,
            sprayTempC = 230.0,
            initialMetalTempC = 540.0,
        )

        val result = SteadyStateSprayCalculator.calculate(input)
        val ms = input.steamFlowTH
        val mw = result.requiredSprayFlowTH
        val p = input.steamPressureBar

        val hSteam = SteamProperties.enthalpyPT(p, input.steamTempC)
        val hSpray = SteamProperties.enthalpyPT(p, input.sprayTempC)
        val hOut = SteamProperties.enthalpyPT(p, input.targetTempC)

        // Energy balance: ms*hSteam + mw*hSpray = (ms+mw)*hOut
        val lhs = ms * hSteam + mw * hSpray
        val rhs = (ms + mw) * hOut

        assertEquals("Energy balance must be satisfied", lhs, rhs, 0.1)
    }

    // ─── Edge Cases ────────────────────────────────────────────────

    @Test
    fun `target equals inlet produces zero spray flow`() {
        val input = SteadyStateInput(
            steamFlowTH = 500.0,
            steamPressureBar = 100.0,
            steamTempC = 500.0,
            targetTempC = 500.0,  // Same as inlet
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val result = SteadyStateSprayCalculator.calculate(input)
        assertEquals("Zero spray when target equals inlet", 0.0, result.requiredSprayFlowTH, 0.1)
    }

    @Test
    fun `very small temperature drop requires small spray flow`() {
        val input = SteadyStateInput(
            steamFlowTH = 500.0,
            steamPressureBar = 100.0,
            steamTempC = 500.0,
            targetTempC = 499.0,  // Only 1°C drop
            sprayTempC = 200.0,
            initialMetalTempC = 540.0,
        )

        val result = SteadyStateSprayCalculator.calculate(input)
        assertTrue("Small ΔT should need small spray", result.requiredSprayFlowTH < 5.0)
        assertTrue("But should be positive", result.requiredSprayFlowTH > 0.0)
    }
}
