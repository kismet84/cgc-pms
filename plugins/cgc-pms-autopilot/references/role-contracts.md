# Role Contracts

## A 需求/架构分析

- 输入：`ready` / `blocked` / `current-focus`、知识图谱有界候选及其 `sourceRefs`、计划书、既有规则
- 输出：图谱健康与 HEAD 游标结论、问题分类、候选当前事实核实、最小实现边界、依赖判断、建议验证命令、是否需要重新分档、查询目的、命中摘要、交叉核验

## B 前端/UI 实现

- 输入：允许文件、禁止文件、验收命令、repair-request
- 输出：修改文件、修改点、局部验证、剩余风险

## C 后端/API 实现

- 输入：允许文件、禁止文件、验收命令、repair-request
- 输出：修改文件、修改点、局部验证、剩余风险

## D 测试/回归

- 输入：目标测试、实现结果、既定验收命令
- 输出：通过/不通过、失败分类、`category/subcategory/confidence/evidence/suggestedNextAction/retryPolicy`、阻塞/非阻塞、首次正式验收/补修次数、repair-request 或通过证据

## E 代码审查/安全审查

- 输入：当前 diff、风险点、D 的验证结论
- 输出：阻塞问题、非阻塞建议、是否纳入本轮、是否需要升档或换角色

## F 文档/上线清单

- 输入：D/E 结论、正式交付物列表、git 状态、A 的图谱路由证据
- 输出：quality/done/blocked/iteration 片段、图谱检索证据或不适用原因、后续项净变化、local commit 建议、剩余风险；评分已批准并激活时还要区分 `implementationCommit` / `closeoutCommit`，输出带版本评分证据和回顾周期登记结果

## 统一边界

1. 实现角色不得自评最终通过。
2. D/E/F 输出必须短、稳定、可复核，不复制真实长日志。
3. 若输入边界不清，先补 repair-request 或任务说明，不直接扩 scope。
4. loop runner 只负责串联 phase 和建议动作，不替代主线程最终裁决。
5. 知识图谱只负责发现、筛选和关联导航；候选进入 Ready 前必须核实当前分支事实与唯一正式载体，图谱异常或过期时 fail-close。
6. Ready allow/forbid 完全覆盖矛盾属于 `ready_issue_config`，必须在 executor/worktree 前拒绝；合法窄禁止 carve-out 不得误伤。
7. 评分只对通过全部硬门禁的实施型 Ready 生效；低分不改变任务裁决。未批准 scoring candidate 不计数，已激活版本按跨批次周期累计。
8. 回顾只提出并正式承接 `NEEDS_CONFIRMATION` 改进方案；报告、问题事实、图谱游标和稳定 Episode 未完成可恢复收口前不得清零周期或恢复派发。
