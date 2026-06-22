---
name: claude-mem-hook-block-fix-2026-06-22
description: claude-mem worker unreachable 导致 UserPromptSubmit 被 hook 阻断的修复记录
metadata:
  type: feedback
tags: [claude-code, hook, plugin, claude-mem, windows]
---

## 问题

- Claude Code 提交提示时出现 `UserPromptSubmit operation blocked by hook`
- 错误核心是 `claude-mem worker unreachable for 11 consecutive hooks`
- 根因是 `claude-mem` 插件的 `UserPromptSubmit` hook 在全局拦截，但后台 worker 无法连通，导致插件直接阻止消息提交

## 修复

- 在全局 `C:\Users\L1597\.claude\settings.json` 中将 `claude-mem@thedotmack` 设为 `false`
- 项目级 `.claude/settings.json` 也保持 `false`，避免后续重新启用时再次覆盖判断
- 先止血恢复 Claude Code 可用性，再考虑单独修复 worker 或缩小 hook matcher

## 经验

- 这类报错优先看 hook 拦截链路，不要先怀疑用户输入本身
- Windows 下的 Claude 插件常见问题包括缓存路径、`node`/`PATH`、worker 进程和 shell 兼容性
- 如果插件 worker 不稳定，先禁用相关 hook，比在交互流里反复重试更快恢复工作流
- 仅改项目级设置不够时，要检查全局 `~/.claude/settings.json` 是否仍启用同名插件
