# 完整演示项目 v1

此数据包面向 V215 最终 schema，显式创建一套可追踪的全业务闭环演示项目。默认数据库基线不执行本目录内容，也不复用会随最终 schema 漂移的历史 Flyway fixture。

安全边界：

- 仅允许 `dev`、`test`、`demo`，MySQL Docker 端口必须绑定 `127.0.0.1`。
- 仓库必须存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`；加载器不执行删除、clean、drop 或 reset。
- 目标库必须已有 `sys_bootstrap_state`、`SUPER_ADMIN` 和完成的平台管理员 bootstrap。
- 同一 package 通过 `sys_bootstrap_state` 的四个阶段键续载；已完成阶段跳过。
- 不提供自动清理。需要重建时创建新的本地 demo 空库。

执行：

```powershell
pwsh scripts/demo/complete-project-v1/load.ps1 -Environment demo -Database cgc_pms_demo
pwsh scripts/demo/complete-project-v1/verify.ps1 -Environment demo -Database cgc_pms_demo
```

数据所有权由 `manifest.yml` 中 package、项目 ID/编码和 `M52-` 业务编码前缀限定。四个阶段依次覆盖核心项目、采购库存、商务资金、质量流程和收尾；每阶段单事务提交。`verify.ps1` 校验对象唯一、数量守恒、金额守恒、审批、质量整改、预警和项目收尾。相同 package 重跑必须保持全部计数不变。
