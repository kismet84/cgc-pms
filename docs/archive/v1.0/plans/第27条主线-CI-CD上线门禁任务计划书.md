# 第27条主线-CI/CD 上线门禁任务计划书

**Goal:** 将第27条主线最高优先级任务 `P0-1：CI/CD 与上线门禁` 落为正式执行计划，基于仓库当前已存在的 `.github/workflows/ci.yml` 做“现有 CI 验真 + 最小补齐门禁”，形成可执行的阶段拆解、派工边界、验收口径和收口产物定义。  
**Architecture:** 复用当前 GitHub Actions、Maven、pnpm、Jacoco、Playwright、SBOM/attestation 能力，不重建流水线框架，不引入全套 DevOps 平台；仅补齐最小缺口，包括前端 `pnpm lint:check`、后端高危依赖漏洞扫描、失败证据 artifact、构建版本追踪、PR required checks 与配置证据，禁止扩展为自动部署、灰度发布、正式镜像发布或业务代码重构。

## 结论优先

本主线是 `P0-1：CI/CD 与上线门禁`，不是从零建设 CI。当前仓库已存在 `.github/workflows/ci.yml`，并已具备以下 job：`backend-test`、`backend-test-mysql`、`type-check`、`frontend-build`、`frontend-test`、`frontend-dependency-audit`、`supply-chain-security`、`e2e`、`sql-safety-scan`。因此本次实施策略应聚焦两件事：

1. 先对现有 CI 做真实触发与结果验真，形成基线报告。
2. 仅补齐当前仍缺失或证据不足的门禁项，形成可审计、可追踪、可阻断的上线门禁闭环。

结论上，建议按最小可行方案推进，不扩大为完整发布平台，不引入新的发布编排层。

## 背景与当前基线

`cgc-pms-production-enhancement-plan.md` 已将 `P0-1：CI/CD 与上线门禁` 定义为短期 P0 任务，验收核心是“自动触发、失败阻断、迁移阻断、版本可追踪”。结合当前仓库基线，可确认：

1. 后端已有 `./mvnw -C verify` 主测试链路，并上传 jar 与 Jacoco coverage。
2. 已有 `backend-test-mysql` 负责 MySQL + Redis 环境下的 Flyway 冒烟验证。
3. 前端已有 `type-check`、`build`、`test:coverage`、`check:bundle-size`、`pnpm audit --audit-level high`。
4. 已有 `supply-chain-security` 负责前后端 SBOM 与 attestation。
5. 已有 `e2e` 与 `sql-safety-scan` 作为更接近上线风险的额外门禁。

当前缺口不是“有没有 CI”，而是：

1. 是否已真实稳定触发并记录所有 job 的结果证据。
2. 是否补齐前端 lint 阻断。
3. 是否补齐后端高危依赖漏洞扫描。
4. 是否把失败证据、构建产物命名、版本追踪、required checks 配置证据做成正式门禁资产。

## 目标与非目标

### 目标

1. 验真当前 GitHub Actions 流水线真实可运行，并输出基线报告。
2. 以最小改动补齐前端 lint、后端高危依赖扫描、失败证据 artifact。
3. 让每次构建产物具备 `sha/run number` 追踪能力。
4. 明确 PR required checks 和分支保护配置证据。
5. 形成上线门禁收口报告，支撑主负责人做通过/不通过裁决。

### 非目标

1. 不建设自动部署平台。
2. 不建设灰度发布流程。
3. 不做 Docker 镜像正式发布。
4. 不重构业务代码。
5. 不将本主线扩展为全套 DevOps 平台、制品库治理平台或多环境发布编排系统。

## 影响范围

本计划的执行影响范围应限制在以下资产：

1. `.github/workflows/ci.yml` 及其必要拆分文件。
2. 前端脚本入口与构建/测试 artifact 归档规则。
3. 后端 Maven 命令链路、漏洞扫描调用方式、构建产物归档规则。
4. GitHub Actions run summary、artifact、required checks、branch protection 配置证据。
5. `docs/plans`、`docs/quality` 下与基线报告、收口报告相关的正式文档。

不应影响业务接口、数据库 schema、部署环境、运行参数和正式发布流程。

## 阶段拆解

### M1.1 CI 基线验真

目标：触发并核对 GitHub Actions，记录所有 job 真实结果，输出基线报告。

执行要点：

1. 以 PR 或等效触发方式跑完整 CI。
2. 逐项记录 `backend-test`、`backend-test-mysql`、`type-check`、`frontend-build`、`frontend-test`、`frontend-dependency-audit`、`supply-chain-security`、`e2e`、`sql-safety-scan` 的真实状态。
3. 对失败项保留 run 链接、日志摘要、失败截图或 artifact 证据。
4. 输出《CI 基线验真报告》，作为后续补齐门禁的唯一事实基线。

退出条件：

1. 所有 job 都有明确结果和证据。
2. 已区分“已有能力可用”“已有能力不稳定”“能力缺失”三类状态。

### M1.2 最小门禁补齐

目标：补前端 `pnpm lint:check`，补后端高危依赖漏洞扫描，补失败证据 artifact。

执行要点：

1. 前端新增独立 lint 阻断步骤，优先复用现有 `package.json` 中的 `pnpm lint:check`。
2. 后端增加高危依赖漏洞扫描，优先采用 Maven 命令式扫描或现有生态成熟插件，先满足高危阻断，再决定是否固化更复杂报告体系。
3. 对关键失败 job 上传最小失败证据 artifact，例如测试报告、扫描 JSON/HTML、关键信息摘要。
4. 不新增与当前仓库无关的安全平台、SaaS 编排或二次封装。

退出条件：

1. 前端 lint 失败可阻断。
2. 后端高危依赖扫描失败可阻断。
3. 失败时有可追溯证据，不依赖人工重翻整份日志。

### M1.3 构建产物与版本追踪

目标：artifact 名称包含 sha/run number，归档 jar、dist、SBOM、coverage，生成 build summary。

执行要点：

1. 统一 artifact 命名规则，至少包含 `github.sha` 短码或 `github.run_number`。
2. 归档后端 jar、前端 dist、前后端 SBOM、前后端 coverage。
3. 在 workflow summary 中汇总本次构建版本号、产物名称、产物下载位置和关键 job 结果。
4. 保证版本追踪在 PR 和主分支构建中口径一致。

退出条件：

1. 任一构建 run 都可从 summary 与 artifact 名称反查版本。
2. 产物最小集合齐全且命名统一。

### M1.4 PR 门禁与分支保护

目标：明确 required checks 并形成配置证据。

执行要点：

1. 以最小 required checks 集合覆盖原始 P0-1 五条验收标准。
2. 明确哪些 job 必须成功才能合并，哪些保留为观察项。
3. 形成 branch protection 配置截图、导出或设置记录，作为审计证据。
4. 如仓库管理员权限不足，需单列为权限前置项，不得口头视为已完成。

建议 required checks 基线：

1. `backend-test`
2. `backend-test-mysql`
3. `type-check`
4. `frontend-build`
5. `frontend-test`
6. `frontend-dependency-audit`
7. `sql-safety-scan`

`e2e` 与 `supply-chain-security` 是否纳入强制项，应以基线稳定性和执行时长复核后再定，但必须保留明确结论和理由。

退出条件：

1. required checks 清单明确。
2. branch protection 有配置证据。

### M1.5 上线门禁收口报告

目标：汇总 CI run 链接、产物清单、阻塞/非阻塞问题。

执行要点：

1. 汇总各阶段 run 链接、artifact 名称、summary、报告路径。
2. 对未达标项明确区分阻塞/非阻塞，并给出处理建议。
3. 用统一口径回看原始 P0-1 五条验收项是否全部满足。
4. 输出正式收口报告，供主负责人裁决“通过/不通过、阻塞/非阻塞、是否可上线”。

退出条件：

1. 所有门禁项都有结论。
2. 主负责人可以直接据此做执行验收和上线裁决。

## 子智能体模型分配表

| 任务/角色 | 任务分类 | model | thinking | reason |
|---|---|---|---|---|
| CI 门禁实现子智能体 | 实现型任务 | gpt-5.4 | medium | 主要是补齐 workflow 步骤、artifact 和 summary，属于现有 CI 的最小增强，不涉及复杂业务重构。 |
| CI 运行验真与结果复核子智能体 | 验收型任务 | gpt-5.5 | medium | 需要读取多 job 真实结果、归并失败证据并判断是否满足阻断标准，证据链比单纯实现更复杂。 |
| 安全/依赖扫描复核子智能体 | 验收型任务 | gpt-5.5 | high | 涉及高危依赖扫描口径、误报排除和是否应阻断合并的判断，安全风险高于一般流水线改动。 |
| GitHub 分支保护与运行态核对子智能体 | 运维型任务 | gpt-5.4 | low | 主要是固定步骤执行、截图取证、状态核对，判断空间较小。 |
| 质量归档与收口报告子智能体 | 审计/归档型任务 | gpt-5.4 | medium | 需要按正式文档口径归档基线报告与收口报告，但不涉及高风险代码实现。 |

分类说明：

1. 实现型任务：M1.2、M1.3。
2. 验收型任务：M1.1、M1.5，及对 M1.2 的安全结论复核。
3. 运维型任务：M1.4 中的 required checks 落地与 branch protection 证据核对。
4. 审计/归档型任务：M1.1、M1.5 的正式报告落盘。

## 详细派工单

### 派工单 A：CI 基线验真

- 任务名称=M1.1 CI 基线验真
- 角色边界=你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作
- 目标=真实触发 GitHub Actions 并输出所有现有 job 的结果基线
- 范围=GitHub Actions run、job 日志、artifact、run summary、正式基线报告
- 禁止事项=不得修改业务代码；不得跳过失败证据；不得将未运行项写成已通过
- model=gpt-5.5
- thinking=medium
- reason=需要汇总多 job 证据并区分能力存在、失败、缺失，属于结果复核任务
- 验收输出=CI run 链接、各 job 状态表、失败证据清单、基线报告路径

### 派工单 B：最小门禁补齐

- 任务名称=M1.2 最小门禁补齐
- 角色边界=你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作
- 目标=补齐前端 lint、后端高危依赖扫描、失败证据 artifact
- 范围=`.github/workflows/ci.yml` 及必要最小关联文件
- 禁止事项=不得扩展为自动部署；不得引入与当前任务无关的新平台；不得改业务逻辑
- model=gpt-5.4
- thinking=medium
- reason=属于现有 workflow 的最小实现增强，复杂度中等
- 验收输出=变更文件清单、门禁新增项说明、失败阻断演示证据

### 派工单 C：版本追踪与产物归档

- 任务名称=M1.3 构建产物与版本追踪
- 角色边界=你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作
- 目标=统一 artifact 命名并补 build summary
- 范围=artifact 命名、SBOM/coverage/jar/dist 归档、run summary
- 禁止事项=不得引入制品平台；不得扩大到 Docker 正式发布
- model=gpt-5.4
- thinking=medium
- reason=实现面清晰，主要是命名和归档口径统一
- 验收输出=artifact 命名样例、summary 样例、版本追踪规则说明

### 派工单 D：PR 门禁与分支保护

- 任务名称=M1.4 PR 门禁与分支保护
- 角色边界=你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作
- 目标=落实 required checks 并形成 branch protection 配置证据
- 范围=GitHub 仓库设置、required checks、配置截图或导出证据
- 禁止事项=不得把未配置的保护项口头算作完成；权限不足必须如实回报
- model=gpt-5.4
- thinking=low
- reason=固定步骤执行型工作，重点在状态核对和证据留存
- 验收输出=required checks 清单、配置证据、权限前置项说明

### 派工单 E：上线门禁收口报告

- 任务名称=M1.5 上线门禁收口报告
- 角色边界=你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作
- 目标=基于 M1.1-M1.4 输出最终门禁结论
- 范围=CI run 证据、artifact 清单、阻塞/非阻塞问题、正式收口报告
- 禁止事项=不得省略未关闭问题；不得把观察项写成通过项
- model=gpt-5.4
- thinking=medium
- reason=属于正式归档与裁决材料整理，需要口径一致但无需高风险实现
- 验收输出=收口报告路径、通过/不通过结论、阻塞/非阻塞清单

## 验收标准

### 通过标准

以下条件全部满足，方可判定 `P0-1：CI/CD 与上线门禁` 通过：

1. PR 自动触发 CI，有真实 run 证据。
2. 后端测试失败可阻断合并，至少覆盖 `backend-test`。
3. 前端 `type-check` / `build` 失败可阻断合并。
4. Flyway migration 错误可阻断合并，至少覆盖 `backend-test-mysql`。
5. 每次构建都有可追踪版本号，能从 artifact 或 summary 反查 `sha/run number`。
6. 前端 `pnpm lint:check` 已纳入门禁并具备失败阻断效果。
7. 后端高危依赖漏洞扫描已纳入门禁并具备失败阻断效果。
8. 关键失败项有 artifact 或等效证据留存。
9. required checks 与分支保护已有配置证据。
10. 已输出上线门禁收口报告，能支撑主负责人做最终裁决。

### 不通过标准

存在任一情况即判定不通过：

1. CI 只能在 push 跑、PR 不能自动触发。
2. 后端测试失败、前端 type-check/build 失败、Flyway migration 失败后仍可合并。
3. 只有日志没有正式证据，关键失败无法回溯。
4. artifact 命名无法追踪版本。
5. required checks 未配置完成，或没有配置证据。
6. 后端高危依赖扫描、前端 lint 门禁仍缺失。

## 风险点与回滚/降级策略

### 风险点

1. `e2e` 运行时间长、波动大，若直接纳入 required checks，可能增加 PR 等待成本。
2. 后端依赖漏洞扫描可能出现误报，需要明确“高危阻断、低危观察”的口径，否则会造成伪阻塞。
3. branch protection 需要仓库管理员权限，执行阶段可能出现权限阻塞。
4. artifact 增多可能带来 Actions 存储和上传时长上升。
5. failure artifact 若范围失控，容易把无关日志一并归档，增加排障噪音。

### 回滚/降级策略

1. 若新增 lint 或漏洞扫描导致 CI 大面积不稳定，可先保留为独立 job，短期降级为非 required check，但必须在收口报告中标红，不能视为完成。
2. 若 `e2e` 稳定性不足，可先保留为观察项，不纳入第一批 required checks，但必须保留 run 证据和后续升级条件。
3. 若 summary 或 artifact 命名方案引入兼容问题，可回退到当前 job 结构，仅保留版本字段补齐，不调整 job 拆分。
4. 若管理员权限无法即时到位，M1.4 单列阻塞，由主负责人协调仓库权限后再闭环。

## 收口产物

本主线收口时至少应交付以下正式产物：

1. `CI 基线验真报告`
2. `最小门禁补齐变更说明`
3. `构建产物与版本追踪规则说明`
4. `required checks / branch protection 配置证据`
5. `上线门禁收口报告`
6. `CI run 链接清单`
7. `artifact 清单（jar、dist、SBOM、coverage）`

## 主负责人裁决建议

- 可进入执行：是。当前仓库已有 CI 基线，适合按“先验真、再最小补齐、再收口”的顺序推进。
- 阻塞项：
  1. `M1.4` 可能依赖 GitHub 仓库管理员权限，执行前需确认是否可配置 branch protection。
  2. 后端高危依赖扫描工具的最终阻断阈值需要在首轮基线后确认，避免误报直接卡死流水线。
- 非阻塞项：
  1. `e2e` 是否纳入第一批 required checks，可待基线稳定性验证后决定。
  2. artifact 命名中的 `sha` 长度、summary 展示格式可在不影响门禁能力的前提下后置微调。
