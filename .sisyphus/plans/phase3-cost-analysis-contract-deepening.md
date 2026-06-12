# 第三阶段开发计划：成本分析与合同深化

版本：V1.0
适用阶段：第 3 阶段
编制日期：2026-06-11
适用文档基线：`doc/开发文档_v2.3`（01-09）+ `doc/第1阶段开发任务拆解与Backlog.md` + `doc/第2阶段开发计划_成本归集与资金闭环.md`
前置状态：第 1、2 阶段已全部完成（合同中心 / 审批引擎 / 采购·材料·分包·计量·付款·签证六条业务链路 / 成本自动归集 / 资金闭环）

> 注：本计划生成于 Prometheus 规划上下文，落盘在 `.sisyphus/plans/`。如需归档到 `doc/`，可在执行阶段由执行代理复制。

---

## TL;DR

> **核心目标**：把"业务数据"升华为"经营洞察"——补齐目标成本基线、修正动态成本公式、打通合同变更与结算闭环，最终以角色化经营驾驶舱和预警中心让管理层实时掌控项目经营。
>
> **交付物**：
> - 目标成本管理（cost_target 多版本 + 审批）
> - 动态成本公式修正 + 预计待发生成本 + 利润测算（修复两处公式 bug + costSubjectId 缺失）
> - 合同变更完整流程（CT_CHANGE，更新合同金额 + 成本联动）
> - 结算管理（总包/分包/采购，纯只读汇总 + 审批 + 不可变锁定）
> - 角色化经营驾驶舱（5 角色视图 + ECharts 图表下钻）
> - 预警中心（8 类预警规则 + 定时批处理）
> - 技术债务修复（H2 审批权限 / M4 token 存储 / P18 vite 升级 / MySQL 全栈验证）
>
> **预估工作量**：约 32-35 人日
> **并行执行**：YES - 5 波次
> **关键路径**：H2修复 → cost_target迁移+实体 → 动态成本公式修正 → 驾驶舱/预警

---

## Context

### 原始需求
用户请求"编制第3阶段开发计划"，明确方向为：
- **业务优先级**：数据分析与报表（成本分析、资金预测、项目看板）
- **系统完善度**：纵向深化现有模块（合同变更、结算）
- **时间预期**：标准周期（4-5 周）

### 访谈纪要

**关键决策（用户确认）**：
- **范围** = 完整数据闭环：目标成本 + 动态成本增强 + 合同变更 + 结算 + 驾驶舱 + 预警中心
- **驾驶舱技术** = ECharts + Ant Design Vue（复用现有技术栈）
- **结算类型** = 全部（总包/分包/采购）
- **技术债务** = 全部修复（H2/M4/P18/MySQL 验证）

**研究发现（explore agent）**：
- 文档定义"第三阶段：成本强化"（目标成本/动态成本/偏差分析/利润测算/成本预警/经营驾驶舱）
- 文档定义合同变更 CT_CHANGE 完整流程被第 2 阶段排到第 3 阶段
- stl_settlement 表已建（V12）但无 Java 实体；cost_target 表设计存在（05.md §9.2）但无 Flyway 迁移

### Metis 评审（基于真实代码证据）

**已确认的关键事实**：
- **VAR_ORDER 实际不更新 ct_contract.current_amount**：现有代码中 current_amount 创建时等于 contractAmount，从未被任何代码修改。CT_CHANGE 将是第一个真正更新 current_amount 的功能——这是 CT_CHANGE 与 VAR_ORDER 的核心边界。
- **动态成本公式有两处 bug**：`CostSummaryService.refreshSummary()` 第 112 行 `dynamicCost = contractLockedCost + actualCost + estimatedRemainingCost`（其中 estimatedRemainingCost=0）；`getProjectSummary()` 第 194 行用了完全不同的公式。estimatedRemainingCost/contractIncome/expectedProfit/paidAmount 均硬编码为 0。
- **costSubjectId 缺失导致按科目汇总失效**：4 个现有 CostGenerationStrategy 都未设置 costSubjectId，而 refreshSummary() 按 costSubjectId 分组（第 76 行），导致所有成本项塌缩到 null 分组——按科目下钻完全失效。
- **策略模式自动注册**：新增 CT_CHANGE 策略只需 @Component + 实现 CostGenerationStrategy，Spring 自动发现，零接线。
- **WorkflowBusinessHandler 模式**：6 个现有 Handler 全部 isCritical()=true，CT_CHANGE/Settlement 照此模式。
- **租户过滤是手动的**：每个 Service 必须手动 `.eq(Entity::getTenantId, ...)`，无全局拦截器——驾驶舱所有查询必须手动加租户过滤。
- **结算表 DDL 与文档不符**：V12 建的 stl_settlement 缺 unpaid_amount/warranty_amount/source_type/source_id 等字段，需 V21 补充。

---

## Work Objectives

### 核心目标
在已打通的"业务→成本→资金"闭环之上，补齐**成本控制基线（目标成本）**、修正**动态成本计算口径**、打通**合同变更与结算闭环**，并以**角色化驾驶舱+预警中心**实现管理层对项目经营的实时洞察。

### 具体交付物
- `cost_target` + `cost_target_item` 表/实体/Service/审批 + 前端目标成本管理页
- `CostSummaryService` 公式修正 + estimatedRemainingCost 计算 + contractIncome/expectedProfit 实现 + 历史数据 backfill
- 4 个现有 CostGenerationStrategy 补 costSubjectId + CT_CHANGE 新策略
- `ct_contract_change` 表/实体/Service/Handler + 前端合同变更 tab
- `stl_settlement(_item)` 字段增强 + 实体/Service/Handler（纯只读汇总）+ 前端结算列表/详情
- `alert_log` 表 + 8 类预警规则 + 定时批处理 + 前端预警中心
- 驾驶舱后端 5 角色视图 API + 前端 ECharts 图表（下钻 2 级）
- 技术债务修复：H2 审批提交权限 / M4 token HttpOnly / P18 vite 升级 / MySQL 全栈验证

### Definition of Done
- [ ] 合同变更审批通过 → ct_contract.current_amount 更新 + 生成 cost_item(CT_CHANGE) + cost_summary 刷新
- [ ] 动态成本公式修正且双公式统一，estimatedRemainingCost 自动计算，利润测算可见
- [ ] 结算可自动汇总合同+变更+签证+计量+验收+付款，审批锁定后不可变
- [ ] 目标成本多版本管理，单项目仅一个 is_active 版本，cost_summary 关联 cost_target_id
- [ ] 5 角色驾驶舱接入真实数据，图表可下钻 2 级，租户隔离生效
- [ ] 8 类预警规则定时批处理生效，结果落 alert_log
- [ ] H2/M4/P18 修复完成，H2+MySQL 双环境集成测试通过

### Must Have
- 合同变更 CT_CHANGE：更新 current_amount + 成本联动 + 审批闭环
- 动态成本公式修正 + 历史数据 backfill
- 结算管理全部类型（总包/分包/采购）
- 目标成本多版本 + 审批
- 5 角色经营驾驶舱
- 8 类预警规则
- 技术债务全部修复

### Must NOT Have (护栏)

**CT_CHANGE 合同变更**：
- ⛔ 禁止修改 ct_contract.contractAmount（原始金额），只更新 currentAmount
- ⛔ 禁止对 DRAFT/TERMINATED 状态合同发起变更
- ⛔ 审批 REJECTED 时禁止生成 cost_item、禁止更新 currentAmount
- ⛔ cost_generated_flag=1 时禁止删除（照 VarOrderService 模式）
- ⛔ CT_CHANGE 审批通过禁止自动调整已有的 VAR_ORDER（两者独立）

**结算管理**：
- ⛔ 结算创建/审批禁止调用 CostGenerationService（纯只读汇总，否则与成本循环依赖）
- ⛔ 禁止手工覆盖计算出的 finalAmount（必须 = 合同+变更+计量-扣款）
- ⛔ 禁止跨项目引用合同
- ⛔ FINALIZED 状态禁止改回 DRAFT、禁止删除、禁止被付款引用后删除
- ⛔ 每个查询必须手动 `.eq(StlSettlement::getTenantId, ...)`

**动态成本公式修正**：
- ⛔ 禁止在无 backfill 策略且未在 staging 验证的情况下部署公式变更
- ⛔ 禁止保留 estimatedRemainingCost 硬编码 0
- ⛔ 禁止存在两套公式（必须同时修 refreshSummary 第112行 和 getProjectSummary 第194行）

**目标成本**：
- ⛔ 同一项目禁止多个 is_active=1 版本（加 DB 唯一约束）
- ⛔ 被 cost_summary.cost_target_id 引用时禁止删除

**驾驶舱**：
- ⛔ 禁止在驾驶舱 API 中拉取原始 cost_item（用预聚合的 cost_summary）
- ⛔ 禁止下钻超过 2 级（公司→项目→按科目）
- ⛔ 禁止跨租户返回数据
- ⛔ 禁止实时刷新/WebSocket（轮询可接受）
- ⛔ 禁止自定义图表构建器/用户自定义 KPI（固定 5 角色视图）

**预警中心**：
- ⛔ 禁止在事务路径中同步评估预警（如成本生成回调中）
- ⛔ 禁止用 DB 触发器评估预警（用定时批处理）
- ⛔ 禁止在第 3 阶段发送邮件/短信（仅存储，通知留第 4 阶段）

**通用**：
- ⛔ 禁止把付款当作成本（沿用第 2 阶段核心原则）
- ⛔ 结算禁止重录数据，必须可反查来源单据

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - 所有验证由 agent 执行。

### Test Decision
- **Infrastructure exists**: YES（第 1/2 阶段已有 H2 + MySQL 双环境集成测试基线）
- **Automated tests**: Tests-after（沿用前两阶段模式，实现后补集成测试 + 测试报告）
- **Framework**: JUnit 5 + Spring Boot Test（H2 local profile + MySQL dev profile）
- **测试目录**: `backend/src/test/java/com/cgcpms/`

### QA Policy
每个任务必须包含 agent 执行的 QA 场景。证据存 `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`。

- **后端 API**: Bash (curl) - 发请求，断言状态码 + 响应字段
- **数据库验证**: Bash (mysql/H2 查询) - 验证数据状态、公式计算结果
- **前端/驾驶舱**: Playwright - 导航、断言图表渲染、下钻、租户隔离、截图
- **审批回调**: 集成测试 - 提交→审批→验证回调动作（成本生成/金额更新）

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1（立即开始 - 数据库迁移 + 关键修复 + 实体基础）:
├── Task 1: 技术债务 H2 审批提交权限修复 (Flyway V21) [quick]
├── Task 2: Flyway V22 cost_target + cost_target_item 表 [quick]
├── Task 3: Flyway V23 ct_contract_change 表 [quick]
├── Task 4: Flyway V24 结算字段增强 + alert_log + cost_summary.cost_target_id [quick]
└── Task 5: 修复 4 个现有 CostGenerationStrategy 的 costSubjectId 缺失 (Flyway V25) [deep]

Wave 2（Wave1 后 - 实体 + 核心 Service）:
├── Task 6: cost_target 实体/Mapper/Service/Controller（多版本+is_active） [unspecified-high]
├── Task 7: ct_contract_change 实体/Mapper/Service/Controller [unspecified-high]
├── Task 8: stl_settlement 实体/Mapper/Service（只读汇总逻辑） [deep]
├── Task 9: 动态成本公式修正 + backfill 迁移（双公式统一） [ultrabrain]
└── Task 10: CT_CHANGE CostGenerationStrategy（@Component 自动注册） [deep]

Wave 3（Wave2 后 - 审批闭环 + 后端 API）:
├── Task 11: CT_CHANGE WorkflowHandler + Flyway 审批模板 + 成本联动 [deep]
├── Task 12: Settlement WorkflowHandler + Flyway 审批模板 + 不可变锁定 [deep]
├── Task 13: cost_target 审批 workflow + 生效机制 [unspecified-high]
├── Task 14: 预警中心 alert_log 实体 + 8 规则 + 定时批处理 [ultrabrain]
└── Task 15: 驾驶舱后端 5 角色视图聚合 API（租户过滤+索引） [deep]

Wave 4（Wave3 后 - 前端）:
├── Task 16: 目标成本管理前端页 [visual-engineering]
├── Task 17: 合同变更前端（合同详情新增 tab） [visual-engineering]
├── Task 18: 结算管理前端（列表+详情多 tab） [visual-engineering]
├── Task 19: 预警中心前端 [visual-engineering]
├── Task 20: 驾驶舱前端 - 项目经理 + 商务经理视图 [visual-engineering]
└── Task 21: 驾驶舱前端 - 成本 + 财务 + 管理层视图 [visual-engineering]

Wave 5（Wave4 后 - 技术债务收尾 + 集成测试）:
├── Task 22: 技术债务 M4 token HttpOnly Cookie 改造 [unspecified-high]
├── Task 23: 技术债务 P18 vite 5→6 升级 [quick]
├── Task 24: 全链路集成测试（CT_CHANGE/结算/动态成本/预警） [ultrabrain]
└── Task 25: MySQL 8.0 全栈验证 + 测试报告 [unspecified-high]

Wave FINAL（所有任务后 - 4 并行评审，再用户确认）:
├── Task F1: 计划合规审计 (oracle)
├── Task F2: 代码质量评审 (unspecified-high)
├── Task F3: 真实手工 QA (unspecified-high)
└── Task F4: 范围保真度检查 (deep)
-> 呈现结果 -> 获取用户明确 okay

关键路径: Task1 → Task2 → Task9 → Task15 → Task21 → Task24 → F1-F4 → 用户okay
并行加速: 约 60% 快于串行
最大并发: 6（Wave 4）
```

### Dependency Matrix

- **1（H2修复 V21）**: 依赖 无 → 阻塞 11,12,24（审批测试）
- **2（V22 cost_target）**: 依赖 无 → 阻塞 6,9
- **3（V23 ct_change）**: 依赖 无 → 阻塞 7,10
- **4（V24 结算+预警字段）**: 依赖 无 → 阻塞 8,14
- **5（costSubjectId 修复 V25）**: 依赖 无 → 阻塞 9,15
- **6（cost_target 实体）**: 依赖 2 → 阻塞 13,9
- **7（ct_change 实体）**: 依赖 3 → 阻塞 11,10
- **8（结算实体）**: 依赖 4 → 阻塞 12,15
- **9（动态成本公式）**: 依赖 2,5,6 → 阻塞 14,15,24
- **10（CT_CHANGE 策略）**: 依赖 3,7 → 阻塞 11
- **11（CT_CHANGE Handler）**: 依赖 1,7,10 → 阻塞 24
- **12（结算 Handler）**: 依赖 1,8 → 阻塞 24
- **13（目标成本审批）**: 依赖 6 → 阻塞 24
- **14（预警中心）**: 依赖 4,9 → 阻塞 19,24
- **15（驾驶舱后端）**: 依赖 5,8,9 → 阻塞 20,21
- **16-21（前端）**: 依赖各自后端 → 阻塞 24
- **22（M4 token）**: 依赖 无 → 阻塞 25
- **23（P18 vite）**: 依赖 无 → 阻塞 25
- **24（集成测试）**: 依赖 11,12,13,14 + 16-21 → 阻塞 25
- **25（MySQL 验证）**: 依赖 22,23,24 → 阻塞 F

### Agent Dispatch Summary

- **Wave 1**: 5 任务 - T1→quick, T2-T4→quick, T5→deep
- **Wave 2**: 5 任务 - T6→unspecified-high, T7→unspecified-high, T8→deep, T9→ultrabrain, T10→deep
- **Wave 3**: 5 任务 - T11→deep, T12→deep, T13→unspecified-high, T14→ultrabrain, T15→deep
- **Wave 4**: 6 任务 - T16-T21→visual-engineering
- **Wave 5**: 4 任务 - T22→unspecified-high, T23→quick, T24→ultrabrain, T25→unspecified-high
- **FINAL**: 4 任务 - F1→oracle, F2→unspecified-high, F3→unspecified-high, F4→deep

---

## TODOs

> 实现 + 测试 = 一个任务。每个任务必须有：推荐 Agent 画像 + 并行化信息 + QA 场景。

### Wave 1 — 数据库迁移 + 关键修复 + 基础

- [x] 1. 技术债务修复：H2 审批提交权限收紧

  **What to do**:
  - 修复 `/workflow/submit` 端点权限过宽问题（当前仅 `isAuthenticated()`，任意认证用户可提交任意业务类型审批）
  - 在 WorkflowController.submit 增加业务类型与权限码映射校验：提交 PURCHASE_ORDER 需 `purchase:submit`，CT_CHANGE 需 `contract:change:submit`，SETTLEMENT 需 `settlement:submit` 等
  - 在 `sys_menu` 字典中补充缺失的 submit 权限码（Flyway 迁移）
  - 优先完成此任务，因为它影响后续 CT_CHANGE/结算的审批测试

  **Must NOT do**:
  - 禁止破坏现有 6 个已通过的审批集成测试（若破坏需排查根因而非放宽）
  - 禁止改动审批引擎核心逻辑（仅在 Controller 层加权限校验）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单一端点权限收紧 + 字典补充，范围明确
  - **Skills**: 无
  - **Skills Evaluated but Omitted**:
    - `git-master`: 非历史检索任务，不需要

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 Task 2,3,4,5）
  - **Blocks**: Task 11, 12, 24（审批测试需正确权限）
  - **Blocked By**: None（可立即开始）

  **References**:
  - **Pattern References**:
    - `backend/src/main/java/com/cgcpms/workflow/controller/WorkflowController.java:submit()` - 当前提交端点，需在此加业务类型权限映射
    - `backend/.../contract/controller/CtContractController.java` 的 `@PreAuthorize` 用法 - 权限码声明范式
  - **API/Type References**:
    - `backend/.../workflow/dto/` submit 请求 DTO，含 businessType 字段
  - **External References**:
    - Spring Security `@PreAuthorize` SpEL 表达式文档
  - **WHY Each Reference Matters**:
    - WorkflowController.submit 是漏洞点，需理解 businessType 如何传入以做映射校验
    - @PreAuthorize 范式确保权限码风格与项目一致（`hasRole('ADMIN') or hasAuthority('xxx:action')`）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 无权限用户提交审批被拒绝
    Tool: Bash (curl)
    Preconditions: 创建一个仅有 contract:read 权限的用户 token
    Steps:
      1. curl -X POST /api/workflow/submit -H "Authorization: Bearer $LIMITED_TOKEN" -d '{"businessType":"PURCHASE_ORDER","businessId":1}'
      2. 断言响应 HTTP 403 Forbidden
      3. 断言响应体含 "权限不足" 或 "Access Denied"
    Expected Result: HTTP 403，无权限用户无法提交采购审批
    Failure Indicators: 返回 200 表示权限未生效
    Evidence: .sisyphus/evidence/task-1-no-permission-reject.txt

  Scenario: 有权限用户提交审批成功
    Tool: Bash (curl)
    Preconditions: 创建有 purchase:submit 权限的用户 token + 一个 DRAFT 采购订单
    Steps:
      1. curl -X POST /api/workflow/submit -H "Authorization: Bearer $AUTH_TOKEN" -d '{"businessType":"PURCHASE_ORDER","businessId":<id>}'
      2. 断言响应 HTTP 200
    Expected Result: 有权限用户正常提交
    Evidence: .sisyphus/evidence/task-1-with-permission-ok.txt
  ```

  **Commit**: YES
  - Message: `fix(workflow): 收紧审批提交端点权限，按业务类型校验权限码`
  - Files: `backend/.../workflow/controller/WorkflowController.java`, `database/migration/V21__add_submit_permissions.sql`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 2. Flyway V22：cost_target + cost_target_item 表

  **What to do**:
  - 参考 `doc/开发文档_v2.3/05_数据库设计方案_MySQL8正式版.md` §9.2 设计建表
  - `cost_target`：id, tenant_id, project_id, version_no(VARCHAR), version_name, total_target_amount, is_active(TINYINT), approval_status, effective_date, remark + 审计列
  - **关键**：增加 `is_active` 字段（文档只有 version_no，不足以选主版本）
  - **关键**：增加唯一约束 `uk_target_active (tenant_id, project_id, is_active)` 仅当 is_active=1 时唯一——同项目仅一个生效版本（MySQL 用部分索引技巧或应用层校验）
  - `cost_target_item`：id, target_id, cost_subject_id, target_amount, remark
  - 使用 `INSERT IGNORE` 保证迁移幂等（如有 demo 数据）

  **Must NOT do**:
  - 禁止只用 version_no 字符串选主版本（必须有 is_active 明确标识）
  - 禁止省略 cost_subject_id 外键（按科目分解是核心）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单一迁移脚本，有文档设计参照
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 6（cost_target 实体）, Task 9（动态成本需目标对比）
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `database/migration/V12__init_phase2_tables.sql` - 建表风格、字符集、索引、审计列范式
    - `database/migration/V4__init_cost_payment_tables.sql:cost_subject` - cost_subject 表结构，target_item 需引用其 id
  - **External References**:
    - `doc/开发文档_v2.3/05_数据库设计方案_MySQL8正式版.md` §9.2 - cost_target/cost_target_item 字段设计
  - **WHY Each Reference Matters**:
    - V12 是最近的迁移范本，确保字符集/审计列/索引命名一致
    - 05.md §9.2 是权威字段设计来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 迁移成功创建表
    Tool: Bash (mysql/H2)
    Preconditions: 干净数据库 + 应用启动跑完 Flyway
    Steps:
      1. 启动后端 local profile，Flyway 自动迁移
      2. 查询 SHOW TABLES LIKE 'cost_target%'
      3. 断言返回 cost_target 和 cost_target_item 两张表
      4. DESCRIBE cost_target，断言含 is_active, version_no, total_target_amount, approval_status 字段
    Expected Result: 两表创建成功，字段齐全
    Evidence: .sisyphus/evidence/task-2-tables-created.txt

  Scenario: is_active 唯一约束生效
    Tool: Bash (mysql)
    Preconditions: 表已创建
    Steps:
      1. INSERT 两条同 project_id 且 is_active=1 的记录
      2. 断言第二条插入失败（唯一约束冲突）或应用层拦截
    Expected Result: 同项目无法有两个生效版本
    Evidence: .sisyphus/evidence/task-2-active-unique.txt
  ```

  **Commit**: YES
  - Message: `feat(cost): 新增目标成本表 cost_target/cost_target_item (V22)`
  - Files: `database/migration/V22__init_cost_target_tables.sql`
  - Pre-commit: `./mvnw flyway:info`

- [x] 3. Flyway V23：ct_contract_change 合同变更表

  **What to do**:
  - 参考 `05.md §6.4` 设计建表
  - 字段：id, tenant_id, project_id, contract_id, change_code(自动编号 CC-yyyyMMdd-XXX), change_name, change_type(AMOUNT/DURATION/CLAUSE), before_amount, change_amount, after_amount, reason(TEXT), approval_status, effective_flag(TINYINT), cost_generated_flag(TINYINT) + 审计列
  - 索引：idx_change_contract(contract_id), idx_change_project(project_id)

  **Must NOT do**:
  - 禁止与 var_order 表混淆（CT_CHANGE 是正式合同变更，VAR_ORDER 是现场签证）
  - 禁止省略 cost_generated_flag（成本幂等需要）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单一迁移脚本，有文档设计参照
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 7（ct_change 实体）, Task 10（CT_CHANGE 策略）
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `database/migration/V12__init_phase2_tables.sql:var_order` - 变更类表结构范本（含 cost_generated_flag）
    - `database/migration/V20__add_contract_cost_generated_flag.sql` - cost_generated_flag 字段用法
  - **External References**:
    - `05.md §6.4` - ct_contract_change 字段设计
  - **WHY Each Reference Matters**:
    - var_order 是最接近的同类表，CT_CHANGE 照此结构 + cost_generated_flag 幂等模式

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 迁移成功创建合同变更表
    Tool: Bash (mysql/H2)
    Preconditions: 干净数据库
    Steps:
      1. 启动后端，Flyway 迁移
      2. DESCRIBE ct_contract_change
      3. 断言含 change_type, before_amount, change_amount, after_amount, effective_flag, cost_generated_flag
    Expected Result: 表创建成功，字段齐全
    Evidence: .sisyphus/evidence/task-3-table-created.txt
  ```

  **Commit**: YES
  - Message: `feat(contract): 新增合同变更表 ct_contract_change (V23)`
  - Files: `database/migration/V23__init_contract_change_table.sql`
  - Pre-commit: `./mvnw flyway:info`

- [x] 4. Flyway V24：结算字段增强 + alert_log + cost_summary.cost_target_id

  **What to do**:
  - **结算表增强**：V12 已建 stl_settlement/stl_settlement_item，但缺字段。ALTER 补充：
    - stl_settlement 加 `unpaid_amount`, `warranty_amount`, `settlement_status`（DRAFT/APPROVING/FINALIZED/ARCHIVED）, `finalized_at`
    - stl_settlement_item 加 `source_type`, `source_id`（用于下钻反查 measurement/change）
  - **预警表**：新建 `alert_log`：id, tenant_id, project_id, rule_type, severity(HIGH/MEDIUM/LOW), message, triggered_at, is_read(TINYINT) + 审计列
  - **成本汇总关联目标**：ALTER cost_summary 加 `cost_target_id`（nullable FK，记录对比的目标版本）
  - **驾驶舱索引**：cost_summary 加索引 idx_summary_tenant_project(tenant_id, project_id), idx_summary_subject(project_id, cost_subject_id)

  **Must NOT do**:
  - 禁止删除/重建 V12 已有的 stl_settlement 表（用 ALTER 增量）
  - 禁止省略 source_type/source_id（结算下钻反查刚需）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: ALTER + 建表迁移，范围明确
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 8（结算实体）, Task 14（预警）, Task 15（驾驶舱索引）
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `database/migration/V12__init_phase2_tables.sql:stl_settlement` - 现有结算表结构（需 ALTER 增强）
    - `database/migration/V8__add_missing_indexes.sql` - 索引添加范式
    - `database/migration/V18__add_contract_paid_amount.sql` - ALTER 加字段范式
  - **External References**:
    - `02.md §5.8.4` - 结算金额计算逻辑（unpaid/warranty 字段含义）
  - **WHY Each Reference Matters**:
    - V12 是结算表原始定义，必须基于它 ALTER 而非重建
    - V18 展示如何安全 ALTER 加字段

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 结算表字段增强成功
    Tool: Bash (mysql/H2)
    Steps:
      1. Flyway 迁移后 DESCRIBE stl_settlement
      2. 断言含 unpaid_amount, warranty_amount, settlement_status, finalized_at
      3. DESCRIBE stl_settlement_item，断言含 source_type, source_id
    Expected Result: 字段全部增加成功
    Evidence: .sisyphus/evidence/task-4-settlement-enhanced.txt

  Scenario: alert_log 表 + cost_summary 索引创建
    Tool: Bash (mysql)
    Steps:
      1. SHOW TABLES LIKE 'alert_log'，断言存在
      2. SHOW INDEX FROM cost_summary，断言含 idx_summary_tenant_project
      3. DESCRIBE cost_summary，断言含 cost_target_id
    Expected Result: 预警表、索引、关联字段齐全
    Evidence: .sisyphus/evidence/task-4-alert-index.txt
  ```

  **Commit**: YES
  - Message: `feat(db): 结算字段增强 + alert_log + cost_summary 目标关联与索引 (V24)`
  - Files: `database/migration/V24__enhance_settlement_alert_summary.sql`
  - Pre-commit: `./mvnw flyway:info`

- [x] 5. 修复 4 个现有 CostGenerationStrategy 的 costSubjectId 缺失

  **What to do**:
  - **根因**（Metis 确认）：4 个策略（ContractCostStrategy/MaterialReceiptCostStrategy/SubMeasureCostStrategy/VarOrderCostStrategy）生成 CostItem 时从不设置 costSubjectId，而 CostSummaryService.refreshSummary() 按 costSubjectId 分组（第76行），导致所有成本塌缩到 null 分组——按科目下钻完全失效
  - 修复每个策略：从来源单据明细（如 mat_receipt_item/sub_measure_item/contract_item）读取 cost_subject_id 并设置到 CostItem
  - 若来源单据无 cost_subject_id，回填规则：按 cost_type 映射到默认科目（材料→材料科目，分包→分包科目等）
  - 修复后对历史 cost_item 数据 backfill costSubjectId

  **Must NOT do**:
  - 禁止留 costSubjectId 为 null（否则驾驶舱按科目分组失效）
  - 禁止破坏 uk_cost_source_item 幂等约束

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 涉及 4 个策略 + 来源单据字段追溯 + 历史数据 backfill，需理解成本归集全链路
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 9（动态成本按科目）, Task 15（驾驶舱科目下钻）
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `backend/.../cost/strategy/MaterialReceiptCostStrategy.java` - 现有策略，需补 costSubjectId 设置
    - `backend/.../cost/strategy/SubMeasureCostStrategy.java` - 同上
    - `backend/.../cost/strategy/ContractCostStrategy.java` - 同上
    - `backend/.../cost/strategy/VarOrderCostStrategy.java` - 同上
    - `backend/.../cost/service/CostSummaryService.java:76` - refreshSummary 按 costSubjectId 分组逻辑
  - **API/Type References**:
    - `backend/.../cost/entity/CostItem.java:costSubjectId` - 目标字段
  - **WHY Each Reference Matters**:
    - 4 个策略是 bug 源头，需逐一修复
    - CostSummaryService:76 是受影响逻辑，验证修复后分组正确

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 新生成的成本项含 costSubjectId
    Tool: Bash (mysql/集成测试)
    Preconditions: 创建材料验收并审批通过
    Steps:
      1. 触发材料验收审批通过 → 生成 cost_item
      2. 查询 SELECT cost_subject_id FROM cost_item WHERE source_type='MAT_RECEIPT' ORDER BY id DESC LIMIT 1
      3. 断言 cost_subject_id 非 null
    Expected Result: 成本项正确关联科目
    Evidence: .sisyphus/evidence/task-5-cost-subject-set.txt

  Scenario: 成本汇总按科目正确分组
    Tool: Bash (mysql/curl)
    Steps:
      1. 触发 cost_summary 刷新
      2. SELECT cost_subject_id, COUNT(*) FROM cost_summary WHERE project_id=1 GROUP BY cost_subject_id
      3. 断言存在多个非 null 的 cost_subject_id 分组（而非全部塌缩到 null）
    Expected Result: 按科目分组生效
    Evidence: .sisyphus/evidence/task-5-summary-grouping.txt
  ```

  **Commit**: YES
  - Message: `fix(cost): 修复成本策略 costSubjectId 缺失导致按科目汇总失效`
  - Files: `backend/.../cost/strategy/*.java`, `database/migration/V25__backfill_cost_subject_id.sql`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

### Wave 2 — 实体 + 核心 Service

- [x] 6. cost_target 实体/Mapper/Service/Controller（多版本+is_active）

  **What to do**:
  - 实体：CostTarget（与 V22 表对应）+ CostTargetItem
  - Mapper：CostTargetMapper + CostTargetItemMapper
  - Service：新建/编辑/删除目标成本（is_active 唯一校验、切换生效版本、版本列表查询）
  - Controller：CRUD + 版本切换端点 `POST /cost-targets/{id}/activate`
  - 版本切换逻辑：旧版本 is_active=0，新版本 is_active=1（事务内确保唯一）
  - 删除校验：被 cost_summary.cost_target_id 引用时禁止删除

  **Must NOT do**:
  - 禁止允许同项目多个 is_active=1（必须事务内切换）
  - 禁止无审批即生效（生效需审批，本任务先实现 CRUD，审批在 Task 13）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 多版本管理 + 唯一性校验 + FK 删除守卫，中等复杂度
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2（与 Task 7,8,9,10）
  - **Blocks**: Task 13（审批）, Task 9（动态成本需目标对比）
  - **Blocked By**: Task 2（V22 迁移）

  **References**:
  - **Pattern References**:
    - `backend/.../contract/entity/CtContract.java` - 实体范式（tenantId/审计列）
    - `backend/.../contract/service/CtContractService.java` - Service CRUD 范式、租户过滤
    - `backend/.../cost/entity/CostSubject.java` - 成本科目实体（target_item 需引用）
  - **API/Type References**:
    - `database/migration/V22__*.sql` - 表结构定义
  - **WHY Each Reference Matters**:
    - CtContract 是最标准的业务实体模板
    - CostSubject 是 target_item 的 FK 目标，理解引用关系

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 创建目标成本并设为生效版本
    Tool: Bash (curl)
    Preconditions: 项目已存在，无生效目标成本
    Steps:
      1. curl -X POST /api/cost-targets -H "Authorization: Bearer $TOKEN" -d '{"projectId":1,"versionNo":"v1.0","totalTargetAmount":5000000,"isActive":1}'
      2. 断言响应 HTTP 200，返回 id
      3. curl /api/cost-targets?projectId=1，断言 isActive=1 的记录唯一
    Expected Result: 创建成功且为生效版本
    Evidence: .sisyphus/evidence/task-6-create-active.txt

  Scenario: 切换生效版本（旧版本自动失效）
    Tool: Bash (curl)
    Steps:
      1. 创建第二个版本 v2.0
      2. curl -X POST /api/cost-targets/{v2_id}/activate -H "Authorization: Bearer $TOKEN"
      3. 查询项目所有版本，断言仅 v2.0 的 isActive=1，v1.0 为 0
    Expected Result: 版本切换成功，唯一性保持
    Evidence: .sisyphus/evidence/task-6-version-switch.txt

  Scenario: 删除被引用的目标成本失败
    Tool: Bash (curl + mysql)
    Steps:
      1. 关联 cost_summary.cost_target_id 到某目标
      2. curl -X DELETE /api/cost-targets/{id}
      3. 断言响应 400 "目标成本已被成本汇总引用，无法删除"
    Expected Result: 删除被拒
    Evidence: .sisyphus/evidence/task-6-delete-guard.txt
  ```

  **Commit**: YES
  - Message: `feat(cost): 目标成本管理 - 多版本+生效切换 CRUD`
  - Files: `backend/.../cost/entity/CostTarget*.java`, `backend/.../cost/service/CostTargetService.java`, `backend/.../cost/controller/CostTargetController.java`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 7. ct_contract_change 实体/Mapper/Service/Controller

  **What to do**:
  - 实体：CtContractChange（与 V23 表对应）
  - Mapper：CtContractChangeMapper
  - Service：新建/编辑/删除合同变更、自动编号生成（CC-yyyyMMdd-XXX）
  - Controller：CRUD + 提交审批端点 `POST /contract-changes/{id}/submit`（暂留空实现，审批在 Task 11）
  - 删除守卫：cost_generated_flag=1 时禁止删除（照 VAR_ORDER 模式）

  **Must NOT do**:
  - 禁止在 Service 中更新 ct_contract.currentAmount（更新在审批回调 Task 11 中做）
  - 禁止允许对 DRAFT/TERMINATED 合同创建变更

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: CRUD + 自动编号 + 删除守卫，中等复杂度
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 11（审批回调）, Task 10（CT_CHANGE 成本策略）
  - **Blocked By**: Task 3（V23 迁移）

  **References**:
  - **Pattern References**:
    - `backend/.../variation/service/VarOrderService.java:207` - cost_generated_flag 删除守卫模式
    - `backend/.../contract/service/CtContractService.java` - 自动编号生成范式
    - `backend/.../contract/entity/CtContract.java` - Contract 实体引用模式
  - **API/Type References**:
    - `database/migration/V23__*.sql` - 表结构
  - **WHY Each Reference Matters**:
    - VarOrderService:207 是删除守卫范本，CT_CHANGE 必须照搬（防成本幂等破坏）
    - CtContractService 展示合同编号生成逻辑（CT-yyyyMMdd-XXX → CC-yyyyMMdd-XXX 类似）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 创建合同变更，自动编号
    Tool: Bash (curl)
    Preconditions: 合同已存在且 status=PERFORMING
    Steps:
      1. curl -X POST /api/contract-changes -H "Authorization: Bearer $TOKEN" -d '{"contractId":1,"changeType":"AMOUNT","beforeAmount":1000000,"changeAmount":50000,"reason":"设计变更"}'
      2. 断言响应 changeCode 格式为 CC-20260611-001
    Expected Result: 编号自动生成
    Evidence: .sisyphus/evidence/task-7-auto-code.txt

  Scenario: 已生成成本的变更禁止删除
    Tool: Bash (curl)
    Steps:
      1. 模拟 cost_generated_flag=1 的变更记录
      2. curl -X DELETE /api/contract-changes/{id}
      3. 断言响应 400 "变更已生成成本，无法删除"
    Expected Result: 删除被守卫拒绝
    Evidence: .sisyphus/evidence/task-7-delete-guard.txt
  ```

  **Commit**: YES
  - Message: `feat(contract): 合同变更 CRUD + 自动编号 + 删除守卫`
  - Files: `backend/.../contract/change/entity/CtContractChange.java`, `backend/.../contract/change/service/CtContractChangeService.java`, `backend/.../contract/change/controller/CtContractChangeController.java`
  - Pre-commit: `./mvnw test`

- [x] 8. stl_settlement 实体/Mapper/Service（纯只读汇总逻辑）

  **What to do**:
  - 实体：StlSettlement + StlSettlementItem（含 V24 增强的 source_type/source_id）
  - Mapper：StlSettlementMapper + StlSettlementItemMapper
  - Service：`computeSettlementAmount(contractId)` 方法——纯只读汇总：
    - 查询 ct_contract.currentAmount
    - 汇总 ct_contract_change WHERE effective_flag=1
    - 汇总 sub_measure WHERE status=CONFIRMED
    - 汇总 var_order WHERE direction=COST
    - 汇总 pay_record WHERE status=SUCCESS
    - 计算 finalAmount / unpaidAmount / warrantyAmount
  - 新建结算时自动调用 compute 填充金额
  - **关键守卫**：Service 中绝不调用 CostGenerationService（纯只读，防循环依赖）

  **Must NOT do**:
  - ⛔ 禁止在结算创建/审批中调用 CostGenerationService（Metis 强调：结算是纯汇总，不生成成本）
  - ⛔ 禁止允许手工覆盖 finalAmount（必须自动计算）
  - ⛔ 禁止跨项目引用合同

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 汇总逻辑复杂，需理解 5 个数据源（合同/变更/计量/签证/付款）+ 循环依赖防护
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 12（结算审批）, Task 15（驾驶舱）
  - **Blocked By**: Task 4（V24 字段增强）

  **References**:
  - **Pattern References**:
    - `backend/.../cost/service/CostSummaryService.java:refreshSummary()` - 多表汇总范式
    - `backend/.../contract/entity/CtContract.java` - 合同引用
    - `backend/.../subcontract/entity/SubMeasure.java` - 分包计量数据源
    - `backend/.../variation/entity/VarOrder.java` - 签证数据源
    - `backend/.../payment/entity/PayRecord.java` - 付款数据源
  - **API/Type References**:
    - `database/migration/V24__*.sql` - 增强后的结算表结构
  - **External References**:
    - `02.md §5.8.4` - 结算金额计算逻辑公式
  - **WHY Each Reference Matters**:
    - CostSummaryService.refreshSummary 是最接近的多表汇总范本
    - 5 个数据源实体是汇总目标，需理解各自的金额字段和状态过滤条件
    - 02.md §5.8.4 是权威计算公式来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 结算金额自动汇总正确
    Tool: Bash (curl + mysql)
    Preconditions: 合同+变更+计量+付款数据已存在
    Steps:
      1. curl -X POST /api/settlements -H "Authorization: Bearer $TOKEN" -d '{"contractId":1}'
      2. 断言响应的 finalAmount = currentAmount + changeAmount + measuredAmount - deductions
      3. 断言 unpaidAmount = finalAmount - paidAmount - warrantyAmount
    Expected Result: 金额计算准确
    Evidence: .sisyphus/evidence/task-8-amount-computed.txt

  Scenario: 结算 Service 不调用成本生成
    Tool: 代码审查 + 单元测试
    Steps:
      1. 全文搜索 StlSettlementService.java 中 "costGenerationService"
      2. 断言搜索结果为空（无调用）
      3. Mock 所有数据源，调用 create()，验证 CostGenerationService 无任何调用
    Expected Result: 结算与成本无耦合
    Evidence: .sisyphus/evidence/task-8-no-cost-gen.txt
  ```

  **Commit**: YES
  - Message: `feat(settlement): 结算管理实体+纯只读汇总Service（防循环依赖）`
  - Files: `backend/.../settlement/entity/StlSettlement*.java`, `backend/.../settlement/service/StlSettlementService.java`
  - Pre-commit: `./mvnw test`

- [x] 9. 动态成本公式修正 + backfill 迁移（双公式统一）

  **What to do**:
  - **Bug 修复**（Metis 确认两处）：
    - `CostSummaryService.refreshSummary()` 第 112 行：`dynamicCost = actualCost + estimatedRemainingCost`（移除 contractLockedCost）
    - `CostSummaryService.getProjectSummary()` 第 194 行：统一为同一公式
  - **实现 estimatedRemainingCost 计算**：`currentAmount - SUM(已确认计量/验收)` 自动计算（不再硬编码 0）
  - **实现 contractIncome 计算**：汇总总包合同金额 + 收入类 VAR_ORDER（direction=INCOME）
  - **实现 expectedProfit 计算**：`contractIncome - dynamicCost`
  - **历史数据 backfill**：Flyway V26 迁移脚本，对所有现有 cost_summary 行重新计算上述 4 个字段
  - **Staging 验证**：在 staging 环境运行 backfill 前后对比，生成 diff 报告存档到 `.sisyphus/evidence/task-9-formula-fix-diff.txt`

  **Must NOT do**:
  - ⛔ 禁止无 backfill 策略且未验证就部署公式变更
  - ⛔ 禁止保留 estimatedRemainingCost 硬编码 0
  - ⛔ 禁止存在两套公式（必须双公式统一）

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: 公式修正 + 自动计算逻辑 + 历史数据 backfill + staging 验证，高复杂度
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: NO（阻塞后续 Wave 3/4 的驾驶舱和预警）
  - **Parallel Group**: Wave 2 内可与其他任务并行，但必须在 Wave 3 前完成
  - **Blocks**: Task 14（预警需正确动态成本）, Task 15（驾驶舱需正确利润测算）, Task 24（集成测试）
  - **Blocked By**: Task 2（cost_target 表），Task 5（costSubjectId 修复），Task 6（目标成本实体）

  **References**:
  - **Pattern References**:
    - `backend/.../cost/service/CostSummaryService.java:112` - Bug 1：错误公式
    - `backend/.../cost/service/CostSummaryService.java:194` - Bug 2：不一致公式
    - `backend/.../cost/entity/CostSummary.java` - 目标字段定义
  - **External References**:
    - `02.md §5.6.2` - 成本口径定义（动态成本/预计待发生成本/预计利润）
  - **WHY Each Reference Matters**:
    - 两处 bug 是修复目标
    - 02.md §5.6.2 是计算逻辑权威来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 公式修复后 dynamicCost 计算正确
    Tool: Bash (mysql + curl)
    Steps:
      1. 触发 cost_summary 刷新
      2. 查询 SELECT project_id, dynamic_cost, actual_cost, estimated_remaining_cost FROM cost_summary WHERE project_id=1
      3. 手工计算 expected_dynamic = actual_cost + estimated_remaining
      4. 断言 dynamic_cost = expected_dynamic（误差 <0.01）
    Expected Result: 公式计算正确
    Evidence: .sisyphus/evidence/task-9-formula-correct.txt

  Scenario: backfill 前后对比 diff 报告
    Tool: Bash (mysql + diff)
    Steps:
      1. 迁移前导出 cost_summary 快照到 /tmp/before.txt
      2. 运行 Flyway V26 backfill 迁移
      3. 迁移后导出到 /tmp/after.txt
      4. diff -u /tmp/before.txt /tmp/after.txt > .sisyphus/evidence/task-9-formula-fix-diff.txt
      5. 断言 diff 显示 estimatedRemainingCost 从 0 变为正数
    Expected Result: Backfill 成功，diff 报告存档
    Evidence: .sisyphus/evidence/task-9-formula-fix-diff.txt

  Scenario: estimatedRemainingCost 自动计算验证
    Tool: Bash (mysql)
    Steps:
      1. 查询项目1的 currentAmount 和已确认计量总和
      2. 查询 cost_summary.estimated_remaining_cost
      3. 断言 estimated_remaining = currentAmount - SUM(measurements)
    Expected Result: 自动计算逻辑正确
    Evidence: .sisyphus/evidence/task-9-estimated-remaining.txt
  ```

  **Commit**: YES
  - Message: `fix(cost): 修正动态成本公式（双bug统一）+ 自动计算待发生/收入/利润 + backfill`
  - Files: `backend/.../cost/service/CostSummaryService.java`, `database/migration/V26__backfill_dynamic_cost_formula.sql`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 10. CT_CHANGE CostGenerationStrategy（@Component 自动注册）

  **What to do**:
  - 新建 `CtContractChangeCostStrategy implements CostGenerationStrategy`
  - `supportSourceType()` 返回 `"CT_CHANGE"`
  - `generateCost(Long changeId)` 实现：
    - 查询 ct_contract_change，验证 approval_status=APPROVED 且 effective_flag=1
    - 生成 CostItem：source_type=CT_CHANGE, source_id=changeId, amount=changeAmount, costSubjectId=从合同默认科目或变更关联科目
    - 幂等：uk_cost_source_item 约束 + try-catch DuplicateKeyException
  - Spring 自动注册（@Component），无需手工注册到 Map

  **Must NOT do**:
  - ⛔ 禁止在策略中更新 ct_contract.currentAmount（更新在 Handler 中做）
  - ⛔ 禁止忘记设置 costSubjectId（复现 Task 5 修复的 bug）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 策略模式实现 + 成本幂等 + costSubjectId 正确性
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 11（CT_CHANGE Handler 调用此策略）
  - **Blocked By**: Task 3（V23 表），Task 7（ct_change 实体）

  **References**:
  - **Pattern References**:
    - `backend/.../cost/strategy/VarOrderCostStrategy.java` - 最接近的策略范本（同为变更类）
    - `backend/.../cost/strategy/MaterialReceiptCostStrategy.java` - costSubjectId 设置范式（Task 5 修复后）
    - `backend/.../cost/service/CostGenerationService.java:init()` - 自动注册机制
  - **WHY Each Reference Matters**:
    - VarOrderCostStrategy 是同类型范本，CT_CHANGE 照此结构
    - MaterialReceiptCostStrategy（修复后）展示正确的 costSubjectId 设置
    - CostGenerationService:init() 展示 Spring 如何自动发现 @Component 策略

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: CT_CHANGE 生成成本项且含 costSubjectId
    Tool: Bash (curl + mysql)
    Preconditions: 合同变更已审批通过
    Steps:
      1. 触发 CT_CHANGE 成本生成（审批回调调用 generateCost）
      2. 查询 SELECT * FROM cost_item WHERE source_type='CT_CHANGE' AND source_id=<changeId>
      3. 断言记录存在，amount 正确，costSubjectId 非 null
    Expected Result: 成本项生成成功，科目正确
    Evidence: .sisyphus/evidence/task-10-cost-generated.txt

  Scenario: 幂等重复调用不产生重复成本
    Tool: 集成测试
    Steps:
      1. 调用 generateCost(changeId) 两次
      2. 查询 cost_item 表，断言仅一条记录
    Expected Result: 幂等约束生效
    Evidence: .sisyphus/evidence/task-10-idempotent.txt
  ```

  **Commit**: YES
  - Message: `feat(cost): CT_CHANGE 成本生成策略（@Component 自动注册 + costSubjectId）`
  - Files: `backend/.../contract/change/strategy/CtContractChangeCostStrategy.java`
  - Pre-commit: `./mvnw test`

### Wave 3 — 审批闭环 + 后端 API

- [x] 11. CT_CHANGE WorkflowHandler + 审批模板 + 成本联动

  **What to do**:
  - 新建 `CtContractChangeWorkflowHandler implements WorkflowBusinessHandler`，isCritical()=true
  - `onApproved(businessId)` 动作（同事务）：
    1. 更新 ct_contract_change.approval_status=APPROVED, effective_flag=1
    2. **更新 ct_contract.currentAmount = currentAmount + changeAmount**（这是 CT_CHANGE 与 VAR_ORDER 的核心区别——VAR_ORDER 不更新合同金额）
    3. 调用 CostGenerationService.generateCost("CT_CHANGE", changeId)
    4. 触发 cost_summary 刷新
  - onRejected：approval_status=REJECTED，不更新金额、不生成成本
  - Flyway 审批模板迁移（照 V17 签证模板）
  - 实现 Task 7 留空的 submit 端点

  **Must NOT do**:
  - ⛔ 禁止修改 contractAmount（原始金额），只更新 currentAmount
  - ⛔ REJECTED 时禁止更新金额/生成成本
  - ⛔ 禁止自动调整已有 VAR_ORDER

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 审批回调 + 合同金额联动 + 成本生成 + 事务一致性，核心业务逻辑
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3（与 Task 12,13,14,15）
  - **Blocks**: Task 24（集成测试）
  - **Blocked By**: Task 1（H2权限），Task 7（ct_change 实体），Task 10（成本策略）

  **References**:
  - **Pattern References**:
    - `backend/.../variation/handler/VarOrderWorkflowHandler.java:39-57` - Handler 范本（onApproved 结构）
    - `backend/.../contract/handler/ContractWorkflowHandler.java` - isCritical+成本生成范式
    - `database/migration/V17__init_variation_approval_template.sql` - 审批模板迁移范本
  - **API/Type References**:
    - `backend/.../cost/service/CostGenerationService.java:generateCost()` - 成本生成入口
    - `backend/.../contract/entity/CtContract.java:currentAmount` - 待更新字段
  - **WHY Each Reference Matters**:
    - VarOrderWorkflowHandler 是最接近范本，但 CT_CHANGE 需额外更新 currentAmount（关键差异）
    - V17 是审批模板迁移范本

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 合同变更审批通过更新合同金额并生成成本
    Tool: Bash (curl + mysql)
    Preconditions: 合同 currentAmount=1000000，创建 +50000 的变更
    Steps:
      1. 提交变更审批 POST /contract-changes/{id}/submit
      2. 通过工作流审批所有节点
      3. 查询 SELECT current_amount FROM ct_contract WHERE id=1，断言 = 1050000
      4. 查询 SELECT contract_amount FROM ct_contract WHERE id=1，断言仍 = 1000000（原始金额不变）
      5. 查询 cost_item WHERE source_type='CT_CHANGE'，断言 1 条 amount=50000
    Expected Result: currentAmount 更新，contractAmount 不变，成本生成
    Evidence: .sisyphus/evidence/task-11-approved-amount-update.txt

  Scenario: 变更被驳回不更新金额
    Tool: Bash (curl + mysql)
    Steps:
      1. 创建 +20000 变更并提交
      2. 工作流驳回
      3. 查询 current_amount，断言仍为 1050000（未变）
      4. 查询 cost_item WHERE source_id=<change2_id>，断言 0 条
    Expected Result: 驳回不产生任何副作用
    Evidence: .sisyphus/evidence/task-11-rejected-no-effect.txt
  ```

  **Commit**: YES
  - Message: `feat(contract): CT_CHANGE 审批闭环 - 合同金额联动+成本生成`
  - Files: `backend/.../contract/change/handler/CtContractChangeWorkflowHandler.java`, `database/migration/V27__init_contract_change_approval_template.sql`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 12. Settlement WorkflowHandler + 审批模板 + 不可变锁定

  **What to do**:
  - 新建 `SettlementWorkflowHandler implements WorkflowBusinessHandler`，isCritical()=true
  - `onApproved(businessId)` 动作：
    1. 更新 stl_settlement.settlement_status=FINALIZED, finalized_at=now
    2. 更新 ct_contract.settlement_amount = finalAmount
    3. **绝不调用 CostGenerationService**（纯只读汇总）
  - 实现不可变守卫：FINALIZED 状态禁止编辑/删除（Service 层校验）
  - 实现 submit 端点 + 结算前置校验（存在未审批变更/未确认计量时禁止结算）
  - Flyway 审批模板迁移
  - businessType=SETTLEMENT 加入 Task 1 的权限映射

  **Must NOT do**:
  - ⛔ 禁止调用 CostGenerationService（循环依赖防护）
  - ⛔ FINALIZED 禁止改回 DRAFT、禁止删除
  - ⛔ 存在未审批变更时禁止结算确认

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 审批回调 + 不可变状态机 + 前置校验，需理解结算与多模块关系
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 24（集成测试）
  - **Blocked By**: Task 1（H2权限），Task 8（结算实体）

  **References**:
  - **Pattern References**:
    - `backend/.../contract/handler/ContractWorkflowHandler.java` - Handler 范本
    - `backend/.../variation/handler/VarOrderWorkflowHandler.java` - onApproved 结构
    - `database/migration/V16__init_payment_approval_template.sql` - 审批模板范本
  - **API/Type References**:
    - `backend/.../settlement/service/StlSettlementService.java` - Task 8 的汇总 Service
  - **WHY Each Reference Matters**:
    - Handler 范本确保审批回调一致；但结算 Handler 必须刻意不调用成本生成（与其他 Handler 的关键差异）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 结算审批通过锁定且不生成成本
    Tool: Bash (curl + mysql)
    Preconditions: 结算单已创建
    Steps:
      1. 提交结算审批并通过所有节点
      2. 查询 SELECT settlement_status, finalized_at FROM stl_settlement WHERE id=1，断言 FINALIZED 且 finalized_at 非空
      3. 查询 cost_item WHERE source_type='SETTLEMENT'，断言 0 条（结算不生成成本）
      4. 查询 ct_contract.settlement_amount，断言 = finalAmount
    Expected Result: 结算锁定，无成本生成，合同回写
    Evidence: .sisyphus/evidence/task-12-finalized-no-cost.txt

  Scenario: FINALIZED 结算禁止删除
    Tool: Bash (curl)
    Steps:
      1. curl -X DELETE /api/settlements/{finalized_id}
      2. 断言响应 400 "已确认结算无法删除"
    Expected Result: 不可变守卫生效
    Evidence: .sisyphus/evidence/task-12-immutable.txt

  Scenario: 存在未审批变更时禁止结算
    Tool: Bash (curl)
    Steps:
      1. 合同有一个 APPROVING 状态的变更
      2. 提交结算审批
      3. 断言前置校验拦截 "存在未审批变更，无法结算"
    Expected Result: 前置校验生效
    Evidence: .sisyphus/evidence/task-12-precheck.txt
  ```

  **Commit**: YES
  - Message: `feat(settlement): 结算审批闭环 - 不可变锁定+前置校验（无成本生成）`
  - Files: `backend/.../settlement/handler/SettlementWorkflowHandler.java`, `backend/.../settlement/controller/StlSettlementController.java`, `database/migration/V28__init_settlement_approval_template.sql`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 13. cost_target 审批 workflow + 生效机制

  **What to do**:
  - 新建 `CostTargetWorkflowHandler implements WorkflowBusinessHandler`，isCritical()=true
  - `onApproved`：approval_status=APPROVED + 触发 is_active 切换（旧版本失效、新版本生效）
  - 审批通过后更新 cost_summary.cost_target_id 关联到生效版本
  - 实现 submit 端点 + 校验（cost_target_item 不能有 null/0 的必填科目）
  - Flyway 审批模板迁移

  **Must NOT do**:
  - 禁止无审批即生效
  - 禁止审批通过后存在多个 is_active=1

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 审批回调 + 版本切换 + cost_summary 关联，中等复杂度
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 24（集成测试）
  - **Blocked By**: Task 6（cost_target 实体）

  **References**:
  - **Pattern References**:
    - `backend/.../variation/handler/VarOrderWorkflowHandler.java` - Handler 范本
    - `database/migration/V17__init_variation_approval_template.sql` - 审批模板范本
  - **API/Type References**:
    - `backend/.../cost/service/CostTargetService.java:activate()` - Task 6 的版本切换方法
  - **WHY Each Reference Matters**:
    - Handler 范本一致性；activate() 复用 Task 6 实现的版本切换逻辑

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 目标成本审批通过后生效并关联 cost_summary
    Tool: Bash (curl + mysql)
    Steps:
      1. 创建目标成本 v2.0（DRAFT）并提交审批
      2. 通过审批
      3. 查询 cost_target WHERE project_id=1，断言 v2.0 is_active=1，v1.0 is_active=0
      4. 查询 cost_summary.cost_target_id，断言指向 v2.0 的 id
    Expected Result: 审批生效+版本切换+关联
    Evidence: .sisyphus/evidence/task-13-approved-active.txt
  ```

  **Commit**: YES
  - Message: `feat(cost): 目标成本审批 workflow + 生效切换机制`
  - Files: `backend/.../cost/handler/CostTargetWorkflowHandler.java`, `database/migration/V29__init_cost_target_approval_template.sql`
  - Pre-commit: `./mvnw test`

- [x] 14. 预警中心 alert_log 实体 + 8 规则 + 定时批处理

  **What to do**:
  - 实体：AlertLog（与 V24 alert_log 表对应）+ Mapper
  - 实现 8 类预警规则评估器（策略模式或规则集）：
    1. 动态成本超目标（dynamic_cost > target_cost）
    2. 材料成本超预算（材料科目实际 > 材料目标）
    3. 分包成本超合同（分包计量累计 > 分包合同金额）
    4. 合同超期（当前日期 > end_date）
    5. 付款超比例（已付款/currentAmount > 约定比例）
    6. 质保金提前释放（当前日期 < 质保金到期日）
    7. 合同即将到期（end_date - now < 30天）
    8. 变更签证未及时确认（APPROVING 超 N 天）
  - 定时批处理：`@Scheduled(cron = "0 */30 * * * ?")` 评估有变化的项目，结果落 alert_log
  - Controller：预警列表查询 + 标记已读 + 手动触发评估端点
  - 提供 `POST /alerts/batch-evaluate` 手动触发端点（供测试用）

  **Must NOT do**:
  - ⛔ 禁止在事务路径中同步评估（如成本生成回调中）
  - ⛔ 禁止用 DB 触发器
  - ⛔ 禁止发送邮件/短信（仅存储）

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: 8 类规则 + 定时批处理 + 性能（1000项目<30s），高复杂度逻辑
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 19（预警前端）, Task 24（集成测试）
  - **Blocked By**: Task 4（alert_log 表），Task 9（动态成本正确）

  **References**:
  - **Pattern References**:
    - `backend/.../cost/service/CostSummaryService.java` - @Scheduled 定时任务范式
    - `backend/.../cost/strategy/CostGenerationStrategy.java` - 策略模式（8规则可照此组织）
  - **External References**:
    - `01.md §八` - 预警规则详细定义（合同/成本/付款/结算四类）
    - `02.md §10` - 预警阈值规则
  - **WHY Each Reference Matters**:
    - CostSummaryService 已有 @Scheduled 范式可复用
    - 01.md §八 是 8 类预警规则的权威定义来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 动态成本超目标触发预警
    Tool: Bash (curl + mysql)
    Preconditions: 项目动态成本 1200000 > 目标成本 1000000
    Steps:
      1. curl -X POST /api/alerts/batch-evaluate -H "Authorization: Bearer $TOKEN"
      2. 断言 HTTP 200
      3. curl "/api/alerts?projectId=1" | jq '.[] | select(.ruleType=="COST_OVERRUN")'
      4. 断言存在 1 条 severity=HIGH 的预警
    Expected Result: 预警正确触发并存储
    Evidence: .sisyphus/evidence/task-14-cost-overrun-alert.txt

  Scenario: 批处理性能（100项目<30s）
    Tool: Bash (time + curl)
    Steps:
      1. 准备 100 个项目数据
      2. time curl -X POST /api/alerts/batch-evaluate
      3. 断言 real < 30s
    Expected Result: 性能达标
    Evidence: .sisyphus/evidence/task-14-batch-perf.txt
  ```

  **Commit**: YES
  - Message: `feat(alert): 预警中心 - 8类规则+定时批处理+alert_log`
  - Files: `backend/.../alert/entity/AlertLog.java`, `backend/.../alert/service/AlertEvaluationService.java`, `backend/.../alert/rule/*.java`, `backend/.../alert/controller/AlertController.java`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 15. 驾驶舱后端 5 角色视图聚合 API（租户过滤+索引）

  **What to do**:
  - 实现 5 个角色视图聚合端点：
    - `GET /dashboard/project-manager`（待办/进度滞后/待审批/即将到期）
    - `GET /dashboard/business-manager`（合同总额/变更/签证/计量/付款比例/结算进度）
    - `GET /dashboard/cost-manager`（目标/动态/偏差/锁定/已发生/待发生/利润）
    - `GET /dashboard/finance`（待付款/已审批未支付/超比例/质保金到期）
    - `GET /dashboard/management`（项目排名/合同总额/动态成本/预计利润/重大风险）
  - 数据源：预聚合的 cost_summary（禁止拉原始 cost_item）+ ct_contract + pay_record + wf_task + alert_log
  - **租户过滤**（关键）：每个查询手动 `.eq(Entity::getTenantId, UserContext.getCurrentTenantId())`
  - 下钻端点：`GET /dashboard/project/{id}/cost-breakdown`（按科目，2级下钻上限）
  - 时间范围筛选支持（本月/本季度/本年）

  **Must NOT do**:
  - ⛔ 禁止拉原始 cost_item（用 cost_summary）
  - ⛔ 禁止下钻超 2 级
  - ⛔ 禁止跨租户返回数据（每查询手动加租户过滤）
  - ⛔ 禁止自定义 KPI/图表构建器

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 5 角色聚合 + 租户安全 + 性能优化，需理解全模块数据关系
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 20, 21（驾驶舱前端）
  - **Blocked By**: Task 5（costSubjectId），Task 8（结算实体），Task 9（动态成本正确）

  **References**:
  - **Pattern References**:
    - `backend/.../cost/service/CostSummaryService.java:getProjectSummary()` - 汇总查询范式
    - `backend/.../cost/controller/CostSummaryController.java` - Controller 范式 + 租户过滤
    - `backend/.../workflow/service/` 待办查询 - wf_task 查询范式
  - **API/Type References**:
    - `backend/.../common/UserContext.java:getCurrentTenantId()` - 租户上下文
  - **External References**:
    - `02.md §6.1-6.3` - 角色化驾驶舱设计、KPI 定义、图表与下钻
  - **WHY Each Reference Matters**:
    - CostSummaryService 提供聚合数据基础
    - UserContext 是租户过滤的关键（Metis 强调手动过滤）
    - 02.md §6 是 5 角色视图的权威设计来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 成本经理视图返回正确指标
    Tool: Bash (curl)
    Steps:
      1. curl /api/dashboard/cost-manager?projectId=1 -H "Authorization: Bearer $TOKEN"
      2. 断言响应含 targetCost, dynamicCost, costDeviation, contractLockedCost, actualCost, estimatedRemainingCost, expectedProfit
      3. 断言 dynamicCost = actualCost + estimatedRemainingCost
    Expected Result: 成本经理视图数据完整正确
    Evidence: .sisyphus/evidence/task-15-cost-manager-view.txt

  Scenario: 租户隔离 - 跨租户数据不可见
    Tool: Bash (curl)
    Preconditions: 租户A和租户B各有项目
    Steps:
      1. 用租户A token 调用 /api/dashboard/management
      2. 断言返回项目仅属于租户A
      3. 用租户B token 调用，断言仅返回租户B项目
    Expected Result: 租户隔离生效
    Evidence: .sisyphus/evidence/task-15-tenant-isolation.txt

  Scenario: 成本下钻按科目（2级上限）
    Tool: Bash (curl)
    Steps:
      1. curl /api/dashboard/project/1/cost-breakdown
      2. 断言返回按 cost_subject_id 分组的成本（非 null 分组）
    Expected Result: 下钻数据正确
    Evidence: .sisyphus/evidence/task-15-drilldown.txt
  ```

  **Commit**: YES
  - Message: `feat(dashboard): 5角色经营驾驶舱聚合API（租户过滤+科目下钻）`
  - Files: `backend/.../dashboard/controller/DashboardController.java`, `backend/.../dashboard/service/DashboardService.java`, `backend/.../dashboard/vo/*.java`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

### Wave 4 — 前端

- [x] 16. 目标成本管理前端页

  **What to do**:
  - 新建目标成本管理页：版本列表、新建/编辑目标成本（按科目分解明细表格）、提交审批、版本切换
  - 复用 StepWizard/编辑器组件模式
  - 接入 Task 6/13 的后端 API
  - 成本科目树选择器（复用现有 cost_subject 树）

  **Must NOT do**:
  - 禁止允许前端直接切换 is_active（须走审批）
  - 禁止 API 错误静默回退 Mock

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 前端页面 + 表格编辑器 + 树选择器
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4（与 Task 17-21）
  - **Blocks**: Task 24（集成测试）
  - **Blocked By**: Task 6, 13（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/components/ContractItemEditor.vue` - 明细表格编辑器范式
    - `frontend-admin/src/pages/contract/` - 业务页面结构范式
    - `frontend-admin/src/stores/contract.ts` - Pinia store 范式
  - **WHY Each Reference Matters**:
    - ContractItemEditor 是按行编辑明细的范本（目标成本按科目分解类似）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 创建目标成本并提交审批
    Tool: Playwright
    Steps:
      1. 登录，导航到 /cost-target
      2. 点击"新建目标成本"，填写版本号 v1.0、总额
      3. 在科目明细表格添加 2 行（材料/分包科目+金额）
      4. 点击保存，断言列表出现新版本
      5. 点击"提交审批"，断言状态变为"审批中"
    Expected Result: 创建+提交成功
    Evidence: .sisyphus/evidence/task-16-create-target.png

  Scenario: API 错误显示弹窗（不静默）
    Tool: Playwright
    Steps:
      1. 模拟后端 500
      2. 断言出现错误弹窗提示
    Expected Result: 错误可见
    Evidence: .sisyphus/evidence/task-16-error-toast.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 目标成本管理页`
  - Files: `frontend-admin/src/pages/cost-target/`, `frontend-admin/src/stores/costTarget.ts`
  - Pre-commit: `pnpm build`

- [x] 17. 合同变更前端（合同详情新增 tab）

  **What to do**:
  - 合同详情页新增"合同变更"tab（现有 tab：清单/付款条件/审批记录）
  - 变更列表 + 新建变更表单（变更类型/前后金额/原因）+ 提交审批 + 审批记录
  - 展示变更对 currentAmount 的影响
  - 接入 Task 7/11 API

  **Must NOT do**:
  - 禁止允许编辑已生成成本的变更
  - 禁止与签证变更（VAR_ORDER）页面混淆

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 合同详情页 tab 扩展 + 表单
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 24
  - **Blocked By**: Task 7, 11（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/pages/contract/` 合同详情页 - tab 结构（清单/付款条件/审批记录）
    - `frontend-admin/src/components/PaymentTermEditor.vue` - 表单编辑器范式
  - **WHY Each Reference Matters**:
    - 合同详情页是 tab 扩展目标，需理解现有 3 个 tab 的组织

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 合同详情页查看并创建变更
    Tool: Playwright
    Steps:
      1. 登录，导航到合同详情页
      2. 点击"合同变更"tab
      3. 点击"新建变更"，填写金额变更 +50000、原因
      4. 保存，断言列表出现新变更，状态"草稿"
      5. 提交审批，断言状态"审批中"
    Expected Result: 变更创建+提交成功
    Evidence: .sisyphus/evidence/task-17-create-change.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 合同详情新增合同变更tab`
  - Files: `frontend-admin/src/pages/contract/`, `frontend-admin/src/components/ContractChangeEditor.vue`
  - Pre-commit: `pnpm build`

- [x] 18. 结算管理前端（列表+详情多 tab）

  **What to do**:
  - 新建结算管理页：结算列表（筛选项目/合同/类型/状态）+ 关键指标卡（结算金额/未付/质保金）
  - 结算详情页多 tab：基本信息→汇总数据→变更签证→付款明细→成本明细→附件→审批记录
  - 详情 tab 展示自动汇总数据，支持反查来源单据（source_type/source_id 跳转）
  - 接入 Task 8/12 API

  **Must NOT do**:
  - 禁止允许前端手工修改 finalAmount
  - 禁止允许编辑 FINALIZED 状态结算

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 列表+多 tab 详情页，前端工作量较大
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 24
  - **Blocked By**: Task 8, 12（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/pages/contract/` 合同详情 - 多 tab 详情页范式
    - `frontend-admin/src/pages/approval/` - 审批记录 Timeline 范式
    - `frontend-admin/src/components/` ContractStatusTag - 状态 Tag 范式
  - **WHY Each Reference Matters**:
    - 合同详情多 tab 是结算详情页的直接范本

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 结算列表与详情查看
    Tool: Playwright
    Steps:
      1. 导航到 /settlement
      2. 断言列表渲染，含结算金额/未付/质保金指标卡
      3. 点击一条结算进入详情
      4. 断言详情页有 7 个 tab，汇总数据 tab 显示自动计算金额
    Expected Result: 列表+详情正常
    Evidence: .sisyphus/evidence/task-18-settlement-list.png

  Scenario: 来源单据反查跳转
    Tool: Playwright
    Steps:
      1. 详情页"成本明细"tab
      2. 点击某条明细的来源链接
      3. 断言跳转到对应计量/变更单据
    Expected Result: 反查跳转生效
    Evidence: .sisyphus/evidence/task-18-drilldown.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 结算管理列表+多tab详情页`
  - Files: `frontend-admin/src/pages/settlement/`, `frontend-admin/src/stores/settlement.ts`
  - Pre-commit: `pnpm build`

- [x] 19. 预警中心前端

  **What to do**:
  - 新建预警中心页：预警列表（按类型/严重度/项目筛选）+ 严重度标识（红/黄/灰）+ 标记已读
  - 手动触发评估按钮
  - 接入 Task 14 API

  **Must NOT do**:
  - 禁止允许用户禁用系统级预警规则（仅标记已读）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 列表页 + 筛选 + 状态标识
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 24
  - **Blocked By**: Task 14（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/pages/approval/` 待办列表 - 列表+筛选范式
    - `frontend-admin/src/components/` 状态 Tag 组件
  - **WHY Each Reference Matters**:
    - 待办列表是预警列表的直接范本（列表+筛选+状态）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 预警列表展示与标记已读
    Tool: Playwright
    Steps:
      1. 导航到 /alerts
      2. 断言列表渲染预警，含严重度标识
      3. 点击"标记已读"，断言该预警状态更新
    Expected Result: 预警列表+已读功能正常
    Evidence: .sisyphus/evidence/task-19-alert-list.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 预警中心页`
  - Files: `frontend-admin/src/pages/alert/`, `frontend-admin/src/stores/alert.ts`
  - Pre-commit: `pnpm build`

- [x] 20. 驾驶舱前端 - 项目经理 + 商务经理视图

  **What to do**:
  - 改造现有 dashboard/index.vue：角色 Tab 切换
  - 项目经理视图：待办/进度滞后/待审批/即将到期卡片 + 待办列表
  - 商务经理视图：合同总额/变更/签证/付款比例 KPI + 合同类型分布饼图（ECharts）
  - 图表点击下钻到对应列表页
  - 时间范围筛选（本月/本季度/本年）
  - 接入 Task 15 API

  **Must NOT do**:
  - 禁止下钻超 2 级
  - 禁止实时刷新/WebSocket（轮询可接受）
  - 禁止自定义图表构建器

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: ECharts 图表 + 角色视图 + 下钻交互
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 24
  - **Blocked By**: Task 15（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/pages/dashboard/index.vue` - 现有驾驶舱（KPI 卡片，需改造）
    - `frontend-admin/src/` ECharts 使用范式（如有现存图表）
  - **External References**:
    - `02.md §6.1` - 项目经理/商务经理视图 KPI 定义
    - ECharts 官方文档 - 饼图/下钻交互
  - **WHY Each Reference Matters**:
    - 现有 dashboard 是改造起点；02.md §6.1 是视图设计来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 项目经理视图渲染真实数据
    Tool: Playwright
    Steps:
      1. 登录，导航到 /dashboard
      2. 切换到"项目经理"Tab
      3. 断言待办/即将到期卡片显示非零数据（接入真实 API）
    Expected Result: 真实数据渲染
    Evidence: .sisyphus/evidence/task-20-pm-view.png

  Scenario: 商务经理视图饼图下钻
    Tool: Playwright
    Steps:
      1. 切换到"商务经理"Tab
      2. 断言合同类型分布饼图渲染
      3. 点击饼图某扇区，断言跳转到合同台账筛选页
    Expected Result: 图表+下钻生效
    Evidence: .sisyphus/evidence/task-20-business-drilldown.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 驾驶舱项目经理+商务经理视图`
  - Files: `frontend-admin/src/pages/dashboard/`, `frontend-admin/src/components/dashboard/`
  - Pre-commit: `pnpm build`

- [x] 21. 驾驶舱前端 - 成本 + 财务 + 管理层视图

  **What to do**:
  - 成本经理视图：目标/动态/偏差/利润 KPI + 成本构成堆叠柱状图（按科目）+ 产值趋势折线图
  - 财务视图：待付款/已审批未支付/超比例/质保金到期 KPI
  - 管理层视图：项目经营排名 + 合同总额/动态成本/预计利润 + 重大风险/预警汇总列表
  - 成本构成图下钻到成本明细台账（2级）
  - 接入 Task 15 API

  **Must NOT do**:
  - 禁止下钻超 2 级
  - 禁止跨租户数据（依赖后端租户过滤）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 多图表（堆叠柱状/折线）+ 3 角色视图 + 下钻
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 24
  - **Blocked By**: Task 15（后端 API）

  **References**:
  - **Pattern References**:
    - `frontend-admin/src/pages/dashboard/index.vue` - 驾驶舱基础（Task 20 改造后）
    - Task 20 的 ECharts 组件 - 复用图表组件
  - **External References**:
    - `02.md §6.2-6.3` - 成本/财务/管理层 KPI + 图表下钻定义
  - **WHY Each Reference Matters**:
    - 复用 Task 20 建立的图表组件基础；02.md §6.2-6.3 是视图设计来源

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 成本经理视图成本构成图下钻
    Tool: Playwright
    Steps:
      1. 切换到"成本经理"Tab
      2. 断言目标/动态/偏差/利润 KPI 显示，且 利润=收入-动态成本
      3. 断言成本构成堆叠柱状图按科目渲染（非 null 分组）
      4. 点击柱状图某科目，断言下钻到成本明细
    Expected Result: 成本视图+下钻正确
    Evidence: .sisyphus/evidence/task-21-cost-manager.png

  Scenario: 管理层视图项目排名
    Tool: Playwright
    Steps:
      1. 切换到"管理层"Tab
      2. 断言项目经营排名列表 + 预警汇总渲染
    Expected Result: 管理层视图正常
    Evidence: .sisyphus/evidence/task-21-management.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): 驾驶舱成本+财务+管理层视图`
  - Files: `frontend-admin/src/pages/dashboard/`, `frontend-admin/src/components/dashboard/`
  - Pre-commit: `pnpm build`

### Wave 5 — 技术债务收尾 + 集成测试

- [x] 22. 技术债务 M4：token HttpOnly Cookie 改造

  **What to do**:
  - 将 access token 从 localStorage 改为 HttpOnly Cookie
  - refresh token 改为 HttpOnly Cookie
  - 后端：登录响应通过 Set-Cookie 下发，增加 CSRF 防护（如 SameSite=Strict 或 CSRF token）
  - 前端：request.ts 拦截器适配（移除手动 Authorization header，依赖 Cookie）
  - 401 刷新逻辑适配 Cookie 模式
  - 评估移动端兼容性（uni-app 预留，文档说明）

  **Must NOT do**:
  - 禁止破坏现有登录/刷新/登出/会话过期流程（须全部测试）
  - 禁止移除 Redis 黑名单机制

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 前后端联动 + CSRF 方案 + 全认证流程回归
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 5
  - **Blocks**: Task 25
  - **Blocked By**: None（独立技术债务，但建议在功能完成后做）

  **References**:
  - **Pattern References**:
    - `backend/.../auth/` 登录控制器 + JWT 签发 - token 下发改造点
    - `frontend-admin/src/utils/request.ts` - 401 刷新拦截器
    - `frontend-admin/src/stores/user.ts` - token 存储逻辑
  - **External References**:
    - Spring Security HttpOnly Cookie + CSRF 文档
  - **WHY Each Reference Matters**:
    - request.ts 是前端 token 处理核心，改造影响最大

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 登录后 token 存于 HttpOnly Cookie
    Tool: Playwright
    Steps:
      1. 登录
      2. 检查 document.cookie，断言 JS 无法读取 token（HttpOnly）
      3. 检查响应头 Set-Cookie 含 HttpOnly; SameSite
      4. 断言后续请求正常携带 Cookie 认证
    Expected Result: token 不可被 JS 读取
    Evidence: .sisyphus/evidence/task-22-httponly.png

  Scenario: 全认证流程回归
    Tool: Bash (curl) + Playwright
    Steps:
      1. 登录→访问受保护资源→token过期→自动刷新→登出
      2. 断言每步正常
      3. 登出后断言 token 进入 Redis 黑名单
    Expected Result: 完整流程通过
    Evidence: .sisyphus/evidence/task-22-auth-flow.txt
  ```

  **Commit**: YES
  - Message: `fix(security): token 改用 HttpOnly Cookie + CSRF 防护 (M4)`
  - Files: `backend/.../auth/`, `frontend-admin/src/utils/request.ts`, `frontend-admin/src/stores/user.ts`
  - Pre-commit: `./mvnw test && pnpm build`

- [x] 23. 技术债务 P18：vite 5→6 升级

  **What to do**:
  - 升级 vite 到 6.x（修复 esbuild 漏洞）
  - 升级相关插件（@vitejs/plugin-vue 等）
  - 验证 dev server 启动 + 生产构建正常
  - 修复升级引入的破坏性变更

  **Must NOT do**:
  - 禁止在 dev server 启动失败或构建失败时合并
  - 禁止顺带升级无关大版本依赖

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 依赖升级 + 构建验证，范围明确
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 5
  - **Blocks**: Task 25
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `frontend-admin/package.json` - 当前 vite 版本
    - `frontend-admin/vite.config.ts` - vite 配置
  - **External References**:
    - Vite 6 迁移指南
  - **WHY Each Reference Matters**:
    - vite.config.ts 可能需适配 vite 6 配置变更

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: vite 升级后构建成功
    Tool: Bash
    Steps:
      1. pnpm install
      2. pnpm build，断言退出码 0
      3. pnpm dev 启动，断言 dev server 正常启动（端口监听）
      4. npm audit，断言 esbuild 漏洞消除
    Expected Result: 升级成功，漏洞修复
    Evidence: .sisyphus/evidence/task-23-vite-build.txt
  ```

  **Commit**: YES
  - Message: `chore(deps): 升级 vite 5→6 修复 esbuild 漏洞 (P18)`
  - Files: `frontend-admin/package.json`, `frontend-admin/vite.config.ts`
  - Pre-commit: `pnpm build`

- [x] 24. 全链路集成测试（CT_CHANGE/结算/动态成本/预警）

  **What to do**:
  - 编写集成测试覆盖第 3 阶段全部链路（沿用第 1/2 阶段 H2 local profile 模式）：
    1. 合同变更链路：创建合同→审批→创建变更→审批→验证 currentAmount 更新+成本生成
    2. 结算链路：合同+变更+计量+付款→创建结算→审批→验证锁定+无成本生成
    3. 动态成本链路：验证公式修正后 dynamic_cost=actual+remaining、利润测算正确
    4. 目标成本链路：创建→审批→版本切换→cost_summary 关联
    5. 预警链路：触发各类预警→验证 alert_log 记录
    6. 跨模块集成：合同变更→动态成本变化→驾驶舱反映→预警触发
  - 边界测试：CT_CHANGE+VAR_ORDER 共存无重复计费、结算前置校验

  **Must NOT do**:
  - 禁止测试覆盖率造假（必须真实断言数据状态）

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: 多链路集成测试 + 跨模块验证 + 边界场景，高复杂度
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖所有功能完成）
  - **Parallel Group**: Wave 5
  - **Blocks**: Task 25
  - **Blocked By**: Task 11,12,13,14 + 16-21（所有功能）

  **References**:
  - **Pattern References**:
    - `backend/src/test/java/com/cgcpms/` 第2阶段集成测试（7 用例，采购→验收→成本→付款）- 集成测试范式
    - 第1阶段 11 用例集成测试 - H2 local profile 配置
  - **External References**:
    - `doc/第2阶段成本归集与资金闭环测试报告.md` - 测试报告范式
  - **WHY Each Reference Matters**:
    - 第2阶段集成测试是直接范本（同样的全链路风格）

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: 合同变更全链路集成测试
    Tool: Bash (mvnw test)
    Steps:
      1. ./mvnw test -Dtest=ContractChangeIntegrationTest -Dspring-boot.run.profiles=local
      2. 断言测试通过：合同→变更→审批→currentAmount更新+成本生成
    Expected Result: 链路测试 PASS
    Evidence: .sisyphus/evidence/task-24-change-integration.txt

  Scenario: CT_CHANGE+VAR_ORDER 共存无重复计费
    Tool: Bash (mvnw test)
    Steps:
      1. 合同→CT_CHANGE +50000（生成 CT_CHANGE 成本）→VAR_ORDER +10000（生成 VAR_ORDER 成本）
      2. 断言 currentAmount = 原始+50000（非+60000，VAR_ORDER 不改合同金额）
      3. 断言 cost_summary.dynamicCost 含三个来源（CT_CONTRACT+CT_CHANGE+VAR_ORDER）
    Expected Result: 无重复计费
    Evidence: .sisyphus/evidence/task-24-no-double-count.txt

  Scenario: 结算不生成成本验证
    Tool: Bash (mvnw test)
    Steps:
      1. 全链路结算测试
      2. 断言 cost_item WHERE source_type='SETTLEMENT' 为 0 条
    Expected Result: 结算纯汇总
    Evidence: .sisyphus/evidence/task-24-settlement-no-cost.txt
  ```

  **Commit**: YES
  - Message: `test(phase3): 全链路集成测试（变更/结算/动态成本/预警）`
  - Files: `backend/src/test/java/com/cgcpms/*IntegrationTest.java`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=local`

- [x] 25. MySQL 8.0 全栈验证 + 测试报告

  **What to do**:
  - 在 MySQL 8.0 环境（dev profile）跑通第 2、3 阶段全部集成测试（补齐第 2 阶段仅在 H2 验证的缺口）
  - 验证所有 Flyway 迁移（V21-V29）在 MySQL 正确执行
  - 验证动态成本 backfill 在 MySQL 数据上正确
  - 编写第 3 阶段测试报告（照第 2 阶段报告格式）：功能验收/一致性幂等验收/测试验收/已知限制
  - 报告归档到 `doc/第3阶段成本分析与合同深化测试报告.md`

  **Must NOT do**:
  - 禁止跳过 MySQL 验证仅报告 H2 结果
  - 禁止测试报告夸大（须如实记录通过/失败/限制）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 双环境验证 + 测试报告撰写
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: NO（最后收尾）
  - **Parallel Group**: Wave 5
  - **Blocks**: Final Verification Wave
  - **Blocked By**: Task 22, 23, 24

  **References**:
  - **Pattern References**:
    - `doc/第2阶段成本归集与资金闭环测试报告.md` - 测试报告格式范本
    - `backend/src/main/resources/application-dev.yml` - MySQL dev profile 配置
  - **WHY Each Reference Matters**:
    - 第2阶段测试报告是格式直接范本

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:
  ```
  Scenario: MySQL 全栈集成测试通过
    Tool: Bash (mvnw test + Docker MySQL)
    Steps:
      1. docker compose up -d（启动 MySQL）
      2. ./mvnw test -Dspring-boot.run.profiles=dev
      3. 断言第2、3阶段全部集成测试 PASS
      4. ./mvnw flyway:info，断言 V21-V29 全部 Success
    Expected Result: MySQL 双环境通过
    Evidence: .sisyphus/evidence/task-25-mysql-fullstack.txt

  Scenario: 测试报告生成
    Tool: 文件检查
    Steps:
      1. 断言 doc/第3阶段成本分析与合同深化测试报告.md 存在
      2. 断言含功能验收/一致性幂等验收/测试验收/已知限制章节
    Expected Result: 报告完整
    Evidence: .sisyphus/evidence/task-25-test-report.txt
  ```

  **Commit**: YES
  - Message: `test(phase3): MySQL 8.0 全栈验证 + 第3阶段测试报告`
  - Files: `doc/第3阶段成本分析与合同深化测试报告.md`
  - Pre-commit: `./mvnw test -Dspring-boot.run.profiles=dev`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. 呈现汇总结果给用户并获取明确 "okay" 后才能完成。
>
> **不要在验证后自动继续。等待用户明确批准后才标记工作完成。**
> **在获得用户 okay 前，绝不将 F1-F4 标记为已勾选。** 拒绝或用户反馈 → 修复 → 重跑 → 再呈现 → 等待 okay。

- [x] F1. **计划合规审计** — `oracle`
  通读计划。逐项验证"Must Have"：实现存在（读文件、curl 端点、跑命令）。逐项验证"Must NOT Have"：搜索代码库禁止模式——发现则带 file:line 拒绝（重点：结算是否调用 CostGenerationService、CT_CHANGE 是否改 contractAmount、是否存在双公式）。检查 `.sisyphus/evidence/` 证据文件存在。对比交付物。
  输出: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **代码质量评审** — `unspecified-high`
  运行 `mvnw test`（H2 + MySQL）+ 前端 lint + build。审查所有变更文件：`as any`/`@ts-ignore`、空 catch、生产 console.log、注释掉的代码、未用 import。检查 AI slop：过度注释、过度抽象、泛化命名（data/result/item/temp）。验证租户过滤在所有新查询中存在。
  输出: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **真实手工 QA** — `unspecified-high`（+ `playwright` skill）
  从干净状态开始。执行每个任务的每个 QA 场景——按精确步骤、捕获证据。测试跨任务集成：合同→变更→成本→驾驶舱→预警端到端。测试边界：合同变更后结算、目标成本多版本切换、租户隔离。存 `.sisyphus/evidence/final-qa/`。
  输出: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **范围保真度检查** — `deep`
  逐任务：读"What to do"、读实际 diff（git log/diff）。验证 1:1——规格内全部实现（无遗漏），规格外无多余（无蔓延，特别防驾驶舱→BI 蔓延）。检查"Must NOT do"合规。检测跨任务污染：任务 N 改动任务 M 的文件。标记无法归因的变更。
  输出: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

> 按任务分组提交，沿用项目规范：`type(scope): desc`

- 见各任务 Commit 字段

---

## Success Criteria

### Verification Commands
```bash
# 后端编译 + 测试（H2）
cd backend && ./mvnw clean test -Dspring-boot.run.profiles=local

# 后端测试（MySQL）
cd backend && ./mvnw test -Dspring-boot.run.profiles=dev

# 前端构建
cd frontend-admin && pnpm build

# Flyway 迁移验证
cd backend && ./mvnw flyway:info
```

### Final Checklist
- [ ] 所有"Must Have"功能存在并通过验收
- [ ] 所有"Must NOT Have"护栏未被违反
- [ ] H2 + MySQL 双环境集成测试全部通过
- [ ] 动态成本公式 backfill 完成且 staging 验证 diff 报告存档
- [ ] 5 角色驾驶舱接入真实数据，租户隔离验证通过
- [ ] 技术债务 H2/M4/P18 修复完成
