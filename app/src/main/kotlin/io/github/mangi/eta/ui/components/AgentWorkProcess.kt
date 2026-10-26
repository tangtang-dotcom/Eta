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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.mangi.eta.ui.model.AgentMessageUi
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
    // 与相邻的过程块在视觉上连成一条：整轮只保留首段顶部圆角与末段底部圆角，
    // 中间被正文隔开的各段共用一条竖条，减少「一堆小条」的碎片感。
    connectsTop: Boolean = false,
    connectsBottom: Boolean = false,
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
    // fullyExpanded 决定「铺开完整时间线」，bodyVisible 只决定「下面有没有东西要画」。
    // 两者分开：收起态可以露出尾部两步，但折叠行仍显示为收起态、保留展开入口。
    val fullyExpanded = manualExpanded ?: autoExpanded
    val bodyVisible = fullyExpanded || collapsedVisibleCount > 0

    // 竖条颜色在组合期取一次：drawBehind 的 lambda 不是 @Composable 上下文，
    // 不能在绘制阶段读主题色。

    // 收起态只展示尾部若干步，其余收进标题行；流式进行中至少留一步，
    // 否则模型跑工具时界面上看不到任何进展，容易误以为卡死。
    val visibleSteps = remember(
        steps,
        fullyExpanded,
        autoExpandWhileActive,
        collapsedVisibleCount,
    ) {
        if (fullyExpanded) {
            steps
        } else {
            val keep = collapsedVisibleCount.coerceAtLeast(
                if (active && autoExpandWhileActive) 1 else 0,
            )
            if (keep >= steps.size) steps else steps.takeLast(keep)
        }
    }
    // 计数只算真正的动作：旁白是被并进过程块的那句话，不是一次步骤，
    // 把它算进「显示更多 N 步」会让隐藏数量虚高。
    val hiddenStepCount = (
        steps.count { !it.isAsideStep() } - visibleSteps.count { !it.isAsideStep() }
        ).coerceAtLeast(0)

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
        // 单行模式下折叠行就是那条引用条本身：气泡里不再显示任何步骤，
        // 只有用户主动点开时才在下方铺开时间线。
        // 有步骤要画就画：收起态由 collapsedVisibleCount 决定露几条，
        // 不再要求用户手动展开过才显示，否则收纳条会永远空着。
        val showSteps = bodyVisible
        // 展开时折叠行与步骤共用外层的同一块画布：竖条只有一条，从上贯通到底。
        val joinedPanel = embedded && singleRow && showSteps
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (joinedPanel) {
                        Modifier.workQuoteBar(
                            connectsTop = connectsTop,
                            connectsBottom = connectsBottom,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            AgentWorkHeader(
                messages = messages,
                working = active || running,
                expanded = fullyExpanded,
                compact = embedded,
                hiddenStepCount = hiddenStepCount,
                quoted = singleRow,
                // 整块由外层画时，这一行不再自己画竖条与底色。
                quotedBackground = !joinedPanel,
                connectsTop = connectsTop,
                connectsBottom = connectsBottom,
                collapsedVisibleCount = collapsedVisibleCount,
                onClick = { manualExpanded = !fullyExpanded },
            )
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
                if (embedded) {
                    // 展开后接在折叠行下方，属于同一块面板：底色与竖条由外层统一画。
                    // 参照 rikkahub，步骤之间不画任何分割线，靠时间线的节点与竖线区分，
                    // 行距交给每步自己的垂直留白。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = WorkQuoteBarWidth + 12.dp,
                                end = 10.dp,
                                top = 2.dp,
                                bottom = 6.dp,
                            ),
                    ) {
                        visibleSteps.forEachIndexed { index, step ->
                            key(step.key) {
                                AgentWorkStepNode(
                                    step = step,
                                    isLast = index == visibleSteps.lastIndex,
                                    quoted = false,
                                    // 展开后仍是一步一行，参数和结果收进各自的展开区。
                                    oneLine = true,
                                    onOpenBrowser = onOpenBrowser,
                                    currentBrowserMessageId = currentBrowserMessageId,
                                    retainedStreamingStates = retainedStreamingStates,
                                )
                            }
                        }
                    }
                } else {
                    // 非气泡嵌入式（独立卡片、语音浮层）保留原来的时间线布局。
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
                                    quoted = false,
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
    // 这一行是否自己画底色与竖条；展开时整块由外层统一画，这里传 false。
    quotedBackground: Boolean = true,
    // 与相邻过程块连通：圆角只在整组首尾出现，中间的段保持直边。
    connectsTop: Boolean = false,
    connectsBottom: Boolean = false,
    // 收起态保留可见的尾部步骤数，用于算「展开 N 步」里的 N。
    collapsedVisibleCount: Int = 0,
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
    // 参照 rikkahub：整组过程只有「展开 N 步」与「收起」两种文案，
    // 收纳控制是顶部一个明确的动作，而不是把摘要揉进标题里。
    val canCollapse = stepCount > collapsedVisibleCount
    val text = when {
        expanded -> stringResource(R.string.work_hide_steps)
        canCollapse -> stringResource(R.string.work_show_more_steps, stepCount - collapsedVisibleCount)
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
            // 展开时整块由外层画，这里不留底边间距，两块才能连成一体。
            .then(if (quotedBackground) Modifier.padding(bottom = 2.dp) else Modifier)
            // 竖条 + 横条自己画；整块由外层画时这一步跳过。
            .then(
                if (quotedBackground) {
                    Modifier.workQuoteBar(
                        connectsTop = connectsTop,
                        connectsBottom = connectsBottom,
                    )
                } else {
                    Modifier
                },
            )
            .padding(start = WorkQuoteBarWidth + 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
    } else {
        Modifier
            .fillMaxWidth()
            // 嵌进气泡时这一行就是收起态的整个灰框，压矮一些不占地方。
            .heightIn(min = if (compact) 26.dp else WorkRowHeight)
            .clip(RoundedCornerShape(10.dp))
    }
    // 收纳行沿用 rikkahub ChainOfThought 的排布：左侧固定 24dp 图标槽、
    // 中间主色的「展开 N 步 / 收起」、右侧 16dp 箭头，整行可点。
    Row(
        modifier = barModifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (quoted) {
            // 参照 rikkahub：图标槽固定 24dp，图标本身不带底色圆块，
            // 只用主色 tint；运行中保留呼吸脉冲作为唯一的动态暗示。
            val pulse = rememberDrawPhasePulse(active = working)
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer { alpha = pulse?.value ?: 1f },
                    tint = if (summary.failedCount > 0) {
                        StatusError
                    } else {
                        AetherChatColors.workStepIcon()
                    },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Box(
                modifier = Modifier.width(if (compact) 18.dp else WorkGutterWidth),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp),
                    // 与收纳条同一主色系：图标、文字、箭头属于一组可点元素。
                    tint = AetherChatColors.workStepIcon(),
                )
            }
            Spacer(modifier = Modifier.width(if (compact) 4.dp else WorkGutterGap))
        }
        // 收纳控制用主色，与正文字色区分开：一眼能看出这里可以点。
        ShimmerText(
            text = text,
            active = working,
            style = MiuixTheme.textStyles.body2,
            color = AetherChatColors.workToggleAccent(),
            modifier = Modifier.weight(1f, fill = false),
        )
        // 收纳行尾部：上下箭头与文字同为主色，与 rikkahub 的顶部控制条一致。
        ExpandChevron(
            expanded = expanded,
            contentDescription = stringResource(
                if (expanded) R.string.work_collapse else R.string.work_expand,
            ),
            modifier = Modifier.padding(start = 4.dp, end = if (quoted) 6.dp else 8.dp),
            vertical = quoted,
            tint = AetherChatColors.workToggleAccent(),
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

            // 短旁白：模型边做事边随口一句，被并进过程块内部。它是正文不是步骤，
            // 因此不挂徽章、不画节点图标，按普通正文排进时间线，位置仍是它被说出的位置。
            is AgentMessageUi -> WorkStepText(
                text = message.content,
                quoted = quoted,
                isLast = isLast,
            )

            else -> Unit
        }
    }
}

/** 这个时间线节点是不是被并入过程块的短旁白（而非真正的动作）。 */
private fun AgentWorkStep.isAsideStep(): Boolean =
    this is AgentWorkStep.Single && message is AgentMessageUi

/**
 * 过程块内部的短旁白行。
 *
 * 它没有节点图标与状态徽章：这条消息是模型说的话，不是一次工具调用，
 * 套上「已完成」的勾选图标会让人误以为它是个步骤。
 */
@Composable
private fun WorkStepText(
    text: String,
    quoted: Boolean,
    isLast: Boolean,
) {
    val content: @Composable () -> Unit = {
        Text(
            text = text.trim(),
            style = MiuixTheme.textStyles.body2,
            color = AetherChatColors.onSurfaceVariant(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (quoted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .workQuoteBar()
                .padding(start = WorkQuoteBarWidth + 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            content()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = if (isLast) 4.dp else 0.dp),
        ) {
            content()
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

/** 过程块左侧竖条宽度：露出 4dp，另有一半被横条压住，形成「｜」嵌进长条的效果。 */
private val WorkQuoteBarWidth = 8.dp

/** 竖条露出的宽度：横条从这条线右侧起笔，盖住竖条右边那一半。 */
private val WorkQuoteBarVisibleWidth = 4.dp

/** 竖条圆角半径。 */
private val WorkQuoteBarRadius = 6.dp

/** 横条右端圆角半径。 */
private val WorkQuoteFillRadiusEnd = 14.dp

/** 横条左端圆角半径：比右端小，左侧更像一条直边的起点。 */
private val WorkQuoteFillStartCorner = 4.dp

/** 横条的轮廓：左小右大的不规则圆角，与竖条拼出一段「｜」嵌进长条的形状。 */
private val WorkQuoteFillShape = RoundedCornerShape(
    topStart = WorkQuoteFillStartCorner,
    bottomStart = WorkQuoteFillStartCorner,
    topEnd = WorkQuoteFillRadiusEnd,
    bottomEnd = WorkQuoteFillRadiusEnd,
)

/**
 * 过程引用条的背景：最左边一条带圆角的竖条，右边贴着不规则圆角（左小右大）的横条。
 *
 * 竖条画得比露出的宽度更宽（[WorkQuoteBarWidth]），横条从 [WorkQuoteBarVisibleWidth]
 * 处起笔、压在竖条右半边上面：左边露出 4dp 的圆角竖条，右边那一半被横条盖住，
 * 两块等高，看起来就是一小段「｜」嵌进长条里。
 */
@Composable
private fun Modifier.workQuoteBar(
    // 与上一段过程块共用同一条视觉引用时，顶部外缘不做圆角，让两段在切口处
    // 严丝合缝地接上；竖条本身始终贯通，不受这两个开关影响。
    connectsTop: Boolean = false,
    // 与下一段过程块相连：底部同理。整轮只有首段画顶部圆角、末段画底部圆角，
    // 中间的段只留竖条与直边横条，读起来就是「一条过程、中间插了几句话」。
    connectsBottom: Boolean = false,
): Modifier {
    val barColor = AetherChatColors.quoteBar()
    val fillColor = AetherChatColors.quoteFill()
    val fillShape = WorkQuoteFillShape
    val density = LocalDensity.current
    val barWidthPx = with(density) { WorkQuoteBarWidth.toPx() }
    val barRadiusPx = with(density) { WorkQuoteBarRadius.toPx() }
    val barVisiblePx = with(density) { WorkQuoteBarVisibleWidth.toPx() }
    // 流式输出时这个条的高度每帧都在涨，drawBehind 也就每帧重跑一次。轮廓换算
    // （createOutline + addOutline）和 Path 分配放在 drawWithCache 的缓存块里，
    // 只在尺寸变化时才重建；颜色变化走 drawBlock 的捕获值，只触发重画不触发重建。
    return this.drawWithCache {
        val barRadius = barRadiusPx.coerceAtMost(size.height / 2f)
        val fillPath = Path().apply {
            addOutline(
                fillShape.createOutline(
                    size = Size((size.width - barVisiblePx).coerceAtLeast(0f), size.height),
                    layoutDirection = layoutDirection,
                    density = this@drawWithCache,
                ),
            )
        }
        onDrawBehind {
            // 竖条：先画，圆角竖条，右侧一截会被下面的横条压住。
            // 与相邻段连通时不收圆角：上下两段的竖条首尾相接，视觉上才是同一条。
            val verticalTopRadius = if (connectsTop) 0f else barRadius
            val verticalBottomRadius = if (connectsBottom) 0f else barRadius
            val verticalRadius = maxOf(verticalTopRadius, verticalBottomRadius)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, 0f),
                size = Size(barWidthPx, size.height),
                cornerRadius = CornerRadius(verticalRadius, verticalRadius),
            )
            // 横条：从露出宽度处起笔，盖在竖条右半边上面。
            translate(left = barVisiblePx) {
                drawPath(path = fillPath, color = fillColor)
            }
        }
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
    // rikkahub 形态：过程块收纳用「向下 / 向上」箭头，方向直接对应展开收起，
    // 不再靠一个 chevron 旋转 90 度来表达。
    vertical: Boolean = false,
    tint: Color? = null,
) {
    if (vertical) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = contentDescription,
            modifier = modifier.size(16.dp),
            tint = tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
        )
        return
    }
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
        tint = tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
    )
}

/** 时间线标题行与节点徽章共用的几何尺寸，保证图标中心与首行文字中线对齐。 */
internal val WorkRowHeight: Dp = 32.dp
internal val WorkGutterWidth: Dp = 22.dp
internal val WorkGutterGap: Dp = 8.dp
private val WorkNodeSize: Dp = 20.dp
