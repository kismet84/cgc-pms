# ISSUE-032-008 覆盖率与 E2E CI 基线复验报告

日期：2026-07-09
Issue：ISSUE-032-008 覆盖率与 E2E CI 基线复验
类型：测试治理 / CI 基线复验
结论：通过（基线事实已归档）/ 非阻塞

本轮只复验“声明阈值”“当前测量是否已复验”“CI 实际执行/阻断”三层事实，不提高覆盖率阈值，不修改 CI，不把旧报告数字当作当前验证结论。

## 1. 执行边界

允许修改范围：

- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`

本轮未执行会写入 `backend/target`、`frontend-admin/coverage*`、`frontend-admin/playwright-report` 或 `frontend-admin/test-results` 的覆盖率 / E2E 命令，因为这些命令会产生后端或前端目录产物，超出本 Ready Issue 的允许修改范围。

因此，本报告对“当前测量值”的裁决分为两类：

- 配置阈值与 CI 阻断：已复验。
- 覆盖率数值重新测量：未在本轮复验；只记录既有残留产物快照，不作为当前新基线。

## 2. 历史报告有效性

`docs/quality/quality-hardening-acceptance.md` 只能作为历史线索，不能直接作为当前结论。

原因：

- 该文档前段写“后端 JaCoCo 0.80/0.70 会失败”“前端 CI 用 `|| true` 忽略失败”“E2E job 不存在”。
- 同一文档后段又写“E2E job 已新增”“前端软门禁已移除”“覆盖率门禁已对齐基线”。
- 两组表述互相冲突，必须以当前 checkout 配置和远端 CI 事实复验为准。

## 3. 后端覆盖率基线

| 层次 | 当前事实 | 结论 |
|---|---|---|
| 声明阈值 | `backend/pom.xml` 的 JaCoCo `jacoco-check` 绑定在 `verify` 阶段；`INSTRUCTION >= 0.73`，`BRANCH >= 0.53`。注释明确为“当前基线 73/53，随覆盖率提升恢复 80/70”。 | 已复验 |
| 当前测量 | 本轮未重新执行 `cd backend && ./mvnw -C verify` 或等价覆盖率命令。工作区存在既有残留 `backend/target/site/jacoco/jacoco.csv`，LastWriteTime 为 `2026-07-09 18:29:52`，汇总快照为 instruction `82.09%`、branch `60.57%`。该文件不是本轮生成，不作为新基线。 | 未完成本轮新测量 |
| CI 实际执行 | `.github/workflows/ci.yml` 的 `backend-test` 执行 `cd backend && ./mvnw -C verify`，会触发 JaCoCo check。最新可读远端 run `29003031520` 中 `backend-test` 在 `Run backend tests with coverage` 步骤失败，且分支保护 required checks 包含 `backend-test`。 | 真阻断已确认 |

分类：

- CI 是否真阻断：是。
- 当前失败是否直接等于“业务代码失败”：不能直接下结论。当前只确认 `backend-test` job 在覆盖率/测试步骤失败；失败细因需要下载或查看 surefire / coverage artifact 后再分诊。
- 本轮不得把阈值从 `73/53` 提高到 `80/70`。

## 4. 前端覆盖率基线

| 层次 | 当前事实 | 结论 |
|---|---|---|
| 声明阈值 | `frontend-admin/vitest.config.ts` 的 coverage thresholds 为 lines `10`、functions `8`、branches `10`、statements `10`；注释写明当前基线约 lines `9.68%`、functions `7.63%`、branches `10%`、statements `9.61%`，目标逐步提升到 `55/45/40/55`。 | 已复验 |
| 当前测量 | 本轮未重新执行 `pnpm test:coverage`。工作区存在既有残留 `frontend-admin/coverage/coverage-summary.json`，LastWriteTime 为 `2026-07-08 03:31:27`，快照为 lines `16.85%`、functions `13.41%`、branches `16.89%`、statements `16.55%`。该文件不是本轮生成，不作为新基线。 | 未完成本轮新测量 |
| CI 实际执行 | `.github/workflows/ci.yml` 的 `frontend-test` 执行 `cd frontend-admin && pnpm install --frozen-lockfile && pnpm test:coverage`，设置 `COVERAGE_DIR=coverage-ci`，没有 `|| true`。最新可读远端 run `29003031520` 中 `frontend-test` 在 `Install & Test with coverage` 步骤失败，且分支保护 required checks 包含 `frontend-test`。 | 真阻断已确认 |

分类：

- CI 是否真阻断：是。
- 当前失败是否直接等于“业务代码失败”：不能直接下结论。当前只确认 `frontend-test` job 在覆盖率测试步骤失败；最新 run artifact 列表未出现 `frontend-coverage-*` artifact，需要后续下载 job 日志或复跑覆盖率后再分清是单测失败、覆盖率阈值失败还是工具配置问题。
- 本轮不得把阈值提高到历史目标 `55/45/40/55`。

## 5. E2E CI 基线

| 层次 | 当前事实 | 结论 |
|---|---|---|
| 声明范围 | `frontend-admin/package.json` 中 `test:e2e:ui` 为 `playwright test e2e/ui-refactor-smoke.spec.ts`。仓库当前共有 `23` 个 `frontend-admin/e2e/**/*.spec.ts` 文件，raw `test(` 调用数为 `86`；但 CI 只执行 `ui-refactor-smoke.spec.ts`。 | 已复验 |
| 当前测量 | 本轮未重新执行 Playwright。未产生新的 `playwright-report` 或 `test-results`。 | 未完成本轮新测量 |
| CI 实际执行 | `.github/workflows/ci.yml` 存在 `e2e` job，依赖 `backend-test-mysql` 与 `frontend-build`，启动 MySQL、Redis、MinIO、后端和前端 preview 后执行 `pnpm test:e2e:ui`。最新可读远端 run `29003031520` 中 `e2e` 的环境启动步骤成功，`Run E2E tests` 步骤失败；分支保护 required checks 包含 `e2e`。 | 真阻断已确认 |

分类：

- CI 是否真阻断：是。
- 当前失败分类：E2E job 已执行到测试步骤，环境前置大体可达；但未下载 `e2e-failure-evidence-*` artifact，不能直接判定为业务代码失败、UI 回归还是测试脚本问题。
- 当前 E2E CI 范围不是全量 23 个 spec，而是 `ui-refactor-smoke.spec.ts` 单一烟测入口。

## 6. CI 与分支保护事实

当前可读远端事实：

- workflow：`.github/workflows/ci.yml`
- 最新可读 run：`29003031520`
- run 标题：`Refine backlog automation and quality reporting`
- event：`push`
- head SHA：`61d4229f85ba6023e03ec197e03eaa277bdd27f1`
- 状态：`completed`
- 结论：`failure`
- URL：`https://github.com/kismet84/cgc-pms/actions/runs/29003031520`

最新 run 的关键 job 结果：

| Job | 结果 | 本轮分类 |
|---|---|---|
| `backend-test` | failure | CI 真阻断；细因待 surefire / coverage 证据分诊 |
| `frontend-test` | failure | CI 真阻断；细因待 coverage / test 日志分诊 |
| `e2e` | failure | CI 真阻断；环境启动成功后测试步骤失败，细因待 E2E artifact 分诊 |
| `frontend-lint` | failure | 与本 Issue 非直接目标，但同属 required check 红灯 |
| `backend-test-mysql` | success | 非阻断 |
| `frontend-build` | success | 非阻断 |
| `type-check` | success | 非阻断 |
| `backend-dependency-scan` | success | 非阻断 |
| `frontend-dependency-audit` | success | 非阻断 |
| `sql-safety-scan` | success | 非阻断 |
| `supply-chain-security` | skipped | 受上游失败影响 |

分支保护 required checks 当前包含：

- `backend-test`
- `backend-test-mysql`
- `backend-dependency-scan`
- `frontend-lint`
- `type-check`
- `frontend-build`
- `frontend-test`
- `frontend-dependency-audit`
- `sql-safety-scan`
- `e2e`
- `supply-chain-security`

`strict=true`，因此上述 required check 红灯会阻断合并。

## 7. 最小裁决

| 类别 | 当前测量是否已复验 | 配置阈值是什么 | CI 是否真阻断 | 裁决 |
|---|---|---|---|---|
| 后端覆盖率 | 未本轮重跑；只发现非本轮残留快照 `82.09% / 60.57%` | JaCoCo `INSTRUCTION >= 0.73`，`BRANCH >= 0.53` | 是，`backend-test` required check 失败 | 先保持当前阈值，不提到 `80/70` |
| 前端覆盖率 | 未本轮重跑；只发现非本轮残留快照 `16.85 / 13.41 / 16.89 / 16.55` | Vitest lines `10`、functions `8`、branches `10`、statements `10` | 是，`frontend-test` required check 失败 | 先保持当前阈值，不提到 `55/45/40/55` |
| E2E CI | 未本轮重跑；当前仅核对 spec 与 CI 入口 | 无覆盖率阈值；CI 入口为 `ui-refactor-smoke.spec.ts` | 是，`e2e` required check 失败 | 先分诊失败 artifact，不扩大为全量 E2E 改造 |

## 8. 后续建议

1. 后续如需形成“新覆盖率基线”，应单独授权运行覆盖率命令，并允许产生 `backend/target`、`frontend-admin/coverage-ci` 或临时 artifact 下载目录。
2. 下一步分诊优先下载或查看最新 run 的 `backend-test-reports-*`、`e2e-failure-evidence-*` 与 job log，先区分工具配置类、环境前置类、真实质量类。
3. 不建议在当前失败未分诊前提高覆盖率阈值或扩大 E2E 范围；这会把基线复验变成硬门禁整改，超出本 Issue 目标。

## 9. 验收证据

- `git branch --show-current`：`master`
- `git status --short`：工作区已有多处既有改动；本轮只新增/修改允许范围内文档。
- `.codex-autopilot/stop.flag`：absent
- `.codex-autopilot/pause.flag`：absent
- `.codex-autopilot/enabled.flag`：present
- `gh run list --workflow ci.yml --branch master --limit 5`：最新 5 次 `ci.yml` run 均为 failure。
- `gh run view 29003031520 --json jobs,...`：确认 `backend-test`、`frontend-test`、`e2e` 失败。
- `gh api repos/kismet84/cgc-pms/branches/master/protection/required_status_checks`：确认 required checks 与 `strict=true`。

最终结论：ISSUE-032-008 的“基线事实复验与归档”已完成；阻塞项不是本报告本身，而是远端 required checks 当前红灯仍需后续专项分诊。
