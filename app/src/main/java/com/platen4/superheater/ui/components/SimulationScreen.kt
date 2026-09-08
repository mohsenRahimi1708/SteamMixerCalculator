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

/**
 * Simulation screen (spec §14, §15, §16, §21):
 * inputs, scenario events, run, plots, and tappable "show calculation" rows.
 */
@Composable
fun SimulationScreen(modifier: Modifier = Modifier) {
    val props = remember { If97SteamProperties() }
    var steamFlowTh by remember { mutableStateOf("100") }
    var pressureBar by remember { mutableStateOf("100") }
    var steamTempC by remember { mutableStateOf("400") }
    var sprayTempC by remember { mutableStateOf("230") }
    var burners by remember { mutableStateOf("2") }
    var durationMin by remember { mutableStateOf("15") }
    var stepEventTime by remember { mutableStateOf("300") }
    var stepFlowTo by remember { mutableStateOf("150") }
    var stepSprayTo by remember { mutableStateOf("5") }
    var stepBurnersTo by remember { mutableStateOf("3") }
    var result by remember { mutableStateOf<PlatenSimulator.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Initial state", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Steam flow t/h", steamFlowTh, Modifier.weight(1f)) { steamFlowTh = it; result = null }
            NumField("Pressure bar", pressureBar, Modifier.weight(1f)) { pressureBar = it; result = null }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Steam T °C", steamTempC, Modifier.weight(1f)) { steamTempC = it; result = null }
            NumField("Spray T °C (<250)", sprayTempC, Modifier.weight(1f)) { sprayTempC = it; result = null }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Burners on", burners, Modifier.weight(1f)) { burners = it; result = null }
            NumField("Duration min", durationMin, Modifier.weight(1f)) { durationMin = it; result = null }
        }

        Spacer(Modifier.height(16.dp))
        Text("Scenario event at t = 300 s (step)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Flow → t/h", stepFlowTo, Modifier.weight(1f)) { stepFlowTo = it; result = null }
            NumField("Spray → t/h", stepSprayTo, Modifier.weight(1f)) { stepSprayTo = it; result = null }
            NumField("Burners →", stepBurnersTo, Modifier.weight(1f)) { stepBurnersTo = it; result = null }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = null
            result = null
            runCatching {
                val events = buildList {
                    if (stepFlowTo.toDoubleOrNull()?.let { it > 0 } == true)
                        add(ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, stepFlowTo.toDouble() / 3.6))
                    if ((stepSprayTo.toDoubleOrNull() ?: 0.0) > 0)
                        add(ScenarioEvent(300.0, ScenarioEvent.Kind.SPRAY_FLOW_KGS, stepSprayTo.toDouble() / 3.6))
                    if (stepBurnersTo.toDoubleOrNull()?.let { it > 0 } == true)
                        add(ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, stepBurnersTo.toDouble()))
                }
                val cfg = PlatenSimulator.Config(
                    dtSeconds = 1.0,
                    durationSeconds = (durationMin.toDoubleOrNull() ?: 15.0) * 60.0,
                )
                PlatenSimulator(props, cfg).run(
                    events,
                    initialSteamTempK = (steamTempC.toDoubleOrNull() ?: 400.0) + 273.15,
                    initialPressurePa = (pressureBar.toDoubleOrNull() ?: 100.0) * 1e5,
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
                StatRow("Metal T avg (end)", fmtK(r.metalTempAvgK[n]) + " °C")
                StatRow("Mixed T (end)", fmtK(r.mixedTempK[n]) + " °C")
                StatRow("h_i (end)", "%.0f W/m²K".format(r.hiWm2K[n]))
                StatRow("U (end)", "%.0f W/m²K".format(r.uWm2K[n]))
                StatRow("Re (end)", "%.3e".format(r.reynolds[n]))
                StatRow("Q absorbed (end)", "%.1f MW".format(r.qAbsorbedW[n] / 1e6))

                Spacer(Modifier.height(12.dp))
                Text("Outlet temperature vs time", style = MaterialTheme.typography.titleSmall)
                SimpleChart(r.times, r.outletTempK, "t [s]", "T_out [°C]")
                Spacer(Modifier.height(12.dp))
                Text("Metal temperature vs time", style = MaterialTheme.typography.titleSmall)
                SimpleChart(r.times, r.metalTempAvgK, "t [s]", "T_metal [°C]")
                Spacer(Modifier.height(12.dp))
                Text("h_i vs time", style = MaterialTheme.typography.titleSmall)
                SimpleChart(r.times, r.hiWm2K, "t [s]", "h_i [W/m²K]")
                Spacer(Modifier.height(12.dp))
                Text("U vs time", style = MaterialTheme.typography.titleSmall)
                SimpleChart(r.times, r.uWm2K, "t [s]", "U [W/m²K]")

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
            DetailLine("ρ", "${"%.3f".format(props.densityPT(p, t))} kg/m³")
            DetailLine("cp", "${"%.1f".format(props.cpPT(p, t) / 1e3)} kJ/kgK")
            DetailLine("μ", "${"%.3e".format(props.viscosityPT(p, t))} Pa·s")
            DetailLine("k", "${"%.4f".format(props.conductivityPT(p, t))} W/mK")
            DetailLine("Pr", "${"%.3f".format(props.prandtlPT(p, t))}")
            DetailLine("Re", "%.3e".format(r.reynolds[n]))
            DetailLine("Nu", "%.1f".format(r.nusselt[n]))
            DetailLine("h_i = Nu·k/D", "${"%.0f".format(r.hiWm2K[n])} W/m²K")
            DetailLine("U (resistance chain)", "${"%.0f".format(r.uWm2K[n])} W/m²K")
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