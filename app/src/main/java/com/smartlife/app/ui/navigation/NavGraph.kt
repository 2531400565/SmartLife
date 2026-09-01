package com.smartlife.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartlife.app.NavEvent
import com.smartlife.app.ui.screen.dashboard.DashboardScreen
import com.smartlife.app.ui.screen.analytics.AnalyticsScreen
import com.smartlife.app.ui.screen.exam.ExamListScreen
import com.smartlife.app.ui.screen.focus.FocusScreen
import com.smartlife.app.ui.screen.profile.ProfileScreen
import com.smartlife.app.ui.screen.reminder.CourseReminderScreen
import com.smartlife.app.ui.screen.semester.SemesterCoursesScreen
import com.smartlife.app.ui.screen.semester.SemesterScreen
import com.smartlife.app.ui.screen.timetable.TimetableScreen
import com.smartlife.app.ui.screen.todo.TodoScreen
import com.smartlife.app.ui.theme.AnimSpec

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** 底部导航 Tab 路由集合（非 Tab 路由不显示底栏）。 */
private val tabRoutes = setOf(
    Routes.DASHBOARD, Routes.TODO, Routes.FOCUS, Routes.TIMETABLE, Routes.PROFILE
)

/** 二级页面路由集合。与 [tabRoutes] 共同构成合法路由白名单，用于校验外部跳转目标。 */
private val secondaryRoutes = setOf(
    Routes.SEMESTER, Routes.SEMESTER_COURSES, Routes.COURSE_REMINDER,
    Routes.EXAM_LIST, Routes.ANALYTICS
)

/**
 * 应用根导航：底部 5 个 Tab + NavHost 路由容器。
 * 学期设置等二级页面为独立路由，隐藏底栏、使用顶栏返回。
 *
 * @param navEvent 外部（通知 / 小组件）跳转请求。
 *                 冷启动时 [NavEvent.route] 直接作为 startDestination；
 *                 热启动由下面的 LaunchedEffect 按 [NavEvent.seq] 响应——
 *                 只比较路由会导致重复点击同一入口无反应，因此以序号为触发键。
 */
@Composable
fun AppNavigation(
    navEvent: NavEvent = NavEvent()
) {
    val navController: NavHostController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // NavHost 的起始目的地（仅在首次组合生效）。非法路由一律回落到首页。
    val startDestination = navEvent.route?.takeIf { it in tabRoutes || it in secondaryRoutes }
        ?: Routes.DASHBOARD

    // 通知 / 小组件点击：按事件序号响应，保证连续点击同一入口也能跳转。
    LaunchedEffect(navEvent.seq) {
        val route = navEvent.route
        if (!route.isNullOrBlank() && route != currentRoute &&
            (route in tabRoutes || route in secondaryRoutes)
        ) {
            if (route in tabRoutes) navController.navigateToTab(route)
            else navController.navigate(route) { launchSingleTop = true }
        }
    }

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
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            // v2.0 P4：全 App 页面切换统一淡入淡出（250ms），与 Tab 内动画节奏一致
            enterTransition = { fadeIn(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) },
            exitTransition = { fadeOut(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) },
            popEnterTransition = { fadeIn(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) },
            popExitTransition = { fadeOut(tween(AnimSpec.MediumMs, easing = AnimSpec.standard)) }
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigateTodo = { navController.navigateToTab(Routes.TODO) },
                    onNavigateTimetable = { navController.navigateToTab(Routes.TIMETABLE) },
                    onNavigateFocus = { navController.navigateToTab(Routes.FOCUS) },
                    onNavigateExamList = { navController.navigate(Routes.EXAM_LIST) }
                )
            }
            composable(Routes.TODO) { TodoScreen() }
            composable(Routes.FOCUS) {
                // 小组件「一键开始专注」：以事件序号驱动，保证每次点击都重新触发
                FocusScreen(autoStartSeq = if (navEvent.startFocus) navEvent.seq else 0)
            }
            composable(Routes.TIMETABLE) { TimetableScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onNavigateSemester = { navController.navigate(Routes.SEMESTER) },
                    onNavigateSemesterCourses = { navController.navigate(Routes.SEMESTER_COURSES) },
                    onNavigateCourseReminder = { navController.navigate(Routes.COURSE_REMINDER) },
                    onNavigateExamList = { navController.navigate(Routes.EXAM_LIST) },
                    onNavigateAnalytics = { navController.navigate(Routes.ANALYTICS) }
                )
            }
            composable(Routes.SEMESTER) {
                SemesterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEMESTER_COURSES) {
                SemesterCoursesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COURSE_REMINDER) {
                CourseReminderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EXAM_LIST) {
                ExamListScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(onBack = { navController.popBackStack() })
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
