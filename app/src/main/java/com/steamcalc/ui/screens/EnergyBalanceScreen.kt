package com.steamcalc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steamcalc.engine.CalculationMode

/**
 * Energy Balance screen that receives serialized data via navigation argument
 * and displays the full calculation breakdown.
 */
@Composable
fun EnergyBalanceScreen(
    data: String,
    onBack: () -> Unit
) {
    val params = remember(data) { parseEnergyBalanceData(data) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ─── Header ────────────────────────────────────────────────────
        Text(
            text = "Energy Balance Report",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        val modeLabel = when {
            params.isTransient -> "Transient"
            params.isSprayImpact -> "Mode B — Spray Impact (Forward)"
            else -> "Mode A — Required Spray (Steady-State)"
        }
        Text(
            text = "Model: $modeLabel",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ─── Input Conditions ─────────────────────────────────────────
        ReportSection("INPUT CONDITIONS") {
            ReportLine("Steam flow", "${fmt(params.steamFlow)} t/h")
            ReportLine("Steam pressure", "${fmt(params.steamPressure)} bar")
            ReportLine("Steam inlet temp", "${fmt(params.steamTemp)} °C")
            if (!params.isSprayImpact) {
                ReportLine("Target outlet", "${fmt(params.targetTemp)} °C")
            }
            ReportLine("Spray water temp", "${fmt(params.sprayTemp)} °C")
            ReportLine("Spray flow", "${fmt(params.sprayFlow)} t/h")
            ReportLine("Initial metal temp", "${fmt(params.initialMetalTemp)} °C")
        }

        // ─── Platen & Burner (transient only) ─────────────────────────
        if (params.isTransient) {
            ReportSection("PLATEN & BURNER") {
                ReportLine("Metal mass", "${fmt(params.metalMass)} kg")
                ReportLine("Metal Cp", "${fmt(params.metalCp)} J/(kg·K)")
                ReportLine("Burner heat", "${fmt(params.burnerHeat)} MW × ${params.burnerCount}")
                ReportLine("η (to platen)", "%.4f".format(params.eta))
                ReportLine("Current metal T", "${fmt(params.currentMetalTemp)} °C")
            }
        }

        // ─── Step 1: Burner → Platen Heat (transient only) ───────────
        if (params.isTransient) {
            ReportSection("STEP 1: BURNER → PLATEN HEAT") {
                val totalBurner = params.burnerCount * params.burnerHeat
                val heatToPlaten = params.eta * totalBurner
                ReportLine("Total burner MW", "%.2f".format(totalBurner) + " MW")
                ReportLine("Heat to platen", "%.2f".format(heatToPlaten) + " MW")
            }
        }

        // ─── Step 2: Steam-side Enthalpies ────────────────────────────
        ReportSection("STEP 2: STEAM-SIDE ENTHALPIES") {
            val hSteam = params.hSteam
            val hOut = params.hOut
            val hSpray = params.hSpray

            ReportLine("h_steam(P,T_in)", "%.2f".format(hSteam) + " kJ/kg")
            if (!params.isSprayImpact) {
                ReportLine("h_target(P,T_out)", "%.2f".format(hOut) + " kJ/kg")
            } else {
                ReportLine("h_out(mix)", "%.2f".format(hOut) + " kJ/kg")
            }
            ReportLine("h_spray(P,T_spray)", "%.2f".format(hSpray) + " kJ/kg")
        }

        // ─── Step 3: Required Cooling ─────────────────────────────────
        ReportSection("STEP 3: REQUIRED COOLING") {
            val dhSteam = params.hSteam - params.hOut
            val dhSpray = params.hOut - params.hSpray
            ReportLine("Δh (steam→target)", "%.2f".format(dhSteam) + " kJ/kg")
            ReportLine("Δh (target→spray)", "%.2f".format(dhSpray) + " kJ/kg")
            ReportLine("Spray flow", "%.2f".format(params.sprayFlow) + " t/h")

            val heatRemoved = params.sprayFlow * dhSpray / 3600.0
            ReportLine("Heat removed", "%.2f".format(heatRemoved) + " MW")
        }

        // ─── Step 4: Net Heat Balance ─────────────────────────────────
        if (params.isTransient) {
            ReportSection("STEP 4: NET HEAT BALANCE") {
                val totalBurner = params.burnerCount * params.burnerHeat
                val heatToPlaten = params.eta * totalBurner
                val dhSpray = params.hOut - params.hSpray
                val heatRemoved = params.sprayFlow * dhSpray / 3600.0
                val netHeat = heatToPlaten - heatRemoved

                ReportLine("Q_in (to platen)", "%.2f".format(heatToPlaten) + " MW")
                ReportLine("Q_out (by spray)", "%.2f".format(heatRemoved) + " MW")
                ReportLine("Q_net", "%.2f".format(netHeat) + " MW")
            }

            // ─── Step 5: Metal Response ──────────────────────────────
            ReportSection("STEP 5: METAL RESPONSE") {
                val totalBurner = params.burnerCount * params.burnerHeat
                val heatToPlaten = params.eta * totalBurner
                val dhSpray = params.hOut - params.hSpray
                val heatRemoved = params.sprayFlow * dhSpray / 3600.0
                val netHeat = heatToPlaten - heatRemoved
                val thermalCap = params.metalMass * params.metalCp
                val dTdt = if (thermalCap > 0) netHeat * 1e6 / thermalCap else 0.0

                ReportLine("dT_metal/dt", "%.6f".format(dTdt) + " °C/s")
                ReportLine("dT_metal/dt", "%.4f".format(dTdt * 60) + " °C/min")
            }
        } else {
            // ─── Mode A/B: Resulting temperature ────────────────────
            ReportSection("RESULT") {
                if (params.isSprayImpact) {
                    ReportLine("Outlet enthalpy", "%.2f".format(params.hOut) + " kJ/kg")
                } else {
                    val temperatureDrop = params.steamTemp - params.targetTemp
                    ReportLine("Temperature drop", "${fmt(temperatureDrop)} °C")
                    ReportLine("Required spray", "%.2f".format(params.sprayFlow) + " t/h")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── Back Button ──────────────────────────────────────────────
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Back to Calculator")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────

@Composable
private fun ReportSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun fmt(v: Double): String = "%.1f".format(v)

// ─── Data Parsing ─────────────────────────────────────────────────────────

private data class EnergyBalanceParams(
    val isTransient: Boolean,
    val isSprayImpact: Boolean,
    val steamFlow: Double,
    val steamPressure: Double,
    val steamTemp: Double,
    val targetTemp: Double,
    val sprayTemp: Double,
    val sprayFlow: Double,
    val hSteam: Double,
    val hSpray: Double,
    val hOut: Double,
    val metalMass: Double,
    val metalCp: Double,
    val burnerHeat: Double,
    val burnerCount: Int,
    val eta: Double,
    val initialMetalTemp: Double,
    val currentMetalTemp: Double,
    val netHeat: Double
)

private fun parseEnergyBalanceData(data: String): EnergyBalanceParams {
    val map = data.split(";").associate {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
    }

    return EnergyBalanceParams(
        isTransient = map["model"] == "TRANSIENT",
        isSprayImpact = map["model"] == "SPRAY_IMPACT",
        steamFlow = map["steamFlow"]?.toDoubleOrNull() ?: 0.0,
        steamPressure = map["steamPressure"]?.toDoubleOrNull() ?: 0.0,
        steamTemp = map["steamTemp"]?.toDoubleOrNull() ?: 0.0,
        targetTemp = map["targetTemp"]?.toDoubleOrNull() ?: 0.0,
        sprayTemp = map["sprayTemp"]?.toDoubleOrNull() ?: 0.0,
        sprayFlow = map["sprayFlow"]?.toDoubleOrNull() ?: 0.0,
        hSteam = map["hSteam"]?.toDoubleOrNull() ?: 0.0,
        hSpray = map["hSpray"]?.toDoubleOrNull() ?: 0.0,
        hOut = map["hOut"]?.toDoubleOrNull() ?: 0.0,
        metalMass = map["metalMass"]?.toDoubleOrNull() ?: 0.0,
        metalCp = map["metalCp"]?.toDoubleOrNull() ?: 0.0,
        burnerHeat = map["burnerHeat"]?.toDoubleOrNull() ?: 0.0,
        burnerCount = map["burnerCount"]?.toIntOrNull() ?: 0,
        eta = map["eta"]?.toDoubleOrNull() ?: 0.0,
        initialMetalTemp = map["initialMetalTemp"]?.toDoubleOrNull() ?: 0.0,
        currentMetalTemp = map["currentMetalTemp"]?.toDoubleOrNull() ?: 0.0,
        netHeat = map["netHeat"]?.toDoubleOrNull() ?: 0.0
    )
}
