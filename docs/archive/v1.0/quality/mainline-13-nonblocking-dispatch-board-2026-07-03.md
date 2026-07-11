# 第 13 条主线非阻塞剩余项派工板（2026-07-03）

## 1. 结论

- 当前结论：原 4 项非阻塞剩余项中，`独立 project.spec.ts 补齐`、`真实写操作深度增强`、`提交边界收敛` 已完成；当前仅剩 1 项仍保留派工价值。
- 当前建议：后续执行必须按本派工板显式指定 `model` 与 `thinking`，不得口头化派工。
- 使用原则：
  - 主线程只负责任务下发、边界控制、结果验收。
  - 工程改动类任务必须由子智能体执行。
  - 现场 / 环境确认类任务由 QA / 验证执行人先跑，只有升级条件满足时再转工程子智能体。

## 2. 派工总览

| 事项 | 执行类型 | 建议执行角色 | model | thinking | 所有权 | 产物 |
| --- | --- | --- | --- | --- | --- | --- |
| 提交边界收敛 | 已完成 | 无需继续派工 | `gpt-5.4-mini` | `low` | 工作区改动边界已收敛 | 验收结论已落地 |
| 独立 `project.spec.ts` 补齐 | 已完成 | 无需继续派工 | `gpt-5.4` | `medium` | `frontend-admin/e2e/project.spec.ts` | 独立 spec + 验证结果已落地 |
| 真实写操作深度增强 | 已完成 | 无需继续派工 | `gpt-5.4` | `medium` | `frontend-admin/e2e/inventory.spec.ts` / `contract.spec.ts` 等增强已落地 | 增强 spec + 验证结果已落地 |
| 项目经理标签内容区数据确认 | 现场确认 | QA 验证执行人 | `gpt-5.4-mini` | `low` | 浏览器、接口、环境证据 | 权限 / 项目 / 接口 / 口径确认记录 |

## 3. 分项派工卡

### 3.1 提交边界收敛

- 派工类型：已完成。
- 建议执行角色：无需继续派工
- `model`：`gpt-5.4-mini`
- `thinking`：`low`
- 所有权：
  - 工作区改动边界判定与相关文档表述
- 输入依据：
  - [`docs/superpowers/plans/2026-07-03-commit-boundary-judgement-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-commit-boundary-judgement-package.md:1)
- 已完成事实：
  - 当前 `git status --short --untracked-files=all` 仅剩 `.serena` 删除项与本批前端收口改动。
  - `.auth` 运行产物已清理，`frontend-admin/.gitignore` 已补认证态忽略规则。
  - 当前工作区已不存在历史改动与本批收口改动混杂问题。
- 后续口径：
  - 不再作为待派工项保留；后续仅随常规收口维护。

### 3.2 独立 `project.spec.ts` 补齐

- 派工类型：已完成。
- 建议执行角色：无需继续派工
- `model`：`gpt-5.4`
- `thinking`：`medium`
- 所有权：
  - `frontend-admin/e2e/project.spec.ts`
- 输入依据：
  - [`docs/superpowers/plans/2026-07-03-project-spec-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-project-spec-package.md:1)
- 已完成事实：
  - `frontend-admin/e2e/project.spec.ts` 已新增。
  - 已执行 `pnpm exec playwright test e2e/project.spec.ts --project=chromium`。
  - 结果 `1 passed (13.9s)`。
- 后续口径：
  - 不再作为待派工项保留；后续仅随常规回归维护。

### 3.3 真实写操作深度增强

- 派工类型：已完成。
- 建议执行角色：无需继续派工
- `model`：`gpt-5.4`
- `thinking`：`medium`
- 所有权：
  - `frontend-admin/e2e/inventory.spec.ts`
  - `frontend-admin/e2e/contract.spec.ts`
- 输入依据：
  - [`docs/superpowers/plans/2026-07-03-real-write-depth-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-real-write-depth-package.md:1)
- 已完成事实：
  - `inventory.spec.ts` 已补库存台账回读并定向验证通过。
  - `contract.spec.ts` 已补保存后回读增强并定向验证通过。
  - `contract.spec.ts` 已补失败场景 `blocks next step when required basic fields are missing`，并定向验证 1/1 通过。
  - `pnpm exec tsc --noEmit --pretty false` 已通过。
- 后续口径：
  - 不再作为待派工项保留；后续仅随常规回归维护。

### 3.4 项目经理标签内容区数据确认

- 派工类型：现场确认 / 环境确认。
- 建议执行角色：QA 验证执行人
- `model`：`gpt-5.4-mini`
- `thinking`：`low`
- 所有权：
  - 当前登录账号、项目选择器、`/dashboard/project-manager` 返回证据、判定记录
- 输入依据：
  - [`docs/superpowers/plans/2026-07-03-pm-dashboard-data-confirmation-package.md`](D:/projects-test/cgc-pms/docs/superpowers/plans/2026-07-03-pm-dashboard-data-confirmation-package.md:1)
- 任务目标：
  - 先拿权限、项目、接口、口径四层证据
  - 仅在升级条件满足时再转前端或后端工程子智能体
- 升级条件：
  - 接口有非空数据但页面不显示 -> 转前端工程执行子智能体
  - 接口异常、字段缺失或权限口径不一致 -> 转后端工程执行子智能体
- 禁止事项：
  - 不在缺证据情况下直接报前端 bug
  - 不拿其他环境经验替代当前环境证据

## 4. 推荐执行顺序

1. `项目经理标签内容区数据确认`

## 5. 通过 / 不通过

### 通过

- 当前剩余项都能以显式 `model`、`thinking`、所有权、产物和验收标准下发执行。
- 后续执行不再依赖口头补充上下文。

### 不通过

- 总目标“完成可继续推进的非阻塞剩余项”仍未完成。
- 当前只是把剩余派工与验收边界压实，尚未拿到现场确认结果。
