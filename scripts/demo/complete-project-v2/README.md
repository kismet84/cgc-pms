# 完整演示项目 v2

此数据包面向 V216 schema，显式创建一套可追踪的全业务闭环演示项目。默认数据库基线不执行本目录内容，也不复用会随最终 schema 漂移的历史 Flyway fixture。

安全边界：

- 仅允许 `dev`、`test`、`demo`，MySQL Docker 端口必须绑定 `127.0.0.1`。
- 仓库必须存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`；加载器不执行删除、clean、drop 或 reset。
- 目标库必须已有 `sys_bootstrap_state`、`SUPER_ADMIN`、完成的平台管理员 bootstrap，以及 V216 的项目、合作方、成本来源权威字典。
- 同一 package 通过 `sys_bootstrap_state` 的 19 个阶段键续载；已完成阶段跳过。
- 不提供自动清理。需要重建时创建新的本地 demo 空库。

执行：

```powershell
pwsh scripts/demo/complete-project-v2/load.ps1 -Environment demo -Database cgc_pms_demo_v2
pwsh scripts/demo/complete-project-v2/verify.ps1 -Environment demo -Database cgc_pms_demo_v2
pwsh scripts/demo/complete-project-v2/field-coverage/verify-coverage.ps1 -Database cgc_pms_demo_v2 -ApiBaseUrl http://127.0.0.1:8080/api
```

字段覆盖验收必须连接到使用 `cgc_pms_demo_v2` 的本地 dev/demo 后端；脚本通过 loopback `dev-login` 建立只读会话并核验 DTO 别名真实 API 回读。未提供 API、后端连接到其他数据库或任一字段无回读证据时，验收失败。

数据所有权由 `manifest.yml` 中 package、项目 ID/编码和 `M52-` 业务编码前缀限定。V2 在 V1 核心闭环基础上增加全页面字段覆盖矩阵、财务发票及后续分域三态样本；每阶段单事务提交。`verify.ps1` 校验对象唯一、数量守恒、金额守恒、审批、质量整改、预警、项目收尾和新增域指标。相同 package 重跑必须保持全部计数不变。

## 验收冻结

- 冻结版本：`CGC-COMPLETE-PROJECT` v2，schema 基线 V216。
- 隔离数据库：`cgc_pms_demo_v2`；不覆盖 `cgc_pms` 或 `cgc_pms_demo`。
- 字段清单：`field-coverage/form-field-coverage.csv`，由 `field-coverage/build-form-field-inventory.ps1` 从当前前端表单生成。
- 覆盖门：617 个表单项、591 个绑定字段均已分类；166 张目标表均有记录；374 条外键无孤儿（含 V217 合同租户一致性约束）。
- 业务门：19 个阶段完成；内部业务编号统一为 `{PREFIX}-{yyyyMMdd}-{3位序号}`，历史演示数据同步规范化，银行流水号、凭证号和发票号不改；8 个角色测试账号、8 个项目覆盖 4 类型、5 状态和 5 审批态；在建项目具备八角色统一“高/中/低/其他”风险筛选数据，以及项目经理待办/临期合同、商务变更/计量/结算、采购/验收/低库存、生产领用、总工技术事项和成本经理两级科目分解样本；成本分解父级金额与四个子级合计一致，其他金额、数量、库存、计量、发票分配和会计平衡差额均为 0。
- 数据质量门：合作方完整性、项目/合同/成本权威字典、成本科目、审批场景与质量处置合同追溯的错误/缺失指标均为 0。
- 幂等门：数据质量阶段重复执行两次，全部数量、金额、业务恒等式和权威校验保持不变。
- 正式验收依据：`docs/quality/第52条主线-Phase4.1-全页面演示数据包V2验收报告-2026-07-18.md`。
- 应用规则与三工作树整合验收：`docs/quality/第52-53条主线-DQ-005-DQ-009应用闭环与三工作树整合验收报告-2026-07-19.md`。

本版本只冻结演示数据与覆盖口径，不冻结后续 UI。前端重构必须继续使用独立 V2 UI 路由和组件目录，不得反向修改本数据包来掩盖页面字段缺口。
