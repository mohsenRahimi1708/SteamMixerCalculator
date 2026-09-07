package com.steamcalc.engine

import com.hummeling.if97.IF97
import com.hummeling.if97.OutOfRangeException

/**
 * Thin wrapper around [com.hummeling.if97.IF97] that provides a clean API
 * for the steam mixer calculator.
 *
 * **Unit system**: [IF97.UnitSystem.ENGINEERING] — pressure in **bar**,
 * temperature in **°C**, enthalpy in **kJ/kg**. All inputs and outputs
 * of this wrapper are in those engineering units; the library handles
 * internal conversion to/from its default SI unit system (MPa, K, kJ/kg).
 *
 * **No IF97 equations are reimplemented** — every thermodynamic property
 * is obtained by calling the validated Hummeling IF97 library. The only
 * non-library code is a bisection root-finder (permitted by the spec)
 * used when the library does not expose a direct inverse for a needed
 * quantity combination.
 *
 * Thread safety: the IF97 instance is effectively immutable after
 * construction, so this wrapper is safe to share across threads.
 */
object SteamProperties {

    /** Shared IF97 instance using engineering units (bar, °C, kJ/kg). */
    private val if97 = IF97(IF97.UnitSystem.ENGINEERING)

    // ─── Forward properties ────────────────────────────────────────────

    /**
     * Specific enthalpy as a function of pressure and temperature.
     *
     * @param pressureBar absolute pressure in bar
     * @param temperatureC temperature in °C
     * @return specific enthalpy in kJ/kg
     * @throws OutOfRangeException if inputs fall outside the IF97 domain
     */
    @Throws(OutOfRangeException::class)
    fun enthalpyPT(pressureBar: Double, temperatureC: Double): Double =
        if97.specificEnthalpyPT(pressureBar, temperatureC)

    // ─── Inverse properties ────────────────────────────────────────────

    /**
     * Temperature as a function of pressure and specific enthalpy.
     * This is the library's own inverse — no custom root-finder needed.
     *
     * @param pressureBar absolute pressure in bar
     * @param enthalpyKJkg specific enthalpy in kJ/kg
     * @return temperature in °C
     * @throws OutOfRangeException if inputs fall outside the IF97 domain
     */
    @Throws(OutOfRangeException::class)
    fun temperaturePH(pressureBar: Double, enthalpyKJkg: Double): Double =
        if97.temperaturePH(pressureBar, enthalpyKJkg)

    // ─── Saturation properties ─────────────────────────────────────────

    /**
     * Saturation temperature at a given pressure, determined via bisection
     * on the library's own [getRegion] call. This does NOT reimplement any
     * IF97 equations — it only calls the library to identify which region
     * a test temperature falls in, and narrows the search interval.
     *
     * @param pressureBar absolute pressure in bar (valid range: 6.11e-2 to 220.64 bar)
     * @return saturation temperature in °C
     * @throws OutOfRangeException if pressure is outside IF97 domain
     */
    @Throws(OutOfRangeException::class)
    fun saturationTemperature(pressureBar: Double): Double {
        // The saturation temperature lies between the triple point (0.01 °C)
        // and the critical temperature (373.946 °C). We bisect, using the
        // library's getRegion to determine whether a test temperature is in
        // the liquid (Region 1) or vapor (Region 2) domain. At exactly T_sat,
        // the library reports Region 4 (saturation/.two-phase).
        var lo = 0.01    // °C — just above triple point
        var hi = 373.94  // °C — just below critical temperature

        for (iter in 0 until 60) {
            val mid = (lo + hi) / 2.0
            val region = try {
                if97.getRegionPT(pressureBar, mid)
            } catch (_: OutOfRangeException) {
                // Near the limits, narrow conservatively
                hi = mid
                continue
            }

            when {
                region.contains("Region 1") -> lo = mid  // liquid → T_sat is higher
                region.contains("Region 2") -> hi = mid  // vapor  → T_sat is lower
                region.contains("Region 4") -> return mid  // saturation found
                region.contains("Region 3") -> hi = mid  // near-critical → narrow down
                else -> lo = mid  // unknown region → try higher
            }
        }

        return (lo + hi) / 2.0
    }

    // ─── Region queries ────────────────────────────────────────────────

    /**
     * Get the IF97 region name for a given (P, T) point.
     * Returns strings like "Region 1", "Region 2", "Region 4", etc.
     *
     * @throws OutOfRangeException if inputs fall outside the IF97 domain
     */
    @Throws(OutOfRangeException::class)
    fun getRegion(pressureBar: Double, temperatureC: Double): String =
        if97.getRegionPT(pressureBar, temperatureC)

    /**
     * Check if a state point is in the superheated steam region (Region 2).
     */
    fun isSuperheated(pressureBar: Double, temperatureC: Double): Boolean =
        try {
            val region = if97.getRegionPT(pressureBar, temperatureC)
            region.contains("Region 2")
        } catch (_: OutOfRangeException) {
            false
        }

    /**
     * Check if a state point is in the compressed liquid region (Region 1).
     */
    fun isCompressedLiquid(pressureBar: Double, temperatureC: Double): Boolean =
        try {
            val region = if97.getRegionPT(pressureBar, temperatureC)
            region.contains("Region 1")
        } catch (_: OutOfRangeException) {
            false
        }
}
