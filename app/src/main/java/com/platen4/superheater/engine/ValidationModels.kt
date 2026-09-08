package com.platen4.superheater.engine

/**
 * Simplified single-block comparison models (spec §16-§17) — explicitly labeled
 * validation modes, never the production path:
 *   (a) constant-U model: fixed overall U (user picks 800 / 170 / fitted)
 *   (b) dynamic-h_i model: single block, h_i from the Re->Pr->Nu->h_i chain
 *   (c) full segmented transient model (PlatenSimulator)
 *
 * Single-block balance:
 *   C_metal*dT_m/dt = Q - (h_i*A_i or U*A_o)*(T_m - T_s)
 *   C_steam *dT_s/dt = m_dot*cp*(T_in - T_s) + (h_i*A_i or U*A_o)*(T_m - T_s)
 */
class SingleBlockModel(
    private val props: SteamProperties,
    private val mode: Mode,
    private val fixedUWm2K: Double = 800.0,
    private val metalMassKg: Double = 62_500.0,
    private val metalCpJkgK: Double = 520.0,
    private val innerAreaM2: Double = Unit4Plant.INNER_AREA_M2,
    private val outerAreaM2: Double = Unit4Plant.OUTER_AREA_M2,
    private val steamVolumeM3: Double = 5.7,
    private val dtSeconds: Double = 0.5,
) {
    enum class Mode { CONSTANT_U, DYNAMIC_HI }

    data class Result(
        val times: DoubleArray,
        val outletTempK: DoubleArray,
        val metalTempK: DoubleArray,
        val hiWm2K: DoubleArray,
        val uWm2K: DoubleArray,
    )

    fun run(
        events: List<ScenarioEvent>,
        initialSteamTempK: Double = 673.15,
        initialPressurePa: Double = 100.0 * 1e5,
        qPlatenW: Double = 80e6,
    ): Result {
        val steamFlow = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_FLOW_KGS, 100.0 / 3.6)
        val sprayFlow = EventInterpolators.of(events, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 0.0)
        val pressure = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_PRESSURE_PA, initialPressurePa)
        val steamTemp = EventInterpolators.of(events, ScenarioEvent.Kind.STEAM_TEMP_K, initialSteamTempK)

        val nSteps = (1800.0 / dtSeconds).toInt() + 1
        val times = DoubleArray(nSteps)
        val outlet = DoubleArray(nSteps)
        val metal = DoubleArray(nSteps)
        val hiArr = DoubleArray(nSteps)
        val uArr = DoubleArray(nSteps)

        var tm = initialSteamTempK
        var ts = initialSteamTempK
        val spray = SprayModel(props)

        for (k in 0 until nSteps) {
            val t = k * dtSeconds
            times[k] = t
            val mDot = steamFlow(t).coerceAtLeast(1e-3)
            val mSpray = sprayFlow(t)
            val p = pressure(t)
            val tSteam = steamTemp(t)
            // Spray water: 230 °C default — always subcooled, below the 250 °C plant limit
            val tIn = if (mSpray > 0) spray.mix(mDot, mSpray, p, tSteam, 503.15).mixedTemperatureK else tSteam

            val rho = props.densityPT(p, ts)
            val mu = props.viscosityPT(p, ts)
            val cp = props.cpPT(p, ts)
            val kCond = props.conductivityPT(p, ts)
            val flow = FlowModel.compute(mDot, Unit4Plant.PANELS, rho, mu)
            val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, 0.029, rho, mu, cp, kCond)

            val heatTransferWm2K = when (mode) {
                Mode.CONSTANT_U -> fixedUWm2K * outerAreaM2 / innerAreaM2 // per inner area
                Mode.DYNAMIC_HI -> ht.hiWm2K
            }
            val aRef = if (mode == Mode.CONSTANT_U) innerAreaM2 else innerAreaM2

            val steamMass = rho * steamVolumeM3
            val qToSteam = heatTransferWm2K * aRef * (tm - ts)

            // Explicit Euler
            tm += dtSeconds * (qPlatenW - qToSteam) / (metalMassKg * metalCpJkgK)
            ts += dtSeconds * (mDot * cp * (tIn - ts) + qToSteam) / (steamMass * cp)

            outlet[k] = ts
            metal[k] = tm
            hiArr[k] = ht.hiWm2K
            uArr[k] = if (mode == Mode.CONSTANT_U) fixedUWm2K else ht.hiWm2K * innerAreaM2 / outerAreaM2
        }
        return Result(times, outlet, metal, hiArr, uArr)
    }
}

/**
 * Calibration against plant historian data (spec §17): fit effective F_platen and
 * h_o (or U) by coordinate search minimizing RMSE of outlet temperature.
 * Fitted parameters are returned separately and labeled empirical — never silently
 * merged into the physical parameters.
 */
object Calibration {

    data class FitResult(
        val fittedPlatenFraction: Double,
        val fittedHoWm2K: Double,
        val maeK: Double,
        val rmseK: Double,
        val maxErrorK: Double,
        val biasK: Double,
        val rSquared: Double,
    )

    /**
     * @param measuredOutletK measured outlet temperatures [K]
     * @param predictedAt a function (platenFraction, ho) -> predicted outlet series [K]
     */
    fun fit(
        measuredOutletK: DoubleArray,
        predictedAt: (platenFraction: Double, hoWm2K: Double) -> DoubleArray,
        platenRange: ClosedFloatingPointRange<Double> = 0.05..0.5,
        hoRange: ClosedFloatingPointRange<Double> = 20.0..500.0,
        platenSteps: Int = 10,
        hoSteps: Int = 10,
    ): FitResult {
        var best = Triple(platenRange.start, hoRange.start, Double.MAX_VALUE)
        for (i in 0..platenSteps) {
            val pf = platenRange.start + (platenRange.endInclusive - platenRange.start) * i / platenSteps
            for (j in 0..hoSteps) {
                val ho = hoRange.start + (hoRange.endInclusive - hoRange.start) * j / hoSteps
                val pred = predictedAt(pf, ho)
                val rmse = rmse(measuredOutletK, pred)
                if (rmse < best.third) best = Triple(pf, ho, rmse)
            }
        }
        val (pf, ho, _) = best
        val pred = predictedAt(pf, ho)
        return FitResult(
            fittedPlatenFraction = pf,
            fittedHoWm2K = ho,
            maeK = mae(measuredOutletK, pred),
            rmseK = best.third,
            maxErrorK = maxError(measuredOutletK, pred),
            biasK = bias(measuredOutletK, pred),
            rSquared = rSquared(measuredOutletK, pred),
        )
    }

    private fun rmse(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += (a[i] - b[i]) * (a[i] - b[i])
        return Math.sqrt(s / a.size)
    }

    private fun mae(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += Math.abs(a[i] - b[i])
        return s / a.size
    }

    private fun maxError(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) m = Math.max(m, Math.abs(a[i] - b[i]))
        return m
    }

    private fun bias(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] - b[i]
        return s / a.size
    }

    private fun rSquared(a: DoubleArray, b: DoubleArray): Double {
        val mean = a.average()
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in a.indices) {
            ssTot += (a[i] - mean) * (a[i] - mean)
            ssRes += (a[i] - b[i]) * (a[i] - b[i])
        }
        return 1.0 - ssRes / ssTot.coerceAtLeast(1e-12)
    }
}