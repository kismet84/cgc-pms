# Codex Desktop Native AutoPilot Execution Policy

Policy-Version: 1
Status: active

根硬门禁由 `AGENTS.md` 自动加载，一级失败分类引用 `.agents/skills/cgc-pms-ci-gate-triage/SKILL.md`；本文件只定义桌面原生 AutoPilot 专项执行边界。

## 默认执行宿主

- `scripts/codex-autopilot/codex-autopilot.config.json` 的 `executionHost=desktop-native` 是默认生产配置。
- Codex 桌面主线程持有连续迭代控制权，直接完成 checkpoint、候选选择、A-F 职责编排、验证、复核、收口和下一轮判断。
- PowerShell 只提供确定性的原子工具：读取 checkpoint、校验/迁移状态、Ready 写入、验证命令、失败分类、复核结果校验、收口投影、Git scope/fencing 检查及本地提交收口。
- `autopilot-run-continuous.ps1` 在桌面原生宿主下只返回结构化 handoff，不进入旧 coordinator，也不得启动 Planner、Executor 或 Reviewer 模型进程。

## 主线程边界

- A-F 由桌面主线程直接编排，不映射为独立线程。
- 桌面主线程持有跨阶段、下一任务选择、全局 state 和 run lock。
- 每阶段前读取 durable checkpoint，并校验 Issue、base commit、worktree/branch、范围、diff 与 evidence 绑定；自由文本不得直接驱动状态迁移。

## 旧 CLI 兼容路径

- `cli-legacy` 仅用于显式兼容、回归测试或经用户授权的紧急回退；缺少 `executionHost` 的旧测试夹具按 `cli-legacy` 解释。
- Planner、Executor、Reviewer 的进程启动函数必须在启动模型前执行 execution-host 门禁；`desktop-native` 一律抛出 `DESKTOP_NATIVE_MODEL_PROCESS_FORBIDDEN`。
- 回退不得绕过 Ready、stop/pause、fencing、控制面指纹、单 Issue 金丝雀、验证、复核、收口或 no-push 边界。
- 宿主切换本身不得删除 worktree、重复执行已完成 implementation，或把心跳/进程活动当作语义进度。

## 恢复与事实源

- 桌面主线程每个阶段前后调用只读 checkpoint 原子工具，并以 state、活动 Issue checkpoint、run lock、控制面指纹和当前 Git 事实的组合决定恢复点。
- state 与 Issue checkpoint 的 `executionHost` 用于记录最后一次写入宿主；旧记录缺失时只读迁移为 `cli-legacy`，不得据此启动模型。
- Reviewer 结果必须写为结构化 JSON，并与同一 Issue 和精确 diff hash 绑定后才能进入 closeout。
- 控制面行为变化后，多 Issue 或无界执行仍需用户明确启动并通过一次 `启动迭代-1`；普通实现或测试命令不得冒充金丝雀。
- 桌面原生单 Issue 金丝雀只有在实施与收口提交不同、Closeout Record v2 已登记、知识图谱 Git 游标等于当前 HEAD 后，才允许调用 `autopilot-register-canary.ps1` 原子登记并读回 `lastCanaryFingerprint` 与报告路径。
- 新批次重置迭代计数时必须保留既有金丝雀证据；是否需要再次执行金丝雀只由当前控制面指纹与已登记指纹是否一致决定，不得按批次机械清空。
