# 第 13 条主线非阻塞剩余项状态矩阵（2026-07-03）

## 1. 结论

- 当前结论：主线 13 已通过，非阻塞剩余项仍未全部关闭。
- 当前判断：原 4 项剩余项中，`独立 project.spec.ts 补齐`、`真实写操作深度增强`、`提交边界收敛` 已关闭；当前仅剩 1 项未关闭，且仍依赖真实环境 / 现场结果。
- 当前建议：不再泛泛表述“还有一些尾项”，而聚焦唯一剩余项的现场确认结果。

## 2. 总状态矩阵

| 事项 | 当前状态 | 当前分类 | 当前依据 | 下一步 |
| --- | --- | --- | --- | --- |
| 项目经理标签内容区数据确认 | 未关闭 | 待现场确认 | 管理员权限前提已基本收敛，当前主要缺项目样本与接口真实返回证据 | 现场或环境执行后回填结果 |
| 独立 `project.spec.ts` 补齐 | 已关闭 | 已完成 | `frontend-admin/e2e/project.spec.ts` 已新增，并已通过 `pnpm exec playwright test e2e/project.spec.ts --project=chromium` 验证，结果 `1 passed (13.9s)` | 无需继续派工，后续仅随主线回归维护 |
| 真实写操作深度增强 | 已关闭 | 已完成 | 已补齐库存成功写入后的台账回读、合同保存后回读增强、合同失败场景，且现有 `c3-failure-path.spec.ts` 已提供失败路径基础 | 无需继续派工，后续仅随主线回归维护 |
| 提交边界收敛 | 已关闭 | 已完成 | 当前 `git status --short --untracked-files=all` 仅剩 `.serena` 删除项与本批前端收口改动；`.auth` 运行产物已清理，`frontend-admin/.gitignore` 已补 `e2e/.auth/admin-user.json`，工作区已不存在历史改动与本批收口改动混杂问题 | 无需继续作为非阻塞剩余项跟踪 |

## 3. 分项说明

### 3.1 项目经理标签内容区数据确认

- 当前状态：未关闭。
- 当前结论：非阻塞，但仍需要确认。
- 已有产物：
  - [`docs/superpowers/plans/2026-07-03-pm-dashboard-data-confirmation-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-pm-dashboard-data-confirmation-package.md:1)
- 已知事实：
  - 现有证据只证明标签可切换。
  - 管理员权限前提已基本收敛，不再是当前主风险。
  - 尚未证明当前项目、当前 `/dashboard/project-manager` 返回值与页面展示之间的真实关系。
- 关闭条件：
  - 至少留存一次真实权限、真实项目、真实接口返回证据，并能落入以下之一：
    - 正常空数据
    - 口径缺解释
    - 前端渲染问题
    - 后端 / 数据问题

### 3.2 独立 `project.spec.ts` 补齐

- 当前状态：已关闭。
- 当前结论：非阻塞项已完成，无需继续派工。
- 已有产物：
  - [`docs/superpowers/plans/2026-07-03-project-spec-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-project-spec-package.md:1)
- 已知事实：
  - `frontend-admin/e2e/project.spec.ts` 已新增。
  - 子任务已在 `frontend-admin` 下执行 `pnpm exec playwright test e2e/project.spec.ts --project=chromium`。
  - 验证结果为 `1 passed (13.9s)`。
- 关闭依据：
  - 独立项目链路 spec 已存在。
  - `项目列表 -> 项目总览` 最小独立链路已被单独验证。

### 3.3 真实写操作深度增强

- 当前状态：已关闭。
- 当前结论：非阻塞项已完成，无需继续派工。
- 已有产物：
  - [`docs/superpowers/plans/2026-07-03-real-write-depth-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-real-write-depth-package.md:1)
- 已知事实：
  - 审批链路已具备真实创建、提交、UI 操作、后端回读闭环。
  - `c3-failure-path.spec.ts` 已提供至少一个显式失败路径证据。
  - `invoice.spec.ts` 已提供发票写链路基础。
  - `frontend-admin/e2e/contract.spec.ts` 已增强，并已通过 `pnpm exec playwright test e2e/contract.spec.ts --grep "saves draft|created contract appears|created contract detail"` 定向验证，结果 3 条通过。
  - `frontend-admin/e2e/inventory.spec.ts` 已增强，并已通过 `pnpm exec playwright test e2e/inventory.spec.ts --grep "stock ledger reads back seeded stock-in transaction"` 定向验证，结果 1 条通过。
  - `frontend-admin/e2e/contract.spec.ts` 已新增失败场景：`blocks next step when required basic fields are missing`，并已通过 `pnpm exec playwright test e2e/contract.spec.ts --grep "blocks next step when required basic fields are missing"` 定向验证，结果 1/1 通过。
  - `pnpm exec tsc --noEmit --pretty false` 已通过。
  - `frontend-admin/e2e/c3-failure-path.spec.ts` 已提供至少一个显式失败路径证据：
    - 库存出库超额时，出现业务级反馈，而不是空白系统异常。
  - `frontend-admin/e2e/invoice.spec.ts` 已覆盖发票创建、列表回查、核验通过 / 异常核验等写链路基础。
- 关闭依据：
  - 已完成库存成功写入后的台账回读。
  - 已完成合同保存后回读增强。
  - 已完成合同失败场景补齐。
  - 已具备现有失败路径基础，不再属于“高风险写路径缺少最小失败 / 回读保护”的未关闭状态。

### 3.4 提交边界收敛

- 当前状态：已关闭。
- 当前结论：原始验收语义已满足，无需继续作为剩余项跟踪。
- 已有产物：
  - [`docs/superpowers/plans/2026-07-03-commit-boundary-judgement-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-commit-boundary-judgement-package.md:1)
- 已知事实：
  - 当前 `git status --short --untracked-files=all` 仅剩：
    - `D .serena/.gitignore`
    - `D .serena/project.yml`
    - `M frontend-admin/.gitignore`
    - `M frontend-admin/e2e/contract.spec.ts`
    - `M frontend-admin/e2e/inventory.spec.ts`
    - `?? frontend-admin/e2e/project.spec.ts`
  - `.auth` 运行产物已清理。
  - `frontend-admin/.gitignore` 已增加 `e2e/.auth/admin-user.json`，用于避免认证态未跟踪文件反复污染。
  - `1378ca9fc` 曾明确执行 `chore: untrack .serena from repo`
  - `1fbf937ae` 又将 `.serena/.gitignore` 与 `.serena/project.yml` 重新加入版本控制
  - [`docs/mcp-cleanup-record-2026-07-03.md`](D:/projects-test/cgc-pms/docs/mcp-cleanup-record-2026-07-03.md:131) 已明确记录：
    - 项目 `D:\projects-test\cgc-pms/.serena/` 目录应整体删除
    - 项目 `.serena` 已完全删除
- 关闭依据：
  - 当前工作区未提交改动已完成分批归类。
  - 当前剩余改动均为有意保留的本批收口内容，不再存在历史改动与上线准备批次混杂问题。
  - 原始待办的验收语义已满足，即使 `.serena` 删除项本身仍作为有意保留改动存在，也不再构成“提交边界未收敛”。

## 4. 优先级建议

1. 等待 `项目经理标签内容区数据确认` 的真实结果回填

## 5. 通过 / 不通过判断

### 通过

- 各剩余项都已从“模糊待办”收敛成“可执行 / 可验收 / 可回写”的状态。
- 当前优先级、边界、责任类型已经明确。

### 不通过

- 目标“完成可继续推进的非阻塞剩余项”尚未达成。
- 当前仍缺：
  - 现场确认结果

## 6. 主负责人结论

- 当前可以继续推进，但不能宣称已完成。
- 现阶段最值钱的动作不是再补分析，而是按本矩阵逐项拿执行结果和现场证据。
