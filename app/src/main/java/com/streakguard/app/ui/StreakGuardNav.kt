package com.streakguard.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.home.HomeScreen
import com.streakguard.app.ui.leetcode.LeetCodeScreen
import com.streakguard.app.ui.settings.SettingsScreen
import com.streakguard.app.ui.theme.Primary

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("today", "Today", Icons.Filled.DateRange),
    Tab("leetcode", "LeetCode", Icons.Filled.List),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun StreakGuardNav(container: AppContainer) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                Tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            indicatorColor = Primary.copy(alpha = 0.16f),
                        ),
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") { HomeScreen(container = container) }
            composable("leetcode") { LeetCodeScreen(container = container) }
            composable("settings") { SettingsScreen(container = container) }
        }
    }
}
