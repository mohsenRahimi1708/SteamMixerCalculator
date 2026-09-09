package com.platen4.superheater.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenario timeline (arbitrary multi-event editor) — parsing, validation, unit
 * conversion, and engine behavior with multiple events per quantity.
 */
class ScenarioTimelineTest {

    private val props: SteamProperties = If97SteamProperties()

    // ---- Unit conversion ----

    @Test
    fun `display values convert to engine units`() {
        assertEquals(450.0 / 3.6, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.STEAM_FLOW_KGS, 450.0), 1e-9)
        assertEquals(5.0 / 3.6, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0), 1e-9)
        assertEquals(430.0 + 273.15, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.STEAM_TEMP_K, 430.0), 1e-9)
        assertEquals(120.0 * 1e5, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.STEAM_PRESSURE_PA, 120.0), 1e-6)
        assertEquals(0.8, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.FIRING_FRACTION, 0.8), 1e-12)
        assertEquals(4.0, ScenarioTimeline.toEngineValue(ScenarioEvent.Kind.BURNERS_FIRING, 4.0), 1e-12)
    }

    // ---- Validation ----

    @Test
    fun `validation rejects bad rows with readable errors`() {
        val badTime = EventRow(1, ScenarioEvent.Kind.STEAM_FLOW_KGS, timeText = "-5", valueText = "450")
        assertTrue(ScenarioTimeline.validate(badTime)!!.contains("time"))

        val badValue = EventRow(2, ScenarioEvent.Kind.STEAM_FLOW_KGS, timeText = "300", valueText = "abc")
        assertTrue(ScenarioTimeline.validate(badValue)!!.contains("number"))

        val zeroFlow = EventRow(3, ScenarioEvent.Kind.STEAM_FLOW_KGS, timeText = "300", valueText = "0")
        assertTrue(ScenarioTimeline.validate(zeroFlow)!!.contains("positive"))

        val tooManyBurners = EventRow(4, ScenarioEvent.Kind.BURNERS_FIRING, timeText = "300", valueText = "30")
        assertTrue(ScenarioTimeline.validate(tooManyBurners)!!.contains("0..24"))

        val badFraction = EventRow(5, ScenarioEvent.Kind.FIRING_FRACTION, timeText = "300", valueText = "1.5")
        assertTrue(ScenarioTimeline.validate(badFraction)!!.contains("0..1"))

        // Valid rows pass
        val ok = EventRow(6, ScenarioEvent.Kind.SPRAY_FLOW_KGS, timeText = "400", valueText = "5")
        assertEquals(null, ScenarioTimeline.validate(ok))
    }

    @Test
    fun `parse keeps only enabled and valid rows converted to SI`() {
        val rows = listOf(
            EventRow(1, ScenarioEvent.Kind.STEAM_FLOW_KGS, enabled = true, timeText = "300", valueText = "450"),
            EventRow(2, ScenarioEvent.Kind.SPRAY_FLOW_KGS, enabled = false, timeText = "400", valueText = "5"),
            EventRow(3, ScenarioEvent.Kind.STEAM_TEMP_K, enabled = true, timeText = "300", valueText = "abc"), // invalid
        )
        val parsed = ScenarioTimeline.parse(rows)
        assertEquals(1, parsed.events.size)
        assertEquals(1, parsed.invalidRows.size)
        assertEquals(450.0 / 3.6, parsed.events[0].value, 1e-9)
        assertEquals(300.0, parsed.events[0].timeSeconds, 1e-9)
    }

    // ---- Engine behavior with multiple events per quantity ----

    @Test
    fun `multiple events per quantity apply at their own times`() {
        val sim = PlatenSimulator(props, PlatenSimulator.Config(dtSeconds = 1.0, durationSeconds = 700.0))
        // Two steam-flow events on the same quantity at different times
        val events = listOf(
            ScenarioEvent(200.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
            ScenarioEvent(500.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 250.0 / 3.6),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7, initialSteamFlowKgs = 100.0 / 3.6)
        assertTrue("error: ${r.error}", r.error == null)

        assertEquals("before first event", 100.0 / 3.6, r.steamFlowKgs[100], 1e-9)
        assertEquals("after first event", 150.0 / 3.6, r.steamFlowKgs[300], 1e-9)
        assertEquals("after second event", 250.0 / 3.6, r.steamFlowKgs[600], 1e-9)
    }

    @Test
    fun `mixed multi-event timeline stays stable and applies all kinds`() {
        val sim = PlatenSimulator(props, PlatenSimulator.Config(dtSeconds = 1.0, durationSeconds = 800.0))
        val events = listOf(
            ScenarioEvent(200.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 450.0 / 3.6),
            ScenarioEvent(300.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, 5.0 / 3.6),
            ScenarioEvent(400.0, ScenarioEvent.Kind.FIRING_FRACTION, 0.8),
            ScenarioEvent(500.0, ScenarioEvent.Kind.BURNERS_FIRING, 3.0),
            ScenarioEvent(600.0, ScenarioEvent.Kind.STEAM_PRESSURE_PA, 120.0 * 1e5),
        )
        val r = sim.run(events, initialSteamTempK = 673.15, initialPressurePa = 1e7, initialSteamFlowKgs = 300.0 / 3.6)
        assertTrue("error: ${r.error}", r.error == null)
        for (t in r.outletTempK) {
            assertTrue("outlet $t physical", t in 300.0..900.0 && !t.isNaN())
        }
        // All kinds actually applied
        assertEquals(450.0 / 3.6, r.steamFlowKgs[250], 1e-9)
        assertEquals(5.0 / 3.6, r.sprayFlowKgs[350], 1e-9)
        assertEquals(120.0 * 1e5, r.pressurePa[650], 1e3)
        assertTrue("burner heat ramps after burner event", r.qPlatenW[700] > r.qPlatenW[450])
    }

    @Test
    fun `firing fraction reduces burner heat`() {
        val sim = PlatenSimulator(props, PlatenSimulator.Config(dtSeconds = 1.0, durationSeconds = 900.0))
        val full = sim.run(emptyList(), initialSteamTempK = 673.15, initialPressurePa = 1e7)
        val reduced = sim.run(
            listOf(ScenarioEvent(0.0, ScenarioEvent.Kind.FIRING_FRACTION, 0.5)),
            initialSteamTempK = 673.15,
            initialPressurePa = 1e7,
        )
        assertTrue("fraction 0.5 halves heat: ${reduced.qPlatenW[800]} vs ${full.qPlatenW[800]}",
            Math.abs(reduced.qPlatenW[800] - 0.5 * full.qPlatenW[800]) < 1e-3)
    }
}
