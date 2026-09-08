package com.platen4.superheater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

/**
 * In-app Help screen: the full user-guide content, structured as sections so it can
 * be browsed on the device without the PDF. Includes a case-insensitive filter that
 * matches section titles and body text, and jump-chips for the main sections.
 */
data class HelpSection(val title: String, val body: List<HelpBlock>)

sealed interface HelpBlock {
    data class Paragraph(val text: String) : HelpBlock
    data class Bullets(val items: List<String>) : HelpBlock
    data class Code(val text: String) : HelpBlock
}

object HelpContent {

    val sections: List<HelpSection> = listOf(
        HelpSection(
            "1. What the app does",
            listOf(
                HelpBlock.Paragraph(
                    "This is an engineering simulation tool, not a static calculator. It computes how the " +
                        "outlet steam temperature of the platen superheater in the 325 MW natural-circulation " +
                        "drum boiler (Unit 4) responds over time to:"
                ),
                HelpBlock.Bullets(
                    listOf(
                        "Steam mass flow / load changes",
                        "Spray-water flow changes at the platen inlet",
                        "Burner start/stop events and furnace heat-input changes",
                        "Steam inlet pressure and temperature changes",
                        "Property changes with P and T (density, cp, viscosity, conductivity)",
                        "The full heat-transfer chain: velocity -> Re -> Pr -> Nu -> h_i -> U",
                        "Tube-metal thermal inertia (the dominant delay source)",
                        "Spray injection cooling dynamics",
                    )
                ),
                HelpBlock.Code(
                    "P, T -> IF97 properties (rho, mu, cp, k)\n" +
                        "     -> velocity -> Re -> Pr -> Nu -> h_i -> U\n" +
                        "     -> Q (per segment)\n" +
                        "     -> metal energy balance -> steam energy balance\n" +
                        "     -> steam outlet T"
                ),
                HelpBlock.Paragraph("U is a calculated, load-dependent output - never a hard-coded constant."),
            ),
        ),
        HelpSection(
            "2. Plant reference data (Unit 4)",
            listOf(
                HelpBlock.Bullets(
                    listOf(
                        "Boiler: 325 MW, natural circulation, drum-type",
                        "Burners: 24 x 40 MW, 3 elevations",
                        "Full-load steam flow: 1000-1040 t/h",
                        "Sliding pressure 10-167 bar, steam temperature ~200-540 C",
                        "Platen: 43 panels x 4 tubes (172), 8 passes of 6 m",
                        "Tube OD / wall / ID: 45 / 8 / 29 mm",
                        "Inner area ~783 m2, outer area ~1216 m2",
                        "Header feed: dual mid-point (panels 1-21 / 22-43)",
                    )
                ),
                HelpBlock.Paragraph(
                    "Sections in flow order: inlet casing (0.5 m, 12Cr1MoV) -> lower radiant (15 m, SA-213 T91) -> " +
                        "inner horizontal (20 m, 12Cr2MoWVTiB) -> upper horizontal (12 m, 12Cr2MoWVTiB) -> " +
                        "outlet casing (2.5 m, 12Cr1MoV)."
                ),
            ),
        ),
        HelpSection(
            "3. The Simulate tab",
            listOf(
                HelpBlock.Paragraph("Initial state fields:"),
                HelpBlock.Bullets(
                    listOf(
                        "Steam flow t/h - total platen steam flow at t = 0 (default 100)",
                        "Pressure bar - absolute pressure at the platen inlet (default 100)",
                        "Steam T C - inlet steam temperature (default 400)",
                        "Spray T C (<250) - spray-water temperature (default 230)",
                        "Burners on - burners firing at t = 0 (default 2)",
                        "Duration min - simulation length (default 15)",
                    )
                ),
                HelpBlock.Paragraph(
                    "Scenario event at t = 300 s: enter new values for flow, spray and burners; " +
                        "leave a field empty to leave that quantity unchanged. Tap RUN SIMULATION - " +
                        "results, charts and calculation details appear below."
                ),
            ),
        ),
        HelpSection(
            "4. Spray water rules (important)",
            listOf(
                HelpBlock.Paragraph(
                    "The engine enforces the plant rule: spray water is always subcooled and below 250 C " +
                        "- in any situation."
                ),
                HelpBlock.Bullets(
                    listOf(
                        "Temperature limit: spray temperature must be below 250 C",
                        "Subcooling: spray temperature must be below Tsat at the mixing pressure " +
                            "(at 167 bar Tsat is ~350 C so the 250 C limit binds; at 20 bar Tsat is ~212 C " +
                            "so subcooling binds first)",
                    )
                ),
                HelpBlock.Paragraph(
                    "If a check fails the simulation refuses with a typed error explaining which condition " +
                    "failed. Why: desuperheating sprays must evaporate completely in the steam stream; " +
                    "saturated water flashes instead of mixing, which the model does not represent."
                ),
                HelpBlock.Code(
                    "h_mix = (m_s*h_s + m_sp*h_sp) / (m_s + m_sp)\n" +
                        "T_mix = T(P, h_mix)   <- IF97 Region 2 backward equation"
                ),
            ),
        ),
        HelpSection(
            "5. Reading the results and charts",
            listOf(
                HelpBlock.Bullets(
                    listOf(
                        "Outlet T (start -> end): steam temperature after the last segment",
                        "Metal T avg: average metal temperature across the 5 segments",
                        "Mixed T: post-spray mixed temperature (equals steam T if no spray)",
                        "h_i: internal convection coefficient, W/m2K",
                        "U: overall heat-transfer coefficient, W/m2K",
                        "Re: Reynolds number in a tube",
                        "Q absorbed: heat picked up by the steam, MW",
                    )
                ),
                HelpBlock.Paragraph(
                    "Interpreting step responses: after a burner step the metal heats first and the outlet " +
                        "follows after a short dead time; after a spray step the outlet falls almost immediately " +
                        "(spray acts at the inlet) and settles toward a new steady value."
                ),
            ),
        ),
        HelpSection(
            "6. Show calculation - transparency",
            listOf(
                HelpBlock.Paragraph(
                    "Tap Show calculation details at the bottom of the results to see, for the end state: " +
                        "P, T_out, rho, cp, mu, k, Pr, Re, Nu, h_i = Nu*k/D and U from the resistance chain. " +
                        "Every displayed result is traceable - a core requirement of this tool."
                ),
            ),
        ),
        HelpSection(
            "7. The Validation tab",
            listOf(
                HelpBlock.Paragraph(
                    "Three model levels run side by side on the same scenario: constant-U (800 W/m2K, " +
                        "plant YAML), dynamic h_i (single block), and the full 5-segment RK4 model."
                ),
                HelpBlock.Paragraph(
                    "The 800 vs ~170 W/m2K discrepancy (plant YAML low load vs Dittus-Boelter full load) is " +
                        "surfaced explicitly - both paths are provided as separate labeled modes, never blended."
                ),
            ),
        ),
        HelpSection(
            "8. Calibration against plant data",
            listOf(
                HelpBlock.Paragraph(
                    "Paste measured outlet temperatures (C, comma-separated, at least 10 points on the 1 s " +
                        "x 900 s grid) and tap Fit parameters. The fitter grid-searches F_platen (0.05..0.5) " +
                        "and h_o (20..500 W/m2K) minimizing RMSE, and reports MAE, RMSE, max error, bias and R2."
                ),
                HelpBlock.Paragraph(
                    "Fitted values are labeled empirical and kept separate from physical parameters - " +
                        "the app never overwrites physics with fitted numbers."
                ),
            ),
        ),
        HelpSection(
            "9. Physics - equations used",
            listOf(
                HelpBlock.Code(
                    "A_flow = pi*Di^2/4          Di = 29 mm, 43 panels x 4 tubes\n" +
                        "m_tube = m_total/172\n" +
                        "v      = m_tube/(rho*A_flow)\n" +
                        "Re     = rho*v*Di/mu\n" +
                        "Pr     = cp*mu/k\n" +
                        "Nu     = 0.023*Re^0.8*Pr^0.4\n" +
                        "h_i    = Nu*k/Di\n" +
                        "1/(U*Ao) = 1/(hi*Ai) + ln(Do/Di)/(2*pi*k*L) + R_foul + 1/(ho*Ao)\n" +
                        "Q_platen = n_burners * 40 MW * F_platen\n" +
                        "C_m*dTm/dt = Q_seg - hi*Ai*(Tm-Ts) - Q_loss\n" +
                        "C_s*dTs/dt = m*cp*(Tin-Ts) + hi*Ai*(Tm-Ts)"
                ),
                HelpBlock.Paragraph(
                    "Properties from IAPWS-IF97 (Regions 1/2/4), verified in tests against the official " +
                        "IAPWS tables. Dittus-Boelter validity (Pr 0.7-160, Re > 10000) is checked and warned, " +
                        "never silently applied. Solver: RK4, dt = 1 s, verified by timestep-halving convergence."
                ),
            ),
        ),
        HelpSection(
            "10. Units and errors",
            listOf(
                HelpBlock.Paragraph(
                    "UI shows bar, C, t/h, MW, W/m2K. The engine is pure SI internally " +
                        "(Pa, K, kg/s, J/kg, W, m, kg/m3, J/kgK, Pa.s, W/mK) - conversions happen once at the UI."
                ),
                HelpBlock.Bullets(
                    listOf(
                        "Spray temperature X C exceeds the 250 C plant limit -> lower spray T",
                        "Spray water must be subcooled: T=.. >= Tsat=.. at .. bar -> lower spray T below Tsat",
                        "IF97 state out of range -> bring P/T into the physical range",
                        "Steam flow must be positive -> enter a positive flow",
                    )
                ),
                HelpBlock.Paragraph(
                    "All errors are typed results - the app never crashes on bad input; it explains."
                ),
            ),
        ),
        HelpSection(
            "11. FAQ",
            listOf(
                HelpBlock.Bullets(
                    listOf(
                        "Outlet keeps rising the whole run? The metal starts at the initial steam " +
                            "temperature; if burner heat exceeds what the flow carries away the system heats " +
                            "toward a higher equilibrium - extend the duration or compare spray-on/off runs.",
                        "Tube velocity ~47 m/s at full load - too high? No: superheated steam at 167 bar/520 C " +
                            "has rho ~ 52 kg/m3, so high velocity is physically correct.",
                        "Why does metal respond before the outlet? The furnace heats the metal directly; " +
                            "steam receives heat only through the metal - that cascade delay is the physics.",
                        "Can I set spray to 300 C to test the limit? The engine refuses - the plant rule " +
                            "is working as intended.",
                        "Pressure absolute or gauge? Absolute (bar a) - IF97 works in absolute pressure.",
                    )
                ),
                HelpBlock.Paragraph(
                    "Property engine: vendored com.hummeling.if97 v2.1.0 by Hummeling Engineering BV, LGPL. " +
                        "Full guide: docs/USER_GUIDE.md and USER_GUIDE.pdf in the repository."
                ),
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<Int>()) }

    val sections = HelpContent.sections
    val filtered = remember(query) {
        if (query.isBlank()) sections.indices.toList()
        else sections.indices.filter { i ->
            val s = sections[i]
            s.title.contains(query, ignoreCase = true) ||
                s.body.any { block ->
                    when (block) {
                        is HelpBlock.Paragraph -> block.text.contains(query, ignoreCase = true)
                        is HelpBlock.Bullets -> block.items.any { it.contains(query, ignoreCase = true) }
                        is HelpBlock.Code -> block.text.contains(query, ignoreCase = true)
                    }
                }
        }
    }

    Column(modifier.fillMaxSize()) {
        // Search field + jump chips
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search help") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEachIndexed { i, s ->
                    FilterChip(
                        selected = expanded.contains(i),
                        onClick = {
                            query = ""
                            expanded = if (expanded.contains(i)) expanded - i else expanded + i
                        },
                        label = { Text(s.title.substringBefore('.'), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        SelectionContainer {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isBlank() && expanded.isEmpty()) {
                    item {
                        Text(
                            "Tap a section chip above (or tap any section) to expand. " +
                                "This is the complete user guide, also available as " +
                                "docs/USER_GUIDE.pdf in the repository.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(filtered) { idx ->
                    val s = sections[idx]
                    val isOpen = query.isNotBlank() || expanded.contains(idx)
                    Card(
                        onClick = {
                            expanded = if (expanded.contains(idx)) expanded - idx else expanded + idx
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                s.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (isOpen) {
                                Spacer(Modifier.height(6.dp))
                                s.body.forEach { block ->
                                    when (block) {
                                        is HelpBlock.Paragraph -> Text(
                                            block.text,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        is HelpBlock.Bullets -> Column {
                                            block.items.forEach {
                                                Row {
                                                    Text("•  ", style = MaterialTheme.typography.bodySmall)
                                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                        is HelpBlock.Code -> Text(
                                            block.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(6.dp),
                                                )
                                                .padding(8.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No section matches \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
