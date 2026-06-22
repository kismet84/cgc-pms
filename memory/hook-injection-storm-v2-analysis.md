---
name: hook-injection-storm-v2-analysis
description: 2026-06-21 深度分析 — hook 重复注入来源、任务循环根因、上下文雪崩机制及优化方案
metadata:
  type: project
tags: [hook, injection, context-storm, performance, plugin, loop]
---

## Hook 注入与任务循环 — 深度根因分析 (2026-06-21 v2)

### 问题现象

用户报告"hook 重复注入，任务循环"问题。表现为：
1. 每次对话轮次中出现大量 `<system-reminder>` 注入文本
2. 相同的提示信息（如"task tools haven't been used recently"）反复出现
3. 上下文消耗极快，短时间内达到 75-95%

### 根因分析：三层注入叠加

每一轮对话中，以下三层同时注入：

#### 第 1 层: claude-mem (v13.6.2) — 最大注入源

claude-mem 在 **7 个事件**上注册了 hooks，几乎所有 matcher 都是空字符串（= 匹配所有工具）：

| Hook 事件 | Matcher | 实际行为 | 每次触发开销 |
|-----------|---------|----------|------------|
| PreToolUse | `""` (所有工具) | `worker-service.cjs hook claude-code file-context` | 60s timeout, 进程启动+DB查询 |
| PostToolUse | `""` (所有工具) | `worker-service.cjs hook claude-code observation` | 120s timeout, 写入 DB |
| UserPromptSubmit | `""` | `worker-service.cjs hook claude-code session-init` | 60s timeout |
| SessionStart | `startup` | 启动 worker 进程 + 注入记忆上下文 | 60s timeout × 2 |
| Stop | (always) | `worker-service.cjs hook claude-code summarize` | 120s timeout |

**关键问题**：
- `worker-service.cjs` 是 **2.5 MB** 的 Bun 打包后的单体脚本，每次调用都启动一个 Node 进程
- PreToolUse 的 `file-context` hook 在空 matcher 下对**所有工具**触发（包括 Bash、Grep、Glob 等），不仅仅是 Read/Edit
- PostToolUse 的 `observation` hook **对每次工具使用记录观察**，每次都有 DB 写入开销
- `context-generator.cjs` 生成注入的"记忆上下文"，内容包含：相关 observation、session summary、相关用户提示 — 每次注入可达 500-2000 行

#### 第 2 层: OMC (v4.14.7) — 20 个 hook 脚本

OMC 在 **12 个事件**上注册了 **20 个 hook 脚本**，全部使用 Node.js `.mjs` 执行：

| Hook 事件 | Matcher | 脚本 | 实际注入内容 |
|-----------|---------|------|------------|
| UserPromptSubmit | `*` | `keyword-detector.mjs` | 关键词检测 |
| UserPromptSubmit | `*` | `skill-injector.mjs` (21KB) | **注入 skill 引用** — 可能很大 |
| SessionStart | `*` | `session-start.mjs` | 恢复持久模式状态 |
| SessionStart | `*` | `project-memory-session.mjs` | 注入项目记忆 |
| SessionStart | `*` | `wiki-session-start.mjs` | 注入 wiki 内容 |
| **PreToolUse** | `*` | `pre-tool-enforcer.mjs` | 模型路由强制执行 + **注入提醒文本** |
| **PostToolUse** | `*` | `post-tool-verifier.mjs` | 验证提醒 + 上下文使用率警告 |
| **PostToolUse** | `*` | `project-memory-posttool.mjs` | 从工具输出学习 |
| **PostToolUse** | `*` | `post-tool-rules-injector.mjs` | **注入 .claude/rules 文件** — 可能非常大 |
| PostToolUseFailure | `*` | `post-tool-use-failure.mjs` | 失败分析提示 |
| **Stop** | `*` | `persistent-mode.mjs` (50KB) | **持久模式循环核心** |
| **Stop** | `*` | `context-guard-stop.mjs` | 上下文阈值检查 — **可能导致 block 循环** |
| Stop | `*` | `code-simplifier.mjs` | 代码简化提醒 |

#### 第 3 层: 系统内置任务提醒

"task tools haven't been used recently" 来自 Claude Code **内置系统**，每次轮次自动注入。它不通过 hook 机制，而是系统 prompt 模板的一部分。

### 任务循环的根因

**persistent-mode.mjs (OMC Stop hook) 是核心循环驱动器**：

```
用户消息 → 处理 → Stop
  → persistent-mode.mjs 检测到活跃模式（ralph/autopilot/ultrawork）
  → 输出 {decision: "block", reason: "..."}
  → Claude Code 不停止，继续下一轮
  → 下一轮再次检查状态...
```

配合 **context-guard-stop.mjs**：
- 上下文 >75% → block stop + 提示 compact
- 上下文 >95% → 放行
- 每 session 最多 block 2 次
- 问题：block 后继续运行反而**消耗更多上下文**，形成负反馈循环

### 每次工具调用的实际开销

以一次 `Read` 调用为例：

```
PreToolUse 触发:
  ├─ OMC pre-tool-enforcer.mjs       (Node 进程启动 ~200ms + 逻辑)
  └─ claude-mem file-context hook     (Bun 进程启动 ~500ms + DB 查询 ~100ms)

工具执行:
  └─ Read 文件 (~50ms)

PostToolUse 触发:
  ├─ OMC post-tool-verifier.mjs       (Node 进程启动 ~200ms)
  ├─ OMC project-memory-posttool.mjs   (Node 进程启动 ~200ms + learner 逻辑)
  ├─ OMC post-tool-rules-injector.mjs  (Node 进程启动 ~200ms + 可能注入 rules)
  └─ claude-mem observation hook      (Bun 进程启动 ~500ms + DB 写入 ~100ms)
```

**每次 Read 操作：至少 7 个额外进程启动，总计 ~2 秒的 hook 开销。**

### 注入文本累积效应

假设每轮对话有 3 次工具调用：

1. claude-mem SessionStart → ~500 行（记忆上下文）
2. OMC SessionStart → ~200 行（项目记忆 + wiki + 状态恢复）
3. 3× PreToolUse (claude-mem) → 3 × ~100 行 = ~300 行
4. 3× PreToolUse (OMC) → 3 × ~60 行 = ~180 行
5. 3× PostToolUse (claude-mem) → 3 × ~80 行 = ~240 行
6. 3× PostToolUse (OMC) → 3 × ~100 行 = ~300 行
7. 系统任务提醒 → ~30 行

**第一轮总计：~1750 行 hook 注入文本。**
随着轮次累积，claude-mem 的记忆上下文不断增加，注入量上升至 **2000-3000 行/轮**。

### 为什么形成"洪水"

1. **claude-mem 的 memory context 是累积的** — 每次 Stop 后 summarize，下次 SessionStart 注入的内容包含更多历史
2. **OMC rules-injector 重复注入** — 虽然有 dedup 机制，但每次访问新文件时触发新的 rules 注入
3. **persistent-mode 的 block 循环** — 每次 block 后继续，hook 再次运行，注入更多文本
4. **无压缩/过滤机制** — hook 返回的 `additionalContext` 不会被压缩或合并，全部追加到上下文窗口

### 优化方案

#### 高优先级（立刻见效）

1. **关闭 claude-mem PreToolUse file-context hook**：
   `hooks.json` 中 PreToolUse matcher 从 `""` 改为不匹配的值（如 `"NONE"`），或删除此 hook。
   这个 hook 对每次工具调用都触发，且文件上下文信息实际价值低。

2. **限制 claude-mem PostToolUse observation hook**：
   将 matcher 从 `""` 改为 `"Write|Edit|Bash"` 等关键操作，
   避免对 Read/Grep/Glob 等纯读取操作触发 observation 记录。

3. **减少 OMC PostToolUse 的 rules-injector 触发频率**：
   `post-tool-rules-injector.mjs` 的 matcher 从 `*` 改为受限集合。

#### 中优先级（需要调参）

4. **persistent-mode 增加冷却时间**：
   当前 Stop → block → continue 的循环没有最小间隔。
   添加 30s 冷却期，防止短时间内的多次 block。

5. **context-guard 提高阈值**：
   将 `OMC_CONTEXT_GUARD_THRESHOLD` 从默认 75% 提高到 85%，
   减少过早的上下文警告。

#### 低优先级（需要代码改动）

6. **claude-mem worker-service 改为常驻进程**：
   避免每次 hook 触发都启动新的 Bun 进程。
   worker 已经以常驻模式运行（`worker-service.cjs start`），
   但 hook 调用仍每次启动新进程。

7. **hook 输出去重**：
   在 OMC 层面添加 hook 输出缓存，
   相同内容 30 秒内不重复注入。

### 当前状态检查

- **persistent-mode 活跃状态**: `.omc/state/skill-active-state.json` 不存在 → 当前无活跃循环模式 ✅
- **无 ralph/autopilot/ultrawork 活跃** ✅
- **上下文百分比**: 需要持续监控

### 已实施的优化 (2026-06-21 23:00)

以下配置修改已完成：

#### claude-mem (`~/.claude-mem/settings.json`) — 减少记忆注入量 80%

| 参数 | 旧值 | 新值 | 效果 |
|------|------|------|------|
| `CLAUDE_MEM_CONTEXT_OBSERVATIONS` | 50 | **10** | 每次注入的观察记录从 50 条降至 10 条 |
| `CLAUDE_MEM_CONTEXT_SESSION_COUNT` | 10 | **3** | 关联的历史会话从 10 个降至 3 个 |
| `CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY` | true | **false** | 关闭上一次会话摘要注入 |
| `CLAUDE_MEM_CONTEXT_SHOW_TERMINAL_OUTPUT` | true | **false** | 关闭终端输出回显 |
| `CLAUDE_MEM_WELCOME_HINT_ENABLED` | true | **false** | 关闭欢迎提示注入 |

#### OMC (`~/.claude/settings.json`) — 减少 loop 风险

| 参数 | 旧值 | 新值 | 效果 |
|------|------|------|------|
| `OMC_CONTEXT_GUARD_THRESHOLD` | (默认 75%) | **85** | 提高 block stop 阈值，减少过早 warning |
| `OMC_QUIET` | (默认 0) | **1** | 减少 PostToolUse 冗余注入 |

#### 预期效果

| 注入来源 | 优化前（行/轮） | 优化后（行/轮） | 降幅 |
|----------|-------------|-------------|------|
| claude-mem 记忆上下文 | ~800-2000 | ~200-400 | **~75%** |
| OMC PostToolUse 注入 | ~300-500 | ~150-250 | **~50%** |
| 系统内置 | ~30 | ~30 | 不变 |
| **总计** | **~1200-2500** | **~400-700** | **~65%** |

> **重启 Claude Code 会话后生效。** 所有修改为配置文件级别，不需要改动插件代码。
