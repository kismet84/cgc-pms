# Iteration Report - 2026-07-09

Issue：ISSUE-005-007 列表页导出与批量操作权限态回归

目标：
- 回归核心列表页导出按钮、批量操作按钮和权限态展示。
- 不新增导出后端能力，不改变权限模型；不通过前端按钮隐藏替代后端权限校验。

修改范围摘要：
- `frontend-admin/src/pages/alert/index.vue`：新增预警列表管理/导出权限计算态，并在批量、行级写操作和导出函数入口加前端权限态 guard。
- `frontend-admin/src/pages/alert/components/AlertTablePanel.vue`：按权限态控制批量处理、标记已读、归档、导出和行级写操作入口展示。
- `frontend-admin/src/pages/alert/__tests__/index.test.ts`：补充无编辑/导出权限时入口隐藏的回归测试。
- `docs/quality/issue-005-007-list-export-batch-permission.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`：将 ISSUE-005-007 收口为 Done，并在 Ready 队列为空后拆出 5 个新 Ready Issue。

验证命令摘要：
- `cd frontend-admin; pnpm test:unit src/pages/alert/__tests__/index.test.ts -- --runInBand`：通过，`1` 个文件、`12` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

失败分类或非失败分类：非失败分类
是否自动合并：auto-merge/local-commit-only
是否推送：否
结论：通过
阻塞：无
剩余风险：
- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮只覆盖实际存在导出/批量入口的预警列表权限态；未新增导出后端能力。

---

Backlog 拆解：Ready 队列补充

来源：
- `docs/backlog/current-focus.md`
- `docs/backlog/cgc-pms-production-enhancement-plan.md` 第 `7.6 P1-3`、`7.7 P1-4`

新增 Ready Issue：
- `ISSUE-006-006`：文件上传大小与 MIME/扩展名校验回归。
- `ISSUE-006-007`：私有桶默认策略与公开 URL 禁用回归。
- `ISSUE-006-008`：文件下载临时链接过期与鉴权失败提示回归。
- `ISSUE-007-009`：JVM 与数据库连接池指标回归。
- `ISSUE-007-010`：备份清单脱敏与恢复演练报告模板回归。

拆解边界：
- 本轮只更新 backlog，不继续执行新拆出的业务任务。
- 新任务均禁止修改已应用 Flyway migration、生产凭据、外部平台配置和生产部署配置。
