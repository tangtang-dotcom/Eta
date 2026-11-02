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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 聊天列表的「已滚到顶栏下面」进度，0（在顶部）→ 1（内容已压到顶栏下）。
 *
 * 必须是可变状态而不是只读值：顶栏在 Scaffold 的 topBar 槽位、聊天列表在
 * content 槽位，两者是兄弟、各自独立组合。顶栏上方若只提供只读值，列表侧
 * 改不到它，顶栏就永远读默认值。
 */
val LocalChatScrollUnder = compositionLocalOf<MutableState<Float>?> { null }

/** 羽化段高度。dsh-go 的 BackdropVeil.top 用 14~16。 */
private val FeatherHeight = 14.dp

/** dsh-go 的 _VeilPainter 用 8 段采样。 */
private const val VeilSamples = 8

/**
 * 顶栏底：把「整张页面背景」在顶栏位置重画一遍，向下羽化收边。
 *
 * 严格复刻 dsh-go 的 `BackdropVeil.top` / `_VeilPainter.paint`：
 *
 * ```dart
 * canvas.saveLayer(rect, Paint());      // ① 必须开独立图层
 * canvas.clipRect(rect);
 * backdrop.paint(canvas, size);         // ② 画整张背景
 * canvas.drawRect(rect, dstIn + 渐变);   // ③ 在图层内削边
 * canvas.restore();
 * ```
 *
 * ①是重点：**不开 layer 就直接 DstIn，会削到下面已经画好的整屏内容**，
 * 实心段下方被过度削掉、露出底色——也就是那条黑线。
 */
@Composable
fun TopFadeSurface(
    progress: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = AetherChatColors.backgroundGradientTop()
    val bottom = AetherChatColors.backgroundGradientBottom()
    val glowPrimary = AetherChatColors.backdropGlowPrimary()
    val glowSecondary = AetherChatColors.backdropGlowSecondary()
    val feather = FeatherHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + feather),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val band = size.height
                    if (band <= 0f) return@drawWithContent
                    val featherPx = feather.toPx()
                    // dsh-go: fade = feather / band，按 1-fade+fade*t 取样。
                    val fade = (featherPx / band).coerceIn(0f, 1f)

                    // ①②③④ 必须在同一次 drawIntoCanvas 里完成：saveLayer 与
                    // restore 要落在同一个 canvas 包装上，中间还夹着 DstIn 绘制。
                    drawIntoCanvas { canvas ->
                        // ① 开独立图层：之后的 DstIn 只作用于本图层内容，
                        //    不会波及下面已经画好的页面。
                        val layerPaint = androidx.compose.ui.graphics.Paint()
                        canvas.saveLayer(
                            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, band),
                            layerPaint,
                        )
                        // ② 画整张页面背景（同一张渐变 + 同一对光晕）。
                        drawEtaBackdrop(top, bottom, glowPrimary, glowSecondary)
                        // ③ 图层内按缓动曲线削出透明下缘。
                        drawVeilMask(
                            fade = fade,
                            band = band,
                            featherAlpha = progress.coerceIn(0f, 1f),
                        )
                        // ④ 关图层。
                        canvas.restore()
                    }
                },
        )
        content()
    }
}

/**
 * 图层内的削边遮罩，取样照抄 dsh-go `_VeilPainter.paint`。
 *
 * - 顶部先放一个不透明黑 stop（实心段起点）；
 * - 之后 i = 0..8，stop 从 `1 - fade + fade * t` 走到 1，
 *   alpha 取 `1 - easeInOutCubic(t)`；
 * - 最底部补一个全透明 stop。
 *
 * 线性斜坡在两端会被读成硬边，所以必须用缓动曲线。
 */
private fun DrawScope.drawVeilMask(
    fade: Float,
    band: Float,
    featherAlpha: Float,
) {
    val stops = ArrayList<Float>(VeilSamples + 3)
    val colors = ArrayList<Color>(VeilSamples + 3)

    stops.add(0f)
    colors.add(Color.Black)

    for (i in 0..VeilSamples) {
        val t = i.toFloat() / VeilSamples
        stops.add(1f - fade + fade * t)
        val eased = easeInOutCubic(t)
        // 实心段（t=0）不透明；往下按曲线降到 0。
        // featherAlpha 只作用于羽化区间：实心段常驻，聊天页的页面背景铺不到
        // 状态栏，这一条必须自己填上。
        val alpha = ((1f - eased) * featherAlpha).coerceIn(0f, 1f)
        colors.add(Color.Black.copy(alpha = alpha))
    }

    stops.add(1f)
    colors.add(Color.Black.copy(alpha = 0f))

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = stops.zip(colors).toTypedArray(),
            startY = 0f,
            endY = band,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, band),
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
