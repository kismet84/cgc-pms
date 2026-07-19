## 变更说明

- 目标：
- 范围：
- 风险与回滚：

## 验证

- 首次非 Draft PR HEAD SHA：
- 同 SHA、`push` 事件、成功 CI URL：

- [ ] 已运行 `scripts/codex-autopilot/verify-pre-pr-ci.ps1`，输出 `status=PASS`
- [ ] 后端全量测试 `mvnw -C verify` 通过
- [ ] 后端测试顺序复验 `mvnw -C -Ptest-order-independence test` 通过
- [ ] MySQL 非 root、库级最小权限迁移与基线 smoke 通过
- [ ] 前端 lint、test、type-check、build 通过
- [ ] 后端依赖、前端依赖、SQL 与制品供应链安全扫描通过
- [ ] V2 门禁通过
- [ ] E2E 通过
- [ ] 新增测试使用独立 ID/编码/单号，未修改共享演示数据或 `sys_user.id = 1`
- [ ] 测试在前后置阶段按外键反向顺序物理清理本类数据

缺少任一同 SHA 证据时，只能保持 Draft，不得声明“可提 PR”或转为 Ready for review。

## 业务闭环影响

- [ ] 项目、合同、预算、审批、收付款及台账关联未被破坏，或已补充对应回归测试
