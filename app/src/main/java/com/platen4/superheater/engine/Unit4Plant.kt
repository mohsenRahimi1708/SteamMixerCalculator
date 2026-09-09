package com.platen4.superheater.engine

import kotlin.math.PI

/**
 * Unit №4 plant reference data — locked geometry, materials and validation targets.
 *
 * Refactored model note: the platen coil is treated as **one uniform tube** (no
 * segmentation) — single material, single metal node, single steam node.
 *
 * Values are inputs to the model, never hard-coded outputs.
 */
object Unit4Plant {

    // ---- Boiler ----
    const val BOILER_CAPACITY_MW = 325.0
    const val BURNER_COUNT = 24
    const val BURNER_RATING_MW = 40.0
    const val FULL_LOAD_STEAM_FLOW_KGS = 1000_000.0 / 3600.0 // 1000 t/h -> kg/s
    const val SLIDING_PRESSURE_MAX_BAR = 167.0
    const val SLIDING_TEMP_MAX_C = 540.0

    // ---- Platen superheater geometry ----
    const val PANELS = 43
    const val TUBES_PER_PANEL = 4
    const val TOTAL_TUBES = PANELS * TUBES_PER_PANEL // 172
    const val PASSES_PER_PANEL = 8
    const val PASS_LENGTH_M = 6.0
    const val TOTAL_TUBE_LENGTH_M = 50.0
    const val TUBE_OD_MM = 57.0
    const val TUBE_WALL_MM = 8.0
    const val TUBE_ID_MM = TUBE_OD_MM - 2 * TUBE_WALL_MM // 41 mm
    const val HEADER_SPLIT_GROUP1_PANELS = 21 // panels 1-21
    const val HEADER_SPLIT_GROUP2_PANELS = 22 // panels 22-43

    // ---- Uniform platen material ----
    const val MATERIAL_NAME = "12Cr2MoWVTiB"
    const val METAL_DENSITY_KG_M3 = 7850.0
    const val METAL_CP_JKG_K = 520.0
    const val METAL_K_WM_K = 32.0

    /** Metal temperature default and range (user input; refactored spec). */
    const val DEFAULT_METAL_TEMP_C = 450.0

    /** Spray-water window: always subcooled and between 100 and 180 °C. */
    const val SPRAY_MIN_TEMP_C = 100.0
    const val SPRAY_MAX_TEMP_C = 180.0
    const val DEFAULT_SPRAY_TEMP_C = 150.0

    // ---- Reference validation targets (model must reproduce, not be forced to) ----
    const val REF_METAL_HEADER_CAPACITY_MJ_K = 36.3
    const val REF_STEAM_CAPACITY_MJ_K = 1.35
    const val REF_TAU_20PCT_S = 66.0
    const val REF_THETA_20PCT_S = 88.0
    const val REF_U_LOW_LOAD_WM2K = 800.0 // plant YAML — unresolved vs Dittus-Boelter
    const val REF_U_FULL_LOAD_WM2K = 170.0 // Dittus-Boelter derived

    /** Steam-side flow area of a single tube [m2]. */
    fun tubeFlowAreaM2(): Double = PI * (TUBE_ID_MM / 1000.0).pow2() / 4.0

    /** Total steam-side flow area [m2] — 172 parallel tubes. */
    fun totalFlowAreaM2(): Double = tubeFlowAreaM2() * TOTAL_TUBES

    /** Total tube inner (steam-side) area from geometry [m2]. */
    fun innerAreaM2(): Double = PI * (TUBE_ID_MM / 1000.0) * TOTAL_TUBE_LENGTH_M * TOTAL_TUBES

    /** Total tube outer (furnace-side) area from geometry [m2]. */
    fun outerAreaM2(): Double = PI * (TUBE_OD_MM / 1000.0) * TOTAL_TUBE_LENGTH_M * TOTAL_TUBES

    /** Metal wall cross-section of one tube [m2]. */
    fun wallCrossSectionM2(): Double = PI * ((TUBE_OD_MM / 1000.0).pow2() - (TUBE_ID_MM / 1000.0).pow2()) / 4.0

    /** Total tube metal mass from geometry and uniform material [kg]. */
    fun totalMetalMassKg(): Double = wallCrossSectionM2() * TOTAL_TUBE_LENGTH_M * TOTAL_TUBES * METAL_DENSITY_KG_M3

    /** Total internal steam volume [m3]. */
    fun steamVolumeM3(): Double = tubeFlowAreaM2() * TOTAL_TUBE_LENGTH_M * TOTAL_TUBES

    private fun Double.pow2(): Double = this * this
}
