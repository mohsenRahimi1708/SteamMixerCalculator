package com.platen4.superheater.engine

import kotlin.math.PI

/**
 * Steam-side flow calculation (spec §5). The full chain is shown:
 * total flow -> active parallel paths -> per-path flow -> velocity.
 *
 * Flow-distribution assumption (spec §3.2, explicit): total platen steam flow splits
 * equally across [activePanels] panels; within a panel the 4 tubes share flow equally.
 * The header mid-point feed (panels 1-21 / 22-43) is modeled as two parallel groups
 * in the segment model; this class computes the single-tube velocity for a given
 * total flow and active panel count.
 */
object FlowModel {

    data class Result(
        val totalFlowKgs: Double,
        val activePanels: Int,
        val flowPerPanelKgs: Double,
        val flowPerTubeKgs: Double,
        val tubeVelocityMs: Double,
        val reynolds: Double,
    )

    /**
     * @param totalFlowKgs total platen steam mass flow [kg/s]
     * @param activePanels number of panels in service (1..43)
     * @param rho steam density [kg/m3]
     * @param mu dynamic viscosity [Pa.s]
     */
    fun compute(totalFlowKgs: Double, activePanels: Int, rho: Double, mu: Double): Result {
        require(totalFlowKgs > 0.0) { "Steam mass flow must be positive (got $totalFlowKgs kg/s)" }
        require(activePanels in 1..Unit4Plant.PANELS) { "activePanels must be 1..${Unit4Plant.PANELS}" }

        val flowPerPanel = totalFlowKgs / activePanels
        val flowPerTube = flowPerPanel / Unit4Plant.TUBES_PER_PANEL
        val area = Unit4Plant.tubeFlowAreaM2()
        val velocity = flowPerTube / (rho * area)
        val diameter = Unit4Plant.TUBE_ID_MM / 1000.0
        val re = rho * velocity * diameter / mu

        return Result(totalFlowKgs, activePanels, flowPerPanel, flowPerTube, velocity, re)
    }
}

/**
 * Internal forced-convection heat-transfer chain (spec §6):
 * properties -> velocity -> Re -> Pr -> Nu -> h_i.
 *
 * Dittus-Boelter: Nu = 0.023 * Re^0.8 * Pr^n, n=0.4 heating / n=0.3 cooling.
 * Validity range (0.7 <= Pr <= 160, Re > 10_000) is enforced with a warning,
 * never silently applied (spec §6).
 */
object HeatTransfer {

    data class ChainResult(
        val reynolds: Double,
        val prandtl: Double,
        val nusselt: Double,
        val hiWm2K: Double,
        val validityWarnings: List<String>,
    )

    fun dittusBoelter(
        velocityMs: Double,
        diameterM: Double,
        rho: Double,
        mu: Double,
        cp: Double,
        k: Double,
        heating: Boolean = true,
    ): ChainResult {
        val re = rho * velocityMs * diameterM / mu
        val pr = cp * mu / k
        val n = if (heating) 0.4 else 0.3
        val nu = 0.023 * Math.pow(re, 0.8) * Math.pow(pr, n)
        val hi = nu * k / diameterM

        val warnings = mutableListOf<String>()
        if (pr < 0.7 || pr > 160) warnings += "Pr=${"%.2f".format(pr)} outside Dittus-Boelter range 0.7..160"
        if (re <= 10_000) warnings += "Re=${"%.0f".format(re)} <= 10_000 — below Dittus-Boelter turbulent range"
        return ChainResult(re, pr, nu, hi, warnings)
    }
}

/**
 * Overall heat-transfer coefficient U from cylindrical resistances in series (spec §7):
 * 1/(U*A_o) = 1/(h_i*A_i) + ln(D_o/D_i)/(2*pi*k_wall*L) + R_fouling + 1/(h_o*A_o)
 *
 * Every term is exposed individually. U is recomputed every timestep from current
 * h_i, h_o and geometry — never a fixed constant.
 */
object OverallU {

    data class Terms(
        val hiWm2K: Double,
        val hoWm2K: Double,
        val wallResistanceKm2W: Double,   // per outer area
        val foulingResistanceKm2W: Double, // per outer area
        val uWm2K: Double,
        val dominantTerm: String,
    )

    /**
     * @param lengthM tube length of the section [m]
     * @param foulingKm2W fouling resistance per outer area [m2.K/W] (0 = clean)
     */
    fun compute(
        hiWm2K: Double,
        hoWm2K: Double,
        tubeODM: Double,
        tubeIDM: Double,
        lengthM: Double,
        kWallWmK: Double,
        foulingKm2W: Double,
    ): Terms {
        val doM = tubeODM
        val diM = tubeIDM
        val ao = PI * doM * lengthM
        val ai = PI * diM * lengthM

        // Per-outer-area resistances [m2.K/W]
        val rConvIn = ao / (hiWm2K * ai)
        val rWall = doM * Math.log(doM / diM) / (2.0 * kWallWmK)
        val rFoul = foulingKm2W
        val rConvOut = 1.0 / hoWm2K

        val u = 1.0 / (rConvIn + rWall + rFoul + rConvOut)

        val dominant = listOf(
            "internal convection" to rConvIn,
            "wall conduction" to rWall,
            "fouling" to rFoul,
            "external convection" to rConvOut,
        ).maxByOrNull { it.second }!!.first

        return Terms(hiWm2K, hoWm2K, rWall, rFoul, u, dominant)
    }
}