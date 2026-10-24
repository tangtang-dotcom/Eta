package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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

    /**
     * 「思考 → 工具 → 正文 → 思考 → 工具 → 正文」应当切成两段，
     * 界面呈现为「子气泡 → 正文 → 子气泡 → 正文」的交替节奏，
     * 而不是把所有过程堆在气泡顶部。
     */
    @Test
    fun workAndAnswersAlternateBySection() {
        val messages = listOf(
            UserMessageUi("user-1", "帮我看看"),
            ThinkingMessageUi("run-1-thinking-1-0", "先想一下", isStreaming = false),
            ToolActivityMessageUi(
                id = "run-1-tool-1-call-a",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            AgentMessageUi("assistant-run-1-1-0", "看到三个模块", isStreaming = false),
            ThinkingMessageUi("run-1-thinking-2-0", "再看细节", isStreaming = false),
            ToolActivityMessageUi(
                id = "run-1-tool-2-call-b",
                toolName = "read_file",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "cat",
            ),
            AgentMessageUi("assistant-run-1-2-0", "结论如下", isStreaming = false),
        )

        val entries = messages.toTimelineEntries()

        assertEquals(2, entries.size)
        val turn = entries[1] as AgentTimelineEntry.Turn

        // 两段：每段都是「一组过程 + 紧跟其后的正文」。
        assertEquals(2, turn.sections.size)

        val first = turn.sections[0]
        assertEquals(2, first.work.size)
        assertEquals(listOf("看到三个模块"), first.answers.map { (it as AgentMessageUi).content })

        val second = turn.sections[1]
        assertEquals(2, second.work.size)
        assertEquals(listOf("结论如下"), second.answers.map { (it as AgentMessageUi).content })

        // 整轮正文合并后仍是完整的答复顺序。
        assertEquals("看到三个模块\n\n结论如下", turn.answers.mergeTurnAnswers(turn.key).content)
    }

    /**
     * 回归：早期实现只在轮次边界切段，导致「过程 → 正文 → 过程 → 正文」全挤成一段，
     * 界面上退化成「所有工具和思考堆在顶部、正文在下面」。这里锁定真正的交替切分。
     */
    @Test
    fun newWorkProcessAfterAnswersStartsANewSection() {
        val messages = listOf(
            UserMessageUi("user-1", "问题"),
            ThinkingMessageUi("run-1-thinking-1-0", "思考一", isStreaming = false),
            AgentMessageUi("assistant-run-1-1-0", "正文一", isStreaming = false),
            ThinkingMessageUi("run-1-thinking-2-0", "思考二", isStreaming = false),
            AgentMessageUi("assistant-run-1-2-0", "正文二", isStreaming = false),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn

        assertEquals(2, turn.sections.size)
        assertEquals(1, turn.sections[0].work.size)
        assertEquals(listOf("正文一"), turn.sections[0].answers.map { (it as AgentMessageUi).content })
        assertEquals(1, turn.sections[1].work.size)
        assertEquals(listOf("正文二"), turn.sections[1].answers.map { (it as AgentMessageUi).content })
    }

    /** 连续多个过程步骤之间没有正文，必须并进同一个子气泡。 */
    @Test
    fun consecutiveWorkStepsShareOneSection() {
        val messages = listOf(
            UserMessageUi("user-1", "问"),
            ThinkingMessageUi("run-1-thinking-1-0", "想", isStreaming = false),
            ToolActivityMessageUi(
                id = "run-1-tool-1-call-a",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            ThinkingMessageUi("run-1-thinking-1-1", "继续想", isStreaming = false),
            AgentMessageUi("assistant-run-1-1-0", "答", isStreaming = false),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn

        assertEquals(1, turn.sections.size)
        assertEquals(3, turn.sections[0].work.size)
        assertEquals(1, turn.sections[0].answers.size)
    }

    /** 没有工具调用的纯问答，只有一段正文，不能凭空多出子气泡。 */
    @Test
    fun plainAnswerStaysASingleContentSection() {
        val messages = listOf(
            UserMessageUi("user-1", "你好"),
            AgentMessageUi("assistant-run-1-1-0", "你好，有什么可以帮你", isStreaming = false),
        )

        val turn = messages.toTimelineEntries()[1] as AgentTimelineEntry.Turn

        assertEquals(1, turn.sections.size)
        assertEquals(0, turn.sections[0].work.size)
        assertEquals("你好，有什么可以帮你", turn.answers.mergeTurnAnswers(turn.key).content)
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

        assertEquals(2, entries.count { it is AgentTimelineEntry.Turn })
        assertEquals(4, entries.size)
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
            sections = listOf(
                AgentTimelineEntry.TurnSection(
                    work = emptyList(),
                    answers = listOf(
                        AgentMessageUi("a-1", "已完成", isStreaming = false),
                        AgentMessageUi("a-2", "正在写", isStreaming = true),
                    ),
                ),
            ),
        )

        assertTrue(turn.answers.mergeTurnAnswers(turn.key).isStreaming)
    }
}
