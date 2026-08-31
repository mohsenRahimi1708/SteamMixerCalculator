package com.steamcalc.if97

/**
 * IAPWS-IF97 common utilities: saturation temperature, saturation pressure,
 * and shared constants.
 *
 * The saturation temperature/pressure equations are from IAPWS-IF97 Eq. 30-31.
 */
object IF97Common {

    private const val TC = 647.096    // Critical temperature, K
    private const val PC = 22.064     // Critical pressure, MPa

    // Coefficients for saturation equations (IAPWS-IF97 Eq. 30, 31)
    private val satN = doubleArrayOf(
        0.0,
        0.11670521452767e4,
        -0.72421316703206e6,
        -0.17073846940092e2,
        0.12020824702470e5,
        -0.32325550322333e7,
        0.14915108613530e2,
        -0.48232657361591e4,
        0.40511340542057e6,
        -0.23855557567849e0,
        0.65017534844798e3
    )

    /**
     * Compute saturation temperature T_sat(P) in K.
     * IAPWS-IF97 Eq. 31.
     *
     * Validity: 611.213 Pa ≤ P ≤ 22.064 MPa
     */
    fun saturationTemperature(P_MPa: Double): Double {
        if (P_MPa < 6.11213e-4 || P_MPa > 22.064) {
            // Fallback for extreme values
            return if (P_MPa <= 22.064) 273.15 else TC
        }

        val beta = Math.pow(P_MPa, 0.25)
        val beta2 = beta * beta

        val E = beta2 + satN[3] * beta + satN[6]
        val F = satN[1] * beta2 + satN[4] * beta + satN[7]
        val G = satN[2] * beta2 + satN[5] * beta + satN[8]

        val D = 2.0 * G / (-F - Math.sqrt(F * F - 4.0 * E * G))

        val dPlusN10 = D + satN[10]
        return (satN[10] + D - Math.sqrt(dPlusN10 * dPlusN10 - 4.0 * (satN[9] + satN[10] * D))) / 2.0
    }

    /**
     * Compute saturation pressure P_sat(T) in MPa.
     * IAPWS-IF97 Eq. 30.
     *
     * Validity: 273.15 K ≤ T ≤ 647.096 K
     */
    fun saturationPressure(T_K: Double): Double {
        if (T_K < 273.15 || T_K > TC) {
            return if (T_K <= TC) 6.11213e-4 else PC
        }

        val tita = T_K + satN[9] / (T_K - satN[10])
        val tita2 = tita * tita

        val A = tita2 + satN[1] * tita + satN[2]
        val B = satN[3] * tita2 + satN[4] * tita + satN[5]
        val C = satN[6] * tita2 + satN[7] * tita + satN[8]

        return Math.pow(2.0 * C / (-B + Math.sqrt(B * B - 4.0 * A * C)), 4.0)
    }
}
