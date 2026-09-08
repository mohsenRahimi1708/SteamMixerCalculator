package com.platen4.superheater.engine

import com.hummeling.if97.IF97
import com.hummeling.if97.OutOfRangeException

/**
 * IAPWS-IF97 implementation of [SteamProperties] on the vendored Hummeling library.
 *
 * The library's DEFAULT unit system is MPa / K / kJ/kg / kg/m3 / kJ/kg/K / Pa.s / W/m/K.
 * All conversions to SI happen here, once.
 */
class If97SteamProperties : SteamProperties {

    private val if97 = IF97() // DEFAULT unit system

    private fun mpa(pPa: Double): Double = pPa / 1e6

    override fun enthalpyPT(pPa: Double, tK: Double): Double =
        runCatching { if97.specificEnthalpyPT(mpa(pPa), tK) * 1e3 }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun temperaturePH(pPa: Double, hJkg: Double): Double =
        runCatching { if97.temperaturePH(mpa(pPa), hJkg / 1e3) }
            .getOrElse { rethrow(it, pPa, null) }

    override fun saturationTemperatureP(pPa: Double): Double =
        runCatching { if97.saturationTemperatureP(mpa(pPa)) }
            .getOrElse { rethrow(it, pPa, null) }

    override fun densityPT(pPa: Double, tK: Double): Double =
        runCatching { if97.densityPT(mpa(pPa), tK) }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun cpPT(pPa: Double, tK: Double): Double =
        runCatching { if97.isobaricHeatCapacityPT(mpa(pPa), tK) * 1e3 }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun viscosityPT(pPa: Double, tK: Double): Double =
        runCatching { if97.dynamicViscosityPT(mpa(pPa), tK) }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun conductivityPT(pPa: Double, tK: Double): Double =
        runCatching { if97.thermalConductivityPT(mpa(pPa), tK) }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun prandtlPT(pPa: Double, tK: Double): Double =
        runCatching { if97.PrandtlPT(mpa(pPa), tK) }
            .getOrElse { rethrow(it, pPa, tK) }

    override fun regionPT(pPa: Double, tK: Double): Int =
        runCatching { if97.getRegionPT(mpa(pPa), tK).substringAfterLast("Region").trim().toIntOrNull() ?: -1 }
            .getOrElse { rethrow(it, pPa, tK) }

    private fun rethrow(e: Throwable, pPa: Double?, tK: Double?): Nothing {
        if (e is OutOfRangeException) {
            throw SteamPropertyException(
                "IF97 state out of range: ${e.message}",
                pressurePa = pPa,
                temperatureK = tK,
            )
        }
        throw e
    }
}