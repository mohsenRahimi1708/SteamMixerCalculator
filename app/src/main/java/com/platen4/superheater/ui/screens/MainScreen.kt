package com.platen4.superheater.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platen4.superheater.ui.components.SimulationScreen
import com.platen4.superheater.ui.components.ValidationScreen

/** Root screen with tabs: Simulate, Validation, About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Simulate", "Validation", "About")

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
                0 -> SimulationScreen(Modifier.fillMaxSize())
                1 -> ValidationScreen(Modifier.fillMaxSize())
                else -> AboutScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Platen Superheater Transient Simulation — Unit №4", style = MaterialTheme.typography.titleLarge)
        Text("325 MW natural-circulation drum boiler. 43 panels × 4 tubes × 8 passes.")
        Text("Property engine: IAPWS-IF97 (vendored com.hummeling.if97 v2.1.0, LGPL).")
        Text("Model: 5-segment distributed energy balance, RK4 solver, dynamic Re→Pr→Nu→h_i→U chain.")
        Text("Spray rule: spray water is always subcooled and below 250 °C (enforced in the engine).")
        Text("Thermodynamics library: IF97 by Hummeling Engineering BV under LGPL — https://www.if97.software")
    }
}