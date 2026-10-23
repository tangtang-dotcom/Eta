package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    fun controlFrostAlpha(): Float = if (LocalIsDarkTheme.current) 0.72f else 0.58f

    /** 顶栏磨砂表面不透明度。 */
    @Composable
    fun topBarSurfaceAlpha(): Float = if (LocalIsDarkTheme.current) 0.92f else 0.74f

    /** 顶栏磨砂渐变端不透明度。 */
    @Composable
    fun topBarProgressiveAlpha(): Float = if (LocalIsDarkTheme.current) 0.82f else 0.56f

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

    /**
     * 顶栏玻璃控件（返回圆钮、模型胶囊、更多圆钮）的底色：
     * 比页面底色亮一档，配合模糊采样形成 TG 那种「深色玻璃」质感。
     */
    @Composable
    fun controlSurface(): Color = when {
        monet -> MiuixTheme.colorScheme.surfaceContainerHigh
        dark -> Color(0xFF24282D)
        else -> Color(0xFFFFFFFF)
    }

    /** 玻璃控件边缘的高光描边：深浅都用白色，浅色提高不透明度才压得住白玻璃。 */
    @Composable
    fun controlStroke(): Color = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.85f)

    /** ChatGptControlShadow */
    val controlShadow: Color = Color(0x14000000)

    /**
     * 柔光玻璃参数（单位 px，运行时按屏幕密度换算）。深浅两套各自独立，
     * 透明度都比原配置再收一档，让底下的内容透得出来。
     */
    @Composable
    fun glassBackground(): Color = if (LocalIsDarkTheme.current) {
        Color(0x330D0D0D)
    } else {
        Color(0x59E1E1E1)
    }

    @Composable
    fun glassBaseBlurPx(): Float = if (LocalIsDarkTheme.current) 80f else 60f

    /** 边缘光带宽：原配置 30px 在 40dp 圆钮上太宽，会糊成一圈内环，收到 6px。 */
    @Composable
    fun glassHighlightWidthPx(): Float = 6f

    /** 玻璃基底不透明度：越低越透，深色比浅色再透一档。 */
    @Composable
    fun glassBaseAlpha(): Float = if (LocalIsDarkTheme.current) 0.34f else 0.12f

    /** 边缘光渐变：顶部亮、底部暗，模拟光从上方打下来。 */
    @Composable
    fun glassStrokeTop(): Color = if (LocalIsDarkTheme.current) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.95f)
    }

    @Composable
    fun glassStrokeBottom(): Color = if (LocalIsDarkTheme.current) {
        Color.White.copy(alpha = 0.03f)
    } else {
        Color.White.copy(alpha = 0.35f)
    }

    /** ChatGptComposerShadow */
    val composerShadow: Color = Color(0x18000000)
}

/**
 * 玻璃边缘光：上亮下暗的 1dp 渐变描边。
 * 用纯绘制代替 Miuix 的 highlight（后者每帧要在整个控件上跑一遍 shader，
 * 顶栏三个控件叠起来会明显掉帧）。
 */
internal fun Modifier.glassEdge(top: Color, bottom: Color, width: Dp = 1.dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(top, bottom)),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            style = Stroke(width = width.toPx()),
        )
    }
