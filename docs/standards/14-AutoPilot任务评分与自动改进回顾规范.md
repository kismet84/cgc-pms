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

## 8. v2 任务执行效率正式版本

- 用户已批准 `autopilot-task-score/v2` 正式启用，五维权重为35/25/20/10/10，其中 `taskExecutionEfficiency=10` 替代 v1 的 `cycleEfficiency=10`；其余四维语义不变。
- 生效边界为批准配置提交后的下一项新实施型 Ready Issue。v1 历史评分不回算、不覆盖；每项任务只允许一个正式评分版本和一个回顾周期计数。
- 当前20任务周期中批准前已经登记的 v1 任务继续保留；回顾报告分别聚合 v1 `cycleEfficiency` 与 v2 `taskExecutionEfficiency`，不得把缺失的异版本维度按0分混算。
- 10分要求 implementation 仅派发一次，validation/Reviewer/closeout 派发次数符合任务路由，`runResumeCount=0`，且无阶段回退、人工恢复、Reviewer/环境工具阻塞或被门禁拦截的重复派发，端到端证据完整。
- implementation 仍仅派发一次、无阶段回退和人工恢复，但恰有一次已分类工具/环境重试或一次被门禁阻断的重复派发时为5分；重复 implementation、阶段回退、人工恢复、多次工具重试或证据不完整时为0分。
- `wallClockSeconds` 与分阶段耗时进入20任务趋势和异常分析，不按固定分钟数直接扣分。效率低分不改变业务正确性、硬门禁或 DONE 裁决，`tool_config` 也不得写成业务质量失败。
- 执行效率聚合必须区分业务实施耗时与控制面/工具恢复耗时；后者记录 `tool_config`、环境重试、恢复次数和停滞区间，不得伪装为业务实现复杂度。
- stall 进度只采信工作区内容、durable checkpoint、result 或正式 evidence 的可复现变化；PID 存活、心跳刷新、CPU 微小变化和无产物的 MCP 活动不算语义进度。
- v2 正式分必须绑定 `implementationCommit`，由独立 `closeoutCommit` 写入正式报告；closeout ledger 登记后才能加入 `reviewCycleScoreKeys`。历史 candidate shadow 继续只作批准前回放证据，不迁移到正式 ledger，也不参与20任务累计。

## 9. 跨 run 评分证据

- 活动 Issue 的阶段 checkpoint 记录 implementation、validation、review、repair、closeout 派发次数，以及 `runResumeCount`、阶段回退、人工恢复、工具阻塞、`environmentRetryCount`、重复派发拦截和阶段耗时。
- checkpoint 属于可丢弃运行态，不替代正式报告；正式报告只保存聚合指标和失败分类，不保存 run id、PID、原始日志或临时路径。
- 同一 `issueId + readyContentHash + baseCommit` 的 implementation dispatch 只能为1。恢复必须复用原 implementation commit 和评分幂等键，禁止以新 run 生成第二份正式评分或回顾计数。
