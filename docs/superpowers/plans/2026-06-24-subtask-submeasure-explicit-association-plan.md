# 分包任务与计量显式关联实施计划书

> 编写日期：2026-06-24  
> 目标：将 `SubMeasure` 与 `SubTask` 的关系从当前通过 `projectId + contractId + partnerId` 的**间接关联**升级为基于 `sub_task_id` 的**显式关联**，减少业务歧义、提升追溯性，并为后续成本、预警、驾驶舱、报表能力打下更清晰的数据基础。

---

## 设计决策（实施前先冻结）

1. **`sub_task_id` 本阶段为可空字段**。  
   目标是先完成“显式关联能力引入”，不是立即强推所有计量单必绑任务。

2. **历史数据不做强制回填**。  
   旧 `sub_measure` 记录允许保持 `sub_task_id = null`，优先保证升级兼容与线上安全。

3. **新增/编辑表单前端默认引导选择任务，但本阶段后端暂不强制必填**。  
   后续若业务确认已稳定，再升级为条件必填或全量必填。

4. **后端校验顺序固定为：存在性 → 租户隔离 → 项目一致性 → 合同一致性 → 合作方一致性**。  
   避免跨租户数据被错误暴露成普通业务校验错误。

5. **查询回填统一返回 `subTaskId + subTaskCode + subTaskName`**。  
   不使用“只返回 id、名称/编码可选”的模糊策略，避免前端二次查询或展示不一致。

6. **第一期目标是“显式关联不破坏现有链路”，不是一次性让所有下游都任务维度化**。  
   成本、付款、驾驶舱、预警等下游模块先做兼容性确认，按需渐进增强。

---

## 一、任务拆解

### 模块 A：数据库模型与迁移

#### 任务 A1：为 `sub_measure` 增加 `sub_task_id` 列
- **任务名称与简要说明**  
  在 `sub_measure` 表增加可空字段 `sub_task_id`，采用渐进式迁移，不破坏现有历史数据。
- **输入/输出**  
  - 输入：当前 `sub_measure` 结构、Flyway 迁移规范  
  - 输出：MySQL / H2 双迁移脚本，新增 `sub_task_id`
- **涉及的文件建议**  
  - ✨新增 `backend/src/main/resources/db/migration/Vxx__add_sub_task_id_to_sub_measure.sql`  
  - ✨新增 `backend/src/main/resources/db/migration-h2/Vxx__add_sub_task_id_to_sub_measure.sql`
- **复杂度**：P0
- **验收标准**
  - **Given** 当前数据库已有 `sub_measure` 数据  
    **When** 执行 Flyway migration  
    **Then** 新增字段 `sub_task_id` 成功，旧数据不丢失，应用可正常启动
  - **边界条件**
    - 历史记录允许 `sub_task_id = null`
  - **异常场景**
    - H2 / MySQL 双环境 migration 均可执行，不出现 schema 漂移
  - **迁移兼容验证**
    - Given migration 前已有历史计量单且 `sub_task_id` 为空  
      When 升级后执行 `getPage/getById/submitForApproval`  
      Then 原有流程不因为空值而报错

#### 任务 A2：补充索引与约束策略
- **任务名称与简要说明**  
  为 `sub_task_id` 添加必要索引，并明确是否只做逻辑校验、不做数据库外键约束。
- **输入/输出**  
  - 输入：现有 `sub_task` / `sub_measure` 访问模式  
  - 输出：索引与约束方案
- **涉及的文件建议**  
  - 🔨修改任务 A1 的 migration 脚本
- **复杂度**：P1
- **依赖**：任务 A1
- **验收标准**
  - 检查项：
    - 至少存在 `idx_sub_measure_sub_task_id`（或等价索引）
    - 若不加物理外键，文档中明确原因（兼容历史数据 / 渐进迁移）
    - 若不加数据库外键，则 Service 层校验必须完整覆盖非法引用场景

---

### 模块 B：后端实体、VO、Service、Controller 改造

#### 任务 B1：更新 `SubMeasure` 实体 / VO / Type 映射
- **任务名称与简要说明**  
  在后端数据模型中增加 `subTaskId`，并对外暴露给前端。
- **输入/输出**  
  - 输入：`SubMeasure`、`SubMeasureVO`、`SubTaskVO` 当前结构  
  - 输出：`SubMeasure` 链路支持显式任务关联
- **涉及的文件建议**  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/entity/SubMeasure.java`  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/vo/SubMeasureVO.java`
- **复杂度**：P0
- **依赖**：任务 A1
- **验收标准**
  - **Given** 后端返回计量详情  
    **When** 前端读取响应  
    **Then** 可拿到 `subTaskId`

#### 任务 B2：`SubMeasureService` 增加显式关联校验
- **任务名称与简要说明**  
  在 create/update 时校验 `subTaskId` 是否存在、是否属于同项目/同合同/同合作方。
- **输入/输出**  
  - 输入：`SubTaskMapper`、`SubMeasureService` 当前逻辑  
  - 输出：后端统一校验逻辑
- **涉及的文件建议**  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubMeasureService.java`
  - 如需要：🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubTaskService.java`
- **复杂度**：P0
- **依赖**：任务 B1
- **验收标准**
  - **校验顺序要求**
    - 必须先校验：`subTaskId` 是否存在
    - 再校验：是否属于当前租户
    - 然后依次校验：项目 / 合同 / 合作方一致性
  - **正常场景**
    - Given `subTaskId` 属于当前项目/合同/合作方  
      When 创建或更新计量单  
      Then 保存成功
  - **边界条件**
    - Given 未选择 `subTaskId`  
      When 保存计量单  
      Then 本阶段应按“兼容空值”策略正常保存
  - **异常场景**
    - Given `subTaskId` 不存在 / 不属于当前项目 / 不属于当前合同 / 不属于当前合作方  
      When 保存  
      Then 返回明确业务错误码

#### 任务 B3：对外查询增加任务信息回填
- **任务名称与简要说明**  
  在 `getPage` / `getById` 结果中，把关联任务的展示信息回填给前端。
- **输入/输出**  
  - 输入：`SubMeasureService.getPage/getById` 现有拼装逻辑  
  - 输出：计量列表和详情可展示“关联分包任务”
- **涉及的文件建议**  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubMeasureService.java`  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/vo/SubMeasureVO.java`
- **复杂度**：P1
- **依赖**：任务 B2
- **验收标准**
  - **Given** 计量单已绑定分包任务  
    **When** 查询列表或详情  
    **Then** 统一返回 `subTaskId`、`subTaskCode`、`subTaskName`

#### 任务 B3.1：兼容历史空值记录的查询返回
- **任务名称与简要说明**  
  确保历史未绑定任务的计量单在查询时仍能正常展示，不因新增字段报错。
- **输入/输出**  
  - 输入：历史 `sub_task_id = null` 记录  
  - 输出：稳定的空值展示策略
- **涉及的文件建议**  
  - 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubMeasureService.java`
- **复杂度**：P1
- **依赖**：任务 B3
- **验收标准**
  - Given 历史计量单未绑定任务  
    When 查询列表和详情  
    Then 页面正常显示，任务字段为空但不报错

#### 任务 B4：检查下游读取方的兼容性
- **任务名称与简要说明**  
  检查成本、付款、驾驶舱、预警等现有读取逻辑在 `sub_task_id` 引入后是否需要增强或保持兼容。
- **输入/输出**  
  - 输入：`PayApplicationService`、`DashboardService`、`AlertEvaluationService`、`SubMeasureCostStrategy` 等当前用法  
  - 输出：兼容性确认清单
- **涉及的文件建议**  
  - 🔨修改 `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`（若展示或筛选需带任务维度）  
  - 🔨修改 `backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java`（如需任务维度分析）  
  - 🔨修改 `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`（如需任务维度预警）
  - 🔨检查 `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementQueryService.java`（未来结算来源按任务维度解释）  
  - 🔨检查 `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`（若未来任务级文档/附件挂载）
- **复杂度**：P1
- **依赖**：任务 B3
- **验收标准**
  - 检查项：
    - 现有成本生成不回归
    - 现有付款依据链路不回归
    - 未来如需任务维度筛选/分析，有明确可扩展点
    - 当前未改造的下游模块有明确“兼容但不增强”的范围说明

---

### 模块 C：前端页面与数据流改造

#### 任务 C1：在计量编辑页增加“关联分包任务”选择器
- **任务名称与简要说明**  
  在 `subcontract/measure.vue` 表单中增加任务下拉，允许用户显式绑定任务。
- **输入/输出**  
  - 输入：现有 `measure.vue` 表单结构、`subcontract.ts` API  
  - 输出：前端可选分包任务并提交 `subTaskId`
- **涉及的文件建议**  
  - 🔨修改 `frontend-admin/src/pages/subcontract/measure.vue`  
  - 🔨修改 `frontend-admin/src/api/modules/subcontract.ts`  
  - 🔨修改 `frontend-admin/src/types/subcontract.ts`
- **复杂度**：P0
- **依赖**：任务 B1
- **验收标准**
  - **Given** 页面已选择项目/合同/合作方  
    **When** 打开计量新增/编辑弹窗  
    **Then** 能看到“关联分包任务”下拉框
  - **异常场景**
    - 若无可选任务，下拉为空且给出合理提示

#### 任务 C2：前端联动筛选任务列表
- **任务名称与简要说明**  
  根据项目/合同/合作方条件过滤可选任务，避免选到无关任务。
- **输入/输出**  
  - 输入：`SubTask` 列表接口与当前筛选条件  
  - 输出：选择器只展示匹配的任务
- **涉及的文件建议**  
  - 🔨修改 `frontend-admin/src/pages/subcontract/measure.vue`  
  - 视情况：🔨修改 `frontend-admin/src/api/modules/subcontract.ts`
- **复杂度**：P1
- **依赖**：任务 C1
- **验收标准**
  - **Given** 当前计量表单已选合同/合作方  
    **When** 加载任务下拉  
    **Then** 仅展示符合条件的任务
  - **编辑态边界条件**
    - Given 旧记录已绑定的任务在最新筛选结果中不可见  
      When 用户打开编辑弹窗  
      Then 页面必须保留当前值并明确提示“当前关联任务已不满足筛选条件”或阻止保存，不能静默丢值

#### 任务 C3：列表页与详情页展示关联任务信息
- **任务名称与简要说明**  
  让“显式关联”在前端真正可见，而不是只作为隐含字段存在。
- **输入/输出**  
  - 输入：`SubMeasureVO` 新字段  
  - 输出：列表列/详情区域显示关联任务
- **涉及的文件建议**  
  - 🔨修改 `frontend-admin/src/pages/subcontract/measure.vue`
- **复杂度**：P1
- **依赖**：任务 C2、B3
- **验收标准**
  - **Given** 计量单已绑定任务  
    **When** 用户查看列表/详情  
    **Then** 可以直接看到关联任务名称/编码

---

### 模块 D：测试与回归保障

#### 任务 D1：后端测试扩展
- **任务名称与简要说明**  
  为 `SubMeasureServiceTest` 增加 `subTaskId` 相关成功/失败分支测试。
- **输入/输出**  
  - 输入：现有 `SubMeasureServiceTest`、`SubTaskService` 数据前置  
  - 输出：后端新增测试用例
- **涉及的文件建议**  
  - 🔨修改 `backend/src/test/java/com/cgcpms/subcontract/SubMeasureServiceTest.java`
- **复杂度**：P0
- **依赖**：任务 B2、B3
- **验收标准**
  - 正常场景：合法 `subTaskId` 保存成功  
  - 异常场景：非法 `subTaskId` 保存失败  
  - 边界场景：未绑定任务时的兼容行为符合设计
  - 迁移兼容场景：历史 `sub_task_id = null` 的记录查询/审批不报错

#### 任务 D2：前端测试扩展
- **任务名称与简要说明**  
  为 `measure.vue` 和 subcontract types / api 增加交互与类型测试。
- **输入/输出**  
  - 输入：现有前端测试基线  
  - 输出：前端相关回归测试
- **涉及的文件建议**  
  - 🔨修改或新增 `frontend-admin/src/pages/subcontract/**/__tests__/*`
- **复杂度**：P1
- **依赖**：任务 C1~C3
- **验收标准**
  - Given 页面渲染  
    When 用户选择分包任务并提交  
    Then 请求 payload 含 `subTaskId`

---

## 二、改动文件清单与计划

- ✨新增 `backend/src/main/resources/db/migration/Vxx__add_sub_task_id_to_sub_measure.sql`  
  MySQL migration：为 `sub_measure` 增加 `sub_task_id`，补索引，遵循 Flyway 不改历史迁移原则。

- ✨新增 `backend/src/main/resources/db/migration-h2/Vxx__add_sub_task_id_to_sub_measure.sql`  
  H2 migration：与 MySQL 同步字段与索引，避免测试环境 schema 漂移。

- 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/entity/SubMeasure.java`  
  增加 `subTaskId` 字段，建立显式关联的后端实体基础。

- 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/vo/SubMeasureVO.java`  
  增加 `subTaskId` 以及可选展示字段（如 `subTaskName` / `subTaskCode`）。

- 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubMeasureService.java`  
  增加 `subTaskId` 校验、保存逻辑、列表/详情回填逻辑。

- 🔨修改 `backend/src/main/java/com/cgcpms/subcontract/service/SubTaskService.java`（如需要）  
  提供任务筛选或轻量查询能力，服务前端选择器。

- 🔨修改 `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java`（视情况）  
  若付款依据展示需要任务维度，补充兼容性。

- 🔨修改 `backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java`（视情况）  
  若后续希望按任务维度聚合指标，预留数据回填点。

- 🔨检查 `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementQueryService.java`  
  当前不强制修改，但需确认引入 `sub_task_id` 后结算来源聚合和展示不回归。

- 🔨检查 `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java`  
  当前不强制修改，但若未来任务级附件挂载，要明确这里是权限扩展入口。

- 🔨修改 `frontend-admin/src/types/subcontract.ts`  
  为 `SubMeasureVO` 增加 `subTaskId` 及展示字段。

- 🔨修改 `frontend-admin/src/api/modules/subcontract.ts`  
  创建/更新/详情请求响应链路透传 `subTaskId`。

- 🔨修改 `frontend-admin/src/pages/subcontract/measure.vue`  
  增加“关联分包任务”下拉、任务展示列、数据联动。

- 🔨修改 `backend/src/test/java/com/cgcpms/subcontract/SubMeasureServiceTest.java`  
  增加显式关联成功/非法关联失败的回归测试。

- 🔨修改或新增 `frontend-admin/src/pages/subcontract/**/__tests__/*`  
  增加前端交互和类型回归测试。

---

## 三、数据流变化

### 当前数据流（改造前）
用户在前端创建计量单  
→ 选择项目 / 合同 / 合作方  
→ `POST /api/subcontract/measures`  
→ 后端仅根据 `projectId + contractId + partnerId` 保存  
→ 后续成本、付款、驾驶舱等只知道“这是一张计量单”，不知道它具体属于哪个分包任务

### 目标数据流（改造后）
用户在前端创建计量单  
→ 选择项目 / 合同 / 合作方  
→ 下拉选择“关联分包任务”  
→ `POST /api/subcontract/measures` 携带 `subTaskId`  
→ 后端校验任务是否属于同项目/同合同/同合作方  
→ 持久化到 `sub_measure.sub_task_id`  
→ `getPage/getById` 回填任务名称/编码  
→ 后续成本/付款/报表/驾驶舱可按任务维度追溯和分析

### 状态与数据格式变化
- `SubMeasure` / `SubMeasureVO` / 前端 `SubMeasureVO` 类型新增：`subTaskId`
- 统一新增展示字段：`subTaskName`、`subTaskCode`
- 前端表单状态增加：当前可选任务列表、任务选择器联动加载状态

---

## 四、影响范围与回归测试建议

### 1. 分包计量创建/编辑
- **影响范围**：`SubMeasureService`、`measure.vue`
- **回归建议**：创建一张带 `subTaskId` 的计量单并再次编辑，确认任务关联不会丢失

### 2. 分包任务列表与筛选
- **影响范围**：`SubTaskService`、前端任务下拉
- **回归建议**：在不同项目/合同/合作方条件下切换，确认下拉任务范围正确

### 3. 成本生成
- **影响范围**：`SubMeasureCostStrategy`
- **回归建议**：审批通过后自动生成成本，确认引入 `subTaskId` 后不影响现有成本归集

### 4. 付款依据关联
- **影响范围**：`PayApplicationService`
- **回归建议**：用计量作为付款依据创建付款申请，确认 basis 链路仍然成立

### 5. 驾驶舱/预警/报表
- **影响范围**：`DashboardService`、`AlertEvaluationService`、未来报表
- **回归建议**：检查现有项目级汇总不回归，同时评估是否要新增任务维度分析视图

---

## 五、冒烟测试方案

1. 新建分包任务，再创建一张绑定该任务的计量单，预期保存成功。  
2. 创建计量单时故意传入不属于当前合同/合作方的 `subTaskId`，预期后端明确拒绝。  
3. 编辑已有计量单并修改关联任务，预期详情页和列表页都能正确显示新任务。  
4. 不选择任务创建计量单（若当前阶段允许为空），预期兼容成功且不影响旧数据。  
5. 提交审批通过后，计量成本生成逻辑仍正常执行。  
6. 用该计量单作为付款依据创建付款申请，预期依据追溯正常。  
7. 切换项目/合同/合作方后，任务下拉列表应随之刷新并只显示匹配项。  
8. H2 / MySQL 双环境执行 migration 后均可正常启动并通过相关测试。  
9. 历史 `sub_task_id = null` 的计量单在列表、详情、审批提交中不报错。  

---

## 六、计划书写入

- **路径**：`D:\projects-test\cgc-pms\docs\superpowers\plans`
- **文件名**：`2026-06-24-subtask-submeasure-explicit-association-plan.md`
