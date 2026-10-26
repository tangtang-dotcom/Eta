package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi

/** 工作过程卡片内的一个时间线节点：单条消息，或连续调用同一工具形成的分组。 */
@Immutable
internal sealed interface AgentWorkStep {
    val key: String

    data class Single(val message: AgentChatMessageUi) : AgentWorkStep {
        override val key: String = message.id
    }

    data class ToolGroup(
        val toolName: String,
        val calls: List<ToolActivityMessageUi>,
    ) : AgentWorkStep {
        override val key: String = "group-${calls.first().id}"
        val running: Boolean get() = calls.any { it.status == ToolActivityStatusUi.Running }
        val failedCount: Int get() = calls.count { it.status == ToolActivityStatusUi.Failed }
    }
}

/**
 * Agent 常连续读取多个文件或多次观察屏幕；同名工具连续出现时合并为一个节点，
 * 时间线只保留“做了什么”的层级，细节按需展开。浏览器条目携带实时预览，保持独立。
 */
internal fun List<AgentChatMessageUi>.toWorkSteps(): List<AgentWorkStep> = buildList {
    val run = mutableListOf<ToolActivityMessageUi>()

    fun flushRun() {
        when (run.size) {
            0 -> Unit
            1 -> add(AgentWorkStep.Single(run.single()))
            else -> add(
                AgentWorkStep.ToolGroup(
                    toolName = groupDisplayToolName(run.first().toolName),
                    calls = run.toList(),
                ),
            )
        }
        run.clear()
    }

    this@toWorkSteps.forEach { message ->
        val family = (message as? ToolActivityMessageUi)?.let { toolFamily(it.toolName) }
        if (family != null && run.isNotEmpty() && toolFamily(run.first().toolName) != family) {
            flushRun()
        }
        if (family != null) {
            run += message
        } else {
            flushRun()
            add(AgentWorkStep.Single(message))
        }
    }
    flushRun()
}

/**
 * 分组用的“工具族”：同步终端、后台任务和常驻服务其实是同一件事的不同入口，
 * 连续出现时合并成一个节点，标题统一显示终端。返回 null 表示不参与合并。
 */
private fun toolFamily(toolName: String): String? = when (toolName) {
    in TERMINAL_TOOLS -> TERMINAL_FAMILY
    "browser_use" -> null
    else -> toolName
}

private const val TERMINAL_FAMILY = "terminal"

private val TERMINAL_TOOLS = setOf("terminal", "terminal_job", "terminal_daemon")

/** 分组标题用的工具名：终端族统一显示为终端。 */
internal fun groupDisplayToolName(toolName: String): String = toolFamily(toolName) ?: toolName

/** 折叠后的工作过程摘要所需计数。 */
internal data class AgentWorkSummary(
    val thinkingCount: Int,
    val toolCount: Int,
    val failedCount: Int,
)

internal fun List<AgentChatMessageUi>.workSummary(): AgentWorkSummary = AgentWorkSummary(
    thinkingCount = count { it is ThinkingMessageUi },
    toolCount = count { it is ToolActivityMessageUi },
    failedCount = count { it is ToolActivityMessageUi && it.status == ToolActivityStatusUi.Failed },
)

/**
 * 合并后的步骤数：连续调用同一工具只算一步。
 * 折叠行要让用户一眼看出“这是一条过程”，报的是条数而不是原始调用次数。
 */
internal fun List<AgentChatMessageUi>.mergedWorkStepCount(): Int = toWorkSteps().size

/**
 * 折叠行下方的一行结果。成功的常态结果（如“完成”）不占行；终端输出取最后一行，
 * 因为命令的结论通常在末尾；失败去掉重复的“失败”前缀与日志用的 code= 尾巴。
 */
internal fun ToolActivityMessageUi.outcomeLine(): String? {
    val summary = resultSummary?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lines = summary.lines().map(String::trim).filter(String::isNotEmpty)
    val head = lines.firstOrNull() ?: return null
    return when (status) {
        ToolActivityStatusUi.Running -> null
        ToolActivityStatusUi.Failed, ToolActivityStatusUi.Unknown -> {
            val detail = lines.drop(1).lastOrNull { it != TRUNCATION_MARK }
            head.removePrefix(FAILURE_PREFIX)
                .substringBefore(FAILURE_CODE_SEPARATOR)
                .takeIf { it.isNotBlank() && it != FAILURE_LABEL }
                ?: detail
        }
        ToolActivityStatusUi.Success -> {
            val output = lines.drop(1).lastOrNull { it != TRUNCATION_MARK }
            when {
                output != null -> output
                head in TRIVIAL_SUCCESS -> null
                else -> head.removePrefix(SUCCESS_PREFIX).takeIf { it.isNotBlank() }
            }
        }
    }
}

// 与 Runtime 侧结果摘要的固定格式对应。
private const val FAILURE_LABEL = "失败"
private const val FAILURE_PREFIX = "失败 · "
private const val FAILURE_CODE_SEPARATOR = " · code="
private const val SUCCESS_PREFIX = "完成 · "
private const val TRUNCATION_MARK = "…"
private val TRIVIAL_SUCCESS = setOf("完成", "执行完成")

/**
 * 过程步骤的标题：动词 + 参数（如「搜索 · token 私有仓库」「读取 · /tmp/a.log」）。
 *
 * 对齐 RikkaHub 的 capsule 标题写法（Searched X / Read X / Fetched X）：
 * 标题先说这一步「做了什么」，再用参数点明「对什么做」，
 * 比单纯一个工具名更容易扫读。参数过长时截断，避免把整行撑开。
 *
 * 参数为空时退回不带参数的动词短语；工具不属于这几类时退回原来的工具名标题。
 */
@Composable
internal fun toolStepTitle(toolName: String, argumentsSummary: String): String {
    val summary = argumentsSummary.trim()
    // 命令类工具的摘要本身就已经是「执行命令 · Linux」这种完整短语，
    // 再套一次动词就会变成「执行 执行命令 · Linux」——同一个意思说两遍，
    // 标题被撑长一大截，这正是终端行显得拥挤的主因。
    // 这类摘要直接原样用作标题，不再加前缀。
    val alreadyDescriptive = toolName in COMMAND_TOOLS && summary.isNotEmpty()
    if (alreadyDescriptive) return shortenToolArgument(summary)

    val detail = summary.takeIf { it.isNotEmpty() }?.let(::shortenToolArgument)
    return when (toolName) {
        "web_search" -> detail
            ?.let { stringResource(R.string.tool_step_search, it) }
            ?: stringResource(R.string.tool_step_search_empty)
        "fetch_url" -> detail
            ?.let { stringResource(R.string.tool_step_fetch, it) }
            ?: stringResource(R.string.tool_step_fetch_empty)
        "browser_use", "open_uri" -> detail
            ?.let { stringResource(R.string.tool_step_read, it) }
            ?: stringResource(R.string.tool_step_read_empty)
        "read_file", "list_directory", "stat_file", "grep_files", "glob_files" -> detail
            ?.let { stringResource(R.string.tool_step_read, it) }
            ?: stringResource(R.string.tool_step_read_empty)
        in COMMAND_TOOLS -> stringResource(R.string.tool_step_run_empty)
        else -> toolDisplayName(toolName)
    }
}

/** 命令类工具：摘要本身就是一句完整描述，不再叠加动词前缀。 */
private val COMMAND_TOOLS = setOf("terminal", "terminal_job", "terminal_daemon", "run_command")

/**
 * 参数摘要的显示长度上限。
 *
 * 中文按字宽算，超过这个长度在步骤行里会被截断并补省略号：一行只放得下
 * 一个简短的对象名（文件名、查询词），完整参数留给展开后的详情。
 */
private const val ToolArgumentMaxLength = 24

private fun shortenToolArgument(value: String): String =
    if (value.length <= ToolArgumentMaxLength) value
    else value.take(ToolArgumentMaxLength - 1) + "…"
