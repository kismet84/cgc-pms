# 全量审计：架构

## 结论

**通过，存在演进边界风险。评分 85/100。** 分层、租户上下文、权限、工作流、资金一致性边界清楚；Legacy 与 Clean-room V2 并行是受控迁移架构，不应误判为重复实现已完成。

## 证据

- 代码图谱显示 773 条路由，核心高扇入集中在 `ApiResponse.success`、`UserContext.getCurrentTenantId`、`ProjectAccessChecker.checkAccess`、审计事件构建器，说明共享横切能力已形成。
- `frontend-admin-v2/src/router.ts:43-67` 由导航目录生成路由；驾驶舱、报表、审批工作台为真实页面，其余导航路由回退 `ShellPlaceholderPage`。
- `frontend-admin-v2/src/router.ts:69-118` 的对象详情深链仍有 6 个显式占位入口。
- 后端权限、租户、项目访问未依赖客户端角色名；V2 角色视图是展示层，不替代服务端裁决。

## 风险

- `BIZ-001`（P1）：V2 仍非 Legacy 全量替代品。流程模板、多个工作区及对象详情仍未迁移；只能声明 M2/P0 范围完成。
- `CODE-001`（P3）：`PayApplicationService` 当前 707 行，源码已标记拆分 TODO；维护耦合偏高，但现有验证未显示功能故障。

## 建议

保持“双前端、单后端权威契约”路线；每个 V2 切片以路由账本、权限、真实角色与回归证据独立关闭，禁止一次性重写。
