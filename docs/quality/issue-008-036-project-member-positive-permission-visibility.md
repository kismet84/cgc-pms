# ISSUE-008-036 项目成员操作按钮权限正向显示回归验收

## 结论

- 结论：通过。
- 阻塞：非阻塞。
- 是否可上线：需要确认；本轮只补自动化回归证据，未发布生产。

## 验收范围

- 保留只读权限下新增、角色编辑、移除控件全部隐藏的既有断言。
- 新增参数化正向用例，分别证明 `project:member:add`、`project:member:edit`、`project:member:delete` 只显示对应操作入口。
- 未改页面、权限码、路由或后端。

## 验收证据

- `pnpm test:unit src/pages/project/__tests__/members.test.ts`：4/4 通过。
- `pnpm type-check`：通过。
- `git diff --check`：通过。

## 剩余风险

- 未执行真实低权限账号浏览器点击；组件渲染断言已覆盖本轮目标，该项不阻塞本地回归证明收口。
