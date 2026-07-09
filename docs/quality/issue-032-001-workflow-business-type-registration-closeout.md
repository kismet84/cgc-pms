# ISSUE-032-001 workflow 业务类型注册缺口收口报告

日期：2026-07-09
Issue：ISSUE-032-001 workflow 全量测试红灯夹具与业务类型注册治理
类型：后端测试治理收口 / workflow 业务类型注册缺口归档 / 队列状态同步
结论：通过（正式收口完成）/ 非阻塞

本报告用于裁决 `ISSUE-032-001` 是否可以从 Ready 队列正式收口。历史入口名称虽然来自“workflow 全量测试红灯治理”，但本包最终实际修复的是 `CONTRACT_REVENUE` 的 workflow 业务类型注册缺口；当前口径应更新为“当前已绿 + 注册缺口治理完成”，不得继续写成现存 workflow 红灯。

## 1. 收口范围

允许修改范围：

- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`

本轮收口只引用已完成的实现、D 验收、E 审查和阻塞解除结果，不重跑测试、不改业务代码。

## 2. 实际修复事实

本包生产改动口径如下：

1. `WorkflowEngine` 增加 `CONTRACT_REVENUE -> revenue:submit` 映射。
2. `WorkflowBusinessAccessValidator` 增加 `CONTRACT_REVENUE` 提交校验。
3. 原有租户、项目、合同、状态、项目访问权限边界保持不变。
4. 未触碰 `TECH_ITEM`，没有借机扩大 scope。

对应测试改动口径如下：

1. `WorkflowControllerAuthTest` 直接覆盖生产 `WorkflowEngine` 映射，保证鉴权测试与真实映射一致。
2. `ContractRevenueServiceTest` 不再接受 `UNSUPPORTED_BUSINESS_TYPE`，改为以真实提交审批成功路径为目标。

裁决：

- `ISSUE-032-001` 最终不再是“仅测试夹具治理”。
- 真实收口对象是 `CONTRACT_REVENUE` workflow 业务类型注册缺口，且修复范围受控在 workflow 域。

## 3. E 审查结论引用

本轮直接采信 E 的正式审查结论：

1. 未发现阻塞级权限、租户、项目、合同、状态越权。
2. `revenue:submit` 与 `ContractRevenueController submit` 入口一致。
3. `TECH_ITEM` 未被扩大纳入本次修复范围。

据此可裁定：

- 本轮不是通过放宽权限或扩大业务类型覆盖面来“过测”。
- `CONTRACT_REVENUE` 的接入点与现有收入确认提交流程一致。

## 4. D 验收与阻塞解除证据

本轮正式采信以下最终证据：

| 证据 | 结果 | 本轮裁决 |
| --- | --- | --- |
| `cd backend; .\mvnw.cmd "-Dtest=ContractRevenueServiceTest,WorkflowControllerAuthTest" test` | 通过，29 tests，0 failures/errors | 采信 |
| `cd backend; .\mvnw.cmd "-Dtest=WorkflowEngineIntegrationTest,WorkflowCoreServiceTest,WorkflowTemplateManagementTest" test` | 通过，43 tests，0 failures/errors | 采信 |
| `git diff --check` | 通过 | 采信 |
| 旧 `testCompile` 阻塞 | 本轮已无法复现 | 归类为瞬时/前置已解除，不挂 blocked |

说明：

- 此前 `testCompile` 旧测试阻塞不再复现，因此不再作为 `ISSUE-032-001` 的当前阻塞项。
- 本轮收口依据是“目标链路已绿 + 审查通过 + whitespace 门禁通过”，而不是历史红灯标题本身。

## 5. 历史红灯入口与当前口径

需要明确区分两层事实：

1. `ISSUE-032-001` 的立项入口来自 mainline-32 M2 的 workflow 红灯分诊。
2. 本次真正落地并完成收口的缺口，是 `CONTRACT_REVENUE` 未完成 workflow 业务类型注册。

因此本轮最终口径应写为：

- workflow 历史红灯入口已完成治理并转绿；
- 对应真实生产缺口是 `CONTRACT_REVENUE` 注册缺口，现已完成最小修复与验收闭环。

不得再写为：

- “workflow 当前仍有红灯但先归档”
- “只是测试夹具债，没有生产改动”

## 6. 未纳入本轮通过口径的事项

以下事项本轮不写成已完成：

1. `TECH_ITEM` 是否需要真实接入 workflow，需另题确认。
2. workflow 模板或测试数据前置类风险未在本报告中扩展治理。
3. `MockBean`、Prometheus 既有 warning 仍属观察项，不作为本 Issue 阻塞。

## 7. Backlog 同步动作

本轮最小同步如下：

1. 从 `docs/backlog/ready-issues.md` 移除 `ISSUE-032-001` 当前 Ready 入口。
2. 在 `docs/backlog/done-issues.md` 新增 `ISSUE-032-001` 完成记录。
3. 在 `docs/backlog/current-focus.md` 移除“当前唯一 Ready=ISSUE-032-001”的描述，改为等待主线程重新拆题。
4. 在 `docs/iterations/iteration-2026-07-09-report.md` 追加本次正式收口记录。

## 8. 最终裁决

正式交付物：

- `docs/quality/issue-032-001-workflow-business-type-registration-closeout.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/backlog/current-focus.md`
- `docs/iterations/iteration-2026-07-09-report.md`

验收证据：

- E 审查通过：未发现阻塞级越权；`revenue:submit` 与收入确认提交入口一致；`TECH_ITEM` 未扩大 scope。
- D/阻塞解除最终证据：两个指定 Maven 命令均通过，分别为 `29` tests、`43` tests，且 `0 failures/errors`。
- `git diff --check` 通过。
- 旧 `testCompile` 阻塞已无法复现，归类为瞬时/前置已解除。

临时产物：无。

结论：通过。
阻塞：无。
剩余风险：

1. 模板/测试数据前置类风险仍需后续专项继续观察。
2. `MockBean`、Prometheus 既有 warning 仍在，但不构成当前阻塞。
3. `TECH_ITEM` 是否真实接入 workflow 需另题确认，不能从本报告外推为已完成。
