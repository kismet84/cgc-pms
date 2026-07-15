# AutoPilot 已提交执行产物识别修复验收报告

## 裁决

- 结论：通过本地验收。
- 失败分类：`tool_config`，并非 ISSUE-040-019 的业务实现失败。
- 阻塞：已解除；执行器自行提交成果后，控制面仍能识别产物并继续执行范围、验证和收口门禁。
- 上线边界：仅修改本地 AutoPilot 控制脚本与回归测试，不涉及生产发布。

## 根因与最小修复

旧实现只通过 `git status` 判断执行器是否产生业务产物。执行器在隔离 worktree 中自行提交后，工作区恢复干净，导致控制面错误返回 `STOP_NO_EXECUTION_ARTIFACTS`；后续范围检查也无法看到已提交文件。

修复后，执行器产物识别同时比较执行前后 HEAD，并把提交差异纳入产物清单；连续 runner 的范围门禁、路由判断和最终检查统一使用“基线提交到当前 HEAD 的差异 + 未提交差异”。Git 路径读取显式设置 `core.quotePath=false`，确保中文报告路径不会被八进制转义后误判为 allowlist 外文件。未提交执行路径保持不变。

## 验收证据

- `scripts/codex-autopilot/test-continuous-runner.ps1`：通过；新增执行器提交中文文件名产物后仍返回 `done`、原始 Unicode 路径可追踪且范围匹配通过的回归断言。
- PowerShell 语法解析：四个变更脚本无解析错误。
- `git diff --check`：通过。

## 收口

- 新增后续项：0。
- 关闭后续项：1（执行器已提交成果被误判为无产物）。
- 后续项净变化：-1。
- 剩余风险：无未承接风险；后续迭代仍受 stop/pause/enabled、Ready、验证、审查和 no-push 门禁约束。
