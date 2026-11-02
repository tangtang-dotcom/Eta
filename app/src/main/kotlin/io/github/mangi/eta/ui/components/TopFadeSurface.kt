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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 聊天列表的「已滚到顶栏下面」进度，0（在顶部）→ 1（内容已压到顶栏下）。
 *
 * dsh-go 的 `scrolledUnder` 同款用途：顶栏按它决定遮罩要不要淡入。
 *
 * **必须是可变状态而不是单向的 Float**：顶栏在 Scaffold 的 topBar 槽位，
 * 聊天列表在 content 槽位，两者是兄弟关系、各自独立组合。如果顶栏上方
 * 提供一个只读值，列表侧根本改不到它（读到的永远是默认值，顶栏就永远
 * 不画遮罩）。所以这里下发的是一个可写状态，上层创建、下层写入、顶栏读。
 */
val LocalChatScrollUnder = compositionLocalOf<MutableState<Float>?> { null }

/** 渐隐尾带：顶栏本体之外再多画这么长，避免在按钮正下方截断成一条硬线。 */
internal val TopFadeExtra = 12.dp

private const val FadeSamples = 12

/**
 * 顶栏底：把「整张页面背景」在顶栏位置重画一遍，向下羽化收边。
 *
 * 对应 dsh-go 的 `BackdropVeil.top` / `TopFade`：
 * - 实心段：顶边到 [solidUntil]，画面与下面的页面**完全同源**（同一张渐变+光晕），
 *   所以看不出顶栏这层边界；内容滚上来在这里被遮住；
 * - 羽化段：再往下到 [height]，沿 easeInOutCubic 削到全透明。
 *
 * 用 12 段采样而不是两色线性渐变——线性的斜坡在两端都会被读成一道硬边。
 *
 * [progress] 控制整体淡入：0 时不画（页面本来就是那张底色，遮不遮一样），
 * 内容滚到顶栏下时才淡入，避免静止时白白多画一层。
 */
@Composable
fun TopFadeSurface(
    progress: Float,
    height: Dp,
    solidUntil: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = AetherChatColors.backgroundGradientTop()
    val bottom = AetherChatColors.backgroundGradientBottom()
    val glowPrimary = AetherChatColors.backdropGlowPrimary()
    val glowSecondary = AetherChatColors.backdropGlowSecondary()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + TopFadeExtra),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val total = size.height
                    if (total <= 0f) return@drawWithContent
                    // 画的是「整张页面背景」在本区域的重绘，不是叠一层单色。
                    // dsh-go 的 BackdropVeil 复用同一个 _BackdropPainter，从页面顶端
                    // 起画、只保留顶部这一段，所以和真背景严丝合缝。
                    drawEtaBackdrop(top, bottom, glowPrimary, glowSecondary)
                    val solid = (solidUntil.toPx() / total).coerceIn(0f, 1f)
                    // 实心段始终保留：聊天页的页面背景铺不到状态栏区域，顶栏必须
                    // 自己把这一条填上，否则状态栏下面会透出内容/空白。
                    // 只有羽化段随 progress 淡入——它才是「内容滚上来时才需要」的部分。
                    drawFadeMask(color = top, solid = solid, feather = progress.coerceIn(0f, 1f))
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
private fun DrawScope.drawFadeMask(
    color: Color,
    solid: Float,
    feather: Float,
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
        // 实心段下方的不透明度：progress 为 0 时直接切到透明（只留实心段，
        // 下面是页面自己的底色，接得上）；progress 上来后逐渐变成完整羽化。
        val keep = (1f - eased) * feather
        colors.add(color.copy(alpha = keep))
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
        // 只保留已画背景在实心段的部分，向下按曲线削掉——这就是 dsh-go
        // _VeilPainter 里 BlendMode.dstIn + 缓动渐变的等价写法。
        blendMode = BlendMode.DstIn,
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
