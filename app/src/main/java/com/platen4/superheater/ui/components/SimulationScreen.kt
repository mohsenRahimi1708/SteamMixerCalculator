package com.platen4.superheater.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platen4.superheater.engine.*
import kotlin.math.roundToInt

/** One selectable plotted parameter (refactored spec §16: all parameters accessible). */
private data class PlotParam(
    val key: String,
    val label: String,
    val unit: String,
    val values: DoubleArray,
)

private fun buildPlotParams(r: PlatenSimulator.Result): List<PlotParam> {
    val c = { k: Double -> k - 273.15 } // K -> °C
    return listOf(
        PlotParam("outlet", "Steam outlet T", "°C", r.outletTempK),
        PlotParam("metal", "Metal T", "°C", r.metalTempK),
        PlotParam("mixed", "Mixed T (post-spray)", "°C", r.mixedTempK),
        PlotParam("inlet", "Steam inlet T", "°C", r.inletSteamTempK),
        PlotParam("pressure", "Pressure", "bar", r.pressurePa),
        PlotParam("steamflow", "Steam flow", "t/h", r.steamFlowKgs),
        PlotParam("sprayflow", "Spray flow", "t/h", r.sprayFlowKgs),
        PlotParam("qplaten", "Burner heat to platen", "MW", r.qPlatenW),
        PlotParam("qabs", "Heat absorbed", "MW", r.qAbsorbedW),
        PlotParam("velocity", "Tube velocity", "m/s", r.velocityMs),
        PlotParam("re", "Reynolds", "-", r.reynolds),
        PlotParam("pr", "Prandtl", "-", r.prandtl),
        PlotParam("nu", "Nusselt", "-", r.nusselt),
        PlotParam("hi", "h_i (internal convection)", "W/m²K", r.hiWm2K),
        PlotParam("u", "U (overall)", "W/m²K", r.uWm2K),
        PlotParam("rho", "Density ρ", "kg/m³", r.densityKgM3),
        PlotParam("cp", "Specific heat cp", "kJ/kg·K", r.cpJkgK),
        PlotParam("mu", "Viscosity μ", "Pa·s", r.viscosityPaS),
        PlotParam("k", "Conductivity k", "W/m·K", r.conductivityWmK),
        PlotParam("h", "Enthalpy h", "kJ/kg", r.enthalpyJkg),
    )
}

/**
 * Simulation screen — refactored spec:
 *  - initial metal temperature input (default 450 °C),
 *  - spray temperature inside the enforced 100-180 °C subcooled window,
 *  - scenario events come from the shared [ScenarioEditorScreen] timeline
 *    (any number of events per quantity, each enabled/disabled individually),
 *  - every thermodynamic parameter selectable for plotting after the run,
 *  - tappable "show calculation" transparency panel.
 */
@Composable
fun SimulationScreen(
    scenarioRows: List<EventRow>,
    onScenarioRowsChange: (List<EventRow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val props = remember { If97SteamProperties() }

    // ---- Initial state ----
    var steamFlowTh by remember { mutableStateOf("300") }
    var pressureBar by remember { mutableStateOf("100") }
    var steamTempC by remember { mutableStateOf("400") }
    var sprayTempC by remember { mutableStateOf("150") }
    var metalTempC by remember { mutableStateOf("450") }
    var burners by remember { mutableStateOf("2") }
    var durationMin by remember { mutableStateOf("15") }

    var result by remember { mutableStateOf<PlatenSimulator.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPlots by remember { mutableStateOf(setOf("outlet", "metal")) }

    // Enabled + valid rows, and how many were skipped
    val parsed = remember(scenarioRows) { ScenarioTimeline.parse(scenarioRows) }
    val enabledCount = parsed.events.size
    val skipped = scenarioRows.count { it.enabled && ScenarioTimeline.validate(it) != null }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Initial state", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Steam flow t/h", steamFlowTh, Modifier.weight(1f)) { steamFlowTh = it; result = null }
            NumField("Pressure bar", pressureBar, Modifier.weight(1f)) { pressureBar = it; result = null }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Steam T °C", steamTempC, Modifier.weight(1f)) { steamTempC = it; result = null }
            NumField("Spray T °C 100-180", sprayTempC, Modifier.weight(1f)) { sprayTempC = it; result = null }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Metal T °C", metalTempC, Modifier.weight(1f)) { metalTempC = it; result = null }
            NumField("Burners on", burners, Modifier.weight(1f)) { burners = it; result = null }
            NumField("Duration min", durationMin, Modifier.weight(1f)) { durationMin = it; result = null }
        }

        Spacer(Modifier.height(16.dp))
        Text("Scenarios", style = MaterialTheme.typography.titleMedium)
        Text(
            "$enabledCount enabled event(s) will fire" + if (skipped > 0) " ($skipped enabled row(s) skipped — fix them in the Scenarios tab)." else ".",
            style = MaterialTheme.typography.bodySmall,
        )
        parsed.events.sortedBy { it.timeSeconds }.take(6).forEach { e ->
            val kindLabel = ScenarioTimeline.displayLabel(e.kind)
            val valueDisp = when (e.kind) {
                ScenarioEvent.Kind.STEAM_FLOW_KGS, ScenarioEvent.Kind.SPRAY_FLOW_KGS -> "%.1f t/h".format(e.value * 3.6)
                ScenarioEvent.Kind.SPRAY_TEMP_K, ScenarioEvent.Kind.STEAM_TEMP_K -> "%.0f °C".format(e.value - 273.15)
                ScenarioEvent.Kind.STEAM_PRESSURE_PA -> "%.0f bar".format(e.value / 1e5)
                else -> "%.2f".format(e.value)
            }
            Text("• t = ${e.timeSeconds.roundToInt()} s — $kindLabel → $valueDisp", style = MaterialTheme.typography.bodySmall)
        }
        if (scenarioRows.any { it.enabled }) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { onScenarioRowsChange(scenarioRows.map { r -> r.copy(enabled = false) }) }) {
                Text("Disable all events")
            }
        }
        if (scenarioRows.none { it.enabled }) {
            Text(
                "No events enabled — the initial state holds for the whole run. Edit events in the Scenarios tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            error = null
            result = null
            runCatching {
                val cfg = PlatenSimulator.Config(
                    dtSeconds = 1.0,
                    durationSeconds = (durationMin.toDoubleOrNull() ?: 15.0) * 60.0,
                    initialMetalK = (metalTempC.toDoubleOrNull() ?: 450.0) + 273.15,
                )
                PlatenSimulator(props, cfg).run(
                    parsed.events,
                    initialSteamTempK = (steamTempC.toDoubleOrNull() ?: 400.0) + 273.15,
                    initialPressurePa = (pressureBar.toDoubleOrNull() ?: 100.0) * 1e5,
                    initialSteamFlowKgs = (steamFlowTh.toDoubleOrNull() ?: 300.0) / 3.6,
                )
            }.onSuccess { result = it }.onFailure { error = it.message }
        }) { Text("RUN SIMULATION") }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        result?.let { r ->
            if (r.error != null) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(r.error!!, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Text("Results", style = MaterialTheme.typography.titleMedium)
                val n = r.times.size - 1
                StatRow("Outlet T (start → end)", "${fmtK(r.outletTempK[0])} → ${fmtK(r.outletTempK[n])} °C")
                StatRow("Metal T (start → end)", "${fmtK(r.metalTempK[0])} → ${fmtK(r.metalTempK[n])} °C")
                StatRow("Mixed T (end)", fmtK(r.mixedTempK[n]) + " °C")
                StatRow("Velocity (end)", "%.1f m/s".format(r.velocityMs[n]))
                StatRow("h_i (end)", "%.0f W/m²K".format(r.hiWm2K[n]))
                StatRow("U (end)", "%.0f W/m²K".format(r.uWm2K[n]))
                StatRow("Re (end)", "%.3e".format(r.reynolds[n]))
                StatRow("Q absorbed (end)", "%.1f MW".format(r.qAbsorbedW[n] / 1e6))
                StatRow("ρ (end)", "%.2f kg/m³".format(r.densityKgM3[n]))
                StatRow("cp (end)", "%.2f kJ/kg·K".format(r.cpJkgK[n] / 1e3))

                Spacer(Modifier.height(12.dp))
                Text("Plotted parameters — select any", style = MaterialTheme.typography.titleMedium)
                val allParams = remember(r) { buildPlotParams(r) }
                allParams.toList().chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { p ->
                            FilterChip(
                                selected = selectedPlots.contains(p.key),
                                onClick = {
                                    selectedPlots = if (selectedPlots.contains(p.key)) selectedPlots - p.key else selectedPlots + p.key
                                },
                                label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                allParams.filter { selectedPlots.contains(it.key) }.forEach { p ->
                    Text("${p.label} vs time", style = MaterialTheme.typography.titleSmall)
                    SimpleChart(r.times, p.values, "t [s]", "${p.label} [${p.unit}]")
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(12.dp))
                CalculationDetails(props, r)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CalculationDetails(props: SteamProperties, r: PlatenSimulator.Result) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide calculation details" else "Show calculation details")
    }
    if (expanded) {
        val n = r.times.size - 1
        val p = r.pressurePa[n]
        val t = r.outletTempK[n]
        Column(Modifier.padding(top = 8.dp)) {
            Text("State at end of simulation (t = ${r.times[n].roundToInt()} s):", style = MaterialTheme.typography.bodySmall)
            DetailLine("P", "${"%.2f".format(p / 1e5)} bar")
            DetailLine("T_out", "${"%.2f".format(t - 273.15)} °C")
            DetailLine("h", "${"%.1f".format(r.enthalpyJkg[n] / 1e3)} kJ/kg")
            DetailLine("ρ", "${"%.3f".format(r.densityKgM3[n])} kg/m³")
            DetailLine("cp", "${"%.1f".format(r.cpJkgK[n] / 1e3)} kJ/kgK")
            DetailLine("μ", "${"%.3e".format(r.viscosityPaS[n])} Pa·s")
            DetailLine("k", "${"%.4f".format(r.conductivityWmK[n])} W/mK")
            DetailLine("Pr", "${"%.3f".format(r.prandtl[n])}")
            DetailLine("v (tube)", "${"%.2f".format(r.velocityMs[n])} m/s")
            DetailLine("Re", "%.3e".format(r.reynolds[n]))
            DetailLine("Nu", "%.1f".format(r.nusselt[n]))
            DetailLine("h_i = Nu·k/D", "${"%.0f".format(r.hiWm2K[n])} W/m²K")
            DetailLine("U (resistance chain)", "${"%.0f".format(r.uWm2K[n])} W/m²K")
            DetailLine("IF97 region at outlet", "${props.regionPT(p, t)}")
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun fmtK(k: Double): String = "%.1f".format(k - 273.15)
