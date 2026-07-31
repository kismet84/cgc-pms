# ISSUE-037-021 CI/CD 与上线门禁 v1.5 复验报告

> 状态提示：本报告保存 2026-07-12 首次失败快照。第40条主线 M0 已于 2026-07-13 以 PR #334 合并、11 个 required checks 全绿和当前分支保护 API 将原三条阻塞裁决为 `VerifiedResolved`；当前状态见 [M0 验收报告](mainline-40-m0-historical-blocker-normalization-acceptance-2026-07-13.md)。

## 裁决

- 核验时间：2026-07-12 15:52:44 +08:00
- 执行分支/commit：`codex/autopilot/issue-037-021` / `1aeb90c8f3fbfe0e4233b79232f3831258dcc7d7`
- 目标分支/commit：`master` / `781b41661cd96b2a2f7eed825f98ff3d9bdf137b`
- 结论：**不通过 / 阻塞 / 不可上线**。
- 依据：目标 commit 的 11 个 required checks 中 `frontend-lint`、`frontend-test`、`e2e` 为 failure；`enforce_admins=false` 允许管理员绕过保护，push restrictions 未启用则表示未限制可推送主体。
- Reviewer 状态：**已完成**。2026-07-12 16:14 +08:00 独立 Reviewer 复读 branch protection、required checks、run/job/step、失败 annotation、E2E failure artifact 与 workflow job 定义，确认本报告裁决和失败分类成立。
- 边界：本 Issue 只读核验并归档，不修业务代码、workflow 或远端设置，不 rerun、不提交、不 push、不发布。

## 当前远端事实

### Runs

| 范围 | Run | Commit | 时间（UTC） | 结论 |
| --- | --- | --- | --- | --- |
| 最新 master push | [29146534529](https://github.com/kismet84/cgc-pms/actions/runs/29146534529) | `781b41661cd96b2a2f7eed825f98ff3d9bdf137b` | 2026-07-11 08:42:04 | failure |
| 最新 PR | [28922967487](https://github.com/kismet84/cgc-pms/actions/runs/28922967487) | `ece3a307fad2997832cb5469f0228ed9dc349dbc` | 2026-07-08 06:40:27 | success |

最新 PR 绿灯早于当前 master commit，不能替代 master 上线门禁。master 最近 20 个可见 CI push runs 均为 failure，红灯不是单次瞬时波动。

### Required checks 与 workflow 对应

`GET /repos/kismet84/cgc-pms/branches/master/protection` 返回 `strict=true`，11 个 context 均绑定 GitHub Actions app `15368`。现行 `.github/workflows/ci.yml` 同时监听 `push` / `pull_request` 的 `master, main`，11 个 required job 名称全部存在且可触发；另有非 required 的 `build-summary`，没有 required 名称缺失或多余 required context。

| Required check | 最新目标 run | 失败 step / job URL |
| --- | --- | --- |
| backend-test | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960016) |
| backend-test-mysql | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960007) |
| backend-dependency-scan | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960018) |
| frontend-lint | **failure** | `Lint frontend` / [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960025) |
| type-check | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960020) |
| frontend-build | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960008) |
| frontend-test | **failure** | `Install & Test with coverage` / [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960031) |
| frontend-dependency-audit | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960009) |
| sql-safety-scan | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86528960030) |
| e2e | **failure** | `Run E2E tests` / [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86529472248) |
| supply-chain-security | success | [job](https://github.com/kismet84/cgc-pms/actions/runs/29146534529/job/86529475604) |

### 分支保护

| 配置 | 当前 API 证据 | 裁决 |
| --- | --- | --- |
| strict | `true` | 符合 |
| required checks | 上述 11 项 | 与 workflow 一一对应 |
| enforce_admins | `false` | **阻塞：管理员可绕过** |
| PR review | 1 人；`dismiss_stale_reviews=false`、`require_code_owner_reviews=false`、`require_last_push_approval=false` | 已启用但强度有限 |
| push restrictions | API 返回 `404 Push restrictions not enabled` | **治理缺口：未限制可推送主体；管理员绕过的直接依据仍是 `enforce_admins=false`** |
| force push / deletion | 均为 `false` | 符合 |
| conversation resolution / linear history / signatures | 均未强制 | 当前非 required 红灯根因；保留治理观察 |

## 红灯分类与解除条件

| 失败任务 | 失败分类 | 关键证据与复现 | 责任域 | 解除条件 / 最小安全恢复 |
| --- | --- | --- | --- | --- |
| frontend-lint | 真实质量类 | 远端制品 `lint-check.txt`：`stock.vue:45-46` 的 `fetchKpi`、`fetchWarehouses` 未使用，2 errors / 331 warnings；本地 `pnpm lint:check` 同样 2 errors（当前分支 414 warnings） | 前端实现 | 单独 Ready 删除/使用死变量并复跑 lint；不放宽规则、不改 workflow |
| frontend-test | 真实质量类 | 远端 `ContractLedgerPage-ui-consistency.test.ts:45` 期望 `title: '签订日期'` 未命中；本地 coverage 进一步稳定得到 3 failed / 502 passed | 前端实现/测试 | 对齐当前页面与真实契约后复跑 coverage；不得简单删除断言掩盖回归 |
| e2e | 真实质量类 | `ui-refactor-smoke.spec.ts:78` 的“付款申请页 -- KPI 卡片可见”在原始运行、retry1、retry2 均失败：`.ant-table, .vxe-table` 5 秒内不存在；页面快照已进入付款申请路由 | 前端实现/E2E | 独立 Ready 核对页面应展示表格还是用例契约已过期，修复后目标 commit 远端 e2e success |
| Actions Node 20 deprecated 警告 | 工具配置类、当前非阻塞 | 多个 job annotation 显示 action 被强制使用 Node 24；不是本次三个失败 step 的退出原因 | CI 工具链 | 依赖升级需另行授权和验证，本 Issue 不升级 action |
| enforce_admins / push restrictions | 工具配置/治理类、阻塞 | API 当前为 `false` / 未启用；前者允许管理员绕过，后者表示无推送主体白名单 | 仓库管理员 | 明确授权后收紧保护并再次读取 API；保留最小回滚为恢复原设置 |

e2e 的三次尝试已经构成稳定复现，因此未在比 master 超前 51 个提交的当前 Issue 工作树启动 Docker/浏览器重复跑；这避免把不同代码基线的结果冒充目标 commit 证据。

## 本地只读复验

本地 HEAD 以目标 master commit 为祖先，ahead 51 / behind 0；以下结果只说明当前 Issue 工作树 HEAD 状态，远端上线裁决仍以目标 master required checks 为准。

| 命令 | 结果 | 分类/摘要 |
| --- | --- | --- |
| `ready-lint.ps1 -IssueTitle ISSUE-037-021` | pass | Ready 字段与 hash 合法 |
| `backend/.\mvnw.cmd verify` | fail | 1506 tests：1 failure、217 errors；217 errors 共享 144-bit 测试 JWT 弱密钥环境前置，另有 `FundAccountMapperTest... expected 0 but was 3` 真实权限断言失败 |
| `pnpm lint:check` | fail | 2 errors / 414 warnings，复现远端未使用变量 |
| `pnpm type-check` | pass | exit 0 |
| `pnpm build` | pass | Vite built in 49.38s |
| `pnpm test:coverage` | fail | 3 failed / 502 passed：SidebarMenu、ContractLedger、DashboardReferenceFidelity 契约断言 |
| `pnpm audit --audit-level high` | pass | 1 moderate，低于 high 阻断阈值；首次批处理参数错误已按原命令复跑，不计为项目失败 |
| `check-sql-safety.ps1` | fail | 当前分支两处固定字面量 `.apply("1 = 0")` 未带 marker；属于扫描规则/豁免配置缺口，不是已确认 SQL 注入，目标 master 对应 check 为 success |
| `git diff --check` | pass | exit 0 |

## 未解除项与回滚

- 远端三个 required 红灯按前端 lint/test 与 e2e 责任域进入 backlog 阻塞记录；修复必须生成新的目标 commit 并让 11 项 required checks 全绿。
- 分支保护绕过风险进入仓库治理阻塞记录；远端设置变更必须另获授权。
- 当前 Issue 工作树的后端测试前置、权限断言及 SQL scan marker 缺口进入 Current Focus，不改变本报告对 master 的红灯分类。
- 独立 Reviewer 已复核 API、run/job/step、失败关键词和本地摘要；Reviewer 通过只代表本报告证据合格，不代表上线门禁通过。
- 最小回滚：仅删除/回退本 Issue 的报告与 backlog/current-focus 文档差异；无代码、workflow、数据库、远端设置或运行态回滚。
