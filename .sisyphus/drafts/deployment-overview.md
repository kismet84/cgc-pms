# Draft: CGC-PMS 部署方案概览

## 用户需求
- 快速了解现有部署方案的全貌

## 现有资产
- deploy/docker-compose.prod.yml：5 服务生产编排
- backend/Dockerfile：多阶段 Java 21 构建
- frontend-admin/Dockerfile：多阶段 Vue 3 + Nginx 构建
- .github/workflows/ci.yml：CI 流水线
- doc/部署与回滚手册_2026-06-13.md：完整部署文档
- doc/备份恢复方案_2026-06-13.md：备份方案
- doc/监控告警清单_2026-06-13.md：监控方案
- doc/上线就绪检查清单_2026-06-13.md：20 条门禁

## 当前状态
- 🟡 有条件上线
- 3 个 P0 阻断项待修复（预计 1-2 天）
- 16/20 门禁直接通过

## 调研发现补充（explore agent）
- deploy/.env 含明文密码且被 git 跟踪 → 安全风险，上线前必须处理
- CI 流水线无自动部署 job → 需手动执行 docker compose build + up
- 后端 application-prod.yml 全量环境变量化 → 生产配置契约清晰
- 项目为单体架构（非微服务），适合 Docker Compose 单机部署
- 无 K8s/Terraform/Serverless 配置 → 如需云原生需从零构建
