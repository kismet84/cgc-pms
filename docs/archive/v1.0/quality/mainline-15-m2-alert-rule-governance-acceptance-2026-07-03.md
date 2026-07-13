# 第 15 条主线 M2：预警中心规则治理增强验收报告（2026-07-03）

## 1. 结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 当前判断：第 15 条主线 `M2` 规则治理增强已完成收口

本结论基于 2026-07-03 当前工作区代码、当前 MySQL 运行态、当前前后端验证结果和当前浏览器实证，不沿用旧会话口径。

## 2. 依据

### 2.1 规则分类治理与标签补齐

实际落地文件：

- `backend/src/main/java/com/cgcpms/alert/entity/AlertLog.java`
- `backend/src/main/java/com/cgcpms/alert/entity/AlertRuleConfig.java`
- `backend/src/main/java/com/cgcpms/alert/mapper/AlertRuleConfigMapper.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `frontend-admin/src/types/alert.ts`
- `frontend-admin/src/pages/alert/index.vue`

确认结果：

1. 预警记录已具备：
   - `alertDomain`
   - `alertCategory`
   - `dedupKey`
   - `processStatus`
   - `processedAt`
   - `archivedAt`
   - `statusRemark`
2. 当前规则已形成稳定分类矩阵：
   - `CONTRACT_EXPIRING -> CONTRACT / CONTRACT_TERM`
   - `PURCHASE_DELIVERY_OVERDUE -> PURCHASE / PURCHASE_DELIVERY`
   - `DYNAMIC_COST_EXCEEDS_TARGET -> COST / COST_DYNAMIC`
   - `MATERIAL_EXCEEDS_BUDGET -> COST / COST_MATERIAL`
   - `SUBCONTRACT_EXCEEDS_CONTRACT -> COST / COST_SUBCONTRACT`
   - `VARIATION_UNCONFIRMED -> VARIATION / VARIATION_CONFIRM`
   - `WARRANTY_EARLY_RELEASE -> CONTRACT / CONTRACT_WARRANTY`
3. 前端已补齐 `标签` 与 `处理口径` 展示。
4. 前端规则中文映射已补齐 `PURCHASE_DELIVERY_OVERDUE -> 采购交期逾期`，并修正了错误分类回退映射。

### 2.2 抑制策略与去重策略

实际落地文件：

- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`

确认结果：

1. 当前规则去重已改为基于 `dedupKey + processStatus(OPEN/PROCESSED) + 时间窗`。
2. 已定义三类最小去重键：
   - 项目维度：`P:{projectId}:R:{ruleType}`
   - 合同维度：`C:{contractId}:R:{ruleType}`
   - 来源对象维度：`S:{sourceType}:{sourceId}:R:{ruleType}`
3. 已覆盖采购交期逾期及非采购类规则。
4. 去重口径不再依赖“未读”状态。

定向测试依据：

1. `TA14: 告警去重 — 24小时内同 dedupKey 的活跃告警不重复生成`
2. 本轮后端定向测试通过。

### 2.3 规则配置化最小实现

实际落地文件：

- `backend/src/main/resources/db/migration/V121__alert_rule_governance.sql`
- `backend/src/main/resources/db/migration-h2/V121__alert_rule_governance.sql`
- `backend/src/main/java/com/cgcpms/alert/entity/AlertRuleConfig.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`

确认结果：

1. 已新增 `alert_rule_config` 表。
2. 当前真实 MySQL 已有 `9` 条规则配置。
3. 当前已配置最小字段集：
   - `enabled`
   - `dedup_hours`
   - `window_days`
   - `threshold_ratio`
   - `severity_override`
4. `AlertEvaluationService` 已按配置优先、默认值回退的方式读取。

当前 MySQL 运行态实证：

1. `flyway_schema_history` 已成功执行：
   - `118`
   - `119`
   - `120`
   - `121`
2. `alert_rule_config` 当前为 `9` 条
3. `alert_log` 新增字段当前全部存在
4. 当前 `alert_log` 中：
   - `process_status IS NULL = 0`
   - `alert_category IS NULL = 0`

### 2.4 归档口径与页面联动

实际落地文件：

- `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `frontend-admin/src/api/modules/alert.ts`
- `frontend-admin/src/stores/alert.ts`
- `frontend-admin/src/pages/alert/index.vue`

确认结果：

1. 后端已提供：
   - `GET /alerts` 支持 `processStatus`
   - `PUT /alerts/{id}/status`
2. 当前状态口径已明确区分：
   - `OPEN`
   - `PROCESSED`
   - `ARCHIVED`
   - `INVALID`
3. 前端页面已支持：
   - `标签` 列
   - `处理口径` 列
   - `标为已处理`
   - `归档`
   - `标为失效`
   - `处理口径` 筛选

### 2.5 后端测试、前端静态验证与页面测试

本轮重新验证结果：

1. 前端 `pnpm type-check`：通过
2. 前端 `pnpm vitest run src/pages/alert/__tests__/index.test.ts`：通过
3. 前端 `pnpm build`：通过
4. 后端 `.\mvnw.cmd -q "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`：通过
5. `git diff --check`：无格式阻塞项，仅存在 CRLF/LF warning，不构成验收阻塞

## 3. 当前运行态与浏览器验收

### 3.1 运行态

当前容器状态：

1. `cgc-pms-backend-dev`：`Up ... (healthy)`
2. `cgc-pms-frontend-dev`：`Up ...`
3. `cgc-pms-mysql-dev`：`Up ... (healthy)`

当前访问状态：

1. `http://localhost:8080/api/actuator/health`：可达
2. `http://localhost:5173`：可达

### 3.2 浏览器证据

关键证据目录：

- `output/playwright/mainline15-m2-governance-2026-07-03T10-08-45`
- `output/playwright/mainline15-m2-process-filter-2026-07-03T10-09-54`

关键通过项：

1. 页面真实显示：
   - `标签`
   - `处理口径`
2. 页面真实显示细标签与中文规则名：
   - `合同期限`
   - `采购交付`
   - `采购交期逾期`
3. 真实页面已执行状态动作：
   - 第一条预警 `标为已处理`
   - 第二条预警 `归档`
4. 真实 API 成功回包：
   - `PUT /api/alerts/{id}/status -> PROCESSED`
   - `PUT /api/alerts/{id}/status -> ARCHIVED`
5. 真实页面筛选已生效：
   - `processStatus=PROCESSED -> total=1`
   - `processStatus=ARCHIVED -> total=1`
6. 真实 API 证据显示当前数据已返回：
   - `alertDomain`
   - `alertCategory`
   - `processStatus`
   - `processedAt`
   - `archivedAt`

## 4. 与计划书逐项对照

对照 [第15条主线-M2-预警中心规则治理增强任务计划书-2026-07-03.md](/../plans/第15条主线-M2-预警中心规则治理增强任务计划书-2026-07-03.md)：

1. 当前已落地规则具备稳定分类口径：通过
2. 当前核心规则形成正式可解释的去重 / 抑制策略：通过
3. 当前核心规则关键参数具备最小配置化能力：通过
4. 已读、已处理、已归档、失效口径已明确区分：通过
5. 前端页面对分类、状态、归档口径展示与后端一致：通过
6. 后端测试、前端静态验证、运行态复验通过：通过
7. 质量归档报告完成，并明确 `M2` 与 `M3` 边界：通过

## 5. 通过 / 不通过 / 阻塞 / 非阻塞判断

| 项目 | 结论 | 依据 |
| --- | --- | --- |
| 是否通过 | 通过 | 分类、去重、配置化、归档状态、页面联动、测试与浏览器证据已闭环 |
| 是否阻塞 | 非阻塞 | 当前剩余项属于 `M3` 或后续专项，不阻塞 `M2` 收口 |
| 是否可关闭第 15 条主线 `M2` | 可关闭 | 当前证据已覆盖 `M2` 验收标准 |

## 6. 不纳入本次收口

以下事项不属于 `M2` 当前交付项，但明确保留为后续阶段任务：

1. 通知平台
2. 多渠道触达
3. 强权限隔离
4. 通用规则引擎设计器
5. 更大范围的规则扩包与订阅机制

说明：

1. 上述内容不是 `M2` 缺陷。
2. 上述内容不是永久排除项。
3. 上述内容应转入 `M3` 或后续专项继续推进。

## 7. 剩余风险

本轮验收后，无新增阻塞性剩余风险。

保留观察项：

1. 当前仍存在较早历史告警样本，需要后续按业务节奏逐步归档，不影响 `M2` 当前通过。
2. 若后续扩入更多规则类型，需继续按 `M2` 形成的配置化与分类口径接入，不得回退为硬编码散落。

## 8. 主负责人建议

1. 按“通过 / 非阻塞”关闭第 15 条主线 `M2`。
2. `M3` 应聚焦：
   - 通知平台
   - 多渠道触达
   - 强权限隔离
   - 订阅策略
3. 后续新增规则时，必须继续沿用当前 `M2` 的：
   - 分类矩阵
   - `dedupKey`
   - `processStatus`
   - `alert_rule_config`
