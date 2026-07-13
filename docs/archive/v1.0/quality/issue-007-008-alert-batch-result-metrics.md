# ISSUE-007-008 预警批处理执行结果指标回归

完成日期：2026-07-09

## 目标

- 回归预警批处理执行结果的本地可观测性。
- 在不接入外部监控平台、不记录敏感信息的前提下，补齐最小执行结果指标断言。

## 修改范围

- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
  - 为批量已读和批量状态更新响应补充 `metrics` 计数块。
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
  - 在批量已读部分成功和批量状态非法失败场景中断言 `metrics.total/success/failed/skipped`。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/iterations/iteration-2026-07-08-report.md`
  - 完成本轮 backlog 与 iteration 收口，并拆出下一轮 Ready Issue。

## 指标边界

本轮新增本地响应指标：

- `data.metrics.total`
- `data.metrics.success`
- `data.metrics.failed`
- `data.metrics.skipped`

覆盖：

- `PUT /alerts/batch/read`：批量已读部分成功、失败计数。
- `PUT /alerts/batch/status`：批量状态更新成功、失败计数。
- 空列表输入：返回 0 计数。

不记录：

- 用户隐私
- Token
- 外部通知凭据
- 预警消息正文
- 失败原因正文

未覆盖：

- Prometheus 抓取、Grafana 面板、外部告警平台。
- 调度任务级执行历史表；本轮只补现有批处理响应的最小可观测计数。

## 验证记录

- 测试类存在性预检：
  - `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`：存在。
  - `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`：存在。
- 红灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=AlertControllerTest" test`
  - 结果：失败，`No value at JSON path "$.data.metrics.total"`，证明新增断言命中缺失指标。
- 指定验证命令：
  - `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest,AlertControllerTest" test`
  - 结果：通过，`Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 自审结论

PASS。

依据：

- 预警批处理响应已有自动化断言覆盖成功、失败和跳过计数。
- `metrics` 只包含整数计数，不包含失败原因、消息正文、Token、凭据或用户隐私。
- 未新增依赖，未接入外部监控平台，未修改前端、migration、deploy 或生产凭据。

## 结论

通过 / 非阻塞。

剩余风险：

- 本轮只覆盖现有预警批处理接口响应指标，不提供外部监控采集和告警面板。
- `failures[].reason` 仍保留为用户可见接口失败原因；正式指标块不复用该字段，后续如需外部采集只能采集 `metrics` 计数。
