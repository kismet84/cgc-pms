# ISSUE-008-017 通知平台平台化缺口-M3：占位渠道可见性与发送记录语义回归

日期：2026-07-10
Issue：ISSUE-008-017 通知平台平台化缺口-M3：占位渠道可见性与发送记录语义回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-017` 白名单改动：

- `backend/src/main/java/com/cgcpms/alert/service/AlertSubscriptionService.java`
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- `frontend-admin/src/pages/alert/index.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `docs/backlog/**`
- `docs/quality/**`
- `docs/iterations/**`
- `.codex-autopilot/state.json`

不纳入本轮：

- migration、deploy、生产配置、外部通知渠道真实接入
- `stop.flag`、`pause.flag`、`enabled.flag`
- 白名单外其他未提交改动

## 2. 关闭口径

`ISSUE-008-016` 收口后保留的剩余风险是：“当前只有 `IN_APP` 真正可达，`EMAIL / WECHAT / SMS` 仍是占位渠道；订阅接口、前端展示与发送记录语义还需要再统一一轮”。

当前关闭口径：

1. 订阅默认值与可选渠道只暴露当前真实已配置能力，不再把 `EMAIL / WECHAT / SMS` 伪装成默认可用项。
2. 当前端尚未拿到订阅接口返回时，预警页订阅摘要不再回退展示占位渠道。
3. 当用户请求未配置占位渠道时，发送记录必须落 `SKIPPED`，并写明 `CHANNEL_NOT_CONFIGURED`，不能误记为 `SENT`。
4. 当某占位渠道被标记为已配置但发送器仍未实现时，发送记录仍必须落 `SKIPPED`，并写明 `CHANNEL_NOT_IMPLEMENTED`。

裁决：`ISSUE-008-017` 的“占位渠道可见性与发送记录语义一致性”口径已闭环，通知平台当前最小平台化缺口继续保持在“仅站内信可达、外部渠道未接入”的真实边界。

## 3. 实现事实

本轮最小实现闭环如下：

1. `AlertSubscriptionService` 删除写死的 `DEFAULT_CHANNELS`，改为基于 `AlertNotificationChannelProperties` 计算当前已配置渠道；没有真实可用渠道时，默认订阅不会误标为启用。
2. `AlertControllerTest` 补订阅接口断言，确认 `effectiveSubscription.channels`、`rawUserOverrides.channels` 与 `availableOptions.channels` 当前都只返回 `IN_APP`。
3. `AlertNotificationDispatcherTest` 补两类发送记录回归：
   - 未配置占位渠道请求落 `SKIPPED + CHANNEL_NOT_CONFIGURED`
   - 已配置但未实现的占位渠道请求落 `SKIPPED + CHANNEL_NOT_IMPLEMENTED`
4. `frontend-admin/src/pages/alert/index.vue` 移除订阅摘要的占位渠道兜底数组，未拿到接口数据前不再展示 `EMAIL / WECHAT / SMS`。
5. `frontend-admin/src/pages/alert/__tests__/index.test.ts` 补前端回归，确认订阅数据未返回前页面不展示占位渠道，接口返回后只展示真实可用的 `IN_APP`。

## 4. 验收与复审证据

本轮正式收口采信既有 D/E 结论，并补当前 Git/flag 核对。

### D 最终验收

- 结论：通过
- 采信范围：
  - 后端：`AlertNotificationDispatcherTest`、`AlertControllerTest`、`AlertEvaluationServiceTest` 共 `59` 项测试通过
  - 前端：`pnpm vitest run src/pages/alert/__tests__/index.test.ts` 共 `18` 项测试通过
  - `pnpm type-check` 通过
  - `git diff --check` 通过
- 验收要点：
  - 订阅接口可见渠道与当前真实已配置能力一致
  - 占位渠道发送记录不再误记为成功
  - 前端订阅摘要与接口返回口径一致，不再展示未生效能力

### E 最终复审

- 结论：PASS
- 复审要点：
  - 修复保持在现有通知平台链路内完成，没有扩写为邮件、短信、企业微信真实接入
  - 根因收敛在共享默认值与占位渠道语义，而不是在单一路径上补局部判断
  - 发送记录语义、订阅接口语义与前端展示语义现已一致

### 当前补充核对

- AutoPilot flag 状态：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`
- 白名单核对：本轮仅 stage 允许提交文件，不包含白名单外路径

## 5. Backlog 与 AutoPilot 同步动作

本轮同步如下：

1. `docs/backlog/ready-issues.md` 将 `ISSUE-008-017` 从当前 Ready 队列移除，并标记为已完成正式收口。
2. `docs/backlog/done-issues.md` 新增 `ISSUE-008-017` 完成记录。
3. `docs/backlog/current-focus.md` 标注 `ISSUE-008-017` 已完成正式收口，当前 Ready 队列为空，待主线程/A 裁决下一条串行 Ready。
4. `docs/iterations/iteration-2026-07-10-report.md` 追加本 Issue 收口摘要。
5. `.codex-autopilot/state.json` 更新为：
   - `iterationCompleted=6`
   - `remainingIterations=4`
   - `iterationLastCountedIssue="ISSUE-008-017"`
   - `lastAction="ISSUE_CLOSED"`
   - `lastReason="READY_ISSUE_CLOSED"`

## 6. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 仅 stage 本 Issue 白名单文件。
2. 不处理白名单外脏改动，不清理工作区。
3. 不执行 push，不改 AutoPilot flag 文件。

## 7. 最终裁决

正式交付物：

- `backend/src/main/java/com/cgcpms/alert/service/AlertSubscriptionService.java`
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- `frontend-admin/src/pages/alert/index.vue`
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
- `docs/quality/issue-008-017-notification-channel-visibility-send-record-semantics.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`
- `.codex-autopilot/state.json`

验收证据：

- D 最终验收：后端 `59` 项测试、前端 `18` 项测试、`type-check` 与 `git diff --check` 通过。
- E 最终复审：PASS，确认实现保持最小闭环且未扩大范围。
- 当前补充核对：AutoPilot flag 状态正常；白名单核对通过。

临时产物：

- `backend/target/**`、前端测试缓存等本地产物未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前通知平台仍只有站内信真实可达；外部渠道接入、模板中心、重试与频控仍是后续题，不在本轮。
2. 当前 Ready 队列已空；下一条串行 Ready 仍需主线程/A 基于长期任务池重新裁决。
