# 第 7 条主线审批中心生产化闭环增强质量归档（2026-07-01）

## 1. 最终结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 是否建议关闭第 7 条主线：建议关闭
- 是否建议上线 / 合并：可进入主负责人最终裁决

第 7 条主线按正式计划拆成 4 个串行小包推进，目标是把审批中心从“样本闭环可用”推进到“四页签服务端筛选、三类核心业务入口、详情一致性、只读/动作口径和运行态验收可交付”。基于小包回传结论、正式计划和本地 evidence 抽查，本主线当前满足关闭条件；剩余风险均为非阻塞增强或样本限制。

## 2. 小包 pass/fail 摘要

| 小包 | 内容 | 结论 | 阻塞状态 |
|---|---|---|---|
| 小包 1 | 后端服务端筛选契约 | 通过 | 非阻塞 |
| 小包 2 | 前端筛选接入与轻量映射收口 | 通过 | 非阻塞 |
| 小包 3 | 详情一致性与入口 / 只读回归 | 通过 | 非阻塞 |
| 小包 4 | 运行态刷新、真实浏览器核心矩阵、负向验收与质量归档 | 通过 | 非阻塞 |

## 3. 实际修改文件

### 后端

1. `backend/src/main/java/com/cgcpms/workflow/controller/WorkflowController.java`
2. `backend/src/main/java/com/cgcpms/workflow/service/WorkflowQueryService.java`
3. `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`

### 前端

1. `frontend-admin/src/pages/approval/todo.vue`
2. `frontend-admin/src/pages/approval/detail.vue`
3. `frontend-admin/src/pages/approval/process.vue`
4. `frontend-admin/src/pages/approval/workflowDisplay.ts`
5. `frontend-admin/src/pages/approval/__tests__/ApprovalWorkList.test.ts`
6. `frontend-admin/src/pages/approval/__tests__/ApprovalConfirm.test.ts`

本归档任务未修改业务代码，未重启服务，未提交 git。

## 4. 验证结果

| 类别 | 验证项 | 结果 |
|---|---|---|
| 后端测试 | `WorkflowQueryServiceTest` | 通过 |
| 前端测试 | 2 个审批测试文件共 16 个用例 | 通过 |
| 前端构建 | `pnpm build` | 通过 |
| 格式检查 | `git diff --check` | 通过 |
| 后端运行态 | `clean package + restart + 等待3分钟 + health=UP` | 通过 |
| 前端运行态 | 重启并等待 3 分钟，`http://localhost:5173/` 返回 200 | 通过 |
| 运行态特征 | 后端筛选参数、审批 helper、三类入口映射、只读/动作显隐关键特征 | 命中 |
| 浏览器验收 | 核心矩阵与负向验收 | 通过 |

本归档任务未重新执行测试、构建或服务刷新，仅抽查本地 evidence 与归档文件状态。

## 5. 浏览器验收证据

证据目录：

`D:/projects-test/cgc-pms/output/playwright/approval-center-production-acceptance-20260701/`

本地 evidence 抽查结果：

| 入口 / 场景 | 证据 |
|---|---|
| `/approval/todo` keyword 筛选 | `02-todo-keyword-network.json` 命中 `/api/workflow/tasks/todo?...&keyword=分包`，响应 200 |
| `/approval/todo` businessType / instanceStatus | 存在 `03-todo-businesstype-*`、`04-todo-status-*` 证据文件 |
| `/approval/done` 采购申请入口 | `09-done-purchase-business-entry-network.json` 命中 `/api/purchase-requests/978000000000000203` 与 `/items`，均 200 |
| `/approval/cc` 只读 | 存在 `12-cc-detail-snapshot.txt` 与 `12-cc-detail.png`，前置回传确认未见 `同意 / 驳回 / 撤回 / 重提` |
| `/approval/mine` instanceStatus | 存在 `14-mine-status-network.json`、`14-mine-status-rows.json` 与截图 |
| `/approval/mine` 翻页 | `21-mine-page2-network.json`、`21-mine-page2-rows.json` 与截图证明翻页真实换页 |
| `/approval/mine` 分包计量入口 | `17-mine-submeasure-business-entry-network.json` 命中 `/api/sub-measures/978000000000000301` 与 `/items`，均 200 |
| invalid 分包 businessId | `18-submeasure-invalid-businessid-network.json` 命中 `/api/sub-measures/999999999999999999`，返回 400 |
| invalid 采购 businessId | 存在 `19-purchase-invalid-businessid-*` 证据，前置回传确认返回 400 且无详情弹窗 |
| 合同入口回归 | 存在 `20-contract-business-entry-network.json`、snapshot 与截图 |

## 6. 安全边界结论

安全复核通过，当前未发现以下新增风险：

1. workflow 权限被扩大为目标业务详情权限。
2. 采购申请 / 分包计量深链绕过目标模块详情接口。
3. 未知业务类型向用户泄露内部码值。
4. `done/cc` 只读页签误显审批动作。

本轮后端筛选契约要求保留原身份谓词，筛选参数只作为原身份边界后的附加条件。业务入口仍由合同、采购申请、分包计量目标模块完成自身鉴权；workflow 只提供审批上下文与导航能力。

## 7. 代码质量裁决

本主线未采用过度方案，符合最小可行治理边界：

1. 未重写 workflow 查询引擎。
2. 未新增四页签巨型联合查询接口。
3. 未新增 workflow 聚合业务详情接口。
4. 未引入全文检索 / 搜索 DSL。
5. 未建立业务类型配置中心、字典表或插件式入口注册表。
6. 未做共用详情大组件重构。
7. 未做全站权限重构或审批中心全面 UI 重做。

当前抽出的 `workflowDisplay.ts` 属于审批模块内轻量 helper，范围限定在业务类型中文名、实例状态中文名、三类业务入口映射和未知兜底，收益大于维护成本。正式版中“筛选走服务端参数”的路线也规避了前端本地筛选导致分页 total 不一致的主要生产风险。

## 8. 剩余风险

1. `/approval/mine` 状态筛选后再翻页缺少浏览器硬证，受样本和分页限制；已有 mine 状态筛选证据与翻页真实换页证据，但二者组合未形成硬证。
2. 同租户无目标业务权限点击入口被拒绝缺少现成账号 / 样本，当前按安全静态复核兜底。
3. 跨租户与复杂 `projectId/data_scope` 场景未纳入真实浏览器硬验，符合正式计划的静态兜底口径。
4. 后续若业务类型扩展到付款申请或更多 workflow 类型，需要继续补 helper 映射、入口策略、测试样本和安全验收，不应直接复用本轮三类业务结论。

上述风险均不阻塞第 7 条主线关闭。

## 9. 是否可关闭第 7 条主线

建议关闭。依据：

1. 四个串行小包均回传通过。
2. 后端服务端筛选契约已落地并通过测试。
3. 前端四页签筛选接入与轻量映射收口通过测试和构建。
4. 第 5/6 条业务入口能力未回退。
5. `done/cc` 只读口径与 `todo/mine` 动作口径未回退。
6. 后端、前端运行态刷新均完成并通过健康检查 / 5173 检查。
7. 真实浏览器核心矩阵和负向验收通过。
8. 质量归档报告已完成。

## 10. 后续建议

1. 主负责人可按“通过 / 非阻塞”关闭第 7 条主线。
2. 不建议在本主线继续追加付款申请、新业务类型、权限感知隐藏入口、全量负向权限账号矩阵或审批中心 UI 重构。
3. 若后续要处理剩余风险，建议单独拆小包：`mine 状态筛选 + 翻页组合硬证`、`同租户无目标业务权限真实样本`、`下一批业务类型入口策略`。

## 11. 归档核对

- 报告路径：`D:/projects-test/cgc-pms/docs/quality/approval-center-production-closure-2026-07-01.md`
- `docs/` 忽略状态：`.gitignore:84:docs/`，因此本报告默认不进入 git status。
- 本次归档未修改业务代码、未重启服务、未提交 git。
