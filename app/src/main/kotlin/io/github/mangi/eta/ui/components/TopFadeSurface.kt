package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 聊天列表的「已滚到顶栏下面」进度，0（在顶部）→ 1（内容已压到顶栏下）。
 *
 * 顶栏要按这个值渐隐自己的底，和 dsh-go 的 `scrolledUnder` 同一个思路：
 * 静止时页面是一整块干净的面，只有内容真滚上来才浮现一层页面色。
 *
 * 由聊天页的列表写入，顶栏读取。默认 null，表示当前页面没有可滚动的聊天内容
 * ——此时顶栏不画底。
 */
val LocalChatScrollUnder = compositionLocalOf<Float?> { null }

/** 渐隐尾带：顶栏本体之外再多画这么长，避免在按钮正下方截断成一条硬线。 */
internal val TopFadeExtra = 12.dp

private const val FadeSamples = 12

/**
 * 顶栏底：**不是**一条常驻色带，而是「页面底色向下渐隐」。
 *
 * 对应 dsh-go 的 `featherFade` / `TopFade`：
 * - 从顶边向下 [solidUntil] 保持完全不透明；
 * - 之后沿 easeInOutCubic 降到全透明，终点在 [height]。
 *
 * 用 12 段采样而不是两色线性渐变——线性的斜坡在两端都会被读成一道硬边，
 * 缓动曲线才不会。
 */
@Composable
fun TopFadeSurface(
    progress: Float,
    height: Dp,
    solidUntil: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + TopFadeExtra),
    ) {
        // 底层遮罩画一次渐变就用 graphicsLayer 的 alpha 控制显隐：
        // 滚动时每帧都在变，避免用一个 if 反复增删节点引起重组抖动。
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                .drawBehind {
                    val total = size.height
                    if (total <= 0f) return@drawBehind
                    val solid = (solidUntil.toPx() / total).coerceIn(0f, 1f)
                    drawFadeRect(color = color, solid = solid)
                },
        )
        content()
    }
}

/**
 * 画一层「上实下虚」的页面色。
 *
 * [solid] 是实心段占整个高度的比例。实心段之后用 easeInOutCubic 采
 * [FadeSamples] 段，逐段降低 alpha。
 */
private fun DrawScope.drawFadeRect(
    color: Color,
    solid: Float,
) {
    val colors = ArrayList<Color>(FadeSamples + 3)
    val stops = ArrayList<Float>(FadeSamples + 3)
    colors.add(color)
    stops.add(0f)
    colors.add(color)
    stops.add(solid)
    for (i in 1..FadeSamples) {
        val t = i.toFloat() / FadeSamples
        val eased = easeInOutCubic(t)
        colors.add(color.copy(alpha = color.alpha * (1f - eased)))
        stops.add(solid + (1f - solid) * t)
    }
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = stops.zip(colors).toTypedArray(),
            startY = 0f,
            endY = size.height,
        ),
        topLeft = Offset.Zero,
        size = size,
    )
}

/** 与 Flutter `Curves.easeInOutCubic` 等价。 */
private fun easeInOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) {
        4f * x * x * x
    } else {
        val f = -2f * x + 2f
        1f - (f * f * f) / 2f
    }
}

/**
 * 把「列表是否已滚到顶栏下」换算成 0..1 的进度。
 *
 * 用 [distance] 个像素的过渡区：从第一项刚好被顶栏盖住开始，到再多滑
 * [distance] 为止，避免刚一动就整个跳出来。
 */
fun scrollUnderProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    distance: Int = 120,
): Float {
    if (distance <= 0) return 0f
    if (firstVisibleItemIndex > 0) return 1f
    return (firstVisibleItemScrollOffset.toFloat() / distance).coerceIn(0f, 1f)
}

/** 让数值在 [target] 变化时平滑过渡，避免快速滚动时渐隐跳变。 */
@Composable
fun animatedFadeProgress(target: Float): Float {
    val state = remember { Animatable(target) }
    LaunchedEffect(target) {
        state.animateTo(target, tween(durationMillis = 120, easing = LinearEasing))
    }
    return state.value
}
