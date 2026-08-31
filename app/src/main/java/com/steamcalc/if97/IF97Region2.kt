package com.steamcalc.if97

/**
 * IAPWS-IF97 Region 2: Superheated steam
 *
 * γ(π,τ) = γ°(π,τ) + γ^r(π,τ)
 *
 * Ideal gas part:
 *   γ° = ln(π) + Σ n°_i · τ^(J°_i)
 *   where π = P / 1 MPa, τ = 540 / T (K)
 *
 * Residual part:
 *   γ^r = Σ n_i · π^I_i · (τ − 0.5)^J_i
 *
 * Enthalpy: h = R · 540 · (∂γ/∂τ)_π
 *
 * R = 0.461526 kJ/(kg·K)
 *
 * Validity: 273.15 K ≤ T ≤ 1073.15 K, 0 ≤ P ≤ 100 MPa
 */
object IF97Region2 {

    private const val R = 0.461526   // kJ/(kg·K)
    private const val TSTAR = 540.0  // K
    private const val PSTAR = 1.0    // MPa

    // === Ideal gas part: γ° = ln(π) + Σ n°_i · τ^(J°_i) ===
    // J° = [0, 1, -5, -4, -3, -2, -1, 2, 3]
    private val no = doubleArrayOf(
        -0.96927686500217e1,  0.10086655968018e2,
        -0.56087911283020e-2, 0.71452738081455e-1,
        -0.40710498223928e0,  0.14240819171444e1,
        -0.43839511319450e1,  -0.28408632460772e0,
        0.21268463753307e-1
    )
    private val Jo = intArrayOf(0, 1, -5, -4, -3, -2, -1, 2, 3)

    // === Residual part: γ^r = Σ n_i · π^I_i · (τ − 0.5)^J_i ===
    private val nr = doubleArrayOf(
        -0.0017731742473212999, -0.017834862292357999, -0.045996013696365003,
        -0.057581259083432, -0.050325278727930002, -3.3032641670203e-05,
        -0.00018948987516315, -0.0039392777243355001, -0.043797295650572998,
        -2.6674547914087001e-05, 2.0481737692308999e-08, 4.3870667284435001e-07,
        -3.2277677238570002e-05, -0.0015033924542148, -0.040668253562648998,
        -7.8847309559367001e-10, 1.2790717852285001e-08, 4.8225372718507002e-07,
        2.2922076337661001e-06, -1.6714766451061001e-11, -0.0021171472321354998,
        -23.895741934103999, -5.9059564324270004e-18, -1.2621808899101e-06,
        -0.038946842435739003, 1.1256211360459e-11, -8.2311340897998004,
        1.9809712802088e-08, 1.0406965210174e-19, -1.0234747095929e-13,
        -1.0018179379511e-09, -8.0882908646984998e-11, 0.10693031879409,
        -0.33662250574170999, 8.9185845355420999e-25, 3.0629316876231997e-13,
        -4.2002467698208001e-06, -5.9056029685639003e-26, 3.7826947613457002e-06,
        -1.2768608934681e-15, 7.3087610595061e-29, 5.5414715350778001e-17,
        -9.4369707241209998e-07
    )
    private val Ir = intArrayOf(
        1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 5, 6,
        6, 6, 7, 7, 7, 8, 8, 9, 10, 10, 10, 16, 16, 18, 20, 20, 20,
        21, 22, 23, 24, 24, 24
    )
    private val Jr = intArrayOf(
        0, 1, 2, 3, 6, 1, 2, 4, 7, 36, 0, 1, 3, 6, 35, 1, 2, 3, 7, 3,
        16, 35, 0, 11, 25, 8, 36, 13, 4, 10, 14, 29, 50, 57, 20, 35, 48,
        21, 53, 39, 26, 40, 58
    )

    /**
     * Compute (∂γ°/∂τ) for the ideal gas part.
     * γ° = ln(π) + Σ n°_i · τ^(J°_i)
     * ∂γ°/∂τ = Σ n°_i · J°_i · τ^(J°_i − 1)
     */
    private fun gammaOTau(tau: Double): Double {
        var sum = 0.0
        for (i in no.indices) {
            if (Jo[i] == 0) continue
            sum += no[i] * Jo[i] * Math.pow(tau, (Jo[i] - 1).toDouble())
        }
        return sum
    }

    /**
     * Compute (∂γ^r/∂τ) for the residual part at constant π.
     * γ^r = Σ n_i · π^I_i · (τ − 0.5)^J_i
     * ∂γ^r/∂τ = Σ n_i · J_i · π^I_i · (τ − 0.5)^(J_i − 1)
     */
    private fun gammaRTau(pi: Double, tau: Double): Double {
        val tauShifted = tau - 0.5
        var sum = 0.0
        for (i in nr.indices) {
            if (Jr[i] == 0) continue
            sum += nr[i] * Jr[i] * Math.pow(pi, Ir[i].toDouble()) *
                    Math.pow(tauShifted, (Jr[i] - 1).toDouble())
        }
        return sum
    }

    /**
     * Compute specific enthalpy h(P, T) in kJ/kg.
     * h = R · T* · [(∂γ°/∂τ) + (∂γ^r/∂τ)]
     */
    fun enthalpy(P_MPa: Double, T_K: Double): Double {
        val pi = P_MPa / PSTAR
        val tau = TSTAR / T_K
        val got = gammaOTau(tau)
        val grt = gammaRTau(pi, tau)
        return R * TSTAR * (got + grt)
    }

    /**
     * Check if state point (P, T) is in Region 2 validity domain.
     * Region 2: T > T_sat(P), T ≤ 1073.15 K, P ≤ 100 MPa
     */
    fun isValid(P_MPa: Double, T_K: Double): Boolean {
        if (P_MPa <= 0.0 || P_MPa > 100.0) return false
        if (T_K < 273.15 || T_K > 1073.15) return false
        // Must be above saturation temperature (superheated)
        val tSat = IF97Common.saturationTemperature(P_MPa)
        return T_K >= tSat - 0.001 // small tolerance
    }

    /**
     * Compute the ideal gas part of dimensionless Gibbs energy.
     * γ° = ln(π) + Σ n°_i · τ^(J°_i)
     */
    fun gammaO(pi: Double, tau: Double): Double {
        var sum = ln(pi)
        for (i in no.indices) {
            sum += no[i] * Math.pow(tau, Jo[i].toDouble())
        }
        return sum
    }

    /**
     * Compute the residual part of dimensionless Gibbs energy.
     * γ^r = Σ n_i · π^I_i · (τ − 0.5)^J_i
     */
    fun gammaR(pi: Double, tau: Double): Double {
        val tauShifted = tau - 0.5
        var sum = 0.0
        for (i in nr.indices) {
            sum += nr[i] * Math.pow(pi, Ir[i].toDouble()) *
                    Math.pow(tauShifted, Jr[i].toDouble())
        }
        return sum
    }

    private fun ln(x: Double): Double = Math.log(x)
}
