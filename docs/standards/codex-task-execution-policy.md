# Codex 任务执行路由索引

状态：Active
根规则：`AGENTS.md`

本文只做按需路由，不复制根硬门禁或专项流程。普通代码、文档、审查和解释任务依赖 Codex 自动加载的根 `AGENTS.md`，显式规则读取为 0。

## 任务路由

| 任务 | 唯一权威 |
| --- | --- |
| 中文、授权、脏工作区、安全/数据/生产、破坏性操作、最小验证、Git 授权、零悬空 | `AGENTS.md` |
| 本地 Docker、backend/frontend、health、dev-login、Vite 代理、浏览器可达性 | `.agents/skills/cgc-pms-runtime-refresh/SKILL.md` |
| 七类失败分类、CI、PR、同 HEAD SHA、远端 checks | `.agents/skills/cgc-pms-ci-gate-triage/SKILL.md` |
| 主线、Backlog、计划书、阶段控制、正式验收与收口 | `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md` |
| 普通 commit、推送、PR、合并与分支清理 | `git-publish-and-cleanup` |
| 升版本、版本发布、Tag、GitHub Release 与历史 Release 回填 | `.agents/skills/release-skills/SKILL.md` |
| AutoPilot 触发、Ready 来源和事实入口 | `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md` |
| AutoPilot 调度、checkpoint、fencing、恢复、Reviewer、评分、回顾与金丝雀 | `plugins/cgc-pms-autopilot/references/control-plane-policy.md`、配置和 Schema |

## 按需原则

1. 普通任务不读取本索引，也不读取专项 Skill。
2. 运行态与 CI 各只读取对应 Skill；主线任务读取 mainline Skill，并仅在实际命中运行态、CI、Git 或 AutoPilot 阶段时继续读取对应入口。
3. 非 AutoPilot 任务不得读取 checkpoint、fencing、评分或控制面 references。
4. Skill 不重新读取根规则；根规则由 Codex 自动注入。
5. 同一规则只在表中指定的权威正文维护；其他文件只能引用，不得复制。

## 维护验证

- `scripts/codex-autopilot/test-codex-task-policy-suite.ps1`
- `scripts/codex-autopilot/test-mainline-owner-flow.ps1`
- `scripts/codex-autopilot/test-control-plane-fingerprint.ps1`

行为性控制面变更必须更新指纹覆盖；测试通过不替代用户明确启动的单 Issue 金丝雀。
