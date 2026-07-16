# ISSUE-040-048 PowerShell 7控制面与UTF-8兼容回归验收报告

**Goal:** 将真实 PowerShell 7 宿主与 AutoPilot 控制面、连续 runner、状态机及 UTF-8 上下文回归绑定为正式证据，解除过期的“本机未安装 PowerShell 7”观察项。

**Architecture:** 只执行仓库既有确定性 PowerShell 自测并回写治理文档；复用 `Resolve-AutopilotPowerShellHost`、控制面指纹、连续 runner、状态机与 context delta 测试，不修改任何 ps1、配置、插件、hooks、skills 或规则文件，也不启动嵌套 Codex。

## 结论

- 验收结论：通过。
- 阻塞状态：无阻塞。
- 任务性质：回归证明，不是新增 AutoPilot 能力。
- 剩余风险：结论仅覆盖当前 Windows 与 PowerShell 7.6.3；Linux/macOS、PowerShell 8 预览版和第三方脚本不在本任务范围，且没有证据要求本轮扩项。

## 验收证据

- `pwsh --version`：PowerShell 7.6.3，满足主版本至少7。
- `test-control-plane.ps1`：退出码0，输出 `control plane self-test passed`。
- `test-state-machine.ps1`：退出码0，输出 `state machine self-test passed`。
- `tests/test-context-delta.ps1`：退出码0，输出 `context delta self-test passed`；覆盖中文 UTF-8 往返与输出无 BOM。
- `test-continuous-runner.ps1`：独立359.6秒窗口退出码0；执行模式、run lock fencing、控制面指纹、恢复/phase recovery、stall、review/repair、closeout、完成计数与报告投影子套件均通过。

## 失败分类与复验

- 首次将四命令并行包装在180秒窗口，聚合命令于184秒超时且未保留子结果；按规则分类为验证编排超时，不定性为控制面失败。
- 拆分后三个快速专项分别明确通过，连续 runner 改用360秒独立有界窗口并在359.6秒通过；未修改源码、测试断言或运行配置。

## Reviewer 裁决

- 所有专项均由真实 pwsh 7.6.3 执行，核心中文/UTF-8、状态原子性、fencing 和收口语义有明确退出码0证据。
- Git 差异仅包含 backlog、项目地图和本正式报告，不含 ps1、JSON 配置、插件、hooks、skills、AGENTS 或业务代码。
- 结论：PASS，findings=无。

## 后续项收口

- 本轮新增后续项：0
- 本轮关闭后续项：1
- 关闭问题键：`OBS-POWERSHELL7-COMPAT`
- 本轮后续项净变化：-1

<!-- AUTOPILOT-FACTS:START -->
## AutoPilot 自动事实

- Issue：ISSUE-040-048
- Ready 哈希：1e8b2f811c41a585f1b8e62114f656cfe2f6ac37daaf74ae0530c522fad9d083
- 实施提交：60c12be200067dd8960d7252cc6d872e210dd24c
- 验证差异哈希：99ee09d8b058a8116a8ff411c75b3c8dc81175fddcc9d6d41e3a3015bd544a2c
- Evidence manifest：e2302a8d3f29163c3ccc7597f00937a5c67957979f61139c6904f9996757d006
- Reviewer：required=True; decision=PASS
- 后续项：added=0; closed=1; net=-1
- 指标：{"closeoutDispatchCount":1,"contextBaseBuildCount":0,"contextDeltaBuildCount":0,"executorInvocationCount":0,"implementationDispatchCount":1,"inputTokens":null,"outputTokens":null,"phaseDurationsSeconds":{"IMPLEMENTED":0,"IMPLEMENTING":647,"REVIEWED":1,"REVIEWING":0,"VALIDATED":0,"VALIDATING":0},"phaseRestartCount":0,"plannerCandidateRefs":[],"plannerInvocationCount":0,"repairDispatchCount":0,"reportProjectionCount":1,"reviewDispatchCount":1,"reviewerInvocationCount":0,"runResumeCount":0,"tokenUsageStatus":"not_available","totalTokens":null,"validationDispatchCount":1,"validationExecutedCount":1,"validationReusedCount":0,"wallClockSeconds":649}
- 控制面指纹：93a0a2677a888b909ef128a07c3bcff648115707190edb8b9555709baf6c5e74
- PreCloseout Facts：cd52f325dc045da199fe76b011c79e7d9b1c18dc8e35d00c34dc59c28b99d9b0
<!-- AUTOPILOT-FACTS:END -->

<!-- AUTOPILOT-TASK-SCORE:BEGIN key=246020de1ed76b9e4ed2c2e0e5869b39f2b2456f4ab509a53402932f7e26cbc4 -->
## AutoPilot 任务评分

```json
{
  "schemaVersion": 2,
  "key": "246020de1ed76b9e4ed2c2e0e5869b39f2b2456f4ab509a53402932f7e26cbc4",
  "issueId": "ISSUE-040-048",
  "implementationCommit": "60c12be200067dd8960d7252cc6d872e210dd24c",
  "scoringVersion": "autopilot-task-score/v2",
  "scoredAt": "2026-07-16T04:55:51.2668088+08:00",
  "total": 100,
  "dimensions": {
    "deliveryCorrectness": {
      "score": 35,
      "max": 35,
      "evidence": [
        "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
      ]
    },
    "zeroDanglingIssues": {
      "score": 25,
      "max": 25,
      "evidence": [
        "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
      ]
    },
    "firstPassAcceptance": {
      "score": 20,
      "max": 20,
      "evidence": [
        "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
      ]
    },
    "taskExecutionEfficiency": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
      ]
    },
    "stockIssueReduction": {
      "score": 10,
      "max": 10,
      "evidence": [
        "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
      ]
    }
  },
  "hardGatesPassed": true,
  "followupNetChange": -1,
  "executionTiming": {
    "wallClockSeconds": 649,
    "businessPhaseSeconds": 647,
    "controlPlaneSeconds": 2,
    "semanticProgressAt": "07/16/2026 04:55:48",
    "livenessSignalsExcluded": true
  },
  "sourceRefs": [
    "docs/quality/ISSUE-040-048-PowerShell7控制面与UTF8兼容回归验收报告.md"
  ],
  "shadow": false
}
```
<!-- AUTOPILOT-TASK-SCORE:END -->
