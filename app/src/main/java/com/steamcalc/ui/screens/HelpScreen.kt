package com.steamcalc.ui.screens

import androidx.compose.foundation.horizontalScroll
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

/**
 * In-app Help / Operator Guide.
 *
 * Condensed version of OPERATOR_GUIDE.md so the documentation travels with the APK.
 * Covers: safety notice, the three calculation modes, the thermodynamic formulas,
 * warning meanings, practical operations guidance, FAQ, and references.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Operator Guide",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Spray attemperation · IAPWS-IF97 · v3",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Safety ──────────────────────────────────────────────────
        HelpSection("⚠ Safety First", container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer) {
            HelpText(
                "This app is an engineering estimation tool. It is NOT a substitute for " +
                        "the boiler OEM documentation, the plant DCS logic, or site operating " +
                        "procedures. Never change spray-water control or burner firing based on " +
                        "this app alone — always cross-check against plant instrumentation."
            )
        }

        // ── Modes ───────────────────────────────────────────────────
        HelpSection("The Three Modes") {
            HelpText("A — Required Spray (reverse)", bold = true)
            HelpText("\u201CI want the outlet at X °C — how much spray water do I need?\u201D " +
                    "Enter the target outlet temperature; the app solves for the spray flow.")
            Spacer(Modifier.height(8.dp))
            HelpText("B — Spray Impact (forward)", bold = true)
            HelpText("\u201CI am injecting Y t/h of spray — what outlet temperature will I get?\u201D " +
                    "Enter the spray flow; the app computes the resulting outlet temperature and drop.")
            Spacer(Modifier.height(8.dp))
            HelpText("Transient (time-stepped)", bold = true)
            HelpText("After burner ignition, how fast does the platen metal heat up and how does " +
                    "spray demand grow over time? Uses platen metal mass, Cp, burner power and η.")
            Spacer(Modifier.height(8.dp))
            HelpText("Energy Balance", bold = true)
            HelpText("Tap \u201CView Full Energy Balance\u201D on any result to see the enthalpy chain, " +
                    "all inputs and the mode that produced the number — your \u201Cwhy?\u201D screen.")
        }

        // ── Formulas ────────────────────────────────────────────────
        HelpSection("Thermodynamic Formulas") {
            HelpText("Mode A — mixing-point energy balance:", bold = true)
            Formula("ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)")
            HelpText("h_s = h(P, T_steam), h_out = h(P, T_target), h_w = h(P, T_spray) — all from IF97.")
            Spacer(Modifier.height(10.dp))
            HelpText("Mode B — forward mixing + backward equation:", bold = true)
            Formula("h_out = (ṁ_s·h_s + ṁ_w·h_w) / (ṁ_s + ṁ_w)\n" +
                    "T_out = T(P, h_out)   ← IF97 backward eq.\n" +
                    "ΔT = T_steam − T_out")
            Spacer(Modifier.height(10.dp))
            HelpText("Transient — lumped metal capacitance (Euler):", bold = true)
            Formula("C = M · Cp\n" +
                    "Q_in = η · N · Q_burner\n" +
                    "Q_spray = ṁ_w · (h_target − h_w) / 3600\n" +
                    "Q_net = Q_in − Q_spray\n" +
                    "T(t+dt) = T(t) + Q_net·10⁶·dt / (M·Cp)")
            Spacer(Modifier.height(10.dp))
            HelpText("Units: flow t/h · pressure bar · temperature °C · enthalpy kJ/kg · " +
                    "heat MW · Q[MW] = ṁ[t/h]·Δh[kJ/kg]/3600.",
                    small = true)
        }

        // ── Worked example ──────────────────────────────────────────
        HelpSection("Worked Example (engine-computed)") {
            HelpText("600 t/h @ 167 bar (T_sat 350.8 °C), 540 → 520 °C, spray 230 °C:", bold = true)
            Formula("h_s = 3404.3  h_out = 3347.0  h_w = 993.4 kJ/kg\n" +
                    "spray = 600 × 57.3 / 2353.6 = 14.61 t/h")
            Spacer(Modifier.height(8.dp))
            HelpText("Mode B sweep at the same conditions:", bold = true)
            Formula("  0 t/h → 540.0 °C (ΔT  0.0 K)\n" +
                    " 10 t/h → 526.1 °C (ΔT 13.9 K)\n" +
                    " 25 t/h → 506.7 °C (ΔT 33.3 K)\n" +
                    " 60 t/h → 467.3 °C (ΔT 72.7 K)")
            HelpText("Spray demand is non-linear — the first 10 t/h buys ~14 K; do not " +
                    "extrapolate from a single data point.", small = true)
        }

        // ── Warnings ────────────────────────────────────────────────
        HelpSection("Warnings — What They Mean") {
            HelpText("Wet steam / over-spray", bold = true)
            HelpText("Predicted outlet enthalpy is below saturation at the pressure. Wet steam " +
                    "risks water carry-over, turbine erosion and tube stress. The mixing model " +
                    "is not valid for wet outlet — reduce spray or raise the target.")
            Spacer(Modifier.height(8.dp))
            HelpText("Cooling not possible", bold = true)
            HelpText("Spray-water enthalpy ≥ target enthalpy: with this spray temperature the " +
                    "target cannot be reached. Spray requirement shows as infinite.")
            Spacer(Modifier.height(8.dp))
            HelpText("Thermal-shock advisory", bold = true)
            HelpText("Fires when steam temperature exceeds the initial platen metal temperature " +
                    "by more than 50 °C. The mixing number is still correct, but the metal lags " +
                    "and may see transient stress — use the Transient screen to quantify the lag.")
            Spacer(Modifier.height(8.dp))
            HelpText("Input rejections", bold = true)
            HelpText("Pressure 20–170 bar · steam 200–560 °C · target ≤ steam inlet · spray water " +
                    "must be subcooled (below T_sat) · metal temp 0–700 °C · Mode B spray ≤ 2× " +
                    "steam flow · steam must be superheated. The app never silently defaults a value.")
        }

        // ── Practical tips ──────────────────────────────────────────
        HelpSection("Practical Tips for Operations") {
            HelpText("• Cross-check modes: feed Mode A's spray flow into Mode B — you must get " +
                    "your target temperature back.")
            Spacer(Modifier.height(6.dp))
            HelpText("• Sensitivity at 167 bar / 540 °C / 230 °C spray: every 1 t/h of spray gives " +
                    "roughly 1.3–1.4 K of cooling. Not constant — recompute when load or pressure changes.")
            Spacer(Modifier.height(6.dp))
            HelpText("• Cold platen: if the thermal-shock advisory fires, spray demand will drift " +
                    "UPWARD as the metal heats. In the default transient case demand rose from " +
                    "14.6 to 17.1 t/h in ten minutes under 11.2 MW of unbalanced burner heat.")
            Spacer(Modifier.height(6.dp))
            HelpText("• Compare with DCS: the app assumes ideal adiabatic mixing with no losses. " +
                    "Real installations typically need 5–15 % more spray due to imperfect " +
                    "evaporation and mixing. Use for estimation, not as a control setpoint source.")
        }

        // ── FAQ ─────────────────────────────────────────────────────
        HelpSection("FAQ") {
            HelpText("Why is initial metal temperature mandatory everywhere?", bold = true)
            HelpText("It documents the plant state behind the estimate and drives the " +
                    "thermal-shock advisory. It never alters the mixing number.")
            Spacer(Modifier.height(8.dp))
            HelpText("Why is my spray temperature rejected?", bold = true)
            HelpText("Spray water must be subcooled liquid at the attemperator pressure — at " +
                    "167 bar, T_sat = 350.8 °C, so anything above that cannot exist as liquid.")
            Spacer(Modifier.height(8.dp))
            HelpText("Can I use it for the reheater?", bold = true)
            HelpText("Yes — the math is identical. Enter reheater pressure, flows and temperatures.")
        }

        // ── About ───────────────────────────────────────────────────
        HelpSection("About") {
            HelpText("Steam properties: IAPWS-IF97 industrial formulation via the " +
                    "com.hummeling:if97 library (LGPL). All calculations run on-device, offline.")
            Spacer(Modifier.height(6.dp))
            HelpText("The engine's unit tests cross-validate Mode A ↔ Mode B and verify the " +
                    "transient model against the analytical no-spray case (36 tests passing).")
            Spacer(Modifier.height(6.dp))
            HelpText("Full guide: OPERATOR_GUIDE.md in the project repository.", small = true)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back to Calculator")
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Private helpers (per-screen convention) ─────────────────────────────

@Composable
private fun HelpSection(
    title: String,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface,
    body: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            body()
        }
    }
}

@Composable
private fun HelpText(text: String, bold: Boolean = false, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun Formula(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    )
}
