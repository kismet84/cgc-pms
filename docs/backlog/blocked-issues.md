# Blocked Issues

## v1.5 当前阻塞任务

`ISSUE-037-021` 只读复验形成以下当前阻塞；完整证据见 [正式报告](../quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md)。v1.0 阻塞快照见 [历史记录](../archive/v1.0/backlog-snapshot/blocked-issues.md)。

### ISSUE-037-021-A：master 前端 required checks 红灯

- 失败分类：真实质量类。
- 阻塞原因：目标 commit `781b41661cd96b2a2f7eed825f98ff3d9bdf137b` 的 `frontend-lint` 与 `frontend-test` failure；lint 为 2 个未使用变量，test 为页面契约断言失败。
- 已完成证据：run 29146534529 的 job、annotation、lint 制品与本地 lint/coverage 复现已归档。
- 解除条件：另拆前端实现型 Ready，保持现有规则并修复后，让新目标 commit 的两项 required checks success。
- 未完成验收项：当前红灯未修；尚未取得新 commit 远端 checks。
- 安全恢复方式：只回滚后续前端修复 diff；不得放宽 lint/test 或取消 required check。

### ISSUE-037-021-B：master e2e required check 红灯

- 失败分类：真实质量类。
- 阻塞原因：“付款申请页 -- KPI 卡片可见”在原始运行及两次 retry 均找不到 `.ant-table, .vxe-table`。
- 已完成证据：run 29146534529 的失败 job 与 e2e failure artifact 已归档。
- 解除条件：另拆 E2E/前端实现型 Ready，确认页面契约并让新目标 commit 的 e2e required check success。
- 未完成验收项：页面实现与测试契约哪一方过期仍需在实现 Issue 中裁决。
- 安全恢复方式：只回滚后续页面或用例修复；不得跳过测试或取消 required check。

### ISSUE-037-021-C：分支保护存在绕过路径

- 失败分类：工具配置/仓库治理类。
- 阻塞原因：master `enforce_admins=false` 允许管理员绕过保护；push restrictions 未启用表示无推送主体白名单。required checks 虽 strict 且名称匹配，治理绕过风险仍未解除。
- 已完成证据：2026-07-12 GitHub branch protection API 只读响应已归档。
- 解除条件：仓库管理员明确授权并收紧设置，随后由独立 Reviewer 复读 API。
- 未完成验收项：未获远端设置变更授权，本 Issue 禁止修改远端。
- 安全恢复方式：若收紧导致不可接受影响，按变更前 API 快照恢复原设置。

## 记录格式

每条阻塞记录必须包含：Issue、失败分类、阻塞原因、已完成证据、解除条件、未完成验收项和安全恢复方式。
