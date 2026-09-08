package com.platen4.superheater.engine

/**
 * Burner / furnace heat-input model (spec §8).
 *
 * Q_platen = Q_furnace * F_platen, with Q_furnace = burnersFiring * ratingMW * firingFraction.
 * Burner start/stop events ramp over [rampSeconds] instead of stepping instantaneously.
 */
class BurnerModel(
    private val burnerRatingMW: Double = Unit4Plant.BURNER_RATING_MW,
    private val platenFraction: Double = 0.5,
    private val rampSeconds: Double = 60.0,
) {
    /** Q into the platen [W] at simulation time [tSeconds]. */
    fun platenHeatW(burnersFiring: Int, firingFraction: Double, tSeconds: Double): Double {
        val target = burnersFiring * burnerRatingMW * firingFraction * platenFraction * 1e6
        // Ramp: linear from 0 at t=0 to target over rampSeconds (only for the initial start)
        val ramp = if (tSeconds >= rampSeconds) 1.0 else tSeconds / rampSeconds
        return target * ramp.coerceIn(0.0, 1.0)
    }
}

/**
 * Spray-water model (spec §9, §10). Spray properties come from IF97 Region 1.
 * The four distinct states are kept visible at all times:
 *   1. upstream steam (pre-spray)
 *   2. spray water
 *   3. mixed state (post-spray, platen inlet)
 *   4. platen outlet (post heat transfer)
 *
 * Mixing: h_mix = (m_steam*h_steam + m_spray*h_spray) / (m_steam + m_spray),
 * then T from the Region 2 backward equation T(P,h) (spec §4).
 */
class SprayModel(private val props: SteamProperties) {

    companion object {
        /** Plant rule: spray water temperature is always below 250 °C. */
        const val MAX_SPRAY_TEMP_K = 523.15 // 250 °C
    }

    data class MixingResult(
        val steamFlowKgs: Double,
        val sprayFlowKgs: Double,
        val steamEnthalpyJkg: Double,
        val sprayEnthalpyJkg: Double,
        val mixedEnthalpyJkg: Double,
        val mixedTemperatureK: Double,
        val mixedDensityKgM3: Double,
        val mixedCpJkgK: Double,
        val mixedViscosityPaS: Double,
        val mixedConductivityWmK: Double,
        val mixedPrandtl: Double,
        /** Q removed from steam by spray [W]: m_spray * (h_steam - h_mix). */
        val sprayCoolingW: Double,
        val pressurePa: Double,
    )

    /**
     * @param steamFlowKgs steam flow before spray [kg/s]
     * @param sprayFlowKgs spray water flow [kg/s] (0 = no spray)
     * @param steamPressurePa pressure at the mixing point [Pa]
     * @param steamTemperatureK steam temperature before spray [K]
     * @param sprayTemperatureK spray water temperature [K]
     */
    fun mix(
        steamFlowKgs: Double,
        sprayFlowKgs: Double,
        steamPressurePa: Double,
        steamTemperatureK: Double,
        sprayTemperatureK: Double,
    ): MixingResult {
        require(steamFlowKgs > 0.0) { "Steam flow must be positive" }
        require(sprayFlowKgs >= 0.0) { "Spray flow must be non-negative" }

        // Plant rule: spray water is always subcooled and below 250 °C.
        // Enforce both conditions explicitly with engineering-readable errors (spec §20).
        if (sprayFlowKgs > 0.0) {
            val tSat = props.saturationTemperatureP(steamPressurePa)
            if (sprayTemperatureK >= SprayModel.MAX_SPRAY_TEMP_K) {
                throw SteamPropertyException(
                    "Spray temperature " + "%.1f".format(sprayTemperatureK - 273.15) +
                        " °C exceeds the 250 °C plant limit",
                    pressurePa = steamPressurePa,
                    temperatureK = sprayTemperatureK,
                )
            }
            if (sprayTemperatureK >= tSat) {
                throw SteamPropertyException(
                    "Spray water must be subcooled: T=" + "%.1f".format(sprayTemperatureK - 273.15) +
                        " °C >= Tsat=" + "%.1f".format(tSat - 273.15) + " °C at " +
                        "%.2f".format(steamPressurePa / 1e5) + " bar",
                    pressurePa = steamPressurePa,
                    temperatureK = sprayTemperatureK,
                )
            }
        }

        val hSteam = props.enthalpyPT(steamPressurePa, steamTemperatureK)
        val hSpray = props.enthalpyPT(steamPressurePa, sprayTemperatureK) // Region 1 (subcooled)

        val total = steamFlowKgs + sprayFlowKgs
        val hMix = (steamFlowKgs * hSteam + sprayFlowKgs * hSpray) / total
        val tMix = props.temperaturePH(steamPressurePa, hMix) // Region 2 backward

        val coolingW = if (sprayFlowKgs > 0.0) sprayFlowKgs * (hSteam - hMix) else 0.0

        return MixingResult(
            steamFlowKgs = steamFlowKgs,
            sprayFlowKgs = sprayFlowKgs,
            steamEnthalpyJkg = hSteam,
            sprayEnthalpyJkg = hSpray,
            mixedEnthalpyJkg = hMix,
            mixedTemperatureK = tMix,
            mixedDensityKgM3 = props.densityPT(steamPressurePa, tMix),
            mixedCpJkgK = props.cpPT(steamPressurePa, tMix),
            mixedViscosityPaS = props.viscosityPT(steamPressurePa, tMix),
            mixedConductivityWmK = props.conductivityPT(steamPressurePa, tMix),
            mixedPrandtl = props.prandtlPT(steamPressurePa, tMix),
            sprayCoolingW = coolingW,
            pressurePa = steamPressurePa,
        )
    }
}

/**
 * Time-stamped scenario events (spec §15). Events are applied in time order;
 * values are absolute targets (not deltas) unless [relative] is set.
 */
data class ScenarioEvent(
    val timeSeconds: Double,
    val kind: Kind,
    val value: Double,
    val relative: Boolean = false,
) {
    enum class Kind { STEAM_FLOW_KGS, SPRAY_FLOW_KGS, SPRAY_TEMP_K, BURNERS_FIRING, FIRING_FRACTION, STEAM_PRESSURE_PA, STEAM_TEMP_K }
}

/** Holds the time-varying simulation inputs, driven by a list of [ScenarioEvent]s. */
class ScenarioState(events: List<ScenarioEvent> = emptyList()) {

    private val sorted = events.sortedBy { it.timeSeconds }

    var steamFlowKgs: Double = 100.0 / 3.6 // 100 t/h default
        private set
    var sprayFlowKgs: Double = 0.0
        private set
    var burnersFiring: Int = 2
        private set
    var firingFraction: Double = 1.0
        private set
    var pressurePa: Double = 100.0 * 1e5
        private set
    var steamTempK: Double = 673.15 // 400 C
        private set
    var sprayTempK: Double = 503.15 // 230 C — always below the 250 C plant limit
        private set

    /** Apply all events with time <= [tSeconds], in order. */
    fun update(tSeconds: Double) {
        for (e in sorted) {
            if (e.timeSeconds > tSeconds) break
            when (e.kind) {
                ScenarioEvent.Kind.STEAM_FLOW_KGS -> steamFlowKgs = if (e.relative) steamFlowKgs + e.value else e.value
                ScenarioEvent.Kind.SPRAY_FLOW_KGS -> sprayFlowKgs = if (e.relative) sprayFlowKgs + e.value else e.value
                ScenarioEvent.Kind.SPRAY_TEMP_K -> {
                    sprayTempK = if (e.relative) sprayTempK + e.value else e.value
                    require(sprayTempK < SprayModel.MAX_SPRAY_TEMP_K) {
                        "Spray temperature must stay below 250 °C (got " + "%.1f".format(sprayTempK - 273.15) + " °C)"
                    }
                }
                ScenarioEvent.Kind.BURNERS_FIRING -> burnersFiring = if (e.relative) burnersFiring + e.value.toInt() else e.value.toInt()
                ScenarioEvent.Kind.FIRING_FRACTION -> firingFraction = if (e.relative) firingFraction + e.value else e.value
                ScenarioEvent.Kind.STEAM_PRESSURE_PA -> pressurePa = if (e.relative) pressurePa + e.value else e.value
                ScenarioEvent.Kind.STEAM_TEMP_K -> steamTempK = if (e.relative) steamTempK + e.value else e.value
            }
        }
    }

    fun events(): List<ScenarioEvent> = sorted
}