# ISSUE-040-054 现金日记账CSV公式注入与导出契约回归验收报告

结论：通过；阻塞：无。

## 范围与结果

- 证明现金日记账同步CSV在首个非空白字符为 `=`、`+`、`-`、`@` 时，在原始单元格值前添加单引号文本前缀；含前导空白的公式内容同样受保护，普通文本不被改写。
- 所有文本字段继续以双引号包裹，内部双引号成对转义；导出内容带UTF-8 BOM，响应类型为 `text/csv;charset=UTF-8`。
- 导出查询通过 `baseWrapper` 固定当前认证租户；Controller入口仍要求 `cashbook:journal:export` 或ADMIN/SUPER_ADMIN，并保留下载审计。
- 本项没有修改业务代码、测试实现、权限、CSV格式、schema或财务数据；不宣称异步导出、对象存储、任务队列、外部报表平台或完整经营分析能力。

## 验收证据

- 服务层：`CashJournalServiceTest` 12/12通过，0失败、0错误、0跳过；公式字符、前导空白、双引号、租户不可见和既有现金日记账边界实际执行。
- Controller层：`CashJournalControllerTest` 4/4通过，0失败、0错误、0跳过；管理员导出当前筛选成功并返回UTF-8 CSV，未认证入口保持拒绝。
- 静态安全核对：导出方法仍有专用 `@PreAuthorize` 与 `@AuditedOperation`，服务查询显式加入当前租户条件。
- Ready lint、允许路径、JSON解析与`git diff --check`：通过。

## 失败分类与恢复

- 目标测试首轮通过；无工具配置、环境前置或真实质量/安全失败。

## Reviewer复核

- 结论：PASS；findings：无。
- 公式判定先去除前导空白进行检测，但安全前缀添加在原始值之前，因此不会丢失空白或改写普通内容。
- CSV双引号转义发生在公式前缀化之后，二者不会相互绕过；空值保持空字段。
- 导出只读取当前租户，权限和下载审计未扩大；报告明确限定为同步导出能力。

## 治理收口

- `OBS-CASHBOOK-CSV-SAFETY` 已从唯一问题载体移除。
- 新增后续项：0
- 关闭后续项：1
- 后续项净变化：-1
- 最小回滚：仅回退本项治理文档与报告。

剩余风险：无；异步任务、对象存储、完整经营分析和外部报表平台继续保留在A-06聚合父项，不属于本项新增后续问题。

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-040-054
- Ready 哈希：1b0fb86e15f31ea7e1228dbf44c4f8909a3fb37fcc4916d0ac7e2c7fabb69a01
- 实施提交：04557568aba54051bf1baad20b73ae3ca6973f13
- 验证差异哈希：baf4fc655c8c60fae735fbca90da75687d1a3b00fa55a57e77f23960e04057b8
- Evidence manifest：f8d17fe31885894579ff4d4d67e7ea3e9a0dea685a6512f4e09757658952ac67
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":0,"contextDeltaBuildCount":0,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":289,"REVIEWED":0,"REVIEWING":0,"VALIDATED":0,"VALIDATING":0},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":1,"validationReusedCount":0,"wallClockSeconds":289}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：caf12438f16478632b904f82575568989fac9b70cc869e85d1954c701481d9db
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=ab2536765a3b26700cbd390665dc1e340c1a22f6bd6a1c022f6136ae8f346d50 -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "ab2536765a3b26700cbd390665dc1e340c1a22f6bd6a1c022f6136ae8f346d50",
  "issueId": "ISSUE-040-054",
  "implementationCommit": "04557568aba54051bf1baad20b73ae3ca6973f13",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-16T07:21:48.0592635+08:00",
  "total": 100,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 289,
    "businessPhaseSeconds": 289,
    "controlPlaneSeconds": 0,
    "semanticProgressAt": "07/16/2026 07:21:25",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-040-054-现金日记账CSV公式注入与导出契约回归验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
