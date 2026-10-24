package io.github.mangi.eta.ui.components

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
        if (message.isProcedureStep()) {
            procedure += message
        } else {
            flushProcedure()
            blocks += AgentTurnBlock.Content(key = message.id, message = message)
        }
    }
    flushProcedure()
    return blocks
}

/**
 * 参与过程块的步骤类型。
 *
 * 工具摘要是对「这一步调用了哪些工具」的补充说明，跟着过程走；
 * 而系统提示（中断、重试、上下文压缩等）属于用户需要看见的状态，不算过程。
 */
internal fun AgentChatMessageUi.isProcedureStep(): Boolean =
    this is ThinkingMessageUi || this is ToolActivityMessageUi || this is ToolSummaryMessageUi
