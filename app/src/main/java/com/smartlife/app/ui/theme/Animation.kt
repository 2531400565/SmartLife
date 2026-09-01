package com.smartlife.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * 全 App 动画规范（v2.0 P4 动画统一，单一事实来源）。
 *
 * 时长三档 + 两个特殊值：
 * - [ShortMs]     200ms：轻量反馈（删除缩放消失、小型显隐）；
 * - [MediumMs]    250ms：内容切换（AnimatedContent、列表重排、进度条、页面淡入淡出）；
 * - [LongMs]      300ms：强调过渡（Hero 淡入、卡片/列表项入场）；
 * - [FocusRingMs] 800ms：专注环形进度（整段时长，刻意缓慢以配合 1s 心跳刷新）；
 * - [CountUpMs]   700ms：数字滚动（CountUpText 默认）。
 *
 * 所有页面动画时长一律引用本规范，禁止魔法数字。
 * 颜色与形状仍走 MaterialTheme；本文件只负责时序与缓动。
 */
object AnimSpec {
    const val ShortMs = 200
    const val MediumMs = 250
    const val LongMs = 300
    const val FocusRingMs = 800
    const val CountUpMs = 700

    /** M3 标准缓动（FastOutSlowInEasing）。 */
    val standard = FastOutSlowInEasing

    /** 卡片/列表项统一入场：淡入 + 轻微上移。 */
    fun enterFadeSlide(durationMs: Int = LongMs): EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = durationMs, easing = standard)) +
            slideInVertically(animationSpec = tween(durationMillis = durationMs, easing = standard)) { fullHeight ->
                fullHeight / 12
            }

    /** 统一出场：淡出 + 轻微下移。 */
    fun exitFadeSlide(durationMs: Int = MediumMs): ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = durationMs, easing = standard)) +
            slideOutVertically(animationSpec = tween(durationMillis = durationMs, easing = standard)) { fullHeight ->
                fullHeight / 12
            }
}
