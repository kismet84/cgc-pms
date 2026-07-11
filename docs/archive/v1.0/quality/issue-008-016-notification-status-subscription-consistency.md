# ISSUE-008-016：通知平台平台化缺口-M2：状态变更通知与订阅偏好一致性回归

## 结论

- 结论：通过
- 阻塞：无
- 失败分类或非失败分类：真实代码质量问题已修复；状态变更通知、订阅默认边界与前端订阅弹窗一致性回归已补齐；D 验收与 E 审查通过
- 是否自动合并：否
- 是否推送：否

## 本轮范围

- 后端回归补强：
  - `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
  - `backend/src/test/java/com/cgcpms/alert/notification/AlertNotificationDispatcherTest.java`
- 前端订阅边界收敛：
  - `frontend-admin/src/pages/alert/index.vue`
  - `frontend-admin/src/pages/alert/__tests__/index.test.ts`

## 实现摘要

### 后端

- `AlertEvaluationServiceTest` 将状态通知回归从单一 `PROCESSED` 扩展到 `PROCESSED / ARCHIVED / INVALID` 三种状态，统一核对：
  - 关闭 `notifyOnStatusChanged` 的用户不会收到状态通知；
  - 提高 `minSeverity` 的用户不会越过严重度门槛收到 `LOW` 级状态通知；
  - 非匹配角色域成员不会越过域边界收到状态通知；
  - 允许接收的管理员仍会收到三种状态对应的站内通知与发送记录。
- `seedMember` 增加幂等保护，避免重复插入项目成员影响当前回归。
- `AlertNotificationDispatcherTest` 新增渠道归一化回归，证明订阅渠道存在大小写和空白差异时，`STATUS_CHANGED` 站内通知不会被静默跳过。

### 前端

- 预警订阅弹窗新增最小边界收敛逻辑：
  - 渠道、预警域仅允许保存到默认范围与可选范围交集；
  - `minSeverity` 不能低于默认门槛；
  - `notifyOnStatusChanged` 只能在默认允许时开启；
  - `enabled` 也不会越过默认启用边界。
- 页面新增订阅摘要文案，直接反映“预警域 / 是否含状态变更 / 最低严重度”的当前有效配置。
- `alert/index.test.ts` 新增前端回归，验证弹窗展示与保存都会收敛到默认边界内，不能通过前端覆盖放大角色默认域或渠道边界。

## 验收证据

- D 验收：通过
- E 审查：PASS
- 后端关键证据：
  - `AlertEvaluationServiceTest` 明确断言状态通知接收人只剩 `USER_ADMIN`，不会越过 `notifyOnStatusChanged`、`minSeverity` 或角色域边界。
  - 同测试断言三种状态标题分别为 `预警已处理`、`预警已归档`、`预警已失效`，并且 `alert_notification_send_record` 中存在 `3` 条 `STATUS_CHANGED / IN_APP / SENT` 记录。
  - `AlertNotificationDispatcherTest` 断言订阅配置为 ` in_app ` 时，仍能调用 `IN_APP` 发送器并落发送记录。
- 前端关键证据：
  - `alert/index.vue` 通过 `resolveSubscriptionBoundaries`、`normalizeSubscriptionConfig` 将订阅表单限制在默认边界内。
  - `alert/__tests__/index.test.ts` 断言弹窗可见选项被收敛为 `channels=['IN_APP']`、`domains=['PURCHASE']`、`severityOptions=['MEDIUM','HIGH']`。
  - 同测试断言即使用户在表单里尝试提交更宽配置，最终保存仍被收敛为 `enabled=true`、`channels=['IN_APP']`、`domains=['PURCHASE']`、`minSeverity='MEDIUM'`、`notifyOnStatusChanged=false`。
- 收口门禁：
  - 后端 `57` 项测试通过：`AlertEvaluationServiceTest`、`AlertControllerTest`、`AlertNotificationDispatcherTest`
  - 前端 `17` 项测试通过：`pnpm vitest run src/pages/alert/__tests__/index.test.ts`
  - `pnpm type-check`：通过
  - `git diff --check`：通过
  - AutoPilot flag：`stop.flag=False`、`pause.flag=False`、`enabled.flag=True`

## 风险与边界

- 本轮只收敛站内通知状态变更与订阅偏好一致性，不扩展到邮件、短信、企业微信、钉钉、WebSocket 或 SSE 外部渠道。
- 本轮只约束“用户覆盖不能放大默认边界”，不重做通知平台角色默认矩阵，也不新增通知平台表结构。
- 订阅摘要属于当前有效配置的只读汇总，不是新的通知中心能力面板。
