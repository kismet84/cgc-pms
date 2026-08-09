# 2026-08-09 本地路径历史产物清理记录

## 范围与授权

- 仅处理 CGC-PMS 本地 clone、worktree、备份与历史快照；不含 commit、push、生产或数据库写入。
- `.archive` 只在用户授权后按深度 4 只读扫描；嵌套保护目录不进入，junction 只记录或解除链接本体，不跟随目标。

## 已完成

1. 4 份唯一 MySQL gzip 备份已归位到 `D:\backups\cgc-pms\mysql` 或 `data-maintenance\cost-subject-removal`；迁移前后 SHA-256 一致且 gzip 可完整读取。空旧根 `D:\backups\mysql`、`D:\backups\cgc-pms\cost-subject-removal` 已删除。
2. 两个历史工作区均已迁入 `D:\projects-test\_worktrees\cgc-pms\` 规范路径并保持 clean：
   - `mainline-82-83-remediation`：分支 `codex/mainline-82-83-remediation`，HEAD `c9e999b4887d1e61bd4f3ac53e45d15c40c47da9`；已 commit、push，CI 成功。
   - `recovery-side-ci-tier-20260809`：分支 `codex/recovery-side-ci-tier-20260809`，HEAD `fa7a549fc1d891ec592bb33b74fd4e564f0e8ba8`；已 commit、push，CI 成功。
   两分支均未创建 PR、未合并，不能据此声明 Git 交付完成；对应恢复包继续保留在 `D:\backups\cgc-pms\quarantine\`。
3. `.archive\cgc-pms-mainline34`、`.archive\cgc-pms-plan-performance-p0`、`.archive\cgc-pms-subcontract-p0` 三个失败快照已删除；仓库根 `.archive` 已删除。
4. `.archive\cgc-pms-worktree-backups` 的 6 个 patch 已按 3 组唯一 SHA 保留代表文件和来源 manifest 至 `D:\backups\cgc-pms\quarantine\archive-worktree-backups-20260809-100018`，旧根已删除。
5. `.archive\cgc-pms-procurement-p0` 仅含一个目标已不存在的 junction；已只解除 junction 本体并删除空父目录。
6. `ponytail` 已整体迁入标准隔离目录 `D:\backups\cgc-pms\quarantine\legacy-ponytail-20260809`；其中保护 payload 未扫描、未总结、未改写。
7. 备份目录中的重复 run 已清理；唯一恢复包及其校验材料保留。
8. 根级 `output\` 的 42 个文件、4,290,684 字节已整体迁入 `local-evidence\output\`；迁移前后树摘要均为 `0864d97c93903ca1aa6049d182b6399f20bd04a0ba3bf6defdad753ca17a0057`，正式 M22/M28 证据引用已改到新路径。根级 `test-results\` 的 2 个截图、97,271 字节也已迁入 `local-evidence\test-results\`，payload 树摘要为 `686c532f1b56ca1e6260805f1efde074b5603919dacdaa3b1a3eec910e4c2fe1`。
9. 根级 `design-qa.md` 已按迁移前 SHA-256 `12D7FBE2402227F072E11C940AFE13561DAB177B8974CFCCBE468A066EED626F` 迁入 `docs\archive\v1.5\quality\design-qa.md`；加入归档路径失效说明后的当前文件 SHA-256 为 `A0FC5492241695B9EE4D8147692E13C77C09B0337DD24DDF02AD00E2C1B08AC8`，仍承担稳定锚点的引用已同步修正。
10. `x-to-markdown\` 与 `.baoyu-skills\` 已分别整体迁入 `D:\backups\cgc-pms\quarantine\legacy-x-to-markdown-20260809`、`legacy-baoyu-skills-20260809`；manifest 位于 payload 外侧。`.baoyu-skills\.env` 仅随目录整体迁移，未读取或输出值。空 `.tmp\` 已迁入 `legacy-empty-tmp-20260809`，无文件内容需要恢复。
11. `mobile\README.md` 仅为空模块占位，已从版本树移除并修正其唯一计划引用。
12. `frontend-admin\`、`memory\`、`mobile\` 已按用户精确授权同盘整体迁入 `D:\backups\cgc-pms\quarantine\legacy-frontend-admin-20260809`、`legacy-repo-memory-20260809`、`legacy-mobile-placeholder-20260809`。三个 payload 均作为不透明目录项移动；保护内容未扫描、未哈希、未修改，外置 manifest 记录原路径与反向恢复边界。

## 正式承接项

| 优先级 | 对象 | 当前证据与延期原因 | 验收标准 |
| --- | --- | --- | --- |
| P0 | `codex/mainline-82-83-remediation` | worktree clean，分支已 commit、push 且 CI 成功；尚无 PR、未合并 | 完成独立 PR CI 与受保护合并后，验证 master 吸收对应 SHA，再清理 worktree 和源分支 |
| P0 | `codex/recovery-side-ci-tier-20260809` | worktree clean，分支已 commit、push 且 CI 成功；尚无 PR、未合并 | 完成独立 PR CI 与受保护合并后，验证 master 吸收对应 SHA，再清理 worktree 和源分支 |
| P0 | 主仓路径与根目录治理改动 | 初始 13 项路径治理已扩展为当前 31 条任务自有 `git status --short` 记录；另有 3 条并发 `long-task-gate` 改动不属本任务，必须排除。本记录不授权 commit、push、PR 或合并 | 按任务归属完成验证与受保护 Git 交付；不得批量带入并发改动 |

## 恢复与删除门槛

- 恢复包至少包含 HEAD、branch、状态、二进制 patch、未跟踪文件及 SHA manifest；补丁必须在相同 HEAD 的干净临时 worktree 通过回放检查。
- worktree 仅在无未提交成果、无未推送提交、无活动占用且分支已合并或明确放弃后，通过 `git worktree remove` 清理。
- 备份与快照删除前必须存在经哈希验证的替代副本；PowerShell/.NET 删除不经过回收站。

## 后续项统计

- 本轮修复并复验：历史路径、失败快照、隔离、重复备份及根级安全迁移已按上述 12 项处理。
- 超出当前 Git 授权并正式承接：3 项（两个未合并分支、主仓 31 条任务自有 dirty 记录）。
- 并发 `long-task-gate` 改动由其原任务承接，本任务不暂存、不修改、不验收。
- 证据不足关闭：0 项。
- 无载体遗留项：0。
