# 第41-1条主线：AutoPilot 任务评分与 20 任务自动改进回顾补充任务计划书

**Goal:** 在第41条主线已经完成的知识图谱优先补货、候选核实和 Ready 契约前置门禁之上，增加可审计的逐任务评分与跨批次累计回顾机制；每个正式完成、验收通过、已归档并完成本地提交的实施型 Ready Issue 都形成带版本的评分记录，累计达到 20 个后按有界/无界迭代语义强制暂停并对本周期全部任务统一回顾，自动发现系统性问题并提出改进方案，但不自动实施改进。
**Architecture:** 复用现有 `autopilot-run-continuous.ps1`、`autopilot-state.ps1`、closeout ledger、iteration 报告、知识图谱问题实体和 Episode 能力；新增独立于单次 `iterationLimit` 的回顾周期状态、确定性评分器和回顾生成器，不另建改进清单、任务数据库或第二事实源。单任务采用“实施提交 → 绑定实施提交的评分 → 评分收口提交”的两阶段 Git 协议，避免评分记录引用其所在提交自身；跨正式报告、问题事实源、Git、Neo4j、Episode 和本地 state 的回顾收口采用可恢复、幂等的阶段日志，不宣称跨系统原子事务。任务评分只用于观测与周期分析，低分本身不改变已经通过的任务裁决；现有测试、权限、安全、范围和问题零悬空硬门禁继续优先。改进发现先写入正式事实载体并刷新知识图谱，按唯一键去重；所有改进方案和新的 `scoringVersion` 都必须经用户批准后才能实施或生效。

**Depends On:** 第41条主线已经实施并收口。
**Tech Stack:** PowerShell 5.1/7、Node.js 22、JSON Schema、Neo4j、Git、现有 AutoPilot runner 与知识图谱采集器。

**实施状态（2026-07-13）：** 基础设施、schema、状态迁移、两阶段收口、20任务门禁、回顾聚合、稳定 Episode CLI 与回归测试已落地；用户已明确批准 `autopilot-task-score/v1` 及 35/25/20/10/10 权重，配置正式激活，并从批准配置提交后启动的下一项新实施型 Ready 起计数。历史样本缺少结构化首次验收、周期效率及两阶段提交字段，因此不回算旧任务。实际规范文件使用未占用编号 `docs/standards/14-AutoPilot任务评分与自动改进回顾规范.md`，避免与既有第12号规范冲突。

## 1. 决策口径

本补充主线固定以下用户决策，实施时不得重新解释：

1. 自动改进机制只负责发现问题和提出改进方案，不自动修改脚本、规则、评分权重、业务代码或运行环境。
2. 所有有效实施型 Ready Issue 都计入跨批次回顾累计，不得通过连续执行多个 `启动迭代-N` 绕过回顾。
3. 只有同时满足以下条件的任务才计数：
   - 任务性质属于实施型 Ready Issue；
   - 正式完成且验收通过；
   - iteration/done/quality 等适用正式材料已经归档；
   - `implementationCommit` 与 `closeoutCommit` 均已成功产生，后者已按适用流程合入并登记；
   - 逐任务评分记录已经生成且通过 schema 校验。
4. dry-run、补货、Ready 拆分、health gate、runtime refresh、失败重试、阻塞核实、回顾本身和只有计划/报告的任务不计数。
5. 优化目标按以下顺序评价：交付正确性、问题零悬空、首次验收通过率、周期效率、存量问题下降速度。
6. 单个任务低分不触发补修、回滚、阻塞、重新验收或自动改进；只有既有硬门禁失败时，任务才不得判定为完成或进入累计。
7. 回顾发现项必须去重并写入正式事实载体，再刷新知识图谱；不创建独立的“自动改进清单”。
8. 具备明确证据、用户价值和可执行验收标准的改进方案，在用户批准前保持 `NEEDS_CONFIRMATION` 或等价待决状态；批准后才按优先级进入正常图谱补货。
9. 系统可以根据回顾数据提出新的评分权重或规则，但不得覆盖当前版本；用户批准后发布新的 `scoringVersion`，历史任务保留原版本与原始得分。
10. `autopilot-task-score/v1` 及其权重当前只是待批准候选；样本回放可以验证可计算性，但用户未明确批准前不得写入 `activeScoringVersion`、不得启用正式计数，也不得把计划修订或代码落地解释为权重批准。
11. 正式评分记录绑定 `implementationCommit`；包含该评分记录的后续提交为 `closeoutCommit`。两者不得混称为同一个 commit，任务只有在 `closeoutCommit` 成功产生、按适用流程合入基线并登记后才可计数。
12. “原子写入”只描述单个 state 文件的替换语义；报告、问题事实源、Git、图谱和 Episode 之间使用可恢复阶段协议。任一阶段失败均保留 `RETROSPECTIVE_REQUIRED` 和累计任务，不得回滚已成功的外部写入，也不得提前清零。

## 2. 回顾阈值与批次语义

### 2.1 无明确迭代上限

执行 `启动迭代` 时：

- 从上一次成功回顾后累计有效任务。
- 第 20 个有效任务完成 `implementationCommit`、评分、`closeoutCommit` 和归档登记后，立即设置 `RETROSPECTIVE_REQUIRED`。
- 不再选择第 21 个任务；在任务边界进入强制暂停并生成本周期回顾。
- 回顾覆盖本周期累计的全部 20 个任务。

### 2.2 有明确迭代上限

执行 `启动迭代-N` 时：

- 本批次完成的有效任务继续加入同一个跨批次回顾周期。
- 若本批次中途达到 20 个，设置 `retrospectiveDue=true`，但不截断当前 N。
- 在未命中更高优先级 stop/pause、安全门禁、无候选或不可恢复阻塞的前提下，先完成本次指定的 N，再强制暂停并回顾。
- 回顾覆盖上一次回顾后到本批次结束的全部累计任务；超过 20 的部分一起复盘，不结转。
- 例如开始时累计 18 个，执行 `启动迭代-3` 后统一回顾 21 个，回顾成功后计数归零。
- 即使一次有界批次跨过多个 20 任务区间，也只在该批次结束后执行一次整体回顾，不拆成多个窗口。

### 2.3 异常与恢复

- 如果达到阈值后当前有界批次因 stop/pause、Ready 耗尽或真实阻塞提前结束，仍在安全收口后对已累计的全部任务执行回顾，不为凑满 N 制造任务。
- `retrospectiveDue=true` 且上一次回顾尚未成功时，禁止启动任何新迭代批次。
- 回顾报告提交、问题写回、知识图谱刷新、Episode 写入或状态写入任一步骤失败时，保持暂停和待回顾状态，不清零计数；重试从最后一个已确认阶段继续。
- 只有回顾正式报告已经本地提交、发现项完成去重处置并形成 `retrospectiveFactsCommit`、图谱 Git 游标追平该提交、稳定 Episode 已写入且最终 state 写入成功后，才能把周期计数清零。
- 清零不等于自动恢复迭代；必须等待用户审阅改进方案并明确恢复或重新启动。

## 3. 评分模型

### 3.1 评分与硬门禁分离

评分满分 100 分，只对已经通过全部硬门禁的有效任务生成。以下情况继续由既有规则直接阻止任务完成，不能靠分数抵消：

- 必需测试、构建、类型检查或契约验证失败；
- 允许/禁止路径矛盾或真实范围越界；
- 权限、安全、租户、金额或数据一致性证据缺失；
- 本轮发现项仍有悬空问题；
- 正式报告、backlog、图谱和 Git 状态不一致；
- 未完成两阶段本地提交与登记，或违反 stop/pause/no-push 边界。

低分任务不自动处理，也不撤销已通过结论。系统只在周期回顾时分析分布、趋势和重复根因。

### 3.2 `scoringVersion=autopilot-task-score/v1` 建议权重

| 维度 | 分值 | 客观取证口径 |
| --- | ---: | --- |
| 交付正确性 | 35 | 验收标准覆盖 20；目标回归与关键静态核对 10；范围和任务性质表述一致 5 |
| 问题零悬空 | 25 | 本轮发现项三分法处置完整 15；唯一承接载体、去重和净变化记录完整 10 |
| 首次验收通过率 | 20 | 首轮正式验收通过 20；一次补修后通过 10；两次及以上补修后通过 0 |
| 周期效率 | 10 | 无无效重复验证、无错误工具路由且周期证据完整 10；出现一次可归因返工 5；多次无效返工 0 |
| 存量问题下降速度 | 10 | 关闭或合并有效存量问题且无新增同根因问题 10；非存量任务但未增加存量问题 5；新增未消化同根因问题 0 |

实施前应把上述权重作为 v1 候选做一次样本回放；如样本证明某个维度无法稳定从正式证据计算，应先修订方案并请求用户批准，不得用模型主观印象补分。

当前批准状态：`APPROVED`。批准版本为 `autopilot-task-score/v1`，权重为 35/25/20/10/10，生效边界为批准配置提交后启动的下一项新实施型 Ready。历史任务不回算，新版本后续仍须重新走用户批准门。

### 3.3 单任务评分记录

每个计数任务至少保存：

```json
{
  "issueId": "ISSUE-...",
  "implementationCommit": "<implementation-commit-sha>",
  "scoringVersion": "autopilot-task-score/v1",
  "scoredAt": "<date-time>",
  "total": 0,
  "dimensions": {
    "deliveryCorrectness": { "score": 0, "max": 35, "evidence": [] },
    "zeroDanglingIssues": { "score": 0, "max": 25, "evidence": [] },
    "firstPassAcceptance": { "score": 0, "max": 20, "evidence": [] },
    "cycleEfficiency": { "score": 0, "max": 10, "evidence": [] },
    "stockIssueReduction": { "score": 0, "max": 10, "evidence": [] }
  },
  "hardGatesPassed": true,
  "followupNetChange": 0,
  "sourceRefs": []
}
```

- `issueId + implementationCommit + scoringVersion` 组成评分幂等键，重复 closeout 不得重复计数或产生第二份评分。
- `evidence` 只引用正式报告、测试摘要、closeout ledger、backlog 记录和 Git commit，不保存原始日志或截图路径。
- 历史评分只追加不改写；新版本不得重新计算旧任务。

### 3.4 两阶段 Git 收口协议

Git commit 不能在自身内容中保存自身 SHA，因此单任务固定使用两个职责不同的提交：

1. `implementationCommit`：包含实现、必需验证证据、归档材料和状态为 `PENDING_SCORE_BINDING` 的 iteration 报告；该提交产生后冻结评分输入。
2. 评分器只读取 `implementationCommit` 对应树和正式证据，生成绑定该 SHA 的评分记录并通过 schema 校验。
3. `closeoutCommit`：在上一提交之上写入评分区、Done/backlog 收口和必要正式材料；评分正文只引用 `implementationCommit`，不尝试写入 `closeoutCommit` 自身 SHA。
4. `closeoutCommit` 产生后由 closeout ledger 记录 `issueId`、`implementationCommit`、`closeoutCommit`、`scoringVersion` 和报告路径；只有该提交按适用流程合入基线且 ledger 登记成功，runner 才更新回顾周期计数。
5. 若第一阶段成功、第二阶段失败，保持任务为 `CLOSEOUT_PENDING_SCORE`，禁止选择下一任务；重试复用相同 `implementationCommit` 和评分幂等键，不重新执行实现，也不制造第三份评分。
6. 若实现执行器已经自行产生提交，closeout 必须先规范化识别唯一 `implementationCommit`，不得把评分收口提交误当作实施提交。

### 3.5 评分版本批准门

- 样本回放、schema、确定性评分器和 disabled 模式测试可以在版本待批准时实施。
- 配置中候选版本与已激活版本分离；待批准版本不得成为默认评分器，不得增加正式回顾计数。
- 用户批准至少明确 `scoringVersion`、五个维度权重和生效时间；批准证据进入正式决策载体并记录为有来源 Episode。
- 权重被拒绝或需要调整时，只修改候选版本并重新回放，不覆盖已产生的历史评分。

## 4. 自动改进回顾模型

### 4.1 回顾输入

回顾生成器读取本周期全部有效任务及其正式证据，至少汇总：

- 总任务数、任务性质、优先级、业务域和存量问题来源分布；
- 总分及五个维度的均值、中位数、最低值和分布；
- 首次验收通过率、补修次数、阻塞次数和失败分类；
- Ready 配置矛盾、范围门禁命中、图谱陈旧/刷新失败和工具召回不足次数；
- 周期耗时、重复验证和无效返工；
- 新增后续项、关闭后续项、净变化；
- 周期开始与结束时的知识图谱当前问题数量，以及 P0/P1/P2 变化。

### 4.2 自动发现规则

系统只按可解释、可复现的聚合规则生成候选，不根据单个低分任务自动立项。v1 至少检测：

- 同一失败分类或同一根因在本周期出现 3 次及以上；
- 任一评分维度的周期平均得分低于该维度满分的 80%；
- 首次验收通过率低于 80%；
- 后续项净变化大于 0，或连续任务出现同根因后续项；
- 存量问题总量未下降，且周期内存在以关闭存量问题为目标的任务；
- Ready 配置、图谱游标、工具路由或失败分类反复造成返工；
- 与上一已完成周期相比，中位周期时长恶化 20% 以上且有可归因证据。

阈值只负责生成“需要审阅的改进提案”，不得自动判定某项改进必须实施。

### 4.3 改进提案字段

每项提案必须包含：

- 稳定唯一键和关联周期；
- 命中规则、重复次数和来源任务；
- 根因证据与影响范围；
- 用户价值或风险降低价值；
- 最小改进方案与明确非目标；
- 优先级、依赖、回滚方式和验收标准；
- 是否涉及规则、评分权重、权限、安全或数据一致性；
- `approvalStatus=NEEDS_CONFIRMATION`；
- 与知识图谱现有问题的去重结果。

没有客观证据、明确价值或可执行验收标准的建议应有依据地关闭，不得为了“自动改进”制造 backlog。

### 4.4 正式载体与知识图谱

- 周期回顾正文写入 `docs/iterations/`，作为正式回顾证据，不写入临时运行目录。
- 逐任务评分写入对应 iteration 报告的结构化评分区，并在 `.codex-autopilot` 维护可恢复运行态索引；运行态索引不是正式事实源。
- 合格改进提案去重后写入现有唯一问题载体，未批准时标记为 `NEEDS_CONFIRMATION`；批准后转为可补货状态。
- 不新增 `improvement-issues.md`、`retrospective-backlog.json` 或其他孤立清单。
- 正式载体更新后必须先产生独立的回顾报告本地提交，再执行知识图谱增量采集；只有 Git 游标追平该提交，才视为图谱已读取正式版本。
- 回顾摘要记录为有来源的 Episode，问题提案成为结构化问题实体。Episode 必须显式传入稳定 ID `cgc-pms:episode:autopilot-retrospective:<reviewCycleId>:<scoringVersion>`，`occurredAt` 固定为本周期首次进入回顾的时间；重试不得依赖默认的“sourceRef + 当前时间”ID。
- 图谱写入或刷新失败时归类为 `tool_config`、`environment_prereq` 或数据一致性问题，并保持 `RETROSPECTIVE_REQUIRED`，不得伪造成功。

### 4.5 跨系统可恢复阶段协议

回顾收口不是跨系统 ACID 事务，必须按以下单向、幂等阶段执行并在 state 中记录最后成功阶段：

1. `REPORT_COMMITTED`：生成回顾报告及提案草案，通过 schema 和 `git diff --check` 后产生回顾报告提交，记录 `retrospectiveReportCommit`。
2. `ISSUES_WRITTEN`：按稳定提案键在唯一问题事实源中新增、合并或关闭，并把结果提交，记录包含报告和问题事实的 `retrospectiveFactsCommit`；重复执行只能命中同一问题。
3. `GRAPH_REFRESHED`：执行增量采集，验证图谱 Git cursor 等于或包含 `retrospectiveFactsCommit`，并按提案唯一键查询成功。
4. `EPISODE_RECORDED`：使用显式稳定 Episode ID 写入回顾摘要，并读回核验 `sourceRef` 与报告路径一致。
5. `RESET_COMMITTED`：最后一次原子写入 state，登记完成时间和报告/图谱/Episode 证据，再清空本周期任务列表与计数；清零后仍保持暂停，等待用户重新启动。

每一阶段写入前先检查已完成证据，写入后再读回验证。失败只记录失败分类和当前阶段，不执行跨系统逆向删除；恢复时从第一个未完成阶段继续。报告或问题提交已经成功但图谱失败时，不得重写历史提交或重复创建提案。

## 5. 目标状态机

```text
实施型 Ready 完成
  → 必需验证通过
  → 正式归档完成
  → implementationCommit 成功
  → 生成绑定 implementationCommit 的带版本任务评分
  → closeoutCommit 成功并登记
  → 幂等加入 reviewCycleCompletedIssueIds
  → reviewCycleCompletedCount + 1
      ├─ 未达到20：正常 checkpoint
      ├─ 无明确N且达到20：RETROSPECTIVE_REQUIRED
      └─ 明确N且达到20：retrospectiveDue=true，完成当前N后进入回顾

RETROSPECTIVE_REQUIRED
  → 禁止新任务派发
  → 汇总本周期全部任务
  → 生成整体回顾与改进提案并提交报告
  → 去重写入正式事实载体并提交
  → 刷新知识图谱并核验 Git cursor
  → 使用稳定 ID 记录并读回 Episode
  → 原子写入 retrospectiveCompleted
  → 周期计数清零（不结转）
  → 保持暂停，等待用户审阅与明确恢复
```

建议新增状态字段：

- `reviewCycleId`
- `reviewCycleStartedAt`
- `reviewCycleCompletedIssueIds`
- `reviewCycleCompletedCount`
- `retrospectiveDue`
- `retrospectiveStatus`
- `retrospectivePhase`
- `retrospectiveRequiredAt`
- `retrospectiveReportCommit`
- `retrospectiveFactsCommit`
- `retrospectiveGraphGitCursor`
- `retrospectiveEpisodeId`
- `retrospectiveFailureCategory`
- `lastRetrospectiveAt`
- `lastRetrospectiveReport`
- `activeScoringVersion`

这些字段不得与现有单次运行的 `iterationLimit`、`iterationCompleted` 或 `completedImplementationIssues` 混用。目标 state schema 固定升级为 `schemaVersion=3`：读取 v2 时只初始化空回顾周期，不把历史 `completedImplementationIssues` 或 `completedIssueIds` 复制为已评分任务；迁移校验或原子写入失败时保持暂停并保留原 v2 文件。低于 v2 或高于当前支持版本的 state 继续 fail-close。

## 6. 实施任务

### Task 1：用失败测试固定计数、评分和批次语义

**Files:**

- Create: `scripts/codex-autopilot/test-task-scoring.ps1`
- Create: `scripts/codex-autopilot/test-retrospective-cycle.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `scripts/codex-autopilot/test-completion-accounting.ps1`
- Modify: `scripts/codex-autopilot/test-unbounded-state.ps1`

- [ ] 证明只有“完成 + 验收通过 + 归档 + `implementationCommit` + 评分成功 + `closeoutCommit` 合入并登记”的实施型 Ready 才计数。
- [ ] 覆盖重复 closeout、重复 issueId、同 issue 不同失败尝试不得重复计数。
- [ ] 覆盖 `implementationCommit` 成功但评分或 `closeoutCommit` 失败时进入可恢复状态，重试不重复实现、不重复评分、不提前计数。
- [ ] 覆盖无界模式在第 20 个任务后不选择第 21 个任务。
- [ ] 覆盖累计 18 后执行 `启动迭代-3`：完成 3 个后对 21 个统一回顾，清零且不结转。
- [ ] 覆盖显式 N 一次跨越多个阈值仍只做一次整批回顾。
- [ ] 覆盖回顾失败时计数不清零、新批次被拒绝。
- [ ] 覆盖报告提交、问题写回、图谱刷新、Episode 和最终清零各阶段失败后的幂等续跑。
- [ ] 覆盖低分只记录，不触发补修或改变 DONE 裁决。

### Task 2：建立带版本的确定性任务评分器

**Files:**

- Create: `scripts/codex-autopilot/autopilot-task-score.ps1`
- Create: `plugins/cgc-pms-autopilot/schemas/task-score.schema.json`
- Modify: `scripts/codex-autopilot/autopilot-closeout.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`

- [ ] 从正式 closeout 证据计算五个维度，不调用模型自由打分。
- [ ] 使用 `issueId + implementationCommit + scoringVersion` 保证评分幂等。
- [ ] 缺失证据时拒绝生成评分并保持任务未进入回顾计数，不用默认分补齐。
- [ ] 按两阶段 Git 协议将评分摘要写入 iteration 正式报告，并在 `closeoutCommit` 成功后把结构化记录交给回顾周期索引。
- [ ] 配置明确区分 candidate 与 active；用户批准前 v1 保持 disabled，不允许回顾程序自行激活或覆盖。

### Task 3：扩展跨批次回顾周期状态

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/autopilot-start.ps1`
- Modify: `scripts/codex-autopilot/autopilot-status.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/loop-state.schema.json`
- Modify: `plugins/cgc-pms-autopilot/examples/loop-state.example.json`

- [ ] 新增独立回顾周期字段并执行 schema、唯一性和计数一致性校验。
- [ ] 状态写入保持原子性，每次变更同步刷新 `lastHeartbeatAt`。
- [ ] 实现确定性的 v2→v3 迁移；初始化空回顾周期，不伪造历史评分或历史回顾，失败时保留原 state 并安全暂停。
- [ ] `autopilot-status.ps1` 明确输出累计数、阈值、待回顾状态和阻止新批次的原因。

### Task 4：把回顾阈值接入连续 runner

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-checkpoint.ps1`
- Modify: `plugins/cgc-pms-autopilot/scripts/autopilot-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`

- [ ] `implementationCommit`、评分、`closeoutCommit`、合入和 ledger 登记全部成功后才更新回顾累计。
- [ ] 无界模式达到 20 后，在下一任务选择前进入回顾。
- [ ] 有界模式达到 20 后只置 pending，当前批次正常执行到 N 或其他合法停止条件。
- [ ] 批次结束统一回顾全部累计任务，不拆窗、不结转。
- [ ] 待回顾状态优先于新任务补货和 Ready 选择，但不绕过当前任务安全收口。

### Task 5：生成整体回顾和改进提案

**Files:**

- Create: `scripts/codex-autopilot/autopilot-retrospective.ps1`
- Create: `plugins/cgc-pms-autopilot/schemas/retrospective.schema.json`
- Create: `docs/iterations/AutoPilot任务周期回顾模板.md`
- Modify: `docs/iterations/README.md`

- [ ] 聚合本周期全部评分和正式收口证据，输出分布、趋势、失败分类和问题净变化。
- [ ] 按 v1 聚合规则发现重复问题，不因单个低分任务自动立项。
- [ ] 每个提案生成稳定唯一键并与知识图谱现有问题去重。
- [ ] 自动生成方案但不修改任何实现、规则或评分权重。
- [ ] 回顾报告明确列出：新增提案、合并提案、关闭建议、待用户批准项和后续项净变化。
- [ ] 回顾报告通过 schema 与 `git diff --check` 后先产生正式本地提交，后续图谱刷新只接受追平该提交的 Git cursor。

### Task 6：接入正式事实源和知识图谱闭环

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-retrospective.ps1`
- Modify: `tools/knowledge-graph/src/cli.js`（仅在现有 CLI 不能安全复用 Episode 写入时增加最小入口）
- Modify: `tools/knowledge-graph/test/cli-issues.test.js` 或新增对应 Episode CLI 测试
- Modify: `scripts/codex-autopilot/test-retrospective-cycle.ps1`

- [ ] 合格提案写入现有唯一问题事实源，默认保持待用户确认，不建独立清单。
- [ ] 正式写回后执行一次知识图谱增量刷新，并验证提案可以按唯一键查询。
- [ ] 回顾摘要使用正式报告路径作为 `sourceRef`，并显式传入基于 `reviewCycleId + scoringVersion` 的稳定 Episode ID；重复执行读回同一节点。
- [ ] 用 `REPORT_COMMITTED → ISSUES_WRITTEN → GRAPH_REFRESHED → EPISODE_RECORDED → RESET_COMMITTED` 阶段协议收口；只有前四阶段证据读回成功后，最终 state 写入才清零周期。
- [ ] 图谱异常时安全暂停，不静默跳过写入或改用私有缓存冒充完成。

### Task 7：固化规则、技能和评分版本治理

**Files:**

- Modify: `AGENTS.override.md`
- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/references/role-contracts.md`
- Modify: `plugins/cgc-pms-autopilot/references/forward-test-scenarios.md`
- Create: `docs/standards/14-AutoPilot任务评分与自动改进回顾规范.md`
- Modify: `docs/README.md`

- [ ] 固化有效任务计数条件、无界/有界语义、整批回顾和不结转规则。
- [ ] 明确低分不处理、硬门禁仍优先。
- [ ] 明确自动改进只提案不实施，用户批准后才能改变规则或发布新评分版本。
- [ ] 明确回顾发现项必须进入知识图谱支持的唯一问题体系并去重。
- [ ] 长期规则不记录一次性 run id、临时日志、截图或本地绝对临时路径。

### Task 8：集成验收与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`
- Create: `docs/quality/mainline-41-1-autopilot-scoring-retrospective-acceptance-YYYY-MM-DD.md`
- Modify: `docs/quality/README.md`
- Modify: `docs/product-intelligence/project-map.md`

- [ ] 执行评分、状态、completion accounting、continuous runner、control plane 和插件 artifact 回归。
- [ ] 用合成正式证据验证 20 个无界任务强制暂停。
- [ ] 用累计 18 + `启动迭代-3` 验证 21 个任务整批回顾且不结转。
- [ ] 验证单任务低分不触发处置，硬门禁失败的任务不计数。
- [ ] 验证改进提案只进入待确认状态，未发生自动实施或权重覆盖。
- [ ] 验证回顾报告、问题事实源、图谱查询、Episode 和 state 一致。
- [ ] 验证 v2→v3 不迁移历史完成计数，并验证每个跨系统阶段中断后可幂等恢复。
- [ ] 验证 v1 未批准时仅运行 candidate/disabled 回放，不能激活版本或增加正式累计。
- [ ] 正式报告记录 CodeGraph 召回不足、补充检索目的与交叉核验结果。

## 7. 验收命令

```powershell
cd D:\projects-test\cgc-pms
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-task-scoring.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-retrospective-cycle.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-completion-accounting.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-unbounded-state.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-state-machine.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-control-plane.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1

cd D:\projects-test\cgc-pms\tools\knowledge-graph
npm test
node src/cli.js status
node src/cli.js issues --view summary --current-only

cd D:\projects-test\cgc-pms
git diff --check
git status --short
```

正式验收至少断言：

- 每个有效计数任务都有唯一、带版本、可追溯的评分。
- 单个低分不会触发自动处置，任何硬门禁也不会被评分绕过。
- 无明确 N 时，第 20 个任务后不派发第 21 个任务。
- 明确 N 时先完成当前批次，再对全部累计任务统一回顾；超过阈值的任务不结转。
- 多次小批量 `启动迭代-N` 不能绕过累计阈值。
- 回顾只提出方案，不自动修改代码、规则、权重或环境。
- 改进提案已去重进入正式问题体系并可从知识图谱查询，未建立孤立清单。
- 未经批准不能发布新的 `scoringVersion`，历史评分未被重算。
- 回顾失败时保持暂停和累计状态，不能启动新批次。

## 8. 风险与控制

| 风险 | 等级 | 控制措施 |
| --- | --- | --- |
| 复用单次迭代计数导致跨批次丢数 | 高 | 新增独立回顾周期字段，禁止与 `iterationCompleted` 混用 |
| 重复 closeout 导致重复计数 | 高 | `issueId + implementationCommit + scoringVersion` 幂等键和唯一性测试 |
| 评分记录试图引用其所在提交自身 | 高 | 区分 `implementationCommit` 与 `closeoutCommit`，评分只绑定前者，后者成功后才计数 |
| 评分驱动“刷分”或替代质量门禁 | 高 | 评分只对已通过任务生成；低分不处置；硬门禁优先 |
| 显式 N 绕过 20 任务回顾 | 高 | 全局累计；达到阈值后最多延迟到当前 N 批次结束 |
| 超阈值任务被错误结转 | 中 | 整批报告成功后全部清零，测试锁定 18+3=21→0 |
| 回顾自动修改项目规则 | 高 | 生成器只输出提案；变更入口要求用户明确批准 |
| 新评分版本改写历史 | 高 | 版本只追加，任务记录绑定原版本，禁止批量重算 |
| 改进建议形成第二 backlog | 高 | 只写现有唯一事实载体并刷新图谱，不建独立清单 |
| 图谱刷新失败但计数已清零 | 高 | 阶段证据逐项读回；只有报告/问题提交、图谱 cursor、Episode 均成功后才执行最终 state 清零 |
| 跨 Git、问题事实源、Neo4j 与 state 的部分成功 | 高 | 使用单向阶段日志与读回证据恢复，不宣称跨系统原子事务，不逆向删除已成功事实 |
| Episode 重试生成重复节点 | 高 | 显式稳定 Episode ID 和固定 `occurredAt`，重试读回同一节点 |
| 未批准的 v1 权重被误激活 | 高 | candidate/active 配置隔离；用户明确批准前 disabled 且不计数 |
| v2 state 被误当作历史评分周期 | 高 | 显式 v2→v3 迁移，只初始化空周期，禁止复制旧完成计数 |
| 效率指标鼓励减少必要验证 | 中 | 周期效率仅评价无效返工；必需验证由硬门禁固定，不参与裁剪 |

## 9. 回滚方案

- 停用评分与回顾触发配置，恢复主线 41 已验收的知识图谱补货和 Ready 门禁行为。
- 回退新增评分器、回顾生成器和状态字段时，保留已经归档的历史评分与回顾报告，不删除知识图谱节点。
- 如果状态升级失败，保持 AutoPilot 暂停并人工核对，不以清空 state 方式跳过待回顾周期。
- 回退不改变 stop/pause、no-push、测试数据重置、Ready 合同和本地 commit 边界。

## 10. 完成定义

只有同时满足以下条件，第41-1条补充主线才可判定通过：

- 逐任务评分、评分版本和正式证据关联完整，且评分算法可重复计算。
- 两阶段 Git 收口可恢复，评分绑定 `implementationCommit`，只有 `closeoutCommit` 合入和登记后才计数。
- 跨批次回顾计数只统计合格实施型 Ready Issue，并能抵抗重复 closeout。
- 无界模式在 20 个任务后强制暂停；有界模式完成当前 N 后整批回顾且不结转。
- 自动回顾能基于聚合证据提出改进方案，但没有自动实施任何方案。
- 单任务低分不触发额外处理，既有硬门禁保持不变。
- 改进发现完成去重、正式承接与知识图谱刷新，没有孤立改进清单或悬空问题。
- 回顾收口各阶段均可幂等恢复，图谱 Git cursor 追平正式提交，Episode 使用稳定 ID 且可读回。
- 新评分版本必须等待用户批准，历史版本和得分不可变。
- v2→v3 state 迁移不伪造历史评分或回顾任务。
- 所有新增及受影响控制面测试通过，`git diff --check` 通过。
- 正式质量报告和项目地图完成回写，新增后续项、关闭后续项和净变化明确。
- 不连接生产、不发布生产、不自动 push。

**计划裁决：** 第41-1条主线已经完成实现、回放、用户批准和激活；正式计数从批准配置提交后启动的下一项新实施型 Ready 开始，历史任务不回算。后续评分版本变更仍按“样本回放 → 用户批准 → 新版本激活”推进，禁止覆盖历史评分或绕过幂等与恢复门禁。
