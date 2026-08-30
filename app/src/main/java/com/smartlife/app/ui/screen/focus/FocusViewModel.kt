package com.smartlife.app.ui.screen.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smartlife.app.data.local.entity.FocusSessionEntity
import com.smartlife.app.di.ServiceLocator
import com.smartlife.app.worker.FocusReminderWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** 番茄钟阶段。 */
enum class FocusPhase { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * 专注页 UI 状态。
 */
data class FocusUiState(
    val phase: FocusPhase = FocusPhase.IDLE,  // 当前阶段
    val plannedMinutes: Int = 25,             // 计划时长（25/45/60）
    val remainingSeconds: Int = 25 * 60,      // 剩余秒数
    val todaySeconds: Long = 0L,              // 今日累计专注秒数（实时）
    val todaySessions: Int = 0,               // 今日完成的专注轮数
    val loading: Boolean = true
)

/**
 * 专注（番茄钟）ViewModel。
 * - 计时基于【绝对时间戳】计算剩余（deadline - now），协程仅做每秒刷新，
 *   因此切页面 / 进程挂起后回来，剩余时间依然准确。
 * - 开始 / 继续时调度 WorkManager 一次性延迟任务，到点发本地通知；
 *   即使进程被系统回收，通知也会按时触发（"退出页面后计时不中断"）。
 * - 完成 / 结束自动写入 FocusSession（completed 区分是否完整跑完）。
 */
class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val focusRepository = ServiceLocator.focusSessionRepository(application)

    private val _uiState = MutableStateFlow(FocusUiState())

    private var tickJob: Job? = null        // 每秒刷新协程
    private var sessionStartAt: Long = 0L   // 当前运行段起点（start/resume 时刻）
    private var accumulatedSeconds: Long = 0L // 暂停前累计专注秒数
    private var deadline: Long = 0L         // 本轮计划结束时刻
    private var pausedRemaining: Int = 0    // 暂停时的剩余秒数

    /** 今日统计（实时，DB 变更自动刷新）+ 计时状态。 */
    val uiState: StateFlow<FocusUiState> = combine(
        focusRepository.todayFocusSeconds(),
        focusRepository.todayCompletedCount(),
        _uiState
    ) { seconds, sessions, state ->
        state.copy(
            todaySeconds = seconds,
            todaySessions = sessions,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusUiState()
    )

    // ===== 时长选择（仅空闲时）=====

    /**
     * 选择专注时长（预设或自定义均走这里）。
     * 统一做范围钳制：即便外部传入 0 / 负数 / 超大值，也会被限制在
     * [MIN_DURATION_MINUTES, MAX_DURATION_MINUTES] 内，保证倒计时与 FocusSession 记录始终合理。
     */
    fun selectDuration(minutes: Int) {
        if (_uiState.value.phase != FocusPhase.IDLE) return
        val safeMinutes = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        _uiState.update {
            it.copy(plannedMinutes = safeMinutes, remainingSeconds = safeMinutes * 60)
        }
    }

    // ===== 开始 =====

    fun start() {
        val state = _uiState.value
        sessionStartAt = System.currentTimeMillis()
        accumulatedSeconds = 0L
        deadline = sessionStartAt + state.plannedMinutes * 60_000L
        scheduleReminder(state.plannedMinutes * 60L)
        startTicker()
        _uiState.update {
            it.copy(phase = FocusPhase.RUNNING, remainingSeconds = state.plannedMinutes * 60)
        }
    }

    // ===== 暂停 =====

    fun pause() {
        if (_uiState.value.phase != FocusPhase.RUNNING) return
        accumulatedSeconds += (System.currentTimeMillis() - sessionStartAt) / 1000
        pausedRemaining = _uiState.value.remainingSeconds
        tickJob?.cancel()
        cancelReminder()
        _uiState.update { it.copy(phase = FocusPhase.PAUSED) }
    }

    // ===== 继续 =====

    fun resume() {
        if (_uiState.value.phase != FocusPhase.PAUSED) return
        sessionStartAt = System.currentTimeMillis()
        deadline = sessionStartAt + pausedRemaining * 1000L
        scheduleReminder(pausedRemaining.toLong())
        startTicker()
        _uiState.update { it.copy(phase = FocusPhase.RUNNING) }
    }

    // ===== 提前结束（保存未完成记录）=====

    fun finish() {
        val state = _uiState.value
        if (state.phase != FocusPhase.RUNNING && state.phase != FocusPhase.PAUSED) return
        val runningExtra = if (state.phase == FocusPhase.RUNNING) {
            (System.currentTimeMillis() - sessionStartAt) / 1000
        } else {
            0L
        }
        val actual = (accumulatedSeconds + runningExtra).coerceAtMost(state.plannedMinutes * 60L)
        tickJob?.cancel()
        cancelReminder()
        viewModelScope.launch {
            focusRepository.addSession(
                FocusSessionEntity(
                    startedAt = sessionStartAt,
                    plannedMinutes = state.plannedMinutes,
                    actualSeconds = actual,
                    completed = false
                )
            )
        }
        resetToIdle(state.plannedMinutes)
    }

    // ===== 倒计时到点（自动完成）=====

    private fun onTickerComplete() {
        val state = _uiState.value
        tickJob?.cancel()
        cancelReminder()
        viewModelScope.launch {
            focusRepository.addSession(
                FocusSessionEntity(
                    startedAt = sessionStartAt,
                    plannedMinutes = state.plannedMinutes,
                    actualSeconds = state.plannedMinutes * 60L,
                    completed = true
                )
            )
        }
        // 显示"完成"阶段（通知已由 WorkManager 发出），点击"开始新一轮"即可重置
        _uiState.update {
            it.copy(phase = FocusPhase.FINISHED, remainingSeconds = it.plannedMinutes * 60)
        }
    }

    private fun resetToIdle(plannedMinutes: Int) {
        _uiState.update {
            it.copy(
                phase = FocusPhase.IDLE,
                plannedMinutes = plannedMinutes,
                remainingSeconds = plannedMinutes * 60
            )
        }
    }

    // ===== 每秒刷新（基于绝对时间戳）=====

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((deadline - System.currentTimeMillis()) / 1000)
                    .toInt()
                    .coerceAtLeast(0)
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining <= 0) {
                    onTickerComplete()
                    break
                }
                delay(500) // 半秒粒度，避免秒级累积漂移
            }
        }
    }

    // ===== WorkManager 本地通知调度 =====

    private fun scheduleReminder(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<FocusReminderWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(REMINDER_TAG, ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancelReminder() {
        WorkManager.getInstance(getApplication()).cancelUniqueWork(REMINDER_TAG)
    }

    companion object {
        private const val REMINDER_TAG = "focus_reminder"

        /**
         * 专注时长允许范围（分钟）。预设值与自定义输入统一受此约束：
         * 禁止 0、负数，上限 180 分钟，避免过长倒计时与异常统计。
         */
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 180

        /** 预设时长（分钟），UI 与自定义共用同一套选择逻辑。 */
        val PRESET_MINUTES = listOf(15, 25, 45, 60)

        /** ViewModel 工厂（AndroidViewModel 需要 Application）。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                FocusViewModel(app)
            }
        }
    }
}
