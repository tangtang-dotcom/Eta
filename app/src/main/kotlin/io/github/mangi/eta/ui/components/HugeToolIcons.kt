package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Chart01
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.Contact01
import me.rerere.hugeicons.stroke.Cursor01
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FolderSearch
import me.rerere.hugeicons.stroke.Globe
import me.rerere.hugeicons.stroke.HandPointingLeft01
import me.rerere.hugeicons.stroke.HeartCheck
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Keyboard
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Location01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.MouseScroll01
import me.rerere.hugeicons.stroke.MusicNote01
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings01
import me.rerere.hugeicons.stroke.Smartphone
import me.rerere.hugeicons.stroke.SourceCode
import me.rerere.hugeicons.stroke.SwipeDown01
import me.rerere.hugeicons.stroke.Terminal
import me.rerere.hugeicons.stroke.Tools

/**
 * 过程步骤图标：与 rikkahub 使用同一套 HugeIcons。
 *
 * 只有「工作过程」这一块使用 HugeIcons——为了让它与参照物完全一致；应用其他位置的
 * 图标仍走原来的 Material Icons，避免一次性替换所有图标带来的回归面。
 *
 * 工具到图标的语义映射沿用 Eta 自己的划分（终端、浏览器、相册、日历各走各的图形）。
 * rikkahub 那边工具少，一律用 [HugeIcons.Tools]；保留语义化能让用户一眼看出这一步
 * 在操作什么，同时图形语言与参照物统一。
 */
internal fun hugeIconForTool(toolId: String): ImageVector = when (toolId) {
    // ── 屏幕观察与手势 ────────────────────────────────────────────────
    "observe", "observe_screen", "browser_screenshot" -> HugeIcons.Camera01
    "click", "tap", "tap_element", "browser_interact" -> HugeIcons.Cursor01
    "tap_area", "get_current_location", "search_saved_places" -> HugeIcons.Location01
    "long_press", "long_press_element" -> HugeIcons.HandPointingLeft01
    "swipe" -> HugeIcons.SwipeDown01
    "scroll", "scroll_element" -> HugeIcons.MouseScroll01

    // ── 文本输入与剪贴板 ──────────────────────────────────────────────
    "type_text", "input_text", "replace_text", "clear_text", "press_key" -> HugeIcons.Keyboard
    "clipboard", "paste_text", "get_clipboard", "set_clipboard" -> HugeIcons.Clipboard

    // ── 应用与系统 ────────────────────────────────────────────────────
    "search_apps" -> HugeIcons.Search01
    "get_current_context", "device_status", "set_device_state", "get_device_environment",
    "release_gui", "open_system_panel", "open_app", "launch_app",
    -> HugeIcons.Smartphone
    "get_setting", "set_setting", "inspect_app", "app_state_control" -> HugeIcons.Settings01
    "open_uri", "fetch_url" -> HugeIcons.Link01

    // ── 网页与浏览 ────────────────────────────────────────────────────
    "browser_use", "网页浏览", "browser_read" -> HugeIcons.Globe
    "web_search", "web_search_call", "网页搜索", "network_info", "wifi_credentials" -> HugeIcons.Earth

    // ── 文件与终端 ────────────────────────────────────────────────────
    "read_file", "write_file", "edit_file", "stat_file", "list_directory",
    "search_files", "search_downloads",
    -> HugeIcons.File01
    "glob_files", "grep_files", "file_search", "file_search_call", "文件搜索" -> HugeIcons.FolderSearch
    "terminal", "run_command", "code_interpreter", "code_interpreter_call", "代码执行" ->
        HugeIcons.Terminal
    "terminal_job", "terminal_daemon", "get_logcat" -> HugeIcons.SourceCode

    // ── 记忆与扩展 ────────────────────────────────────────────────────
    "memory_get", "memory_write", "character_memory_get", "character_memory_write" -> HugeIcons.Brain
    "skills_list", "skills_read", "skills_read_resource", "skills_list_curated",
    "skills_inspect_github", "skills_install_from_github", "mcp_call", "MCP 工具",
    -> HugeIcons.Tools

    // ── 个人上下文 ────────────────────────────────────────────────────
    "search_calendar_events", "set_alarm", "set_timer", "list_alarms", "list_active_timers" ->
        HugeIcons.Calendar03
    "search_contacts" -> HugeIcons.Contact01
    "search_messages" -> HugeIcons.Message01
    "search_media", "search_qq_chat_images", "search_wechat_chat_images", "read_image",
    "image_generation", "image_generation_call", "图像生成",
    -> HugeIcons.Image01
    "search_audio", "media_control", "set_volume" -> HugeIcons.MusicNote01
    "search_recordings", "search_coloros_recordings", "search_recording_summaries" -> HugeIcons.Play
    "recent_notifications", "search_notification_history" -> HugeIcons.Notification01
    "read_sms_code" -> HugeIcons.Key01
    "get_health_summary" -> HugeIcons.HeartCheck
    "recent_app_activity", "app_usage_summary", "top_memory_apps", "top_storage_apps" ->
        HugeIcons.Chart01

    // ── 其余沿用通用工具图标，与 rikkahub 的兜底一致 ──────────────────
    else -> HugeIcons.Tools
}
