# AutoPilot 任务评分 v1 批准决策

## 决策

- 批准状态：`APPROVED`
- 评分版本：`autopilot-task-score/v1`
- 批准时间：`2026-07-13T18:30:59.8101265+08:00`
- 用户批准原文：`批准 autopilot-task-score/v1，权重35/25/20/10/10，自下一项新实施型 Ready 生效，并本地提交`
- 生效边界：本批准配置形成正式本地提交之后启动的下一项新实施型 Ready Issue；历史任务、已完成任务和当前 `LIMIT_REACHED` 运行态不回算、不补记。

## 批准权重

| 维度 | 权重 |
| --- | ---: |
| 交付正确性 | 35 |
| 问题零悬空 | 25 |
| 首次验收通过率 | 20 |
| 周期效率 | 10 |
| 存量问题下降速度 | 10 |

## 激活配置

- `taskScoring.enabled=true`
- `taskScoring.activeVersion=autopilot-task-score/v1`
- `taskScoring.approvalStatus=APPROVED`
- `retrospective.enabled=true`
- `retrospective.threshold=20`
- `retrospective.requireUserRestart=true`

评分与回顾必须共同启用；任一配置缺失或不一致均 fail-close。只有通过全部硬门禁、完成正式归档、形成不同的 `implementationCommit` 与 `closeoutCommit` 并完成 ledger 登记的实施型 Ready 才计数。

## 不变边界

- 低分只进入周期观测，不改变 DONE 裁决，不触发自动补修或回滚。
- 自动回顾只生成 `NEEDS_CONFIRMATION` 改进提案，不自动修改代码、规则、权重或环境。
- 无界模式第20个有效任务后阻断第21个；有界模式完成当前 N 后整批回顾且不结转。
- `autoPush=false`，不发布生产、不连接生产数据库。

## 收口

- 本轮新增后续项：0。
- 本轮关闭后续项：0。
- 后续项净变化：0。
- 已关闭待决项：`autopilot-task-score/v1` 版本与权重批准门。
