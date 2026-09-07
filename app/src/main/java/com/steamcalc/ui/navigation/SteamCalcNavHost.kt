package com.steamcalc.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.steamcalc.ui.screens.EnergyBalanceScreen
import com.steamcalc.ui.screens.QuickCalculatorScreen
import com.steamcalc.ui.screens.SprayImpactScreen
import com.steamcalc.ui.screens.TransientSimulationScreen
import java.net.URLEncoder

// ─── Route Definitions ────────────────────────────────────────────────────

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object QuickCalc : Screen("quick_calc", "Required Spray", Icons.Default.Calculate)
    data object SprayImpact : Screen("spray_impact", "Spray Impact", Icons.Default.ChangeHistory)
    data object Transient : Screen("transient", "Transient", Icons.Default.Timeline)
}

// ─── Nav Host ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamCalcNavHost() {
    val navController = rememberNavController()
    val screens = listOf(Screen.QuickCalc, Screen.SprayImpact, Screen.Transient)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on main screens (not energy balance)
    val showBottomBar = screens.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Steam Mixer Calculator v3") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    screens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.QuickCalc.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.QuickCalc.route) {
                QuickCalculatorScreen(
                    onNavigateToEnergyBalance = { data ->
                        val encoded = URLEncoder.encode(data, "UTF-8")
                        navController.navigate("energy_balance/$encoded")
                    }
                )
            }

            composable(Screen.SprayImpact.route) {
                SprayImpactScreen(
                    onNavigateToEnergyBalance = { data ->
                        val encoded = URLEncoder.encode(data, "UTF-8")
                        navController.navigate("energy_balance/$encoded")
                    }
                )
            }

            composable(Screen.Transient.route) {
                TransientSimulationScreen(
                    onNavigateToEnergyBalance = { data ->
                        val encoded = URLEncoder.encode(data, "UTF-8")
                        navController.navigate("energy_balance/$encoded")
                    }
                )
            }

            composable(
                route = "energy_balance/{data}",
                arguments = listOf(navArgument("data") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedData = backStackEntry.arguments?.getString("data") ?: ""
                val data = java.net.URLDecoder.decode(encodedData, "UTF-8")
                EnergyBalanceScreen(
                    data = data,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
