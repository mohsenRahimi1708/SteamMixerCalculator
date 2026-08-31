package com.steamcalc.if97

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IAPWS-IF97 Region 1 (compressed liquid water).
 *
 * Reference values from IAPWS-IF97 Table 1 (verification data).
 * All enthalpy values in kJ/kg.
 */
class IF97Region1Test {

    private val TOLERANCE = 0.01  // kJ/kg tolerance

    /**
     * Verification point 1: P = 3 MPa, T = 300 K
     * h = 115.331273 kJ/kg (from IAPWS-IF97 Table 1)
     */
    @Test
    fun testRegion1_P3MPa_T300K() {
        val h = IF97Region1.enthalpy(3.0, 300.0)
        assertEquals(115.331273, h, TOLERANCE)
    }

    /**
     * Verification point 2: P = 80 MPa, T = 300 K
     * h = 184.142828 kJ/kg (from IAPWS-IF97 Table 1)
     * Note: at high pressure, Pv term contributes significantly to enthalpy.
     */
    @Test
    fun testRegion1_P80MPa_T300K() {
        val h = IF97Region1.enthalpy(80.0, 300.0)
        assertEquals(184.142828, h, TOLERANCE)
    }

    /**
     * Verification point 3: P = 3 MPa, T = 500 K
     * h = 975.541674 kJ/kg (from IAPWS-IF97 Table 1)
     */
    @Test
    fun testRegion1_P3MPa_T500K() {
        val h = IF97Region1.enthalpy(3.0, 500.0)
        assertEquals(975.541674, h, TOLERANCE)
    }

    /**
     * Additional verification: spray water conditions
     * P = 16.7 MPa (167 bar), T = 503.15 K (230°C)
     * Expected h ≈ 990 kJ/kg (from steam tables)
     */
    @Test
    fun testRegion1_sprayWaterConditions() {
        val h = IF97Region1.enthalpy(16.7, 503.15)
        // Verify it's in the expected range for compressed liquid
        assertTrue("Enthalpy should be around 990 kJ/kg, got $h", h > 900.0 && h < 1100.0)
    }

    /**
     * Additional verification: P = 4.0 MPa (40 bar), T = 473.15 K (200°C)
     * Expected h ≈ 852 kJ/kg (from steam tables)
     */
    @Test
    fun testRegion1_lowPressure() {
        val h = IF97Region1.enthalpy(4.0, 473.15)
        // From steam tables, h ≈ 852 kJ/kg
        assertTrue("Enthalpy should be around 852 kJ/kg, got $h", h > 840.0 && h < 870.0)
    }
}
