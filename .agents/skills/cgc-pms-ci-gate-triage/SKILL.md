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

### PowerShell 与 ripgrep 调用

1. PowerShell 中禁止使用 Bash/C 风格的反斜杠转义双引号；`\"` 不会转义 PowerShell 双引号。包含双引号的检索表达式必须使用 PowerShell 单引号字面量。
2. 精确文本检索使用 `rg -F`，每个目标通过独立 `-e 'literal'` 传入；只有确需正则语义时才使用正则，不把多个含引号目标拼成双引号包裹的 alternation。
3. 构建/测试与证据检索分开执行，避免后置检索的退出码覆盖已成功门禁；需要顺序短路时显式检查 `$LASTEXITCODE`。
4. `regex parse error`、`Unexpected token`、`string is missing the terminator` 归类 `tool_invocation`。保留此前已成功步骤的客观证据，改用单引号或 `rg -F -e` 后只做一次最小复验，同时核对退出码与命中结果。

## CI 分诊

1. 收集 workflow、job、step、分支、HEAD SHA、失败关键词和本地/远端差异。
2. GitHub Actions 只有 GitHub 服务、网络或 Runner 基础设施故障可归 `environment_prerequisite`。workflow 内 Docker、数据库、端口、测试数据配置，以及代码/测试/迁移/基线不同步，都不是外部环境故障。
3. 代码、测试、迁移或基线不同步导致 CI 失败归 `quality_or_security/DELIVERY_GATE_OMISSION`；后续修绿不得改写 PR 首次 CI 结果。
4. 最小顺序：分类 → 修配置/调用/环境 → 一次最小等价复验 → 仍失败才整改代码或阻塞。
5. 后端全量测试使用下述长时监控规则；其他等待同样禁止把内部轮询转换成无变化用户心跳。
6. 远端日志因 EOF、Schannel 或超时不可得时，不切 Git SSH、不无界下载。优先定位失败 step；支持 `Accept-Ranges` 时读取末段，默认 `256 KB`，缺最终摘要只扩大一次。临时签名 URL 不写长期文件。

### 后端全量测试长时监控

1. 适用于本地后端全量 Maven 验证及 GitHub Actions `backend-test` 的 `Run backend tests with coverage` 步骤。
2. 每次启动前读取最近 10 次成功 GitHub Actions `backend-test` 中该步骤的 `startedAt`、`completedAt`，按步骤实际耗时更新平均值、中位数和范围；排除失败、取消、跳过、超时、缺时间及非正耗时记录。查询暂不可用时使用最近已确认快照，不猜测。
3. 当前已确认快照：平均 `344.8` 秒（约 `5分45秒`），中位数 `5分52秒`，范围 `4分19秒～6分15秒`。
4. 启动时只播报一次预计耗时。达到平均时长前，状态未变化必须保持用户可见静默；禁止固定 60 秒心跳，禁止重复发送“仍在执行”“未见失败”等无新证据消息。内部轮询可以继续。
5. 超过平均时长仍未结束时，检查 Maven/Java 子进程、CPU、最新 Surefire 报告时间和终端新增输出；只有取得状态变化或新证据才播报。
6. 动态异常阈值为 `max(平均时长 × 1.5, 最近最大值 + 120秒)`；当前约 `8分37秒`。超过阈值进入疑似卡住检查，先分类为 `unknown`，取得线程、进程或测试报告证据后再归因，不因单纯变慢判定失败。
7. 终态必须同时读取 Maven `BUILD SUCCESS`/`BUILD FAILURE` 和 Surefire tests/failures/errors/skipped 汇总；不能只凭进程退出码裁决。
8. 用户可见更新仅限：启动、阶段变化、明确失败、超过动态异常阈值、最终完成。
9. 监控只读；不得因等待时间触发重跑、取消、修改 CI 或终止 Maven。

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
