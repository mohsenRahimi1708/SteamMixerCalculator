package com.platen4.superheater.engine

/**
 * Unit №4 plant reference data (spec §3) — locked geometry, materials and
 * validation targets. Values are inputs to the model, never hard-coded outputs.
 */
object Unit4Plant {

    // ---- Boiler (§3.1) ----
    const val BOILER_CAPACITY_MW = 325.0
    const val BURNER_COUNT = 24
    const val BURNER_RATING_MW = 40.0
    const val FULL_LOAD_STEAM_FLOW_KGS = 1000_000.0 / 3600.0 // 1000 t/h -> kg/s
    const val SLIDING_PRESSURE_MAX_BAR = 167.0
    const val SLIDING_TEMP_MAX_C = 540.0

    // ---- Platen superheater geometry (§3.2) ----
    const val PANELS = 43
    const val TUBES_PER_PANEL = 4
    const val TOTAL_TUBES = PANELS * TUBES_PER_PANEL // 172
    const val PASSES_PER_PANEL = 8
    const val PASS_LENGTH_M = 6.0
    const val TOTAL_TUBE_LENGTH_M = 50.0
    const val TUBE_OD_MM = 45.0
    const val TUBE_WALL_MM = 8.0
    const val TUBE_ID_MM = TUBE_OD_MM - 2 * TUBE_WALL_MM // 29 mm
    const val INNER_AREA_M2 = 783.0 // steam-side
    const val OUTER_AREA_M2 = 1216.0 // furnace-side
    const val HEADER_SPLIT_GROUP1_PANELS = 21 // panels 1-21
    const val HEADER_SPLIT_GROUP2_PANELS = 22 // panels 22-43

    // ---- Section materials (flow order, §3.3) ----
    data class Section(
        val name: String,
        val lengthM: Double,
        val material: String,
        val densityKgM3: Double,
        val cpJkgK: Double,
        val kWmK: Double,
    )

    val SECTIONS = listOf(
        Section("Inlet casing", 0.5, "12Cr1MoV", 7850.0, 500.0, 35.0),
        Section("Lower radiant", 15.0, "SA-213 T91", 7750.0, 550.0, 28.0),
        Section("Inner horizontal", 20.0, "12Cr2MoWVTiB", 7850.0, 520.0, 32.0),
        Section("Upper horizontal", 12.0, "12Cr2MoWVTiB", 7850.0, 520.0, 32.0),
        Section("Outlet casing", 2.5, "12Cr1MoV", 7850.0, 500.0, 35.0),
    )

    // ---- Reference validation targets (model must reproduce, not be forced to) ----
    const val REF_TUBE_METAL_MASS_T = 62.5
    const val REF_METAL_HEADER_CAPACITY_MJ_K = 36.3
    const val REF_STEAM_CAPACITY_MJ_K = 1.35
    const val REF_TAU_20PCT_S = 66.0
    const val REF_THETA_20PCT_S = 88.0
    const val REF_U_LOW_LOAD_WM2K = 800.0 // plant YAML — unresolved vs Dittus-Boelter
    const val REF_U_FULL_LOAD_WM2K = 170.0 // Dittus-Boelter derived

    /** Steam-side flow area of a single tube [m2]. */
    fun tubeFlowAreaM2(): Double = Math.PI * (TUBE_ID_MM / 1000.0).pow2() / 4.0

    /** Total steam-side flow area [m2] — 172 parallel tubes. */
    fun totalFlowAreaM2(): Double = tubeFlowAreaM2() * TOTAL_TUBES

    private fun Double.pow2(): Double = this * this
}