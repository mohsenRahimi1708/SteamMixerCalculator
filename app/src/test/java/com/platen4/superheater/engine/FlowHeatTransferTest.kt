package com.platen4.superheater.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §22 tests 2-4 — flow calculation, Re->Pr->Nu->h_i chain, and
 * monotonic increase of all heat-transfer quantities with load.
 * Geometry: refactored spec — uniform tube OD 57 mm / ID 41 mm, 43 panels × 4 tubes.
 */
class FlowHeatTransferTest {

    private val props: SteamProperties = If97SteamProperties()

    // Full-load reference state: 167 bar, 520 C (guide worked example)
    private val pFull = 16.7e6
    private val tFull = 793.15
    private val rhoFull = props.densityPT(pFull, tFull)
    private val muFull = props.viscosityPT(pFull, tFull)
    private val tubeIDM = Unit4Plant.TUBE_ID_MM / 1000.0

    @Test
    fun `velocity and Reynolds at full load are physically plausible`() {
        // 1000 t/h = 277.78 kg/s across 43 panels x 4 tubes
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)

        // Hand check: tube area = pi/4 * 0.041^2 = 1.3203e-3 m2
        val area = Math.PI * tubeIDM * tubeIDM / 4.0
        val expectedV = (277.78 / 43 / 4) / (rhoFull * area)
        assertTrue("velocity ${flow.tubeVelocityMs} vs $expectedV", Math.abs(flow.tubeVelocityMs - expectedV) < 1e-9)
        // Superheated steam at 167 bar / 520 C is ~52 kg/m3; with the larger 41 mm ID
        // the tube velocity is ~20-35 m/s — physically correct for superheated steam.
        assertTrue("velocity ${flow.tubeVelocityMs} should be 10-45 m/s", flow.tubeVelocityMs in 10.0..45.0)

        val expectedRe = rhoFull * expectedV * tubeIDM / muFull
        assertTrue("Re ${flow.reynolds} ~ $expectedRe", Math.abs(flow.reynolds - expectedRe) < 1e-6)
        assertTrue("Re ${flow.reynolds} should be > 1e5 at full load", flow.reynolds > 1e5)
    }

    @Test
    fun `geometry constants match refactored spec`() {
        assertTrue("OD 57 mm", Unit4Plant.TUBE_OD_MM == 57.0)
        assertTrue("ID 41 mm", Unit4Plant.TUBE_ID_MM == 41.0)
        assertTrue("wall 8 mm", Unit4Plant.TUBE_WALL_MM == 8.0)
        assertTrue("single material 12Cr2MoWVTiB", Unit4Plant.MATERIAL_NAME == "12Cr2MoWVTiB")
        // Metal mass from the new geometry (pi/4*(57^2-41^2) mm2 * 50 m * 172 * 7850)
        val massT = Unit4Plant.totalMetalMassKg() / 1000.0
        assertTrue("metal mass $massT t should be 80-95 t for 57/41 mm", massT in 80.0..95.0)
        // Spray window
        assertTrue(Unit4Plant.SPRAY_MIN_TEMP_C == 100.0)
        assertTrue(Unit4Plant.SPRAY_MAX_TEMP_C == 180.0)
        assertTrue(Unit4Plant.DEFAULT_METAL_TEMP_C == 450.0)
    }

    @Test
    fun `heat transfer chain Re to hi is consistent end-to-end`() {
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)
        val cp = props.cpPT(pFull, tFull)
        val k = props.conductivityPT(pFull, tFull)
        val pr = props.prandtlPT(pFull, tFull)

        val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rhoFull, muFull, cp, k)

        // Nu = 0.023 Re^0.8 Pr^0.4
        val expectedNu = 0.023 * Math.pow(flow.reynolds, 0.8) * Math.pow(pr, 0.4)
        assertTrue("Nu ${ht.nusselt} vs $expectedNu", Math.abs(ht.nusselt - expectedNu) < 1e-9)
        // h_i = Nu*k/D
        assertTrue("hi ${ht.hiWm2K}", Math.abs(ht.hiWm2K - expectedNu * k / tubeIDM) < 1e-6)
        // With the 41 mm ID h_i lands around 2000-5000 W/m2K for this state.
        assertTrue("hi ${ht.hiWm2K} should be 1500-8000", ht.hiWm2K in 1500.0..8000.0)
        assertTrue("no validity warnings at full load", ht.validityWarnings.isEmpty())
    }

    @Test
    fun `overall U from resistance chain is between 90 and 220 at full load`() {
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)
        val cp = props.cpPT(pFull, tFull)
        val k = props.conductivityPT(pFull, tFull)
        val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rhoFull, muFull, cp, k)

        val u = OverallU.compute(ht.hiWm2K, 150.0, Unit4Plant.TUBE_OD_MM / 1000.0, tubeIDM, 1.0, Unit4Plant.METAL_K_WM_K, 0.0)
        // With h_i ~ 3000 and h_o = 150, external convection dominates U (~140 W/m2K)
        assertTrue("U ${u.uWm2K} should be ~90-220", u.uWm2K in 90.0..220.0)
        assertTrue("external convection should dominate (h_o=150 << h_i)", u.dominantTerm == "external convection")
    }

    @Test
    fun `velocity Re Nu hi and U all increase monotonically with load`() {
        val loads = doubleArrayOf(0.2, 0.35, 0.5, 0.7, 1.0)
        var prevV = 0.0; var prevRe = 0.0; var prevNu = 0.0; var prevHi = 0.0; var prevU = 0.0

        for (load in loads) {
            val flow = FlowModel.compute(277.78 * load, Unit4Plant.PANELS, rhoFull, muFull)
            val cp = props.cpPT(pFull, tFull)
            val k = props.conductivityPT(pFull, tFull)
            val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, tubeIDM, rhoFull, muFull, cp, k)
            val u = OverallU.compute(ht.hiWm2K, 150.0, Unit4Plant.TUBE_OD_MM / 1000.0, tubeIDM, 1.0, Unit4Plant.METAL_K_WM_K, 0.0)

            assertTrue("v increases", flow.tubeVelocityMs > prevV); prevV = flow.tubeVelocityMs
            assertTrue("Re increases", flow.reynolds > prevRe); prevRe = flow.reynolds
            assertTrue("Nu increases", ht.nusselt > prevNu); prevNu = ht.nusselt
            assertTrue("hi increases", ht.hiWm2K > prevHi); prevHi = ht.hiWm2K
            assertTrue("U increases", u.uWm2K > prevU); prevU = u.uWm2K
        }
    }
}
