package com.steamcalc.if97

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IAPWS-IF97 saturation temperature and pressure equations.
 *
 * Reference values from IAPWS-IF97 and standard steam tables.
 */
class IF97SaturationTest {

    /**
     * Saturation temperature at P = 10 MPa (100 bar).
     * Expected: T_sat ≈ 584.15 K (311.0°C)
     */
    @Test
    fun testSaturationTemp_10MPa() {
        val tSat = IF97Common.saturationTemperature(10.0)
        assertEquals(584.15, tSat, 0.5)
    }

    /**
     * Saturation temperature at P = 16.529 MPa (boundary Region 1-3)
     * Expected: T_sat = 623.15 K
     */
    @Test
    fun testSaturationTemp_Boundary() {
        val tSat = IF97Common.saturationTemperature(16.52916425)
        assertEquals(623.15, tSat, 0.01)
    }

    /**
     * Saturation pressure at T = 500 K.
     * Expected: P_sat ≈ 2.63889776 MPa (from IAPWS-IF97)
     */
    @Test
    fun testSaturationPressure_500K() {
        val pSat = IF97Common.saturationPressure(500.0)
        assertEquals(2.63889776, pSat, 0.001)
    }

    /**
     * Saturation temperature at P = 40 bar (4 MPa).
     * Expected: T_sat ≈ 523.55 K (250.4°C)
     */
    @Test
    fun testSaturationTemp_4MPa() {
        val tSat = IF97Common.saturationTemperature(4.0)
        assertEquals(523.55, tSat, 0.5)
    }

    /**
     * Saturation temperature at P = 167 bar (16.7 MPa).
     * Expected: T_sat ≈ 624.5 K (351.4°C)
     */
    @Test
    fun testSaturationTemp_167Bar() {
        val tSat = IF97Common.saturationTemperature(16.7)
        assertEquals(624.5, tSat, 1.0)
    }
}
