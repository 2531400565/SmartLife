package com.smartlife.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
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
import com.smartlife.app.ui.screen.semester.SemesterScreen
import com.smartlife.app.ui.screen.timetable.TimetableScreen
import com.smartlife.app.ui.screen.todo.TodoScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** 底部导航 Tab 路由集合（非 Tab 路由不显示底栏）。 */
private val tabRoutes = setOf(
    Routes.DASHBOARD, Routes.TODO, Routes.FOCUS, Routes.TIMETABLE, Routes.PROFILE
)

/**
 * 应用根导航：底部 5 个 Tab + NavHost 路由容器。
 * 学期设置等二级页面为独立路由，隐藏底栏、使用顶栏返回。
 */
@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        BottomNavItem(Routes.DASHBOARD, "首页", Icons.Outlined.Home),
        BottomNavItem(Routes.TODO, "待办", Icons.AutoMirrored.Outlined.List),
        BottomNavItem(Routes.FOCUS, "专注", Icons.Outlined.Timer),
        BottomNavItem(Routes.TIMETABLE, "课表", Icons.Outlined.CalendarMonth),
        BottomNavItem(Routes.PROFILE, "我的", Icons.Outlined.Person)
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateToTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigateTodo = { navController.navigateToTab(Routes.TODO) },
                    onNavigateTimetable = { navController.navigateToTab(Routes.TIMETABLE) },
                    onNavigateFocus = { navController.navigateToTab(Routes.FOCUS) }
                )
            }
            composable(Routes.TODO) { TodoScreen() }
            composable(Routes.FOCUS) { FocusScreen() }
            composable(Routes.TIMETABLE) { TimetableScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onNavigateSemester = { navController.navigate(Routes.SEMESTER) }
                )
            }
            composable(Routes.SEMESTER) {
                SemesterScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 底部导航跳转：保留各 Tab 状态、单实例、回到栈顶。首页卡片跳转复用同一策略。 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
