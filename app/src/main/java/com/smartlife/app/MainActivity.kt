package com.smartlife.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smartlife.app.data.repository.SettingsRepository
import com.smartlife.app.ui.navigation.AppNavigation
import com.smartlife.app.ui.theme.SmartLifeTheme
import com.smartlife.app.ui.theme.ThemeMode
import com.smartlife.app.widget.BaseWidgetProvider

/**
 * 外部（通知 / 桌面小组件）带入的跳转请求。
 *
 * [seq] 为单调递增的事件序号：Compose 的 LaunchedEffect 依赖「参数变化」触发，
 * 若只传路由字符串，连续两次点击同一入口（如「待办」→ 切回首页 → 再点「待办」）
 * 参数未变化，第二次点击将毫无反应。携带序号可保证每次点击都被响应。
 */
data class NavEvent(
    val route: String? = null,
    val startFocus: Boolean = false,
    val seq: Int = 0
)

class MainActivity : ComponentActivity() {

    /** 通知 / 小组件带入的跳转请求（冷启动作为 startDestination，热启动由导航层响应）。 */
    private var navEvent by mutableStateOf(NavEvent())

    /** 事件序号发号器（见 [NavEvent.seq]）。 */
    private var navSeq = 0

    /** Android 13+ 通知权限申请器（已授权时系统会直接回调，无副作用）。 */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 忽略结果 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navEvent = readNavEvent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            // 读取用户选择的主题模式（DataStore 持久化），实时生效
            val themeMode by SettingsRepository.themeMode(this)
                .collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SmartLifeTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 底部导航 + 五个模块的路由容器
                    AppNavigation(navEvent = navEvent)
                }
            }
        }
    }

    /** 应用已在前台时，通知 / 小组件点击走此回调。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navEvent = readNavEvent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时顺带刷新桌面小组件（系统的 30 分钟周期刷新较慢）
        BaseWidgetProvider.updateAllWidgets(this)
    }

    /** 读取跳转意图；每次调用分配新的序号，保证重复点击同一入口也能被响应。 */
    private fun readNavEvent(source: Intent?): NavEvent = NavEvent(
        route = source?.getStringExtra(EXTRA_NAVIGATE_ROUTE)?.takeIf { it.isNotBlank() },
        startFocus = source?.getBooleanExtra(EXTRA_START_FOCUS, false) ?: false,
        seq = ++navSeq
    )

    /** Android 13（API 33）起需要运行时申请通知权限，否则通知不会展示。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** 通知 / 小组件 PendingIntent 携带的目标路由键。 */
        const val EXTRA_NAVIGATE_ROUTE = "extra_navigate_route"

        /** 小组件「一键开始专注」标记键。 */
        const val EXTRA_START_FOCUS = "extra_start_focus"
    }
}
