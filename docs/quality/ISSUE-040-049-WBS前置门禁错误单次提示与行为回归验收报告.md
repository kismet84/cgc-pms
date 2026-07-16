# ISSUE-040-049 WBS前置门禁错误单次提示与行为回归验收报告

## 验收结论

- 结论：通过。
- 阻塞：无。
- 任务性质：缺口修复。
- 范围：请求错误已提示标记、WBS 保存失败兜底条件、请求层与组件行为测试；未修改后端、数据模型或其他页面错误策略。

## 实现与边界

- `request.ts` 新增不可枚举的请求错误已提示标记和查询函数，只对对象错误附加元数据，不改变原错误类型、message 或 Promise 拒绝语义。
- 业务响应错误、非401响应错误和401刷新失败在拒绝前均带标记；非401错误仍由请求层显示原有错误提示。
- WBS `handleModalOk` catch 对已标记错误静默，对未标记错误继续显示一次页面兜底；成功提示、本地校验 warning、401 登录过期流程和前置状态禁用保持原行为。
- 移除关键 catch 源码字符串断言，改为真实挂载组件并调用保存行为的运行时测试。

## 验收证据

- `pnpm vitest run src/api/__tests__/request.test.ts src/pages/subcontract/__tests__/task.test.ts`：2个文件、17项测试通过。
- 行为断言：请求层先调用一次 `message.error` 并拒绝已标记错误后，WBS catch 不再追加提示，总调用次数严格为1；未标记普通异常由页面兜底，调用次数严格为1。
- `pnpm exec vue-tsc --noEmit`：通过。
- 目标 ESLint：通过。
- 首次目标测试因测试模块完整替换 Pinia、导入请求帮助函数后缺少 `defineStore` 失败；分类为测试模块配置，改为部分 mock 后目标测试通过，未放宽业务断言。
- 真实浏览器通过 dev-login 进入分包任务页；首次测试数据写入暴露旧后端 JAR 缺少 Logback `ThrowableProxy`，分类为环境前置。干净打包、容器重启与184秒稳定采样后接口恢复。
- 浏览器创建未完成前置任务与后置任务，编辑页只出现1处“前置任务未完成，当前任务不能开工或完成”，且进行中/已完成选项被禁用；精确的请求拒绝提示次数由上述组件行为测试裁决。
- 测试数据删除前确认 dev 环境、数据库主机 `127.0.0.1`、`.codex-autopilot/ALLOW_TEST_DATA_RESET` 三项同时满足；两条任务依序删除，复读剩余0条。
- `git diff --check`、Ready lint、当前问题 JSON 解析和允许路径复核：通过。

## 风险复核

- 标记使用 Symbol 且不可枚举，不进入序列化，不污染后端载荷或日志字段。
- 只抑制请求层已提示错误；非请求异常、页面本地异常与表单异常仍有兜底，未形成静默失败。
- 本次未修改后端门禁、WBS 状态机或其他页面 catch；剩余风险限于其他页面仍可能各自存在重复提示，因没有复现证据和明确价值，本轮不制造泛化后续项。

## 后续项收口

- 本轮新增后续项：0
- 本轮关闭后续项：1
- 关闭问题键：OBS-WBS-DUPLICATE-ERROR
- 本轮后续项净变化：-1

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-040-049
- Ready 哈希：a3d960deb609af4cf8f997ab61f6094aaffac1ba0c7af9b1372a50c0fc3a0145
- 实施提交：e4b13d1952c6a8b60c77479a12a2db38f9b9a971
- 验证差异哈希：8d6024810d71601dadd625d1d6c0f2bafa60fc2f325a45ca9624ef6b9f1aff4f
- Evidence manifest：e2302a8d3f29163c3ccc7597f00937a5c67957979f61139c6904f9996757d006
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":0,"contextDeltaBuildCount":0,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":1482,"REVIEWED":14,"REVIEWING":0,"VALIDATED":0,"VALIDATING":0},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":1,"validationReusedCount":0,"wallClockSeconds":1497}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：910f17f4811eb74ad9e5f2b7b682b07fb1f0b13c70ad7b32a37e21cce9682a9f
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=c154e28275d8c142f389b9cdd75b5545dd2d652c2b59fed9dcc120d1a418fa31 -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "c154e28275d8c142f389b9cdd75b5545dd2d652c2b59fed9dcc120d1a418fa31",
  "issueId": "ISSUE-040-049",
  "implementationCommit": "e4b13d1952c6a8b60c77479a12a2db38f9b9a971",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-16T05:23:15.3168946+08:00",
  "total": 95,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 5,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 1497,
    "businessPhaseSeconds": 1482,
    "controlPlaneSeconds": 15,
    "semanticProgressAt": "07/16/2026 05:23:12",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-040-049-WBS前置门禁错误单次提示与行为回归验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
