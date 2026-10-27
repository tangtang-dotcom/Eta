package io.github.mangi.eta.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.platformDynamicColors

@Composable
fun AgentAppTheme(
    appearance: AppearanceSettings,
    applyInterfaceScale: Boolean,
    onResolvedDarkModeChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appearance.themeMode) {
        AppearanceThemeMode.SYSTEM -> systemDark
        AppearanceThemeMode.LIGHT -> false
        AppearanceThemeMode.DARK -> true
    }
    val colorSchemeMode = when {
        !appearance.monetEnabled && appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.Light
        !appearance.monetEnabled && appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.Dark
        !appearance.monetEnabled -> ColorSchemeMode.System
        appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val systemSeedColor = if (
        appearance.monetEnabled && appearance.accentColor == AppearanceAccentColor.SYSTEM
    ) {
        platformDynamicColors(isDark).primary
    } else {
        null
    }
    val keyColor = when {
        !appearance.monetEnabled -> null
        appearance.accentColor == AppearanceAccentColor.SYSTEM -> systemSeedColor
        else -> appearance.accentColor.seedColor()
    }
    val controller = remember(appearance, colorSchemeMode, keyColor, isDark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = keyColor,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = appearance.paletteStyle.toMiuixPaletteStyle(),
            isDark = isDark,
        )
    }
    val colors = controller.currentColors()
    val themedColors = remember(colors, isDark, appearance.monetEnabled, appearance.pureBlackEnabled) {
        when {
            // 莫奈取色优先：背景交给壁纸配色，这里一个色值都不覆盖。
            appearance.monetEnabled -> if (appearance.pureBlackEnabled && isDark) {
                colors.copy(background = Color.Black, surface = Color.Black)
            } else {
                colors
            }
            // 非莫奈：全应用统一底色。
            // 背景相关字段各自被不同页面读取，必须分别覆盖：
            // - background：聊天页舞台、部分自定义页面；
            // - surface：Miuix Scaffold 的默认容器色（设置页等）；
            // - surfaceContainer：设置里的分组卡片（CardDefaults 取它）与侧边栏。
            //
            // background 与 surface 统一成同一个底色；surfaceContainer 不能再等值，
            // 否则卡片与背景完全同色——设置页那些小 tap 会整块糊进背景里。
            // 这里让 surfaceContainer 等于「供应商气泡色」，卡片就是气泡那块颜色。
            isDark -> colors.copy(
                background = Color(0xFF10131C),
                surface = Color(0xFF10131C),
                surfaceContainer = Color(0xFF181A26),
            )
            else -> colors.copy(
                background = Color(0xFFF0F1F3),
                surface = Color(0xFFF0F1F3),
                surfaceContainer = Color(0xFFFFFFFF),
            )
        }
    }

    LaunchedEffect(isDark) { onResolvedDarkModeChange(isDark) }

    MiuixTheme(colors = themedColors) {
        val platformDensity = LocalDensity.current
        val appDensity = remember(platformDensity, appearance.interfaceScale, applyInterfaceScale) {
            if (applyInterfaceScale) {
                Density(
                    density = platformDensity.density * appearance.interfaceScale,
                    fontScale = platformDensity.fontScale,
                )
            } else {
                platformDensity
            }
        }
        CompositionLocalProvider(
            LocalIsDarkTheme provides isDark,
            LocalAppearanceSettings provides appearance,
            LocalBlurEnabled provides appearance.blurEnabled,
            LocalTopBarBlurStyle provides appearance.topBarBlurStyle,
            LocalPlatformDensity provides platformDensity,
            LocalDensity provides appDensity,
        ) {
            content()
        }
    }
}

private fun AppearancePaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle = when (this) {
    AppearancePaletteStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    AppearancePaletteStyle.NEUTRAL -> ThemePaletteStyle.Neutral
    AppearancePaletteStyle.VIBRANT -> ThemePaletteStyle.Vibrant
    AppearancePaletteStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
    AppearancePaletteStyle.RAINBOW -> ThemePaletteStyle.Rainbow
    AppearancePaletteStyle.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
    AppearancePaletteStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    AppearancePaletteStyle.FIDELITY -> ThemePaletteStyle.Fidelity
    AppearancePaletteStyle.CONTENT -> ThemePaletteStyle.Content
}

private fun AppearanceAccentColor.seedColor(): Color = when (this) {
    AppearanceAccentColor.SYSTEM, AppearanceAccentColor.BLUE -> Color(0xFF3482FF)
    AppearanceAccentColor.PURPLE -> Color(0xFF6750A4)
    AppearanceAccentColor.PINK -> Color(0xFFB0006D)
    AppearanceAccentColor.RED -> Color(0xFFBA1A1A)
    AppearanceAccentColor.ORANGE -> Color(0xFFB65D00)
    AppearanceAccentColor.YELLOW -> Color(0xFF7D5700)
    AppearanceAccentColor.GREEN -> Color(0xFF006D3B)
    AppearanceAccentColor.TEAL -> Color(0xFF006A6A)
}
