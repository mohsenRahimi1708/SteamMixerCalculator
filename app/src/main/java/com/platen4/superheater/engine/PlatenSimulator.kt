package com.platen4.superheater.engine

import kotlin.math.abs

/**
 * Piecewise interpolators built from scenario events — deterministic and RK4-safe
 * (no hidden state mutated inside derivative evaluations).
 */
object EventInterpolators {

    /** Step function: holds previous value until an event time, then jumps. */
    fun step(points: List<Pair<Double, Double>>, initial: Double): (Double) -> Double {
        val sorted = points.sortedBy { it.first }
        val f: (Double) -> Double = { t ->
            var v = initial
            for ((te, vv) in sorted) if (te <= t) v = vv else break
            v
        }
        return f
    }

    /** Linear ramp between consecutive event values; holds outside the range. */
    fun ramp(points: List<Pair<Double, Double>>, initial: Double): (Double) -> Double {
        val sorted = listOf(0.0 to initial) + points.sortedBy { it.first }
        val f: (Double) -> Double = { t ->
            when {
                t <= sorted.first().first -> sorted.first().second
                t >= sorted.last().first -> sorted.last().second
                else -> {
                    var v = sorted.last().second
                    for (i in 0 until sorted.size - 1) {
                        val (t0, v0) = sorted[i]
                        val (t1, v1) = sorted[i + 1]
                        if (t in t0..t1) {
                            val frac = (t - t0) / (t1 - t0).coerceAtLeast(1e-12)
                            v = v0 + frac * (v1 - v0)
                            break
                        }
                    }
                    v
                }
            }
        }
        return f
    }

    fun of(events: List<ScenarioEvent>, kind: ScenarioEvent.Kind, initial: Double, ramp: Boolean = false): (Double) -> Double {
        val pts = events.filter { it.kind == kind }.map { it.timeSeconds to it.value }
        return if (ramp) ramp(pts, initial) else step(pts, initial)
    }
}

/**
 * Platen superheater transient simulation (spec §11-§13).
 *
 * Five physical sections in flow order (inlet casing -> lower radiant -> inner
 * horizontal -> upper horizontal -> outlet casing). Each segment carries its own
 * metal mass, steam mass, and thermal state; steam state propagates segment to
 * segment (outlet of segment n = inlet of segment n+1).
 *
 * Per-segment direct energy balance (not LMTD):
 *   C_metal * dT_m/dt = Q_furnace,seg - h_i*A_i*(T_m - T_s) - Q_loss
 *   C_steam * dT_s/dt = m_dot*cp*(T_in - T_s) + h_i*A_i*(T_m - T_s)
 *
 * Integrated with RK4. Spray mixing (4-state model) is applied at the platen inlet
 * every timestep; T(P,h) comes from the IF97 Region 2 backward equation.
 */
class PlatenSimulator(
    private val props: SteamProperties,
    private val config: Config = Config(),
) {

    data class Config(
        val dtSeconds: Double = 0.5,
        val durationSeconds: Double = 1800.0,
        val activePanels: Int = Unit4Plant.PANELS,
        val heating: Boolean = true,
        val hoWm2K: Double = 150.0,
        val foulingKm2W: Double = 0.0,
        val platenFraction: Double = 0.15,
        /** Q_platen split across the 5 sections (must sum to 1.0). */
        val heatFractions: DoubleArray = doubleArrayOf(0.0, 0.60, 0.25, 0.15, 0.0),
        val burnerRampSeconds: Double = 60.0,
        val sprayRampSeconds: Double = 0.0,
        val lossCoeffWm2K: Double = 0.0,
        val ambientK: Double = 300.0,
        val initialMetalK: Double? = null,
    )

    class Result(
        val times: DoubleArray,
        val outletTempK: DoubleArray,
        val metalTempAvgK: DoubleArray,
        val mixedTempK: DoubleArray,
        val hiWm2K: DoubleArray,
        val uWm2K: DoubleArray,
        val reynolds: DoubleArray,
        val prandtl: DoubleArray,
        val nusselt: DoubleArray,
        val qPlatenW: DoubleArray,
        val qAbsorbedW: DoubleArray,
        val sprayFlowKgs: DoubleArray,
        val steamFlowKgs: DoubleArray,
        val pressurePa: DoubleArray,
        val segmentSteamTempK: Array<DoubleArray>,
        val segmentMetalTempK: Array<DoubleArray>,
        val error: String? = null,
    )

    // ---- Geometry (computed once) ----
    private val tubeODM = Unit4Plant.TUBE_OD_MM / 1000.0
    private val tubeIDM = Unit4Plant.TUBE_ID_MM / 1000.0
    private val totalLengthM = Unit4Plant.SECTIONS.sumOf { it.lengthM }
    private val wallAreaPerM2 = Math.PI * (tubeODM * tubeODM - tubeIDM * tubeIDM) / 4.0 // metal cross-section [m2]
    private val totalMetalMassKg = wallAreaPerM2 * totalLengthM * Unit4Plant.TOTAL_TUBES * 7750.0
    private val steamVolumePerM3 = Math.PI * tubeIDM * tubeIDM / 4.0 * Unit4Plant.TOTAL_TUBES // [m3/m]

    private val segmentMetalMassKg = DoubleArray(5) { i ->
        val s = Unit4Plant.SECTIONS[i]
        wallAreaPerM2 * s.lengthM * Unit4Plant.TOTAL_TUBES * s.densityKgM3
    }
    private val segmentSteamVolumeM3 = DoubleArray(5) { i ->
        steamVolumePerM3 * Unit4Plant.SECTIONS[i].lengthM
    }
    private val segmentInnerAreaM2 = DoubleArray(5) { i ->
        Math.PI * tubeIDM * Unit4Plant.SECTIONS[i].lengthM * Unit4Plant.TOTAL_TUBES
    }
    private val segmentOuterAreaM2 = DoubleArray(5) { i ->
        Math.PI * tubeODM * Unit4Plant.SECTIONS[i].lengthM * Unit4Plant.TOTAL_TUBES
    }

    init {
        require(abs(config.heatFractions.sum() - 1.0) < 1e-9) { "heatFractions must sum to 1.0" }
        require(config.dtSeconds > 0.0) { "dt must be positive" }
    }

    fun run(
        events: List<ScenarioEvent> = emptyList(),
        initialSteamTempK: Double = 673.15,
        initialPressurePa: Double = 100.0 * 1e5,
    ): Result {
        val spray = SprayModel(props)
        val nSeg = 5
        val nSteps = (config.durationSeconds / config.dtSeconds).toInt() + 1

        // Input interpolators (deterministic, RK4-safe)
        val steamFlow = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_FLOW_KGS, initialSteamFlow(events), ramp = false)
        val sprayFlow = EventInterpolators.of(events, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 0.0, ramp = config.sprayRampSeconds > 0)
        val sprayTemp = EventInterpolators.of(events, ScenarioEvent.Kind.SPRAY_TEMP_K, 503.15) // 230 C default, always < 250 C
        val burners = EventInterpolators.of(events, ScenarioEvent.Kind.BURNERS_FIRING, 2.0, ramp = true)
        val pressure = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_PRESSURE_PA, initialPressurePa)
        val steamTemp = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_TEMP_K, initialSteamTempK)

        val tMetal0 = config.initialMetalK ?: initialSteamTempK
        var state = DoubleArray(2 * nSeg) { i -> if (i % 2 == 0) tMetal0 else initialSteamTempK }

        // Output buffers
        val times = DoubleArray(nSteps)
        val outlet = DoubleArray(nSteps)
        val metalAvg = DoubleArray(nSteps)
        val mixedT = DoubleArray(nSteps)
        val hiArr = DoubleArray(nSteps)
        val uArr = DoubleArray(nSteps)
        val reArr = DoubleArray(nSteps)
        val prArr = DoubleArray(nSteps)
        val nuArr = DoubleArray(nSteps)
        val qPlat = DoubleArray(nSteps)
        val qAbs = DoubleArray(nSteps)
        val sprayArr = DoubleArray(nSteps)
        val flowArr = DoubleArray(nSteps)
        val pressArr = DoubleArray(nSteps)
        @Suppress("UNCHECKED_CAST")
        val segSteam = Array(nSteps) { DoubleArray(nSeg) }
        @Suppress("UNCHECKED_CAST")
        val segMetal = Array(nSteps) { DoubleArray(nSeg) }

        for (k in 0 until nSteps) {
            val t = k * config.dtSeconds
            times[k] = t

            val mDot = steamFlow(t).coerceAtLeast(1e-3)
            val mSpray = sprayFlow(t).coerceAtLeast(0.0)
            val p = pressure(t)
            val tSteam = steamTemp(t)
            val burnersOn = burners(t)

            // Spray mixing at platen inlet (4-state model) — if no spray, inlet = steam state
            val mix = if (mSpray > 0.0) {
                spray.mix(mDot, mSpray, p, tSteam, sprayTemp(t))
            } else null
            val tIn = mix?.mixedTemperatureK ?: tSteam
            val qPlaten = BurnerModel(platenFraction = config.platenFraction, rampSeconds = config.burnerRampSeconds)
                .platenHeatW(burnersOn.toInt(), 1.0, t)

            // ---- RK4 step ----
            val h = config.dtSeconds
            val k1 = derivatives(state, t, mDot, mSpray, mix, p, tIn, qPlaten)
            val k2 = derivatives(add(state, mul(k1, h / 2)), t + h / 2, mDot, mSpray, mix, p, tIn, qPlaten)
            val k3 = derivatives(add(state, mul(k2, h / 2)), t + h / 2, mDot, mSpray, mix, p, tIn, qPlaten)
            val k4 = derivatives(add(state, mul(k3, h)), t + h, mDot, mSpray, mix, p, tIn, qPlaten)

            for (i in state.indices) {
                state[i] += h / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i])
            }

            // ---- Record outputs ----
            outlet[k] = state[2 * nSeg - 1]
            metalAvg[k] = (0 until nSeg).sumOf { state[2 * it] } / nSeg
            mixedT[k] = tIn
            sprayArr[k] = mSpray
            flowArr[k] = mDot
            pressArr[k] = p
            qPlat[k] = qPlaten
            for (i in 0 until nSeg) {
                segSteam[k][i] = state[2 * i + 1]
                segMetal[k][i] = state[2 * i]
            }

            // Heat-transfer chain at segment 0 (representative; all segments share flow)
            val s0 = state[1]
            try {
                val rho = props.densityPT(p, s0)
                val mu = props.viscosityPT(p, s0)
                val cp = props.cpPT(p, s0)
                val kCond = props.conductivityPT(p, s0)
                val pr = props.prandtlPT(p, s0)
                val flow = FlowModel.compute(mDot, config.activePanels, rho, mu)
                val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rho, mu, cp, kCond, config.heating)
                val u = OverallU.compute(ht.hiWm2K, config.hoWm2K, tubeODM, tubeIDM, 1.0, 32.0, config.foulingKm2W)
                hiArr[k] = ht.hiWm2K
                uArr[k] = u.uWm2K
                reArr[k] = ht.reynolds
                prArr[k] = pr
                nuArr[k] = ht.nusselt
                qAbs[k] = (0 until nSeg).sumOf { i ->
                    ht.hiWm2K * segmentInnerAreaM2[i] * (state[2 * i] - state[2 * i + 1])
                }
            } catch (e: SteamPropertyException) {
                return Result(times, outlet, metalAvg, mixedT, hiArr, uArr, reArr, prArr, nuArr,
                    qPlat, qAbs, sprayArr, flowArr, pressArr, segSteam, segMetal,
                    error = "IF97 state error at t=${"%.1f".format(t)}s: ${e.message}")
            }
        }

        return Result(times, outlet, metalAvg, mixedT, hiArr, uArr, reArr, prArr, nuArr,
            qPlat, qAbs, sprayArr, flowArr, pressArr, segSteam, segMetal)
    }

    private fun initialSteamFlow(events: List<ScenarioEvent>): Double {
        val e = events.filter { it.kind == ScenarioEvent.Kind.STEAM_FLOW_KGS }.minByOrNull { it.timeSeconds }
        return e?.value ?: (100.0 / 3.6)
    }

    /** d(state)/dt with inputs evaluated at time t. */
    private fun derivatives(
        state: DoubleArray,
        t: Double,
        mDot: Double,
        mSpray: Double,
        mix: SprayModel.MixingResult?,
        p: Double,
        tIn: Double,
        qPlaten: Double,
    ): DoubleArray {
        val d = DoubleArray(state.size)
        for (i in 0 until 5) {
            val tm = state[2 * i]
            val ts = state[2 * i + 1]
            val sec = Unit4Plant.SECTIONS[i]

            val rho = props.densityPT(p, ts)
            val mu = props.viscosityPT(p, ts)
            val cp = props.cpPT(p, ts)
            val kCond = props.conductivityPT(p, ts)
            val flow = FlowModel.compute(mDot, config.activePanels, rho, mu)
            val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rho, mu, cp, kCond, config.heating)

            val steamMass = rho * segmentSteamVolumeM3[i]
            val metalMass = segmentMetalMassKg[i]
            val metalCp = sec.cpJkgK

            val qFurnace = qPlaten * config.heatFractions[i]
            val qToSteam = ht.hiWm2K * segmentInnerAreaM2[i] * (tm - ts)
            val qLoss = config.lossCoeffWm2K * segmentOuterAreaM2[i] * (tm - config.ambientK)

            // Inlet temperature: segment 0 gets the post-spray mixed temp; others chain
            val segInlet = if (i == 0) tIn else state[2 * (i - 1) + 1]

            d[2 * i] = (qFurnace - qToSteam - qLoss) / (metalMass * metalCp)

            // Steam energy balance, relaxed to explicit stability.
            // The casing segments hold very little steam (steam time constant ~0.1 s),
            // which would make explicit RK4 unstable at dt ~ 1 s. Using
            //   tau = max(C_steam / (m_dot*cp + h_i*A_i), dt)
            // the balance converges to the exact steady state as fast as the timestep
            // allows, preserving the energy balance (same fixed point) while remaining
            // stable for any segment size.
            val steamConductance = mDot * cp + ht.hiWm2K * segmentInnerAreaM2[i]
            val steamTau = steamMass * cp / steamConductance
            val tauRelax = Math.max(steamTau, config.dtSeconds)
            val tsSteady = (mDot * cp * segInlet + ht.hiWm2K * segmentInnerAreaM2[i] * tm) / steamConductance
            d[2 * i + 1] = (tsSteady - ts) / tauRelax
        }
        return d
    }

    private fun add(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(a.size) { a[it] + b[it] }
    private fun mul(a: DoubleArray, f: Double): DoubleArray = DoubleArray(a.size) { a[it] * f }
}

/**
 * Step-response metrics (τ, θ) per spec §3.3 reference targets.
 * θ = time from the step until outlet T deviates more than [thetaThreshold] of the total change;
 * τ = time to reach 63.2% of the total change, measured from the step time.
 */
object StepMetrics {
    fun compute(times: DoubleArray, outletK: DoubleArray, stepTime: Double): Pair<Double, Double> {
        val base = outletK.first()
        val final = outletK.last()
        val delta = final - base
        if (abs(delta) < 1e-6) return 0.0 to 0.0

        val threshold = abs(delta) * 0.005
        var theta = 0.0
        var tau = 0.0
        for (k in times.indices) {
            if (times[k] < stepTime) continue
            val change = abs(outletK[k] - base)
            if (theta == 0.0 && change > threshold) theta = times[k]
            if (change >= abs(delta) * 0.632 && tau == 0.0) {
                tau = times[k]
                break
            }
        }
        return theta to (tau - theta).coerceAtLeast(0.0)
    }
}