# ISSUE-005-007 列表页导出与批量操作权限态回归

完成日期：2026-07-09

## 结论

通过。

本轮只回归前端权限态，不新增导出后端能力，不改变后端权限模型。预警列表批量处理、批量标记已读、批量归档、行级标记/处理/归档统一受 `alert:edit` 或管理员角色控制；当前视图 CSV 导出受 `alert:view` 或管理员角色控制。后端批量读/状态接口仍由 `@PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")` 兜底。

## 修改范围

- `frontend-admin/src/pages/alert/index.vue`
  - 新增 `canManageAlerts`、`canExportAlerts` 计算态。
  - 批量、行级写操作和前端导出函数增加权限态 guard。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`
  - 新增权限态 props。
  - 无权限时隐藏批量处理、标记已读、归档、导出和行级写操作入口。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`
  - 补充无编辑/导出权限下工具栏和行级写操作入口隐藏的单元测试。

## 验收证据

- `cd frontend-admin; pnpm test:unit src/pages/alert/__tests__/index.test.ts -- --runInBand`：通过，1 个文件 12 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 权限边界

- 覆盖入口：预警列表导出、批量处理、批量标记已读、批量归档、行级标记已读、行级处理、行级归档。
- 无权限：前端入口不可见，函数级 guard 不触发前端请求。
- 有权限：`alert:edit` 或管理员角色保留批量/行级写操作；`alert:view` 或管理员角色保留当前视图 CSV 导出。
- 边界说明：前端权限态只负责 UI 与交互回归，不替代后端接口鉴权；后端权限校验未修改。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于单元测试、类型检查、构建和代码审查。
- 其他列表页未发现实际导出/批量入口，本轮未新增占位按钮或后端导出能力。
