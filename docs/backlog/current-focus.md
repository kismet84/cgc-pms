# Current Focus

## 当前阶段

P1：前端生产化尾项与运维可观测性补齐

## 任务来源

- 长期总任务池：`docs/backlog/cgc-pms-production-enhancement-plan.md`
- 当前执行队列：`docs/backlog/ready-issues.md`
- 当前执行队列没有合格 Ready Issue 时，只允许按本文件的当前阶段和禁止范围，从长期总任务池拆出最多 5 个一轮可执行 Ready Issue；该轮只更新 backlog，不直接修改业务代码；后续执行仍按每轮最多处理 1 个 Ready Issue。

## 当前允许执行的 Epic

- EPIC-004：主链路回归
- EPIC-005：前端列表页生产化
- EPIC-006：文件上传与发票识别安全
- EPIC-007：生产监控、日志、备份恢复

## 当前禁止执行

- 总工程师相关任务
- BIM 相关任务
- AI 相关任务
- 财务生产集成
- 生产发布
- 生产数据库连接
- 数据库 migration

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
