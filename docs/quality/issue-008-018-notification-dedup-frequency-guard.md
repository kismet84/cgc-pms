# ISSUE-008-018 通知平台平台化缺口-M4：同告警重复通知抑制与站内信频控回归

日期：2026-07-10
Issue：ISSUE-008-018 通知平台平台化缺口-M4：同告警重复通知抑制与站内信频控回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-018` 白名单改动：

- `backend/src/main/java/com/cgcpms/alert/notification/AlertNotificationDispatcher.java`
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- `docs/backlog/current-focus.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `docs/quality/issue-008-018-notification-dedup-frequency-guard.md`
- `.codex-autopilot/state.json`

不纳入本轮：

- migration、deploy、生产配置、外部通知渠道真实接入
- `stop.flag`、`pause.flag`、`enabled.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-017` 收口后，通知平台剩余最小缺口收敛为：“规则侧已有告警去重，但同一告警在分发侧重复触发时，站内信与发送记录仍可能重复刷屏”。

当前关闭口径：

1. 同一 `alertId + targetUserId + eventType + IN_APP` 在短时间内重复分发时，只保留一条有效站内通知。
2. 被抑制的重复分发不能误记为 `SENT`，必须在发送记录中留下明确抑制语义。
3. 不同事件类型或不同告警 ID 不能被错误合并，既有通知链路不回退。
4. 本轮只补齐最小分发侧抑制与站内信频控，不扩大为模板中心、失败重试队列、全局可配置频控或外部渠道真实接入。

裁决：`ISSUE-008-018` 的“同告警重复通知抑制与站内信频控”最小平台化口径已闭环。

## 3. 实现事实

本轮最小实现闭环如下：

1. `AlertNotificationDispatcher` 在现有通知分发链路内补齐同告警重复发送抑制，优先复用当前记录语义，不新增表结构或新配置。
2. `AlertNotificationDispatcherTest` 补重复分发回归，覆盖同告警同事件抑制与非同事件/非同告警不误合并两类边界。
3. 发送记录对被抑制的重复分发保留明确状态或原因，不再误记为 `SENT`。

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/flag 核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - 后端：`AlertNotificationDispatcherTest`、`AlertEvaluationServiceTest`、`AlertControllerTest` 共 `62` 项测试通过
  - `git diff --check` 通过
- 验收要点：
  - 同一告警同一事件类型重复触发时，只保留一条有效站内通知
  - 被抑制记录不会误记为 `SENT`
  - 不同事件类型或不同告警 ID 不被错误合并

### E 最终复审

- 结论：PASS
- 复审要点：
  - 原 P1 已关闭
  - 修复保持在现有通知平台链路内完成，没有扩写为通知平台大改造
  - 根因收敛在共享分发抑制边界，而不是在单一路径上补局部判断

### 当前补充核对

- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-018` 从当前 Ready 队列移除，并标记为已完成正式收口。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-018` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-018` 已完成正式收口，当前 Ready 队列为空，待主线程/A 裁决下一条串行 Ready。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=7`
   - `remainingIterations=3`
   - `iterationLastCountedIssue="ISSUE-008-018"`
   - `lastAction="ISSUE_CLOSED"`
   - `lastReason="READY_ISSUE_CLOSED"`

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push，不改 AutoPilot flag 文件。

## 7. 最终裁决

正式交付物：

- `backend/src/main/java/com/cgcpms/alert/notification/AlertNotificationDispatcher.java`
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- `docs/quality/issue-008-018-notification-dedup-frequency-guard.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：后端 `62` 项测试与 `git diff --check` 通过。
- E 最终复审：PASS，原 P1 已关闭。
- 当前补充核对：AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**` 等本地产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前并发竞态仍是通知平台后续非本轮剩余风险，本轮未扩写到并发级别治理。
2. 当前 Ready 队列已空；下一条串行 Ready 仍需主线程/A 基于长期任务池重新裁决。
