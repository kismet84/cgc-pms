# ISSUE-008-020 规则治理中心平台化缺口-M2：阈值/窗口/严重度配置生效回归

日期：2026-07-10
Issue：ISSUE-008-020 规则治理中心平台化缺口-M2：阈值/窗口/严重度配置生效回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-020` 白名单改动：

- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `docs/backlog/current-focus.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `docs/quality/issue-008-020-rule-governance-config-effectiveness.md`
- `.codex-autopilot/state.json`

不纳入本轮：

- migration、deploy、生产配置、前端页面
- `stop.flag`、`pause.flag`、`enabled.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-006` 已验证 `enabled=0` 的最小启停语义，但 `alert_rule_config` 中 `threshold_ratio`、`window_days`、`severity_override` 仍停留在“字段存在、缺少正式生效证据”状态。

当前关闭口径：

1. `threshold_ratio` 调整后，金额/比例型规则触发阈值随配置变化，不再只依赖默认值。
2. `window_days` 调整后，时效类规则扫描窗口随配置变化，不回退到固定默认天数。
3. `severity_override` 配置后，生成预警严重度与配置一致；未配置时保留既有默认严重度。
4. 本轮只补规则配置生效回归，不扩大为规则治理中心页面、规则设计器、执行日志、效果分析或新表结构建设。

裁决：`ISSUE-008-020` 的“规则配置字段真实生效”最小平台化口径已闭环。

## 3. 实现事实

本轮最小实现闭环如下：

1. `AlertEvaluationServiceTest` 补齐 `CONTRACT_EXPIRING` 规则的 `window_days` 与 `severity_override` 生效回归。
2. `AlertEvaluationServiceTest` 补齐 `DYNAMIC_COST_EXCEEDS_TARGET` 规则的 `threshold_ratio` 生效回归。
3. 辅助清理与查询方法落在现有测试类内部，复用 `alert_rule_config`、现有评估链路和现有测试基座，不新增 migration 或生产实现重构。

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/flag 核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - 后端：`AlertEvaluationServiceTest`、`AlertControllerTest` 共 `58` 项测试通过
  - `git diff --check` 通过
- 验收要点：
  - `threshold_ratio` 能改变动态成本告警触发边界
  - `window_days` 能扩大合同到期扫描窗口
  - `severity_override` 能覆盖默认严重度，未配置时默认值不回退

### E 最终复审

- 结论：PASS
- 复审要点：
  - 修复保持在现有规则评估链路与测试基座内完成，没有扩写为规则引擎重构
  - 证据直接落在共享规则配置生效边界，而不是在单一调用点补局部判断
  - 原 `P2` 已关闭，不存在新增阻塞

### 当前补充核对

- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-020` 从当前 Ready 队列移出，并标记为已完成正式收口。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-020` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-020` 已完成正式收口，当前 Ready 队列为空，待主线程/A 基于长期任务池重新裁决下一条串行 Ready。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=9`
   - `remainingIterations=1`
   - `iterationLastCountedIssue="ISSUE-008-020"`
   - `lastAction="ISSUE_CLOSED"`
   - `lastReason="READY_ISSUE_CLOSED"`

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push，不改 AutoPilot flag 文件。

## 7. 最终裁决

正式交付物：

- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `docs/quality/issue-008-020-rule-governance-config-effectiveness.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：后端 `58` 项测试与 `git diff --check` 通过。
- E 最终复审：PASS；原 `P2` 已关闭。
- 当前补充核对：AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**` 等本地产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前规则治理中心仍只有配置字段生效层面的最小回归，不等于完整规则治理平台已完成。
2. 当前 Ready 队列已空；下一条串行 Ready 仍需主线程/A 基于长期任务池重新裁决。
