---
name: statusline-config-fix-2026-06-21
description: 固定本项目 Claude Code 状态栏配置为 ccstatusline，避免 claude-hud/claude-hub 覆盖
tags:
  - claude-code
  - statusline
  - config
  - pitfall
metadata:
  type: feedback
---

本项目 `.claude/settings.json` 曾用 `claude-hud` 插件脚本作为 `statusLine.command`，用户要求“固定 claude-hub 状态栏配置”后确认实际目标是“改回 ccstatusline”。修复方式是将项目级 `statusLine.command` 固定为 `ccstatusline`，保留 `padding: 0` 和 `refreshInterval: 10`，并在项目级 `enabledPlugins` 中设置 `claude-hud@claude-hud: false`，避免项目配置继续覆盖全局状态栏。

验证经验：
- 用 Node 解析 `.claude/settings.json` 并断言 `statusLine.type === "command"`、`statusLine.command === "ccstatusline"`、`padding === 0`、`refreshInterval === 10`、`claude-hud@claude-hud === false`。
- 用 `Get-Command ccstatusline` 确认命令存在。
- `ccstatusline` 直接执行可能等待 stdin；冒烟测试应传入示例 JSON：`'{"workspace":{"current_dir":"D:\\\\projects-test\\\\cgc-pms"},"model":{"display_name":"Opus 4.8"},"cost":{"total_cost_usd":0},"session_id":"verify"}' | ccstatusline`。

**Why:** 项目级 `.claude/settings.json` 的 `statusLine` 会覆盖全局配置；只改全局 `~/.claude/settings.json` 不能解决本项目被 claude-hud/claude-hub 覆盖的问题。

**How to apply:** 遇到 Claude Code 状态栏显示不符合预期时，先同时检查全局 `C:\Users\L1597\.claude\settings.json` 和项目 `.claude/settings.json`；如果要本项目固定回 `ccstatusline`，优先修改项目级 `statusLine`，并禁用会接管状态栏的项目级插件配置。注意 `Edit` 匹配复杂 shell 命令容易因转义/隐藏差异失败，已读完整 JSON 后可用 `Write` 全量重写小型 settings 文件。

相关：[[hook-injection-storm-v2-analysis]] [[duplicate-injection-sources-audit]]
