package com.steamcalc.if97

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IAPWS-IF97 Region 2 (superheated steam).
 *
 * Reference values from IAPWS-IF97 Table 2 (verification data).
 * All enthalpy values in kJ/kg.
 */
class IF97Region2Test {

    private val TOLERANCE = 0.1  // kJ/kg tolerance for enthalpy

    /**
     * Verification point 1: P = 0.0035 MPa, T = 300 K
     * h = 2549.91145 kJ/kg (from IAPWS-IF97 Table 2)
     */
    @Test
    fun testRegion2_P0035MPa_T300K() {
        val h = IF97Region2.enthalpy(0.0035, 300.0)
        assertEquals(2549.91145, h, TOLERANCE)
    }

    /**
     * Verification point 2: P = 0.0035 MPa, T = 700 K
     * h = 3335.68375 kJ/kg (from IAPWS-IF97 Table 2)
     */
    @Test
    fun testRegion2_P0035MPa_T700K() {
        val h = IF97Region2.enthalpy(0.0035, 700.0)
        assertEquals(3335.68375, h, TOLERANCE)
    }

    /**
     * Verification point 3: P = 30 MPa, T = 700 K
     * h = 2631.49474 kJ/kg (from IAPWS-IF97 Table 2)
     */
    @Test
    fun testRegion2_P30MPa_T700K() {
        val h = IF97Region2.enthalpy(30.0, 700.0)
        assertEquals(2631.49474, h, TOLERANCE)
    }

    /**
     * Steam inlet conditions for test case 1:
     * P = 16.7 MPa, T = 813.15 K (540°C)
     * Expected h ≈ 3393 kJ/kg
     */
    @Test
    fun testRegion2_steamInletConditions() {
        val h = IF97Region2.enthalpy(16.7, 813.15)
        assertTrue("Enthalpy should be around 3393 kJ/kg, got $h", h > 3350.0 && h < 3450.0)
    }

    /**
     * Steam inlet conditions for test case 2:
     * P = 4.0 MPa, T = 623.15 K (350°C)
     * Expected h ≈ 3093 kJ/kg
     */
    @Test
    fun testRegion2_lowPressureSteam() {
        val h = IF97Region2.enthalpy(4.0, 623.15)
        assertTrue("Enthalpy should be around 3093 kJ/kg, got $h", h > 3050.0 && h < 3130.0)
    }

    /**
     * Region 2 validation check:
     * P = 17 MPa, T = 473.15 K (200°C) should NOT be valid (below saturation)
     */
    @Test
    fun testRegion2_invalidInputBelowSaturation() {
        assertFalse("200°C at 170 bar should be outside Region 2",
            IF97Region2.isValid(17.0, 473.15))
    }
}
