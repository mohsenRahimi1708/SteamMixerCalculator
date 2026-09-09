package com.platen4.superheater.engine

import kotlin.math.abs

/**
 * Piecewise interpolators built from scenario events — deterministic and RK4-safe
 * (no hidden state mutated inside derivative evaluations).
 *
 * **Enabled-flag semantics:** disabled events are excluded when the interpolator
 * list is built, so an event that is toggled off simply never fires. This is the
 * mechanism behind single-scenario and mixed-scenario runs (refactored spec §15).
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

    fun of(
        events: List<ScenarioEvent>,
        kind: ScenarioEvent.Kind,
        initial: Double,
        ramp: Boolean = false,
    ): (Double) -> Double {
        val pts = events.filter { it.kind == kind && it.enabled }.map { it.timeSeconds to it.value }
        return if (ramp) ramp(pts, initial) else step(pts, initial)
    }
}

/**
 * Platen superheater transient simulation — **uniform lumped model** (refactored spec).
 *
 * The platen coil is treated as a single uniform tube (one metal node, one steam node):
 * no segmentation, single material (12Cr2MoWVTiB), uniform heat flux. Energy balances:
 *
 *   C_metal * dT_m/dt = Q_platen - h_i*A_i*(T_m - T_s) - Q_loss
 *   C_steam * dT_s/dt = m_dot*cp*(T_in - T_s) + h_i*A_i*(T_m - T_s)
 *
 * Integrated with RK4. Spray mixing (4-state model) is applied at the platen inlet
 * every timestep; T(P,h) comes from the IF97 Region 2 backward equation.
 *
 * **Scenario enable/disable:** each [ScenarioEvent] carries an `enabled` flag; disabled
 * events are excluded from the input interpolation, so the user can simulate a single
 * scenario or any mix of scenarios (refactored spec §15).
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
        val burnerRampSeconds: Double = 60.0,
        val sprayRampSeconds: Double = 0.0,
        val lossCoeffWm2K: Double = 0.0,
        val ambientK: Double = 300.0,
        /** Initial metal temperature [K] — user input, default 450 °C (refactored spec §15). */
        val initialMetalK: Double = (Unit4Plant.DEFAULT_METAL_TEMP_C + 273.15),
        /** Wall thermal conductivity [W/m·K] — single uniform material. */
        val wallKWmK: Double = Unit4Plant.METAL_K_WM_K,
        /** Metal specific heat [J/kg·K] — single uniform material. */
        val metalCpJkgK: Double = Unit4Plant.METAL_CP_JKG_K,
    )

    /** All thermodynamic and derived parameters at one time sample (refactored spec §16). */
    data class Sample(
        val timeS: Double,
        val pressurePa: Double,
        val steamTempK: Double,
        val metalTempK: Double,
        val mixedTempK: Double,
        val outletTempK: Double,
        val enthalpyJkg: Double,
        val densityKgM3: Double,
        val cpJkgK: Double,
        val viscosityPaS: Double,
        val conductivityWmK: Double,
        val prandtl: Double,
        val steamFlowKgs: Double,
        val sprayFlowKgs: Double,
        val velocityMs: Double,
        val reynolds: Double,
        val nusselt: Double,
        val hiWm2K: Double,
        val hoWm2K: Double,
        val uWm2K: Double,
        val qPlatenW: Double,
        val qAbsorbedW: Double,
        val validityWarnings: List<String>,
    )

    class Result(
        val times: DoubleArray,
        val outletTempK: DoubleArray,
        val metalTempK: DoubleArray,
        val mixedTempK: DoubleArray,
        val inletSteamTempK: DoubleArray,
        val pressurePa: DoubleArray,
        val hiWm2K: DoubleArray,
        val uWm2K: DoubleArray,
        val reynolds: DoubleArray,
        val prandtl: DoubleArray,
        val nusselt: DoubleArray,
        val velocityMs: DoubleArray,
        val densityKgM3: DoubleArray,
        val cpJkgK: DoubleArray,
        val viscosityPaS: DoubleArray,
        val conductivityWmK: DoubleArray,
        val enthalpyJkg: DoubleArray,
        val qPlatenW: DoubleArray,
        val qAbsorbedW: DoubleArray,
        val sprayFlowKgs: DoubleArray,
        val steamFlowKgs: DoubleArray,
        /** Full parameter sample every [sampleStride]-th step — every value accessible in the UI. */
        val samples: List<Sample>,
        val error: String? = null,
    )

    // ---- Uniform geometry (computed once) ----
    private val tubeODM = Unit4Plant.TUBE_OD_MM / 1000.0
    private val tubeIDM = Unit4Plant.TUBE_ID_MM / 1000.0
    private val totalLengthM = Unit4Plant.TOTAL_TUBE_LENGTH_M
    private val metalMassKg = Unit4Plant.totalMetalMassKg()
    private val innerAreaM2 = Unit4Plant.innerAreaM2()
    private val outerAreaM2 = Unit4Plant.outerAreaM2()
    private val steamVolumeM3 = Unit4Plant.steamVolumeM3()

    init {
        require(config.dtSeconds > 0.0) { "dt must be positive" }
        require(config.durationSeconds > 0.0) { "duration must be positive" }
        require(config.activePanels in 1..Unit4Plant.PANELS) { "activePanels must be 1..${Unit4Plant.PANELS}" }
    }

    fun run(
        events: List<ScenarioEvent> = emptyList(),
        initialSteamTempK: Double = 673.15,
        initialPressurePa: Double = 100.0 * 1e5,
        initialSteamFlowKgs: Double = 100.0 / 3.6,
    ): Result {
        val spray = SprayModel(props)
        val nSteps = (config.durationSeconds / config.dtSeconds).toInt() + 1

        // Input interpolators (deterministic, RK4-safe; disabled events excluded).
        // Initial conditions come from the explicit parameters only — an event at a
        // later time must never alter the state before its time.
        val steamFlow = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_FLOW_KGS, initialSteamFlowKgs)
        val sprayFlow = EventInterpolators.of(events, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 0.0, ramp = config.sprayRampSeconds > 0)
        val sprayTemp = EventInterpolators.of(events, ScenarioEvent.Kind.SPRAY_TEMP_K, Unit4Plant.DEFAULT_SPRAY_TEMP_C + 273.15)
        val burners = EventInterpolators.of(events, ScenarioEvent.Kind.BURNERS_FIRING, 2.0, ramp = true)
        val pressure = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_PRESSURE_PA, initialPressurePa)
        val steamTemp = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_TEMP_K, initialSteamTempK)

        var state = doubleArrayOf(config.initialMetalK, initialSteamTempK)

        // Output buffers
        val times = DoubleArray(nSteps)
        val outlet = DoubleArray(nSteps)
        val metalArr = DoubleArray(nSteps)
        val mixedT = DoubleArray(nSteps)
        val inletT = DoubleArray(nSteps)
        val pressArr = DoubleArray(nSteps)
        val hiArr = DoubleArray(nSteps)
        val uArr = DoubleArray(nSteps)
        val reArr = DoubleArray(nSteps)
        val prArr = DoubleArray(nSteps)
        val nuArr = DoubleArray(nSteps)
        val velArr = DoubleArray(nSteps)
        val rhoArr = DoubleArray(nSteps)
        val cpArr = DoubleArray(nSteps)
        val muArr = DoubleArray(nSteps)
        val kArr = DoubleArray(nSteps)
        val hArr = DoubleArray(nSteps)
        val qPlat = DoubleArray(nSteps)
        val qAbs = DoubleArray(nSteps)
        val sprayArr = DoubleArray(nSteps)
        val flowArr = DoubleArray(nSteps)
        val sampleStride = 10
        val samples = ArrayList<Sample>(nSteps / sampleStride + 2)

        val burner = BurnerModel(platenFraction = config.platenFraction, rampSeconds = config.burnerRampSeconds)

        for (k in 0 until nSteps) {
            val t = k * config.dtSeconds
            times[k] = t

            val mDot = steamFlow(t).coerceAtLeast(1e-3)
            val mSpray = sprayFlow(t).coerceAtLeast(0.0)
            val p = pressure(t)
            val tSteam = steamTemp(t)

            // Spray mixing at platen inlet (4-state model) — if no spray, inlet = steam state
            val mix = if (mSpray > 0.0) {
                try {
                    spray.mix(mDot, mSpray, p, tSteam, sprayTemp(t))
                } catch (e: SteamPropertyException) {
                    return resultWithError(
                        times, outlet, metalArr, mixedT, inletT, pressArr, hiArr, uArr, reArr, prArr,
                        nuArr, velArr, rhoArr, cpArr, muArr, kArr, hArr, qPlat, qAbs, sprayArr, flowArr,
                        samples, "Spray error at t=${"%.1f".format(t)}s: ${e.message}",
                    )
                }
            } else null
            val tIn = mix?.mixedTemperatureK ?: tSteam
            val qPlaten = burner.platenHeatW(burners(t).toInt(), 1.0, t)

            // ---- Record outputs BEFORE stepping (t=0 shows the exact initial condition) ----
            val ts = state[1]
            val tm = state[0]
            try {
                val rho = props.densityPT(p, ts)
                val mu = props.viscosityPT(p, ts)
                val cp = props.cpPT(p, ts)
                val kCond = props.conductivityPT(p, ts)
                val pr = props.prandtlPT(p, ts)
                val enthalpy = props.enthalpyPT(p, ts)
                val flow = FlowModel.compute(mDot, config.activePanels, rho, mu)
                val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rho, mu, cp, kCond, config.heating)
                val u = OverallU.compute(ht.hiWm2K, config.hoWm2K, tubeODM, tubeIDM, totalLengthM, config.wallKWmK, config.foulingKm2W)
                val qAbsorbed = ht.hiWm2K * innerAreaM2 * (tm - ts)

                outlet[k] = ts
                metalArr[k] = tm
                mixedT[k] = tIn
                inletT[k] = tSteam
                pressArr[k] = p
                hiArr[k] = ht.hiWm2K
                uArr[k] = u.uWm2K
                reArr[k] = ht.reynolds
                prArr[k] = pr
                nuArr[k] = ht.nusselt
                velArr[k] = flow.tubeVelocityMs
                rhoArr[k] = rho
                cpArr[k] = cp
                muArr[k] = mu
                kArr[k] = kCond
                hArr[k] = enthalpy
                qPlat[k] = qPlaten
                qAbs[k] = qAbsorbed
                sprayArr[k] = mSpray
                flowArr[k] = mDot

                if (k % sampleStride == 0) {
                    samples += Sample(
                        t, p, tSteam, tm, tIn, ts, enthalpy, rho, cp, mu, kCond, pr,
                        mDot, mSpray, flow.tubeVelocityMs, ht.reynolds, ht.nusselt,
                        ht.hiWm2K, config.hoWm2K, u.uWm2K, qPlaten, qAbsorbed, ht.validityWarnings,
                    )
                }
            } catch (e: SteamPropertyException) {
                return resultWithError(
                    times, outlet, metalArr, mixedT, inletT, pressArr, hiArr, uArr, reArr, prArr,
                    nuArr, velArr, rhoArr, cpArr, muArr, kArr, hArr, qPlat, qAbs, sprayArr, flowArr,
                    samples, "IF97 state error at t=${"%.1f".format(t)}s: ${e.message}",
                )
            }

            // ---- RK4 step ----
            val h = config.dtSeconds
            val k1 = derivatives(state, t, mDot, tIn, qPlaten, p)
            val k2 = derivatives(add(state, mul(k1, h / 2)), t + h / 2, mDot, tIn, qPlaten, p)
            val k3 = derivatives(add(state, mul(k2, h / 2)), t + h / 2, mDot, tIn, qPlaten, p)
            val k4 = derivatives(add(state, mul(k3, h)), t + h, mDot, tIn, qPlaten, p)

            for (i in state.indices) {
                state[i] += h / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i])
            }
            if (state[0].isNaN() || state[1].isNaN() || state[0] < 273.0 || state[0] > 1500.0 || state[1] < 273.0 || state[1] > 1500.0) {
                return resultWithError(
                    times, outlet, metalArr, mixedT, inletT, pressArr, hiArr, uArr, reArr, prArr,
                    nuArr, velArr, rhoArr, cpArr, muArr, kArr, hArr, qPlat, qAbs, sprayArr, flowArr,
                    samples, "Solver divergence at t=${"%.1f".format(t + h)}s: non-physical temperature " +
                        "T_metal=${"%.1f".format(state[0] - 273.15)} °C, T_steam=${"%.1f".format(state[1] - 273.15)} °C",
                )
            }
        }

        return Result(
            times, outlet, metalArr, mixedT, inletT, pressArr, hiArr, uArr, reArr, prArr, nuArr,
            velArr, rhoArr, cpArr, muArr, kArr, hArr, qPlat, qAbs, sprayArr, flowArr, samples,
        )
    }

    private fun resultWithError(
        times: DoubleArray, outlet: DoubleArray, metalArr: DoubleArray, mixedT: DoubleArray,
        inletT: DoubleArray, pressArr: DoubleArray, hiArr: DoubleArray, uArr: DoubleArray,
        reArr: DoubleArray, prArr: DoubleArray, nuArr: DoubleArray, velArr: DoubleArray,
        rhoArr: DoubleArray, cpArr: DoubleArray, muArr: DoubleArray, kArr: DoubleArray,
        hArr: DoubleArray, qPlat: DoubleArray, qAbs: DoubleArray, sprayArr: DoubleArray,
        flowArr: DoubleArray, samples: List<Sample>, error: String,
    ): Result = Result(
        times, outlet, metalArr, mixedT, inletT, pressArr, hiArr, uArr, reArr, prArr, nuArr,
        velArr, rhoArr, cpArr, muArr, kArr, hArr, qPlat, qAbs, sprayArr, flowArr, samples, error,
    )

    /** d(state)/dt with inputs evaluated at time t. State = [T_metal, T_steam]. */
    private fun derivatives(
        state: DoubleArray,
        t: Double,
        mDot: Double,
        tIn: Double,
        qPlaten: Double,
        p: Double,
    ): DoubleArray {
        val tm = state[0]
        val ts = state[1]

        val rho = props.densityPT(p, ts)
        val mu = props.viscosityPT(p, ts)
        val cp = props.cpPT(p, ts)
        val kCond = props.conductivityPT(p, ts)
        val flow = FlowModel.compute(mDot, config.activePanels, rho, mu)
        val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rho, mu, cp, kCond, config.heating)

        val steamMass = rho * steamVolumeM3
        val qToSteam = ht.hiWm2K * innerAreaM2 * (tm - ts)
        val qLoss = config.lossCoeffWm2K * outerAreaM2 * (tm - config.ambientK)

        val d = DoubleArray(2)
        d[0] = (qPlaten - qToSteam - qLoss) / (metalMassKg * config.metalCpJkgK)

        // Steam energy balance, relaxed to explicit stability (same fixed point):
        //   tau = max(C_steam / (m_dot*cp + h_i*A_i), dt)
        val steamConductance = mDot * cp + ht.hiWm2K * innerAreaM2
        val steamTau = steamMass * cp / steamConductance
        val tauRelax = Math.max(steamTau, config.dtSeconds)
        val tsSteady = (mDot * cp * tIn + ht.hiWm2K * innerAreaM2 * tm) / steamConductance
        d[1] = (tsSteady - ts) / tauRelax
        return d
    }

    private fun add(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(a.size) { a[it] + b[it] }
    private fun mul(a: DoubleArray, f: Double): DoubleArray = DoubleArray(a.size) { a[it] * f }
}

/**
 * Step-response metrics (τ, θ). θ = time from the step until outlet T deviates more
 * than [threshold] of the total change; τ = time to reach 63.2% of the total change.
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
