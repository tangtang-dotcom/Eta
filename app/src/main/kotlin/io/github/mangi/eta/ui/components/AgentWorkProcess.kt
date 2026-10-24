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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.ui.markdown.StreamingMarkdownState
import io.github.mangi.eta.ui.model.AgentMessageUi
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
            onClick = { manualExpanded = !bodyVisible },
        )
        AnimatedVisibility(
            visible = bodyVisible,
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
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                visibleSteps.forEachIndexed { index, step ->
                    key(step.key) {
                        AgentWorkStepNode(
                            step = step,
                            isLast = index == visibleSteps.lastIndex,
                            quoted = embedded,
                            folded = !bodyVisible,
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
    onClick: () -> Unit,
) {
    val runningTool = messages.lastOrNull { message ->
        message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running
    } as? ToolActivityMessageUi
    val summary = remember(messages) { messages.workSummary() }
    val base = when {
        runningTool != null -> pluralStringResource(
            R.plurals.work_processing_step,
            summary.toolCount,
            summary.toolCount,
        ) + " · " + runningTool.argumentsSummary.ifBlank { toolDisplayName(runningTool.toolName) }
        working -> stringResource(R.string.work_analyzing)
        else -> workSummaryText(summary)
    }
    // 收起态还有步骤被折在下面时给出可见提示，否则用户不知道上面还藏着内容。
    val text = if (!expanded && hiddenStepCount > 0) {
        val hidden = pluralStringResource(
            R.plurals.work_completed_steps,
            hiddenStepCount,
            hiddenStepCount,
        )
        "$base · $hidden"
    } else {
        base
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 嵌进气泡时这一行就是收起态的整个灰框，压矮一些不占地方。
            .heightIn(min = if (compact) 26.dp else WorkRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(if (compact) 18.dp else WorkGutterWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    runningTool != null -> iconForTool(runningTool.toolName)
                    working -> Icons.Rounded.Lightbulb
                    else -> Icons.Rounded.Checklist
                },
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
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )
    }
}

@Composable
private fun workSummaryText(summary: AgentWorkSummary): String {
    val parts = buildList {
        if (summary.thinkingCount > 0) {
            add(pluralStringResource(R.plurals.work_summary_thinking, summary.thinkingCount, summary.thinkingCount))
        }
        if (summary.toolCount > 0) {
            add(pluralStringResource(R.plurals.work_summary_tools, summary.toolCount, summary.toolCount))
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
    // 所在子气泡处于收起态：所有步骤都折在标题行之后，旁白也只留一行。
    folded: Boolean = false,
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
            ToolGroupRow(group = step, onOpenBrowser = onOpenBrowser)
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

            // 过程旁白：夹在工具/思考之间的正文。它不是结论，只是模型在说明正在做什么，
            // 因此收进工作过程里按次级文字呈现，不占外层气泡的回答位置。
            is AgentMessageUi -> WorkStep(
                icon = Icons.Rounded.Checklist,
                tone = WorkNodeTone.Done,
                isLast = isLast,
                quoted = quoted,
            ) {
                WorkNarrationRow(message = message, folded = folded)
            }

            else -> Unit
        }
    }
}

/**
 * 过程旁白：模型在两次工具调用之间说明自己在做什么。
 *
 * 它属于工作过程而不是本轮回答，所以用次级文字色、限制行数，避免和真正的结论抢注意力。
 */
@Composable
private fun WorkNarrationRow(message: AgentMessageUi, folded: Boolean) {
    val maxLines = when {
        message.isStreaming -> Int.MAX_VALUE
        folded -> NARRATION_COLLAPSED_MAX_LINES
        else -> NARRATION_MAX_LINES
    }
    Text(
        text = message.content,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
    )
}

/**
 * 旁白最多显示几行。
 *
 * 折叠时一行——它只是过程摘要的一部分，展开后才给几行上下文，避免子气泡被旁白撑长。
 */
private const val NARRATION_COLLAPSED_MAX_LINES = 1
private const val NARRATION_MAX_LINES = 3

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
        // 嵌进回答气泡时，整组工作过程共用一个灰色圆角框，步骤自身不再画卡片，
        // 只用一点纵向间距把相邻步骤分开。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            content()
        }
    } else {
        WorkTimelineNode(icon = icon, tone = tone, isLast = isLast) { content() }
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
