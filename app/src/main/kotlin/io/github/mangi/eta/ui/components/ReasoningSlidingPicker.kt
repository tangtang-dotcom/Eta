package io.github.mangi.eta.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import io.github.mangi.eta.data.model.ReasoningEffort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.PI
import kotlin.math.sin

/**
 * 思考档位滑条：拖动选择推理深度，松手回写。
 *
 * 结构来自 IRIDIS 的分档滑块（点按与拖动分离、松手才提交、带无障碍 setProgress），
 * 轨道特效来自 rikkahub 的思考深度滑条（流动光带、随深度增多的上升火花、MAX 彩虹）。
 */
@Composable
internal fun ReasoningSlidingPicker(
    effort: ReasoningEffort,
    options: List<ReasoningEffort>,
    onEffortChange: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.size < 2) return
    val count = options.size
    val currentIndex = options.indexOf(effort).coerceAtLeast(0)
    var sliderIndex by remember { mutableIntStateOf(currentIndex) }
    LaunchedEffect(currentIndex, count) { sliderIndex = currentIndex.coerceIn(0, count - 1) }

    val haptic = LocalHapticFeedback.current
    val latestChange by rememberUpdatedState(onEffortChange)
    val latestOptions by rememberUpdatedState(options)
    val latestEffort by rememberUpdatedState(effort)

    val previewLevel = options.getOrElse(sliderIndex) { options.last() }
    // 每滑过一档给一次轻触感，滑动过程本身不写设置。
    fun moveTo(index: Int) {
        val clamped = index.coerceIn(0, count - 1)
        if (clamped != sliderIndex) {
            sliderIndex = clamped
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // 松手才提交：避免每滑一格都写一次设置。
    fun commit() {
        latestOptions.getOrNull(sliderIndex)?.let {
            if (it != latestEffort) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                latestChange(it)
            }
        }
    }

    val wordColor by animateColorAsState(
        targetValue = reasoningSurgeColor(previewLevel),
        animationSpec = tween(180),
        label = "reasoningWord",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = effortDisplayName(previewLevel),
                style = TextStyle(
                    color = wordColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            BasicText(
                text = effortWireLabel(previewLevel),
                style = TextStyle(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        val trackHeight = ReasoningSliderStyle.TrackHeight
        val knobRadius = ReasoningSliderStyle.KnobRadius
        val density = LocalDensity.current
        val knobPx = with(density) { (knobRadius * 2).toPx() }
        val trackColor = MiuixTheme.colorScheme.surfaceContainerHigh
        val animPos by animateFloatAsState(
            targetValue = sliderIndex.toFloat(),
            animationSpec = tween(160),
            label = "reasoningKnob",
        )
        // 无条件调用：Compose 要求 composable 调用位置稳定，不能放进 if 分支。
        val transition = rememberInfiniteTransition(label = "reasoningSurge")
        // 深度越深光带流得越快：AUTO 约 3.4s 一圈，MAX 约 0.7s。
        val surgeDepth = previewLevel.ordinal / (ReasoningEffort.entries.size - 1).toFloat()
        val flowPeriodMs = (3400 - (2600 * surgeDepth)).roundToInt().coerceAtLeast(700)
        val surgePhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(flowPeriodMs, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "surgePhase",
        )
        val surgePulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween((flowPeriodMs / 2).coerceAtLeast(350), easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "surgePulse",
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReasoningSliderStyle.Height)
                .semantics {
                    contentDescription = "思考深度"
                    stateDescription = effortDisplayName(previewLevel)
                    setProgress { v ->
                        moveTo((v * (count - 1)).roundToInt())
                        commit()
                        true
                    }
                }
                .pointerInput(count) {
                    detectTapGestures { offset ->
                        moveTo(reasoningStopAt(offset.x, size.width.toFloat(), knobPx, count))
                        commit()
                    }
                }
                .pointerInput(count) {
                    detectHorizontalDragGestures(
                        onDragEnd = { commit() },
                        onDragCancel = { commit() },
                    ) { change, _ ->
                        change.consume()
                        moveTo(reasoningStopAt(change.position.x, size.width.toFloat(), knobPx, count))
                    }
                },
        ) {
            drawReasoningSurgeTrack(
                count = count,
                knobX = reasoningKnobX(animPos, size.width, knobPx, count),
                level = previewLevel,
                trackHeightPx = trackHeight.toPx(),
                knobRadiusPx = knobRadius.toPx(),
                surfaceColor = trackColor,
                phase = surgePhase,
                pulse = surgePulse,
            )
        }
    }
}

private object ReasoningSliderStyle {
    val TrackHeight = 8.dp
    // 滑块直径 14dp；光晕高 12dp，比滑块略小一圈，滑块边缘微微露出。
    val KnobRadius = 7.dp
    val Height = 44.dp
}

/** 滑块中心可移动区间：knob/2 到 width - knob/2，两端不会越界。 */
internal fun reasoningStopAt(x: Float, width: Float, knob: Float, count: Int): Int {
    if (count <= 1) return 0
    val usable = (width - knob).coerceAtLeast(1f)
    val ratio = ((x - knob / 2f) / usable).coerceIn(0f, 1f)
    return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

internal fun reasoningKnobX(pos: Float, width: Float, knob: Float, count: Int): Float {
    if (count <= 1) return width / 2f
    val clamped = pos.coerceIn(0f, (count - 1).toFloat())
    return knob / 2f + (width - knob).coerceAtLeast(0f) * (clamped / (count - 1))
}

// ── 特效调色板（沿用 rikkahub 的冷→暖序列，Eta 多一档 DEFAULT 补中性蓝） ────────────
private val SurgeOffColor = Color(0xFF8B96A3)
private val SurgeDefaultColor = Color(0xFF7EA8C9)
private val SurgeMinimalColor = Color(0xFF29B6F6)
private val SurgeLowColor = Color(0xFF26C6DA)
private val SurgeMediumColor = Color(0xFF66BB6A)
private val SurgeHighColor = Color(0xFFFFB300)
private val SurgeXHighColor = Color(0xFFFB8C00)
private val SurgeMaxColor = Color(0xFFE53935)

private val SurgeRainbow = listOf(
    Color(0xFFFF5252),
    Color(0xFFFF9800),
    Color(0xFFFFEB3B),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFF9C27B0),
    Color(0xFFFF5252),
)

internal fun reasoningSurgeColor(level: ReasoningEffort): Color = when (level) {
    ReasoningEffort.OFF -> SurgeOffColor
    ReasoningEffort.DEFAULT -> SurgeDefaultColor
    ReasoningEffort.MINIMAL -> SurgeMinimalColor
    ReasoningEffort.LOW -> SurgeLowColor
    ReasoningEffort.MEDIUM -> SurgeMediumColor
    ReasoningEffort.HIGH -> SurgeHighColor
    ReasoningEffort.XHIGH -> SurgeXHighColor
    ReasoningEffort.MAX -> SurgeMaxColor
}

private fun effortDisplayName(level: ReasoningEffort): String = when (level) {
    ReasoningEffort.OFF -> "关闭"
    ReasoningEffort.DEFAULT -> "默认"
    ReasoningEffort.MINIMAL -> "极简"
    ReasoningEffort.LOW -> "快速"
    ReasoningEffort.MEDIUM -> "均衡"
    ReasoningEffort.HIGH -> "深入"
    ReasoningEffort.XHIGH -> "研究"
    ReasoningEffort.MAX -> "极致"
}

private fun effortWireLabel(level: ReasoningEffort): String = when (level) {
    ReasoningEffort.OFF -> "Off"
    ReasoningEffort.DEFAULT -> "Default"
    ReasoningEffort.MINIMAL -> "Minimal"
    ReasoningEffort.LOW -> "Low"
    ReasoningEffort.MEDIUM -> "Medium"
    ReasoningEffort.HIGH -> "High"
    ReasoningEffort.XHIGH -> "XHigh"
    ReasoningEffort.MAX -> "Max"
}

/**
 * 轨道绘制，与 rikkahub 的 ReasoningSurgeTrack 保持一致：
 * 未选中底色 + 流动光带（重复波纹沿轨道滑动，MAX 为流动彩虹）+ 呼吸光晕 + 上升火花 + 滑块。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReasoningSurgeTrack(
    count: Int,
    knobX: Float,
    level: ReasoningEffort,
    trackHeightPx: Float,
    knobRadiusPx: Float,
    surfaceColor: Color,
    phase: Float,
    pulse: Float,
) {
    val cy = size.height / 2f
    val top = cy - trackHeightPx / 2f
    val radius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
    val base = reasoningSurgeColor(level)
    val max = level == ReasoningEffort.MAX
    val animated = level != ReasoningEffort.OFF
    val levels = ReasoningEffort.entries
    val intensity = level.ordinal / (levels.size - 1).toFloat()

    // 未选中段
    drawRoundRect(
        color = surfaceColor,
        topLeft = Offset(0f, top),
        size = Size(size.width, trackHeightPx),
        cornerRadius = radius,
    )

    // 光带与光晕右端对齐滑块右缘，而不是滑块中心。
    val activeWidth = (knobX + knobRadiusPx).coerceIn(0f, size.width)
    if (activeWidth <= 0f) {
        drawReasoningKnob(knobX, cy, knobRadiusPx, base)
        return
    }
    if (!animated) {
        drawRoundRect(
            color = base.copy(alpha = 0.6f),
            topLeft = Offset(0f, top),
            size = Size(activeWidth, trackHeightPx),
            cornerRadius = radius,
        )
        drawReasoningKnob(knobX, cy, knobRadiusPx, base)
        return
    }

    // 流动光带：亮→暗→暗→亮的重复波纹沿轨道滑动；MAX 换成流动彩虹。
    val bandPeriod = (if (max) 200.dp else 140.dp).toPx()
    val shift = phase * bandPeriod
    val bandBrush = if (max) {
        Brush.linearGradient(
            colors = SurgeRainbow,
            start = Offset(shift - bandPeriod, 0f),
            end = Offset(shift, 0f),
            tileMode = TileMode.Repeated,
        )
    } else {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to base,
                0.45f to base.copy(alpha = 0.45f),
                0.55f to base.copy(alpha = 0.45f),
                1.0f to base,
            ),
            start = Offset(shift - bandPeriod, 0f),
            end = Offset(shift, 0f),
            tileMode = TileMode.Repeated,
        )
    }

    // 呼吸光晕：比轨道更高的柔和底光，强度随深度与 pulse 起伏。
    val haloAlpha = 0.16f + 0.30f * intensity * pulse
    // 光晕高度 1.5 倍轨道（12dp），比 14dp 的滑块略小，滑块边缘微露。
    val haloHeight = trackHeightPx * 1.5f
    val haloTopLeft = Offset(0f, cy - haloHeight / 2f)
    val haloSize = Size(activeWidth, haloHeight)
    val haloRadius = CornerRadius(haloHeight / 2f, haloHeight / 2f)
    drawRoundRect(
        brush = if (max) bandBrush else SolidColor(base),
        topLeft = haloTopLeft,
        size = haloSize,
        cornerRadius = haloRadius,
        alpha = haloAlpha,
    )

    drawRoundRect(
        brush = bandBrush,
        topLeft = Offset(0f, top),
        size = Size(activeWidth, trackHeightPx),
        cornerRadius = radius,
    )

    // 刻度点
    if (count > 1) {
        val dotR = 2.dp.toPx()
        for (i in 0 until count) {
            val x = reasoningKnobX(i.toFloat(), size.width, knobRadiusPx * 2, count)
            drawCircle(
                color = if (x <= knobX) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                radius = dotR,
                center = Offset(x, cy),
            )
        }
    }

    // 上升火花：正弦淡入淡出 + 横向摆动，深度越深越多越高。
    val particleCount = (intensity * 18f).roundToInt()
    if (particleCount > 0) {
        val riseMax = trackHeightPx * 2f
        val barCenterY = top + trackHeightPx / 2f
        val rainbowSwatches = SurgeRainbow.size - 1
        for (i in 0 until particleCount) {
            val rnd = kotlin.random.Random(i.toLong() * 31 + 17)
            val xFrac = rnd.nextFloat()
            val offset = rnd.nextFloat()
            val rise = 0.55f + 0.65f * rnd.nextFloat()
            val rDp = 1.2f + 1.4f * rnd.nextFloat()
            val sway = rnd.nextFloat() * 2f - 1f
            val t = ((phase + offset) % 1f)
            val px = activeWidth * xFrac +
                sin(t * 2f * PI.toFloat() + i.toFloat()) * trackHeightPx * 0.5f * sway
            val py = barCenterY - t * riseMax * rise
            val alpha = sin(t * PI.toFloat()) * (0.45f + 0.45f * intensity)
            drawCircle(
                color = if (max) SurgeRainbow[i % rainbowSwatches] else base,
                alpha = alpha.coerceIn(0f, 1f),
                radius = rDp.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }

    drawReasoningKnob(knobX, cy, knobRadiusPx, base)
}

/** 滑块：投影 + 白色圆面 + 等级色描边。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReasoningKnob(
    knobX: Float,
    cy: Float,
    knobRadiusPx: Float,
    base: Color,
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.16f),
        radius = knobRadiusPx + 0.5.dp.toPx(),
        center = Offset(knobX, cy + 0.5.dp.toPx()),
    )
    drawCircle(
        color = Color.White,
        radius = knobRadiusPx,
        center = Offset(knobX, cy),
    )
    drawCircle(
        color = base,
        radius = knobRadiusPx,
        center = Offset(knobX, cy),
        style = Stroke(1.5.dp.toPx()),
    )
}
