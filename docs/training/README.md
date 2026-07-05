# 培训材料入口

本目录是试点培训的最小材料入口，不建设长期课程体系。

当前定位：
- 第 13 条主线当前上线收口已通过，本目录对上线收口非阻塞。
- 本目录对试点交付与培训落地是阻塞项。

## 范围

本轮只覆盖：
- 角色化培训脚本
- FAQ 记录字段
- 培训问题回流规则

本轮不覆盖：
- PPT
- 录屏
- 考试题库
- 知识库平台
- 长期培训运营机制

## 当前入口

- [角色化培训脚本](role-based-training-script.md)
- [FAQ 与问题回流](faq-feedback-loop.md)
- [试点账号与审批模板确认清单](pilot-account-and-approval-checklist.md)
- [外部输入资料清单](external-input-checklist.md)
- [试点现场确认记录单模板](pilot-confirmation-record.md)
- [试点现场确认记录单（预置执行包）](pilot-confirmation-record-seeded.md)
- [待确认事项签收单模板](pending-items-signoff.md)
- [待确认事项签收单（预置执行包）](pending-items-signoff-seeded.md)
- [试点外部确认执行说明](pilot-confirmation-runbook.md)
- [试点现场执行任务卡](pilot-execution-task-card.md)
- [现场确认结果回填安排单](pilot-backfill-session-plan.md)
- [现场回填会会议通知正文模板](pilot-backfill-meeting-notice.md)
- [现场回填会后汇报模板](pilot-backfill-summary-template.md)
- [确认结果回写标准作业说明](confirmation-writeback-sop.md)
- [真实回填执行结果整理包](pilot-backfill-result-package.md)
- [试点真实回填外部执行交接单](pilot-backfill-handoff.md)
- [终端用户手册](../manuals/end-user-manual.md)
- [管理员手册](../manuals/admin-manual.md)

## 使用顺序

1. 先用手册确认当前有效边界。
2. 先核对 [试点账号与审批模板确认清单](pilot-account-and-approval-checklist.md)，确认哪些事项仍然只是待确认，不得当作既成事实。
3. 再使用 [试点现场确认记录单模板](pilot-confirmation-record.md) 逐项记录现场确认过程与证据。
4. 现场确认后，使用 [待确认事项签收单模板](pending-items-signoff.md) 形成“通过 / 不通过 / 保留待确认”结论。
5. 需要执行现场确认时，按 [试点外部确认执行说明](pilot-confirmation-runbook.md) 组织“确认清单 → 现场记录 → 签收”的最小动作顺序；需要更新正式文档时，按 [确认结果回写标准作业说明](confirmation-writeback-sop.md) 执行回写与复核。
6. 最后再按角色化培训脚本组织试点培训；培训现场问题统一记入 FAQ，并按回流规则分流。

补充说明：
- 模板文件用于后续建包或复用：`pilot-confirmation-record.md`、`pending-items-signoff.md`。
- seeded 文件用于本轮直接执行：`pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md`。
- 当前文档准备已基本齐全；后续推进主要依赖外部资料补齐与现场真实执行。
- `external-input-checklist.md` 用于现场前收资料，`pilot-execution-task-card.md` 用于现场按顺序执行最小闭环。
- 当前不仅可组织现场回填会，也可直接复用 `pilot-backfill-meeting-notice.md` 发送通知，并在会后按 `pilot-backfill-summary-template.md` 输出汇报。
- 当前已具备回填执行条件；下一步按 `pilot-backfill-session-plan.md` 组织一次真实回填，即可把 seeded 文件推进到现场记录与签收状态。
- 当前已进入结果整理包阶段；`pilot-backfill-result-package.md` 仅用于承接真实回填结果归档，真实结果仍待现场执行后回填。
- 当前仓库内准备工作已收口；剩余问题可按 `pilot-backfill-handoff.md` 作为交接单移交外部执行。
