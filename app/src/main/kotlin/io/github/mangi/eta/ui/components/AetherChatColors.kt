package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.app.LocalIsDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天界面配色：
 * - 「莫奈取色」开启时全部走 Miuix 主题（跟随壁纸）；
 * - 关闭时深/浅两套严格复刻 Aether 的 AetherPalette：
 *   dark  background #151619 / gradientTop #1B1D22 / surface #1C1F23
 *         surfaceHigh #24282D / surfaceHigher #2C3036
 *         onSurface #F3F1EC / onSurfaceVariant #B9B4AA / primary #C0AEFF
 *   light background #FFFFFF / surface #FFFFFF / surfaceHigh #F3F3F2
 *         surfaceHigher #ECECEC / onSurface #202123 / onSurfaceVariant #6B6B6B
 *         primary #AD7BF9
 */
internal object AetherChatColors {
    private val monet: Boolean
        @Composable get() = LocalAppearanceSettings.current.monetEnabled

    private val dark: Boolean
        @Composable get() = LocalIsDarkTheme.current

    @Composable
    fun background(): Color = when {
        monet -> MiuixTheme.colorScheme.background
        dark -> Color(0xFF151619)
        else -> Color(0xFFFFFFFF)
    }

    @Composable
    fun backgroundGradientTop(): Color = when {
        monet -> MiuixTheme.colorScheme.background
        dark -> Color(0xFF1B1D22)
        else -> Color(0xFFFFFFFF)
    }

    @Composable
    fun surface(): Color = when {
        // 莫奈下 surface 常与 background 同色，输入卡片会“消失”；
        // 改用带色调的容器色，保证卡片/胶囊与背景有色差。
        monet -> MiuixTheme.colorScheme.surfaceContainer
        dark -> Color(0xFF1C1F23)
        else -> Color(0xFFFFFFFF)
    }

    @Composable
    fun surfaceHigh(): Color = when {
        monet -> MiuixTheme.colorScheme.surfaceContainer
        dark -> Color(0xFF24282D)
        else -> Color(0xFFF3F3F2)
    }

    @Composable
    fun surfaceHigher(): Color = when {
        monet -> MiuixTheme.colorScheme.surfaceContainerHigh
        dark -> Color(0xFF2C3036)
        else -> Color(0xFFECECEC)
    }

    @Composable
    fun onSurface(): Color = when {
        monet -> MiuixTheme.colorScheme.onSurface
        dark -> Color(0xFFF3F1EC)
        else -> Color(0xFF202123)
    }

    @Composable
    fun onSurfaceVariant(): Color = when {
        monet -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        dark -> Color(0xFFB9B4AA)
        else -> Color(0xFF6B6B6B)
    }

    /** 输入框占位文字：Aether 固定 #8C8C8C。 */
    @Composable
    fun placeholder(): Color = when {
        monet -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> Color(0xFF8C8C8C)
    }

    /** 发送键：莫奈开用主题主色，关用 Aether ChatGptPurple。 */
    @Composable
    fun sendButton(): Color = when {
        monet -> MiuixTheme.colorScheme.primary
        else -> Color(0xFF9B5CFF)
    }

    @Composable
    fun onSendButton(): Color = when {
        monet -> MiuixTheme.colorScheme.onPrimary
        else -> Color.White
    }

    /** 磨砂混色不透明度：暗色需要更实一些，否则卡片和背景糊在一起。 */
    @Composable
    fun frostBlendAlpha(): Float = if (LocalIsDarkTheme.current) 0.58f else 0.46f

    /** 顶栏圆钮/胶囊的磨砂混色不透明度：小控件略实一些，保证图标与文字清晰。 */
    @Composable
    fun controlFrostAlpha(): Float = if (LocalIsDarkTheme.current) 0.66f else 0.58f

    /** 顶栏磨砂表面不透明度。 */
    @Composable
    fun topBarSurfaceAlpha(): Float = if (LocalIsDarkTheme.current) 0.92f else 0.90f

    /** 顶栏磨砂渐变端不透明度。 */
    @Composable
    fun topBarProgressiveAlpha(): Float = if (LocalIsDarkTheme.current) 0.82f else 0.72f

    /**
     * 用户气泡底色：莫奈开保持原来的主题容器色，关闭时照抄 Aether 的 messageBubble
     * （dark #32264A / light #F0E3FF）。深浅两套都用 Aether 气泡，不再分叉。
     */
    @Composable
    fun userBubble(): Color = when {
        monet -> MiuixTheme.colorScheme.surfaceContainerHigh
        dark -> Color(0xFF32264A)
        else -> Color(0xFFF0E3FF)
    }

    /** 用户气泡文字：对应 Aether 的 onPrimaryContainer（dark #F0E9FF / light #4D2F8E）。 */
    @Composable
    fun onUserBubble(): Color = when {
        monet -> MiuixTheme.colorScheme.onSurface
        dark -> Color(0xFFF0E9FF)
        else -> Color(0xFF4D2F8E)
    }

    /** ChatGptControlShadow */
    val controlShadow: Color = Color(0x14000000)

    /** ChatGptComposerShadow */
    val composerShadow: Color = Color(0x18000000)
}
