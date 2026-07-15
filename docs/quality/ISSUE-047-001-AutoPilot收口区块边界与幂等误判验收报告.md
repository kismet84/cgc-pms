# ISSUE-047-001 AutoPilot 收口区块边界与幂等误判验收报告

结论：通过；阻塞：无。

## 范围与结果

- `Complete-AutopilotIssueCloseout` 的已完成判断改为读取目标 Issue 标题至下一 Issue 标题之间的有界区块，只接受目标区块内的精确 `状态：Done`。
- 临时 Git 仓库夹具在目标 Ready 后增加另一条已完成 Issue；旧实现会提前返回 idempotent，修复后继续触发原有存量问题关闭门禁并完成双提交。
- 未修改状态机、评分、报告投影、账本、合并协议、执行宿主、业务代码或业务数据。

## 验收证据

- `test-closeout.ps1` 通过，覆盖目标 Ready 后存在 Done、存量问题未关闭拒绝、实现/收口双提交、真正已完成目标幂等、评分绑定和 fast-forward 幂等。
- `test-control-plane.ps1` 完整控制面自测通过；Ready lint 0错误0警告，`git diff --check` 通过。
- 独立风险复核：新增函数只读 Ready 文本，标题使用 `Regex.Escape`，区块以下一 `### ISSUE-` 或文件末尾为边界；分支、基线、主工作区脏状态、存量关闭、评分与 Git 门禁代码未变。结论 `PASS`，findings=无。

## 治理收口

- 唯一台账移除 `AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY`，剩余连续迭代不再受该根因阻塞。
- 新增后续项：0
- 关闭后续项：1
- 后续项净变化：-1
- 最小回滚：回退有界 Done 判定和对应夹具；不修改历史 ledger、既有提交或业务数据。

剩余风险：区块语法继续依赖现行 `### ISSUE-` 标题约定，该约定同时由 Ready lint 与 `Set-AutopilotReadyDone` 使用；未发现需要另立项的证据。
