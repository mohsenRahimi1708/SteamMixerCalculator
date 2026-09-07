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
import com.steamcalc.engine.SteadyStateResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCalculatorScreen(
    onNavigateToEnergyBalance: (String) -> Unit,
    viewModel: QuickCalculatorViewModel = viewModel()
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
            text = "Required Spray (Mode A)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "IAPWS-IF97 · Steady-State Mixing · Reverse",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ─── Steam Inlet Section ──────────────────────────────────────
        SectionHeader("Steam Inlet", MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = uiState.steamFlow,
            onValueChange = { viewModel.updateSteamFlow(it) },
            label = { Text("Steam mass flow (t/h)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.steamPressure,
            onValueChange = { viewModel.updateSteamPressure(it) },
            label = { Text("Steam pressure (bar)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.steamTemp,
            onValueChange = { viewModel.updateSteamTemp(it) },
            label = { Text("Steam temperature (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Spray Water Section ──────────────────────────────────────
        SectionHeader("Spray Water", MaterialTheme.colorScheme.secondary)

        OutlinedTextField(
            value = uiState.targetTemp,
            onValueChange = { viewModel.updateTargetTemp(it) },
            label = { Text("Target outlet temperature (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.sprayTemp,
            onValueChange = { viewModel.updateSprayTemp(it) },
            label = { Text("Spray water temperature (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Initial Metal Temperature ────────────────────────────────
        SectionHeader("Platen Metal", Color(0xFF616161))

        OutlinedTextField(
            value = uiState.initialMetalTemp,
            onValueChange = { viewModel.updateInitialMetalTemp(it) },
            label = { Text("Initial metal temperature (°C) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        // ─── Calculate Button ─────────────────────────────────────────
        Button(
            onClick = { viewModel.calculate() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = !uiState.isCalculating
        ) {
            if (uiState.isCalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("CALCULATE", fontWeight = FontWeight.Bold)
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

        // ─── Results Card ─────────────────────────────────────────────
        uiState.result?.let { result ->
            ResultCard(result, onNavigateToEnergyBalance)
        }
    }
}

@Composable
private fun ResultCard(
    result: SteadyStateResult,
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
                text = "Results — Required Spray",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Primary result
            Text(
                text = "Required Spray Flow: ${"%.2f".format(result.requiredSprayFlowTH)} t/h",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Temperature Drop: ${"%.1f".format(result.temperatureDropC)} °C",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Combined Flow: ${"%.1f".format(result.combinedFlowTH)} t/h",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Enthalpy chain
            HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Enthalpy Chain",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text("h_steam(P,T): ${"%.2f".format(result.hSteamKJkg)} kJ/kg", style = MaterialTheme.typography.bodySmall)
            Text("h_spray(P,T): ${"%.2f".format(result.hSprayKJkg)} kJ/kg", style = MaterialTheme.typography.bodySmall)
            Text("h_out(P,T):   ${"%.2f".format(result.hOutKJkg)} kJ/kg", style = MaterialTheme.typography.bodySmall)

            // Full assumption set (transparency requirement §5)
            HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Assumptions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val a = result.assumptions
            Text("Mode: ${a.model} (Mode A — Required Spray)", style = MaterialTheme.typography.bodySmall)
            Text("Steam: ${a.steamFlowTH} t/h, ${a.steamPressureBar} bar, ${a.steamTempC} °C", style = MaterialTheme.typography.bodySmall)
            Text("Target: ${a.targetTempC} °C, Spray: ${a.sprayTempC} °C", style = MaterialTheme.typography.bodySmall)
            Text("Initial metal temp: ${"%.1f".format(a.initialMetalTempC)} °C", style = MaterialTheme.typography.bodySmall)

            // Warnings
            if (result.warnings.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 8.dp))
                result.warnings.forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // Energy Balance button
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
                        "sprayFlow=${result.requiredSprayFlowTH}",
                        "hSteam=${result.hSteamKJkg}",
                        "hSpray=${result.hSprayKJkg}",
                        "hOut=${result.hOutKJkg}",
                        "initialMetalTemp=${a.initialMetalTempC}"
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
