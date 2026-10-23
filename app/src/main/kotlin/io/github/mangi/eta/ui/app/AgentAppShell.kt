package io.github.mangi.eta.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.CharacterAvatar
import io.github.mangi.eta.ui.components.ConversationSidePaneScaffold
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.RoleplayChatUi
import io.github.mangi.eta.ui.components.AgentModelPillButton
import io.github.mangi.eta.ui.components.TopBarBackdrop
import io.github.mangi.eta.ui.components.captureForTopBar
import io.github.mangi.eta.ui.components.rememberTopBarBackdrop
import io.github.mangi.eta.ui.components.topBarContainerColor
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * Agent App 统一壳层。
 *
 * - 负责全局 Scaffold、状态栏/横向安全边距、顶层工具栏。
 * - 首页工具栏只保留历史入口与溢出菜单，保持聊天舞台干净。
 * - 非首页子路由统一提供返回按钮与标题，避免每个页面各自像独立设置页。
 * - Settings 由标准二级页骨架自己提供 TopAppBar，壳层在此路由不重复绘制。
 */
@Composable
internal fun AgentAppShell(
    currentRoute: AppRoute?,
    isCurrentRoute: Boolean,
    conversationPaneState: ConversationPaneUiState?,
    isConversationPaneOpen: Boolean,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onDismissConversationPane: () -> Unit,
    onSearchConversations: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    modifier: Modifier = Modifier,
    homeCharacter: RoleplayChatUi? = null,
    onOpenCharacter: () -> Unit = {},
    // 聊天顶栏的模型胶囊状态；仅在 Home 路由生效，选择逻辑仍走 Agent 侧原有实现。
    chatModelPickerState: AgentModelPickerUiState? = null,
    chatIsStreaming: Boolean = false,
    chatReasoningEffort: ReasoningEffort? = null,
    chatAvailableReasoningEfforts: List<ReasoningEffort> = emptyList(),
    onChatReasoningEffortChange: ((ReasoningEffort) -> Unit)? = null,
    onChatModelSelected: (String) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberTopBarBackdrop()
    val topBarColor = topBarContainerColor(backdrop)
    val pageContent: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            topBar = {
                if (currentRoute !is AppRoute.Settings) {
                    val isChat = currentRoute is AppRoute.Home
                    TopBarBackdrop(
                        backdrop = backdrop,
                        // 聊天页正文密集，透出太多会显得脏；设置页维持原本的轻盈感。
                        progressiveAlpha = if (isChat) ChatTopBarProgressiveAlpha else 0.3f,
                        surfaceAlpha = if (isChat) ChatTopBarSurfaceAlpha else 0.8f,
                        progressiveRadius = if (isChat) ChatTopBarProgressiveRadius else 10f,
                    ) {
                        if (isChat && chatModelPickerState != null) {
                            // 聊天顶栏整体按 Aether 浮动结构重写：渐变遮罩 + 左右圆形钮 + 中央模型胶囊。
                            ChatTopBarOverlay(
                                modelPickerState = chatModelPickerState,
                                isStreaming = chatIsStreaming,
                                reasoningEffort = chatReasoningEffort,
                                availableReasoningEfforts = chatAvailableReasoningEfforts,
                                onReasoningEffortChange = onChatReasoningEffortChange,
                                onModelSelected = onChatModelSelected,
                                onOpenConversationPane = onOpenConversationPane,
                                onNewConversation = onNewConversation,
                                onOpenTerminal = onOpenTerminal,
                                onLaunchKimiWeb = onLaunchKimiWeb,
                                kimiWebLabel = kimiWebLabel,
                                canStopKimiWeb = canStopKimiWeb,
                                onStopKimiWeb = onStopKimiWeb,
                                onRefreshKimiWeb = onRefreshKimiWeb,
                                onOpenBrowser = onOpenBrowser,
                                homeCharacter = homeCharacter,
                                onOpenCharacter = onOpenCharacter,
                            )
                        } else {
                            AgentTopBar(
                                route = currentRoute,
                                scrollBehavior = scrollBehavior,
                                color = topBarColor,
                                backdrop = backdrop,
                                onBack = onBack,
                                onOpenConversationPane = onOpenConversationPane,
                                onNewConversation = onNewConversation,
                                onOpenTerminal = onOpenTerminal,
                                onLaunchKimiWeb = onLaunchKimiWeb,
                                kimiWebLabel = kimiWebLabel,
                                canStopKimiWeb = canStopKimiWeb,
                                onStopKimiWeb = onStopKimiWeb,
                                onRefreshKimiWeb = onRefreshKimiWeb,
                                onOpenBrowser = onOpenBrowser,
                                homeCharacter = homeCharacter,
                                onOpenCharacter = onOpenCharacter,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureForTopBar(backdrop)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                content(padding)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (conversationPaneState != null && currentRoute is AppRoute.Home) {
            ConversationSidePaneScaffold(
                state = conversationPaneState,
                visible = isConversationPaneOpen,
                backHandlerEnabled = isCurrentRoute,
                onOpen = onOpenConversationPane,
                onDismiss = onDismissConversationPane,
                onSearchChange = onSearchConversations,
                onConversationSelected = onSelectConversation,
                onConversationRename = onConversationRename,
                onConversationExport = onConversationExport,
                onConversationDelete = onConversationDelete,
                onOpenSettings = onOpenSettings,
                onOpenModelProviders = onOpenModelProviders,
                onOpenTools = onOpenTools,
                onOpenSkills = onOpenSkills,
                onOpenCharacters = onOpenCharacters,
                onOpenPermissions = onOpenPermissions,
            ) {
                pageContent()
            }
        } else {
            pageContent()
        }
    }
}

@Composable
private fun AgentTopBar(
    route: AppRoute?,
    scrollBehavior: ScrollBehavior,
    color: Color,
    backdrop: LayerBackdrop? = null,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeCharacter: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
) {
    val isHome = route is AppRoute.Home
    val navigationIcon: @Composable () -> Unit = {
        if (isHome) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FrostedCircle(backdrop = backdrop) {
                    IconButton(onClick = onOpenConversationPane) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShortText,
                            modifier = Modifier.size(20.dp),
                            contentDescription = stringResource(R.string.action_conversation_history),
                        )
                    }
                }
                // 角色对话的身份标识放在顶栏左侧，点按进入角色详情；普通对话保持空标题。
                homeCharacter?.let { character ->
                    CharacterTopBarIdentity(character = character, onClick = onOpenCharacter)
                }
            }
        } else {
            MiuixBackButton(onClick = onBack)
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (isHome) {
            FrostedCircle(backdrop = backdrop) {
                TopBarOverflowMenu(
                    onNewConversation = onNewConversation,
                    onOpenTerminal = onOpenTerminal,
                    onLaunchKimiWeb = onLaunchKimiWeb,
                    kimiWebLabel = kimiWebLabel,
                    canStopKimiWeb = canStopKimiWeb,
                    onStopKimiWeb = onStopKimiWeb,
                    onRefreshKimiWeb = onRefreshKimiWeb,
                    onOpenBrowser = onOpenBrowser,
                )
            }
        }
    }

    // 首页聊天舞台与浏览器、终端这类整屏工具页没有可驱动折叠的滚动列表，大标题会一直展开、
    // 白占一屏顶部；它们使用紧凑标题，其余二级内容页统一使用可折叠大标题。
    val compactTitle = isHome || route is AppRoute.Browser || route is AppRoute.Terminal
    if (compactTitle) {
        SmallTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        AdaptiveTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

/** 40dp 磨砂圆钮：采样顶栏同一份 backdrop，没有实时模糊时退回半透明实底。 */
@Composable
private fun FrostedCircle(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val blurAvailable = backdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (blurAvailable) {
                    Modifier.textureBlur(
                        backdrop = backdrop!!,
                        shape = CircleShape,
                        blurRadius = 20f,
                        colors = BlurColors(
                            blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.5f))),
                        ),
                    )
                } else {
                    Modifier.background(surface.copy(alpha = 0.92f))
                },
            )
            // 半透明白钮落在浅色内容上几乎看不见，补一圈极淡描边把轮廓交代清楚。
            .border(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 聊天页顶栏（结构对齐 Aether ConversationTopOverlay）：
 * 正文段是背景色渐变遮罩 + 控件行，下方再接 42dp 渐隐尾带让控件融入消息流。
 */
@Composable
private fun ChatTopBarOverlay(
    modelPickerState: AgentModelPickerUiState,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort?,
    availableReasoningEfforts: List<ReasoningEffort>,
    onReasoningEffortChange: ((ReasoningEffort) -> Unit)?,
    onModelSelected: (String) -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeCharacter: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
) {
    val base = MiuixTheme.colorScheme.background
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to base.copy(alpha = 0.98f),
                            0.28f to base.copy(alpha = 0.92f),
                            0.58f to base.copy(alpha = 0.52f),
                            0.82f to base.copy(alpha = 0.18f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        ) {
            ChatTopBarFrame(
                modelPickerState = modelPickerState,
                isStreaming = isStreaming,
                reasoningEffort = reasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                onReasoningEffortChange = onReasoningEffortChange,
                onModelSelected = onModelSelected,
                onOpenConversationPane = onOpenConversationPane,
                onNewConversation = onNewConversation,
                onOpenTerminal = onOpenTerminal,
                onLaunchKimiWeb = onLaunchKimiWeb,
                kimiWebLabel = kimiWebLabel,
                canStopKimiWeb = canStopKimiWeb,
                onStopKimiWeb = onStopKimiWeb,
                onRefreshKimiWeb = onRefreshKimiWeb,
                onOpenBrowser = onOpenBrowser,
                homeCharacter = homeCharacter,
                onOpenCharacter = onOpenCharacter,
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatTopBarTailHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to base.copy(alpha = 0.10f),
                            0.42f to base.copy(alpha = 0.04f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/**
 * 控件行按 AetherConversationTopBarFrame：左右 38dp 圆钮（光晕下移 4dp、模糊 14dp）、
 * 水平 15dp / 垂直 12dp、中央胶囊区左右各留 12dp。
 */
@Composable
private fun ChatTopBarFrame(
    modelPickerState: AgentModelPickerUiState,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort?,
    availableReasoningEfforts: List<ReasoningEffort>,
    onReasoningEffortChange: ((ReasoningEffort) -> Unit)?,
    onModelSelected: (String) -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeCharacter: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatHeaderCircle(
            icon = Icons.AutoMirrored.Outlined.ShortText,
            contentDescription = stringResource(R.string.action_conversation_history),
            onClick = onOpenConversationPane,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp),
        ) {
            // 模型胶囊贴近左侧圆钮（Aether 中央区内容从左侧排布）；角色对话时身份标识并排在前。
            Row(verticalAlignment = Alignment.CenterVertically) {
                homeCharacter?.let { character ->
                    CharacterTopBarIdentity(character = character, onClick = onOpenCharacter)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                AgentModelPillButton(
                state = modelPickerState,
                isStreaming = isStreaming,
                reasoningEffort = reasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                onReasoningEffortChange = onReasoningEffortChange,
                onModelSelected = onModelSelected,
                )
            }
        }
        ChatHeaderCircle(
            icon = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.action_new_conversation),
            onClick = onNewConversation,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.size(ChatHeaderButtonSize)) {
            ChatHeaderHalo()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                TopBarOverflowMenu(
                    onNewConversation = onNewConversation,
                    onOpenTerminal = onOpenTerminal,
                    onLaunchKimiWeb = onLaunchKimiWeb,
                    kimiWebLabel = kimiWebLabel,
                    canStopKimiWeb = canStopKimiWeb,
                    onStopKimiWeb = onStopKimiWeb,
                    onRefreshKimiWeb = onRefreshKimiWeb,
                    onOpenBrowser = onOpenBrowser,
                )
            }
        }
    }
}

/** Aether 风格的 38dp 圆形顶栏按钮：光晕垫底 + 96% 表面色实底。 */
@Composable
private fun ChatHeaderCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(ChatHeaderButtonSize)) {
        ChatHeaderHalo()
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                modifier = Modifier.size(ChatHeaderIconSize),
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun BoxScope.ChatHeaderHalo() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(radius = 6.dp, color = Color.Black, alpha = 0.12f),
            ),
    )
}

private val ChatHeaderButtonSize = 38.dp
private val ChatHeaderIconSize = 19.dp
private val ChatTopBarTailHeight = 42.dp

/** 聊天页顶栏的遮蔽强度：比设置页更强，避免正文透出后看起来发脏。 */
private const val ChatTopBarProgressiveAlpha = 0.72f
private const val ChatTopBarSurfaceAlpha = 0.9f
private const val ChatTopBarProgressiveRadius = 14f

private val TopBarMenuIconSize = 20.dp

/**
 * 首页顶栏溢出菜单。WindowListPopup 以父布局为锚点，因此与触发按钮包在同一个 Box 中，
 * 弹层从按钮下方右对齐展开。
 */
@Composable
private fun TopBarOverflowMenu(
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    Box {
        IconButton(
            onClick = { onRefreshKimiWeb(); showMenu = true },
            minWidth = 38.dp,
            minHeight = 38.dp,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                modifier = Modifier.size(ChatHeaderIconSize),
                contentDescription = stringResource(R.string.action_more),
            )
        }
        WindowListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { showMenu = false },
        ) {
            val newConversationText = stringResource(R.string.action_new_conversation)
            val openTerminalText = stringResource(R.string.action_open_terminal)
            val launchKimiWebText = kimiWebLabel
            val stopKimiWebText = stringResource(R.string.capability_kimi_stop)
            val openBrowserText = stringResource(R.string.action_open_browser)
            val autoReadText = stringResource(
                if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
            )
            val menuItems = remember(
                newConversationText,
                openTerminalText,
                launchKimiWebText,
                openBrowserText,
                stopKimiWebText,
                canStopKimiWeb,
                autoReadText,
                hasSpeechPlayback,
                autoReadEnabled,
            ) {
                listOf(
                    DropdownItem(
                        text = newConversationText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Rounded.AddComment,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = openTerminalText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = launchKimiWebText,
                        icon = { modifier ->
                            Icon(
                                painter = painterResource(R.drawable.ic_kimi_code),
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = openBrowserText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                ) + (if (canStopKimiWeb) {
                    listOf(
                        DropdownItem(
                            text = stopKimiWebText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = modifier.size(TopBarMenuIconSize),
                                )
                            },
                        ),
                    )
                } else {
                    emptyList()
                }) + if (hasSpeechPlayback) {
                    listOf(
                        DropdownItem(
                            text = autoReadText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = if (autoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                                        else Icons.AutoMirrored.Rounded.VolumeOff,
                                    contentDescription = null,
                                    modifier = modifier.size(TopBarMenuIconSize),
                                )
                            },
                        ),
                    )
                } else emptyList()
            }
            ListPopupColumn {
                menuItems.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = item,
                        optionSize = menuItems.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            if (hasSpeechPlayback && index == menuItems.lastIndex) {
                                speechSettings?.let { current ->
                                    scope.launch {
                                        SpeechSettingsRepository.save(current.copy(chatAutoSpeak = !current.chatAutoSpeak))
                                    }
                                }
                            } else when (index) {
                                0 -> onNewConversation()
                                1 -> onOpenTerminal()
                                2 -> onLaunchKimiWeb()
                                3 -> onOpenBrowser()
                                4 -> onStopKimiWeb()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun titleForRoute(route: AppRoute?): String = when (route) {
    is AppRoute.Home -> ""
    is AppRoute.Browser -> stringResource(R.string.route_browser)
    is AppRoute.Terminal -> stringResource(R.string.route_terminal)
    is AppRoute.Tools -> stringResource(R.string.route_tools)
    is AppRoute.WebToolsSettings -> stringResource(R.string.web_tools_settings_title)
    is AppRoute.Skills -> stringResource(R.string.route_skills)
    is AppRoute.Characters -> "角色"
    is AppRoute.CharacterDetail -> "角色详情"
    is AppRoute.CharacterEditor -> "编辑角色"
    is AppRoute.CharacterPersona -> "我的人设"
    is AppRoute.CharacterMemory -> "剧情记忆"
    is AppRoute.Permissions -> stringResource(R.string.route_permissions)
    is AppRoute.SystemEnhance -> stringResource(R.string.route_system_enhancements)
    is AppRoute.Settings -> stringResource(R.string.route_settings)
    is AppRoute.SpeechSettings -> stringResource(R.string.speech_settings_title)
    is AppRoute.SpeechRecognition -> stringResource(R.string.speech_recognition_title)
    is AppRoute.SpeechSynthesis -> stringResource(R.string.speech_synthesis_title)
    is AppRoute.SpeechOss -> stringResource(R.string.speech_oss_title)
    is AppRoute.AppearanceSettings -> stringResource(R.string.appearance_title)
    is AppRoute.DataBackup -> stringResource(R.string.data_backup_title)
    is AppRoute.Memory -> stringResource(R.string.route_memory)
    is AppRoute.LinuxEnvironment -> stringResource(R.string.route_linux_environment)
    is AppRoute.Workspace -> stringResource(R.string.capability_workspace)
    is AppRoute.SharedFolders -> stringResource(R.string.route_shared_folders)
    is AppRoute.LinuxFiles -> stringResource(R.string.route_linux_files)
    is AppRoute.ModelProviders -> stringResource(R.string.route_model_providers)
    is AppRoute.CommunityCatalog -> "从目录添加"
    is AppRoute.CommunityCatalogProvider -> "选择模型"
    is AppRoute.McpServers -> stringResource(R.string.route_mcp_servers)
    is AppRoute.McpServerDetail -> stringResource(R.string.route_mcp_server_detail)
    is AppRoute.ModelProviderDetail -> stringResource(R.string.route_provider_details)
    is AppRoute.ModelProviderNew -> stringResource(R.string.route_new_provider)
    null -> stringResource(R.string.app_name)
}

@Composable
private fun CharacterTopBarIdentity(character: RoleplayChatUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 32.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}
