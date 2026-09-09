package com.platen4.superheater.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platen4.superheater.engine.EventRow
import com.platen4.superheater.engine.ScenarioTimeline
import com.platen4.superheater.ui.components.HelpScreen
import com.platen4.superheater.ui.components.ScenarioEditorScreen
import com.platen4.superheater.ui.components.SimulationScreen
import com.platen4.superheater.ui.components.ValidationScreen

/** Root screen with tabs: Simulate, Scenarios, Validation, Help, About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Simulate", "Scenarios", "Validation", "Help", "About")

    // Shared scenario timeline — hoisted so edits survive tab switches and the
    // Simulate tab always runs exactly what the Scenarios tab shows.
    var scenarioRows by remember { mutableStateOf(ScenarioTimeline.defaultRows()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platen Superheater — Unit №4") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> SimulationScreen(
                    scenarioRows = scenarioRows,
                    onScenarioRowsChange = { scenarioRows = it },
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> ScenarioEditorScreen(
                    rows = scenarioRows,
                    onRowsChange = { scenarioRows = it },
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> ValidationScreen(Modifier.fillMaxSize())
                3 -> HelpScreen(Modifier.fillMaxSize())
                else -> AboutScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Platen Superheater Transient Simulation — Unit №4", style = MaterialTheme.typography.titleLarge)
        Text("325 MW natural-circulation drum boiler. 43 panels × 4 tubes (172), uniform tube OD 57 / ID 41 mm.")
        Text("Property engine: IAPWS-IF97 (vendored com.hummeling.if97 v2.1.0, LGPL).")
        Text("Model: uniform lumped energy balance (metal + steam nodes), RK4 solver, dynamic Re→Pr→Nu→h_i→U chain.")
        Text("Spray rule: spray water is always subcooled, 100–180 °C (enforced in the engine).")
        Text("Scenarios tab: add any number of events per quantity with own times; enable/disable each event; single or mixed runs.")
        Text("Metal temperature is a user input (default 450 °C).", style = MaterialTheme.typography.bodySmall)
        Text("Thermodynamics library: IF97 by Hummeling Engineering BV under LGPL — https://www.if97.software")
    }
}
