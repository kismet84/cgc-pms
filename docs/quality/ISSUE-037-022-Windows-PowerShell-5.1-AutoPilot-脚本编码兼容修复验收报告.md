# ISSUE-037-022 Windows PowerShell 5.1 AutoPilot 脚本编码兼容修复验收报告

## 结论

- 结论：通过。
- 阻塞：本 Issue 非阻塞；原 AutoPilot 控制面编码阻塞已解除。
- 是否可上线：不适用。本 Issue 只修本地 AutoPilot 工具链，不构成业务版本上线授权；`ISSUE-037-021` 的 CI/CD 不通过、阻塞、不可上线裁决保持不变。

## 根因与范围

当前宿主为 Windows PowerShell 5.1。AutoPilot 中含非 ASCII 内容的 `.ps1` 为 UTF-8 无 BOM，`powershell -File` 按系统 ANSI/GBK 解码后吞并字符串边界并产生 ParserError；显式 UTF-8 读入后的 AST 解析错误为 0。源码解析恢复后，连续 runner 又证明 UTF-8 无 BOM 的 `state.json` 若由未指定编码的 `Get-Content` 读取，会发生同类误解码并破坏 JSON。

修复只包含两层编码边界：含非 ASCII 的 AutoPilot PowerShell 源文件使用 UTF-8 BOM；AutoPilot 脚本实际文本读取显式指定 `-Encoding UTF8`。另在现有控制面自测中增加项目脚本与插件脚本默认解析断言。未修改业务代码、数据库、权限、租户、审批、金额、生产配置或远端仓库设置。

## 验收证据

| 验证项 | 结果 | 失败分类/说明 |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-control-plane.ps1` | 通过 | 修复前稳定失败并列出默认解析错误；修复后输出 `control plane self-test passed` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1` | 通过 | 首次 1 秒终止为命令调用前置；足额复跑暴露 UTF-8 JSON 读取缺口；补修后输出 `continuous runner self-test passed` |
| 实际控制面 `-ExplainNextAction -MaxIterations 1` | 通过 | 正确读取 flags/state/Ready，并串行选取 `ISSUE-037-022`；`autoPush=False` |
| Windows PowerShell AST 默认文件解析 | 通过 | 项目及插件 AutoPilot `.ps1` 解析错误为 0 |
| PowerShell 差异机械一致性核对 | 通过 | 除新增解析断言外，将 HEAD 机械补齐 BOM/显式 UTF-8 后与工作区一致，`mechanicalMismatchCount=0` |
| `git diff --check` | 通过 | 无空白错误 |

## 图谱与源码核验

- 查询目的：定位 AutoPilot runner、被加载脚本、状态读写与测试入口的编码影响面。
- CodeGraph：未召回预期 PowerShell 控制面，归类为“工具召回不足”，不据此判断代码不存在。
- `codebase-memory-mcp`：首次项目名错误；改用索引项目名后查询仍失败，归类 `tool_config`，未伪造命中。
- 交叉核验：使用 `rg`、PowerShell AST、Git 最近提交、工作区/HEAD blob hash 和机械差异重放确认根因及修改范围。

## 风险审查

- 高风险点：无业务权限、安全、金额、租户、审批或数据一致性变更；未连接数据库或生产环境。
- 范围风险：PowerShell 文件较多，但程序化重放证明除控制面新增断言外均为 BOM/显式 UTF-8 的机械变化，无控制流差异。
- 回滚：回退本 Issue 的 PowerShell 编码、控制面自测及状态文档即可；无需数据或运行态恢复。
- 非阻塞剩余风险：本机未安装 PowerShell 7，本轮未覆盖 `pwsh`；该项已进入 `current-focus.md`，不阻塞 Windows PowerShell 5.1 控制面。

## 最终裁决

- 通过/不通过：通过。
- 阻塞/非阻塞：非阻塞。
- 下一任务：本轮达到 `启动迭代-1` 的 1/1 上限，停止下一任务派发。
