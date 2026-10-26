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

    /**
     * 短旁白不切断过程：模型边做事边一句「我先看下文件」时，
     * 过程应保持为一条，而不是被切成两段、冒出两个折叠行。
     */
    @Test
    fun briefAsideDoesNotSplitProcedure() {
        val blocks = listOf(
            UserMessageUi("user-1", "帮我看看"),
            ThinkingMessageUi("think-1", "先想一下", isStreaming = false),
            AgentMessageUi("aside-1", "我先看下文件", isStreaming = false),
            ToolActivityMessageUi(
                id = "tool-1",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
            AgentMessageUi("agent-2", "结论如下", isStreaming = false),
        ).turn().blocks

        // 短旁白并入过程，只有最后那段长正文独立成块。
        assertEquals(listOf("过程:think-1,aside-1,tool-1", "正文:agent-2"), blocks.map { it.describe() })
    }

    /** 长正文仍然切断过程：模型真正在回答时不能被折进折叠行。 */
    @Test
    fun longAnswerStillSplitsProcedure() {
        val long = "这是一段足够长的回答，" + "用来确认它会独立成块。".repeat(10)
        val blocks = listOf(
            UserMessageUi("user-1", "问"),
            ThinkingMessageUi("think-1", "想", isStreaming = false),
            AgentMessageUi("agent-1", long, isStreaming = false),
            ToolActivityMessageUi(
                id = "tool-1",
                toolName = "terminal",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "ls",
            ),
        ).turn().blocks

        assertEquals(listOf("过程:think-1", "正文:agent-1", "过程:tool-1"), blocks.map { it.describe() })
    }

    /** 被并入过程块的旁白仍要出现在整轮全文里，复制不能丢半句话。 */
    @Test
    fun briefAsideSurvivesInMergedAnswer() {
        val turn = listOf(
            UserMessageUi("user-1", "问"),
            ThinkingMessageUi("think-1", "想", isStreaming = false),
            AgentMessageUi("aside-1", "我先看下文件", isStreaming = false),
            AgentMessageUi("agent-1", "结论如下", isStreaming = false),
        ).turn()

        val content = turn.answers.joinToString("\n\n") { (it as AgentMessageUi).content }
        assertTrue(content.contains("我先看下文件"))
        assertTrue(content.contains("结论如下"))
    }

    /**
     * 复现用户遇到的情况：一整轮做完后进程被中断，末尾追加一条 interrupted 提示。
     * 中断提示必须独立成条，并且它之前的整轮回答（正文+过程）不能因此消失。
     */
    @Test
    fun interruptedNoticeKeepsPrecedingTurnIntact() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "下载并安装")),
            AgentTimelineEntry.Message(AgentMessageUi("a-1", "APK 在另一个 artifact", isStreaming = false)),
            AgentTimelineEntry.Message(
                ToolActivityMessageUi(
                    id = "t-1",
                    toolName = "terminal_job",
                    status = ToolActivityStatusUi.Running,
                    argumentsSummary = "pm install",
                ),
            ),
            AgentTimelineEntry.Message(
                SystemNoticeMessageUi(
                    id = "interrupted-run-1",
                    code = SystemNoticeCode.Interrupted,
                ),
            ),
            AgentTimelineEntry.Message(UserMessageUi("user-2", "又出问题了")),
        ).mergeTurnTurns()

        // 中断提示独立成条，不能把前面的回答吞掉。
        assertTrue(
            entries.any {
                it is AgentTimelineEntry.Message &&
                    it.message.id == "interrupted-run-1"
            },
        )
        val turn = entries.filterIsInstance<AgentTimelineEntry.Turn>().firstOrNull()
        assertTrue("整轮 Turn 不应消失", turn != null)
        assertTrue(
            "回答正文必须仍在 Turn 内",
            turn!!.answers.any { it is AgentMessageUi && it.id == "a-1" },
        )
        assertTrue(
            "过程步骤必须仍在 Turn 内",
            turn.blocks.filterIsInstance<AgentTurnBlock.Procedure>()
                .any { proc -> proc.steps.any { it.id == "t-1" } },
        )
    }

    /**
     * 回归：工具之间的系统提示曾把一轮切成两段——
     * 「工具 → 重试提示 → 工具」渲染成两个独立过程块加一条裸提示，
     * 用户看到的就是没有气泡的终端消息散落在对话里。
     * 现在提示挂起到本轮收口之后，前后的工具保持在同一个 Turn。
     */
    @Test
    fun noticeBetweenToolsDoesNotSplitTheTurn() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "提交构建")),
            AgentTimelineEntry.Message(
                ToolActivityMessageUi(
                    id = "t-1",
                    toolName = "terminal",
                    status = ToolActivityStatusUi.Success,
                    argumentsSummary = "git push",
                ),
            ),
            AgentTimelineEntry.Message(
                SystemNoticeMessageUi(id = "retry-1", code = SystemNoticeCode.ModelRetry),
            ),
            AgentTimelineEntry.Message(
                ToolActivityMessageUi(
                    id = "t-2",
                    toolName = "terminal",
                    status = ToolActivityStatusUi.Success,
                    argumentsSummary = "gh run watch",
                ),
            ),
            AgentTimelineEntry.Message(AgentMessageUi("a-1", "构建已触发")),
        ).mergeTurnTurns()

        // 前后工具必须在同一个 Turn 里，不能被提示切开。
        val turn = entries.filterIsInstance<AgentTimelineEntry.Turn>().single()
        val toolIds = turn.blocks.filterIsInstance<AgentTurnBlock.Procedure>()
            .flatMap { it.steps }.map { it.id }
        assertEquals(listOf("t-1", "t-2"), toolIds)
        // 提示插在 Turn 之后，仍然独立成条。
        val lastNotice = entries.lastOrNull {
            it is AgentTimelineEntry.Message && it.message.id == "retry-1"
        }
        assertTrue("提示应保留为独立条目", lastNotice != null)
    }

    /**
     * 回归：系统提示曾被并进本轮 Turn，变成一个没有正文的 Content 块，
     * 于是"模型请求重试""任务已中断"这类提示整条从界面消失。
     * 现在它们必须保留为独立条目，交给 SystemNoticeMessageUi 分支渲染本地化文案。
     */
    @Test
    fun systemNoticeStaysItsOwnEntry() {
        val entries = listOf(
            AgentTimelineEntry.Message(UserMessageUi("user-1", "问")),
            AgentTimelineEntry.Message(AgentMessageUi("agent-1", "第一段")),
            AgentTimelineEntry.Message(
                SystemNoticeMessageUi(
                    id = "retry-1",
                    code = SystemNoticeCode.ModelRetry,
                    detail = "网络错误",
                ),
            ),
            AgentTimelineEntry.Message(AgentMessageUi("agent-2", "第二段")),
        ).mergeTurnTurns()

        // 提示自己成条，且不能出现在任何 Turn 的内容块里。
        assertTrue(entries.any { it is AgentTimelineEntry.Message && it.message.id == "retry-1" })
        val turnAnswerIds = entries.filterIsInstance<AgentTimelineEntry.Turn>()
            .flatMap { turn -> turn.answers.map { it.id } }
        assertTrue("retry-1" !in turnAnswerIds)
        assertEquals(listOf("agent-1", "agent-2"), turnAnswerIds)
    }

    /**
     * 回归：Turn 的 key 曾取首个块的 key。流式期间首块会从过程块变成内容块，
     * key 跟着变，LazyColumn 会把同一个气泡当成新条目重新组合。
     * 现在 key 锚在本轮第一条消息上，跨首块变化保持稳定。
     */
    @Test
    fun turnKeyStaysStableWhileFirstBlockChanges() {
        val user = AgentTimelineEntry.Message(UserMessageUi("user-1", "问"))
        val thinking = AgentTimelineEntry.Message(ThinkingMessageUi("think-1", "想", isStreaming = true))
        val answer = AgentTimelineEntry.Message(AgentMessageUi("agent-1", "答", isStreaming = true))

        val onlyWork = listOf(user, thinking).mergeTurnTurns()
        val withAnswer = listOf(user, thinking, answer).mergeTurnTurns()

        val keyBefore = onlyWork.filterIsInstance<AgentTimelineEntry.Turn>().single().key
        val keyAfter = withAnswer.filterIsInstance<AgentTimelineEntry.Turn>().single().key
        assertEquals(keyBefore, keyAfter)
        // key 锚在用户消息上，与首块是思考还是正文无关。
        assertEquals("turn-user-1", keyBefore)
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

/**
 * 回归：过程块夹在两段正文中间时必须能识别出来，好让界面给它补上下留白。
 *
 * 「正文 → 过程 → 正文」中间那段过程收起后横条两端都裸露，前后都是正文，
 * 曾经只有 2dp 底边距，横条几乎直接压在正文上。这里锁定块结构，
 * 保证 sandwiched 判定拿到的仍是 Process 夹在两个 Content 之间。
 */
@Test
fun procedureBetweenContentsIsSandwiched() {
    val longText = "这是一段足够长的正文，长度超过旁白阈值，因此必须独立成块。".repeat(4)
    val entries = listOf(
        AgentTimelineEntry.Message(UserMessageUi("user-1", "列一下文件")),
        AgentTimelineEntry.Message(AgentMessageUi("a-1", longText)),
        AgentTimelineEntry.WorkProcess(
            key = "w-1",
            messages = listOf(
                ToolActivityMessageUi(
                    id = "t-1",
                    toolName = "terminal",
                    status = ToolActivityStatusUi.Success,
                    argumentsSummary = "ls",
                ),
            ),
        ),
        AgentTimelineEntry.Message(AgentMessageUi("a-2", longText)),
    ).mergeTurnTurns()

    val turn = entries.filterIsInstance<AgentTimelineEntry.Turn>().single()
    val kinds = turn.blocks.map { block ->
        when (block) {
            is AgentTurnBlock.Content -> "content"
            is AgentTurnBlock.Procedure -> "procedure"
        }
    }
    // 结构必须是「正文 → 过程 → 正文」，中间的 Process 才需要补间距。
    assertEquals(listOf("content", "procedure", "content"), kinds)
    val procedureIndex = kinds.indexOf("procedure")
    assertTrue(kinds.take(procedureIndex).contains("content"))
    assertTrue(kinds.drop(procedureIndex + 1).contains("content"))
}

/** 测试内的小包装：把消息序列走一遍真实的轮次合并。 */
private fun List<AgentTimelineEntry>.mergeTurnTurns(): List<AgentTimelineEntry> =
    mergeWorkIntoAnswers()
