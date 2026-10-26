package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi

/**
 * 一轮回答内部的渲染分组。
 *
 * 移植自 RikkaHub Agent Pure 的 groupMessageParts(foldProcessNarration = true)：
 * 把「过程步骤」和「夹在过程之间的旁白」收成同一个 Process 组，只把最后一轮
 * 过程之后的正文留作独立的 Content 组。
 *
 * 为什么不是「按消息归属」而是「按分组」：一轮里模型常常先说一句、调个工具、
 * 再说一句。如果把每句话都当成独立正文，界面上就会冒出「过程卡 → 半句话 →
 * 过程卡」的碎片，而且每条正文都可能带出一个新气泡。分组只解决一件事：
 * 哪些文本属于过程，哪些是真正的回答。
 *
 * 与之前那版「短正文按字数并进过程」的关键区别：判定不看长度，只看位置。
 * 只有「最后一次思考或工具之前」的文本才算旁白；之后的一律是正文。按字数猜
 * 会在模型把回答说得短时把整段回答吞进过程，那正是「回复消失」的成因。
 */
internal sealed interface AgentTurnGroup {
    /**
     * 过程组：思考、工具调用、工具摘要，以及夹在它们中间的旁白。
     *
     * 顺序即发生顺序，旁白保留它被说出的位置，不会挪到组首或组尾。
     */
    data class Process(
        val steps: List<AgentChatMessageUi>,
    ) : AgentTurnGroup

    /** 内容组：真正的模型回答，永远完整显示，不折叠。 */
    data class Content(
        val message: AgentChatMessageUi,
    ) : AgentTurnGroup
}

/** 参与过程组的消息类型。过程类型判断收敛在这一处，方便跟随官方调整。 */
private fun AgentChatMessageUi.isProcessStep(): Boolean =
    this is ThinkingMessageUi || this is ToolActivityMessageUi || this is ToolSummaryMessageUi

/**
 * 空白文本不参与渲染，也不能打断当前的过程分组。
 *
 * 流式输出时正文会先以空串占位；把它当成内容组会让「过程 → 空正文 → 过程」
 * 被切成两段，等真正的文本到达时再合并，视觉上就是过程卡跳一下。
 */
private fun AgentChatMessageUi.isVisuallyBlank(): Boolean =
    this is AgentMessageUi && content.isBlank()

/**
 * 把一轮消息按 RikkaHub 的规则分组。
 *
 * - 连续的思考/工具并进同一个 [AgentTurnGroup.Process]；
 * - 位置在「最后一次思考/工具」之前的文本作为旁白并进过程组；
 * - 位置在其之后的文本才是 [AgentTurnGroup.Content]；
 * - 空白文本跳过且不打断分组。
 *
 * 纯函数：不读组合状态、不改消息实例、不产生新的身份，因此可安全 remember。
 */
internal fun List<AgentChatMessageUi>.toAgentTurnGroups(): List<AgentTurnGroup> {
    if (isEmpty()) return emptyList()

    // 最后一次思考/工具的位置：它之前的文本是过程旁白，之后的才是回答。
    // 没有过程步骤时得到 -1，于是所有文本都按正文处理，与 RikkaHub 一致。
    val lastProcessIndex = indices.lastOrNull { this[it].isProcessStep() } ?: -1

    val groups = mutableListOf<AgentTurnGroup>()
    val steps = mutableListOf<AgentChatMessageUi>()

    fun flushProcess() {
        if (steps.isEmpty()) return
        groups += AgentTurnGroup.Process(steps.toList())
        steps.clear()
    }

    this.forEachIndexed { index, message ->
        when {
            message.isProcessStep() -> steps += message
            // 空白正文既不渲染也不切断过程：流式头部会先来一段空串。
            message.isVisuallyBlank() -> Unit
            // 只按位置判定，不看字数：最后一次过程之前的文本是旁白。
            index < lastProcessIndex -> steps += message
            else -> {
                flushProcess()
                groups += AgentTurnGroup.Content(message)
            }
        }
    }
    flushProcess()
    return groups
}

/**
 * 过程组里是否还有正在进行的步骤。
 *
 * 用于决定过程卡默认展开还是收起：只要有流式中的思考或运行中的工具，
 * 就说明这一轮还在干活，卡片应当保持活动态。
 */
internal fun List<AgentChatMessageUi>.hasRunningWork(): Boolean =
    any { message ->
        (message is ThinkingMessageUi && message.isStreaming) ||
            (message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running)
    }
