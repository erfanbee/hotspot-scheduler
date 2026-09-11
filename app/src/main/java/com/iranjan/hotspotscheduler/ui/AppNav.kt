package com.iranjan.hotspotscheduler.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.calibration.CalibrationScreen
import com.iranjan.hotspotscheduler.ui.editor.RoutineEditorScreen
import com.iranjan.hotspotscheduler.ui.routines.RoutinesScreen
import com.iranjan.hotspotscheduler.ui.setup.SetupScreen
import com.iranjan.hotspotscheduler.ui.usage.UsageScreen

private data class BottomItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val items = listOf(
        BottomItem("routines", Icons.Filled.AccessTime, R.string.routines_title),
        BottomItem("usage", Icons.Filled.DataUsage, R.string.usage_title),
        BottomItem("setup", Icons.Filled.Settings, R.string.setup_title)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "routines",
            modifier = Modifier.padding(padding)
        ) {
            composable("routines") {
                RoutinesScreen(
                    onNew = { navController.navigate("editor/-1") },
                    onEdit = { id -> navController.navigate("editor/$id") }
                )
            }
            composable("usage") { UsageScreen() }
            composable("setup") {
                SetupScreen(onCalibrate = { navController.navigate("calibration") })
            }
            composable("editor/{routineId}") { entry ->
                val id = entry.arguments?.getString("routineId")?.toLongOrNull() ?: -1L
                RoutineEditorScreen(routineId = id, onDone = { navController.popBackStack() })
            }
            composable("calibration") { CalibrationScreen() }
        }
    }
}
