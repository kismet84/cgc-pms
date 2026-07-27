# ISSUE-053-040 M7 流程配置与 adminOnly 门 V2 验收报告

> 日期：2026-07-27
>
> 基线：`master@07f0169da0a3c8ae3f6ee4de463dfe55da2e84de`
>
> 裁决：`PASS / ISSUE-053-040 Done / M7 CONTINUES`

## 1. 范围

`/approval/process` 由 Clean-room V2 流程配置页承接。

- router 与 navigation 同时要求 `ADMIN`/`SUPER_ADMIN` 角色和精确 `workflow:process:query` 权限；任一缺失均失败关闭且不请求流程 API。
- 页面复用既有模板、节点和排序 API；写成功后重新读取详情或列表，金额区间保留服务端字符串。
- 后端只做自动化证明所需根修：同模板写操作按租户行锁串行，模板启用状态只接受 `0/1`。
- 未新增权限码、数据库 migration、前端审批裁决或第二套工作流。

## 2. 契约与安全证据

| 事项 | 结论 | 证据 |
| --- | --- | --- |
| adminOnly 双门禁 | ADMIN/SUPER_ADMIN 与精确权限缺一不可；普通角色即使误配权限也不可见、不可路由、不可请求 API | navigation/router unit、目标 E2E、`WorkflowTemplateControllerTest` |
| 租户隔离 | 列表、详情和写操作按当前租户失败关闭；写锁 SQL 同时限定模板 ID 与租户 ID | `WorkflowTemplateManagementTest`、`WfTemplateMapper.selectByIdForUpdate` |
| 原子性 | 模板更新、节点增删改和完整 ID 排序均先取得同模板行锁并在事务内完成 | `WorkflowTemplateManagementTest.reorderWaitsForTemplateWriteLock` |
| 状态与金额 | enabled 只接受 `0/1`；金额上下限由后端校验，前端保持原始十进制字符串 | DTO/service/controller test、V2 page unit |
| 写后回读 | 模板更新、节点增删改和排序后重新读取服务端详情；列表需要时同步刷新 | V2 page/service unit |
| 运行中实例 | 页面明确提示模板修改不影响运行中实例，不在前端改写实例状态 | WorkflowProcessPage |

## 3. 验证

| 门禁 | 结果 |
| --- | --- |
| 后端定向测试 | 3 个测试类，38 项，0 失败，`BUILD SUCCESS` |
| V2 目标 unit | navigation、router、page、service 共 31/31 通过 |
| 设计系统门禁 | 2 文件、73 项全通过 |
| V2 全量 unit | 54 文件、391 项全通过 |
| type/contracts | `pnpm type-check`、`pnpm type-check:contracts` 通过 |
| lint | 0 error；本任务 warning 为 0；6 个既有 warning 均在未修改的 `e2e/m1-shell.spec.ts:149-156` |
| clean-room | 195 个 V2 文件、16 个契约文件通过 |
| route ledger | 87 条命名路由，`8/79/0`，无漂移 |
| build | 250 modules，构建通过 |
| 目标 E2E | 独立端口 5178，3/3 通过 |

失败处理遵循唯一分类契约：台账预期、Pinia/Response 测试夹具、金额字符串断言、设计系统规则和并发测试线程上下文归 `quality_or_security`，均在本轮修复并复验；5174 旧 Vite 被复用归 `runtime_environment`，改用隔离端口 5178 后通过。未放宽业务或安全门禁。

## 4. 回滚与边界

- 回滚只移除流程配置 V2 页面、服务、adminOnly 元数据与守卫、对应测试和台账接受项，并恢复本切片后端行锁/状态校验改动；无数据库 migration。
- 正式入口、Legacy 退役、commit、push、PR、目标环境和生产均未授权。
- 下一唯一 Ready 为 `ISSUE-053-041`，承接系统管理 8 条路由；不由本切片提前实现。
- 新增后续项：0；关闭后续项：0；后续项净变化：0。当前范围内无口头悬空项。
