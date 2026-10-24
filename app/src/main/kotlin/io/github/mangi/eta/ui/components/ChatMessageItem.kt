package io.github.mangi.eta.ui.components

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.app.LocalIsDarkTheme
import io.github.mangi.eta.ui.markdown.MarkdownTone
import io.github.mangi.eta.ui.markdown.StaticMarkdown
import io.github.mangi.eta.ui.markdown.StreamingMarkdown
import io.github.mangi.eta.ui.markdown.StreamingMarkdownState
import io.github.mangi.eta.ui.markdown.THINKING_LINE_HEIGHT_SP
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.RunTraceMessageUi
import io.github.mangi.eta.ui.model.SuggestionChipsMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.voice.SpeechReadAloudButton
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberDataUrlBitmap(dataUrl: String) = remember(dataUrl) {
    decodeDataUrlBitmap(dataUrl)
}

internal fun decodeDataUrlBitmap(dataUrl: String): ImageBitmap? {
    val base64 = dataUrl.substringAfter("base64,", "")
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * 等待首个文本片段时的轻量反馈。
 */
@Composable
fun AITypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(alpha = alpha)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary, CircleShape)
            )
        }
    }
}

/**
 * 只有正在执行的状态才持有无限动画。历史思考和工具条目保持静态，避免长会话里
 * 每个已完成节点都持续产生帧时钟与状态更新。
 */
@Composable
internal fun rememberActivePulse(
    active: Boolean,
    label: String,
): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}_alpha",
    )
    return alpha
}

@Composable
internal fun ChatMessageItem(
    message: AgentChatMessageUi,
    onSuggestionClick: (String) -> Unit,
    onRunTraceClick: () -> Unit,
    onOpenBrowser: () -> Unit,
    showBrowserShortcut: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    assistantOverlay: Boolean = false,
    retainedStreamingState: StreamingMarkdownState? = null,
    showCopyAction: Boolean = true,
    showMessageActions: Boolean = false,
    assistantModel: AgentModelOptionUi? = null,
    workMessages: List<AgentChatMessageUi> = emptyList(),
    workActive: Boolean = false,
    workStreamingStates: Map<String, StreamingMarkdownState> = emptyMap(),
    currentBrowserMessageId: String? = null,
    messageActionsEnabled: Boolean = true,
    isEditing: Boolean = false,
    onEditMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerateMessage: (String) -> Unit = {},
    onSelectReplyCandidate: (String, Int) -> Unit = { _, _ -> },
) {
    when (message) {
        is UserMessageUi -> UserMessageBubble(
            message = message,
            assistantOverlay = assistantOverlay,
            actionsEnabled = messageActionsEnabled,
            isEditing = isEditing,
            onEdit = { onEditMessage(message.id) },
            onDelete = { onDeleteMessage(message.id) },
            onRegenerate = { onRegenerateMessage(message.id) },
            modifier = modifier,
        )
        is AgentMessageUi -> AgentMessageBlock(
            message = message,
            allowSpeech = true,
            model = assistantModel,
            workMessages = workMessages,
            workActive = workActive,
            workStreamingStates = workStreamingStates,
            currentBrowserMessageId = currentBrowserMessageId,
            onOpenBrowser = onOpenBrowser,
            assistantOverlay = assistantOverlay,
            retainedStreamingState = retainedStreamingState,
            showCopyAction = showCopyAction,
            showMessageActions = showMessageActions,
            messageActionsEnabled = messageActionsEnabled,
            onDelete = { onDeleteMessage(message.id) },
            onRegenerate = { onRegenerateMessage(message.id) },
            onEdit = { onEditMessage(message.id) },
            onSelectCandidate = { onSelectReplyCandidate(message.id, it) },
            modifier = modifier,
        )
        is SystemNoticeMessageUi -> if (message.code == SystemNoticeCode.ContextCompaction) {
            ContextCompactionMarker(message = message, modifier = modifier)
        } else {
            AgentMessageBlock(
                message = AgentMessageUi(
                    id = message.id,
                    content = buildString {
                        append(
                            stringResource(
                                when (message.code) {
                                    SystemNoticeCode.Stopped -> R.string.system_notice_stopped
                                    SystemNoticeCode.EmptyResult -> R.string.system_notice_empty_result
                                    SystemNoticeCode.ContextCompaction -> R.string.context_compaction
                                    SystemNoticeCode.ModelRetry -> R.string.system_notice_model_retry
                                    SystemNoticeCode.RuntimeFailed -> R.string.system_notice_runtime_failed
                                    SystemNoticeCode.Interrupted -> R.string.system_notice_interrupted
                                },
                            ),
                        )
                        message.detail?.takeIf(String::isNotBlank)?.let { detail ->
                            append("\n\n")
                            append(detail)
                        }
                    },
                    renderMarkdown = false,
                ),
                forceBubble = true,
                model = assistantModel,
                assistantOverlay = false,
                retainedStreamingState = null,
                showCopyAction = showCopyAction,
                showMessageActions = showMessageActions,
                messageActionsEnabled = messageActionsEnabled,
                onDelete = { onDeleteMessage(message.id) },
                onRegenerate = { onRegenerateMessage(message.id) },
                modifier = modifier,
            )
        }
        is ThinkingMessageUi -> ThinkingRow(
            message = message,
            retainedStreamingState = retainedStreamingState,
            modifier = modifier,
            compact = compact,
        )
        is RunTraceMessageUi -> RunTraceRow(message = message, onClick = onRunTraceClick, modifier = modifier)
        is ToolActivityMessageUi -> ToolActivityInline(
            message = message,
            onOpenBrowser = onOpenBrowser,
            showBrowserShortcut = showBrowserShortcut,
            modifier = modifier,
            compact = compact,
        )
        is ToolSummaryMessageUi -> ToolSummaryInline(message = message, modifier = modifier, compact = compact)
        is SuggestionChipsMessageUi -> SuggestionChipsRow(message = message, onSuggestionClick = onSuggestionClick, modifier = modifier)
    }
}

// ── 用户消息：轻盈美观气泡 ──────────────────────────────────────────────

@Composable
private fun UserMessageBubble(
    message: UserMessageUi,
    assistantOverlay: Boolean,
    actionsEnabled: Boolean,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val tooltipState = rememberTooltipState(isPersistent = true)
    val haptic = LocalHapticFeedback.current
    var copied by remember(message.id) { mutableStateOf(false) }
    var confirmRegenerate by remember(message.id) { mutableStateOf(false) }
    // 操作行默认收起，单击气泡再显示，点一下又收起来。
    var actionsVisible by rememberSaveable(message.id) { mutableStateOf(false) }
    val appearance = LocalAppearanceSettings.current
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_400)
            copied = false
        }
    }
    LaunchedEffect(actionsEnabled) {
        if (!actionsEnabled) tooltipState.dismiss()
    }
    LaunchedEffect(tooltipState) {
        snapshotFlow { tooltipState.isVisible }.collect { visible ->
            if (visible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val visiblePrompt = remember(message.content) {
        AgentFileReferencePromptCodec.parse(message.content)
    }
    val overlayBubbleColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF37393D)
    } else {
        Color(0xFFE5E7EA)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (assistantOverlay) 16.dp else 20.dp,
                vertical = if (assistantOverlay) 4.dp else 8.dp,
            ),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Below,
            ),
            tooltip = {
                RichTooltip(insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MessageTooltipAction(
                            icon = Icons.Rounded.ContentCopy,
                            label = stringResource(R.string.ui_copy_4edd1d),
                            onClick = {
                                @Suppress("DEPRECATION")
                                clipboardManager.setText(AnnotatedString(message.content))
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                tooltipState.dismiss()
                            },
                        )
                        MessageTooltipAction(
                            icon = Icons.Rounded.Edit,
                            label = stringResource(R.string.ui_edit_a7f814),
                            onClick = {
                                tooltipState.dismiss()
                                onEdit()
                            },
                        )
                        MessageTooltipAction(
                            icon = Icons.Rounded.Delete,
                            label = stringResource(R.string.ui_delete_3755f5),
                            onClick = {
                                tooltipState.dismiss()
                                onDelete()
                            },
                        )
                    }
                }
            },
            state = tooltipState,
            focusable = true,
            enableUserInput = actionsEnabled,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .then(
                        if (assistantOverlay) {
                            Modifier.background(overlayBubbleColor, RoundedCornerShape(10.dp))
                        } else {
                            // Aether 用户气泡：统一 24dp 圆角 + messageBubble 实色，没有尾巴。
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(AetherChatColors.userBubble())
                        }
                    )
                    .then(
                        if (isEditing) {
                            Modifier.border(
                                width = 1.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(24.dp),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(enabled = actionsEnabled) {
                        actionsVisible = !actionsVisible
                    }
                    .padding(
                        start = if (assistantOverlay) 12.dp else 18.dp,
                        end = if (assistantOverlay) 12.dp else 18.dp,
                        top = if (assistantOverlay) 8.dp else 14.dp,
                        // 底部留白交给操作行自己控制，图标才会贴着气泡下沿。
                        bottom = if (assistantOverlay) 8.dp else 14.dp,
                    ),
            ) {
                if (message.images.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        message.images.forEach { dataUrl ->
                            val bitmap = rememberDataUrlBitmap(dataUrl)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = stringResource(R.string.chat_image_attachment),
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (visiblePrompt.references.isNotEmpty()) {
                    SentFileReferenceFlow(
                        references = visiblePrompt.references,
                        modifier = Modifier.padding(
                            bottom = if (visiblePrompt.request.isNotBlank()) 8.dp else 0.dp
                        ),
                    )
                }
                if (visiblePrompt.request.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = visiblePrompt.request,
                            style = MiuixTheme.textStyles.body1,
                            color = if (assistantOverlay) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                AetherChatColors.onUserBubble()
                            },
                        )
                    }
                }
                if (message.isEdited) {
                    Text(
                        text = stringResource(R.string.ui_edited_c36776),
                        style = MiuixTheme.textStyles.body2,
                        color = if (assistantOverlay) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            AetherChatColors.onUserBubble().copy(alpha = 0.72f)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (!assistantOverlay) {
            Spacer(Modifier.height(2.dp))
            UserMessageActionRow(
                visible = actionsVisible &&
                    actionsEnabled &&
                    appearance.chatUserMessageActions &&
                    visiblePrompt.request.isNotBlank(),
                copied = copied,
                onCopy = {
                    @Suppress("DEPRECATION")
                    clipboardManager.setText(AnnotatedString(message.content))
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    copied = true
                },
                onRegenerate = { confirmRegenerate = true },
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
        }
    }
    if (confirmRegenerate) {
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.chat_regenerate_user_title),
            summary = stringResource(R.string.chat_regenerate_user_body),
            onDismissRequest = { confirmRegenerate = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.ui_regenerate_2e1905),
                onCancel = { confirmRegenerate = false },
                onConfirm = {
                    confirmRegenerate = false
                    onRegenerate()
                },
            )
        }
    }
}

/**
 * 用户消息下方的常驻操作行：复制、重新生成、编辑、删除。
 *
 * 与助手回答的操作行共用 [AnswerActionButton] 的尺寸和弱化配色，两处样式保持一致；
 * 流式运行中由外部传入 visible=false 隐藏，避免打断执行态。
 */
@Composable
private fun UserMessageActionRow(
    visible: Boolean,
    copied: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // 气泡回到默认底色，操作行也回到弱化的次级文字色；复制成功时用主题色反馈。
    val actionTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.75f)
    val copiedTint = MiuixTheme.colorScheme.primary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + expandVertically(tween(160)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier,
        ) {
            AnswerActionButton(
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = stringResource(
                    if (copied) R.string.copy_copied else R.string.chat_copy_message,
                ),
                onClick = onCopy,
                tint = if (copied) copiedTint else actionTint,
            )
            TooltipBox(text = stringResource(R.string.ui_regenerate_2e1905)) {
                AnswerActionButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.chat_regenerate_user_action),
                    onClick = onRegenerate,
                    tint = actionTint,
                )
            }
            TooltipBox(text = stringResource(R.string.ui_edit_a7f814)) {
                AnswerActionButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.ui_edit_a7f814),
                    onClick = onEdit,
                    tint = actionTint,
                )
            }
            TooltipBox(text = stringResource(R.string.ui_delete_3755f5)) {
                AnswerActionButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.ui_delete_3755f5),
                    onClick = onDelete,
                    tint = actionTint,
                )
            }
        }
    }
}

@Composable
private fun MessageTooltipAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            // 下方文字已经命名该操作，图标不再重复朗读。
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

// ── 上下文压缩：时间线中的轻量胶囊标记 ─────────────────────────────────

/**
 * 压缩不是一轮对话结果，而是上下文维护事件；用居中胶囊标记与助手正文区分，
 * 进行中通过图标脉冲反馈，结束后保留压缩前后的 token 信息。
 */
@Composable
private fun ContextCompactionMarker(
    message: SystemNoticeMessageUi,
    modifier: Modifier = Modifier,
) {
    val pulseAlpha = rememberActivePulse(active = message.running, label = "compaction_pulse")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MiuixTheme.colorScheme.surface)
                .border(
                    0.5.dp,
                    MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                    RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Compress,
                contentDescription = null,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer(alpha = if (message.running) pulseAlpha else 1f),
                tint = if (message.running) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.detail?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.context_compaction),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Agent 结果 ───────────────────────────────────────────────────────

@Composable
private fun AgentMessageBlock(
    message: AgentMessageUi,
    allowSpeech: Boolean = false,
    model: AgentModelOptionUi? = null,
    workMessages: List<AgentChatMessageUi> = emptyList(),
    workActive: Boolean = false,
    workStreamingStates: Map<String, StreamingMarkdownState> = emptyMap(),
    currentBrowserMessageId: String? = null,
    onOpenBrowser: () -> Unit = {},
    // 系统提示（已停止、任务中断等）也要落在气泡里，但它没有正文可渲染。
    forceBubble: Boolean = false,
    assistantOverlay: Boolean = false,
    retainedStreamingState: StreamingMarkdownState?,
    showCopyAction: Boolean,
    showMessageActions: Boolean,
    messageActionsEnabled: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit = {},
    onSelectCandidate: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember(message.id) { mutableStateOf(false) }
    // 生成触感反馈：内容每有新字符到达时给一次轻触感，45ms 节流，
    // 避免逐字刷新时连续触发把马达打满。
    val appearance = LocalAppearanceSettings.current
    var lastGenerationHapticAt by remember(message.id) { mutableLongStateOf(0L) }
    LaunchedEffect(message.content.length, message.isStreaming, appearance.chatGenerationHaptic) {
        if (!appearance.chatGenerationHaptic || !message.isStreaming || message.content.isEmpty()) {
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastGenerationHapticAt >= 45L) {
            lastGenerationHapticAt = now
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        }
    }
    val keepStreamingMarkdown = remember(message.id) { message.isStreaming }
    DisposableEffect(retainedStreamingState) {
        retainedStreamingState?.attachContent()
        onDispose { retainedStreamingState?.detachContent() }
    }
    var streamingRevealComplete by remember(message.id) {
        mutableStateOf(!keepStreamingMarkdown)
    }
    // 渲染会话由列表层按 message.id 持有，item 滚出视口被销毁后滑回时复用同一
    // 会话；没有外部持有者时（如嵌套条目）退回组合内 remember，行为与之前一致。
    val streamingState = if (keepStreamingMarkdown) {
        retainedStreamingState ?: remember(message.id) { StreamingMarkdownState() }
    } else {
        null
    }
    val completedDocument = (streamingState ?: retainedStreamingState)
        ?.snapshot?.completedDocumentFor(message.content)
    val revealComplete = streamingRevealComplete && !message.isStreaming &&
        (streamingState == null || completedDocument != null)
    LaunchedEffect(retainedStreamingState, revealComplete, message.content) {
        retainedStreamingState?.revealedContent = message.content.takeIf { revealComplete }
    }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_400)
            copied = false
        }
    }

    val character = LocalRoleplayChat.current.takeUnless { assistantOverlay }
    val isGreeting = character != null && message.id.startsWith(GreetingMessagePrefix)
    // 回答气泡：非悬浮窗的正常回答包成描边卡片，并在顶部署名模型。
    val showBubble = !assistantOverlay && (forceBubble || message.renderMarkdown)
    // 回答气泡同样支持单击切换操作行；无气泡（悬浮窗）时保持原来的常显规则。
    var replyActionsVisible by rememberSaveable(message.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (assistantOverlay) 16.dp else 20.dp,
                vertical = 8.dp,
            ),
    ) {
        if (character != null) {
            CharacterReplyHeader(character = character, isGreeting = isGreeting)
        }
        if (showBubble) {
            AssistantReplyHeader(model = model)
        }
        // 浅色主题固定纯白，避免莫奈把 surface 染成灰蓝后与页面背景糊在一起；
        // 深色主题的 surface 与 background 常常同色，改用 background 掺 8% onBackground，
        // 在纯黑与深灰底上都能看出一层卡片。
        val darkBubble = LocalIsDarkTheme.current
        val bubbleFill = if (darkBubble) {
            lerp(MiuixTheme.colorScheme.background, MiuixTheme.colorScheme.onBackground, 0.08f)
        } else {
            Color.White
        }
        Column(
            modifier = if (showBubble) {
                Modifier
                    .widthIn(max = AssistantBubbleMaxWidth)
                    .squircleSurface(
                        color = bubbleFill,
                        cornerRadius = AssistantBubbleCorner,
                    )
                    .clickable(enabled = messageActionsEnabled) {
                        replyActionsVisible = !replyActionsVisible
                    }
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        ) {
            if (showBubble && workMessages.isNotEmpty()) {
                AssistantWorkBubble(
                    messages = workMessages,
                    active = workActive,
                    assistantOverlay = assistantOverlay,
                    onOpenBrowser = onOpenBrowser,
                    currentBrowserMessageId = currentBrowserMessageId,
                    retainedStreamingStates = workStreamingStates,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            when {
                message.content.isBlank() && message.isStreaming -> {
                    AITypingIndicator(
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                streamingState != null && !revealComplete -> {
                    StreamingMarkdown(
                        state = streamingState,
                        content = message.content,
                        isStreaming = message.isStreaming,
                        tone = MarkdownTone.Answer,
                        onRevealCompleteChange = { streamingRevealComplete = it },
                    )
                }
                message.renderMarkdown -> {
                    SelectionContainer {
                        StaticMarkdown(
                            content = message.content,
                            tone = MarkdownTone.Answer,
                            parsed = completedDocument,
                        )
                    }
                }
                message.content.isNotBlank() -> {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

        }

        // 回答显现完成后才出现操作行；淡入展开避免贴底滚动时整页突然跳动一行高度。
        AnimatedVisibility(
            visible = showCopyAction &&
                !message.isStreaming &&
                message.content.isNotBlank() &&
                revealComplete &&
                (!showBubble || replyActionsVisible),
            enter = fadeIn(tween(160)) + expandVertically(tween(160)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 与工作过程标题图标共用左侧中心线。
                    .offset(x = -AnswerActionCenterOffset)
                    .padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnswerActionButton(
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(
                        if (copied) R.string.copy_copied else R.string.copy_answer,
                    ),
                    onClick = {
                        @Suppress("DEPRECATION")
                        clipboardManager.setText(AnnotatedString(message.content))
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        copied = true
                    },
                    tint = if (copied) MiuixTheme.colorScheme.primary else null,
                )
                if (allowSpeech) SpeechReadAloudButton(message.id, message.content)
                if (showMessageActions) {
                    if (message.characterEditable) {
                        AnswerActionButton(
                            icon = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.chat_edit_character_reply),
                            onClick = onEdit,
                            enabled = messageActionsEnabled,
                        )
                    }
                    TooltipBox(text = stringResource(R.string.ui_regenerate_2e1905), enabled = messageActionsEnabled) {
                        AnswerActionButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.ui_regenerate_reply_84a7d9),
                            onClick = onRegenerate,
                            enabled = messageActionsEnabled,
                        )
                    }
                    TooltipBox(text = stringResource(R.string.ui_delete_3755f5), enabled = messageActionsEnabled) {
                        AnswerActionButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.ui_delete_this_conversation_3f351b),
                            onClick = onDelete,
                            enabled = messageActionsEnabled,
                        )
                    }
                    if (message.characterEditable && message.candidateCount > 1) {
                        Spacer(Modifier.weight(1f))
                        CandidateSwitcher(
                            selected = message.selectedCandidate,
                            count = message.candidateCount,
                            enabled = messageActionsEnabled,
                            onSelect = onSelectCandidate,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回答气泡里的工作过程气泡：思考与工具调用收在浅色内层卡片里，形成气泡包气泡。
 *
 * 内层只提供背景与内边距，可展开的摘要行、时间线与节点沿用 [AgentWorkProcess]。
 */
@Composable
private fun AssistantWorkBubble(
    messages: List<AgentChatMessageUi>,
    active: Boolean,
    assistantOverlay: Boolean,
    onOpenBrowser: () -> Unit,
    currentBrowserMessageId: String?,
    retainedStreamingStates: Map<String, StreamingMarkdownState>,
    modifier: Modifier = Modifier,
) {
    // 整组工作过程共用一个灰色圆角框：收起时是一行摘要，展开后是步骤列表，外框不变。
    // 深色下比气泡再亮一档，保证外框、工作过程框和页面底三者互相分得开。
    val darkWork = LocalIsDarkTheme.current
    val cardFill = if (darkWork) {
        lerp(MiuixTheme.colorScheme.background, MiuixTheme.colorScheme.onBackground, 0.16f)
    } else {
        lerp(MiuixTheme.colorScheme.surface, MiuixTheme.colorScheme.onSurface, 0.06f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .squircleSurface(color = cardFill, cornerRadius = 14.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AgentWorkProcess(
            id = messages.first().id,
            messages = messages,
            assistantOverlay = assistantOverlay,
            onOpenBrowser = onOpenBrowser,
            currentBrowserMessageId = currentBrowserMessageId,
            retainedStreamingStates = retainedStreamingStates,
            active = active,
            embedded = true,
            // 子气泡默认只留最后几步：过程往往十几步，全展开会把大气泡又撑长。
            collapsedVisibleCount = WorkBubbleCollapsedSteps,
        )
    }
}

/**
 * 回答气泡内的工作过程收起时保留的步骤数。
 *
 * 0 等于只剩一行摘要；这里是 3，让用户不用展开也能看到最近在干什么。
 */
private const val WorkBubbleCollapsedSteps = 3

/** 回答气泡圆角；与思考卡片的 12dp 区分，气泡更圆一些。 */
private val AssistantBubbleCorner = 18.dp

/** 回答气泡最宽；与用户气泡一致，短回答不再铺满整行。 */
private val AssistantBubbleMaxWidth = 320.dp

/**
 * 回答气泡顶部的署名行：圆角方块 AI 标识加模型名。
 *
 * 底色取主题色低透明度，不引入图片资源；模型名缺失时退回“AI”。
 */
@Composable
private fun AssistantReplyHeader(model: AgentModelOptionUi?) {
    Row(
        modifier = Modifier.padding(start = 1.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logo = modelOrProviderBrandLogoRes(model?.modelId, model?.providerSourceType)
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = model?.displayName?.takeIf(String::isNotBlank) ?: "AI",
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 开场白消息由开始对话时写入，标识前缀与写入方保持一致。 */
private const val GreetingMessagePrefix = "greeting-"

/**
 * 角色回复的署名行：小头像加角色名，开场白额外标注“故事开始”。
 * 正文保持与普通回答同宽，避免为头像缩进后长段落在窄屏上过于拥挤。
 */
@Composable
private fun CharacterReplyHeader(character: RoleplayChatUi, isGreeting: Boolean) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 28.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isGreeting) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.chat_character_story_start),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** 思考卡片与运行记录等全宽描边卡片共用的圆角与描边强度，相邻时轮廓一致。 */
private val ChatCardCorner = 12.dp
private const val ChatCardOutlineAlpha = 0.5f

/** 回答操作按钮的触控尺寸；行整体左移半个尺寸减去图标半宽，使图标落在工作过程图标的中心线上。 */
internal val AnswerActionSize = 36.dp
private val AnswerActionCenterOffset = 11.dp

/** 回答下方的轻量操作：图标显式着色，因此禁用态需要自己换成禁用色，否则看起来仍可点。 */
@Composable
internal fun AnswerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = AnswerActionSize,
        minHeight = AnswerActionSize,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = when {
                !enabled -> MiuixTheme.colorScheme.disabledOnSurface
                tint != null -> tint
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.75f)
            },
        )
    }
}

@Composable
private fun CandidateSwitcher(
    selected: Int,
    count: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 2.dp),
    ) {
        AnswerActionButton(
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = stringResource(R.string.chat_previous_candidate),
            onClick = { onSelect(selected - 1) },
            enabled = enabled && selected > 0,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = "${selected + 1}/$count",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        AnswerActionButton(
            icon = Icons.Rounded.ChevronRight,
            contentDescription = stringResource(R.string.chat_next_candidate),
            onClick = { onSelect(selected + 1) },
            enabled = enabled && selected < count - 1,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ── 思考过程 ─────────────────────────────────────────────────────────

@Composable
internal fun ThinkingRow(
    message: ThinkingMessageUi,
    retainedStreamingState: StreamingMarkdownState?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    DisposableEffect(retainedStreamingState) {
        retainedStreamingState?.attachContent()
        onDispose { retainedStreamingState?.detachContent() }
    }
    // null 表示跟随思考生命周期；用户点过之后只认手动选择。
    var manualExpanded by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    val keepStreamingMarkdown = remember(message.id) { message.isStreaming }
    val streamingState = if (keepStreamingMarkdown) {
        retainedStreamingState ?: remember(message.id) { StreamingMarkdownState() }
    } else {
        null
    }
    val completedDocument = (streamingState ?: retainedStreamingState)
        ?.snapshot?.completedDocumentFor(message.content)
    // 展开态必须在组合期同步推导：若经 LaunchedEffect 晚一帧收起，思考结束的那一帧尾部窗口
    // 已解除而正文仍展开，会以全文高度闪现一帧，再从该高度执行收起动画。
    val expanded = manualExpanded ?: message.isStreaming
    // 自动模式下高度上限覆盖到收起动画结束，退出过程不会先撑开再收拢。
    val tailWindow = manualExpanded == null

    val pulseAlpha = rememberActivePulse(
        active = message.isStreaming,
        label = "thinking_pulse",
    )

    // compact 模式渲染在工作过程时间线内，节点徽章已承担图标，这里只保留文字与展开控制。
    val containerModifier = if (compact) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surface,
                cornerRadius = ChatCardCorner,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = ChatCardOutlineAlpha),
                cornerRadius = ChatCardCorner,
            )
    }

    Column(modifier = containerModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    // 尾部窗口下点击视为要看全文，保持展开并放开高度限制。
                    manualExpanded = (tailWindow && expanded) || !expanded
                }
                .then(
                    if (compact) {
                        Modifier.heightIn(min = WorkRowHeight)
                    } else {
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer(alpha = if (message.isStreaming) pulseAlpha else 1f),
                    tint = if (message.isStreaming) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            ShimmerText(
                active = message.isStreaming,
                text = if (message.isStreaming) {
                    stringResource(R.string.reasoning_in_progress)
                } else {
                    message.elapsedSeconds?.takeIf { it > 0 }?.let { seconds ->
                        pluralStringResource(
                            R.plurals.reasoning_completed_seconds,
                            seconds,
                            seconds,
                        )
                    } ?: stringResource(R.string.reasoning_completed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (message.isStreaming) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.weight(1f, fill = !compact),
            )
            ExpandChevron(
                expanded = expanded,
                contentDescription = stringResource(
                    if (expanded) R.string.reasoning_collapse else R.string.reasoning_expand,
                ),
                modifier = Modifier.padding(start = 4.dp, end = if (compact) 8.dp else 0.dp),
            )
        }

        AnimatedVisibility(visible = expanded && message.content.isNotBlank()) {
            Column {
                if (!compact) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    )
                }
                val contentModifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 0.dp else 12.dp,
                        end = if (compact) 8.dp else 12.dp,
                        top = if (compact) 0.dp else 8.dp,
                        bottom = if (compact) 8.dp else 12.dp,
                    )
                if (streamingState != null &&
                    (message.isStreaming || completedDocument == null || tailWindow)
                ) {
                    ThinkingTailWindow(
                        enabled = tailWindow,
                        modifier = contentModifier,
                    ) {
                        StreamingMarkdown(
                            state = streamingState,
                            content = message.content,
                            isStreaming = message.isStreaming,
                            tone = MarkdownTone.Thinking,
                        )
                    }
                } else {
                    StaticMarkdown(
                        content = message.content,
                        tone = MarkdownTone.Thinking,
                        parsed = completedDocument,
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}

/**
 * 进行中的思考只露出最近几行：内容按底部对齐超出部分向上裁掉，顶部渐隐提示还有更早内容。
 * 高度封顶后增量不再推高工作过程卡片，列表也不必随每次思考快照重新跟底。
 */
@Composable
private fun ThinkingTailWindow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val maxHeight = with(LocalDensity.current) {
        (THINKING_TAIL_WINDOW_LINES * THINKING_LINE_HEIGHT_SP).sp.toDp()
    }
    Box(
        modifier = modifier
            .heightIn(max = maxHeight)
            .clipToBounds()
            // 离屏合成只覆盖这块有界区域，用于让渐隐遮罩作用在文字 alpha 上。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (size.height >= maxHeight.toPx() - 1f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true),
        ) {
            content()
        }
    }
}

private const val THINKING_TAIL_WINDOW_LINES = 4

// ── Run trace：轻量入口行 ─────────────────────────────────────────────

@Composable
private fun RunTraceRow(
    message: RunTraceMessageUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .squircleSurface(color = MiuixTheme.colorScheme.surface, cornerRadius = ChatCardCorner)
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = ChatCardOutlineAlpha),
                cornerRadius = ChatCardCorner,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ui_available_capacity_743337),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
        )
    }
}

// ── 工具摘要 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolSummaryInline(
    message: ToolSummaryMessageUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 10.dp else 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.tools.forEach { tool ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = iconForTool(tool),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolDisplayName(tool),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

// ── 建议语 ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChipsRow(
    message: SuggestionChipsMessageUi,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message.prompts.forEach { prompt ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        MiuixTheme.colorScheme.outline.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSuggestionClick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = prompt,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
