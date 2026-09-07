package com.steamcalc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steamcalc.engine.CalculationMode
import com.steamcalc.engine.TransientResult

@Composable
fun TransientSimulationScreen(
    onNavigateToEnergyBalance: (String) -> Unit,
    viewModel: TransientSimulationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ─── Header ────────────────────────────────────────────────────
        Text(
            text = "Transient Simulation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Burner ignition — platen thermal inertia",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ─── Steam Side ───────────────────────────────────────────────
        SectionHeader("Steam Side", MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = uiState.steamFlow,
            onValueChange = { viewModel.updateSteamFlow(it) },
            label = { Text("Steam flow (t/h)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.steamPressure,
            onValueChange = { viewModel.updateSteamPressure(it) },
            label = { Text("Pressure (bar)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.steamTemp,
            onValueChange = { viewModel.updateSteamTemp(it) },
            label = { Text("Steam inlet temp (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.targetTemp,
            onValueChange = { viewModel.updateTargetTemp(it) },
            label = { Text("Target outlet (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.sprayTemp,
            onValueChange = { viewModel.updateSprayTemp(it) },
            label = { Text("Spray water temp (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Initial Metal Temperature (mandatory) ────────────────────
        SectionHeader("Initial Platen Metal Temperature", Color(0xFFFF6F00))

        OutlinedTextField(
            value = uiState.initialMetalTemp,
            onValueChange = { viewModel.updateInitialMetalTemp(it) },
            label = { Text("Initial metal temperature (°C) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            supportingText = { Text("Starting condition for thermal inertia model") }
        )

        // ─── Platen Metal Properties ──────────────────────────────────
        SectionHeader("Platen Metal Properties", Color(0xFF616161))

        OutlinedTextField(
            value = uiState.metalMass,
            onValueChange = { viewModel.updateMetalMass(it) },
            label = { Text("Metal mass (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.metalCp,
            onValueChange = { viewModel.updateMetalCp(it) },
            label = { Text("Metal Cp (J/(kg·K))") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Burner ───────────────────────────────────────────────────
        SectionHeader("Burner", MaterialTheme.colorScheme.tertiary)

        OutlinedTextField(
            value = uiState.burnerHeat,
            onValueChange = { viewModel.updateBurnerHeat(it) },
            label = { Text("Heat per burner (MW)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.burnerCount,
            onValueChange = { viewModel.updateBurnerCount(it) },
            label = { Text("Number of burners") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.eta,
            onValueChange = { viewModel.updateEta(it) },
            label = { Text("η (burner→platen, 0–1)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Simulation ───────────────────────────────────────────────
        SectionHeader("Simulation", Color(0xFF006A60))

        OutlinedTextField(
            value = uiState.timeStep,
            onValueChange = { viewModel.updateTimeStep(it) },
            label = { Text("Time step (s)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.duration,
            onValueChange = { viewModel.updateDuration(it) },
            label = { Text("Duration (s)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Simulate Button ──────────────────────────────────────────
        Button(
            onClick = { viewModel.simulate() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = !uiState.isSimulating
        ) {
            if (uiState.isSimulating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("RUN SIMULATION", fontWeight = FontWeight.Bold)
        }

        // ─── Error Card ───────────────────────────────────────────────
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(
                    text = "⚠ $error",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ─── Results ──────────────────────────────────────────────────
        uiState.result?.let { result ->
            TransientResultCard(result, onNavigateToEnergyBalance)
        }
    }
}

@Composable
private fun TransientResultCard(
    result: TransientResult,
    onNavigateToEnergyBalance: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF1B5E20)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Simulation Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val lastStep = result.steps.last()
            val a = result.assumptions

            Text(
                text = "Final Metal Temp: ${"%.1f".format(lastStep.metalTempC)} °C",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Final Spray Flow: ${"%.2f".format(lastStep.requiredSprayFlowTH)} t/h",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Steady-State Spray: ${"%.2f".format(result.finalSteadyStateSprayTH)} t/h",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ─── Temperature vs Time Chart ────────────────────────────
            HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))

            val tempData = result.steps.map { it.timeS to it.metalTempC }
            if (tempData.size >= 2) {
                com.steamcalc.ui.components.ChartView(
                    data = tempData,
                    xLabel = "Time (s)",
                    yLabel = "Temp (°C)",
                    lineColor = MaterialTheme.colorScheme.primary,
                    title = "Metal Temperature vs Time",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ─── Spray Flow vs Time Chart ─────────────────────────────
            val sprayData = result.steps.map { it.timeS to it.requiredSprayFlowTH }
            if (sprayData.size >= 2) {
                com.steamcalc.ui.components.ChartView(
                    data = sprayData,
                    xLabel = "Time (s)",
                    yLabel = "Spray (t/h)",
                    lineColor = MaterialTheme.colorScheme.secondary,
                    title = "Spray Flow vs Time",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ─── Assumptions ──────────────────────────────────────────
            HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Assumptions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text("Mode: ${a.model} (Transient)", style = MaterialTheme.typography.bodySmall)
            Text("Steam: ${a.steamFlowTH} t/h, ${a.steamPressureBar} bar, ${a.steamTempC} °C", style = MaterialTheme.typography.bodySmall)
            Text("Target: ${a.targetTempC} °C, Spray: ${a.sprayTempC} °C", style = MaterialTheme.typography.bodySmall)
            Text("Initial metal temp: ${"%.1f".format(a.initialMetalTempC)} °C", style = MaterialTheme.typography.bodySmall)
            Text("Metal: ${a.platenMetalMassKg} kg, Cp = ${a.metalSpecificHeat} J/(kg·K)", style = MaterialTheme.typography.bodySmall)
            Text("Burners: ${a.numberOfBurners} × ${a.burnerHeatMW} MW, η = ${a.burnerToPlatenFraction}", style = MaterialTheme.typography.bodySmall)
            Text("Final metal temp: ${"%.1f".format(a.currentMetalTempC)} °C", style = MaterialTheme.typography.bodySmall)

            // ─── Warnings ─────────────────────────────────────────────
            if (result.warnings.isNotEmpty()) {
                result.warnings.forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // ─── Energy Balance Button ────────────────────────────────
            HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(
                onClick = {
                    val data = listOf(
                        "model=${a.model}",
                        "steamFlow=${a.steamFlowTH}",
                        "steamPressure=${a.steamPressureBar}",
                        "steamTemp=${a.steamTempC}",
                        "targetTemp=${a.targetTempC}",
                        "sprayTemp=${a.sprayTempC}",
                        "sprayFlow=${lastStep.requiredSprayFlowTH}",
                        "metalMass=${a.platenMetalMassKg}",
                        "metalCp=${a.metalSpecificHeat}",
                        "burnerHeat=${a.burnerHeatMW}",
                        "burnerCount=${a.numberOfBurners}",
                        "eta=${a.burnerToPlatenFraction}",
                        "initialMetalTemp=${a.initialMetalTempC}",
                        "netHeat=${lastStep.netHeatMW}"
                    ).joinToString(";")
                    onNavigateToEnergyBalance(data)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Full Energy Balance", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
