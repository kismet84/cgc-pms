# 第42-1条主线 AutoPilot 控制面一致性验收报告

## 结论

- 自动化实现验收：通过。
- 真实 AutoPilot 金丝雀：未执行；遵守用户“不要使用自动迭代系统”的约束。
- N>1/无界放量：不通过，阻塞；必须由用户另行明确启动一次新指纹 `启动迭代-1` 并完成全部读回门禁。
- 业务代码失败：无证据；本主线只修改本地 AutoPilot 控制面、测试、规则和治理文档。

## 正式交付物

- 单一 `DRY_RUN / EXPLAIN / APPLY` 执行模式与派发权能断言。
- PowerShell 7 `pwsh` 固定宿主门禁；缺失时输出 `tool_config/AUTOPILOT_POWERSHELL7_REQUIRED`，不回退 Windows PowerShell 5.1。
- 独立采集 stdout、stderr、exitCode、timeout 的原生命令封装；Git warning 不再等同失败。
- 原子 `run.lock` 获取/接管与 `runInstanceId + leaseEpoch + controlPlaneFingerprint` fencing。
- state、Issue checkpoint 和 child result 的代次元数据、原子写入、结果路径前置绑定与恢复优先级。
- 基于工作区、checkpoint、result、evidence 的语义进度；CPU、PID、心跳和无产物 MCP 活动不计进度。
- 同一 `failureFingerprint + phase + diffHash` 一次自动恢复预算。
- 普通临时 Git fixture 的本地换行隔离、独立 CRLF warning 专项 fixture，以及统一 LF 的 PowerShell 测试源码。
- v2 效率证据中的业务阶段耗时与控制面返工耗时拆分；权重继续为35/25/20/10/10。

## 关键验收证据

| 验收项 | 结果 |
| --- | --- |
| PowerShell 7 宿主 | 本机 `pwsh 7.6.3`；控制面、插件入口和测试统一使用 `pwsh` |
| 模式矩阵 | `test-execution-mode-matrix.ps1` 通过 |
| Git warning / 退出码语义 | `test-native-command-semantics.ps1` 通过；专项 fixture 捕获 warning 且 exitCode=0 仍成功 |
| 原子锁与 stale writer | `test-run-lock-fencing.ps1` 通过 |
| 控制面指纹与宿主 | `test-control-plane.ps1` 通过 |
| 状态迁移 | `test-state-machine.ps1` 通过 |
| child result / 阶段恢复 / 一次恢复预算 | `test-phase-recovery.ps1` 通过 |
| Reviewer 与合法 repair | `test-review-repair.ps1` 通过 |
| closeout Git 裁决 | `test-closeout.ps1` 通过 |
| stall 语义与退役 | `test-executor-stall.ps1` 通过 |
| 连续 Runner 全矩阵 | `test-continuous-runner.ps1` 通过 |
| v2 效率与耗时拆分 | `test-task-scoring.ps1` 通过 |
| JSON Schema 基础解析 | task-score、Issue checkpoint、loop state 三份 schema 通过 `ConvertFrom-Json` |
| 运行态忽略门 | `git check-ignore -v .codex-autopilot/checkpoints/example.json` 命中 `.gitignore` |
| 差异完整性 | `git diff --check` 通过 |

普通 fixture 不再输出 CRLF warning；`test-phase-recovery.ps1` 中的 warning 是显式设置 `core.autocrlf=true` 并写入 LF 的专项故障注入，属于预期诊断证据。

## 失败分类与修正

1. 首轮连续 Runner 复验因空 `ControlPlaneFingerprint` 参数导致 pwsh 参数缺值，分类为 `tool_config / 命令构造`；改为非空时才传参后通过。
2. PowerShell 7 会把 ISO 日期反序列化为日期对象，旧测试要求原字符串保持不变；启动脚本增加确定性时间字符串转换，独立启动测试与连续 Runner 均通过。
3. 旧 mock Executor 未生成正式 F 报告，分类为测试夹具契约缺失；夹具现生成包含后续项三项统计的正式报告。
4. CRLF warning 根因确认是系统 Git 配置、临时仓库缺少换行规则与 LF fixture 共同作用；普通 fixture 已隔离，专项 fixture 保留稳定复现。

## 风险与放量门

- 当前唯一阻塞不是已知代码失败，而是尚未执行真实新指纹单任务金丝雀。
- 金丝雀必须在控制面不再中途热修的前提下完成，并读回不同的 `implementationCommit` / `closeoutCommit`、closeout ledger、最终 state、知识图谱 Git cursor 和金丝雀指纹。
- 金丝雀完成前，N>1 与无界模式必须继续安全拒绝；不得用夹具通过替代真实放量证据。
- 本轮未提交、未 push、未发布生产。

## 后续项统计

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 计划内既有门禁“用户启动真实 `启动迭代-1`”继续由 `docs/backlog/current-focus.md` 唯一承接，不属于本轮新增悬空问题。
