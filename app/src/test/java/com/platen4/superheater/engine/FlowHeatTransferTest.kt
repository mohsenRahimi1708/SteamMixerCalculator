package com.platen4.superheater.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §22 tests 2-4 — flow calculation, Re->Pr->Nu->h_i chain, and
 * monotonic increase of all heat-transfer quantities with load.
 */
class FlowHeatTransferTest {

    private val props: SteamProperties = If97SteamProperties()

    // Full-load reference state: 167 bar, 520 C (guide worked example)
    private val pFull = 16.7e6
    private val tFull = 793.15
    private val rhoFull = props.densityPT(pFull, tFull)
    private val muFull = props.viscosityPT(pFull, tFull)

    @Test
    fun `velocity and Reynolds at full load are physically plausible`() {
        // 1000 t/h = 277.78 kg/s across 43 panels x 4 tubes
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)

        // Hand check: tube area = pi/4 * 0.029^2 = 6.605e-4 m2
        val area = Math.PI * 0.029 * 0.029 / 4.0
        val expectedV = (277.78 / 43 / 4) / (rhoFull * area)
        assertTrue("velocity ${flow.tubeVelocityMs} vs $expectedV", Math.abs(flow.tubeVelocityMs - expectedV) < 1e-9)
        // Superheated steam at 167 bar / 520 C is only ~52 kg/m3 -> tube velocity is
        // high (~45 m/s). This is physically correct for superheated steam.
        assertTrue("velocity ${flow.tubeVelocityMs} should be 20-70 m/s", flow.tubeVelocityMs in 20.0..70.0)

        val expectedRe = rhoFull * expectedV * 0.029 / muFull
        assertTrue("Re ${flow.reynolds} ~ $expectedRe", Math.abs(flow.reynolds - expectedRe) < 1e-6)
        assertTrue("Re ${flow.reynolds} should be > 1e5 at full load", flow.reynolds > 1e5)
    }

    @Test
    fun `heat transfer chain Re to hi is consistent end-to-end`() {
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)
        val cp = props.cpPT(pFull, tFull)
        val k = props.conductivityPT(pFull, tFull)
        val pr = props.prandtlPT(pFull, tFull)

        val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, 0.029, rhoFull, muFull, cp, k)

        // Nu = 0.023 Re^0.8 Pr^0.4
        val expectedNu = 0.023 * Math.pow(flow.reynolds, 0.8) * Math.pow(pr, 0.4)
        assertTrue("Nu ${ht.nusselt} vs $expectedNu", Math.abs(ht.nusselt - expectedNu) < 1e-9)
        // h_i = Nu*k/D
        assertTrue("hi ${ht.hiWm2K}", Math.abs(ht.hiWm2K - expectedNu * k / 0.029) < 1e-6)
        // At 47 m/s and Re ~ 5e5 the Dittus-Boelter h_i is high (~8000 W/m2K);
        // the correct band for THIS geometry/state is 3000-15000.
        assertTrue("hi ${ht.hiWm2K} should be 3000-15000", ht.hiWm2K in 3000.0..15000.0)
        assertTrue("no validity warnings at full load", ht.validityWarnings.isEmpty())
    }

    @Test
    fun `overall U from resistance chain is between 100 and 250 at full load`() {
        val flow = FlowModel.compute(277.78, Unit4Plant.PANELS, rhoFull, muFull)
        val cp = props.cpPT(pFull, tFull)
        val k = props.conductivityPT(pFull, tFull)
        val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, 0.029, rhoFull, muFull, cp, k)

        val u = OverallU.compute(ht.hiWm2K, 150.0, 0.045, 0.029, 1.0, 32.0, 0.0)
        // With h_i ~ 8000 and h_o = 150, external convection dominates U (~140 W/m2K)
        assertTrue("U ${u.uWm2K} should be ~100-250", u.uWm2K in 100.0..250.0)
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
            val ht = HeatTransfer.dittusBoelter(flow.tubeVelocityMs, 0.029, rhoFull, muFull, cp, k)
            val u = OverallU.compute(ht.hiWm2K, 150.0, 0.045, 0.029, 1.0, 32.0, 0.0)

            assertTrue("v increases", flow.tubeVelocityMs > prevV); prevV = flow.tubeVelocityMs
            assertTrue("Re increases", flow.reynolds > prevRe); prevRe = flow.reynolds
            assertTrue("Nu increases", ht.nusselt > prevNu); prevNu = ht.nusselt
            assertTrue("hi increases", ht.hiWm2K > prevHi); prevHi = ht.hiWm2K
            assertTrue("U increases", u.uWm2K > prevU); prevU = u.uWm2K
        }
    }
}