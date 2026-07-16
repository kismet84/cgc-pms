# ISSUE-047-003 日报 Controller 测试 JWT 密钥环境隔离修复验收报告

结论：通过；阻塞：无。

## 范围与结果

- `SiteDailyLogControllerTest` 的 `SpringBootTest.properties` 增加测试类专用强 `jwt.secret`，长度满足JWT HMAC至少256-bit要求。
- 外部 `TEST_JWT_SECRET` 未读取、覆盖或清除；`JwtUtils`强密钥校验、共享local/test profile、生产配置、日报业务代码和测试断言均未修改。
- 修复与现有隔离良好的Controller测试模式一致，只消除目标测试上下文对用户环境变量的依赖。

## 验收证据

- 修复前：Spring在 `JwtUtils` 构造阶段抛 `WeakKeyException`，报告外部密钥仅144-bit，0条业务断言执行。
- 修复后第一轮：`SiteDailyLogControllerTest` 3项通过，0失败、0错误。
- 修复后第二轮：同一3项再次通过，0失败、0错误；两轮合计6项。
- Ready lint、允许路径和 `git diff --check`：通过；共享profile、生产源码、前端、脚本和部署目录无修改。

## Reviewer复核

- 结论：PASS；findings：无。
- 密钥只存在测试源码，不是生产凭据；没有降低JwtUtils的安全门槛，也没有污染其他测试上下文或修改用户环境。
- 目标测试实际执行日报创建、更新、提交、重复提交拒绝、NULL/0、负数、小数、越界人数与天气长度边界。

## 治理收口

- `AUTOPILOT-SITE-DAILY-JWT-ISOLATION` 已从唯一问题载体移除，`ISSUE-040-052` 恢复Ready。
- 新增后续项：0
- 关闭后续项：1
- 后续项净变化：-1
- 最小回滚：回退测试类properties和本项治理文档；不影响业务代码、schema或数据。

剩余风险：无；本结论只证明目标Controller测试的JWT环境隔离，不外推为全仓所有测试类已完成同类审计。

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-047-003
- Ready 哈希：04963dbc88863f6199ddbf2acfdda7ca1421cc40b6b4a76c80d3c6535f3c13b6
- 实施提交：8d4ea35c09ccf3aa9e35e68648fcd2233a5a0c12
- 验证差异哈希：3e2476db27618dac0f56ab2ffb2bc08704f829d143cc51fd015ddcf269127fa9
- Evidence manifest：fca3173818e1c058657edffe299cf1df91ac824fdaa01b6031ec5dfe3ad35ee6
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":0,"contextDeltaBuildCount":0,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":357,"REVIEWED":0,"REVIEWING":0,"VALIDATED":0,"VALIDATING":0},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":2,"validationReusedCount":0,"wallClockSeconds":357}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：b28e66962a150f1cb4c2c617595bad81b80be773a2f3219a8f280a80b301ffce
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=c2a2f416d1a6565380fc12a3872cd924b375bbe2d34567d6d4c25959db45bd6f -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "c2a2f416d1a6565380fc12a3872cd924b375bbe2d34567d6d4c25959db45bd6f",
  "issueId": "ISSUE-047-003",
  "implementationCommit": "8d4ea35c09ccf3aa9e35e68648fcd2233a5a0c12",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-16T06:59:08.7181750+08:00",
  "total": 100,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 357,
    "businessPhaseSeconds": 357,
    "controlPlaneSeconds": 0,
    "semanticProgressAt": "07/16/2026 06:58:53",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-047-003-日报Controller测试JWT密钥环境隔离修复验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
