# Current Focus

## 2026-07-31 第58条主线：新版前端正式切换与 Legacy 归档完成

- v1.5 已完成开发版本收口；PR #379 合并提交为 `5c7af1578cec2f8665b4682e2fb5957e75a4e8b1`。
- 当前开发分支：`codex/v1.6-start`。
- 当前版本：后端 `1.6.0-SNAPSHOT`；正式前端 `frontend-admin-v2` 为 `1.6.0-dev.0`。
- 正式前端已接管根路径、`5173`、Compose `frontend`、CI 制品与 E2E；Legacy 应用源码归档到 `archive/v1.6/frontend-admin-legacy/`，不再参与正式运行、构建或发布链。
- 本地运行、真实浏览器、91 项迁移门 E2E 与 `新版→归档旧版→新版恢复` 演练通过。
- 当前 Ready：0。历史事项不自动恢复为 v1.6 Ready。
- 当前现行问题以 [`current-issues.json`](current-issues.json) 为唯一事实源。
- 生产仍受 `REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION` 三项 `RELEASE_GATE` 阻塞。
- Tag、GitHub Release、Git 交付和生产发布未执行。
- AutoPilot 当前控制面指纹为 `51cff640086c50dd13cab5f0f9369c1ad51acb41cfb7cb7ced4141d17dfeac29`；进入 N>1 或无界执行前必须由用户明确发送 `启动迭代-1` 完成金丝雀。
- v1.5 完整执行记录、计划、报告和台账见 [`docs/archive/v1.5/`](../archive/v1.5/)。

新增后续项 0、关闭后续项 0、后续项净变化 0；无无载体遗留项。
