# ISSUE-032-001 workflow 全量测试红灯夹具与业务类型注册治理

## 结论

结论：通过。workflow 域夹具治理已完成，Ready Issue 指定 Maven 命令复验通过。

阻塞：无。

分类：本轮 workflow 原始红灯属于测试夹具债与断言漂移，不是审批状态机缺陷。

## 处理内容

- `WorkflowEngineIntegrationTest`：为真实 `CONTRACT_APPROVAL` 提交补齐合同与项目夹具，并按多租户用例分配业务对象租户。
- `WorkflowConcurrencyTest`：为并发提交场景补齐合同与项目夹具。
- `WorkflowCoreServiceTest`：移除测试专用 workflow businessType 提交流程，改用已注册 `CONTRACT_APPROVAL`，保留模板查询和金额匹配测试意图。
- `WorkflowApproverResolverTest`：将测试专用 businessType 收敛为 `CONTRACT_APPROVAL`，用金额区间隔离角色审批与空审批人模板。
- `WorkflowTemplateManagementTest`：将模板快照测试改为真实 `CONTRACT_APPROVAL`，用金额区间命中特定测试模板。

## 验证证据

- 首次复现：`cd backend; .\mvnw.cmd "-Dtest=WorkflowEngineIntegrationTest,WorkflowCoreServiceTest,WorkflowConcurrencyTest,WorkflowApproverResolverTest,WorkflowTemplateManagementTest" test`
  - 结果：失败。
  - 证据：50 tests，6 failures，22 errors。
  - 主要错误：`审批业务对象不存在`、`不支持的业务类型`、`UNSUPPORTED_BUSINESS_TYPE` 导致的错误码断言漂移。
- 修复后复验同一命令：
  - 结果：通过，Maven 退出码 0。
  - 命令：`cd backend; .\mvnw.cmd "-Dtest=WorkflowEngineIntegrationTest,WorkflowCoreServiceTest,WorkflowConcurrencyTest,WorkflowApproverResolverTest,WorkflowTemplateManagementTest" test`
- `git diff --check`：通过；仅有换行转换 warning，无 whitespace error。

## 风险与边界

- 未修改 `backend/src/main/resources/db/migration/**`。
- 未修改生产凭据、生产数据库连接、生产发布配置。
- 未修改 dashboard / invoice / purchase / revenue 业务代码。
- 未放宽审批权限、租户隔离、审批状态边界。

## 剩余风险

- 阻塞：无。
- 非阻塞：本轮新增测试夹具使用固定测试项目 / 合同 ID；仅用于本地 H2 测试数据，不新增 migration。
