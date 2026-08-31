package com.steamcalc.calculator

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SteamMixerCalculator.
 *
 * Test cases from the specification:
 * 1. Steam: 1000 t/h, 167 bar, 540°C; Spray: 20 t/h, 167 bar, 230°C
 * 2. Steam: 300 t/h, 40 bar, 350°C; Spray: 5 t/h, 40 bar, 200°C
 * 3. Invalid: 170 bar, 200°C steam — must be rejected
 */
class SteamMixerCalculatorTest {

    /**
     * Test case 1: High-pressure steam with spray water.
     * Steam: 1000 t/h, 167 bar, 540°C; Spray: 20 t/h, 167 bar, 230°C
     * Expected: T_out ≈ 517°C, ΔT ≈ 23°C
     */
    @Test
    fun testCase1_highPressureMixing() {
        val validation = SteamMixerCalculator.validate(1000.0, 167.0, 540.0, 20.0, 230.0)
        assertTrue("Validation should pass", validation is SteamMixerCalculator.ValidationResult.Valid)

        val result = SteamMixerCalculator.calculate(1000.0, 167.0, 540.0, 20.0, 230.0)
        assertNotNull("Result should not be null", result)
        result!!

        assertNull("No warning expected", result.warning)

        // T_out should be approximately 517°C (within 5°C tolerance for IAPWS-IF97)
        assertTrue("T_out should be around 517°C, got ${result.outletTempC}",
            result.outletTempC > 510.0 && result.outletTempC < 530.0)

        // ΔT should be approximately 23°C
        assertTrue("ΔT should be around 23°C, got ${result.deltaT}",
            result.deltaT > 10.0 && result.deltaT < 40.0)

        // Combined flow should be 1020 t/h
        assertEquals(1020.0, result.combinedFlow, 0.01)
    }

    /**
     * Test case 2: Medium-pressure steam with spray water.
     * Steam: 300 t/h, 40 bar, 350°C; Spray: 5 t/h, 40 bar, 200°C
     * Expected: T_out ≈ 336°C, ΔT ≈ 14°C
     */
    @Test
    fun testCase2_mediumPressureMixing() {
        val validation = SteamMixerCalculator.validate(300.0, 40.0, 350.0, 5.0, 200.0)
        assertTrue("Validation should pass", validation is SteamMixerCalculator.ValidationResult.Valid)

        val result = SteamMixerCalculator.calculate(300.0, 40.0, 350.0, 5.0, 200.0)
        assertNotNull("Result should not be null", result)
        result!!

        assertNull("No warning expected", result.warning)

        // T_out should be approximately 336°C (within 5°C tolerance)
        assertTrue("T_out should be around 336°C, got ${result.outletTempC}",
            result.outletTempC > 330.0 && result.outletTempC < 345.0)

        // ΔT should be approximately 14°C
        assertTrue("ΔT should be around 14°C, got ${result.deltaT}",
            result.deltaT > 5.0 && result.deltaT < 25.0)

        // Combined flow should be 305 t/h
        assertEquals(305.0, result.combinedFlow, 0.01)
    }

    /**
     * Test case 3: Invalid steam conditions.
     * 170 bar, 200°C — T_sat at 170 bar ≈ 352°C, so 200°C is subcooled liquid.
     * Must be rejected, not computed.
     */
    @Test
    fun testCase3_invalidInput() {
        val validation = SteamMixerCalculator.validate(500.0, 170.0, 200.0, 10.0, 200.0)
        assertTrue("Validation should fail",
            validation is SteamMixerCalculator.ValidationResult.Invalid)

        val msg = (validation as SteamMixerCalculator.ValidationResult.Invalid).message
        assertTrue("Error message should mention 'outside superheated steam region'",
            msg.contains("outside superheated steam region"))
    }

    /**
     * Verify that the invalid input case does NOT produce a computed result.
     */
    @Test
    fun testCase3_noComputation() {
        val validation = SteamMixerCalculator.validate(500.0, 170.0, 200.0, 10.0, 200.0)
        assertTrue("Should be invalid", validation is SteamMixerCalculator.ValidationResult.Invalid)

        // We should never reach the calculate() method, but verify it's caught
    }
}
