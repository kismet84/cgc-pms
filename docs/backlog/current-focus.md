# Current Focus

## 当前阶段

由项目总负责人按长期计划、当前项目状态和最新约束动态选择下一批 Ready Issue。

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 当前执行队列：`docs/backlog/ready-issues.md`
- 当前执行队列没有合格 Ready Issue 时，只允许由项目总负责人结合长期总任务池、`done/ready/blocked` 现状、当前项目状态、用户最新约束、风险和依赖关系，拆出最多 5 个一轮可执行 Ready Issue；该轮只更新 backlog，不直接修改业务代码；后续执行仍按每轮最多处理 1 个 Ready Issue。

## 动态候选范围与选择原则

- 候选任务默认来自长期总任务池，不再固定绑定 `EPIC-004~007` 样例范围。
- 由项目总负责人根据当前项目状态动态判断优先级，优先选择依赖清晰、风险可控、可在本地测试环境收口的任务。
- 选题依据至少包括：长期总任务池、`docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/backlog/blocked-issues.md`、当前项目状态、用户最新约束、任务风险与前置依赖。
- 默认跳过：已 Done 的任务、Blocked 且未解除阻塞的任务、超出当前禁止范围的任务、需要生产环境/生产数据库连接的任务、需要数据库 migration 的任务。

## 当前禁止执行

- 总工程师相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- BIM 相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- AI 相关任务：若长期计划中仍标为冻结或缺少基础域，默认不选
- 财务生产集成：默认不选，除非用户明确解除
- 生产发布：默认不选，除非用户明确解除
- 生产数据库连接：默认不选，除非用户明确解除
- 数据库 migration：默认不选，除非用户明确解除

## 每轮上限

- 每轮最多处理 1 个 Ready Issue
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
