# 文档中心

本目录是 cgc-pms v1.6 当前知识入口。历史测试数量、覆盖率和上线结论均不得替代当前验证。

当前状态：V1.5 开发版本已封存，V1.6 开发已启动；第58条主线已将新版切为仓库和本地正式前端，Legacy 源码已归档。当前环境与授权边界只读取 [AGENTS.md](../AGENTS.md)，本索引不复制。V1.5 权威结论见[开发版本归档](archive/v1.5/README.md)和[第57条主线正式收口验收报告](archive/v1.5/quality/第57条主线-CGC-PMS-V1.5开发版本正式收口验收报告-2026-07-29.md)。

最新主线：第100条“MySQL TLS 信任链与依赖安全整改”采用 MySQL 8.4.12 / Connector/J 26.7.0，实现提交 `c2b81a21` 已推送，同 SHA CI、精确镜像安全扫描及加强版隔离恢复通过；G4 测试桶创建受执行策略阻塞，真实浏览器与 G5 尚未完成、未合并。唯一载体 `ISSUE-100-001` 保持开放；旧组合浏览器记录不替代新组合验收。状态、计划与验收分别见[当前 Backlog](backlog/current-focus.md)、[第100条计划](plans/第100条主线-MySQL TLS信任链与依赖安全整改任务计划书.md)和[质量报告](quality/2026-08-30-issue-100-MySQL-TLS信任链与依赖安全整改.md)。

## 权威层级与文档类型

| 层级 | 唯一入口 | 边界 |
| --- | --- | --- |
| 根硬门禁 | [AGENTS.md](../AGENTS.md) | 自动加载；授权、安全、工作区、验证、Git 与收口 |
| 任务执行路由 | [Codex 任务执行路由索引](standards/codex-task-execution-policy.md) | 只定位专项 Skill、配置和 Schema，不复制正文 |
| 技术与运行标准 | [现行技术文档与规范](#现行技术文档与规范) | 稳定工程、架构、测试、部署和安全规则 |
| 业务标准 | [业务闭环标准](#业务闭环标准) | 稳定业务口径、事实、状态机、职责与验收不变量 |
| 手册与模板 | `manuals/`、`prompt/`、`training/` | 解释和任务模板；不得建立第二权威 |
| 状态与证据 | `backlog/`、`plans/`、`quality/`、`iterations/` | 当前任务状态、计划和验收证据；不得反向改写稳定标准 |
| 历史 | `archive/`、`docs/archive/` | 只供回溯，不作为当前执行入口 |

同一主题出现冲突时，先按上述层级确定权威；同层冲突则停止依赖冲突内容执行写操作，修正唯一权威后再继续。

## 现行技术文档与规范

`状态` 只描述文档生命周期，不代表功能、CI、发布或环境已经通过。

| 编号 | 类型 | 状态 | 文档 |
| --- | --- | --- | --- |
| 00 | 设计标准 | Active | [唯一设计系统标准与门禁](standards/00-UI-Design-Baselines-and-Code-Specifications.md) |
| 01 | 操作指南 | Active | [快速开始](standards/01-快速开始.md) |
| 02 | 架构基线 | Active | [系统架构](standards/02-系统架构.md) |
| 03 | 模块目录 | Active | [业务模块说明](standards/03-业务模块说明.md) |
| 04 | 开发规范 | Active | [后端开发规范](standards/04-后端开发规范.md) |
| 05 | 开发规范 | Active | [前端开发规范](standards/05-前端开发规范.md) |
| 06 | 契约规范 | Active | [API 契约规范](standards/06-API契约规范.md) |
| 07 | 数据规范 | Active | [数据库设计与迁移规范](standards/07-数据库与迁移规范.md) |
| 08 | 业务技术规范 | Active | [权限与审批流程](standards/08-权限与审批流程.md) |
| 09 | 验收规范 | Active | [测试规范](standards/09-测试规范.md) |
| 10 | 运行手册 | Active | [部署运维手册](standards/10-部署运维手册.md) |
| 11 | 安全规范 | Active | [安全规范](standards/11-安全规范.md) |
| 12 | 已退役 | Retired | 原子智能体实施计划已删除；保留编号，不重排后续文档 |
| 13 | 数据展示规范 | Active | [驾驶舱摘要字段规范](standards/13-驾驶舱摘要字段生成与展示规范.md) |
| 14 | 治理规范 | Active | [AutoPilot 任务评分与自动改进回顾规范](standards/14-AutoPilot任务评分与自动改进回顾规范.md) |
| 15 | 业务技术规范 | Active | [业务编号生成规范](standards/15-业务编号生成规范.md) |
| 16 | 本地治理规范 | Active | [本地路径与产物规范](standards/16-本地路径与产物规范.md) |
| — | 路由索引 | Active | [Codex 任务执行路由索引](standards/codex-task-execution-policy.md) |

## 业务闭环标准

以下文件的 `Active` 只表示业务契约现行。业务口径、事实定义、公式、状态机、职责分离和验收不变量属于规范正文；日期、分支、提交、CI run、测试数量、实施完成度及生产/目标环境结论仅为历史实施记录，不是当前状态源或执行阻塞项。当前状态必须由当前代码、Backlog、计划、质量报告和本轮验证共同证明。

| 领域 | 状态 | 业务标准或契约 |
| --- | --- | --- |
| 单据生成 | Active | [业务单据生成 Provider 与渲染契约](business/document-generation-provider-contract.md) |
| 财务核算 | Active | [财务核算与月结闭环业务标准](business/financial-accounting-month-end-closed-loop.md) |
| 预警通知 | Active | [项目预警与通知闭环业务标准](business/project-alert-notification-closed-loop.md) |
| 资金预测 | Active | [项目资金计划与现金预测闭环业务标准](business/project-cash-plan-forecast-closed-loop.md) |
| 项目收尾 | Active | [项目竣工与收尾闭环业务标准](business/project-completion-closeout-closed-loop.md) |
| 技术管理 | Active | [图纸、RFI 与技术方案闭环业务标准](business/project-drawing-rfi-technical-scheme-closed-loop.md) |
| 产值结算 | Active | [产值计量与业主结算闭环业务标准](business/project-output-measurement-owner-settlement-closed-loop.md) |
| 资金支出 | Active | [项目资金支出闭环业务标准](business/project-payment-closed-loop.md) |
| 采购库存 | Active | [采购—验收—库存—领料—成本闭环业务标准](business/project-procurement-receipt-inventory-requisition-cost-closed-loop.md) |
| 质量安全 | Active | [质量安全整改闭环业务标准](business/project-quality-safety-rectification-closed-loop.md) |
| 收入回款 | Active | [项目收入与回款闭环业务标准](business/project-revenue-collection-closed-loop.md) |
| 施工履约 | Active | [项目计划与施工履约闭环业务标准](business/project-schedule-construction-performance-closed-loop.md) |
| 分包履约 | Active | [分包履约与分包结算付款闭环业务标准](business/project-subcontract-performance-settlement-payment-closed-loop.md) |
| 供应商履约 | Active | [供应商招采与履约评价闭环业务标准](business/project-supplier-sourcing-performance-closed-loop.md) |
| 成本利润 | Active | [目标成本与动态利润闭环业务标准](business/project-target-cost-dynamic-profit-closed-loop.md) |
| 变更索赔 | Active | [变更、签证与索赔闭环业务标准](business/project-variation-claim-closed-loop.md) |

## 可执行专项规则与操作入口

- [CI 失败分类与门禁排障](../.agents/skills/cgc-pms-ci-gate-triage/SKILL.md)
- [主线、计划与收口](../.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md)
- [本地运行态刷新与验真](../.agents/skills/cgc-pms-runtime-refresh/SKILL.md)
- [Codex 长任务完成门禁手册](manuals/codex-long-task-gate.md)及其 [Skill](../.agents/skills/long-task-gate/SKILL.md)
- [版本发布 Skill](../.agents/skills/release-skills/SKILL.md)
- [AutoPilot Owner](../plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md)
- [Prompt 索引](prompt/README.md)
- [用户手册](manuals/README.md)
- [培训材料](training/README.md)

Skill、插件 references、配置和 Schema 必须保留在所属包内；文档中心只提供入口，不复制执行正文。

## 当前状态与证据入口

- [当前 Backlog](backlog/current-focus.md)
- [灵感与想法暂存](ideas.md)
- [v1.6 计划书](plans/README.md)
- [v1.6 质量报告](quality/README.md)
- [产品情报、项目地图与迭代决策](product-intelligence/README.md)
- [v1.6 迭代记录](iterations/README.md)
- [数据库最终基线 B215 与平台初始化](database/database-baseline-v215.md)
- [项目知识图谱工具](../tools/knowledge-graph/README.md)

## 历史边界

v1.5 的计划、质量报告、迭代记录、培训确认、专题研究、UI 基线、审计报告、过程文件和 Backlog 快照从 [v1.5 文档归档](archive/v1.5/README.md) 查阅；v1.0 及更早记录从 [v1.0 文档归档](archive/v1.0/README.md) 查阅。Git 忽略的本地私有封存不属于项目运行依赖，也不得作为当前规范引用。
