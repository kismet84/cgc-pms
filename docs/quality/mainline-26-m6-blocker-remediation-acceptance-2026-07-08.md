# 第26条主线 M6 阻塞项整改最终收口报告

报告日期：2026-07-08  
报告类型：M6.5 汇总复核与收口归档  
报告边界：本次仅读取 M6 计划书、M6.1-M6.4 验收报告与当前 Git 事实，形成最终裁决；未修改业务代码、配置、测试源码、Git 状态或运行环境。  
报告路径：`D:\projects-test\cgc-pms\docs\quality\mainline-26-m6-blocker-remediation-acceptance-2026-07-08.md`

## 1. 总体裁决

- 总体结论：**通过**
- 阻塞/非阻塞：**非阻塞**
- 主线 26-M6 当前状态：**已收口，可作为第26条主线 M6 阻塞项整改的正式关闭依据**

裁决依据：

1. B1、B3 已由 `docs/quality/mainline-26-m6-1-backend-security-verification-2026-07-08.md` 判定通过，且定向测试与代码证据闭环。
2. B4-B7 已由 `docs/quality/mainline-26-m6-3-4-browser-verification-2026-07-08.md` 判定通过。
3. B8 已由 `docs/quality/mainline-26-m6-4-b8-final-browser-verification-2026-07-08.md` 判定通过；库存有效提交、专用领料申请、最终审批和回补证据均已补齐。
4. B2 早期复核报告 `docs/quality/mainline-26-m6-2-cleanliness-verification-2026-07-08.md` 当时基于“提交前工作区”判定不通过；当前 `HEAD=8bd6c9d6eb010828660fe8ee686133f11bdd8e4f`，提交 `8bd6c9d6e chore: close mainline 26 m6 blockers` 已把相关运行态产物移出版本树，故 B2 最终应改判为通过。

## 2. 证据来源

文档证据：

1. `docs/plans/第26条主线-M6-阻塞项整改任务计划书-2026-07-08.md`
2. `docs/quality/mainline-26-m6-1-backend-security-verification-2026-07-08.md`
3. `docs/quality/mainline-26-m6-2-cleanliness-verification-2026-07-08.md`
4. `docs/quality/mainline-26-m6-3-4-browser-verification-2026-07-08.md`
5. `docs/quality/mainline-26-m6-4-b8-final-browser-verification-2026-07-08.md`

Git/版本树证据：

1. `git rev-parse HEAD`
2. `git log --oneline -n 5`
3. `git show --stat --oneline 8bd6c9d6e`
4. `git status --short`
5. `git ls-tree -r --name-only HEAD -- output/playwright frontend-admin/coverage-result.json .gitignore`
6. `git check-ignore -v output/playwright/m22-5-acceptance/admin-state.json frontend-admin/coverage-result.json`

## 3. B1-B8 对照表

| 编号 | 事项 | 最终状态 | 是否关闭 | 主要依据 |
| --- | --- | --- | --- | --- |
| B1 | 成本摘要接口项目级访问控制 | 通过 | 已关闭 | M6.1 报告确认 `CostSummaryService` 三条入口统一接入 `ProjectAccessChecker`，定向权限测试通过 |
| B2 | 移除/治理版本库跟踪的测试与浏览器运行态产物 | 通过 | 已关闭 | 早期 M6.2 报告为提交前阻塞；当前 `HEAD=8bd6c9d6e`，`git ls-tree` 不再列出 `output/playwright/**`、`admin-state.json`、`frontend-admin/coverage-result.json`，`.gitignore` 仍命中对应忽略规则 |
| B3 | Token blacklist / Redis 生产降级语义 | 通过 | 已关闭 | M6.1 报告确认 `prod` 下 blacklist 缺失拒绝放行，Redis 检查异常 fail-close，定向测试通过 |
| B4 | dashboard 导出 | 通过 | 已关闭 | M6.3/M6.4 浏览器报告捕获 `Page.downloadWillBegin`，文件名 `成本列表.csv` |
| B5 | alert 导出 | 通过 | 已关闭 | M6.3/M6.4 浏览器报告捕获 `Page.downloadWillBegin`，文件名 `alerts-2026-07-08.csv` |
| B6 | inventory transaction 空提交/重复点击反馈 | 通过 | 已关闭 | 连续空点可见提示“请选择仓库”，未发起错误提交 API |
| B7 | cost ledger `projectList` Vue warn | 通过 | 已关闭 | 页面加载后控制台 warn/error 为 0，未再出现 `projectList` 空值告警 |
| B8 | 上传、最终审批、有效业务提交覆盖不足 | 通过 | 已关闭 | 最终 B8 复核报告确认库存有效提交恢复 200，专用领料实例创建/提交/最终审批完成，库存净额已回补 |

## 4. B2 改判说明

`docs/quality/mainline-26-m6-2-cleanliness-verification-2026-07-08.md` 的“不通过 / 阻塞”结论成立前提，是当时 `HEAD` 仍跟踪运行态产物、工作区只处于 staged deletion/待提交状态。

当前事实已经变化：

1. `git rev-parse HEAD` 返回 `8bd6c9d6eb010828660fe8ee686133f11bdd8e4f`。
2. `git log --oneline -n 5` 显示当前头提交就是 `8bd6c9d6e chore: close mainline 26 m6 blockers`。
3. `git show --stat --oneline 8bd6c9d6e` 明确包含：
   - `frontend-admin/coverage-result.json` 删除
   - `output/playwright/**` 大量运行态截图与 `admin-state.json` 删除
   - `.gitignore` 增补忽略规则
4. `git ls-tree -r --name-only HEAD -- output/playwright frontend-admin/coverage-result.json .gitignore` 当前仅返回 `.gitignore`，说明上述运行态产物已不在版本树。
5. `git check-ignore -v output/playwright/m22-5-acceptance/admin-state.json frontend-admin/coverage-result.json` 仍命中 `.gitignore:61:output/` 与 `.gitignore:65:coverage-result.json`。

因此，B2 最终裁决应以最新 HEAD 事实为准，改判为：**通过 / 已关闭**。

## 5. 验证命令摘要

```powershell
git rev-parse HEAD
git log --oneline -n 5
git show --stat --oneline 8bd6c9d6e
git status --short
git ls-tree -r --name-only HEAD -- output/playwright frontend-admin/coverage-result.json .gitignore
git check-ignore -v output/playwright/m22-5-acceptance/admin-state.json frontend-admin/coverage-result.json
```

摘要结论：

1. 当前 `HEAD` 已落在 M6 收口提交 `8bd6c9d6e`。
2. 工作区当前无需要纳入本次裁决的额外 Git 状态异常。
3. 目标运行态产物已退出版本树，忽略规则仍然生效。

## 6. 剩余非阻塞风险

1. 后端全量测试基线仍为红：M6.1 报告记录 `.\mvnw.cmd -q test "-Djasypt.encryptor.password=dev-jasypt-key"` 结果为 `Tests run: 250, Failures: 1, Errors: 142, Skipped: 1`。该问题属于仓库既有全量红基线，不阻塞本次 M6 定向收口，但不能被误写成“全量已绿”。
2. B8 虽已通过，但专用审批实例与已审批业务记录无法物理删除，只能通过实例 ID、业务 ID、明细前缀、审批意见和库存流水 `sourceType` 做隔离留痕；该残留为隔离型非阻塞风险。
3. B1/B3 仍有后续加固空间：如 `refresh/history` 的更多正向权限断言、刷新/登出链路 blacklist 语义一致化；这些不影响本次阻塞关闭结论。

## 7. 未纳入范围

1. 本轮仅裁决 B1-B8，不把第26条主线审计中的 P2/P3 问题并入本次通过判断。
2. 本轮不重新执行浏览器测试、后端测试、服务启动或 Git 提交；统一复用既有验收报告和当前 Git 事实。
3. 本轮不对业务代码、配置、测试源码或运行环境做任何新增修改。

## 8. 后续建议

1. 将后端全量测试红基线单列到后续治理主线，避免后续阶段继续混用“定向通过”和“全量未绿”两个口径。
2. 对 B8 专用审批实例保留一份可检索台账，至少记录实例 ID、业务 ID、隔离前缀和回补流水，便于后续审计追踪。
3. B1/B3 若继续加固，优先补最小测试闭环，不扩展为权限体系或认证体系重构。

## 9. 最终结论

- B1-B8：**全部关闭**
- M6 最终结论：**通过 / 非阻塞**
- 主线 26-M6 当前状态：**已完成收口归档**

## 10. 禁止事项遵守情况

1. 未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`。
2. 未修改业务代码、配置、测试源码、Git 状态或运行环境。
3. 未运行新测试、未启动浏览器/服务、未提交 Git、未推送、未清理文件。
4. 本次唯一写入文件为本收口报告。
