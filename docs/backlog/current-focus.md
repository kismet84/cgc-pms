# Current Focus

## 当前阶段

由项目总负责人按长期计划、当前项目状态和最新约束动态选择下一批 Ready Issue。

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 当前执行队列：`docs/backlog/ready-issues.md`
- 当前执行队列没有合格 Ready Issue 时，只允许由项目总负责人结合长期总任务池、`done/ready/blocked` 现状、当前项目状态、用户最新约束、风险和依赖关系，拆出最多 5 个一轮可执行 Ready Issue；该轮只更新 backlog，不直接修改业务代码；后续执行允许最多 3 个完全无关联、无任何代码关联的 Ready Issue 并行，不能证明完全无关联时按串行处理。

## 动态候选范围与选择原则

- 候选任务默认来自长期总任务池，不再固定绑定 `EPIC-004~007` 样例范围。
- 由项目总负责人根据当前项目状态动态判断优先级，优先选择依赖清晰、风险可控、可在本地测试环境收口的任务。
- 选题依据至少包括：长期总任务池、`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/backlog/blocked-issues.md`、当前项目状态、用户最新约束、任务风险与前置依赖。
- 默认跳过：已 Done 的任务、Blocked 且未解除阻塞的任务、超出当前禁止范围的任务、需要生产环境/生产数据库连接的任务、未满足下方“阶段切换条件”的数据库 migration 任务。

## 阶段切换条件

当当前 Ready 队列中生产准入类回归任务已清空，且 `blocked-issues.md` 没有未解除阻塞项时，AutoPilot 可以从长期计划的下一阶段拆分生产增强类 Ready Issue。

允许进入下一阶段的候选范围：

- P2 报表中心、规则治理中心、通知平台、WBS / 进度计划 / 甘特图、供应商评分 / 采购增强。
- 任务必须能在本地 dev/test 环境闭环，不依赖生产环境、生产数据库或外部生产平台。
- 第一轮只拆出最多 5 个 Ready Issue；每个 Ready Issue 必须写清允许修改范围、禁止事项、验证命令、归档报告和是否需要新增 migration。
- 如果涉及数据库结构，只允许新增版本化 Flyway migration，不得修改已应用 migration；Ready Issue 必须包含表/字段最小范围、回滚口径和本地验证命令。
- 若新增 migration 同时触达权限、安全、租户隔离、金额口径、审批状态机或生产集成，默认不自动执行，先进入人工裁决。

## 当前禁止执行

- 总工程师相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- BIM 相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- AI 相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- 财务生产集成：默认不选，除非用户明确解除
- 生产发布：默认不选，除非用户明确解除
- 生产数据库连接：默认不选，除非用户明确解除
- 数据库 migration：默认不选；满足“阶段切换条件”且仅新增版本化 Flyway migration 时，可作为生产增强类 Ready Issue 执行

## 每轮上限

- 每轮最多处理 3 个 Ready Issue；仅限完全无关联、无任何代码关联的任务并行，不能证明完全无关联时按串行处理
- 每轮最多修改 20 个文件
- 无 Ready Issue 不写代码

## 自动合并边界

允许：

- backend/**
- frontend-admin/**
- deploy/**
- docs/backlog/**
- docs/iterations/**
- docs/quality/**

禁止：

- 仓库外文件删除
- .git 删除
- 用户目录删除
- 生产环境变更
