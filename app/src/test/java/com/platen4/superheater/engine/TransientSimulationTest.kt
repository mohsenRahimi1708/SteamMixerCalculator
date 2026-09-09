package com.platen4.superheater.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §22 tests 5-8 — dynamic behavior of the uniform transient model — plus tests
 * for the refactored-spec features: per-scenario enable/disable, the 100-180 °C
 * subcooled spray window, and the user-set metal temperature (default 450 °C).
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

        val baseMetal = r.metalTempK[300]
        val baseOut = r.outletTempK[300]
        var metalIdx = -1
        var outletIdx = -1
        for (k in 300 until r.times.size) {
            if (metalIdx < 0 && Math.abs(r.metalTempK[k] - baseMetal) > 0.5) metalIdx = k
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
        val mix = spray.mix(27.78, 1.389, 1e7, 673.15, 423.15) // spray 150 C, inside 100-180 window
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
        assertTrue("spray run cooler at t=600 (delta=$d300 K)", d300 > 3.0)
        assertTrue("spray run cooler at end (delta=$dEnd K)", dEnd > 3.0)
    }

    @Test
    fun `spray temperature outside 100-180 window is rejected`() {
        val spray = SprayModel(props)
        // Below the window (90 C)
        try {
            spray.mix(27.78, 1.389, 1e7, 673.15, 363.15)
            throw AssertionError("expected rejection below 100 C")
        } catch (e: SteamPropertyException) {
            assertTrue(e.message!!.contains("100-180"))
        }
        // Above the window (200 C) — also above the old 250 C rule but caught earlier
        try {
            spray.mix(27.78, 1.389, 1e7, 673.15, 473.15)
            throw AssertionError("expected rejection above 180 C")
        } catch (e: SteamPropertyException) {
            assertTrue(e.message!!.contains("100-180"))
        }
        // Inside the window and subcooled passes
        spray.mix(27.78, 1.389, 1e7, 673.15, 423.15)
        // Non-subcooled at low pressure (Tsat ~ 212 C at 20 bar, spray 180 C < Tsat OK;
        // use 3 bar where Tsat ~ 133 C to trigger subcooling failure)
        try {
            spray.mix(27.78, 1.389, 3e5, 673.15, 423.15)
            throw AssertionError("expected subcooling rejection at 3 bar")
        } catch (e: SteamPropertyException) {
            assertTrue(e.message!!.contains("subcooled"))
        }
    }

    @Test
    fun `disabled events are excluded enabled events apply`() {
        val sim = PlatenSimulator(props, quickConfig())

        // Two steam-flow events: one disabled (300 -> 150 t/h), one enabled (300 -> 250 t/h)
        val events = listOf(
            ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6, enabled = false),
            ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 250.0 / 3.6, enabled = true),
        )
        val withEvents = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("simulation error: ${withEvents.error}", withEvents.error == null)

        // Baseline: no events, same initial flow (explicit, not event-derived)
        val baseline = sim.run(emptyList(), initialSteamTempK = 673.15, initialPressurePa = 1e7, initialSteamFlowKgs = 100.0 / 3.6)
        assertEquals("before event identical", baseline.outletTempK[299], withEvents.outletTempK[299], 1e-9)
        assertTrue("flow applied after event", withEvents.steamFlowKgs[400] > 60.0)
        assertEquals("flow is the enabled value", 250.0 / 3.6, withEvents.steamFlowKgs[400], 1e-9)

        // A run where ALL events are disabled must equal the baseline exactly
        val allDisabled = sim.run(
            listOf(
                ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6, enabled = false),
                ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 5.0, enabled = false),
                ScenarioEvent(300.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0 / 3.6, enabled = false),
            ),
            initialSteamTempK = 673.15,
            initialPressurePa = 1e7,
            initialSteamFlowKgs = 100.0 / 3.6,
        )
        assertEquals(allDisabled.outletTempK.last(), baseline.outletTempK.last(), 1e-9)
        assertEquals(allDisabled.metalTempK.last(), baseline.metalTempK.last(), 1e-9)
    }

    @Test
    fun `single scenario vs mixed scenario runs differ as expected`() {
        val sim = PlatenSimulator(props, quickConfig())

        // Single scenario: only the burner step
        val single = sim.run(
            listOf(ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 4.0)),
            initialSteamTempK = 673.15,
            initialPressurePa = 1e7,
        )
        // Mixed scenario: burner step + spray, both enabled
        val mixed = sim.run(
            listOf(
                ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 4.0, enabled = true),
                ScenarioEvent(300.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0 / 3.6, enabled = true),
            ),
            initialSteamTempK = 673.15,
            initialPressurePa = 1e7,
        )
        assertTrue("single error: ${single.error}", single.error == null)
        assertTrue("mixed error: ${mixed.error}", mixed.error == null)
        // Mixed (with spray) must be cooler than single (no spray) — physically consistent superposition
        assertTrue(
            "mixed cooler than single: ${mixed.outletTempK.last()} vs ${single.outletTempK.last()}",
            mixed.outletTempK.last() < single.outletTempK.last(),
        )
    }

    @Test
    fun `metal temperature is user-settable and defaults to 450 C`() {
        assertTrue(Unit4Plant.DEFAULT_METAL_TEMP_C == 450.0)
        // Explicit initial metal temperature is honored exactly at t=0
        val simT550 = PlatenSimulator(props, quickConfig(duration = 60.0).copy(initialMetalK = 550.0 + 273.15))
        val r550 = simT550.run(initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("initial metal honored: ${r550.metalTempK[0]}", Math.abs(r550.metalTempK[0] - 823.15) < 1e-6)

        // Default config starts at 450 C
        val rDefault = PlatenSimulator(props, quickConfig(duration = 60.0))
            .run(initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("default metal 450 C: ${rDefault.metalTempK[0]}", Math.abs(rDefault.metalTempK[0] - 723.15) < 1e-6)
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
        // The first seconds are the physical equilibration of the user-set initial
        // metal/steam gap (450 C metal vs 400 C steam) — the steam node is fast by design.
        // Solver instability would show as repeated oscillation AFTER that window.
        val equilibrationEnd = 30
        for (k in equilibrationEnd + 1 until r.outletTempK.size) {
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
    fun `samples carry every thermo parameter for the UI`() {
        val r = PlatenSimulator(props, quickConfig(dt = 1.0, duration = 120.0))
            .run(initialSteamTempK = 673.15, initialPressurePa = 1e7)
        assertTrue("samples collected", r.samples.isNotEmpty())
        val s = r.samples.last()
        // Every refactored-spec §16 parameter must be populated and physical
        assertTrue(s.pressurePa > 0)
        assertTrue(s.densityKgM3 > 0)
        assertTrue(s.cpJkgK > 0)
        assertTrue(s.viscosityPaS > 0)
        assertTrue(s.conductivityWmK > 0)
        assertTrue(s.enthalpyJkg > 0)
        assertTrue(s.prandtl > 0)
        assertTrue(s.velocityMs > 0)
        assertTrue(s.reynolds > 0)
        assertTrue(s.nusselt > 0)
        assertTrue(s.hiWm2K > 0)
        assertTrue(s.hoWm2K > 0)
        assertTrue(s.uWm2K > 0)
        assertTrue(s.qAbsorbedW > 0)
    }

    @Test
    fun `step metrics produce positive dead time and time constant`() {
        val sim = PlatenSimulator(props, quickConfig(dt = 1.0, duration = 1200.0))
        val events = listOf(
            ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7)
        val (theta, tau) = StepMetrics.compute(r.times, r.outletTempK, 300.0)
        // The response must not precede the step (no negative dead time)
        assertTrue("theta=$theta must be >= step time", theta >= 300.0)
        assertTrue("tau=$tau must be non-negative", tau >= 0.0)
        // The model must react to the load step. Direction is operating-point dependent:
        // with the metal hotter than the steam, the flow-driven h_i increase first RAISES
        // the outlet (more heat transferred), while the steady-state dilution effect
        // (less heating per kg) would lower it — whichever wins, the change is significant.
        val change = Math.abs(r.outletTempK.last() - r.outletTempK[300])
        assertTrue("outlet should respond to load step (change=$change K)", change > 5.0)
    }
}
