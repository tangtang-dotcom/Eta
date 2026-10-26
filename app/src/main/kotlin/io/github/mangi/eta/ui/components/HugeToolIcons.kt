package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Rocket01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Time02
import me.rerere.hugeicons.stroke.Tools

/**
 * 过程步骤图标：按步骤语义区分。
 *
 * 对齐 RikkaHub Agent Pure 的 capsule 图标方案（ToolCallPresentation 的
 * capsuleToolIcon）：思考是灯泡、工具按语义分族，未登记的工具落兜底图标。
 *
 * 与参照物的差别只在工具名：RikkaHub 那套名字是它自己的（workspace_shell、
 * search_web 等），Eta 的工具栈完全不同（terminal、read_file、tap 等），
 * 因此按语义等价重新映射，而不是照抄字符串表。
 */
internal object WorkIcons {
    /** 思考步骤：灯泡。 */
    val Reasoning: ImageVector = HugeIcons.Idea01

    /**
     * 过程旁白：对话气泡。
     *
     * 模型在两次工具调用之间说的那一句，是它说的话而不是它做的一次动作，
     * 因此不套工具或思考图标，改用 RikkaHub ChatMessageNarrationStep 同一个
     * Message01，读起来像「插话」而不是「步骤」。
     */
    val Narration: ImageVector = HugeIcons.Message01

    /** 未登记的工具：参照 RikkaHub 的兜底图标。 */
    val Fallback: ImageVector = HugeIcons.Rocket01

    /** 工具摘要节点。 */
    val Tool: ImageVector = HugeIcons.Tools
}

/**
 * 按工具名返回过程步骤图标。
 *
 * 分组依据与 RikkaHub 一致：同类工具共用一个图标，让时间线上「读文件」「跑命令」
 * 「搜网页」一眼可辨，而不是所有工具都顶同一个扳手。
 *
 * 映射按 Eta 的实际工具名整理：
 * - 终端族：terminal / terminal_job / terminal_daemon / run_command
 * - 文件族：读、写、改、列目录、glob / grep / stat
 * - 网络族：web_search / fetch_url / browser_use
 * - 手机操控族：屏幕读取、点击、滑动、输入、按键、启动应用
 * - 记忆与设置族：memory_* / set_* / get_* 等
 */
internal fun workIconForTool(toolName: String): ImageVector = when (toolName) {
    // 终端
    "terminal", "terminal_job", "terminal_daemon", "run_command" ->
        HugeIcons.ComputerTerminal01

    // 文件读取
    "read_file", "list_directory", "glob_files", "grep_files", "stat_file", "get_logcat" ->
        HugeIcons.FileView

    // 文件写入与修改
    "write_file" -> HugeIcons.FileAdd
    "edit_file" -> HugeIcons.FileEdit

    // 网络与浏览
    "web_search" -> HugeIcons.GlobalSearch
    "fetch_url", "browser_use", "open_uri" -> HugeIcons.Internet

    // 记忆
    "memory_get", "memory_write" -> HugeIcons.Eraser

    // 剪贴板与输入
    "set_clipboard", "get_clipboard", "paste_text" -> HugeIcons.Clipboard

    // 时间与闹钟
    "set_alarm", "set_timer", "get_current_context" -> HugeIcons.Time02

    // 手机与系统设置
    "observe_screen", "tap", "tap_element", "tap_area", "long_press",
    "long_press_element", "swipe", "scroll", "scroll_element", "press_key",
    "release_gui", "type_text", "input_text", "replace_text", "clear_text",
    "wait", "wait_for_text", "wait_for_package", "launch_app", "search_apps",
    "open_system_panel", "inspect_app", "app_state_control", "media_control",
    "get_device_state", "device_status", "recent_notifications",
    -> HugeIcons.SmartPhone01

    // 设备设置与网络配置
    "set_device_state", "set_setting", "get_setting", "set_volume",
    "set_brightness", "set_screen_timeout", "set_sound_mode",
    "set_do_not_disturb", "get_hotspot", "set_hotspot", "network_info",
    "wifi_credentials", "get_flashlight", "set_flashlight",
    "get_device_environment", "get_display_state", "get_sound_state",
    -> HugeIcons.Settings03

    // 短信与通知
    "read_sms_code", "search_messages" -> HugeIcons.Message02

    // 技能与上下文压缩
    "use_skill", "run_js", "context_compaction" -> HugeIcons.AiMagic

    else -> WorkIcons.Fallback
}
