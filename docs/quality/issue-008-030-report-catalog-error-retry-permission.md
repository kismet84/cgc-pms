# ISSUE-008-030 报表目录异常态、重试入口与权限过滤回归

## 结论

通过 / 非阻塞。

本 Issue 在既有报表目录页面补齐加载失败提示与重试入口，并以组件级测试覆盖失败后重试成功、权限过滤空态与 API-only fallback；不扩展报表定义、异步导出或权限模型。

## 范围

- 修改：`frontend-admin/src/pages/report/catalog.vue`、`frontend-admin/src/pages/report/__tests__/catalog.test.ts`
- 归档：`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/backlog/current-focus.md`、`docs/iterations/iteration-2026-07-10-report.md`、`.codex-autopilot/state.json`
- 未修改：后端、migration、deploy、依赖、报表设计器、异步导出任务、字段级权限模型、外部报表平台

## 验收证据

- `pnpm test:unit src/pages/report/__tests__/catalog.test.ts`：通过，`5 passed`
- `pnpm type-check`：exit 0
- `pnpm build`：exit 0
- 限定 `git diff --check`：exit 0
- E 审查：PASS，无阻塞

## 覆盖点

- 报表目录加载失败时显示稳定错误提示与重试按钮，复用既有 `fetchCatalog`；重试成功后清除错误态并恢复目录展示。
- 权限过滤后无可见报表时稳定展示 `当前暂无可见报表`，不泄露无权限入口。
- API-only fallback 继续保持不可点击，不被误导为可打开或可导出入口。

## 失败分类或非失败分类

非失败分类；报表目录异常态、重试入口与权限过滤回归已完成，D 验收与 E 审查通过。

## 剩余风险

- 当前证据为组件级单元测试，不是浏览器 E2E；该风险符合 Ready 边界，非阻塞。
- 本轮只完成既有报表目录入口治理，不代表完整报表中心、异步导出平台或外部报表平台已完成。
