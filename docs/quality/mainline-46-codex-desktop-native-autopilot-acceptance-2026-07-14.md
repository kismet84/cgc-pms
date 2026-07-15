# 第46条主线 Codex 桌面原生 AutoPilot 执行机制验收报告

## 结论

- 实现与自动化验收：通过。
- desktop-native 默认宿主最终可用性：不通过，阻塞于用户尚未明确启动并成功收口真实 `启动迭代-1` 金丝雀。
- N>1 / 无界连续执行：阻塞。
- 提交与推送：均未执行。

## 实施范围

- 生产配置新增 `executionHost=desktop-native`，保留显式 `cli-legacy` 兼容路径。
- 默认 runner 入口在桌面宿主下只输出结构化 handoff；Planner、Executor、Reviewer 和 Executor supervisor 在模型/子进程启动前执行宿主门禁。
- 新增桌面执行策略、主线程触发协议、短生命周期子智能体边界与原子 checkpoint/Ready/classifier/closeout/commit 薄入口。
- state、Issue checkpoint、run lock 支持执行宿主事实；Schema 字段保持可选以兼容历史文件，代码读取后补全默认值。
- checkpoint 汇总只读暴露 flags、state、活动 checkpoint、run lock、控制面指纹和 worktree，不启动嵌套 PowerShell 或模型进程。

## 安全切换证据

- 实施前分支为 `develop/1.5`，基线 HEAD 为 `167b2d49ab08d88b504c0c712a84c96fb9014e9c`。
- 发现 `ISSUE-040-025` 的活动 checkpoint 处于 `REPAIRING`，worktree 为 `.worktrees/autopilot/issue-040-025`；其 diff、checkpoint 与分支均未清理、未重派、未合并。
- 设置 `.codex-autopilot/pause.flag` 后，checkpoint 决策为 `pause`，`enabled.flag=true`、`stop.flag=false`、无 `run.lock`，因此没有第二宿主并发写入。
- 桌面入口 handoff 返回 `nestedModelCliInvocationCount=0`；新增模型门禁测试使用伪 `codex.cmd`，未生成调用 marker。

## 自动化验收

- 新增测试：`test-desktop-execution-host.ps1`、`test-no-nested-codex.ps1`、`test-desktop-checkpoint-recovery.ps1`、`test-desktop-subagent-boundary.ps1`，全部通过。
- desktop fixture 在不启动模型进程的情况下创建 `desktop-native` Issue checkpoint，依次读回 IMPLEMENTED、VALIDATING、VALIDATED、REVIEWING、结构化 Review PASS、CLOSING、两阶段提交状态、REGISTERED 与 CLOSED；恢复查询前后的既有 state/checkpoint 哈希不变。
- 核心契约：StageResult、transition writer、state machine、recovery reconciliation、review routing、run-lock fencing、control-plane fingerprint，全部通过。
- 兼容矩阵：`tests/test-runner-compatibility.ps1` 完整通过，旧 fixture 缺少 `executionHost` 时仍按 `cli-legacy` 工作。
- 收口与效率：closeout consistency、Evidence v2 reuse、context delta、efficiency observability、refill、context isolation、repair integration，全部通过。
- 插件资产校验、PowerShell 语法检查、JSON 解析与 `git diff --check` 通过。

## 失败分类与修正

1. 控制面策略升级到 v2 后，旧测试仍断言 v1；归类为测试契约同步，更新断言后通过。
2. readiness 的“缺 Executor”负例继承生产 `desktop-native`，按新语义应通过；将该负例显式设为 `cli-legacy` 后恢复原测试意图并通过。
3. checkpoint 在 StrictMode 下读取不含 `controlPlaneCanary` 的旧 fixture 失败；归类为兼容配置读取问题，增加属性存在性检查后完整兼容套件通过。
4. 完整兼容套件首次复跑超过外层 120 秒预算；归类为外层测试预算，不修改测试行为，扩大命令预算后 149.5 秒完成并通过。

## 换行与 Git

- 仓库本地 Git 配置为 `core.autocrlf=false`、`core.eol=lf`；系统级 `core.autocrlf=true` 未修改。
- 仅 `owner-boundary.md` 实际含 CRLF，已转换为 UTF-8 无 BOM 的 LF；内容差异为 3 增 1 删，没有整文件换行噪声。
- 未暂存、未提交、未 push。

## 未执行项与阻塞

- 本轮没有收到精确触发词 `启动迭代-1`，未运行真实 Ready、未生成 canary closeout record，也未更新 KG Git cursor。
- `ISSUE-040-025` 是已存在的 Ready 与 durable checkpoint 承接项，不新增重复 backlog。
- 解除条件：用户另行明确执行 `启动迭代-1`，先按 checkpoint 恢复并安全收口活动 Issue，再读回 implementation/closeout commit、ledger、state、KG cursor、flags 和新控制面指纹。

## 后续项统计

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 悬空问题：0；金丝雀由现有控制面门禁与 `ISSUE-040-025` Ready/checkpoint 正式承接。
