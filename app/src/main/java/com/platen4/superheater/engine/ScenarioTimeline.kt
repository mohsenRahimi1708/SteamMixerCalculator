package com.platen4.superheater.engine

/**
 * UI-facing scenario timeline — arbitrary multi-event timelines (refactored spec §15).
 *
 * An [EventRow] holds the values exactly as typed by the user, in display units
 * (t/h, bar, °C, count, fraction). Parsing, validation and conversion to engine
 * [ScenarioEvent]s happen here in pure Kotlin so the whole timeline pipeline is
 * unit-testable without Android.
 *
 * The engine accepts arbitrary event lists: any number of events per quantity,
 * each with its own time — the step/ramp interpolators resolve them in time order.
 * The `enabled` flag (per event) lets the user simulate a single scenario or any mix.
 */
data class EventRow(
    val id: Long,
    val kind: ScenarioEvent.Kind,
    val enabled: Boolean = true,
    val timeText: String = "300",
    val valueText: String = "",
)

object ScenarioTimeline {

    /** Display unit for a kind's value, as shown in the editor. */
    fun displayUnit(kind: ScenarioEvent.Kind): String = when (kind) {
        ScenarioEvent.Kind.STEAM_FLOW_KGS -> "t/h"
        ScenarioEvent.Kind.SPRAY_FLOW_KGS -> "t/h"
        ScenarioEvent.Kind.SPRAY_TEMP_K -> "°C"
        ScenarioEvent.Kind.BURNERS_FIRING -> "count"
        ScenarioEvent.Kind.FIRING_FRACTION -> "0–1"
        ScenarioEvent.Kind.STEAM_PRESSURE_PA -> "bar"
        ScenarioEvent.Kind.STEAM_TEMP_K -> "°C"
    }

    /** Human label for a kind, as shown in the editor. */
    fun displayLabel(kind: ScenarioEvent.Kind): String = when (kind) {
        ScenarioEvent.Kind.STEAM_FLOW_KGS -> "Steam flow"
        ScenarioEvent.Kind.SPRAY_FLOW_KGS -> "Spray flow"
        ScenarioEvent.Kind.SPRAY_TEMP_K -> "Spray temperature"
        ScenarioEvent.Kind.BURNERS_FIRING -> "Burners firing"
        ScenarioEvent.Kind.FIRING_FRACTION -> "Burner firing fraction"
        ScenarioEvent.Kind.STEAM_PRESSURE_PA -> "Steam pressure"
        ScenarioEvent.Kind.STEAM_TEMP_K -> "Steam temperature"
    }

    /** Convert a user-entered display value to the engine unit for [kind]. */
    fun toEngineValue(kind: ScenarioEvent.Kind, displayValue: Double): Double = when (kind) {
        ScenarioEvent.Kind.STEAM_FLOW_KGS, ScenarioEvent.Kind.SPRAY_FLOW_KGS -> displayValue / 3.6 // t/h -> kg/s
        ScenarioEvent.Kind.SPRAY_TEMP_K, ScenarioEvent.Kind.STEAM_TEMP_K -> displayValue + 273.15  // °C -> K
        ScenarioEvent.Kind.STEAM_PRESSURE_PA -> displayValue * 1e5                                  // bar -> Pa
        else -> displayValue
    }

    /** Sensible new-row default value (display units) for [kind]. */
    fun defaultDisplayValue(kind: ScenarioEvent.Kind): String = when (kind) {
        ScenarioEvent.Kind.STEAM_FLOW_KGS -> "450"
        ScenarioEvent.Kind.SPRAY_FLOW_KGS -> "5"
        ScenarioEvent.Kind.SPRAY_TEMP_K -> "150"
        ScenarioEvent.Kind.BURNERS_FIRING -> "3"
        ScenarioEvent.Kind.FIRING_FRACTION -> "1"
        ScenarioEvent.Kind.STEAM_PRESSURE_PA -> "120"
        ScenarioEvent.Kind.STEAM_TEMP_K -> "430"
    }

    /** A new editable row for [kind]. */
    fun newRow(id: Long, kind: ScenarioEvent.Kind): EventRow = EventRow(
        id = id,
        kind = kind,
        enabled = true,
        timeText = "300",
        valueText = defaultDisplayValue(kind),
    )

    /** Validate one row; returns an engineering-readable error or null when valid. */
    fun validate(row: EventRow): String? {
        val t = row.timeText.toDoubleOrNull()
        if (t == null || t < 0.0) return "Event time must be a number ≥ 0 (seconds)"
        val v = row.valueText.toDoubleOrNull() ?: return "Value must be a number (${displayUnit(row.kind)})"
        return when (row.kind) {
            ScenarioEvent.Kind.STEAM_FLOW_KGS -> if (v <= 0.0) "Steam flow must be positive" else null
            ScenarioEvent.Kind.SPRAY_FLOW_KGS -> if (v < 0.0) "Spray flow must be ≥ 0" else null
            ScenarioEvent.Kind.BURNERS_FIRING ->
                if (v < 0.0 || v > Unit4Plant.BURNER_COUNT) "Burners must be 0..${Unit4Plant.BURNER_COUNT.toInt()}" else null
            ScenarioEvent.Kind.FIRING_FRACTION -> if (v < 0.0 || v > 1.0) "Firing fraction must be 0..1" else null
            ScenarioEvent.Kind.STEAM_PRESSURE_PA -> if (v <= 0.0) "Pressure must be positive" else null
            else -> null
        }
    }

    data class Parsed(val events: List<ScenarioEvent>, val invalidRows: List<EventRow>)

    /** Parse all rows; enabled + valid rows become engine events (converted to SI). */
    fun parse(rows: List<EventRow>): Parsed {
        val events = ArrayList<ScenarioEvent>()
        val invalid = ArrayList<EventRow>()
        for (row in rows) {
            if (!row.enabled) continue
            if (validate(row) != null) {
                invalid += row
                continue
            }
            events += ScenarioEvent(
                timeSeconds = row.timeText.toDouble(),
                kind = row.kind,
                value = toEngineValue(row.kind, row.valueText.toDouble()),
            )
        }
        return Parsed(events, invalid)
    }

    /** The default timeline: the spec's example events, all disabled until toggled. */
    fun defaultRows(): List<EventRow> = listOf(
        EventRow(1, ScenarioEvent.Kind.STEAM_FLOW_KGS, enabled = false, timeText = "300", valueText = "450"),
        EventRow(2, ScenarioEvent.Kind.SPRAY_FLOW_KGS, enabled = false, timeText = "400", valueText = "5"),
        EventRow(3, ScenarioEvent.Kind.BURNERS_FIRING, enabled = false, timeText = "500", valueText = "3"),
        EventRow(4, ScenarioEvent.Kind.STEAM_PRESSURE_PA, enabled = false, timeText = "300", valueText = "120"),
        EventRow(5, ScenarioEvent.Kind.STEAM_TEMP_K, enabled = false, timeText = "300", valueText = "430"),
    )
}
