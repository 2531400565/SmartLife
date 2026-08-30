package com.smartlife.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartlife.app.ui.screen.dashboard.DashboardScreen
import com.smartlife.app.ui.screen.focus.FocusScreen
import com.smartlife.app.ui.screen.profile.ProfileScreen
import com.smartlife.app.ui.screen.timetable.TimetableScreen
import com.smartlife.app.ui.screen.todo.TodoScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 应用根导航：底部 5 个 Tab + NavHost 路由容器。
 * Phase 0 仅搭建骨架，各 Screen 为占位实现，业务功能在后续模块填充。
 */
@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()

    val items = listOf(
        BottomNavItem(Routes.DASHBOARD, "首页", Icons.Outlined.Home),
        BottomNavItem(Routes.TODO, "待办", Icons.AutoMirrored.Outlined.List),
        BottomNavItem(Routes.FOCUS, "专注", Icons.Outlined.Timer),
        BottomNavItem(Routes.TIMETABLE, "课表", Icons.Outlined.CalendarMonth),
        BottomNavItem(Routes.PROFILE, "我的", Icons.Outlined.Person)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.TODO) { TodoScreen() }
            composable(Routes.FOCUS) { FocusScreen() }
            composable(Routes.TIMETABLE) { TimetableScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }
}
