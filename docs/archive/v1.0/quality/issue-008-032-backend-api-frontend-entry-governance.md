# ISSUE-008-032 后端业务接口与前端入口治理收口报告

## 裁决

- 结论：通过。
- 阻塞：无。
- 范围：50 个非 dev/test 后端 Controller 与现有前端 router、navigation、API module、页面的映射治理。
- 口径：本轮完成入口映射基线并修复一个高置信既有页面入口，不代表所有后端业务接口均已建设前端承载页。

## 分类结果

| 分类 | 数量 | 处理结论 |
| --- | ---: | --- |
| 已有前端入口 | 39 | 保持现状 |
| 合法后台复用接口 | 4 | File、Notification、CtContractItem、CtContractPaymentTerm 继续由既有页面或上层业务复用，不强制注册独立入口 |
| 运维专用接口 | 1 | Prometheus `/actuator` 保持运维入口，不进入业务导航 |
| 高置信入口缺口 | 6 | 项目成员入口已修复；其余 5 项转为非阻塞后续治理 |

本轮将项目成员路由权限由不存在的 `project:member:query` 对齐为后端 `project:member:list`，继续复用现有 `/system/users` 用户选择数据源，不新增 API 封装或占位页面。成员新增、角色编辑和移除分别由 `project:member:add`、`project:member:edit`、`project:member:delete` 控制；后端成员服务当前仍校验同租户项目归属，但未接入 `ProjectAccessChecker`，项目级访问范围需作为既存非阻塞后端数据范围风险后续专项复核与治理。

## 验收证据

- D2：3 个目标测试文件共 `27/27` 通过。
- `pnpm type-check`：exit 0。
- `pnpm build`：exit 0。
- `git diff --check`：exit 0。
- E 首轮审查发现 list-only 用户仍可见新增、编辑、删除控件；B2 使用既有 `hasPermission` 按 add/edit/delete 权限完成最小补修。
- E2 最终审查：PASS，无阻塞。

## 非阻塞后续

- 仍无现成前端承载页的 5 个业务接口：`/accounting-entry`、`/audit-logs`、`/bid-cost`、`/overhead-allocation`、`/contract-revenue`。这些接口需要独立页面或业务入口裁决，本轮不新增占位页面。
- 当前测试已证明 list-only 用户不会看到新增、角色编辑和移除控件；具备 add/edit/delete 权限时的正向显示用例尚未补齐。现有权限实现与 D2/E2 证据足以收口，作为非阻塞测试补强项留待后续。
