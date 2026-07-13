# Iteration Plan

阶段：P0 主链路回归与生产准入补强

**Goal:**
- 将 AutoPilot 从 EPIC-000 治理阶段切换到真实业务 P0 主链路回归阶段，优先处理成本、采购库存、付款发票审批三条核心链路，并为后续 P1 列表页生产化、文件安全、监控备份补齐可自动选择的 Ready Issue 池。

**Architecture:**
- 沿用现有前后端分层、测试与质量归档边界，不新增跨模块抽象，不引入生产发布、生产数据库连接、财务生产集成、总工程师、BIM、AI 等扩展范围；每轮只处理 1 个 Ready Issue，按单 Epic、单链路、最小允许修改范围推进。

## 本阶段执行顺序

1. `ISSUE-004-001` 成本台账与汇总口径回归
2. `ISSUE-004-002` 采购收货库存数量一致性回归
3. `ISSUE-004-003` 付款发票审批状态链路回归
4. `ISSUE-005-001` 付款与发票列表页生产化补强
5. `ISSUE-006-001` 文件上传白名单与发票识别失败兜底
6. `ISSUE-007-001` 访问日志上下文与备份清单补强

## 轮次门禁

- AutoPilot 采用两级队列：`docs/backlog/cgc-pms-production-enhancement-plan.md` 是长期总任务池，`docs/backlog/ready-issues.md` 是当前执行队列。
- 当当前执行队列没有合格 Ready Issue 时，本轮只从长期总任务池中按 `current-focus.md` 拆出最多 3 个小型 Ready Issue 并更新 backlog；拆解轮不直接修改业务代码，下一轮再执行。
- 只能从 `docs/backlog/ready-issues.md` 选择状态为 `Ready` 的 Issue。
- 开始前、选题后、改代码前、跑验证前、更新报告后都要检查 `.codex-autopilot/stop.flag` 与 `.codex-autopilot/pause.flag`。
- 每轮最多处理 1 个 Issue，最多修改 20 个文件，不跨两个 Epic 混做。
- 涉及后端或前端改动时，必须运行该 Issue 自带验证命令，并执行 `git diff --check`。
- 未完成失败分类前，不得把 CI 红灯、页面异常或接口失败直接定性为业务代码问题。
- 下一阶段采用全托管自动合并但不自动推送：仅当 Issue 允许范围、自带验证命令、自审 PASS、`git diff --check`、iteration report 更新、backlog done/blocked 更新、stop/pause 二次检查全部通过时，才允许自动合并，且 `autoPush=false`。
- 任一自动合并门禁失败即记为 blocked，不进入人工等待态。

## 非目标

- 不做总工程师驾驶舱、BIM、AI、财务生产集成。
- 不做生产发布、生产数据库连接、清库或测试数据重置。
- 不做跨全仓统一列表页改造；P1 仅允许从付款、发票列表页试点。
- 不在本阶段引入外部监控平台、病毒扫描服务或新增大型基础设施。

## 风险

- 主链路回归类 Issue 容易扩散到多个域，必须严格按允许修改范围收口。
- 列表页生产化若顺手扩到采购、库存等页面，会导致单轮超范围并影响验收。
- 文件上传与发票识别安全项涉及前后端联动，若缺少清晰失败口径，容易反复返工。
- 日志与备份项存在“只写文档不留验证证据”的风险，需强制带上最小验证或报告。

## 完成判定

- `current-focus.md` 已切换到 P0 主链路回归与生产准入补强。
- `ready-issues.md` 至少具备 4 个以上可执行 Ready Issue，且 ISSUE-000-001 不再处于 Ready。
- 每个 Ready Issue 都包含优先级、类型、状态、自动合并策略、目标、允许修改、禁止修改、验收标准、验证命令。
- Ready Issue 的自动合并策略统一为“允许但受门禁约束，`autoPush=false`”，失败直接 blocked。
- 下一轮 AutoPilot 可按顺序直接选题，无需再补治理型占位任务。
