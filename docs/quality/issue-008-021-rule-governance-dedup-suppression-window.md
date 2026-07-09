# ISSUE-008-021 规则治理中心平台化缺口-M3：规则侧去重时窗与重复预警抑制生效回归

日期：2026-07-10
Issue：ISSUE-008-021 规则治理中心平台化缺口-M3：规则侧去重时窗与重复预警抑制生效回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-021` 白名单改动：

- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `docs/backlog/current-focus.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `docs/quality/issue-008-021-rule-governance-dedup-suppression-window.md`
- `.codex-autopilot/state.json`
- `.codex-autopilot/enabled.flag`

不纳入本轮：

- migration、deploy、生产配置、前端页面
- `stop.flag`、`pause.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-020` 收口后，规则治理中心剩余最小缺口收敛为：`alert_rule_config.dedup_hours` 字段已存在，但仍缺少“规则评估侧重复预警抑制真实生效”的正式证据。

当前关闭口径：

1. 同一规则、同一业务对象在 `dedup_hours` 窗口内重复评估时，不再生成第二条有效告警。
2. 当旧告警已超出配置的 `dedup_hours` 窗口时，规则允许重新生成新告警，不会被永久抑制。
3. 去重键不能错误串并不同规则类型、不同业务键或不同项目边界；既有 `enabled`、`threshold_ratio`、`window_days`、`severity_override` 生效语义不回退。
4. 本轮只补规则评估侧去重时窗回归，不扩大为规则治理中心页面、规则设计器、执行日志、效果分析或通知平台继续扩写。

裁决：`ISSUE-008-021` 的“规则侧去重时窗与重复预警抑制生效”最小平台化口径已闭环。

## 3. 实现事实

本轮最小实现闭环如下：

1. `AlertEvaluationServiceTest` 为 `insertRuleConfig` 增加 `dedupHours` 参数复用入口，避免为去重场景另造测试装配。
2. 新增 `TA14b/TA14c/TA14d` 三组回归，覆盖窗口内抑制、窗口外重新生成、不同规则/业务键/项目边界不串并。
3. 复用现有 `alert_rule_config`、`AlertEvaluationService`、`AlertLog` 与测试基座，不新增 migration，不改生产实现路径。

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/flag 核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - 后端：`AlertEvaluationServiceTest`、`AlertControllerTest` 共 `61` 项测试通过
  - `git diff --check` 通过
- 验收要点：
  - `dedup_hours` 窗口内重复评估不会新增第二条有效告警
  - 旧告警超出配置窗口后允许重新生成
  - 不同规则类型、不同业务键、不同项目边界不被错误串并

### E 最终复审

- 结论：PASS
- 复审要点：
  - 修复保持在现有规则评估链路与测试基座内完成，没有扩写为规则引擎或通知平台重构
  - 证据落在共享去重边界，而不是只给单一路径补局部判断
  - `threshold_ratio`、`window_days`、`severity_override` 既有生效语义未回退

### 当前补充核对

- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=False`
- 迭代上限状态：`iterationCompleted=10/10`、`remainingIterations=0`、`status=STOPPED`、`stopReason=ITERATION_LIMIT_REACHED`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-021` 从当前 Ready 队列移出，并标记为已完成正式收口。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-021` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `启动迭代-10` 已达到 `10/10` 上限、当前 Ready 队列为空，停止继续派发下一任务。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=10`
   - `remainingIterations=0`
   - `iterationLastCountedIssue="ISSUE-008-021"`
   - `lastIssue="ISSUE-008-021：规则治理中心平台化缺口-M3：规则侧去重时窗与重复预警抑制生效回归"`
   - `lastAction="ITERATION_LIMIT_REACHED"`
   - `lastReason="READY_ISSUE_CLOSED_LIMIT_REACHED"`
   - `status="STOPPED"`
   - `stopReason="ITERATION_LIMIT_REACHED"`
6. 删除 `.codex-autopilot/enabled.flag`，阻断第 11 轮启动。

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push。

## 7. 最终裁决

正式交付物：

- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `docs/quality/issue-008-021-rule-governance-dedup-suppression-window.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`
- `.codex-autopilot/enabled.flag`（删除）

验收证据：

- D 最终验收：后端 `61` 项测试与 `git diff --check` 通过。
- E 最终复审：PASS。
- 当前补充核对：AutoPilot 已达到 `10/10` 上限并处于停止态；白名单核对通过。

临时产物：

- `backend/target/**` 等本地产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前规则治理中心仍只有最小规则评估回归，不等于完整规则治理平台已完成。
2. `enabled.flag` 已关闭且本轮达到 `10/10` 上限；后续若要继续迭代，必须由主线程重新裁决并显式重新启用。
