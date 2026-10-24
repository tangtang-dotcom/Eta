package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮内分块的行为锁定。
 *
 * 规则与 chatcube 的 `groupMessageParts()` 一致：连续的思考与工具归成一个过程块，
 * 正文永远单独成块且永不折叠。这里只断言「分块结果」，不涉及任何渲染细节。
 */
class AgentChatTimelineEntriesTest {

    private fun List<AgentChatMessageUi>.turn(key: String = "turn"): AgentTimelineEntry.Turn =
        AgentTimelineEntry.Turn(key = key, blocks = toTurnBlocks())

    private fun AgentTurnBlock.describe(): String = when (this) {
        is AgentTurnBlock.Procedure -> "过程:" + steps.joinToString(",") { it.id }
        is AgentTurnBlock.Content -> "正文:" + message.id
    }

    /** 没有工具调用的纯问答：只有一个内容块，不能凭空多出子气泡。 */
    @Test
    fun plainAnswerIsASingleContentBlock() {
        val blocks = listOf(
            UserMessageUi("user-1", "你好"),
            AgentMessageUi("agent-1", "你好，有什么可以帮你", isStreaming = false),
        ).turn().blocks

        assertEquals(listOf("正文:agent-1"), blocks.map { it.describe() })
    }

    /**
     * 回归：夹在过程之间的正文曾被折进子气泡，导致模型说的话消失。
     * 现在正文必须完整留在外层气泡里，只是位置落在它前面的过程块之后。
     */
    @Test
    fun answerBetweenWorkStepsStaysInBubble() {
        val blocks = listOf(
            UserMessageUi("user-1", "帮我看看"),
            ThinkingMessageUi("think-1", "先想一下", isStreaming = false),
            AgentMessageUi("agent-1", "我先看看文件", isStreaming = false),
            ToolActivityMessageUi(
                id = "tool-1",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            AgentMessageUi("agent-2", "结论如下", isStreaming = false),
        ).turn().blocks

        // 过程 → 正文 → 过程 → 正文，四块交替。
        assertEquals(
            listOf("过程:think-1", "正文:agent-1", "过程:tool-1", "正文:agent-2"),
            blocks.map { it.describe() },
        )
    }

    /** 连续的过程步骤之间没有正文，必须并进同一个过程块。 */
    @Test
    fun consecutiveWorkStepsShareOneProcedureBlock() {
        val blocks = listOf(
            UserMessageUi("user-1", "问"),
            ThinkingMessageUi("think-1", "想", isStreaming = false),
            ToolActivityMessageUi(
                id = "tool-1",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            ThinkingMessageUi("think-2", "继续想", isStreaming = false),
            AgentMessageUi("agent-1", "答", isStreaming = false),
        ).turn().blocks

        assertEquals(listOf("过程:think-1,tool-1,think-2", "正文:agent-1"), blocks.map { it.describe() })
    }

    /** 以思考收尾的回答同样完整保留：最后一块是过程，不影响前面正文的归属。 */
    @Test
    fun answerStaysWhenTurnEndsWithWork() {
        val blocks = listOf(
            UserMessageUi("user-1", "帮我查一下"),
            AgentMessageUi("agent-1", "查到了，是 42", isStreaming = false),
            ThinkingMessageUi("think-1", "再确认一下", isStreaming = false),
        ).turn().blocks

        assertEquals(listOf("正文:agent-1", "过程:think-1"), blocks.map { it.describe() })
    }

    /** 系统提示属于用户要看见的状态，不算过程，也不并进过程块。 */
    @Test
    fun systemNoticeIsItsOwnContentBlock() {
        val blocks = listOf(
            UserMessageUi("user-1", "开始"),
            ThinkingMessageUi("think-1", "想", isStreaming = false),
            SystemNoticeMessageUi("notice-1", SystemNoticeCode.Stopped),
        ).turn().blocks

        assertEquals(listOf("过程:think-1", "正文:notice-1"), blocks.map { it.describe() })
    }

    /** 生成刚开始时的空正文仍要占位，否则气泡不会立刻出现。 */
    @Test
    fun streamingEmptyAnswerKeepsItsBlock() {
        val blocks = listOf(
            UserMessageUi("user-1", "开始"),
            ThinkingMessageUi("think-1", "先看看", isStreaming = false),
            AgentMessageUi("agent-1", "", isStreaming = true),
        ).turn().blocks

        assertEquals(listOf("过程:think-1", "正文:agent-1"), blocks.map { it.describe() })
    }

    /** 相邻两轮问答不能并进同一个气泡。 */
    @Test
    fun consecutiveUserTurnsAreSeparateTurns() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "第一问")),
            AgentTimelineEntry.Message(AgentMessageUi("agent-1", "第一答")),
            AgentTimelineEntry.Message(UserMessageUi("user-2", "第二问")),
            AgentTimelineEntry.Message(AgentMessageUi("agent-2", "第二答")),
        ).mergeTurnTurns()

        assertEquals(2, entries.count { it is AgentTimelineEntry.Turn })
    }

    /** 一轮里没有任何正文时，仍要产出一个空块占位并保持流式。 */
    @Test
    fun emptyTurnStillProducesABubble() {
        val turn = listOf(
            UserMessageUi("user-1", "问题"),
            ThinkingMessageUi("think-1", "思考中", isStreaming = true),
        ).turn()

        assertEquals(0, turn.answers.size)
        val merged = turn.answers.mergeTurnAnswers(turn.key)
        assertEquals("", merged.content)
        assertTrue(merged.isStreaming)
    }

    /** 合并后只要还有一段在流式，整轮就按流式渲染，避免正文闪烁。 */
    @Test
    fun mergedAnswerStaysStreamingWhileAnySegmentIsStreaming() {
        val turn = AgentTimelineEntry.Turn(
            key = "k",
            blocks = listOf(
                AgentTurnBlock.Content("a-1", AgentMessageUi("a-1", "已完成", isStreaming = false)),
                AgentTurnBlock.Content("a-2", AgentMessageUi("a-2", "正在写", isStreaming = true)),
            ),
        )

        assertTrue(turn.answers.mergeTurnAnswers(turn.key).isStreaming)
    }

    /** 多段正文合并时用空行拼接，保留完整答复顺序。 */
    @Test
    fun mergedAnswerJoinsAllContentBlocks() {
        val turn = AgentTimelineEntry.Turn(
            key = "k",
            blocks = listOf(
                AgentTurnBlock.Content("a-1", AgentMessageUi("a-1", "第一段")),
                AgentTurnBlock.Procedure("p-1", listOf(ThinkingMessageUi("t-1", "想"))),
                AgentTurnBlock.Content("a-2", AgentMessageUi("a-2", "第二段")),
            ),
        )

        assertEquals("第一段\n\n第二段", turn.answers.mergeTurnAnswers(turn.key).content)
    }
}

/** 测试内的小包装：把消息序列走一遍真实的轮次合并。 */
private fun List<AgentTimelineEntry>.mergeTurnTurns(): List<AgentTimelineEntry> {
    val messages = mapNotNull { entry ->
        (entry as? AgentTimelineEntry.Message)?.message
    }
    val rebuilt = buildList {
        var buffer = mutableListOf<AgentChatMessageUi>()
        fun flush() {
            if (buffer.isEmpty()) return
            val blocks = buffer.toTurnBlocks()
            buffer = mutableListOf()
            if (blocks.isNotEmpty()) {
                add(AgentTimelineEntry.Turn(key = blocks.first().key, blocks = blocks))
            }
        }
        for (message in messages) {
            if (message is UserMessageUi) {
                flush()
                add(AgentTimelineEntry.Message(message))
            } else {
                buffer += message
            }
        }
        flush()
    }
    return rebuilt
}
