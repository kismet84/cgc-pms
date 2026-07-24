---
name: cgc-pms-autopilot-owner
description: Owns cgc-pms AutoPilot triggers, non-bypassable boundaries, and authoritative control-plane routing.
---

# cgc-pms AutoPilot Owner

普通任务不读取本 Skill 或其 references。只有完整 AutoPilot 触发短语，或用户明确要求审查/修改 AutoPilot 治理时使用。

## 触发协议

- `启动预演`：只做 dry-run；不开始实施，不提交，不推送。
- `启动迭代`：进入连续迭代；仍受 Ready、checkpoint、fencing、指纹、验证、Reviewer、收口和 no-push 门禁。
- `启动迭代-N`：`N=1..50`；最多完成 N 个实施型 Ready Issue。
- `停止迭代`：安全停止，不强杀当前任务；完成安全 checkpoint 后不再启动下一任务。
- 兼容短语：`启动自动迭代系统`、`启动连续自动迭代系统`、`启动连续自动迭代系统-N`、`停止自动迭代系统`。

## 不可绕过边界

- AutoPilot 只实施合格 Ready Issue；普通交互任务不受此限制。
- 权限、stop/pause/enabled checkpoint、fencing、控制面指纹、验证、Reviewer、收口、Git 和 no-push 门禁不可绕过。
- 禁止自动发布生产、连接生产数据库、删除仓库外文件、删除 `.git` 或用户目录、读取项目禁止区。
- 测试数据重置仅限 dev/test/demo、host 为 `localhost` 或 `127.0.0.1`，且存在 `.codex-autopilot/ALLOW_TEST_DATA_RESET`；缺一即禁止。
- 自动 commit、merge、push 或生产操作仍需相应明确授权；`autoPush=false` 时禁止自动 push。
- 控制面行为变化必须纳入指纹。新指纹进入 N>1 或无界执行前，必须由用户明确触发 `启动迭代-1` 并取得该指纹的金丝雀证据。

## 执行入口

1. 用 `scripts/codex-autopilot/autopilot-run-continuous.ps1` 作为唯一可写控制面；插件 runner 只做兼容与 dry-run。
2. 从权威 Ready 来源选择任务；Ready 为空时只按 control-plane 的有界补货顺序处理，异常时 fail-close。
3. 按配置执行 checkpoint、隔离 worktree、实现、验证、Reviewer、收口与恢复；动态值不得从本 Skill 推导。
4. 失败先调用统一 classifier；一级分类名称只引用 `.agents/skills/cgc-pms-ci-gate-triage/SKILL.md`。
5. 控制面修改通过静态与契约测试后，停在单 Issue 金丝雀授权门前；不得用测试代替真实金丝雀。

## 唯一事实入口

- 调度、补货、恢复、评分、回顾、金丝雀：`../../references/control-plane-policy.md`
- 桌面执行宿主：`../../references/desktop-execution-policy.md`
- Owner/角色职责：`../../references/owner-boundary.md`、`../../references/role-contracts.md`
- AutoPilot 子分类与动作：`../../references/classifier-rules.md`
- 重试与恢复：`../../references/rerun-policy.md`
- 输出与产物：`../../references/output-contract.md`、`../../references/artifact-governance.md`
- 循环预算：`../../references/loop-budget-policy.md`
- 动态配置：`../../../../scripts/codex-autopilot/codex-autopilot.config.json`
- 结构契约：`../../schemas/**`
- 正式状态与业务事实：项目 `docs/**`；插件 `artifacts/**` 只存插件自有产物。

## 收口输出

回报触发模式、Ready/Issue、checkpoint/指纹、验证与 Reviewer 证据、通过/不通过、阻塞、Git 状态、剩余风险，以及新增/关闭/净变化后续项。临时 run id、日志名和截图名不进入长期规则。
