# ISSUE-008-025 供应商评分排名可见性最小落地收口报告

## 结论

- 结论：通过
- 阻塞：无
- 是否可计入本轮实施型 Ready Issue：是，计入第 3 个
- 合并方式：auto-merge/local-commit-only；本次归档不 stage、不 commit、不 push

## 目标与边界

- 目标：在采购经理驾驶舱展示供应商采购订单交期表现排名，包含供应商、订单数、逾期未完成数、交期达成率和当前评分口径。
- 实现边界：前端直接消费既有 `PurchaseManagerDashboardVO.supplierScores` / `supplierScores` 数据，不在页面重新计算评分，不扩展后端契约。
- 明确未做：不新增供应商评分事实表，不新增 npm 依赖，不做询价、比价、定标、黑名单或综合评分配置器。

## 修改摘要

- 采购经理驾驶舱新增供应商采购订单交期表现表格。
- 页面文案限定为“采购订单交期表现”，未包装为综合供应商评级。
- 保持现有采购驾驶舱布局与既有数据权限链路，前端不绕过后端过滤。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=DashboardMaterialRoleServiceTest" test`：通过，12/12。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。
- E 审查：PASS，Critical / Important / Minor 均无阻塞。

## 失败分类或非失败分类

- 非失败分类：后端契约已由 `ISSUE-008-024` 固化，本轮为前端可见性最小落地。
- 真实质量结论：未发现阻塞级质量、安全、权限或数据一致性问题。

## 非阻塞观察与沉淀

- 非阻塞观察：前端测试证据为源码字符串级断言，未覆盖真实浏览器交互；本 Ready 已允许用 type-check、build 和最小等价既有测试收口。
- 沉淀位置：已同步写入 `docs/backlog/current-focus.md`，后续若同类前端可见性题连续重复出现，应由主线程拆成前端真实渲染验收治理 Ready 或转入 blocked。

## 剩余风险

- 当前仍只证明“采购订单交期表现”可见，不代表完整供应商综合评分平台完成。
- 质量、价格、服务、黑名单、询价/比价/定标仍不在本轮范围。
