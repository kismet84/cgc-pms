# AutoPilot 无人值守资格报告

日期：2026-07-11

## 裁决

结论：**通过（本地低/中风险、串行、无 push）**。

阻塞：无。

适用边界：`maxParallel=1`、`autoPush=false`、本地 dev/test/demo；不代表生产发布许可，不连接生产数据库，不开放高风险任务免审，也不把并发提升到 2。

## 20-Issue 资格窗口

| 指标 | 硬门槛 | 实测 | 结论 |
|---|---:|---:|---|
| 样本数 | 20 | 20 | 通过 |
| Done | 20/20 | 20/20 | 通过 |
| 首轮成功率 | ≥ 80% | 100% | 通过 |
| 人工介入 | 0 | 0 | 通过 |
| 范围越界 | 0 | 0 | 通过 |
| 本地提交绑定 | 100% | 20/20 | 通过 |
| 验证证据绑定 | 100% | 20/20，逐项校验 issueId/baseCommit/diffHash/pass | 通过 |
| 必需 Reviewer | 100% | 本窗口均为低风险且不要求 Reviewer；高风险门另由路由测试覆盖 | 通过 |
| 迭代上限 | 20 后停止 | `STOP_ITERATION_LIMIT_REACHED` | 通过 |

最终演练命令：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-unattended-canary.ps1`。

最终耗时：312.6 秒。演练使用临时 Git 仓库，逐条完成 Ready → 隔离 worktree → 执行 → 验证 → 证据绑定 → Done → 本地提交 → fast-forward merge，不修改项目业务数据。

## 故障与恢复资格

- 确定性首次验证失败：新建 `repair-1/context.json`，一次新鲜缩小上下文重试后通过并收口。
- 执行器停滞：3/8 秒加速测试等价验证生产 5/10 分钟策略；检查后终止，两次停滞后安全 Blocked。
- 不确定已提交 worktree：不直接合并，隔离并重新执行 Issue。
- baseBranch 错误、主线执行中漂移、最终范围越界、过期证据、Reviewer identity/diff hash 不匹配：全部 fail-close。
- Blocked 结果不消耗“完成 N 个实施型 Issue”额度。

## 对比基线

历史基线最近 10 条结果为 Done 7、Blocked 2、Noop 1；可识别真实运行事件的 P50 为 453.7 秒、P95 为 728.9 秒，且 0/9 Done/Blocked 结果具备完整 commit 绑定。本次资格窗口达到 20/20 Done、20/20 commit/evidence 绑定，并将单窗口演练耗时压到 312.6 秒。

该对比用于证明控制面完整性和自动收口效率提升；临时 canary 与真实业务 Issue 难度不同，不把 312.6 秒解释为真实业务工期承诺。

## 剩余风险

- 尚无 20 个真实业务 Issue 的滚动窗口，因此保持串行，不启用并发 2。
- runtime refresh 会调用本地 `python scripts/rebuild.py` 并等待 180 秒；仅适用于本地 dev/test/demo。
- PowerShell/Windows 行尾警告不影响退出码，但后续可单独治理 `.gitattributes`，本主线不扩 scope。

