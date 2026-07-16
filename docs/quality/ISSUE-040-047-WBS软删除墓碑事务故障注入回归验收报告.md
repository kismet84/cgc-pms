# ISSUE-040-047 WBS软删除墓碑事务故障注入回归验收报告

**Goal:** 通过真实 Spring 事务故障注入，证明分包任务墓碑编号更新与逻辑删除具有原子性；删除第二步失败时不占用原编号或墓碑命名空间，正常路径仍释放业务编号。

**Architecture:** 仅新增独立 local H2 集成测试，复用 `SubTaskService.delete`、MyBatis 与既有 `@Transactional(rollbackFor = Exception.class)`；Mockito spy 只在真实 `updateById` 成功后、`deleteById` SQL 前注入异常，真实更新通过同事务 `SqlSessionTemplate` Mapper 执行。禁止修改生产删除逻辑、事务传播、数据模型、前端及 AutoPilot 控制面。

## 结论

- 验收结论：通过。
- 阻塞状态：无阻塞。
- 任务性质：回归证明，不是新增 WBS 能力。
- 剩余风险：本专项覆盖单库事务原子性；批量删除、物理删除、多前置 WBS 与跨服务事务不在当前能力范围，且没有证据支持在本轮扩项。

## 实现与边界

- 新增 `SubTaskDeleteTransactionTest`，使用项目10001及专用任务947001/947002。
- 故障路径让真实墓碑 UPDATE 返回1后，在逻辑删除 SQL 前抛出 `TEST_DELETE_BY_ID_FAILURE`；服务事务退出后直接 SQL 复读 task_code 和 deleted_flag。
- 正常路径复读 `DELETED-947001` 与 deleted_flag=1，并插入使用原编号的新活动任务，证明编号释放。
- 每项测试前后物理删除专用 ID，重置 spy 与 UserContext；生产源码、迁移、前端和控制面均未修改。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=SubTaskDeleteTransactionTest" test`：连续两次均为 Tests run: 2, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS。
- 故障路径 SQL 轨迹观测到真实 `UPDATE sub_task ... task_code=DELETED-947001` 更新1行，未执行真实 deleteById SQL；事务结束后断言原编号和 deleted_flag=0。
- 正常路径 SQL 轨迹观测到墓碑 UPDATE 与逻辑删除 UPDATE 各更新1行；原编号复用插入成功。
- Ready lint、JSON 解析、`git diff --check`、允许/禁止路径核对及结构化 Reviewer 结果随 AutoPilot 收口证据保存。

## Reviewer 裁决

- 故障点位由 AtomicBoolean 证明在真实墓碑更新成功之后，并明确位于 deleteById SQL 之前；复读在服务事务异常退出后执行。
- 成功与失败路径同时核对 task_code、deleted_flag 和编号唯一性，专用夹具与线程本地身份均清理。
- 未发现权限、租户、金额、生产数据、迁移或跨模块风险；结论 PASS，findings=无。

## 后续项收口

- 本轮新增后续项：0
- 本轮关闭后续项：1
- 关闭问题键：`OBS-WBS-TOMBSTONE-FAULT`
- 本轮后续项净变化：-1

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-040-047
- Ready 哈希：420202e6587db35497772d21393043d63b7a2ef525b4a80f550455a6917ae8cc
- 实施提交：510c0d2047f88a55eb2a45dacc066b2c55372e97
- 验证差异哈希：5b4702892dc8c6c6efde710831d2906198b0100138fe71924e5474f7ff2d773d
- Evidence manifest：e2302a8d3f29163c3ccc7597f00937a5c67957979f61139c6904f9996757d006
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":0,"contextDeltaBuildCount":0,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":368,"REVIEWED":1,"REVIEWING":0,"VALIDATED":0,"VALIDATING":0},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":1,"validationReusedCount":0,"wallClockSeconds":369}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：bf46c4502851e0aa9e8608f27bc54e6bbcc4365288ade377fcc2d7a0b54203e6
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=9ce9eaca87028fd7f0871ef78627b9b4f044b74c8e3339a0f3c7776ffcc4c724 -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "9ce9eaca87028fd7f0871ef78627b9b4f044b74c8e3339a0f3c7776ffcc4c724",
  "issueId": "ISSUE-040-047",
  "implementationCommit": "510c0d2047f88a55eb2a45dacc066b2c55372e97",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-16T04:42:51.0079061+08:00",
  "total": 100,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 369,
    "businessPhaseSeconds": 368,
    "controlPlaneSeconds": 1,
    "semanticProgressAt": "07/16/2026 04:42:48",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-040-047-WBS软删除墓碑事务故障注入回归验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
