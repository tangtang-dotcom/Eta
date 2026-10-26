package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentChatMessageUi
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
 * 轮内分组的行为锁定。
 *
 * 规则移植自 RikkaHub Agent Pure 的 groupMessageParts(foldProcessNarration = true)：
 * 最后一次思考/工具之前的文本算过程旁白，之后的一律是正文。
 */
class AgentTurnGroupingTest {

    private fun tool(id: String) = ToolActivityMessageUi(
        id = id,
        toolName = "terminal",
        status = ToolActivityStatusUi.Success,
        argumentsSummary = "ls",
    )

    private fun describe(groups: List<AgentTurnGroup>): List<String> = groups.map { group ->
        when (group) {
            is AgentTurnGroup.Process -> "过程:" + group.steps.joinToString(",") { it.id }
            is AgentTurnGroup.Content -> "正文:" + group.message.id
        }
    }

    /** 纯问答：没有过程步骤，所有文本都是正文。 */
    @Test
    fun plainAnswerHasNoProcessGroup() {
        val groups = listOf<AgentChatMessageUi>(
            AgentMessageUi("a-1", "你好"),
        ).toAgentTurnGroups()

        assertEquals(listOf("正文:a-1"), describe(groups))
    }

    /**
     * 回归：夹在工具之间的短正文是过程旁白，不再单独成为正文块。
     *
     * 这正是「每显示更多一步就分出新气泡」的成因：旁白各自成块，每块带一个气泡。
     */
    @Test
    fun narrationBeforeLastToolFoldsIntoProcess() {
        val groups = listOf<AgentChatMessageUi>(
            ThinkingMessageUi("t-1", "想一下"),
            AgentMessageUi("n-1", "我先看下文件"),
            tool("tool-1"),
            AgentMessageUi("a-1", "找到了，文件在 /tmp"),
        ).toAgentTurnGroups()

        assertEquals(
            listOf("过程:t-1,n-1,tool-1", "正文:a-1"),
            describe(groups),
        )
    }

    /**
     * 关键安全线：最后一段文本永远是正文，不会被吞进过程。
     *
     * 与旧版按字数猜测的区别——即使回答只有一个字，也必须是独立的正文块。
     */
    @Test
    fun trailingShortAnswerAlwaysBecomesContent() {
        val groups = listOf<AgentChatMessageUi>(
            tool("tool-1"),
            AgentMessageUi("a-1", "好"),
        ).toAgentTurnGroups()

        assertEquals(listOf("过程:tool-1", "正文:a-1"), describe(groups))
    }

    /** 空白正文既不渲染，也不能把过程切成两段。 */
    @Test
    fun blankTextDoesNotSplitProcess() {
        val groups = listOf<AgentChatMessageUi>(
            tool("tool-1"),
            AgentMessageUi("blank", "   "),
            tool("tool-2"),
        ).toAgentTurnGroups()

        assertEquals(listOf("过程:tool-1,tool-2"), describe(groups))
    }

    /** 多轮过程之间都有旁白时，各自归属所在的过程组。 */
    @Test
    fun narrationBetweenMultipleRoundsFoldsIntoFollowingProcess() {
        val groups = listOf<AgentChatMessageUi>(
            AgentMessageUi("n-1", "先看看目录"),
            tool("tool-1"),
            AgentMessageUi("n-2", "再读一下配置"),
            tool("tool-2"),
            AgentMessageUi("a-1", "最终结论"),
        ).toAgentTurnGroups()

        assertEquals(
            listOf("过程:n-1,tool-1,n-2,tool-2", "正文:a-1"),
            describe(groups),
        )
    }
}

/**
 * 轮聚合的行为锁定。
 *
 * 一轮对话必须只产出一个 Turn 条目：气泡粒度由此决定。
 */
class AgentTurnMergeTest {

    private fun tool(id: String) = ToolActivityMessageUi(
        id = id,
        toolName = "terminal",
        status = ToolActivityStatusUi.Success,
        argumentsSummary = "ls",
    )

    @Test
    fun oneRoundBecomesSingleTurn() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "问")),
            AgentTimelineEntry.Message(tool("tool-1")),
            AgentTimelineEntry.Message(AgentMessageUi("a-1", "答")),
        ).mergeIntoAgentTurns()

        assertEquals(2, entries.size)
        assertTrue(entries[0] is AgentTimelineEntry.Message)
        val turn = entries[1] as AgentTimelineEntry.Turn
        assertEquals("turn-user-1", turn.key)
        // 过程与正文都在这一个条目里，界面上因此只有一个气泡。
        assertEquals(1, turn.answers.size)
        assertEquals(1, turn.workMessages.size)
    }

    /** 系统提示独立成条，不能并进 Turn，否则文案会整条消失。 */
    @Test
    fun systemNoticeStaysSeparate() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "问")),
            AgentTimelineEntry.Message(AgentMessageUi("a-1", "答")),
            AgentTimelineEntry.Message(
                SystemNoticeMessageUi(id = "notice-1", code = SystemNoticeCode.Interrupted),
            ),
        ).mergeIntoAgentTurns()

        assertEquals(3, entries.size)
        assertTrue(entries[1] is AgentTimelineEntry.Turn)
        assertTrue(entries[2] is AgentTimelineEntry.Message)
    }

    /** 没有用户消息可锚（历史页中段起读）时，用首个成员 id 兜底。 */
    @Test
    fun orphanRoundFallsBackToFirstMessageId() {
        val entries = listOf(
            AgentTimelineEntry.Message(AgentMessageUi("a-1", "答")),
        ).mergeIntoAgentTurns()

        assertEquals("turn-a-1", (entries.single() as AgentTimelineEntry.Turn).key)
    }
}
