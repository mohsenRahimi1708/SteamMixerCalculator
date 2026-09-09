package com.platen4.superheater.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platen4.superheater.engine.*

/**
 * Validation screen (spec §16-§17): side-by-side comparison of the three model
 * levels, the 800 vs ~170 W/m²K discrepancy surfaced explicitly, and the
 * historian-data calibration fitter with MAE/RMSE/max/bias/R².
 */
@Composable
fun ValidationScreen(modifier: Modifier = Modifier) {
    val props = remember { If97SteamProperties() }
    var measuredInput by remember { mutableStateOf("") }
    var fitResult by remember { mutableStateOf<Calibration.FitResult?>(null) }
    var comparison by remember { mutableStateOf<String?>(null) }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Model comparison at fixed scenario", style = MaterialTheme.typography.titleMedium)
        Text(
            "Load step 100→150 t/h at t=300 s, burner step 2→3 at t=300 s, 15 min window.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val events = listOf(
                ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
                ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 3.0),
            )
            val a = SingleBlockModel(props, SingleBlockModel.Mode.CONSTANT_U, fixedUWm2K = 800.0)
                .run(events)
            val b = SingleBlockModel(props, SingleBlockModel.Mode.DYNAMIC_HI).run(events)
            val c = PlatenSimulator(props, PlatenSimulator.Config(dtSeconds = 1.0, durationSeconds = 900.0)).run(events)
            val f = { k: Double -> "%.1f".format(k - 273.15) }
            comparison = "constant-U (800):   end ${f(a.outletTempK.last())} °C\n" +
                "dynamic h_i:        end ${f(b.outletTempK.last())} °C\n" +
                "uniform RK4:        end ${f(c.outletTempK.last())} °C\n" +
                "U discrepancy: plant YAML 800 W/m²K (low load) vs Dittus-Boelter ~170 (full load) — " +
                "unresolved; both paths are provided, never blended."
        }) { Text("Run comparison") }
        comparison?.let {
            Spacer(Modifier.height(8.dp))
            Card { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }

        Spacer(Modifier.height(24.dp))
        Text("Calibration against historian data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste measured outlet temperatures (°C), comma-separated, same Δt as the simulation (1 s × 900). " +
                "The fitter searches F_platen ∈ [0.05..0.5] and h_o ∈ [20..500] and reports fit quality. " +
                "Fitted parameters are labeled empirical and never overwrite physical ones.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = measuredInput,
            onValueChange = { measuredInput = it },
            label = { Text("Measured T_out series (°C)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            runCatching {
                val measured = measuredInput.split(',', '\n').mapNotNull { it.trim().toDoubleOrNull() }
                require(measured.size >= 10) { "Need at least 10 measured points" }
                val events = listOf(
                    ScenarioEvent(300.0, ScenarioEvent.Kind.STEAM_FLOW_KGS, 150.0 / 3.6),
                    ScenarioEvent(300.0, ScenarioEvent.Kind.BURNERS_FIRING, 3.0),
                )
                val fit = Calibration.fit(
                    measured.map { it + 273.15 }.toDoubleArray(),
                    predictedAt = { pf: Double, ho: Double ->
                        val sim = PlatenSimulator(
                            props,
                            PlatenSimulator.Config(
                                dtSeconds = 2.0,
                                durationSeconds = 900.0,
                                platenFraction = pf,
                                hoWm2K = ho,
                            ),
                        ).run(events)
                        sim.outletTempK
                    },
                )
                fit
            }.onSuccess { fitResult = it }.onFailure { fitResult = null; comparison = "Calibration error: ${it.message}" }
        }) { Text("Fit parameters") }
        fitResult?.let { fr ->
            Spacer(Modifier.height(8.dp))
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Fitted (empirical — keep separate from physical values):", style = MaterialTheme.typography.labelMedium)
                    Text("F_platen = %.3f   h_o = %.0f W/m²K".format(fr.fittedPlatenFraction, fr.fittedHoWm2K))
                    Text("MAE %.2f K   RMSE %.2f K   max %.2f K".format(fr.maeK, fr.rmseK, fr.maxErrorK))
                    Text("bias %.2f K   R² %.4f".format(fr.biasK, fr.rSquared))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}