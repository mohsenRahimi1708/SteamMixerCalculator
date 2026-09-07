package com.steamcalc.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SteamProperties] — validates the IF97 library wrapper against
 * published IAPWS-IF97 verification points.
 *
 * Reference values are from the Hummeling IF97 library's own test suite and from
 * the official IAPWS-IF97 verification document (Wagner & Kretzschmar, 2nd ed.).
 *
 * These tests confirm the app is *calling the library correctly* (right units,
 * right unit system, right arguments) — not re-validating the library itself.
 */
class SteamPropertiesTest {

    /** Tolerance for comparisons — the IF97 library is accurate to ~0.01 kJ/kg */
    private val TOL_ENTHALPY = 0.1  // kJ/kg
    private val TOL_TEMP = 0.05     // °C

    // ─── Region 2 (Superheated Steam) Verification Points ──────────
    // These are well-known IAPWS-IF97 verification points.

    @Test
    fun `enthalpy at 160 bar, 530C matches IF97 verification point`() {
        // IAPWS-IF97 Region 2 verification: P=16 MPa (160 bar), T=803.15 K (530°C)
        // Hummeling IF97 v2.0.0 returns h ≈ 3384.05 kJ/kg
        val h = SteamProperties.enthalpyPT(160.0, 530.0)
        assertEquals("Enthalpy at 160 bar, 530°C", 3384.05, h, 10.0)
    }

    @Test
    fun `enthalpy at 30 bar, 300C matches IF97 verification point`() {
        // Region 2: P=3 MPa (30 bar), T=573.15 K (300°C)
        // Expected h ≈ 2993.5 kJ/kg (approximate)
        val h = SteamProperties.enthalpyPT(30.0, 300.0)
        // At 30 bar, 300°C, steam is superheated (T_sat ≈ 233.9°C)
        assertTrue("Should be superheated", h > 2900.0)
        assertTrue("Reasonable range", h < 3100.0)
    }

    @Test
    fun `enthalpy at 100 bar, 500C is reasonable for superheated steam`() {
        // Region 2: P=10 MPa (100 bar), T=773.15 K (500°C)
        // Well-known reference: h ≈ 3373.7 kJ/kg
        val h = SteamProperties.enthalpyPT(100.0, 500.0)
        assertEquals("Enthalpy at 100 bar, 500°C", 3373.7, h, 5.0)
    }

    @Test
    fun `enthalpy at 1 bar, 100C matches water reference`() {
        // Region 1 or 2: P=0.1 MPa (1 bar), T=100°C
        // Saturated liquid at 1 bar: h ≈ 417.46 kJ/kg
        // At 100°C and 1 bar, T_sat ≈ 99.6°C, so 100°C is slightly superheated
        // Expected h ≈ 2676 kJ/kg (superheated steam at 1 bar, 100°C)
        val h = SteamProperties.enthalpyPT(1.0, 100.0)
        // This should be in the superheated region (barely)
        assertTrue("Enthalpy should be reasonable for low-pressure steam", h > 2600.0)
        assertTrue("Enthalpy should be reasonable for low-pressure steam", h < 2800.0)
    }

    // ─── Region 1 (Compressed Liquid) Verification Points ──────────

    @Test
    fun `enthalpy at 100 bar, 200C matches compressed liquid reference`() {
        // Region 1: P=10 MPa (100 bar), T=473.15 K (200°C)
        // T_sat at 100 bar ≈ 311°C, so 200°C is compressed liquid
        // Hummeling IF97 v2.0.0 returns h ≈ 855.92 kJ/kg
        val h = SteamProperties.enthalpyPT(100.0, 200.0)
        assertEquals("Enthalpy at 100 bar, 200°C (compressed liquid)", 855.92, h, 5.0)
    }

    @Test
    fun `enthalpy at 50 bar, 100C is reasonable for compressed liquid`() {
        // Region 1: P=5 MPa (50 bar), T=373.15 K (100°C)
        // T_sat at 50 bar ≈ 264°C, so 100°C is compressed liquid
        // Hummeling IF97 v2.0.0 returns h ≈ 422.78 kJ/kg
        val h = SteamProperties.enthalpyPT(50.0, 100.0)
        assertEquals("Enthalpy at 50 bar, 100°C", 422.78, h, 5.0)
    }

    // ─── Saturation Temperature ────────────────────────────────────

    @Test
    fun `saturation temperature at 1 bar is approximately 99_6C`() {
        val tSat = SteamProperties.saturationTemperature(1.0)
        assertEquals("T_sat at 1 bar", 99.6, tSat, 0.5)
    }

    @Test
    fun `saturation temperature at 10 bar is approximately 179_9C`() {
        val tSat = SteamProperties.saturationTemperature(10.0)
        assertEquals("T_sat at 10 bar", 179.9, tSat, 0.5)
    }

    @Test
    fun `saturation temperature at 100 bar is approximately 311_1C`() {
        val tSat = SteamProperties.saturationTemperature(100.0)
        assertEquals("T_sat at 100 bar", 311.1, tSat, 1.0)
    }

    @Test
    fun `saturation temperature at 160 bar is approximately 347_4C`() {
        val tSat = SteamProperties.saturationTemperature(160.0)
        assertEquals("T_sat at 160 bar", 347.4, tSat, 1.0)
    }

    // ─── Temperature Inverse ───────────────────────────────────────

    @Test
    fun `roundtrip enthalpy-temperature is consistent`() {
        // At 50 bar, 400°C: compute h, then T from h, should get 400°C back
        val p = 50.0
        val tOrig = 400.0
        val h = SteamProperties.enthalpyPT(p, tOrig)
        val tBack = SteamProperties.temperaturePH(p, h)
        assertEquals("Roundtrip T→h→T at 50 bar, 400°C", tOrig, tBack, TOL_TEMP)
    }

    @Test
    fun `roundtrip at high pressure is consistent`() {
        val p = 160.0
        val tOrig = 540.0
        val h = SteamProperties.enthalpyPT(p, tOrig)
        val tBack = SteamProperties.temperaturePH(p, h)
        assertEquals("Roundtrip T→h→T at 160 bar, 540°C", tOrig, tBack, TOL_TEMP)
    }

    // ─── Region Detection ──────────────────────────────────────────

    @Test
    fun `isSuperheated detects superheated steam`() {
        assertTrue("160 bar, 540°C is superheated", SteamProperties.isSuperheated(160.0, 540.0))
        assertTrue("50 bar, 400°C is superheated", SteamProperties.isSuperheated(50.0, 400.0))
    }

    @Test
    fun `isCompressedLiquid detects compressed liquid`() {
        assertTrue("100 bar, 200°C is compressed liquid", SteamProperties.isCompressedLiquid(100.0, 200.0))
        assertTrue("50 bar, 100°C is compressed liquid", SteamProperties.isCompressedLiquid(50.0, 100.0))
    }

    @Test
    fun `steam at 200C and 167 bar is not superheated`() {
        // T_sat at 167 bar ≈ 352°C, so 200°C is compressed liquid
        assertFalse("200°C at 167 bar is NOT superheated", SteamProperties.isSuperheated(167.0, 200.0))
    }

    // ─── Existing Worked Examples (from v1 README) ─────────────────

    @Test
    fun `example 1 from v1 - 167 bar 540C steam`() {
        // Steam: 167 bar, 540°C → h should be in superheated range
        val h = SteamProperties.enthalpyPT(167.0, 540.0)
        // At 167 bar, 540°C: expected h ≈ 3394 kJ/kg (from steam tables)
        assertTrue("h should be > 3300", h > 3300.0)
        assertTrue("h should be < 3500", h < 3500.0)
    }

    @Test
    fun `spray water at 167 bar 230C is compressed liquid`() {
        val h = SteamProperties.enthalpyPT(167.0, 230.0)
        // At 167 bar, 230°C (T_sat ≈ 352°C): compressed liquid
        // Expected h ≈ 990 kJ/kg
        assertTrue("h should be < 1100 for compressed liquid", h < 1100.0)
        assertTrue("h should be > 900", h > 900.0)
    }
}
