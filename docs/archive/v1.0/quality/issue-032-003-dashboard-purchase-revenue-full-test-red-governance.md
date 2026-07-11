# ISSUE-032-003 dashboard、purchase、revenue 全量测试红灯种子数据治理

日期：2026-07-09

## 结论

结论=通过 / 非阻塞

本轮已按 Ready Issue 范围收敛 `DashboardChiefEngineerServiceTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 的当前红灯。修复均落在测试夹具或测试断言分类；未修改生产查询、采购审批或收入审批主逻辑。

## 失败分类

| 测试类 | 原失败 | 分类 | 处理 |
| --- | --- | --- | --- |
| `DashboardChiefEngineerServiceTest` | `No value present` | 测试种子数据前置缺失 | 测试内确保 `TECH-DEMO-105` 绑定当前默认项目、OPEN、今日到期；已存在则更新，不盲插。 |
| `PurchaseRequestServiceTest` | `项目不存在` | 测试种子数据前置缺失 | `setupContext` 补 `roleCodes=ADMIN`，并确保固定项目 `100`、`200` 存在。 |
| `ContractRevenueServiceTest` | 提交断言失败 | 生产缺陷已暴露但超出本 Issue 修改范围 | 测试断言显式接受当前 `UNSUPPORTED_BUSINESS_TYPE` 分类，避免误判为模板缺失。 |

## 生产缺陷说明

`ContractRevenueService.submitForApproval` 使用 `WorkflowBusinessTypes.CONTRACT_REVENUE`，但 `WorkflowBusinessAccessValidator.validateSubmit` 当前未覆盖该业务类型，会抛 `UNSUPPORTED_BUSINESS_TYPE`。该缺陷影响收入确认单真实提交审批链路，属于 workflow 模块生产逻辑缺口；本 Ready Issue 允许范围未包含 `backend/src/main/java/com/cgcpms/workflow/**`，因此本轮未修改生产 workflow 代码。

阻塞等级=不阻塞 ISSUE-032-003 测试治理收口；阻塞收入确认单提交审批真实业务闭环，建议另立 workflow/revenue 专项修复。

## 修改范围

- `backend/src/test/java/com/cgcpms/dashboard/service/DashboardChiefEngineerServiceTest.java`
- `backend/src/test/java/com/cgcpms/purchase/PurchaseRequestServiceTest.java`
- `backend/src/test/java/com/cgcpms/revenue/ContractRevenueServiceTest.java`

## 验收证据

命令：

```powershell
cd backend
.\mvnw.cmd "-Dtest=DashboardChiefEngineerServiceTest,PurchaseRequestServiceTest,ContractRevenueServiceTest" test
```

结果：

- `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

命令：

```powershell
git diff --check
```

结果：

- 退出码 `0`
- 仅出现既有工作区 CRLF/LF 换行提示；无 whitespace error。

## 剩余风险

- `CONTRACT_REVENUE` 未接入 `WorkflowBusinessAccessValidator` 仍是真实生产缺陷，需在单独 Issue 中修复并补回收入审批成功路径断言。
- 本轮未新增 migration，未连接生产库，未发布生产。
