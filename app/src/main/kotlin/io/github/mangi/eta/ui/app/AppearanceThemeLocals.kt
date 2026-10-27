package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalBlurEnabled = staticCompositionLocalOf { true }
internal val LocalTopBarBlurStyle = staticCompositionLocalOf { AppearanceTopBarBlurStyle.GAUSSIAN }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }

/**
 * 首页顶栏悬浮在内容之上，消息列表需要自己补回这部分顶部留白。
 *
 * por 版没有这一个；AetherChatColors 的悬浮态与聊天列表顶部留白依赖它。
 */
internal val LocalChatTopOverlayInset = staticCompositionLocalOf { 0.dp }

/**
 * 当前解析出的深色状态，切换主题时立即翻转，不依赖颜色插值的中间值。
 *
 * AetherChatColors 的三态配色（莫奈 / 深 / 浅）全靠它判定深浅分支。
 */
internal val LocalIsDarkTheme = staticCompositionLocalOf { false }
