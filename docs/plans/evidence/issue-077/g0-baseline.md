# ISSUE-077-001 G0 基线

> 锁定时间：2026-08-06
> 结论：`G0_PASSED / LOCAL_ONLY`

- 实施基线：`master@535526eb1021db522fa18e57f07999cfbbeab757`；`origin/master` 同 SHA。
- 实施分支：`codex/mainline-77-pwa-reliability`。
- 实施前工作区：仅未跟踪第77条计划书，归属本任务；无其他用户改动。
- 工作树：主工作树加两个 detached 只读/交付工作树；本分支未被其他工作树占用。
- Codemap：已从当前 HEAD 重建三件套，并补齐通知、现场日报、质量安全入口、影响边和测试；`field-delivery` 节点含 3 个入口、5 个测试、4 条影响边、2 条业务流。
- 数据结构：MySQL/H2 当前迁移最新均为 `V285`；下一迁移保留 `V286`，不修改历史迁移。
- 实际本地运行：backend `8080`、frontend `5173`、MySQL `3307`、Redis `6379`、MinIO `9000/9001`、ClamAV `3310` 均为现有 dev 容器；health=`UP`，dev-login 返回 `302 /dashboard`。
- 实际数据源：`cgc_pms_demo_ui_20260728`，Flyway=`285`；1 个租户、2 个项目、12 个用户、3 份日报、1 个质量问题/整改/检查样本。
- 浏览器基线：仓库 Playwright 使用 Chromium；G4 另以应用内浏览器验证 390×844、768×1024、1440×900。
- 运行刷新：`python scripts/rebuild.py`，稳定等待 25 秒；仅 G4 执行，不用旧容器或单元测试代替运行态证据。
- 验证命令：后端 Maven 定向测试后全量 `verify`；前端实际命令为 `type-check`、`lint:check`、`test:unit`、`build`、相关 Playwright/`check:pre-push`。

基线异常记录：首次直接执行 Compose 配置未带 `deploy/.env`，报 `MINIO_ACCESS_KEY is required`，分类 `tool_invocation`；不属于业务失败。实际容器与数据源通过 Docker、HTTP 和数据库回读独立确认。
