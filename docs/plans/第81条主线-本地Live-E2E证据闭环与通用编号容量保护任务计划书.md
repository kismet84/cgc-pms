# 第81条主线：本地 Live E2E 证据闭环与通用编号容量保护

**Goal:** 修正 `CGC-AUD-20260808-001` 的证据语义漂移，建立可重复、绑定当前 SHA 的本地 9 组 live E2E 验收入口；修复 `CGC-AUD-20260808-002` 中通用三位日序号在 `999` 后生成不可解析 `1000` 的确定性缺陷，并覆盖全部现有调用方。 **Architecture:** 复用 `complete-project-v2` 本地演示数据包、Playwright `liveSpecs` 显式清单、现有 contract CI 分层、`CodeGenerationService` 与既有 `BUSINESS_CODE_SEQUENCE_EXHAUSTED` 错误契约；增加一个本地 live 编排入口和一个公共容量守卫。不得把 live 搬入 GitHub required CI，不新建 nightly/release/生产流程、序列表、编号宽度、第二编号器或数据库 migration。

> 编制日期：2026-08-08
> 计划状态：`IMPLEMENTED / G0-G5_LOCAL_PASSED / GIT_DELIVERY_AUTHORIZED_PENDING`
> 唯一问题源：`ISSUE-081-001`
> 审计来源：`CGC-AUD-20260808-001`、`CGC-AUD-20260808-002`
> 编制基线：`master@367d110e6e66dfbf3248d7b00299c98ebd526603`，编制前工作区干净
> 环境边界：仅本地 dev/test/demo 与既有 GitHub Actions；生产、目标环境、发布演练不适用
> 授权边界：用户已授权完整实施、本地验证和受保护Git交付；不授权生产、非本地环境、强推或绕过保护

## 1. 审计裁决

| 审计项 | 裁决 | 证据与影响 |
| --- | --- | --- |
| `CGC-AUD-20260808-001` | 部分成立，P2、非现有 required CI 阻塞 | required `e2e` 只运行 27 个 contract spec 是第80条有意分层；真实缺口是9组本地 live 未形成当前 SHA 证据，但第80条载体仍写 `G0-G5_PASSED`，且 pre-push 的 `full` 实际仍只运行 contract |
| `CGC-AUD-20260808-002` | 成立，P2、数据一致性风险 | 通用生成器固定解析3位序号，却允许格式化 `1000`；随后字典序最大值和解析失败会反复尝试同一非法编号。影响投标成本、合同收入、单据模板、项目预算、合同和付款记录6个直接调用方 |
| “缺少生产迁移、备份、发布和回滚证明” | 不适用，关闭 | 项目根规则明确仅有本地环境；不得据此制造生产阻塞项或整改任务 |
| 其余未验证风险 | 证据不足，关闭 | 未形成可复现缺陷、唯一价值或验收标准，不进入 Backlog |

结论：今日报告的“两项 P2 均为生产阻塞、系统不具备生产就绪条件”不适用于本项目。第81条只承接已核实的本地证据语义和编号容量缺陷。

## 2. 范围、非目标与不变量

### 范围

1. 为9个 `liveSpecs` 建立一个 fail-fast 的本地统一入口，证明真实 frontend/backend/API/权限/状态机。
2. 更正 `full`/`e2e:full` 等会让 contract-only 被误读为 live/full 的名称和报告语义。
3. 在 `CodeGenerationService` 单点保护 `001..999` 容量，覆盖普通查询、软删除查询、offset 和全部解析路径。
4. 对6个直接调用方建立影响清单和代表性回归；以新质量报告重新裁决 G0～G5。

### 非目标

- 不把 live E2E 加入 GitHub required CI；不启动 backend、MySQL、Redis、MinIO 供 contract job 使用。
- 不新增定时、nightly、release candidate、生产或目标环境工作流。
- 不扩大编号为4位，不建序列表、Redis计数器或新锁，不修改数据库 schema。
- 不重写或替换 `BusinessCodeGenerator`，不为6个调用方分别复制容量判断。
- 不改写第80条历史计划正文；只在现行载体和第81条质量报告中说明证据边界。

### 不变量

- contract CI 继续只证明前端路由、DOM、交互、A11y 和 mock API 契约；必须0 skipped。
- live 仅允许 loopback、本地专用 `cgc_pms_demo_v2`，数据库重载必须同时满足 dev/test/demo、`127.0.0.1` 和 `.codex-autopilot/ALLOW_TEST_DATA_RESET`。
- backend 必须被客观证明实际连接上述专用库；缺任何前置即 fail-closed，不自动改写配置或重置其他数据库。
- 业务编号保持 `{PREFIX}-{yyyyMMdd}-{3位序号}`；同前缀同日达到999后统一失败为 `BUSINESS_CODE_SEQUENCE_EXHAUSTED`。
- 租户隔离、软删除历史占号、跨日归零、现有唯一约束和调用方重试语义不得退化。

## 3. 目标架构与文件边界

```text
GitHub required browser-contract ── 27 contract specs，静态 preview，0 skip

本地 verify-live-all.ps1
├─ 安全前置：loopback + marker + 专用库 + backend实际库
├─ 复用 complete-project-v2 load/verify
├─ 精确设置9组 V2_LIVE_* 并按 liveSpecs 执行
└─ SHA/URL/DB/租户/用户/Playwright结果证据

6个业务调用方
└─ CodeGenerationService
   ├─ 统一解析并验证 001..999
   ├─ offset 后再次验证
   └─ >999 统一 BUSINESS_CODE_SEQUENCE_EXHAUSTED
```

预期文件边界：

- 本地 live：`scripts/demo/complete-project-v2/verify-live-all.ps1`、既有 `load.ps1`/`verify.ps1`/`verify-m3-live.ps1`、`frontend-admin-v2/package.json`。
- 门禁语义：`frontend-admin-v2/scripts/e2e-spec-groups.mjs`、`run-push-quality-gate.mjs` 及其测试、`scripts/ci/test-workflow-contract.ps1`、`docs/standards/09-测试规范.md`。
- 编号容量：`backend/src/main/java/com/cgcpms/common/util/CodeGenerationService.java`、`ContractRevenueMapper`/`ContractRevenueService` 的软删除占号适配、新增/相关测试；其余五个调用方原则上不改生产逻辑。
- 治理：本计划、`docs/backlog/current-issues.json`、`current-focus.md`、`project-map.md`、实施后的新质量报告。

实施前 code map 门：当前 `docs/codemap/codemap.lock` 不绑定编制 HEAD。任何代码修改前必须先重新生成并核对 `docs/codemap/codemap.html`、`codemap.json`、`codemap.lock`，回答调用方、影响面和测试覆盖；边界改变时与代码同批更新。

## 4. 阶段与任务

### G0：基线与事实冻结

1. 重新核对分支、HEAD、工作区归属、code map 新鲜度和任务文件范围。
2. 冻结27 contract、9 live、1 special 清单；记录每个 live spec 的开关、数据 ID、角色和写入/清理行为。
3. 冻结6个编号调用方：`BidCostService`、`ContractRevenueService`、`DocumentTemplateService`、`ProjectBudgetService`、`CtContractService`、`PayRecordService`。
4. 建立第81条质量报告骨架，旧报告和旧计划只作历史证据，不当作当前通过证明。

通过条件：基线与编制 HEAD 一致；无同名 Ready/Issue；code map 已刷新；文件所有权明确。否则保持 `G0_PENDING`。

### G1：契约冻结与最小测试先行

1. 冻结 live 前置、执行清单、0 skip 和证据字段；测试统一入口与 `liveSpecs` 完全一致。
2. 冻结编号容量契约：`001`、`009`、`099`、`998→999`、`999→exhausted`、遗留 `1000`、畸形后缀、offset 溢出、软删除占号、跨租户和跨日。
3. 增加会先失败的公共服务测试；禁止只在模板服务上打补丁。

通过条件：测试能稳定复现两个缺口，且不依赖生产或非本地环境。

### G2：`LIVE-EVIDENCE-01` 本地 live 证据闭环

1. 新增 `verify-live-all.ps1`；只编排既有本地演示包，不另建数据平台。
2. 前置逐项校验 loopback、marker、库名、backend 数据源、frontend/backend 健康、租户和测试用户；任何失败均在数据库写入前退出。
3. 设置全部9组 `V2_LIVE_*`，按显式清单精确执行；任何 skipped、unexpected、flaky 或未选中 spec 均失败。
4. 输出绑定 HEAD SHA 的机器可读摘要：清单、URL、库名、租户/用户、用例数、失败数、skip 数、开始/结束时间和 Playwright 报告路径；不得写入凭据。
5. pre-push 的 `full` 改为 `full-contract` 等准确名称；命中 live 域时只提示必须另跑的本地 live 命令，不自动加载/重置数据库，也不得报告 live 已通过。
6. workflow contract 固化 required `e2e` 不含 `V2_LIVE_*` 和真实 backend 服务，防止未来误把本地 live 搬入 CI。

通过条件：统一入口覆盖9/9 spec；安全前置负例全部 fail-closed；contract/live 证据名称无歧义。

### G3：`CODE-CAP-01` 通用编号容量保护

1. 在公共服务中建立唯一 `formatAndValidateSequence` 等等价小函数；所有生成出口，包括 null 基线+offset、普通解析、软删除解析和重试 offset，均通过该守卫。
2. 只接受预期前缀后的3位十进制后缀；`1..999` 合法，数值越界统一抛 `BUSINESS_CODE_SEQUENCE_EXHAUSTED`。
3. 已存在的 `1000` 或畸形最大号不得静默回退 `001`；以可诊断的 fail-closed 异常结束。若现有错误码不能准确表达数据损坏，先经 G1 明确后增加最小错误码。
4. 六个调用方复用公共修复；`RV` 补齐 `DeletedCodeSource` 并改为含软删除取号，落实“软删编号不复用”；其余调用方仅补测试或必要适配，不复制守卫、不改变租户范围和锁策略。
5. 保留各域唯一键、既有重试和租户锁；容量耗尽必须保留稳定业务错误，不得被重试转换为 `DOCUMENT_TEMPLATE_CODE_DUPLICATE` 或通用 `DATA_CONFLICT`。
6. 无 migration；不修复、删除或重编号历史数据。若本地样本发现非法历史号，只记录并单独裁决。

通过条件：容量边界测试全部通过；六调用方均可追溯到公共守卫；并发/唯一约束和跨租户行为不退化。

### G4：本地验收与独立复核

1. 后端：公共服务专项、6调用方代表性测试、完整相关模块测试；真实本地 MySQL 复验软删除、租户和并发唯一性。
2. 前端/脚本：门禁脚本单测、workflow contract、type-check/相关单测、27 contract 0 skip。
3. 本地 live：专用 demo 库加载后执行9/9 live，0 skipped、0 unexpected、0 flaky；证据必须绑定当前 SHA。
4. 独立只读复核：核对编号边界、数据隔离、安全前置、当前 diff、测试报告和第80条证据语义纠正。

通过条件：三类证据分别记录，互不冒充。live 前置不足属于未验收，G4/G5 均不得通过。

### G5：治理收口与可选 Git 交付

1. 新质量报告分别裁决 contract CI、local live、M8 special、H2/MySQL 和编号容量，不再用单个“全量通过”覆盖不同证明边界。
2. `current-focus`、`project-map`、计划索引和 Issue 同批回写；若需要，现行状态说明第80条历史 live 证据缺口，但不改写其历史正文。
3. 用户已授权 Git 完整交付；完成本地门后继续取得同 SHA push CI、Pre-PR verifier、独立PR CI、合并和post-merge证据。

通过条件：G0～G4 客观证据齐全、唯一 Issue 关闭、载体一致、无无载体遗留项。否则保持未完成。

## 5. 验收矩阵

| 编号 | 验收项 | 通过标准 |
| --- | --- | --- |
| A-01 | contract CI 边界 | 27个显式 spec；0 skipped/unexpected/flaky；不设置 `V2_LIVE_*`，不启动真实 backend/数据库 |
| A-02 | 本地 live 清单 | 与 `liveSpecs` 9/9 一致，全部实际发现并执行；0 skipped/unexpected/flaky |
| A-03 | live 安全前置 | 非 loopback、marker 缺失、非专用库、backend 库不一致、服务不健康均在任何重载前失败 |
| A-04 | live 证据 | 绑定 HEAD SHA、loopback URL、库名、租户/用户、测试统计和 Playwright JSON；无凭据 |
| A-05 | 门禁语义 | contract-only 不再称 `full`/live；live 域变化明确输出本地命令但不伪造通过 |
| A-06 | 基本编号 | `001/009/099/999` 格式不变，跨日归零和跨租户互不影响 |
| A-07 | 容量耗尽 | 当前最大999及任何使下一值>999的 offset 均抛 `BUSINESS_CODE_SEQUENCE_EXHAUSTED`，不产生1000 |
| A-08 | 非法历史号 | `1000`、非数字、错误长度不回退001；错误可诊断、无重复插入 |
| A-09 | 六调用方 | 全部调用公共守卫；`RV` 含软删除占号；软删除/非软删除路径和代表性 offset 重试均覆盖，耗尽错误不被通用冲突覆盖 |
| A-10 | 数据一致性 | 本地 MySQL 唯一约束、租户范围和现有锁行为无回归；不产生 schema 变化 |
| A-11 | 治理 | 新报告不再声明生产阻塞；G4缺live证据时必须保持 pending；四个活跃载体一致 |

建议验证命令以实施时实际脚本为准，至少包含：

```powershell
pwsh -NoProfile -File scripts/codex-autopilot/test-mainline-owner-flow.ps1 -PlanPath "docs/plans/第81条主线-本地Live-E2E证据闭环与通用编号容量保护任务计划书.md" -Profile HighRisk
pwsh -NoProfile -File scripts/ci/test-workflow-contract.ps1
pnpm --dir frontend-admin-v2 test:e2e:contract
pwsh -NoProfile -File scripts/demo/complete-project-v2/verify-live-all.ps1
Push-Location backend; .\mvnw.cmd -Dtest=CodeGenerationServiceTest test; Pop-Location
```

## 6. 金丝雀与恢复矩阵

### 金丝雀

1. 编号金丝雀只在隔离本地测试租户执行：先验证998生成999，再验证下一次失败且数据库无1000；随后验证另一个租户仍可从001开始。
2. live 金丝雀先只做前置探测和一个只读认证场景；确认 backend 库、租户和用户绑定正确后，才加载任务自有演示数据并执行9组。
3. 金丝雀失败不得原样重试；先按失败分类定位，再决定继续或回滚。

### 恢复矩阵

| 失败点 | 立即动作 | 数据恢复 | 恢复后证明 |
| --- | --- | --- | --- |
| live 安全前置失败 | 写入前终止，不切换数据库 | 无数据变化 | 修正本地前置后重新探测 |
| demo 数据加载中断 | 停止 live，保留日志 | 仅按 `complete-project-v2` 既有任务所有权恢复/重载专用库 | load/verify 通过后再执行 live |
| live 业务失败 | 保留 Playwright/console/network 证据 | 不修改非任务数据；必要时重载专用 demo 包 | 最小失败场景通过后跑9/9 |
| 编号容量守卫误伤合法号 | 停止6调用方验收 | 回退公共服务代码；无 migration 可撤销 | 旧合法边界+新容量测试全绿 |
| 发现非法历史号 | fail-closed，禁止自动重编号 | 不删除、不改写历史号 | 形成独立数据裁决后再处理 |
| 并发/租户回归 | 停止 G4/G5 | 回退公共守卫改动，不改变数据库 | MySQL并发、唯一约束、跨租户复验通过 |

## 7. 失败分类

| 分类 | 本计划示例 | 处理 |
| --- | --- | --- |
| `tool_config` | live 入口、版本、凭据载体或门禁工具缺失/不兼容 | 修复工具前置，不判业务失败 |
| `ready_issue_config` | 把 live 误定义为 required CI、contract-only 名称为 full、Issue/计划契约矛盾 | 修正计划或门禁配置；不改业务代码 |
| `environment_prerequisite` | marker、专用库、loopback、backend 数据源或本地服务缺失 | 补本地前置；禁止将测试搬入生产/远端 |
| `tool_invocation` | 命令、路径、参数或 shell 引号错误 | 纠正一次后复跑，不归因业务 |
| `retrieval_gap` | code map、审计会话或索引未召回已知调用方 | 使用允许的备用检索，不作“不存在”断言 |
| `quality_or_security` | 合法前置下仍生成1000/重复编号，或 live 有 skip、证据不绑定SHA、跨租户/数据库安全边界失败 | 阻塞 G4/G5，修复后完整复验 |
| `unknown` | 证据不足无法归类 | fail-closed，补证据后再裁决 |

## 8. 风险、回滚与零悬空

主要风险：本地 live 会写专用演示数据；统一容量守卫影响6个业务域；非法历史号可能暴露存量数据问题。控制方式：写前安全门、单点守卫、测试先行、隔离租户金丝雀、真实 MySQL 和独立复核。

回滚：live 编排与门禁命名可按文件回退；编号修复无 migration，可回退公共服务代码。回滚后仍须保留容量缺陷为未关闭 Issue，不得恢复为“已通过”。

本轮新增后续项0、关闭后续项0、后续项净变化`0`。计划全周期新增问题源1（`ISSUE-081-001`）、关闭1、净变化`0`；不新增平行 Backlog。

## 9. 实施结果

- G0～G5本地门禁通过，`ISSUE-081-001`关闭；受保护Git交付按用户授权继续。
- 实现与修复依赖SHA `75a84cb8` 的本地live执行9/9 spec、80/80用例、0 skipped/unexpected/flaky且working tree clean；browser contract执行27/27 spec、98/98用例。
- 公共编号与六调用方相关测试107/107、真实MySQL 5/5、前端单测502/502通过；编号超过999统一fail-closed，六Mapper软删除历史占号和legacy1000优先成立。
- 证据分类、失败分类、边界和剩余Git状态见[`质量报告`](../quality/2026-08-08-issue-081-本地Live-E2E证据闭环与通用编号容量保护.md)。
