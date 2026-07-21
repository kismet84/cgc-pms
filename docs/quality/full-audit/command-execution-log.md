# 全量审计命令执行日志

## 原则

- 所有检查均为只读；数据库仅执行元数据、Flyway 历史与行数查询。
- 未执行写业务、迁移、清库、提交、推送或生产操作。
- 原始长日志不入库，仅保留能支撑裁决的摘要。

## 命令与结果摘要

| 类别 | 命令/动作 | 结果 |
| --- | --- | --- |
| Git 基线 | `git branch --show-current`、`git rev-parse HEAD`、`git status --short` | master / `e38737a0...` / 仅用户文件 `docs/prompt/123.md` |
| 架构 | codebase-memory 架构图、CodeGraph 符号/调用链、`rg` 源码核验 | 38,387 nodes；122,493 edges；773 routes |
| 后端 | `backend\mvnw.cmd -C verify` | 2049 tests；0 failure；0 error；3 skipped；耗时约 776 秒 |
| 覆盖率 | 汇总 `backend/target/site/jacoco/jacoco.csv` | instruction 80.13%；branch 58.89% |
| Legacy | `pnpm lint:check`、`test:unit`、`type-check`、`build`、bundle gate | 全通过；732 tests；25 warnings |
| V2 | boundary、route ledger、lint、unit、contract type、type、build、bundle | 全通过；83 tests；87 routes |
| 运行态 | `docker ps`、后端 health、5173、5174 | 依赖健康；HTTP 200/UP |
| 数据库 | MySQL 容器内只读查询 schema/Flyway | active demo_v2 197 表；B215/V216/V217/V218 success |
| 迁移 | 列出 MySQL/H2 B215、V216—V218 并计算 SHA/行数 | 双方言文件齐全；由 smoke test 验证语义 |
| GitHub | `gh pr view 358`、`gh run view`、Environment/保护 API | PR 合并且 13 项全绿；post-merge success；Environment=0 |
| 部署 | `docker compose --env-file deploy/.env.example -f deploy/docker-compose.prod.yml config --quiet` | 因缺 `FRONTEND_TAG` 失败关闭，未生成漂移配置 |
| 静态安全 | Controller/`@PreAuthorize`、JWT、CSRF、CORS、Swagger、secret 模式检索 | 未确认可利用 P0/P1 源码漏洞 |
| 并发 | `@Version`、`FOR UPDATE`、幂等、条件更新及测试检索 | 关键资金/审批/分摊链有控制和测试 |
| 可观测 | Logback、Actuator、Prometheus 配置/认证链核验 | 后端基础存在；Prometheus 抓取配置缺认证；前端中央监控 TODO |

## 过程偏差

首次仓库清单命令曾枚举到禁止目录 `backend/src/main/resources/db/migration-h2/.omc` 的路径名。未打开、读取、审计或总结其中内容。发现后所有递归检索显式加入禁止目录排除参数；该路径未作为任何结论证据。

## 未执行项

- 生产环境连接、凭据轮换、文件复扫、Flyway、备份恢复、真实角色验收。
- 本地重复执行完整浏览器 E2E；采用 PR #358 同 SHA 远端成功证据。
- 任何 Git commit/push/PR 操作。
