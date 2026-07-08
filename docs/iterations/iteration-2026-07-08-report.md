# Iteration Report

Issue：ISSUE-000-001 搭建本地 Codex AutoPilot 第一轮治理框架

目标：
- 将第一轮治理框架的通过验收事实正式归档，避免该 Issue 继续作为 Ready 任务被自动选中。

修改范围摘要：
- `docs/backlog/ready-issues.md`：将 ISSUE-000-001 从 Ready 池移出，转入已完成/历史。
- `docs/backlog/done-issues.md`：追加 Done 记录，标记完成日期、合并方式、验证结果和关联报告。
- `docs/iterations/iteration-2026-07-08-report.md`：沉淀本轮正式 iteration report。
- 验收依据摘要：第一轮治理框架独立验收结论为通过/非阻塞；17 项交付物已落地；`start`、`status`、`pause`、`resume`、`stop`、`kill` 默认不强杀、config JSON parse、`git diff --check` 已验证通过。

验证命令摘要：
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-start.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-status.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-pause.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-resume.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-stop.ps1`：通过
- `powershell -ExecutionPolicy Bypass -File scripts/codex-autopilot/autopilot-kill.ps1`：通过，默认不强杀
- `git diff --check`：通过

是否自动合并：否
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- Windows 计划任务仍需在实机场景补做一次安装/卸载核验。
- CRLF 警告属于非阻塞项，不影响本轮归档结论。
- 后续进入真实业务迭代前，需补充新的 P0 Ready Issues。

---

Issue：ISSUE-004-001 成本台账与汇总口径回归

目标：
- 核对成本台账、成本汇总、成本科目联动与 Dashboard 成本视图主链路金额口径，形成可追溯回归证据。

修改范围摘要：
- `docs/quality/issue-004-001-cost-ledger-regression.md`：新增正式质量报告，记录金额口径、样例断言、验证命令与通过结论。
- `docs/backlog/ready-issues.md`：将 ISSUE-004-001 从 Ready 池移出。
- `docs/backlog/done-issues.md`：追加 Done 记录。
- 本轮未修改后端生产代码或测试代码；现有覆盖已满足该 Issue 验收标准。

验证命令摘要：
- `cd backend; .\mvnw.cmd "-Dtest=DashboardServiceTest,DashboardCostServiceTest,CostSummaryServiceTest,CostLedgerServiceTest" test`：通过，`Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

是否自动合并：否
是否推送：否
结论：通过
阻塞：非阻塞
剩余风险：
- 原 Ready Issue 中 `CostServiceTest` 类名不存在，本轮已使用真实成本测试类替代。
- 本轮未跑全量后端测试，结论限于 ISSUE-004-001 指定回归范围。
