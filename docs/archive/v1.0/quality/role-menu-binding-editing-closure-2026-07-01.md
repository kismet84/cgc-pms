# 10C 首包角色菜单绑定编辑增强质量归档

日期：2026-07-01

## 总体结论

结论：通过。

阻塞/非阻塞：非阻塞。

关闭建议：建议关闭 10C 首包。

本归档仅覆盖“角色菜单绑定编辑增强”首包：复用现有 `PermissionModal -> updateRoleMenus -> PUT /system/roles/{id}/menus` 链路，在后端 `SysRoleService.assignMenus` 上补最小门禁与最小审计快照。未开放权限码本体编辑，未新增新权限事实源，未扩展到 10B 项目强隔离、10D 入口注册、全站权限平台或动态权限 DSL。

## 范围边界

本次纳入：

- 角色菜单绑定正向保存。
- `SUPER_ADMIN` 角色禁改。
- 高危系统权限 diff 禁改。
- 无权限账号不可提交。
- 成功/失败审计快照落库。
- 前端零改动承接后端拒绝语义。

本次不纳入：

- `sys_menu.perms` 权限码本体编辑。
- 可编辑权限平台页面。
- 新权限事实源。
- 项目级强隔离。
- 全站入口注册体系。
- 付款申请或更多业务类型。

## 前置结论摘要

| 专项 | 结论 | 说明 |
|---|---|---|
| 后端 | 通过 | 继续复用 `PUT /system/roles/{id}/menus` 与 `SysRoleService.assignMenus`，补最小门禁和审计。 |
| 数据库 | 通过 | 新增 `sys_role_menu_audit_snapshot`，MySQL / H2 对等 migration。 |
| 安全 | 通过 | 未新增授权聚合、权限事实源或权限码本体编辑入口。 |
| 运维 | 通过 | 运行态刷新通过。 |
| 前端 | 通过 | 零改动成立，现有 `PermissionModal` 与 `message.error(...)` 足以承接后端拒绝语义。 |
| 测试 | 通过 | 正向保存、拒绝门禁、审计快照均有最终回传证据。 |

## 代码与数据证据

- `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java`
  - `assignMenus` 仍是角色菜单绑定主入口。
  - `requireEditableRole` 覆盖 `ROLE_MENU_SUPER_ADMIN_PROTECTED` 与 `ROLE_MENU_SELF_EDIT_FORBIDDEN`。
  - `rejectHighRiskDiff` 覆盖 `system:user:*` / `system:role:*` / `system:menu:*` 高危权限 diff。
  - 成功路径调用 `SysRoleMenuAuditService.record(..., true, null)`。
  - `BusinessException` 失败路径调用 `recordFailureAudit(...)` 写失败快照。
- `backend/src/main/java/com/cgcpms/system/service/SysRoleMenuAuditService.java`
  - 使用 `REQUIRES_NEW` 记录审计快照，避免失败审计随主事务回滚丢失。
- `backend/src/main/java/com/cgcpms/system/entity/SysRoleMenuAuditSnapshot.java`
  - 表实体包含 `tenantId`、`operatorId`、`roleId`、`beforeMenuIds`、`afterMenuIds`、`successFlag`、`errorSummary`、`createdAt`。
- `backend/src/main/resources/db/migration/V112__create_role_menu_audit_snapshot.sql`
- `backend/src/main/resources/db/migration-h2/V112__create_role_menu_audit_snapshot.sql`
  - MySQL / H2 双 migration 均存在。
- `backend/src/test/java/com/cgcpms/system/SysRoleServiceTest.java`
  - 覆盖 `SUPER_ADMIN` 禁改、自编辑禁改、高危权限 diff 禁改、成功审计快照等定向断言。
- `backend/src/test/java/com/cgcpms/MigrationIntegrityTest.java`
  - 覆盖审计快照 migration 存在性检查。

## 测试最终回传

1. 普通角色菜单绑定正向成功：`COMMON_USER` 真实保存成功，`PUT /api/system/roles/3/menus` 返回 `200/code=0`，并已恢复。
2. `SUPER_ADMIN` 禁改成立：`PUT /api/system/roles/1/menus` 返回 `400/code=ROLE_MENU_SUPER_ADMIN_PROTECTED`。
3. 高危系统权限 diff 禁改成立：`PUT /api/system/roles/3/menus` 返回 `400/code=ROLE_MENU_HIGH_RISK_FORBIDDEN`。
4. 无权限账号不可提交成立：`demo_workflow_only` 对同一 PUT 写接口直探返回 `403 code=AUTH_FORBIDDEN`。
5. 审计快照成功/失败均落库：`sys_role_menu_audit_snapshot` 可查到 `role_id=3` 的成功快照与失败快照，以及 `role_id=1` 的失败快照。
6. 前端零改动成立：现有 `PermissionModal` 与 `message.error(...)` 足以承接后端拒绝语义。

## 非阻塞观察项

### OBS-001：`ROLE_MENU_SELF_EDIT_FORBIDDEN` 缺少 dev 运行态独立浏览器硬证

- 现象：测试最终回传中，`ROLE_MENU_SELF_EDIT_FORBIDDEN` 未提供 dev 运行态独立浏览器硬证。
- 已有证据：后端定向测试覆盖 `ROLE_MENU_SELF_EDIT_FORBIDDEN`，源码门禁位于 `SysRoleService.requireEditableRole`。
- 质量判断：非阻塞观察项。按当前主负责人分派口径，不应误判为 10C 首包阻塞。
- 后续建议：若后续做权限平台可视化验收矩阵，再补一个自持角色编辑拒绝的浏览器样本；当前不为此拆新修复包。

### OBS-002：当前工作区存在非本归档范围残留

- 现象：`git status --short` 显示除 10C 相关文件外，仍有 `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`、`backend/src/test/java/com/cgcpms/contract/CtContractServiceTest.java` 与 `output/` 残留。
- 质量判断：不影响本报告对 10C 首包的关闭建议，但这些对象不应被本归档顺手清理或裁决。
- 后续建议：由主负责人在工程卫生或对应业务线中单点裁决。

## 实际修改文件

本归档基于当前工作区与前置回传观察到的 10C 相关变更如下：

- `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java`
- `backend/src/main/java/com/cgcpms/system/entity/SysRoleMenuAuditSnapshot.java`
- `backend/src/main/java/com/cgcpms/system/mapper/SysRoleMenuAuditSnapshotMapper.java`
- `backend/src/main/java/com/cgcpms/system/service/SysRoleMenuAuditService.java`
- `backend/src/main/resources/db/migration/V112__create_role_menu_audit_snapshot.sql`
- `backend/src/main/resources/db/migration-h2/V112__create_role_menu_audit_snapshot.sql`
- `backend/src/test/java/com/cgcpms/system/SysRoleServiceTest.java`
- `backend/src/test/java/com/cgcpms/MigrationIntegrityTest.java`

本报告新增：

- `docs/quality/role-menu-binding-editing-closure-2026-07-01.md`

## 质量判断

收益：

- 用最小后端门禁收住角色菜单绑定写入口的高风险变更。
- 用独立快照表保留成功/失败前后菜单集合，满足恢复路径与审计追溯。
- 前端零改动，避免把首包扩大成权限平台 UI 改造。

成本：

- 增加一张审计快照表和少量后端服务代码。
- 失败审计使用 `REQUIRES_NEW`，后续若审计量上升，需要再评估容量与清理策略。

风险：

- `SELF_EDIT` 仅有后端定向测试证据，缺少 dev 浏览器硬证，当前按非阻塞观察项处理。
- 审计快照当前是恢复依据，不是一键回滚功能；恢复仍需通过现有角色菜单绑定链路回写。

## 最终建议

建议关闭 10C 首包。

关闭条件已满足：

- 角色菜单绑定正向保存通过。
- 三类运行态拒绝回传通过：`SUPER_ADMIN` 禁改、高危系统权限 diff 禁改、无权限账号不可提交。
- 审计快照成功/失败均落库。
- 前端零改动成立。
- 未新增权限事实源，未开放权限码本体编辑，未扩到 10B/10D。

剩余 `ROLE_MENU_SELF_EDIT_FORBIDDEN` 浏览器硬证缺口保留为非阻塞观察项，不阻塞本首包关闭。

## 归档状态

- 报告文件：`D:\projects-test\cgc-pms\docs\quality\role-menu-binding-editing-closure-2026-07-01.md`
- `docs/` 状态：`.gitignore` 第 84 行忽略 `docs/`，本报告属于本地归档产物。
- 本轮未运行测试、未重启服务、未提交 git。
