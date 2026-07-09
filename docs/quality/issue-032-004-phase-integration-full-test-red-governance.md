# ISSUE-032-004 Phase2 与 Phase4 历史集成链路红灯治理

日期：2026-07-09

## 结论

结论=通过 / 非阻塞

`Phase2FullChainIntegrationTest` 与 `Phase4IntegrationTest` 当前红灯已收敛。失败归因为历史集成测试夹具仍绕过当前真实业务校验，不是金额口径、审批状态机或租户隔离规则缺陷。

本轮只补测试夹具：未新增 migration，未修改生产凭据、生产数据库连接或生产发布配置，未放宽 workflow 业务对象校验。

## 前序专项引用

- `ISSUE-032-001` 已确认 workflow 原始红灯属于测试夹具债与断言漂移，并通过指定 workflow 测试复验。
- `ISSUE-032-002` 已确认 invoice / migration 红灯属于发票项目关系测试夹具缺失，并保留真实业务 fail-close 口径。
- `ISSUE-032-003` 已确认 dashboard / purchase / revenue 当前红灯已收敛；收入审批真实缺口另列风险，不在本 Issue 内重复修底层夹具。

## 失败分类

| 测试类 | 原失败 | 分类 | 处理 |
| --- | --- | --- | --- |
| `Phase2FullChainIntegrationTest#test07_writebackAndLinkage` | `PAY_APP_NOT_APPROVED / 仅审批通过的付款申请可付款` | 测试夹具口径漂移 | 回写前将本测试新建付款申请推进为 `APPROVED`，匹配 `PayRecordService.writeback` 当前真实入口约束 |
| `Phase4IntegrationTest#test04_ccChain` | `PURCHASE_REQUEST_NOT_FOUND / 审批业务对象不存在` | 测试夹具缺业务对象 | 用真实草稿采购申请 ID 提交 workflow，替代随机 `fakeBusinessId` |
| `Phase4IntegrationTest#test08_tenantIsolationCcAndMatrix` | `PURCHASE_REQUEST_NOT_FOUND / 审批业务对象不存在` | 测试夹具缺业务对象 | 同上，保留 tenant 0 业务对象与 CC 隔离验证 |

## 修改范围

- `backend/src/test/java/com/cgcpms/Phase2FullChainIntegrationTest.java`
- `backend/src/test/java/com/cgcpms/Phase4IntegrationTest.java`
- `docs/backlog/ready-issues.md`
- `docs/quality/issue-032-004-phase-integration-full-test-red-governance.md`

## 验收证据

命令：

```powershell
cd backend
.\mvnw.cmd "-Dtest=Phase2FullChainIntegrationTest,Phase4IntegrationTest" test
```

结果：

- `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

命令：

```powershell
git diff --check
```

结果：通过。仅出现既有工作区 CRLF/LF 换行转换 warning，无 whitespace error。

## 剩余风险

- 阻塞：无。`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest` 不再阻塞后端全量门禁。
- 非阻塞：本轮只验证 Ready Issue 指定的两个历史集成测试类，未重新执行后端全量 `.\mvnw.cmd test`。
