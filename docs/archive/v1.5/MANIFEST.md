# v1.5 开发版本封存清单

- 封存日期：2026-07-31
- 来源 PR：[#379](https://github.com/kismet84/cgc-pms/pull/379)
- PR Head：`e471923f86a3a93d22e2c6e7413044a729c98937`
- `master` 合并提交：`5c7af1578cec2f8665b4682e2fb5957e75a4e8b1`
- 后续开发分支：`codex/v1.6-start`
- 发布边界：未创建 Tag/GitHub Release，未发布生产

| 原路径 | 归档路径 | 处理方式 | 文件数 |
| --- | --- | --- | ---: |
| `docs/plans/` | `docs/archive/v1.5/plans/` | Git 跟踪移动 | 54 |
| `docs/quality/` | `docs/archive/v1.5/quality/` | Git 跟踪移动 | 349 |
| `docs/iterations/` | `docs/archive/v1.5/iterations/` | Git 跟踪移动 | 10 |
| `docs/superpowers/` | `docs/archive/v1.5/process/` | Git 跟踪移动 | 4 |
| `docs/backlog/` | `docs/archive/v1.5/backlog-snapshot/` | Git 跟踪快照复制并修复相对链接 | 10 |
| `docs/training/` | `docs/archive/v1.5/training/` | Git 跟踪移动 | 19 |
| `docs/ui-v2/` 的 V1.5 基线与冻结矩阵 | `docs/archive/v1.5/ui-v2/` | Git 跟踪移动 | 3 |
| `docs/product-intelligence/` 的 V1.5 分析、研究与决策 | `docs/archive/v1.5/product-intelligence/` | Git 跟踪移动 | 3 |
| `docs/database/database-structure-audit.md` | `docs/archive/v1.5/database/` | Git 跟踪移动 | 1 |
| `docs/database/database-remediation-deployment-runbook.md`、`flyway-v215-manifest.csv` | `docs/archive/v1.5/database/` | Git 跟踪移动 | 2 |
| `docs/database/generated/` 的 v1.5 结构快照 | `docs/archive/v1.5/database/generated/` | Git 跟踪快照复制 | 2 |
| `docs/runbook/` | `docs/archive/v1.5/runbook/` | Git 跟踪移动 | 2 |
| `docs/manuals/production-manager-capability-boundary.md` | `docs/archive/v1.5/manuals/` | Git 跟踪移动 | 1 |
| `docs/V1.5-开发版本说明-2026-07-31.md` | `docs/archive/v1.5/` | Git 跟踪移动 | 1 |
| `docs/未来开发计划.md` | `docs/archive/v1.5/` | Git 跟踪移动 | 1 |
| `docs/历史开发记录.md` | `docs/archive/v1.5/` | Git 跟踪移动 | 1 |

## 当前目录重建

- `docs/plans/`、`docs/quality/`、`docs/iterations/`、`docs/training/` 已重建为 v1.6 当前入口。
- `docs/backlog/` 保留现行状态源；历史全文以本归档快照为准。
- `docs/standards/`、`docs/business/`、`docs/database/`、`docs/manuals/`、`docs/training/`、`docs/prompt/`、`docs/product-intelligence/` 保持现行；数据库生成文档已按当前本地开发库重建。
- `docs/archive/v1.0/` 及其私有禁止区未读取、未修改。

## 验收边界

- v1.5 状态：`V1.5_DEVELOPMENT_CLOSED / SAME_HEAD_CI_PASSED / LOCAL_RC_ACCEPTED`。
- 生产状态：`BLOCKED`；三项 `RELEASE_GATE` 保持。
- 正式入口切换、Legacy 退役、Tag、GitHub Release、生产操作均未执行。
