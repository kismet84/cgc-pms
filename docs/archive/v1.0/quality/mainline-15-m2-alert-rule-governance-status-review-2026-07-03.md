# 第 15 条主线 M2：预警中心规则治理增强状态审查（2026-07-03）

## 1. 结论

- 结论：不通过
- 阻塞 / 非阻塞：阻塞
- 当前判断：`M2` 计划书已完成，代码候选已存在，但截至 2026-07-03 当前证据，`M2` 不能宣布收口

阻塞原因不是“没有开始做”，而是**缺少足以证明 `M2` 通过的当前态运行证据与正式验收归档**。

## 2. 当前已确认完成项

### 2.1 计划书已正式落地

已存在正式计划书：

- [第15条主线-M2-预警中心规则治理增强任务计划书-2026-07-03.md](/../plans/第15条主线-M2-预警中心规则治理增强任务计划书-2026-07-03.md)

计划书已满足以下要求：

1. 已将 `M2` 定位收敛为规则治理增强。
2. 已将规则引擎、通知平台、多渠道触达、强权限隔离改写为后续阶段任务，而不是永久排除项。
3. 已补齐子智能体 `model + thinking` 逐任务分档规则。
4. 已补齐交付包、验收标准、阻塞项、阶段关闭口径。

### 2.2 当前工作区存在 M2 实现候选

从当前工作区可见，以下候选改动已存在：

1. 后端新增规则配置实体与 mapper：
   - `backend/src/main/java/com/cgcpms/alert/entity/AlertRuleConfig.java`
   - `backend/src/main/java/com/cgcpms/alert/mapper/AlertRuleConfigMapper.java`
2. 后端新增治理 migration：
   - `backend/src/main/resources/db/migration/V121__alert_rule_governance.sql`
   - `backend/src/main/resources/db/migration-h2/V121__alert_rule_governance.sql`
3. `AlertLog`、`AlertEvaluationService`、`AlertController` 已有 `alertCategory / dedupKey / processStatus / processedAt / archivedAt / statusRemark` 等候选实现。
4. 前端 `alert.ts / api / store / page` 已有 `标签`、`处理口径`、`状态变更` 等候选实现。
5. 定向测试文件已出现相应补充：
   - `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
   - `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
   - `frontend-admin/src/pages/alert/__tests__/index.test.ts`

结论：

- `M2` 不是空计划。
- 当前已进入“实现候选存在，但验收证据不够”的阶段。

## 3. 当前阻塞证据

### 3.1 `M2` 正式验收报告缺失

以下目标验收文档当前不存在：

- `docs/quality/mainline-15-m2-alert-rule-governance-acceptance-2026-07-03.md`

这意味着：

1. `M2` 尚未形成正式“通过 / 不通过、阻塞 / 非阻塞”的归档口径。
2. 当前不能按计划书第十二节“阶段关闭口径”关闭 `M2`。

### 3.2 当前浏览器运行证据仍显示旧运行态

已存在的浏览器复验目录：

- `output/playwright/mainline15-alert-recheck-localhost-20260703-164921`

其中 `result.json` 反映的当前态证据表明：

1. 运行时 `/api/alerts` 返回记录仍大量是旧结构：
   - `alertDomain = null`
   - 无 `alertCategory`
   - 无 `processStatus`
2. 以 `alertDomain=CONTRACT` 查询返回 `total=0`，与 `M2` 期望“分类口径稳定”不一致。
3. 页面文本仍主要体现 `M1` 能力，未证明 `M2` 的“标签`/`处理口径`/`归档状态”已在真实运行态生效。

结论：

- 当前浏览器证据不能证明 `M2` 已落到真实运行态。
- 相反，它更像是在证明当前运行态仍停留在 `M1` 或 `M1+局部候选代码未生效`。

### 3.3 最新一次最终复验直接失败

以下复验结果存在致命错误：

- `output/playwright/mainline15-final-recheck-20260703-170020/result.json`

当前错误：

1. `page.goto("http://localhost:5173/alert")` 超时 60000ms

结论：

- 当前没有一份可用的最终浏览器证据能支撑 `M2` 收口。

### 3.4 现有证据仍偏向 M1，而不是 M2

当前已存在的正式验收文档是：

- [mainline-15-alert-center-enhancement-acceptance-2026-07-03.md](/mainline-15-alert-center-enhancement-acceptance-2026-07-03.md)

该文档明确对应的是：

1. 后端分页
2. 基础筛选
3. 采购交期逾期
4. 批量已读
5. 业务单据跳转
6. 角色默认筛选

这属于 `M1` 收口，不是 `M2` 规则治理验收。

## 4. 当前可视为“已部分完成但未闭环”的项

### 4.1 规则分类与标签治理

判断：

- 部分完成

依据：

1. 代码候选里已有 `alertCategory` 和规则分类映射。
2. 但当前运行证据未证明真实 API 与页面已稳定输出这些字段。

### 4.2 抑制策略与去重策略

判断：

- 部分完成

依据：

1. `AlertEvaluationService` 候选代码已引入 `dedupKey` 和 `processStatus in (OPEN, PROCESSED)` 的去重口径。
2. 但缺少当前 MySQL 运行态和真实页面的复验证据。

### 4.3 规则配置化

判断：

- 部分完成

依据：

1. 候选代码已引入 `AlertRuleConfig` 和 `V121` migration。
2. 但没有当前态证据证明：
   - `V121` 已应用到真实 MySQL
   - `alert_rule_config` 已落库
   - 规则配置已在运行态生效

### 4.4 归档口径与页面联动

判断：

- 部分完成

依据：

1. 候选代码与页面已有 `processStatus`、`标为已处理`、`归档`、`标为失效`。
2. 但没有真实浏览器证据证明这些动作在当前运行态可用。

## 5. 主负责人裁决

截至当前证据，`M2` 不能判定为“通过 / 非阻塞”。

当前唯一合理裁决是：

1. `计划书通过`
2. `实现候选存在`
3. `M2 验收不通过`
4. `当前阻塞于运行态证据与正式验收归档缺失`

## 6. 下一步最小派工建议

### 子智能体模型分配表

| 任务/角色 | 任务分类 | model | thinking | reason |
| --- | --- | --- | --- | --- |
| 后端治理实现收尾 | 实现型任务 | `gpt-5.5` | `high` | 涉及 migration、去重口径、状态口径、配置化落库 |
| 前端页面联动收尾 | 实现型任务 | `gpt-5.4` | `medium` | 主要是页面字段、筛选与状态动作联动 |
| 运行态刷新与环境核查 | 运维型任务 | `gpt-5.4` | `low` | 以固定步骤执行、端口和服务状态核对为主 |
| 真实浏览器复验 | 验收型任务 | `gpt-5.5` | `medium` | 需要确认真实页面、真实接口、真实状态动作 |
| 质量归档 | 审计/归档型任务 | `gpt-5.5` | `medium` | 需要形成正式通过/不通过裁决依据 |

### 串行顺序

1. 先完成后端/前端 M2 候选实现收尾。
2. 再刷新后端与前端运行态。
3. 再验证当前 MySQL 是否已有 `V121` 与 `alert_rule_config`。
4. 再做真实浏览器复验：
   - 标签列
   - 处理口径列
   - `PROCESSED / ARCHIVED / INVALID`
   - `processStatus` 筛选
5. 最后补正式验收报告：
   - `docs/quality/mainline-15-m2-alert-rule-governance-acceptance-2026-07-03.md`

## 7. 当前关闭口径

本文件不是 `M2` 验收通过报告。

本文件只用于记录主负责人截至当前态的裁决：

1. `M2` 正式计划书已完成
2. `M2` 候选实现已存在
3. `M2` 当前仍未闭环
4. 后续必须继续推进运行态验证与正式验收归档
