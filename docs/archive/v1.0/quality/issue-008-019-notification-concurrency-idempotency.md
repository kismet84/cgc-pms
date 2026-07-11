# ISSUE-008-019 通知平台平台化缺口-M5：并发重复分发幂等与发送记录一致性回归

日期：2026-07-10
Issue：ISSUE-008-019 通知平台平台化缺口-M5：并发重复分发幂等与发送记录一致性回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-019` 白名单改动：

- `backend/src/main/java/com/cgcpms/alert/notification/AlertNotificationDispatcher.java`
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- `docs/backlog/current-focus.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `docs/quality/issue-008-019-notification-concurrency-idempotency.md`
- `.codex-autopilot/state.json`

不纳入本轮：

- migration、deploy、生产配置、外部通知渠道真实接入
- `stop.flag`、`pause.flag`、`enabled.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-018` 收口后，通知平台剩余最小缺口收敛为：“串行重复分发已抑制，但并发重复触发时，站内信与发送记录仍可能重复写入”。

当前关闭口径：

1. 同一 `tenantId + alertId + targetUserId + eventType + IN_APP` 在并发重复触发时，只允许落下一条有效站内通知 / 有效 `SENT` 记录。
2. 被并发抑制的重复分发必须保留明确 `SKIPPED` 或抑制原因，不能误记为第二条 `SENT`。
3. 不同事件类型、不同告警 ID 或不同目标用户不能被错误串并，既有串行抑制语义不回退。
4. 本轮只补齐并发分发幂等与发送记录一致性，不扩大为模板中心、失败重试队列、全局频控配置、多 JVM 分布式幂等或外部渠道真实接入。

裁决：`ISSUE-008-019` 的“并发重复分发幂等与发送记录一致性”最小平台化口径已闭环。

## 3. 实现事实

本轮最小实现闭环如下：

1. `AlertNotificationDispatcher` 在现有通知分发链路内补齐并发场景最小幂等保护，优先复用当前发送记录与通知服务语义，不新增表结构或新配置。
2. `AlertNotificationDispatcherTest` 补并发回归，覆盖“同键并发只留一条有效发送”和“异键不误串并”两类边界。
3. 并发抑制分支对发送记录保留明确跳过语义，避免同一批次同时产生多条有效 `SENT`。

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/flag 核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - 后端：`AlertNotificationDispatcherTest`、`NotificationServiceTest`、`AlertEvaluationServiceTest` 共 `63` 项测试通过
  - `git diff --check` 通过
- 验收要点：
  - 同一并发键最终只产生一条有效站内通知或一条有效 `SENT`
  - 被抑制并发分发不会误记为第二条 `SENT`
  - 不同事件类型、不同告警 ID 或不同目标用户不被错误合并

### E 最终复审

- 结论：PASS
- 复审要点：
  - 修复保持在现有通知平台链路内完成，没有扩写为通知平台大改造
  - 根因收敛在共享分发幂等边界，而不是在单一路径上补局部锁或局部判断
  - 多 JVM 场景的分布式幂等仍属后续非本轮剩余风险，不影响本轮通过

### 当前补充核对

- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-019` 从当前 Ready 队列移除，并标记为已完成正式收口。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-019` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-019` 已完成正式收口，当前 Ready 队列为空，待主线程/A 基于长期任务池裁决下一条串行 Ready。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=8`
   - `remainingIterations=2`
   - `iterationLastCountedIssue="ISSUE-008-019"`
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
- `docs/quality/issue-008-019-notification-concurrency-idempotency.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：后端 `63` 项测试与 `git diff --check` 通过。
- E 最终复审：PASS；多 JVM 幂等为非本轮剩余风险。
- 当前补充核对：AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**` 等本地产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前幂等保护基于单 JVM / 当前进程内并发闭环，多 JVM / 分布式实例间幂等仍是后续平台化题。
2. 当前 Ready 队列已空；下一条串行 Ready 仍需主线程/A 基于长期任务池重新裁决。
