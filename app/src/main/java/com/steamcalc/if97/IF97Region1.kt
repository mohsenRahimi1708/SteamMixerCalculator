package com.steamcalc.if97

/**
 * IAPWS-IF97 Region 1: Compressed liquid water
 *
 * The dimensionless Gibbs free energy is:
 *   γ(π,τ) = Σ n_i · (7.1 − π)^I_i · (τ − 1.222)^J_i
 *
 * where π = P / P* (P* = 16.53 MPa), τ = T* / T (T* = 1386 K)
 *
 * Enthalpy: h = R · T* · (∂γ/∂τ)_π
 *
 * R = 0.461526 kJ/(kg·K)
 *
 * Validity: 273.15 K ≤ T ≤ 623.15 K, 0 ≤ P ≤ 100 MPa
 */
object IF97Region1 {

    private const val R = 0.461526  // kJ/(kg·K)
    private const val TSTAR = 1386.0 // K
    private const val PSTAR = 16.53  // MPa

    // 34-term coefficients: n, I, J
    private val n = doubleArrayOf(
        0.14632971213167, -0.84548187169114, -3.756360367204, 0.33855169168385e1,
        -0.95791963387872, 0.15772038513228, -0.16616417199501e-1, 0.81214629983568e-3,
        0.28319080123804e-3, -0.60706301565874e-3, -0.18990068218419e-1, -0.32529748770505e-1,
        -0.21841717175414e-1, -0.52838357969930e-4, -0.47184321073267e-3, -0.30001780793026e-3,
        0.47661393906987e-4, -0.44141845330846e-5, -0.72694996297594e-15, -0.31679644845054e-4,
        -0.28270797985312e-5, -0.85205128120103e-9, -0.22425281908000e-5, -0.65171222895601e-6,
        -0.14341729937924e-12, -0.40516996860117e-6, -0.12734301741641e-8, -0.17424871230634e-9,
        -0.68762131295531e-18, 0.14478307828521e-19, 0.26335781662795e-22, -0.11947622640071e-22,
        0.18228094581404e-23, -0.93537087292458e-25
    )
    private val I = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3,
        3, 3, 4, 4, 4, 5, 8, 8, 21, 23, 29, 30, 31, 32
    )
    private val J = intArrayOf(
        -2, -1, 0, 1, 2, 3, 4, 5, -9, -7, -1, 0, 1, 3, -3, 0, 1, 3, 17, -4,
        0, 6, -5, -2, 10, -8, -11, -6, -29, -31, -38, -39, -40, -41
    )

    /**
     * Compute the dimensionless Gibbs free energy derivative (∂γ/∂τ) at constant π.
     *
     * γ = Σ n_i · (7.1 − π)^I_i · (τ − 1.222)^J_i
     * ∂γ/∂τ = Σ n_i · J_i · (7.1 − π)^I_i · (τ − 1.222)^(J_i − 1)
     */
    fun gammaTau(P_MPa: Double, T_K: Double): Double {
        val pi = P_MPa / PSTAR
        val tau = TSTAR / T_K
        val piShifted = 7.1 - pi
        val tauShifted = tau - 1.222

        var sum = 0.0
        for (i in n.indices) {
            if (J[i] == 0) continue // derivative of constant is zero
            sum += n[i] * J[i] * Math.pow(piShifted, I[i].toDouble()) *
                    Math.pow(tauShifted, (J[i] - 1).toDouble())
        }
        return sum
    }

    /**
     * Compute specific enthalpy h(P, T) in kJ/kg.
     * h = R · T* · (∂γ/∂τ)_π
     */
    fun enthalpy(P_MPa: Double, T_K: Double): Double {
        return R * TSTAR * gammaTau(P_MPa, T_K)
    }

    /**
     * Check if state point (P, T) is in Region 1 validity domain.
     * Region 1: 273.15 K ≤ T ≤ 623.15 K, 0 ≤ P ≤ 100 MPa
     * Additionally, must be on the liquid side of the saturation line.
     */
    fun isValid(P_MPa: Double, T_K: Double): Boolean {
        if (T_K < 273.15 || T_K > 623.15) return false
        if (P_MPa < 0.0 || P_MPa > 100.0) return false
        // Must be liquid (below saturation temperature at this pressure)
        val tSat = IF97Common.saturationTemperature(P_MPa)
        return T_K <= tSat + 0.001 // small tolerance
    }

    /**
     * Compute the dimensionless Gibbs free energy γ.
     * γ = Σ n_i · (7.1 − π)^I_i · (τ − 1.222)^J_i
     */
    fun gamma(P_MPa: Double, T_K: Double): Double {
        val pi = P_MPa / PSTAR
        val tau = TSTAR / T_K
        val piShifted = 7.1 - pi
        val tauShifted = tau - 1.222

        var sum = 0.0
        for (i in n.indices) {
            sum += n[i] * Math.pow(piShifted, I[i].toDouble()) *
                    Math.pow(tauShifted, J[i].toDouble())
        }
        return sum
    }
}
