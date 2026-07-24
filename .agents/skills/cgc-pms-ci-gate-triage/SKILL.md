---
name: cgc-pms-ci-gate-triage
description: 用于 cgc-pms 的统一失败分类、GitHub Actions、PR 与 CI 门禁排障。用户要求排查 CI 红灯、checks、构建/测试失败、PR 门禁或需要判断失败归因时使用。
---

# cgc-pms CI、PR 与失败分类

根规则由 Codex 自动加载。本 Skill 是项目失败分类、CI 与 PR 契约的唯一权威正文。

## 统一失败分类

所有新结论和新写入只使用以下七类：

| 分类 | 适用证据 | 处理 |
| --- | --- | --- |
| `tool_config` | 工具未加载、索引/凭据/规则/入口缺失、版本不兼容 | 修复配置或前置，不判业务失败 |
| `tool_invocation` | schema、参数、转义、命令调用格式错误 | 修正调用后做一次最小复验 |
| `environment_prerequisite` | Docker、端口、数据库、服务、代理、等待时间或测试数据未就绪 | 恢复环境后复验 |
| `ready_issue_config` | Ready 范围、验证选择器、命令或报告路径失真 | 最小修正 Ready 契约 |
| `retrieval_gap` | 图谱召回或索引覆盖不足 | 使用允许的备用检索，不作不存在断言 |
| `quality_or_security` | 可复现的代码、测试、构建、契约、权限、安全或数据一致性失败 | 实施整改或阻塞裁决 |
| `unknown` | 证据不足或冲突 | 补证据，禁止强行归因 |

先分类，再决定重试、修复或阻塞；相同前置和参数下禁止原样重试。历史旧值只读兼容，不得继续写入。

## CI 分诊

1. 收集 workflow、job、step、分支、HEAD SHA、失败关键词和本地/远端差异。
2. GitHub Actions 只有 GitHub 服务、网络或 Runner 基础设施故障可归 `environment_prerequisite`。workflow 内 Docker、数据库、端口、测试数据配置，以及代码/测试/迁移/基线不同步，都不是外部环境故障。
3. 代码、测试、迁移或基线不同步导致 CI 失败归 `quality_or_security/DELIVERY_GATE_OMISSION`；后续修绿不得改写 PR 首次 CI 结果。
4. 最小顺序：分类 → 修配置/调用/环境 → 一次最小等价复验 → 仍失败才整改代码或阻塞。
5. 轮询采用退避；状态未变化保持静默，只在状态变化、超时、确定失败或需用户决策时回报。
6. 远端日志因 EOF、Schannel 或超时不可得时，不切 Git SSH、不无界下载。优先定位失败 step；支持 `Accept-Ranges` 时读取末段，默认 `256 KB`，缺最终摘要只扩大一次。临时签名 URL 不写长期文件。

## 首次非 Draft PR 门禁

1. 功能分支最终提交先 push，并在 `event=push`、`headSha=git rev-parse HEAD` 的同一 SHA 上取得完整成功 CI；任何新提交使旧证据失效。
2. 必须覆盖：后端全量与顺序复验、MySQL 最小权限迁移、前端 lint/test/type-check/build、安全扫描、V2 门禁、E2E 与 `build-summary`。
3. 运行 `scripts/codex-autopilot/verify-pre-pr-ci.ps1` 绑定分支、SHA、tracked 工作区和全部 job。缺任一证据时禁止创建/转为非 Draft PR，也禁止声明“可提 PR”。
4. PR 创建后的首次 CI 独立计入 `PR 首次 CI 通过率`；本地成功或后续重跑转绿不能追溯改写。
5. 默认分支合并后只运行轻量 post-merge 证据核验；无法证明来自合格已合并 PR 时 fail-close，并通过 `workflow_dispatch` 补跑完整 CI。

## 最小回报

```text
失败任务/步骤=
失败分类=
关键证据=
当前处理与复验=
是否阻塞=
首次 PR CI 结论=
```
