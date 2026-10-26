package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Tools

/**
 * 过程步骤图标：与 rikkahub 完全一致的两枚图标。
 *
 * rikkahub 的 ChatMessageTools / ChatMessageReasoning 里，整个 ChainOfThought
 * 只用了两个图标：
 *
 * - 工具（客户端 Tool、服务端 ServerTool）：一律 `HugeIcons.Tools`（交叉工具），
 *   不按工具名区分；
 * - 思考（Reasoning）：一律 `HugeIcons.Idea01`（灯泡），且始终显示，
 *   执行中也不会换成加载点。
 *
 * 这里照抄这个映射。之前按工具语义分了终端、浏览器、相册等十几个图标，
 * 与参照物对不上，已改回统一图标。
 */
internal object WorkIcons {
    /** 工具步骤图标：所有工具共用。 */
    val Tool: ImageVector = HugeIcons.Tools

    /** 思考步骤图标：灯泡。 */
    val Reasoning: ImageVector = HugeIcons.Idea01
}

/**
 * 工具步骤图标。保留这个函数是为了不改动散布在各处的调用点；
 * 语义上它只表达「这一步是一个工具调用」，不再按工具名区分图形。
 */
internal fun hugeIconForTool(@Suppress("UNUSED_PARAMETER") toolId: String): ImageVector = WorkIcons.Tool
