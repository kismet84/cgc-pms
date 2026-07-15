# 第44条主线：AutoPilot 分层补货与同轮续跑自动化验收报告

## 结论

- 实施验收：通过。
- 阻塞：实施与自动化验收非阻塞；N>1/无界放量阻塞。
- 放量条件：控制面指纹变化后，由用户另行明确启动单 Issue 金丝雀，并完成 implementationCommit、closeoutCommit、ledger、state、策略指纹与知识图谱 Git cursor 读回。
- 本轮未启动 AutoPilot、未执行真实 Ready、未提交、未 push、未连接生产环境。

## 正式交付物

- 补货快路径：权威 ReadySpec 完整性校验、确定性 Ready 生成、候选证据提交绑定。
- Planner 慢路径：Ready Plan schema v2、候选逐项决策、零 Ready 合法返回、300 秒硬超时与独立心跳。
- 同轮续跑：RUN/ISSUE StageResult v2、v1 读取兼容、RUN transition 写后读回、补货后重新 checkpoint。
- 双基线与策略证据：`candidateEvidenceHead`、`executionBaseCommit`、策略版本/哈希/引用进入运行和阶段上下文。
- 治理分层：主线负责人 Skill 收窄触发并去除动态事实副本；新增 AutoPilot 控制面策略契约和 HighRisk 计划校验入口。
- 回归夹具：所有实际启动 runner 的临时仓库显式携带策略契约；范围门禁和补修夹具补齐正式收口统计，避免在目标断言前被 F 门禁提前拦截。

## 验收证据

| 验证 | 结果 |
| --- | --- |
| `test-refill.ps1` | 通过；覆盖快路径、慢路径、v2 决策、零 Ready、候选越界与图谱停止分支 |
| `tests/test-refill-continuation.ps1` | 通过；覆盖 RUN transition、同轮 checkpoint、stop/pause |
| `test-continuous-runner.ps1` | 通过；完整兼容与主题套件 178.8 秒完成 |
| `test-state-machine.ps1`、`tests/test-transition-writer.ps1` | 通过；RUN/ISSUE 迁移写后读回 |
| `tests/test-stage-result-contract.ps1` | 通过；StageResult v2 与 v1 读取兼容 |
| `test-control-plane-fingerprint.ps1`、`test-context-isolation.ps1` | 通过；策略与双基线绑定 |
| `test-mainline-owner-flow.ps1 -Profile HighRisk` | 通过；触发范围、动态事实、计划结构与主线编号检查通过 |
| `validate-loop-artifacts.ps1` | 通过；缺失、非法 JSON、schema/example 预检均为 0 |
| 最终控制面指纹 | `0b534f6688f04077d1c9e486ea4867af9b1de225757b43ff62700e2c354abe95` |
| `git diff --check` | 通过；仅有 Git 换行提示，无格式错误 |

## 失败分类与修正

- 首次完整回归失败归类为 `tool_config/test_fixture`：多个临时 runner 仓库未携带新增策略文件，按 fail-close 在目标断言前退出。修正为夹具显式创建版本化策略文件。
- 范围门禁与补修集成随后被既有 F 收口门禁提前拦截，归类为 `ready_issue_config/test_fixture`。夹具报告补齐新增后续项、关闭后续项和净变化字段后，目标断言与完整套件均通过。
- 未发现需要业务代码补修、权限/安全整改或数据迁移的问题。

## 零悬空统计

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 唯一待执行动作是既有控制面金丝雀门禁，已由 `current-focus.md` 正式承接，不属于未登记问题。

## 剩余风险

- 自动化 fixture 已证明同轮续跑状态机和门禁语义，但没有替代真实本地单 Issue 金丝雀。
- Planner 的真实模型耗时仅由硬超时和心跳契约约束；本轮未调用外部 Planner，不形成真实耗时样本。
- 未经用户明确授权，不得把本报告解释为允许 N>1/无界 AutoPilot 放量。
