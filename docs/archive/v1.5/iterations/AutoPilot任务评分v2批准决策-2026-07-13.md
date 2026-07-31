# AutoPilot 任务评分 v2 批准决策

## 批准结论

- 用户批准指令：`v2 正式启用`
- `scoringVersion`：`autopilot-task-score/v2`
- 五维权重：`deliveryCorrectness=35`、`zeroDanglingIssues=25`、`firstPassAcceptance=20`、`taskExecutionEfficiency=10`、`stockIssueReduction=10`
- 批准状态：`APPROVED`
- 生效边界：`NEXT_NEW_IMPLEMENTATION_READY`，即本批准配置提交后的下一项新实施型 Ready Issue

## 数据与兼容边界

- v1 已有评分、评分键和20任务周期事实保持原版本，不回算、不覆盖。
- 当前周期已登记的 v1 任务继续保留，后续 v2 任务加入同一20任务周期；回顾时分别聚合两个版本的效率维度。
- 同一任务只能产生一个正式评分版本和一个回顾周期计数；v2 生效后不再为新任务生成 v1 正式分或 v2 candidate shadow 双计数。
- 历史 `autopilot-task-score/v2-candidate` shadow 仅作为批准前回放证据，不迁移到正式 ledger。
- 低效率分仍只用于周期观测，不改变 DONE、测试、权限、安全、数据一致性或零悬空硬门禁。

## 放量边界

- 本决策只批准评分版本，不解除 `.codex-autopilot/pause.flag`，也不启动任何 Ready Issue。
- 评分控制面指纹已经变化；N>1 或无界运行前，仍必须由用户明确启动并成功收口一次 `启动迭代-1` 金丝雀。
