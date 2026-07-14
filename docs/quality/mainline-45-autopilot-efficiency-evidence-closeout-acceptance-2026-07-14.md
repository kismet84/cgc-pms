# 第45条主线：AutoPilot 模型往返证据复用与收口事实源优化验收报告

## 裁决

- 实现与自动化验收：通过。
- N>1 或无界连续执行放量：阻塞，必须由用户另行明确发出 `启动迭代-1`，并以新控制面指纹完成真实单 Issue 金丝雀。
- 控制面基线已完成提交前复验，本报告随该基线提交纳入版本管理；新指纹 AutoPilot 金丝雀尚未启动，未 push。

## 范围与正式交付物

- 模型调用观测：Executor、Reviewer、Planner 仅在真实进程成功启动后登记稳定 invocationId；Issue 与 RUN 作用域分开，Planner 候选只记录引用、不向 Issue 扇出。
- Context v3：每 Issue 一个不可变 Context Base，implement、repair、validate、review 使用独立 Delta；恢复与证据绑定继续核验 Ready、base commit、候选取证提交、策略和 diff。
- Evidence v2：记录类别、执行/复用模式、来源 evidenceId、命令、上下文、环境与差异指纹；只有 `UNIT_BUILD` 在全部身份一致时允许同 Issue 复用，v1、静态廉价、集成和浏览器证据不得复用。
- 收口事实链：`PreCloseoutFacts → Markdown 自动事实区 → closeout commit → frozen final result → Closeout Record v2 → ledger read-back → REGISTERED`。
- Closeout ledger：v2 同 key 同 payload 幂等返回原 `registeredAt`，同 key 异 payload 拒绝为 `integrity_conflict`；历史 `key + registeredAt` 记录只读兼容，不原地升级。
- 控制面策略、Schema、模板、artifact manifest、指纹清单、Current Focus 与项目地图已同步。

## 效率证据与 Task 4 裁决

- M0b 标准低风险 fixture：Executor 调用 1 次，Reviewer 调用 0 次；不存在可消除的额外模型往返。
- Owner 快速通道：`NO_MEASURABLE_BENEFIT`。未新增 route 分支、快速通道配置、Owner Skill 规则或快速通道测试。
- Planner 按 RUN 计数并保存 candidateRefs；不按候选或产出的 Issue 数重复计数。
- token 数据不可获得时输出 `tokenUsageStatus=not_available`，input/output/total 不伪记为 0。
- 当前正式 `closeouts.ndjson` 中 Closeout Record v2 样本为 0/20，生产聚合明确返回 `insufficient_sample`；p50/p95 为 null。历史 v1 和测试 fixture 均未进入生产样本。

## 验收证据

- 最终完整矩阵：24/24 通过，覆盖 metrics、efficiency observability、refill、stall、context isolation/delta、review/repair、repair integration、completion accounting、Evidence v2/reuse、closeout/projection/record、routing、state/transition/recovery、fingerprint、control plane、runner compatibility 和插件 artifact 校验。
- 报告投影：同一 PreCloseout Facts 重复渲染字节一致，人工裁决区保留，自动区不包含 closeoutCommit、reportHash、resultHash 或 REGISTERED 自引用字段。
- 冻结结果：重复写入相同 payload 幂等；不同 payload 触发完整性冲突。
- 换行：临时 Git fixture 显式使用 `core.autocrlf=false`、`core.eol=lf`；最终测试 `WARNING_COUNT=0`，本轮改动文件无混合 CRLF，UTF-8 无 BOM。
- 当前控制面指纹：`220473b3de1012674700ee8ad5c2f3336513534fa444b154afdece580861ce87`。

## 失败分类与处置

- fixture 主工作区 CRLF、worktree LF 导致策略原始字节哈希不一致：归类为文本规范兼容缺口；策略描述符改为 LF 规范化文本哈希，控制面总指纹仍覆盖行为文件。
- repair 使用模型调用前 delta 绑定模型调用后 diff：归类为 Evidence 身份契约缺口；新增 validate delta 后复验通过，未放宽 stale 门禁。
- done ledger 追加 CRLF 导致 `git diff --check` trailing whitespace：归类为文本写入缺口；改为 UTF-8 无 BOM、LF 写入。
- v2 样本为空时 percentile 参数绑定失败：归类为空样本边界缺口；纯函数显式允许空集合并返回 `insufficient_sample`。
- 非评分任务重复进入未合并 closeout commit 时仍错误要求评分证据：归类为幂等恢复分支缺口；评分校验改为仅在评分激活时执行，并补充“非评分两阶段提交、重复收口、再快进合并”回归，相关收口测试复验通过。
- 真实金丝雀首次补货时 Ready Planner 的结构化输出 Schema 被 API 拒绝：归类为 `tool_config`；`ready-plan.schema.json` 改为严格输出兼容形态，补齐 `schemaVersion` 类型、将条件字段改为必填 nullable，并由 importer 继续执行 CREATED/REJECTED/BLOCKED 语义校验；refill、runner compatibility 与 artifact 校验复验通过。

## 后续项治理

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 真实金丝雀不是口头悬空建议，而是既有控制面放量授权门；本报告不伪称已执行。用户授权前仅阻塞 N>1/无界放量，不否定本轮实现与自动化验收通过。

## 剩余风险

- 生产 v2 样本当前为 0，尚不能给出最近20项实际 p50/p95；首个新指纹真实金丝雀成功登记后才会形成首条正式样本。
- 本轮没有运行真实模型 token 计量，指标按契约标记不可用。
