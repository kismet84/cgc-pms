# v1.0 封存清单

- 基线标签：`v1.0.0`
- 实施分支：`develop/1.5`
- 私有归档：`archive/v1.0/private/`（本地、Git 忽略）

| 原路径 | 归档路径 | 处理方式 |
| --- | --- | --- |
| `docs/00-*.md`～`docs/13-*.md` | `docs/standards/` | 移动为 v1.5 当前规范 |
| `docs/plans/*` | `docs/archive/v1.0/plans/` | Git 跟踪封存 |
| `docs/quality/*` | `docs/archive/v1.0/quality/` | Git 跟踪封存 |
| `docs/iterations/iteration-2026-*.md` | `docs/archive/v1.0/iterations/` | Git 跟踪封存 |
| `docs/issue/*` | `docs/archive/v1.0/issues/` | Git 跟踪封存 |
| `docs/backlog/*.md` | `docs/archive/v1.0/backlog-snapshot/` | 快照复制 |
| v1.0 Superpowers 计划与证据 | 对应 `plans/superpowers/`、`quality/superpowers-evidence/` | Git 跟踪封存 |
| 私有目录与历史运行态 | `archive/v1.0/private/` | 本地封存、哈希校验 |

文件数量、私有字节数和哈希在搬移完成后补充到本清单及私有 CSV 清单。
