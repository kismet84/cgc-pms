# ISSUE-040-018：AutoPilot 存量问题优先补货机制验收报告

## 结论

- 验收结论：通过。
- 阻塞性：本次控制面变更无阻塞；仓库现存 `pause.flag` 正确阻止下一任务派发，属于用户控制状态，不是实现缺陷。
- 上线边界：仅本地 AutoPilot 治理机制可用；没有启动业务迭代、连接生产、发布、提交或 push。

## 目标与范围

目标是让 AutoPilot 在已有合格 Ready 之后，第一优先从 `docs/backlog/current-issues.json` 的存量问题拆任务，并保证拆分、去重、完成回写形成闭环。范围仅包括规则、refill、连续 runner、closeout、测试及正式治理文档。

不实施任何具体存量业务问题，不处理阻塞/发布门禁，不把冻结或需要确认项自动转为 Ready，不从长期增强计划直接生成任务。

## 实现结果

1. 补货顺序统一为：已有 Ready → 可执行存量 → 当前 focus 可解除阻塞 → 已过决策门 Ad-hoc → 产品情报刷新。
2. 存量选择器只接纳 `OPEN` / `OBSERVATION` 且分类为 `STILL_APPLICABLE`、`NON_BLOCKING_OBSERVATION` 或 `OPERATIONAL_RISK` 的非阻塞项，并要求验收标准与来源证据完整。
3. 发布门禁、冻结、需要确认、聚合父项和证据不完整项被排除；排序为 P0→P2、Open→Observation、叶子优先。
4. Ready 使用 `[stock:问题键]` 在 Ready、Done、Blocked 间稳定去重；closeout 在原问题仍可继续拆分时拒绝 Done，要求先移出台账或正式重分类。
5. 删除连续 runner 读取长期增强计划并直接生成宽泛 Ready 草稿的旧旁路；Planner 不可用时 fail-close。

## 验收证据

- PowerShell AST：6个相关脚本解析错误为0。
- `test-refill.ps1`：通过，覆盖存量优先、父项/发布门禁/需要确认过滤、标记保留、Ad-hoc 回退与长期计划禁入。
- `test-continuous-runner.ps1`：通过，覆盖真实候选来源展示、Planner 不可用 fail-close 和不生成宽泛 Ready。
- `test-closeout.ps1`：通过，证明原存量问题仍可拆时拒绝收口，正式重分类后允许收口且重试幂等。
- `test-control-plane.ps1`：通过。
- `plugins/cgc-pms-autopilot/scripts/test-autopilot-loop-runner.ps1`：通过。
- 真实台账只读核对：单批上限5条时返回5条，非法状态/分类0条，聚合父项0条；该批依次为 `A-01-MENU-CREATE`、`A-01-MENU-DELETE`、`A-01-MENU-DETAIL`、`A-01-MENU-LIST`、`A-01-MENU-UPDATE`。
- 真实 refill 决策：命中现存 `pause.flag` 并返回 `PAUSE`，没有派发下一任务；直接调用只读选择器才展示上述候选。

## 风险审查

- 权限、安全、金额、租户和数据一致性：本次不改业务代码或数据；后续具体存量任务仍须按各自风险单独验收。
- 误派风险：通过状态/分类/证据/父子过滤、稳定去重、Planner fail-close 与 pause/stop 优先级控制。
- 台账漂移风险：通过 closeout 回写门禁控制；如果原问题仍处于可拆状态，自动收口失败。
- 回滚：回退本 Issue 对 AutoPilot 脚本、规则、测试与治理文档的差异；无数据库或运行态回滚动作。

## 非阻塞问题零悬空统计

- 本轮新增后续项：0。
- 本轮关闭后续项：0。
- 后续项净变化：0。
- 本轮发现的两项根因——存量台账未接入补货、长期计划存在直接拆 Ready 旁路——均已修复并复验，没有仅停留在报告备注中的问题。

## 剩余风险

无未承接实现风险。`pause.flag` 仍存在，因此下一任务派发保持停止；这是预期控制状态，解除前不得开始自动迭代。
