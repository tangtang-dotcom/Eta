package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.ui.markdown.StreamingMarkdownState
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 把连续的思考与工具调用收束为一条可展开的工作过程：折叠时只是一行摘要，展开后是
 * 左侧带连线的时间线。工作过程与正文同样不加卡片外壳，让最终回答保持视觉主角。
 */
@Composable
internal fun AgentWorkProcess(
    id: String,
    messages: List<AgentChatMessageUi>,
    assistantOverlay: Boolean = false,
    onOpenBrowser: () -> Unit,
    currentBrowserMessageId: String?,
    retainedStreamingStates: Map<String, StreamingMarkdownState>,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    // 嵌入回答气泡时由外层提供内边距与背景，自身不再横向撑开。
    embedded: Boolean = false,
    // 生成中是否自动展开；回答气泡内传 false，进出过程都只剩一行摘要。
    autoExpandWhileActive: Boolean = true,
    // 收起时尾部保留几步可见；0 表示只留标题行，Int.MAX_VALUE 表示永不收起。
    collapsedVisibleCount: Int = 0,
    // 过程整段收成一行：折叠行本身就是那条引用条，展开时在其下方铺时间线。
    // 嵌进回答气泡时开启，做到“一条过程、一行可见”。
    singleRow: Boolean = false,
) {
    val running = messages.any { message ->
        (message is ThinkingMessageUi && message.isStreaming) ||
            (message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running)
    }
    val steps = remember(messages) { messages.toWorkSteps() }
    // null 表示自动模式；用户点过之后只认手动选择。
    var manualExpanded by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }

    // 以“是否仍是本轮末尾的工作过程”判定收起，而不是 running：思考结束到工具开始之间
    // running 会短暂为 false，按它收起会造成反复开合。展开态在组合期同步推导，与正文
    // 条目插入落在同一帧，不经 LaunchedEffect 晚一帧收起。语音浮层空间有限，自动模式不展开。
    val autoExpanded = active && !assistantOverlay && autoExpandWhileActive
    val bodyVisible = manualExpanded ?: (autoExpanded || collapsedVisibleCount > 0)

    // 竖条颜色在组合期取一次：drawBehind 的 lambda 不是 @Composable 上下文，
    // 不能在绘制阶段读主题色。

    // 收起态只展示尾部若干步，其余收进标题行；流式进行中至少留一步，
    // 否则模型跑工具时界面上看不到任何进展，容易误以为卡死。
    val visibleSteps = remember(
        steps,
        bodyVisible,
        manualExpanded,
        autoExpanded,
        autoExpandWhileActive,
        collapsedVisibleCount,
    ) {
        if (bodyVisible && (manualExpanded == true || autoExpanded)) {
            steps
        } else {
            val keep = collapsedVisibleCount.coerceAtLeast(
                if (active && autoExpandWhileActive) 1 else 0,
            )
            if (keep >= steps.size) steps else steps.takeLast(keep)
        }
    }
    val hiddenStepCount = steps.size - visibleSteps.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = when {
                    embedded -> 0.dp
                    assistantOverlay -> 12.dp
                    else -> 16.dp
                },
                vertical = if (embedded) 0.dp else 2.dp,
            ),
    ) {
        AgentWorkHeader(
            messages = messages,
            working = active || running,
            expanded = bodyVisible,
            compact = embedded,
            hiddenStepCount = hiddenStepCount,
            quoted = singleRow,
            onClick = { manualExpanded = !bodyVisible },
        )
        // 单行模式下折叠行就是那条引用条本身：气泡里不再显示任何步骤，
        // 只有用户主动点开时才在下方铺开时间线。
        val showSteps = if (singleRow) bodyVisible && manualExpanded == true else bodyVisible
        AnimatedVisibility(
            visible = showSteps,
            enter = fadeIn(tween(160)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) {
            // 竖条与底色改由每一步自己画：
            // 整组一条竖线会被拉到全部步骤的总高，看起来像一根柱子；
            // 探索过程本来就是一行一条的短记录，每步各带底色更接近“引用一句话”。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                visibleSteps.forEachIndexed { index, step ->
                    key(step.key) {
                        AgentWorkStepNode(
                            step = step,
                            isLast = index == visibleSteps.lastIndex,
                            quoted = embedded,
                            oneLine = singleRow,
                            onOpenBrowser = onOpenBrowser,
                            currentBrowserMessageId = currentBrowserMessageId,
                            retainedStreamingStates = retainedStreamingStates,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentWorkHeader(
    messages: List<AgentChatMessageUi>,
    working: Boolean,
    expanded: Boolean,
    compact: Boolean = false,
    // 收起态下被折进标题行的步骤数，展开后为 0。
    hiddenStepCount: Int = 0,
    // 单行模式：这一行自己就是过程引用条，折叠时不再叠任何步骤。
    quoted: Boolean = false,
    onClick: () -> Unit,
) {
    val runningTool = messages.lastOrNull { message ->
        message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running
    } as? ToolActivityMessageUi
    val summary = remember(messages) { messages.workSummary() }
    // 合并后的步骤数（连续同类工具算一步），折叠行报的就是条数。
    val stepCount = remember(messages) { messages.mergedWorkStepCount() }
    val base = when {
        runningTool != null -> pluralStringResource(
            R.plurals.work_processing_step,
            stepCount,
            stepCount,
        ) + " · " + runningTool.argumentsSummary.ifBlank { toolDisplayName(runningTool.toolName) }
        working -> stringResource(R.string.work_analyzing)
        else -> workSummaryText(summary, stepCount)
    }
    // 与 chatcube 的 ChainOfThought 一致：收起态提示还能展开多少步，
    // 展开态给「收起」，用户始终知道还能往哪个方向切换。
    val text = when {
        hiddenStepCount > 0 && !expanded -> stringResource(
            R.string.work_show_more_steps,
            hiddenStepCount,
        )
        expanded -> stringResource(R.string.work_hide_steps)
        else -> base
    }
    val icon = when {
        runningTool != null -> iconForTool(runningTool.toolName)
        working -> Icons.Rounded.Lightbulb
        else -> Icons.Rounded.Checklist
    }
    // 单行模式下整条过程只有这一行：引用条自带底色、竖条与圆角，
    // 不再额外叠一层步骤列表，多条工具/思考合并成“一条、一行”。
    val barModifier = if (quoted) {
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            // 竖条直接把左侧圆角包住：整张卡只有一个 shape。
            .workQuoteBar()
            .padding(start = WorkQuoteBarWidth + 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
    } else {
        Modifier
            .fillMaxWidth()
            // 嵌进气泡时这一行就是收起态的整个灰框，压矮一些不占地方。
            .heightIn(min = if (compact) 26.dp else WorkRowHeight)
            .clip(RoundedCornerShape(10.dp))
    }
    Row(
        modifier = barModifier
            .clip(if (quoted) quoteBarShape() else RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (quoted) {
            Box(contentAlignment = Alignment.Center) {
                WorkNodeBadge(
                    icon = icon,
                    tone = when {
                        working -> WorkNodeTone.Running
                        summary.failedCount > 0 -> WorkNodeTone.Failed
                        else -> WorkNodeTone.Done
                    },
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
        } else {
            Box(
                modifier = Modifier.width(if (compact) 18.dp else WorkGutterWidth),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp),
                    tint = if (working) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
            Spacer(modifier = Modifier.width(if (compact) 4.dp else WorkGutterGap))
        }
        ShimmerText(
            text = text,
            active = working,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f, fill = false),
        )
        ExpandChevron(
            expanded = expanded,
            contentDescription = stringResource(
                if (expanded) R.string.work_collapse else R.string.work_expand,
            ),
            modifier = Modifier.padding(start = 4.dp, end = if (quoted) 6.dp else 8.dp),
        )
    }
}

@Composable
private fun workSummaryText(summary: AgentWorkSummary, stepCount: Int): String {
    val parts = buildList {
        if (summary.thinkingCount > 0) {
            add(pluralStringResource(R.plurals.work_summary_thinking, summary.thinkingCount, summary.thinkingCount))
        }
        if (summary.toolCount > 0) {
            add(pluralStringResource(R.plurals.work_summary_tools, stepCount, stepCount))
        }
        if (summary.failedCount > 0) {
            add(pluralStringResource(R.plurals.work_summary_failed, summary.failedCount, summary.failedCount))
        }
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.work_completed) }
}

@Composable
private fun AgentWorkStepNode(
    step: AgentWorkStep,
    isLast: Boolean,
    onOpenBrowser: () -> Unit,
    currentBrowserMessageId: String?,
    retainedStreamingStates: Map<String, StreamingMarkdownState>,
    // 嵌进回答气泡时改画成引用块样式的小卡片，不再走左侧时间线。
    quoted: Boolean = false,
    // 展开后的每一步也只占一行（参数、命令、结果都收进各自的详情）。
    oneLine: Boolean = false,
) {
    when (step) {
        is AgentWorkStep.ToolGroup -> WorkStep(
            icon = iconForTool(step.toolName),
            tone = when {
                step.running -> WorkNodeTone.Running
                step.failedCount > 0 -> WorkNodeTone.Failed
                else -> WorkNodeTone.Done
            },
            isLast = isLast,
            quoted = quoted,
        ) {
            ToolGroupRow(group = step, onOpenBrowser = onOpenBrowser, oneLine = oneLine)
        }

        is AgentWorkStep.Single -> when (val message = step.message) {
            is ToolActivityMessageUi -> WorkStep(
                icon = iconForTool(message.toolName),
                tone = message.status.nodeTone(),
                isLast = isLast,
                quoted = quoted,
            ) {
                ToolActivityRow(
                    message = message,
                    onOpenBrowser = onOpenBrowser,
                    showBrowserShortcut = message.id == currentBrowserMessageId,
                    oneLine = oneLine,
                )
            }

            is ThinkingMessageUi -> WorkStep(
                icon = Icons.Rounded.Lightbulb,
                tone = if (message.isStreaming) WorkNodeTone.Running else WorkNodeTone.Done,
                isLast = isLast,
                quoted = quoted,
            ) {
                ThinkingRow(
                    message = message,
                    retainedStreamingState = retainedStreamingStates[message.id],
                    compact = true,
                    oneLine = oneLine,
                )
            }

            is ToolSummaryMessageUi -> WorkStep(
                icon = Icons.Rounded.Build,
                tone = WorkNodeTone.Done,
                isLast = isLast,
                quoted = quoted,
            ) {
                ChatMessageItem(
                    message = message,
                    onSuggestionClick = {},
                    onRunTraceClick = {},
                    onOpenBrowser = onOpenBrowser,
                    showBrowserShortcut = false,
                    compact = true,
                )
            }

            else -> Unit
        }
    }
}

/** 时间线节点与纯内容两种排布的切换点，正文内容完全一致。 */
@Composable
private fun WorkStep(
    icon: ImageVector,
    tone: WorkNodeTone,
    isLast: Boolean,
    quoted: Boolean,
    content: @Composable () -> Unit,
) {
    if (quoted) {
        // 底色与竖条统一走 workQuoteSurface()：折叠行和展开后的每一步保持同一套颜色。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                // 与折叠行同一个背景，不再“深底 + 亮条”两层分开。
                .workQuoteBar()
                .padding(start = WorkQuoteBarWidth + 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 徽章居中对齐：单行时徽章与文字中线对齐。
            // 两行时也不会像顶部对齐那样悬在行首上方。
            Box(contentAlignment = Alignment.Center) {
                WorkNodeBadge(icon = icon, tone = tone)
            }
            Spacer(modifier = Modifier.width(7.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    } else {
        WorkTimelineNode(icon = icon, tone = tone, isLast = isLast) { content() }
    }
}

/** 过程块左侧竖条宽度：收窄到 4dp，仍然包住左侧圆角但不再显粗。 */
private val WorkQuoteBarWidth = 4.dp

/** 过程条右侧圆角：保持与聊天气泡一致的 24dp。 */
private val WorkQuoteCorner = 24.dp

/** 过程条左侧两角的小圆角：左缘保持竖直直线，只把上下两个角磨圆。 */
private val WorkQuoteStartCorner = 8.dp

/**
 * 过程引用条的形状：左缘是一条竖直直线，只有左上/左下两个角是小圆角，
 * 右侧保持气泡的 24dp。中间整段边缘没有弧线，看起来就是“左边切直的一条”。
 *
 * GenericShape 的构造 lambda 只有 (Path, Size, LayoutDirection)，拿不到 Density，
 * 所以两个圆角在这里按当前屏幕密度预先换算成像素再传进来。
 */
@Composable
private fun quoteBarShape(): Shape {
    val density = LocalDensity.current
    val startRadiusPx = with(density) { WorkQuoteStartCorner.toPx() }
    val endRadiusPx = with(density) { WorkQuoteCorner.toPx() }
    return remember(startRadiusPx, endRadiusPx) {
        quoteBarShape(startRadiusPx, endRadiusPx)
    }
}

private fun quoteBarShape(startRadiusPx: Float, endRadiusPx: Float) = GenericShape { size, _ ->
    val startRadius = startRadiusPx.coerceAtMost(size.width / 2f)
    val endRadius = endRadiusPx.coerceAtMost(size.width / 2f)
    val w = size.width
    val h = size.height
    // 左上角：从直边起弧，再落到顶部直线。
    moveTo(0f, startRadius)
    arcTo(
        rect = Rect(0f, 0f, startRadius * 2f, startRadius * 2f),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(w - endRadius, 0f)
    arcTo(
        rect = Rect(w - endRadius * 2f, 0f, w, endRadius * 2f),
        startAngleDegrees = 270f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(w, h - endRadius)
    arcTo(
        rect = Rect(w - endRadius * 2f, h - endRadius * 2f, w, h),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(startRadius, h)
    arcTo(
        rect = Rect(0f, h - startRadius * 2f, startRadius * 2f, h),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    // 左边回到起点：这一整条是竖直直线。
    close()
}

/**
 * 过程引用条的背景与竖条：折叠行和展开后的每一步都走这一处。
 *
 * 外层按 [quoteBarShape] 裁出“左边直、右端圆”的轮廓并铺满竖条色；
 * 内侧再铺一块同轮廓的横条色，只是不从最左边起笔——
 * 横条自身的左缘是垂直的，右端才带 24dp 圆角，于是竖条与横条拼成一块，中间没有缝。
 */
@Composable
private fun Modifier.workQuoteBar(): Modifier {
    val barColor = AetherChatColors.quoteBar()
    val stepBackground = AetherChatColors.quoteFill()
    return this
        .clip(quoteBarShape())
        .background(barColor)
        .drawBehind {
            // 内侧横条从竖条右缘起笔：左缘垂直向下，右端沿用气泡圆角。
            // 竖条宽度落在裁切轮廓内，因此两者的上下边缘完全重合，不会露出阶梯。
            val endRadius = WorkQuoteCorner.toPx().coerceAtMost(size.width / 2f)
            val left = WorkQuoteBarWidth.toPx()
            val w = size.width
            val h = size.height
            if (w - left <= endRadius) return@drawBehind
            val path = Path().apply {
                moveTo(left, 0f)
                lineTo(w - endRadius, 0f)
                arcTo(
                    rect = Rect(w - endRadius * 2f, 0f, w, endRadius * 2f),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(w, h - endRadius)
                arcTo(
                    rect = Rect(w - endRadius * 2f, h - endRadius * 2f, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(left, h)
                close()
            }
            drawPath(path = path, color = stepBackground)
        }
}

internal enum class WorkNodeTone { Running, Done, Failed }

internal fun ToolActivityStatusUi.nodeTone(): WorkNodeTone = when (this) {
    ToolActivityStatusUi.Running -> WorkNodeTone.Running
    ToolActivityStatusUi.Failed -> WorkNodeTone.Failed
    ToolActivityStatusUi.Success, ToolActivityStatusUi.Unknown -> WorkNodeTone.Done
}

/**
 * 时间线节点：左侧固定宽度的槽位放图标徽章，连线在绘制阶段画出，不参与测量。
 * 节点默认向上接到工作过程标题图标，最后一个节点不向下延伸。
 */
@Composable
internal fun WorkTimelineNode(
    icon: ImageVector,
    tone: WorkNodeTone,
    isLast: Boolean,
    connectTop: Boolean = true,
    content: @Composable () -> Unit,
) {
    val lineColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = WorkGutterWidth.toPx() / 2f
                val stroke = 1.dp.toPx()
                val gap = 3.dp.toPx()
                val nodeTop = (WorkRowHeight - WorkNodeSize).toPx() / 2f
                val nodeBottom = nodeTop + WorkNodeSize.toPx()
                if (connectTop) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, nodeTop - gap), stroke)
                }
                if (!isLast) {
                    drawLine(lineColor, Offset(x, nodeBottom + gap), Offset(x, size.height), stroke)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .width(WorkGutterWidth)
                .height(WorkRowHeight),
            contentAlignment = Alignment.Center,
        ) {
            WorkNodeBadge(icon = icon, tone = tone)
        }
        Spacer(modifier = Modifier.width(WorkGutterGap))
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun WorkNodeBadge(icon: ImageVector, tone: WorkNodeTone) {
    val (background, tint) = when (tone) {
        WorkNodeTone.Running -> MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) to
            MiuixTheme.colorScheme.primary
        WorkNodeTone.Failed -> StatusError.copy(alpha = 0.12f) to StatusError
        WorkNodeTone.Done -> MiuixTheme.colorScheme.surfaceContainer to
            MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val pulse = rememberDrawPhasePulse(active = tone == WorkNodeTone.Running)
    Box(
        modifier = Modifier
            .size(WorkNodeSize)
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = tint,
        )
    }
}

/** 呼吸动画只返回 State，由调用方在 graphicsLayer 中读取，帧间不触发重组。 */
@Composable
internal fun rememberDrawPhasePulse(active: Boolean): State<Float>? {
    if (!active) return null
    val transition = rememberInfiniteTransition(label = "work_node_pulse")
    return transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "work_node_pulse_alpha",
    )
}

/**
 * 进行中的标题用一道扫过的高光提示“正在发生”。高光在绘制阶段以 SrcAtop 叠在字形上，
 * 动画只使绘制失效；离屏层限定在这一行文字范围内。
 */
@Composable
internal fun ShimmerText(
    text: String,
    active: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shimmerModifier = if (active) {
        val highlight = MiuixTheme.colorScheme.onSurface
        val transition = rememberInfiniteTransition(label = "work_shimmer")
        val progress = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_600, easing = LinearEasing),
            ),
            label = "work_shimmer_progress",
        )
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val band = maxOf(size.width * 0.35f, 72.dp.toPx())
                val center = -band + (size.width + band * 2f) * progress.value
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        startX = center - band,
                        endX = center + band,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            }
    } else {
        Modifier
    }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(shimmerModifier),
    )
}

@Composable
internal fun ExpandChevron(
    expanded: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "expand_chevron",
    )
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = contentDescription,
        modifier = modifier
            .size(14.dp)
            .graphicsLayer { rotationZ = rotation },
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
    )
}

/** 时间线标题行与节点徽章共用的几何尺寸，保证图标中心与首行文字中线对齐。 */
internal val WorkRowHeight: Dp = 32.dp
internal val WorkGutterWidth: Dp = 22.dp
internal val WorkGutterGap: Dp = 8.dp
private val WorkNodeSize: Dp = 20.dp
