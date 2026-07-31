# 全量审计：仓库清单

## 基线

- 审计时间：2026-07-21（Asia/Shanghai）
- 分支：`master`
- HEAD：`e38737a0d04d6f38a96317b9056dbed40262bc73`
- 初始工作区：仅 `?? docs/prompt/123.md`，判定为用户文件，本次未修改。
- 审计模式：只读优先；唯一写入范围为 `docs/quality/full-audit/`。

## 规模

| 范围 | 文件数/事实 |
| --- | ---: |
| 后端生产 Java | 739 |
| 后端测试 Java | 254 |
| MySQL 活跃迁移 | 4（B215、V216—V218） |
| Legacy 前端 `src` | 429 |
| Legacy E2E | 61 |
| Clean-room V2 `src` | 55 |
| Clean-room V2 tests | 19 |
| GitHub Actions workflow | 2 |
| Deploy 文件 | 13 |
| 业务标准 | 16 |

代码图谱索引包含 38,387 节点、122,493 边、773 条路由；语言主量为 Java 993、SQL 445、TypeScript 310、Vue 153。图谱仅用于导航，正式结论均回到当前源码、测试、GitHub 与运行态核验。

## 主要入口

- 后端：Spring Boot 3 / Java 21，`backend/`。
- Legacy：Vue 3 / TypeScript / Vite，`frontend-admin/`。
- Clean-room V2：`frontend-admin-v2/`，共享契约位于 `packages/frontend-contracts/`。
- 数据库：MySQL 8 生产方言、H2 测试方言，Flyway B215 + V216—V218；历史脚本保留在 legacy 目录。
- 部署：Docker Compose、Nginx、Actuator、Prometheus、ClamAV、MinIO。

## 排除边界

按项目规则排除 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`、`archive/v1.0/private/`。一次初始目录清单误枚举到 `backend/src/main/resources/db/migration-h2/.omc` 路径名，但未读取内容；后续检索全部显式排除，详见命令日志。
