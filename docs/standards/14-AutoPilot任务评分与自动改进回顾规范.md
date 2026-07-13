# AutoPilot 任务评分与自动改进回顾规范

## 1. 适用范围

本规范只约束本地 AutoPilot 连续迭代。评分是通过任务的观测数据，不是质量门禁，也不能替代测试、权限、安全、数据一致性、范围和问题零悬空裁决。

## 2. 评分版本批准门

- 配置必须分开保存 `candidateVersion` 与 `activeVersion`。
- 用户未明确批准版本、五维权重和生效时间时，必须保持 `enabled=false`、`activeVersion=null`、`approvalStatus=NEEDS_CONFIRMATION`。
- candidate/disabled 状态只允许 schema 校验、样本回放和测试；不得写正式评分、增加回顾计数或触发20任务暂停。
- 已产生的评分绑定原 `scoringVersion`，新版本不得重算或覆盖历史记录。

## 3. 有效任务与两阶段收口

只有同时满足以下条件的实施型 Ready Issue 才能计数：全部硬门禁通过、正式材料归档、实施提交成功、确定性评分通过 schema、评分收口提交成功并合入登记。

1. `implementationCommit` 冻结实现和正式证据。
2. 评分器只从该提交对应的正式证据计算五个维度，并以 `issueId + implementationCommit + scoringVersion` 生成幂等键。
3. `closeoutCommit` 写入评分、Done/backlog 收口事实；它不得与实施提交混称。
4. closeout ledger 登记成功后，runner 才把 Issue ID 和评分键加入回顾周期。

缺失正式证据、硬门禁失败或两阶段收口未完成时拒绝评分和计数。单任务低分不触发补修、回滚或重新验收。

## 4. 跨批次回顾周期

- 回顾计数独立于单次 `iterationLimit`，按 Issue ID 和评分键双重去重。
- 无界模式完成第20个有效任务后进入 `RETROSPECTIVE_REQUIRED`，不得派发第21个任务。
- 有界 `启动迭代-N` 在批次内达到20时只置待回顾；在没有更高优先级停止条件时完成当前 N，再回顾全部累计任务。
- 超过20的任务与本周期一起回顾，成功后全部清零，不结转。
- 回顾成功后仍保持暂停，必须等待用户审阅并重新启动。

## 5. 回顾与改进提案

回顾聚合总分、五维分布、首次通过率、周期耗时、重复根因、后续项净变化和存量问题变化。只有可复现的聚合规则可以产生提案；单个低分不得自动立项。

提案必须具有稳定唯一键、来源任务、价值、最小方案、非目标、依赖、回滚和验收标准，并保持 `approvalStatus=NEEDS_CONFIRMATION`。提案只去重写入 `docs/backlog/current-issues.json` 这一正式问题体系，不创建第二 backlog，也不自动修改代码、规则、权重或环境。

## 6. 可恢复收口

回顾按以下阶段单向推进：

`REPORT_COMMITTED → ISSUES_WRITTEN → GRAPH_REFRESHED → EPISODE_RECORDED`

- 报告写入 `docs/iterations/` 并先形成正式本地提交。
- 提案去重写入唯一问题事实源，并形成事实提交。
- 知识图谱 Git 游标必须追平事实提交。
- Episode 使用 `reviewCycleId + scoringVersion` 派生的稳定 ID，并以正式报告为 `sourceRef`。
- 任一步失败都保留 `RETROSPECTIVE_REQUIRED`、累计 Issue 和评分键；只允许从最后确认阶段幂等续跑。
- 前四阶段全部读回成功后才能清零 state；不得逆向删除已成功的正式事实。

## 7. 安全边界

不连接生产、不发布生产、不自动 push，不因效率评分裁剪必需验证。运行态索引不是正式事实源；原始日志、截图和一次性 run id 不进入长期规则或正式回顾。
