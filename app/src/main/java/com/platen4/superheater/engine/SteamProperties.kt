package com.platen4.superheater.engine

/**
 * Typed error for invalid thermodynamic states / out-of-range property calls (spec §20).
 * Carries the offending state so the UI can show an engineering-readable explanation.
 */
class SteamPropertyException(
    message: String,
    val pressurePa: Double? = null,
    val temperatureK: Double? = null,
) : Exception(message)

/**
 * Thermodynamic property facade — **pure SI units** everywhere:
 * P [Pa], T [K], h [J/kg], rho [kg/m3], cp [J/kg/K], mu [Pa.s], k [W/m/K], Pr [-].
 *
 * Implemented on the vendored IAPWS-IF97 library (com.hummeling.if97, LGPL, v2.1.0 source).
 * The library's DEFAULT unit system is MPa / K / kJ/kg / kg/m3 / kJ/kg/K / Pa.s / W/m/K;
 * the facade converts to SI so the rest of the engine never sees non-SI units.
 *
 * The interface exists so a different property engine (e.g. CoolProp) can be swapped in
 * without touching the rest of the codebase (spec §18 last note).
 */
interface SteamProperties {

    /** Specific enthalpy h(P,T) [J/kg]. Region 1 (subcooled) or Region 2 (superheated). */
    fun enthalpyPT(pPa: Double, tK: Double): Double

    /** Temperature T(P,h) [K] — Region 2 backward equation (needed for spray mixing, spec §4). */
    fun temperaturePH(pPa: Double, hJkg: Double): Double

    /** Saturation temperature [K] at pressure [Pa] (Region 4). */
    fun saturationTemperatureP(pPa: Double): Double

    /** Density [kg/m3]. */
    fun densityPT(pPa: Double, tK: Double): Double

    /** Isobaric heat capacity [J/kg/K]. */
    fun cpPT(pPa: Double, tK: Double): Double

    /** Dynamic viscosity [Pa.s]. */
    fun viscosityPT(pPa: Double, tK: Double): Double

    /** Thermal conductivity [W/m/K]. */
    fun conductivityPT(pPa: Double, tK: Double): Double

    /** Prandtl number [-]. */
    fun prandtlPT(pPa: Double, tK: Double): Double

    /** IF97 region number (1..5) at (P,T). */
    fun regionPT(pPa: Double, tK: Double): Int
}