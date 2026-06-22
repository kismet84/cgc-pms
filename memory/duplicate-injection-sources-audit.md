---
name: duplicate-injection-sources-audit
description: 2026-06-21 全量审计 — 所有造成重复上下文注入的配置源，量化为字节数
metadata:
  type: feedback
tags: [context-storm, duplication, audit, hook, rules, CLAUDE.md]
---

## 重复注入源 — 全量审计

### 发现汇总

| # | 重复类型 | 涉及文件 | 每次注入大小 | 严重度 |
|---|---------|---------|------------|--------|
| 1 | **OMC 块完全重复** | `~/.claude/CLAUDE.md` ↔ `.claude/CLAUDE.md` | ~3560 字节 | 🔴 高 |
| 2 | **Karpathy 内容重叠** | `CLAUDE.md` ↔ `.claude/rules/karpathy-guidelines.md` | ~2100 字节 | 🟡 中 |
| 3 | **superpowers SessionStart 注入** | 每次启动注入 using-superpowers SKILL.md | 5899 字节 | 🟡 中 |
| 4 | **.omc/project-memory 注入** | SessionStart 注入项目记忆 | 6227 字节 | 🟢 低 |
| 5 | **CLAUDE.md → rules-injector 回注** | OMC 检测文件路径后可能回注 CLAUDE.md 内容 | 不定 | 🟡 中 |
| 6 | **7 个 rules 文件** | PostToolUse 时注入全部匹配规则 | 9126 字节 | 🟡 中 |

---

### 1. OMC 块完全重复（~3560 字节 × 2 = 7KB 浪费）

**根因**: `~/.claude/CLAUDE.md`（全局）和 `.claude/CLAUDE.md`（项目级）包含**完全相同**的 OMC 配置块。

```
~/.claude/CLAUDE.md:   <!-- OMC:START --> ... <!-- OMC:END --> [3561 bytes]
.claude/CLAUDE.md:     <!-- OMC:START --> ... <!-- OMC:END --> [3562 bytes]
                                                         (多一个空行)
```

Claude Code 会同时加载两个文件，OMC 的运维指令在整个上下文中出现两次。

**建议**: `.claude/CLAUDE.md` 只包含 OMC 块，无项目内容。它应从 repo 中删除或只保留项目特有的内容。全局级别的 `~/.claude/CLAUDE.md` 已经提供了相同的 OMC 指令。

### 2. Karpathy 内容重叠（CLAUDE.md 摘要 vs rules 详细版）

`CLAUDE.md` 第 248-253 行:
```markdown
Behavioral guidelines to reduce common LLM coding mistakes:
1. Think Before Coding — State assumptions explicitly...
2. Simplicity First — Minimum code to solve the problem...
3. Surgical Changes — Touch only what you must...
4. Goal-Driven Execution — Define verifiable success criteria...
```

`.claude/rules/karpathy-guidelines.md` (59 行, 2183 字节) — 包含相同的 4 条准则，但有详细展开

两者同时加载，Karpathy 编码准则出现 2 次。

此外，**OMC post-tool-rules-injector** 在检测到文件访问时还会动态注入 `.claude/rules/karpathy-guidelines.md`。

### 3. superpowers SessionStart 注入 (5899 字节)

`hooks/session-start` 每次会话启动时将 `using-superpowers/SKILL.md`（121 行）作为 `additionalContext` 注入。

触发条件: `matcher: "startup"` — 仅会话启动时注入一次，不是每轮。

**这是有意的设计**，但 5899 字节仍然不少。而且内容中包含的大段 "platform adaptation" 工具映射表格与 Claude Code 无关（针对 Codex/Cursor/Copilot 的映射）。

### 4. .omc/project-memory.json (6227 字节)

OMC `project-memory-session.mjs` 在 SessionStart 时读取并注入 `.omc/project-memory.json`（6227 字节）。

**属于 OMC 的项目记忆系统**，内容包含之前会话的总结和指令。如果内容重复或过时，会白白占上下文。

### 5. CLAUDE.md 被 rules-injector 回注

OMC `post-tool-rules-injector.mjs` 扫描 `.claude/rules/`。当文件路径匹配 `.claude/rules` 时，注入对应的规则文件。

但这有一个间接问题：如果你 Read 了 `.claude/rules/` 下的文件 → rules-injector 触发 → 注入更多规则 → 上下文膨胀。

更隐蔽的是，如果你 Read 了 CLAUDE.md → rules-injector 发现 CLAUDE.md 包含 "rules" 相关内容 → 可能触发注入。

### 6. 7 个 rules 文件的注入时机

`.claude/rules/` 下的 7 个文件共计 9126 字节。它们不是一次性全部注入的——OMC rules-injector 采用按需机制（文件路径匹配时注入）。

但实际效果是：在一次典型的开发会话中，Read/Edit/Write 操作频繁触发 PostToolUse → rules-injector 检查 → 如果路径包含 `.claude/rules` 或 `rules` 关键词 → 注入对应规则。

### 未发现的重复

以下**没有**重复问题：
- `.claude/rules/coding-style.md` ↔ CLAUDE.md — **无重叠**（CLAUDE.md 不含 immutable/error-handling 等内容）
- `.claude/rules/testing.md` ↔ CLAUDE.md — **无重叠**（CLAUDE.md 不含 TDD/coverage 等内容）
- karpathy-skills 插件 — **已禁用**，不参与注入
- typescript-lsp 插件 — 无 hooks
- graphify — 被动 skill，不注入

---

## 优化建议（按收益排序）

### 高收益、低风险

1. **删除 `.claude/CLAUDE.md` 中的 OMC 块**
   - 当前 `.claude/CLAUDE.md` 仅含 OMC 块，而 `~/.claude/CLAUDE.md` 已有相同内容
   - 如果 `.claude/CLAUDE.md` 需要保留项目级 OMC 配置覆盖，则反过来：从 `~/.claude/CLAUDE.md` 删除 OMC 块
   - **节省**: ~3560 字节（每次加载去重）

2. **Karpathy 去重：选择一方保留**
   - 选项 A: 从 `CLAUDE.md` 删除 4 行摘要，保留 `.claude/rules/karpathy-guidelines.md`（更详细）
   - 选项 B: 从 `.claude/rules/` 删除 `karpathy-guidelines.md`，保留 CLAUDE.md 中的 4 行摘要
   - **节省**: ~2100 字节或 4 行（取决于选择）

### 中收益、需确认

3. **评估 `.claude/rules/` 中哪些规则真正需要**
   - 当前 7 个 rules 文件，很多是模板默认值（README.md 只是模板说明）
   - 删除未使用的模板文件
   - **节省**: 不定（取决于删除哪些）

4. **检查 `.omc/project-memory.json` 是否有过期内容**
   - 6227 字节的项目记忆每次都注入

### 低优先级

5. **superpowers using-superpowers 注入** — 这是插件核心功能，不建议删除。但可以联系插件维护者减少无关平台的内容
