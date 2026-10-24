package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatTimelineEntriesTest {

    @Test
    fun unchangedWorkProcessReusesPreviousEntry() {
        val thinking = ThinkingMessageUi(id = "think-1", content = "分析", isStreaming = false)
        val first = listOf(
            UserMessageUi(id = "user-1", content = "问题"),
            thinking,
            AgentMessageUi(id = "agent-1", content = "答", isStreaming = true),
        )
        val previous = first.toTimelineEntries()
        val next = listOf(
            first[0],
            thinking,
            AgentMessageUi(id = "agent-1", content = "答案", isStreaming = true),
        ).toTimelineEntries(previous)

        assertEquals("work-think-1", previous[1].key)
        assertEquals(previous[1].key, next[1].key)
    }

    @Test
    fun changedWorkProcessMemberCreatesNewEntry() {
        val first = listOf(ThinkingMessageUi(id = "think-1", content = "分析", isStreaming = true))
        val previous = first.toTimelineEntries()
        val next = listOf(
            ThinkingMessageUi(id = "think-1", content = "分析中", isStreaming = true),
        ).toTimelineEntries(previous)

        assertNotSame(previous[0], next[0])
    }

    @Test
    fun prependingTheStartOfAWorkProcessPreservesItsVisibleKey() {
        val partialThinking = ThinkingMessageUi("think-tail", "后续思考", isStreaming = false)
        val answer = AgentMessageUi("answer", "结果")
        val previous = listOf(partialThinking, answer).toTimelineEntries()
        val next = listOf(
            UserMessageUi("user", "问题"),
            ThinkingMessageUi("think-start", "起始思考", isStreaming = false),
            partialThinking,
            answer,
        ).toTimelineEntries(previous)

        // 前后两次都归属同一个 Turn，key 由首个工作过程决定，前插后必须保持不变。
        assertEquals(previous[0].key, next[1].key)
        val turn = next[1] as AgentTimelineEntry.Turn
        assertEquals(2, turn.work.size)
        assertEquals(1, turn.answers.size)
    }

    @Test
    fun splittingAPreviouslyPrependedGroupDoesNotReuseItsKeyTwice() {
        val first = ThinkingMessageUi("think-first", "起始思考", isStreaming = false)
        val last = ThinkingMessageUi("think-last", "后续思考", isStreaming = false)
        val previous = listOf(first, last).toTimelineEntries(listOf(last).toTimelineEntries())

        val next = listOf(first, AgentMessageUi("answer", "中间结果"), last).toTimelineEntries(previous)

        assertEquals(next.size, next.map { it.key }.distinct().size)
    }

    /**
     * 一次问答里「正文 → 工具 → 正文」在投影层是多条 [AgentMessageUi]，展示时必须收进同一个气泡；
     * 且工具之前的正文属于过程旁白，只把最后一组工作过程之后的正文留在外层气泡里。
     */
    @Test
    fun narrationBeforeToolCallsFoldsIntoWorkAndOnlyFinalTextIsTheAnswer() {
        val messages = listOf(
            UserMessageUi("user-1", "帮我看看"),
            AgentMessageUi("assistant-run-1-1-0", "我先看一下", isStreaming = false),
            ToolActivityMessageUi(
                id = "run-1-tool-2-call-a",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            AgentMessageUi("assistant-run-1-2-0", "找到了", isStreaming = false),
            ToolActivityMessageUi(
                id = "run-1-tool-3-call-b",
                toolName = "read_file",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "cat",
            ),
            AgentMessageUi("assistant-run-1-3-0", "结论如下", isStreaming = false),
        )

        val entries = messages.toTimelineEntries()

        assertEquals(2, entries.size)
        val turn = entries[1] as AgentTimelineEntry.Turn

        // 过程：旁白、工具、旁白、工具；回答：只剩最后一段结论。
        assertEquals(4, turn.work.size)
        assertEquals(listOf("我先看一下", "找到了"), turn.work.filterIsInstance<AgentMessageUi>().map { it.content })
        assertEquals(1, turn.answers.size)

        val merged = turn.answers.mergeTurnAnswers(turn.key)
        assertEquals("assistant-run-1-3-0", merged.id)
        assertEquals("结论如下", merged.content)
    }

    /** 没有工具调用的纯问答，正文全部是回答，不能被误判成旁白。 */
    @Test
    fun plainAnswerWithoutToolCallsStaysInTheBubble() {
        val messages = listOf(
            UserMessageUi("user-1", "你好"),
            AgentMessageUi("assistant-run-1-1-0", "你好，有什么可以帮你", isStreaming = false),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn

        assertEquals(0, turn.work.size)
        assertEquals(1, turn.answers.size)
        assertEquals("你好，有什么可以帮你", turn.answers.mergeTurnAnswers(turn.key).content)
    }

    /** 思考之后再出现正文，正文是最终回答。 */
    @Test
    fun textAfterThinkingIsTheAnswer() {
        val messages = listOf(
            UserMessageUi("user-1", "问"),
            ThinkingMessageUi("run-1-thinking-1-0", "想一想", isStreaming = false),
            AgentMessageUi("assistant-run-1-1-0", "答", isStreaming = false),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn

        assertEquals(1, turn.work.size)
        assertEquals(listOf("答"), turn.answers.map { (it as AgentMessageUi).content })
    }

    /** 相邻两轮问答不能并进同一个气泡。 */
    @Test
    fun consecutiveUserTurnsAreSeparateBubbles() {
        val messages = listOf(
            UserMessageUi("user-1", "第一问"),
            AgentMessageUi("assistant-run-1-1-0", "第一答"),
            UserMessageUi("user-2", "第二问"),
            AgentMessageUi("assistant-run-2-1-0", "第二答"),
        )

        val entries = messages.toTimelineEntries()

        // 没有工作过程时 key 退回首条正文 id；有工作过程时沿用 work- 前缀。
        assertEquals(
            listOf("user-1", "assistant-run-1-1-0", "user-2", "assistant-run-2-1-0"),
            entries.map { it.key },
        )
        assertEquals(2, entries.count { it is AgentTimelineEntry.Turn })
    }

    /** 正文还没到达时仍要立刻占据一个气泡位，并保持流式态。 */
    @Test
    fun emptyTurnStillProducesAStreamingBubble() {
        val messages = listOf(
            UserMessageUi("user-1", "问题"),
            ThinkingMessageUi("run-1-thinking-1-0", "思考中", isStreaming = true),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn
        assertEquals(0, turn.answers.size)

        val merged = turn.answers.mergeTurnAnswers(turn.key)
        assertEquals(turn.key, merged.id)
        assertEquals("", merged.content)
        assertTrue(merged.isStreaming)
    }

    /** 合并后只要还有一段在流式，整轮就按流式渲染，避免正文闪烁。 */
    @Test
    fun mergedAnswerStaysStreamingWhileAnySegmentIsStreaming() {
        val turn = AgentTimelineEntry.Turn(
            key = "k",
            work = emptyList(),
            answers = listOf(
                AgentMessageUi("a-1", "已完成", isStreaming = false),
                AgentMessageUi("a-2", "正在写", isStreaming = true),
            ),
        )

        val merged = turn.answers.mergeTurnAnswers(turn.key)
        assertTrue(merged.isStreaming)
    }
}
