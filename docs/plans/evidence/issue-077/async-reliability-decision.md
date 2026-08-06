# ISSUE-077-001 异步可靠性裁决

> 结论：`RELIABLE-TASK-01 NOT_TRIGGERED`。不新增通用任务表、处理器注册、管理工作台或消息中间件。

| 调用点 | 事实/副作用 | 可重复与恢复 | 当前持久性 | 裁决 |
| --- | --- | --- | --- | --- |
| `ProjectArchiveNotifier.checkAndNotify @Async` | 项目归档资格提示/站内通知 | 可从项目、结算和归档事实重算；通知可补读 | 进程内执行，通知 DB 持久 | 保留进程内；提示丢失不改变项目事实 |
| `AlertEvaluationService.scheduledEvaluate` | 预警求值及通知 | 从业务事实重算；重复受预警规则/发送记录约束 | 调度触发，预警/发送记录持久 | 保留定时重算 |
| `CostSummaryService.scheduledRefresh` | 成本汇总投影 | 从权威成本事实重算 | 调度触发，投影写 DB | 保留定时重算 |
| `FinanceOperationsService.scheduledReconciliationAndAlerts` | 财务对账与预警 | 从权威账务事实重算 | 调度触发，按租户事务 | 保留定时重算 |
| `CommunicationService.expireDrafts` | 过期草稿清理 | 候选仍在 DB；失败下轮重扫 | 草稿事实持久 | 保留定时扫描 |
| `CommunicationEventService.heartbeat` | SSE 心跳 | 无业务事实；允许丢失 | 进程内 | 保留进程内 |
| `FileObjectTaskService.retryPending` | 对象删除/Office 预览派生物 | 稳定幂等键、重试、终态、卡死恢复 | `sys_file_object_task` | 复用现有文件任务，不重复建设 |
| `DocumentGenerationAutoRetryService` | 文档生成失败重试 | `biz_document_generation` 保存状态、幂等键、重试来源 | 领域记录已持久 | 复用现有文档生成事实 |

触发条件复核：文件删除/预览属于同一文件领域且已由现有任务覆盖；文档生成已有持久失败记录和重试；剩余任务均为提示、心跳或可重算投影。不存在至少两个“现有能力不可复用、丢失影响不可接受、可定义稳定幂等键”的新领域副作用，故条件包关闭。以后只有出现两个独立合格消费者时重新评估。
