# 第 12 条主线核心业务闭环收口归档（2026-07-02）

## 1. 结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 建议：合入

第 12 条主线原计划中的三个前端 GAP 已全部验收通过，真实 Browser 冒烟通过；本轮补了一个最小后端一致性修复，用于阻断 `PENDING task + 非 RUNNING instance` 脏数据进入 `/approval/todo`。当前无新增阻塞点，建议按本归档结论合入。

## 2. 本轮代码改动文件

1. `backend/src/main/java/com/cgcpms/workflow/service/WorkflowQueryService.java`
2. `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`

## 3. 验证命令

- `backend\\mvnw.cmd -q -Dtest=WorkflowQueryServiceTest test`

## 4. 验证结果

- `WorkflowQueryServiceTest`：31 tests, 0 failures
- 结果：`BUILD SUCCESS`
- 运行态：真实 Browser 冒烟通过

## 5. 剩余风险

1. 仅覆盖本轮已确认边界，未展开全站审批链路再审。
2. `pendingDays` / 日期语义后续若再扩面，仍需单独裁决。
3. 这轮只做最小一致性补丁，不代表后续没有新增数据脏写风险。

## 6. 不纳入范围

1. 后端权限修复包
2. 审批引擎改造
3. UI 大改
4. 全站日期治理
5. 新业务模块扩面
6. 迁移脚本与非必要测试体系重构
