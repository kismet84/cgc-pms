# ISSUE-040-048 PowerShell 7控制面与UTF-8兼容回归验收报告

**Goal:** 将真实 PowerShell 7 宿主与 AutoPilot 控制面、连续 runner、状态机及 UTF-8 上下文回归绑定为正式证据，解除过期的“本机未安装 PowerShell 7”观察项。

**Architecture:** 只执行仓库既有确定性 PowerShell 自测并回写治理文档；复用 `Resolve-AutopilotPowerShellHost`、控制面指纹、连续 runner、状态机与 context delta 测试，不修改任何 ps1、配置、插件、hooks、skills 或规则文件，也不启动嵌套 Codex。

## 结论

- 验收结论：通过。
- 阻塞状态：无阻塞。
- 任务性质：回归证明，不是新增 AutoPilot 能力。
- 剩余风险：结论仅覆盖当前 Windows 与 PowerShell 7.6.3；Linux/macOS、PowerShell 8 预览版和第三方脚本不在本任务范围，且没有证据要求本轮扩项。

## 验收证据

- `pwsh --version`：PowerShell 7.6.3，满足主版本至少7。
- `test-control-plane.ps1`：退出码0，输出 `control plane self-test passed`。
- `test-state-machine.ps1`：退出码0，输出 `state machine self-test passed`。
- `tests/test-context-delta.ps1`：退出码0，输出 `context delta self-test passed`；覆盖中文 UTF-8 往返与输出无 BOM。
- `test-continuous-runner.ps1`：独立359.6秒窗口退出码0；执行模式、run lock fencing、控制面指纹、恢复/phase recovery、stall、review/repair、closeout、完成计数与报告投影子套件均通过。

## 失败分类与复验

- 首次将四命令并行包装在180秒窗口，聚合命令于184秒超时且未保留子结果；按规则分类为验证编排超时，不定性为控制面失败。
- 拆分后三个快速专项分别明确通过，连续 runner 改用360秒独立有界窗口并在359.6秒通过；未修改源码、测试断言或运行配置。

## Reviewer 裁决

- 所有专项均由真实 pwsh 7.6.3 执行，核心中文/UTF-8、状态原子性、fencing 和收口语义有明确退出码0证据。
- Git 差异仅包含 backlog、项目地图和本正式报告，不含 ps1、JSON 配置、插件、hooks、skills、AGENTS 或业务代码。
- 结论：PASS，findings=无。

## 后续项收口

- 本轮新增后续项：0
- 本轮关闭后续项：1
- 关闭问题键：`OBS-POWERSHELL7-COMPAT`
- 本轮后续项净变化：-1
