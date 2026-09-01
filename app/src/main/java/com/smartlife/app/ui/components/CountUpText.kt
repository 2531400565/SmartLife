package com.smartlife.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.smartlife.app.ui.theme.AnimSpec

/**
 * 数字滚动（CountUp）文本（v1.3 P1 / P2 共用）。
 *
 * 行为：
 * - 首次出现：从 0 递增到目标值（CountUp）；
 * - 目标值变化：从当前显示值平滑过渡到新值，不会先跳回 0。
 *
 * 仅影响显示，不参与任何业务逻辑、不改动数据层。
 *
 * @param value          目标数值
 * @param suffix         显示在数字后的后缀（如 "%"）
 * @param durationMillis 动画时长，默认 [AnimSpec.CountUpMs]ms
 */
@Composable
fun CountUpText(
    value: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    durationMillis: Int = AnimSpec.CountUpMs
) {
    // Animatable 起始于 0：首次进入页面即自然形成 0 → value 的 CountUp 效果
    val animated = remember { Animatable(0f) }

    LaunchedEffect(value) {
        animated.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(durationMillis = durationMillis)
        )
    }

    Text(
        text = "${animated.value.toInt()}$suffix",
        modifier = modifier,
        style = style,
        color = color
    )
}
