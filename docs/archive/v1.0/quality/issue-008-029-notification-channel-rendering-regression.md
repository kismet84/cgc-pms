# ISSUE-008-029 通知订阅弹窗与渠道可见性真实渲染验收治理

## 结论

通过 / 非阻塞。

本 Issue 只补齐通知平台前端组件级渲染证据：订阅弹窗、订阅摘要和明细展示会把当前可用的站内信与占位渠道区分开，不把邮件、企业微信、短信展示为已可直接生效或已发送成功。它不是浏览器 E2E，也不是外部邮件、短信、企业微信或其他渠道的正式打通。

## 范围

- 修改：`frontend-admin/src/pages/alert/__tests__/index.test.ts`
- 归档：`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/backlog/current-focus.md`、`docs/iterations/iteration-2026-07-10-report.md`、`.codex-autopilot/state.json`
- 未修改：后端、migration、deploy、外部渠道接入、模板中心、失败重试队列、全局频控配置

## 验收证据

- `pnpm test:unit src/pages/alert/__tests__/index.test.ts`：通过，`19 passed`
- `pnpm type-check`：通过
- `pnpm build`：通过
- `git diff --check`：通过
- E 审查：PASS

## 证据说明

- 新增订阅明细组件级断言：当后端返回 `IN_APP`、`EMAIL`、`WECHAT`、`SMS` 时，页面仅展示当前默认允许的“站内信”，不展示“邮件”“企业微信”“短信”为可用能力。
- 新增订阅表单渲染断言：弹窗保留“通知渠道”区域并展示“站内信”，占位渠道不渲染为可选项。
- 既有源码防回退断言继续约束：不允许前端硬编码展示 `['IN_APP', 'EMAIL', 'WECHAT', 'SMS']` 全量占位渠道。

## 失败分类或非失败分类

非失败分类；通知订阅弹窗与渠道可见性的组件级真实渲染证据已补齐。

## 剩余风险

- 当前证据为组件级 mount / 单元测试，不是浏览器 E2E；该风险符合 Ready 边界，非阻塞。
- 当前只验证既有预警页订阅弹窗与渠道可见性，不代表邮件、短信、企业微信、钉钉、WebSocket/SSE、模板中心、失败重试队列或完整通知平台已完成。
