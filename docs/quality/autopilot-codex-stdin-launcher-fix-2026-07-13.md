# AutoPilot Codex stdin 启动参数修复验收报告

## 裁决

- 结论：通过本地验收。
- 失败分类：`tool_config`，不是业务代码、Ready Issue 或测试环境失败。
- 阻塞：已解除；Ready Planner、Issue Executor 和独立 Reviewer 共用的 Codex 启动路径已修复。
- 上线边界：仅影响本地 AutoPilot 控制面，不构成 CGC-PMS 生产发布。

## 根因与最小修复

Windows PowerShell 通过 `-File codex.ps1` 启动 Codex 时，会把参数末尾单独的 `-` 解释为非法参数名。三条控制面路径已经重定向 stdin，Codex 在没有显式 prompt 参数时会自动读取 stdin，因此末尾标记没有必要。

公共启动器新增参数规范化，移除 Codex 参数中的独立 stdin prompt 标记；Executor 会在配置参数之后追加模型与推理参数，因此不能只检查最后一项。其他参数及顺序保持不变。Planner、Executor、Reviewer 均在创建进程前使用同一规范化函数，不改变模型、推理强度、sandbox、审批、工作目录或输出 schema。

## 验收证据

- 修复前最小探针稳定返回 PowerShell `PSArgumentException`；移除末尾标记后相同模型、schema、输出路径和 stdin 调用成功。
- `scripts/codex-autopilot/test-control-plane.ps1`：通过，新增末尾标记移除与普通参数保持不变的回归断言。
- `scripts/codex-autopilot/test-refill.ps1`：通过。
- `scripts/codex-autopilot/test-continuous-runner.ps1`：通过。
- `git diff --check`：通过。

## 收口

- 新增后续项：0。
- 关闭后续项：1（Ready Planner 无法通过 Windows PowerShell shim 读取 stdin）。
- 后续项净变化：-1。
- 剩余风险：无未承接实现风险；连续 runner 仍需按 stop/pause/enabled、Ready、验证、审查和 no-push 门禁运行。
