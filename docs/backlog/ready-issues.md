# Ready Issues

## Ready 队列状态

v1.0 队列已封存到 [backlog 快照](../archive/v1.0/backlog-snapshot/ready-issues.md)。

`ISSUE-040-034`、`ISSUE-040-035`、`ISSUE-040-036`、`ISSUE-040-037`、`ISSUE-040-038` 已完成；本次 `启动迭代-5` 已达到 5 条上限。

`ISSUE-040-039`、阻塞修复 `ISSUE-047-001`、`ISSUE-040-040`～`ISSUE-040-055`、阻塞修复 `ISSUE-047-002` 与 `ISSUE-047-003` 已完成；`启动迭代-20` 已完成 20/20。站内通知的租户/用户隔离、已读幂等、SSE与通知铃契约已完成回归证明。

### ISSUE-040-056：WBS单前置FS门禁与风险提示回归

优先级：P1
任务性质：回归证明
类型：WBS / 单前置FS / 状态门禁 / 环依赖 / 项目隔离 / 单次提示
状态：Ready
来源锚点：唯一问题载体 `docs/backlog/current-issues.json`；sourceRefs=`docs/product-intelligence/project-map.md`; candidateEvidenceHead=f332d7462783d39ad9873480ce338570f6af2e88
存量问题键：[stock:OBS-WBS-PREDECESSOR-EVIDENCE]
关联产品目标：证明现有单前置FS门禁与前端风险提示在当前master保持一致，明确它不等于多前置和自动排程平台。
候选对比：现有Controller和前端任务测试已覆盖跨项目、环依赖、未完成门禁与迟交提示，独立收口成本低于扩关系表和排程引擎。
核验结论：SubTaskControllerTest覆盖前置关联、环检测、跨项目和未完成状态门禁；前端task测试覆盖前置未完成/迟交展示与错误单次提示。
阻塞证据：A-04概括多前置与平台能力仍缺，缺少当前master正式证据时容易低估既有单前置FS闭环或把它外推为完整排程。
解除条件：后端与前端目标测试通过，真实浏览器只打开/取消任务弹窗，正式报告关闭观察项。
Migration：不需要
依赖：既有 SubTask 单前置字段、状态机、任务页与目标测试。
风险等级：中
运行态要求：本地 H2/MySQL 与 Vite；浏览器只打开/取消，不写入任务。
Reviewer要求：确认跨项目、环依赖、未完成前置和单次提示边界；不得宣称多前置、lag、多类型、自动排程、基线或资源平衡。
归档报告：`docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md`
最小回滚：仅回退本项治理文档与报告。
目标：
- 证明单前置FS状态门禁、项目边界、环检测和前端风险提示在当前master生效。
- 明确A-04既有最小闭环与平台化缺口。
非目标：
- 不修改业务代码、数据库、测试实现或运行数据。
- 不新增多前置、SS/FF/SF、lag、自动排程、基线、资源平衡或甘特拖拽。
- 不连接生产、不发布生产、不push。
允许修改：
- `docs/backlog/current-issues.json`
- `docs/backlog/ready-issues.md`
- `docs/backlog/current-focus.md`
- `docs/product-intelligence/project-map.md`
- `docs/quality/ISSUE-040-056-WBS单前置FS门禁与风险提示回归验收报告.md`
禁止修改：
- `backend/**`
- `frontend-admin/**`
- `scripts/**`
- `plugins/**`
- `AGENTS.md`
- `AGENTS.override.md`
- `.github/**`
- `deploy/**`
验收标准：
- `SubTaskControllerTest` 全部通过，覆盖前置关联、未完成门禁、跨项目、环依赖和解除前置。
- `task.test.ts` 全部通过，覆盖未完成/迟交风险和错误单次提示；真实浏览器只取消不写入。
- Ready lint、允许路径、`git diff --check`通过；观察项关闭且新增后续项0、关闭1、净变化-1。
验证命令：
- `pwsh -NoProfile -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot . -ReadyPath docs/backlog/ready-issues.md -IssueTitle ISSUE-040-056`
- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest" test`
- `cd frontend-admin; pnpm vitest run src/pages/subcontract/__tests__/task.test.ts`
- `git diff --check`

## v1.5 准入要求

每条 Ready Issue 必须包含：编号、任务性质、目标、范围、非目标、验收标准、真实存在的验证命令、风险与回滚方式、归档报告路径。

只有状态为 `Ready` 且字段完整的任务才能进入 AutoPilot 实施；候选项不能直接执行。

## 历史 Issue 主题

- [ISSUE-047-003：日报Controller测试JWT密钥环境隔离修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-052：日报人工天气摘要与在场人数契约回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-053：供应商交付评分项目与空值边界回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-054：现金日记账CSV公式注入与导出契约回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-055：站内通知租户用户隔离与已读幂等回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-047-002：V155双数据库迁移镜像与H2测试隔离修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-051：供应商默认提前期与采购订单交货日期预填](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-050：现场日报领料联动真实浏览器视觉回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-049：WBS前置门禁错误单次提示与行为回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-048：PowerShell 7控制面与UTF-8兼容回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-047：WBS软删除墓碑事务故障注入回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-046：库存阈值与出库真实并发防覆盖回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-045：补货设置权限项目与乐观锁负向回归](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-044：驾驶舱性能测试阈值文案对齐](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-043：间接费规则受控删除入口与执行事实保护](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-042：间接费规则受控修改入口与字段状态边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-041：间接费规则受控新建入口与科目租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-040：投标成本标记未中标入口与费用核销边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-047-001：AutoPilot 收口区块边界与幂等误判修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-039：投标成本标记中标入口与状态项目边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-038：投标成本受控删除入口与状态租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-037：投标成本受控修改入口与状态租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-036：项目受控归档入口与项目数据范围](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-035：间接费规则只读列表入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-034：工作流个人效率统计只读入口与数据范围](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-033：投标成本详情只读入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-032：投标成本受控新建入口与租户状态边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-030：成本汇总历史只读入口与项目数据边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-031：投标成本只读列表入口与租户边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-029：共享列表中窄视口表格高度链修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-028：系统角色修改管理员入口与安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-027：系统角色删除管理员入口与安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-018：AutoPilot 存量问题优先补货与闭环门禁](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-017：知识图谱当前问题结构化查询](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-012～016：A-01 财务核算五项接口入口治理](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-001：预警批量评估独立权限码修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-022：Windows PowerShell 5.1 AutoPilot 脚本编码兼容修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-017：BaseEntity 备注写入契约修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-016：WBS 软删除编号冲突修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-015：WBS 单前置 FS 开工门禁](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-014：现场日报当日已审批领料只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-013：现场日报变更历史只读展示](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-012：现场日报当日计划任务只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-011：现场日报已审批材料到货只读联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-005：现场日报最小闭环](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-009：库存项人工补货目标量联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-010：库存项人工补货提前期与计划日期预填](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-008：现场日报 dev-login 直达路由白名单修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-007：现场日报天气摘要与在场人数补强](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-006：库存项安全库存阈值与补货建议联动](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-018：子智能体超时、悬挂执行线程退役与有限重派治理](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-019：后端接口无前端入口只读盘点与治理裁决](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-020：长期计划描述性标题误入候选修复](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-037-021：CI/CD 与上线门禁 v1.5 现状复验与红灯分类裁决](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-019：系统菜单新建管理员入口与树约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-020：系统菜单删除管理员入口与安全约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-021：系统菜单详情管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-022：系统菜单平铺列表管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-023：系统菜单修改管理员入口与树结构约束](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-024：间接费执行分摊用户入口与金额安全边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-025：系统角色新建管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)
- [ISSUE-040-026：系统角色详情管理员入口与权限边界](ready-history/ready-issues-review-b8c59c337f3e49b8a81e770e3a3d85a4.md)

## 当前 Ready Issue
