# ISSUE-040-056 WBS单前置FS门禁与风险提示回归验收报告

结论：通过；阻塞：无。

## 范围与结果

- 证明分包任务现有单前置FS关联在当前master生效：前置与后续任务必须属于同一项目，计划开始不得早于前置计划结束，反向引用形成的环依赖会被拒绝。
- 前置任务未完成时，后续任务不能直接创建或更新为进行中、已完成；前置完成后允许开工，显式解除前置后恢复独立流转。
- 前端只读WBS/甘特概览区分未完成前置与计划迟交风险；请求层已经提示的前置门禁错误不会被页面重复提示，未提示的本地异常仍保留一次兜底。
- 本项没有修改业务代码、数据库、测试实现、权限、状态机或运行数据；不宣称多前置、SS/FF/SF、lag、自动排程、基线、关键路径、资源平衡或甘特拖拽完成。

## 验收证据

- 后端：`SubTaskControllerTest` 14/14通过，0失败、0错误、0跳过；覆盖同项目前置关联、FS日期、环依赖、前置被引用删除、未完成前置的创建/开工/完成门禁、前置完成后的放行、解除前置和跨项目拒绝。
- 前端：`task.test.ts` 11/11通过；覆盖只读WBS/甘特、仅逾期且未完成行标记迟交、单前置FS风险、请求层已提示错误不重复提示、本地异常一次兜底，以及不引入依赖线、拖拽或甘特库的最小范围。
- 运行态：backend、frontend与dev-login三项health gate均返回200。
- 真实浏览器：开发演示超级管理员进入 `/subcontract/task`，页面显示“项目内 WBS 树与只读甘特展示”；打开“新建分包任务”后确认存在“前置任务（FS）”同项目选择项，点击“取消”后弹窗消失，控制台新增error/warn为0。未填写字段、未点击确定、未写入任务。
- Ready lint与允许路径：通过；`current-issues.json` JSON解析和`git diff --check`在收口验证执行。

## 失败分类与恢复

- 首轮后端测试在进入WBS断言前失败：local profile中的 `${TEST_JWT_SECRET}` 未解析，`JwtUtils` 将18字节占位符判定为144-bit弱密钥并安全退出。
- 分类：`environment_prerequisite`；影响仅限测试上下文启动，不构成WBS业务失败。
- 恢复：仅为复验进程设置非生产、长度满足HS256要求的测试密钥，未修改仓库配置、用户环境变量或生产安全校验；同一目标命令复验14/14通过。

## Reviewer复核

- 结论：PASS；findings：无。
- 后端以单个 `predecessor_task_id` 为唯一前置关系，服务端核验项目一致性、日期、循环和完成状态；没有把前端禁用当作权威门禁。
- 前端风险展示和单次错误提示与后端拒绝边界一致；浏览器验收只触发页面读取和弹窗打开/取消，没有触发保存接口。
- 表述严格限定为分包任务现有单前置FS闭环；项目计划域已有版本化WBS不等于本项实现完整排程平台，A-04聚合父项继续保留平台化缺口。

## 治理收口

- `OBS-WBS-PREDECESSOR-EVIDENCE` 已从唯一问题载体移除。
- 新增后续项：0
- 关闭后续项：1
- 后续项净变化：-1
- 最小回滚：仅回退本项治理文档与报告。

剩余风险：无；多前置、多依赖类型、lag、自动排程、基线、关键路径和资源平衡继续由A-04聚合父项承接，不属于本项新增后续问题。

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-040-056
- Ready 哈希：f2c3c661f60730e6a6590d7e2f637c6f28a457166956bd5d70efd148b91a31a3
- 实施提交：33b13ba518c3bcbbd7e3437901c51eaafcbb7b80
- 验证差异哈希：6d35e1b5bc32362e82d3ecbbdca440169569c2addd88692d0609cec3a9a912da
- Evidence manifest：42108763507ae879d0e9d95816cc91d0dbe0a6530f4547752369cec91e43fd9c
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":1,"contextDeltaBuildCount":2,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":820,"REVIEWED":149,"REVIEWING":0,"VALIDATED":33,"VALIDATING":53},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":5,"validationReusedCount":0,"wallClockSeconds":1055}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：46ddca2cded57999a91f6ad0f5a8dbacfd700828a795f2aa356a3915727bf7b9
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=a68f18cfb95ebb24da0e4f74c887818f92cd7559badc3ea972165c3a14d88506 -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "a68f18cfb95ebb24da0e4f74c887818f92cd7559badc3ea972165c3a14d88506",
  "issueId": "ISSUE-040-056",
  "implementationCommit": "33b13ba518c3bcbbd7e3437901c51eaafcbb7b80",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-17T13:35:39.2867700+08:00",
  "total": 95,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 5,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 1055,
    "businessPhaseSeconds": 873,
    "controlPlaneSeconds": 182,
    "semanticProgressAt": "07/17/2026 13:35:37",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
