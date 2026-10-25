package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi

/**
 * 一轮回答的内容块划分。
 *
 * 参考 chatcube 的 `groupMessageParts()`：把一轮里连续的思考与工具调用归成一个
 * 「过程块」，遇到正文或系统提示就收口、另起一个「内容块」。界面因此呈现为
 * 「过程块 → 正文 → 过程块 → 正文」的真实生成时序，而不是把所有过程堆在顶部。
 *
 * 与旧实现的区别只有一点，但很关键：**正文永远不被折叠**。夹在工具之间的正文仍然
 * 是完整的内容块，只是位置落在它前面的过程块之后；这样不会出现「模型说的话被吞进
 * 子气泡」的误判。
 *
 * 判定完全靠类型序列，不看文本内容，所以不需要猜测「这句话是不是旁白」。
 */
internal sealed interface AgentTurnBlock {
    val key: String

    /** 过程块：思考、工具调用、工具摘要。收起时只留尾部若干步。 */
    data class Procedure(
        override val key: String,
        val steps: List<AgentChatMessageUi>,
    ) : AgentTurnBlock

    /** 内容块：模型正文，或一条不压缩上下文的系统提示。永远完整显示。 */
    data class Content(
        override val key: String,
        val message: AgentChatMessageUi,
    ) : AgentTurnBlock
}

/**
 * 把一轮消息按发生顺序切成块。
 *
 * - 连续的过程步骤并进同一个 [AgentTurnBlock.Procedure]
 * - 正文与系统提示各自单独成块，且会打断前后的过程块
 * - 空白正文（生成刚开始时的占位）仍然保留，让气泡能立刻出现
 * - **短正文不打断过程块**：模型边做事边随口一句（"我先看下文件"）时，
 *   如果把过程切成两段，界面上就会冒出两个折叠行、两条竖线，
 *   「显示更多 N 步」频繁出现，节奏被切得很碎。这类一句半句的正文
 *   改为记进当前过程块内部，随过程一起展开/收起；足够长的正文仍然
 *   独立成块，保证模型真正说出的回答不会被折叠。
 *
 * 块的 key 由首个成员的 id 派生，流式增量刷新时身份保持稳定，不会触发整块重建。
 */
internal fun List<AgentChatMessageUi>.toTurnBlocks(): List<AgentTurnBlock> {
    val blocks = mutableListOf<AgentTurnBlock>()
    var procedure = mutableListOf<AgentChatMessageUi>()

    fun flushProcedure() {
        if (procedure.isEmpty()) return
        blocks += AgentTurnBlock.Procedure(
            key = "proc-${procedure.first().id}",
            steps = procedure.toList(),
        )
        procedure = mutableListOf()
    }

    for (message in this) {
        when {
            message.isProcedureStep() -> procedure += message
            // 短旁白并入正在展开的过程；过程还没开始时它就是开头，同样记进过程里。
            message.isBriefAside() -> procedure += message
            else -> {
                flushProcedure()
                blocks += AgentTurnBlock.Content(key = message.id, message = message)
            }
        }
    }
    flushProcedure()
    return blocks
}

/**
 * 多短算「一句旁白」。
 *
 * 阈值按视觉行数而不是字符数取：两个换行以内的正文在手机宽度下最多两三行，
 * 展开进过程块后不占地方；超过这个规模的正文就是模型真正在回答，
 * 必须独立成块、不能被收进折叠行。
 */
private const val BriefAsideMaxLines = 3
private const val BriefAsideMaxChars = 120

/**
 * 是否属于「随口一句」的短正文。
 *
 * 只对真正的模型正文生效：系统提示（重试、中断）必须独立成条，
 * 否则用户可能永远看不到它。
 */
/**
 * 按原始发生顺序摊平本轮所有块，返回其中的模型正文。
 *
 * 短旁白被并进了过程块，但复制与合并全文必须按真实顺序取到它，
 * 因此这里按块顺序展开、再过滤出正文，而不是先分别取再拼接。
 */
internal fun List<AgentTurnBlock>.answerMessagesInOrder(): List<AgentChatMessageUi> =
    flatMap { block ->
        when (block) {
            is AgentTurnBlock.Content -> listOf(block.message)
            is AgentTurnBlock.Procedure -> block.steps
        }
    }.filterIsInstance<AgentChatMessageUi>()

internal fun AgentChatMessageUi.isBriefAside(): Boolean {
    if (this !is AgentMessageUi) return false
    val text = content.trim()
    if (text.isEmpty()) return false
    // 正在流式的那段可能马上变长，先按长正文对待，避免块结构反复变动。
    if (isStreaming && text.length > BriefAsideMaxChars) return false
    return text.length <= BriefAsideMaxChars &&
        text.count { it == '\n' } < BriefAsideMaxLines
}

/**
 * 参与过程块的步骤类型。
 *
 * 工具摘要是对「这一步调用了哪些工具」的补充说明，跟着过程走；
 * 而系统提示（中断、重试、上下文压缩等）属于用户需要看见的状态，不算过程。
 */
internal fun AgentChatMessageUi.isProcedureStep(): Boolean =
    this is ThinkingMessageUi || this is ToolActivityMessageUi || this is ToolSummaryMessageUi
