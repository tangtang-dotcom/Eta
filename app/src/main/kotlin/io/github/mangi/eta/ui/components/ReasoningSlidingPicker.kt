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
    // 松手才提交：拖动过程中只更新预览，避免每滑一格都写一次设置。
    fun commit() {
        latestOptions.getOrNull(sliderIndex)?.let {
            if (it != latestEffort) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        val surgePhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1600, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "surgePhase",
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReasoningSliderStyle.Height)
                .semantics {
                    contentDescription = "思考深度"
                    stateDescription = effortDisplayName(previewLevel)
                    setProgress { v ->
                        sliderIndex = (v * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        commit()
                        true
                    }
                }
                .pointerInput(count) {
                    detectTapGestures { offset ->
                        sliderIndex = reasoningStopAt(offset.x, size.width.toFloat(), knobPx, count)
                        commit()
                    }
                }
                .pointerInput(count) {
                    detectHorizontalDragGestures(
                        onDragEnd = { commit() },
                        onDragCancel = { commit() },
                    ) { change, _ ->
                        change.consume()
                        sliderIndex = reasoningStopAt(change.position.x, size.width.toFloat(), knobPx, count)
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
            )
        }
    }
}

private object ReasoningSliderStyle {
    val TrackHeight = 14.dp
    val KnobRadius = 12.dp
    val Height = 52.dp
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

/** 轨道：未选中底色 + 选中段光带（随深度加速、MAX 彩虹）+ 上升火花 + 滑块。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReasoningSurgeTrack(
    count: Int,
    knobX: Float,
    level: ReasoningEffort,
    trackHeightPx: Float,
    knobRadiusPx: Float,
    surfaceColor: Color,
    phase: Float,
) {
    val cy = size.height / 2f
    val top = cy - trackHeightPx / 2f
    val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
    val base = reasoningSurgeColor(level)
    val max = level == ReasoningEffort.MAX
    val animated = level != ReasoningEffort.OFF
    val levels = ReasoningEffort.entries
    val depth = level.ordinal / (levels.size - 1).toFloat()

    drawRoundRect(
        color = surfaceColor,
        topLeft = Offset(0f, top),
        size = Size(size.width, trackHeightPx),
        cornerRadius = corner,
    )

    val fillW = (knobX + trackHeightPx / 2f).coerceAtMost(size.width)
    if (fillW > 0f) {
        val brush = if (max) {
            val period = 96.dp.toPx()
            val shift = phase * period
            Brush.linearGradient(
                colors = SurgeRainbow,
                start = Offset(shift - period, 0f),
                end = Offset(shift, 0f),
                tileMode = TileMode.Repeated,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(base.copy(alpha = 0.55f), base),
                startX = 0f,
                endX = fillW.coerceAtLeast(1f),
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = Offset(0f, top),
            size = Size(fillW, trackHeightPx),
            cornerRadius = corner,
        )
    }

    if (count > 1) {
        val dotR = 2.dp.toPx()
        for (i in 0 until count) {
            val x = reasoningKnobX(i.toFloat(), size.width, knobRadiusPx * 2, count)
            drawCircle(
                color = if (x <= knobX) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f),
                radius = dotR,
                center = Offset(x, cy),
            )
        }
    }

    if (animated) {
        val particleCount = (depth * 14f).roundToInt()
        if (particleCount > 0) {
            for (i in 0 until particleCount) {
                val rnd = kotlin.random.Random(i.toLong() * 31 + 17)
                val xFrac = rnd.nextFloat()
                val offset = rnd.nextFloat()
                val rise = 0.55f + 0.65f * rnd.nextFloat()
                val rDp = 1.0f + 1.2f * rnd.nextFloat()
                val sway = rnd.nextFloat() * 2f - 1f
                val homeX = (knobX * xFrac).coerceAtLeast(0f)
                val p = ((phase + offset) % 1f)
                val py = cy - p * rise * 18.dp.toPx()
                val px = homeX + sway * p * 4.dp.toPx()
                drawCircle(
                    color = base.copy(alpha = (1f - p) * 0.7f),
                    radius = rDp.dp.toPx(),
                    center = Offset(px, py),
                )
            }
        }
    }

    drawCircle(
        color = Color.Black.copy(alpha = 0.18f),
        radius = knobRadiusPx + 1.dp.toPx(),
        center = Offset(knobX, cy + 1.dp.toPx()),
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
        style = Stroke(2.dp.toPx()),
    )
}
