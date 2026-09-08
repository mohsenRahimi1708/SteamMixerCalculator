package com.platen4.superheater.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §22 tests 5-8 — dynamic behavior of the segmented transient model.
 */
class TransientSimulationTest {

    private val props: SteamProperties = If97SteamProperties()

    private fun quickConfig(dt: Double = 1.0, duration: Double = 900.0) =
        PlatenSimulator.Config(dtSeconds = dt, durationSeconds = duration, activePanels = Unit4Plant.PANELS)

    @Test
    fun `burner start raises metal temperature before steam outlet responds`() {
        val sim = PlatenSimulator(props, quickConfig())
        val events = listOf(
            ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 4.0),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("simulation error: ${r.error}", r.error == null)

        val baseMetal = r.metalTempAvgK[300]
        val baseOut = r.outletTempK[300]
        var metalIdx = -1
        var outletIdx = -1
        for (k in 300 until r.times.size) {
            if (metalIdx < 0 && Math.abs(r.metalTempAvgK[k] - baseMetal) > 0.5) metalIdx = k
            if (outletIdx < 0 && Math.abs(r.outletTempK[k] - baseOut) > 0.5) outletIdx = k
            if (metalIdx > 0 && outletIdx > 0) break
        }
        assertTrue("metal should respond (idx=$metalIdx)", metalIdx > 0)
        assertTrue("outlet should respond (idx=$outletIdx)", outletIdx > 0)
        // Metal is heated directly; the steam outlet can only respond through the
        // metal -> steam cascade, so it never leads the metal.
        assertTrue("metal responds before outlet: $metalIdx vs $outletIdx", metalIdx <= outletIdx)
        // And the outlet eventually rises (more heat in)
        assertTrue("outlet rises after burner start", r.outletTempK.last() > r.outletTempK[300] + 2.0)
    }

    @Test
    fun `spray increase lowers outlet temperature and enthalpy balance closes`() {
        // Enthalpy balance check on the mixing model directly
        val spray = SprayModel(props)
        val mix = spray.mix(27.78, 1.389, 1e7, 673.15, 503.15)
        val expected = (27.78 * mix.steamEnthalpyJkg + 1.389 * mix.sprayEnthalpyJkg) / (27.78 + 1.389)
        assertEquals(expected, mix.mixedEnthalpyJkg, 1e-6)
        assertTrue("spray cools: h_mix < h_steam", mix.mixedEnthalpyJkg < mix.steamEnthalpyJkg)
        assertTrue("spray cooling power positive", mix.sprayCoolingW > 0)

        // Dynamic test: twin runs, identical except one has 5 t/h spray from t=300 s.
        // Comparing the two isolates the spray effect from the ongoing heat-up transient.
        val sim = PlatenSimulator(props, quickConfig())
        val without = sim.run(emptyList(), initialSteamTempK = 673.15, initialPressurePa = 1e7)
        val withSpray = sim.run(
            listOf(ScenarioEvent(300.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0 / 3.6)), // 5 t/h
            initialSteamTempK = 673.15,
            initialPressurePa = 1e7,
        )
        assertTrue("simulation error: ${withSpray.error}", withSpray.error == null)

        // Before the event the runs are identical; after it the spray run is cooler.
        assertEquals(without.outletTempK[299], withSpray.outletTempK[299], 1e-9)
        val d300 = without.outletTempK[600] - withSpray.outletTempK[600]
        val dEnd = without.outletTempK.last() - withSpray.outletTempK.last()
        assertTrue("spray run cooler at t=600 (delta=$d300 K)", d300 > 5.0)
        assertTrue("spray run cooler at end (delta=$dEnd K)", dEnd > 5.0)
    }

    @Test
    fun `combined load burner and spray events stay stable`() {
        val sim = PlatenSimulator(props, quickConfig())
        val events = listOf(
            ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
            ScenarioEvent(400.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0 / 3.6),
            ScenarioEvent(500.0, ScenarioEvent.Kind.BURNERS_FIRING, 3.0),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("simulation error: ${r.error}", r.error == null)

        // No NaN / non-physical temperatures
        for (t in r.outletTempK) {
            assertTrue("outlet $t in physical range", t in 300.0..900.0)
            assertTrue("no NaN", !t.isNaN())
        }
        // No wild oscillation: consecutive outlet samples change by < 20 K
        for (k in 1 until r.outletTempK.size) {
            val d = Math.abs(r.outletTempK[k] - r.outletTempK[k - 1])
            assertTrue("oscillation at step $k: $d K", d < 20.0)
        }
    }

    @Test
    fun `halving timestep converges to same result`() {
        val events = listOf(
            ScenarioEvent(200.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
            ScenarioEvent(400.0, ScenarioEvent.Kind.BURNERS_FIRING, 3.0),
        )
        val rCoarse = PlatenSimulator(props, quickConfig(dt = 2.0, duration = 600.0))
            .run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        val rFine = PlatenSimulator(props, quickConfig(dt = 1.0, duration = 600.0))
            .run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)

        assertTrue("coarse: ${rCoarse.error}", rCoarse.error == null)
        assertTrue("fine: ${rFine.error}", rFine.error == null)
        val diff = Math.abs(rCoarse.outletTempK.last() - rFine.outletTempK.last())
        assertTrue("final outlet converges (dt=2 vs dt=1): diff=$diff K", diff < 5.0)
    }

    @Test
    fun `step metrics produce positive dead time and time constant`() {
        val sim = PlatenSimulator(props, quickConfig(dt = 1.0, duration = 1200.0))
        val events = listOf(
            ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        val (theta, tau) = StepMetrics.compute(r.times, r.outletTempK, 300.0)
        assertTrue("theta=$theta >= 0", theta >= 0.0)
        assertTrue("tau=$tau > 0", tau > 0.0)
    }
}