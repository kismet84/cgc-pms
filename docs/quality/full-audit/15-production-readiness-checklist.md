# 生产就绪检查表

| 检查项 | 状态 | 证据/缺口 |
| --- | --- | --- |
| 当前 HEAD 本地后端全量 | 通过 | 2049 tests，0 failure/error |
| Legacy lint/unit/type/build/bundle | 通过 | 732 tests；0 error/25 warning |
| V2 boundary/lint/unit/type/build/bundle | 通过 | 83 tests；0 warning |
| 同 SHA push/PR CI | 通过 | PR #358，13 项全绿 |
| post-merge CI | 通过 | master `e38737a0...` success |
| MySQL 最小权限迁移 | 通过（CI） | baseline + Flyway smoke |
| 本地运行态 | 通过 | backend/5173/5174/依赖健康 |
| 分支保护 | 通过 | strict、11 checks、admin enforcement |
| 目标环境识别 | **失败** | GitHub Environment=0；无 Secret/Variable 载体 |
| 制品标签固定 | **失败** | 生产 Compose 缺必需标签并 fail-close |
| 生产凭据轮换 | **失败** | SEC-001 |
| 生产存量文件复扫 | **失败** | FILE-001 |
| 目标 Flyway/真实角色/租户/金额/文件 | **失败** | REL-001 |
| 备份恢复/回滚演练 | **失败** | REL-001 |
| Prometheus 真实 scrape | **失败** | OBS-002，配置未认证 |
| 前端中央错误告警 | 未完成 | OBS-001 |
| V2 全量业务替代 | 未完成 | BIZ-001；只允许 M2 已迁移范围 |

## 裁决

**禁止上线。** 代码与 CI 通过不能覆盖 3 个 P0 生产门禁。若发布目标仅为本地/演示环境，可继续使用当前运行态；不得称为生产发布。
