# 第41-1条主线：AutoPilot 任务评分与20任务自动改进回顾验收报告

## 结论

- 实施结论：通过；`autopilot-task-score/v1` 已获用户明确批准并正式激活。
- 阻塞判断：非阻塞；评分与20任务回顾从批准配置提交后启动的下一项新实施型 Ready 起生效，历史任务不回算。
- 上线边界：配置为 `enabled=true`、`activeVersion=autopilot-task-score/v1`、`approvalStatus=APPROVED`，权重35/25/20/10/10；本轮未连接生产、未发布、未 push，也未启动下一项 Ready 或修改实际回顾累计。

## 正式交付物

- 确定性评分器、评分 schema、正式报告幂等评分区和缺证据 fail-close。
- 两阶段 closeout：评分绑定 `implementationCommit`，独立 `closeoutCommit` 完成评分与 ledger 收口。
- loop state v3、v2→v3 空周期迁移、Issue/评分键双重去重、`RETROSPECTIVE_REQUIRED` 门禁。
- 无界20任务停止、有界批次越阈值后整批回顾且不结转。
- 回顾聚合器、稳定改进提案键、唯一问题台账幂等合并、稳定 Episode ID 与最小 CLI 入口。
- 评分/回顾规范、回顾模板、候选样本回放、规则、主线 Skill、插件 Skill 与前向场景。

## 验收证据

以下命令均通过：

- `test-task-scoring.ps1`：100分样本、一次补修80分、确定性幂等键、缺证据拒绝、硬门禁拒绝、未批准配置拒绝、报告幂等。
- `test-retrospective-cycle.ps1`：无界20阻断第21个任务、重复登记不计数、18+3保留21个整批回顾、checkpoint 阻断新批次、阶段顺序与清零门禁、提案聚合和 Episode 稳定性。
- `test-completion-accounting.ps1`、`test-unbounded-state.ps1`、`test-state-machine.ps1`：既有完成计数和状态机回归通过。
- `test-closeout.ps1`：未评分旧链路回归和已批准夹具的两个不同提交、父子关系、报告评分及重试幂等通过。
- `test-continuous-runner.ps1`：完整 runner 回归通过。
- `test-control-plane.ps1` 与 `validate-loop-artifacts.ps1`：控制面、schema 和 artifact 治理通过。
- `tools/knowledge-graph` 的 `npm test`：24项全部通过，包含 Episode CLI 参数门禁和稳定有来源写入回归。
- `node src/cli.js status` 与 `node src/cli.js issues --view summary --current-only`：本地 Neo4j 只读查询成功，最近采集成功且失败数为0，Git cursor 与当前 HEAD 一致；当前问题汇总可读。
- `git diff --check`：通过。

代码审查阶段发现 `retrospective.enabled` 初版只存在于配置、runner 未把它纳入激活一致性门禁；本轮已修复为评分与回顾必须共同批准启用，单边启用会 fail-close，并由评分与完整 runner 回归覆盖。该发现已本轮修复并复验，没有转成悬空建议。

## 样本回放与批准门

对 ISSUE-040-019、020、021 三份正式报告的回放结果：交付正确性、问题零悬空和存量变化均可从正式材料稳定取证；首次验收与周期效率只有自然语言过程描述，没有统一结构化字段；历史报告也没有分别记录两个提交 SHA。因此没有补写主观分数或回算历史任务。用户已在回放结论基础上批准 v1，后续只使用新 runner 的结构化证据正式评分。

## 检索与交叉核验

- 查询目的：定位 AutoPilot runner、state、closeout、正式报告证据和知识图谱 Episode 的跨文件影响。
- CodeGraph 结果：优先查询仅召回知识图谱查询层，没有覆盖预期 PowerShell runner/state，归类为“工具召回不足”，没有据此判断代码不存在。
- 补充核验：使用只读 codebase-memory 做跨文件导航，并以当前分支的 `rg`、文件读取、Git diff 和专项测试交叉确认实际调用链与字段；未通过工具改写规则或配置。

## 失败分类

- 新增 PowerShell 文件首次在 Windows PowerShell 5.1 下因无 BOM 中文脚本解析失败，分类为 `tool_config/脚本编码前置`；统一为 UTF-8 BOM 后 AST 与测试通过，不是业务逻辑失败。
- continuous runner 旧测试夹具写入不完整 legacy state，与明确的 v2→v3 fail-close 契约冲突，分类为 `tool_config/测试夹具版本`；改为合法完整 v2 夹具后原测试通过。
- 首次从知识图谱子目录调用项目根 `autopilot-status.ps1` 使用了错误相对路径，分类为命令调用问题；切回仓库根后状态只读复验成功，不影响实现结论。

## 本地运行态边界

收口只读检查显示 `stop.flag=false`、`pause.flag=false`、`enabled.flag=true`、`run.lock=false`；现有实际 state 仍是上一轮的 schema v2 / `LIMIT_REACHED`。本轮没有调用 start/runner 写入该 state，v2→v3 迁移只在隔离测试中验证，因此没有伪造历史回顾计数，也没有派发下一任务。

## 风险与回滚

- 剩余风险：权重区分度尚待新结构化正式样本持续观测；该风险不降低硬门禁，也不允许系统自行调整权重，新版本仍须重新批准。
- 回滚：停用/移除评分与回顾候选脚本及 v3 扩展即可恢复第41条主线行为；已归档规范和回放报告保留审计，不删除知识图谱事实。

## 后续项零悬空

- 本轮新增后续项：0。
- 本轮关闭后续项：0。
- 后续项净变化：0。
- 已关闭待决项：用户已批准 `autopilot-task-score/v1` 五维权重及“下一项新实施型 Ready”生效边界；没有创建可被 AutoPilot 自动补货的审批 backlog。

## 最终裁决

- 实现、批准与激活：通过、非阻塞。
- 正式评分与20任务回顾：从下一项新实施型 Ready 起生效；历史累计为0。
- 是否可上线：本地 AutoPilot 控制面可用；不代表业务生产发布，本轮未发布生产、未连接生产数据库、未 push。
