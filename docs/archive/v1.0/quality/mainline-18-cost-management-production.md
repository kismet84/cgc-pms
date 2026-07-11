# 主线18：成本管理模块生产化增强质量报告

## 最终结论

- 结论：通过
- 阻塞：否
- 可上线：是

## 变更范围

- `frontend-admin/src/pages/cost/ledger.vue`
- `frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
- `frontend-admin/src/pages/cost-target/index.vue`
- `frontend-admin/src/pages/cost/summary.vue`
- `frontend-admin/src/router/index.ts`
- 对应成本管理相关测试文件
- 未启用后端补丁；本轮归档结论基于前端收口、路由权限修正、命令验收、运行态刷新和真实浏览器复验证据。

## 验收证据

- 契约复核 `M18-Cost-1`：
  - 后端补丁不启用。
  - 必改项已收口为：成本台账 `pageNo` / 筛选控件、目标成本 `approvalStatus` / `isActive`、成本路由权限码。
  - 动态成本和成本科目未发现新增后端筛选缺口。
- 成本台账 `M18-Cost-2`：
  - 文件：`frontend-admin/src/pages/cost/ledger.vue`、`frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`
  - 已修正 `pageNo`。
  - 已补 `合同`、`合作方`、`成本类型`、`日期范围` 控件。
  - 已实现项目切换清空合同/合作方。
  - 已处理本地 `contractOptions` 非阻塞加载、`dateRange` 安全访问、移动卡片、列设置移动隐藏。
  - 规格复核：通过。
  - 质量复核：通过。
- 目标成本 / 动态成本 / 路由 `M18-Cost-3`：
  - 文件：`frontend-admin/src/pages/cost-target/index.vue`、`frontend-admin/src/pages/cost/summary.vue`、`frontend-admin/src/router/index.ts`、对应测试。
  - 目标成本：已补 `approvalStatus` / `isActive` 控件和移动卡片。
  - 动态成本：项目列表改 `pageNo`，切项目清空旧 `summary`，补移动卡片。
  - 路由权限：`CostLedger=cost:ledger:query`、`CostSummary=cost:summary:view`、`CostSubject=cost:query`。
  - 规格复核：通过。
  - 质量复核：通过。
- 命令验收：
  - Vitest：`3` 个测试文件、`13` 个测试通过。
  - `pnpm type-check`：通过。
  - `pnpm build`：通过。
- 首次真实浏览器验收：
  - 结论：不通过。
  - 原因：前端运行态陈旧，不是本轮源码缺陷。
  - 证据目录：`D:\projects-test\cgc-pms\output\m18-cost-management-acceptance\`
- 运维刷新：
  - 执行：`python scripts/rebuild.py frontend`
  - 结果：前端运行态源码特征恢复正确，`/cost/ledger` 返回 `200`。
  - 说明：未改源码，仅刷新前端运行态。
- 真实浏览器复验：
  - 证据目录：`D:\projects-test\cgc-pms\output\m18-cost-management-acceptance-rerun\`
  - 关键证据：
    - `evidence.json`
    - `dom-followup.json`
    - `interaction-followup.json`
    - `summary-switch-followup.json`
    - 对应桌面 / 移动截图
  - 复验结论：通过。
  - 阻塞：否。
  - 可上线：是。
  - API 使用 `pageNo` / `pageSize`，未发现 `pageNum`。
  - 成本相关 API 失败：`0`
  - `consoleErrors=0`
  - `pageErrors=0`
  - `requestFailures=0`
  - 移动端：`ledger` / `target` 卡片存在，`summary` 未选项目时为空态合理，无横向溢出。
- 目标成本补充验收 `M18-Cost-8`：
  - 原非阻塞项：`/cost-target/index` 当前无目标成本数据，查看 / 编辑 / 激活只能按空数据态确认入口。
  - 首次有数据态验收：
    - 通过创建并清理临时数据补齐真实记录操作链路。
    - 发现桌面操作按钮被 `.lg-toolbar` / `.lg-pagination` 遮挡，编辑 / 激活不通过。
  - 首次修复与命令验收：
    - 修复文件：`frontend-admin/src/pages/cost-target/index.vue`
    - 测试补守卫：`frontend-admin/src/pages/cost-target/__tests__/CostTargetProduction.test.ts`
    - 修复内容：调整桌面布局，避免工具栏 / 分页遮挡操作列。
    - 命令验收：目标成本测试 `8` 个通过、`pnpm type-check` 通过、`pnpm build` 通过。
  - 第二次复验：
    - 编辑和激活入口已真实可触达。
    - 缺独立“查看详情”入口，复验未完全收口。
  - 第二次补丁与命令验收：
    - 补丁内容：桌面行操作菜单补独立“查看详情”入口，复用现有弹窗链路。
    - 命令验收：目标成本测试 `8` 个通过、`pnpm type-check` 通过、`pnpm build` 通过。
  - 最终有数据态复验：
    - 结论：通过。
    - 阻塞：否。
    - 非阻塞项：原“无目标成本数据导致未验证真实记录操作”已关闭。
    - 临时记录：`M18_TEMP_FINAL_2026-07-04T16-14-05-130Z`
    - 临时记录 ID：`2073440226837860354`
    - 验后已删除，复查 `/api/cost-targets` 返回 `total=0`。
  - 最终证据目录：`D:\projects-test\cgc-pms\output\m18-cost-target-data-acceptance-final\`
  - 关键证据：
    - `acceptance-result.json`
    - 截图 `01` 到 `04`
    - 查看详情：真实点击打开弹窗。
    - 编辑：真实点击打开弹窗，未保存。
    - 切换版本 / 激活：真实点击打开确认弹窗，未确认。
    - `apiFailures=[]`
    - `consoleErrors=[]`
    - `pageErrors=[]`
    - `requestFailures=[]`
- 严格只读详情态质量归档 `M18-Cost-10`：
  - 用户追加要求：修复目标成本“严格只读详情态”。
  - 实现范围：
    - `frontend-admin/src/pages/cost-target/index.vue`：`targetModalMode` 扩为 `create/edit/view`，`handleView(row)` 传 `view`。
    - `frontend-admin/src/pages/cost-target/edit.vue`：新增 `view` / `isView` 只读分支；详情态标题为“目标成本详情”；控件 `disabled` / `readonly`；添加 / 删除明细隐藏；底部只保留关闭；`doSubmit` 在 `view` 模式直接返回，不触发写接口。
    - `frontend-admin/src/pages/cost-target/__tests__/CostTargetProduction.test.ts`、`frontend-admin/src/pages/cost-target/__tests__/CostTargetModal.test.ts`：补齐 `view` 模式和只读契约覆盖。
  - 命令验收：
    - `CostTargetProduction.test.ts` + `CostTargetModal.test.ts`：`13/13` 通过。
    - `pnpm type-check`：通过。
    - `pnpm build`：通过。
  - 运行态说明：
    - 前端 `5173` 曾短暂超时，后续运维复查已恢复。
    - `/cost-target/index` 返回 `200`。
    - 运行态源码命中 `targetModalMode.value = "view"` 和“目标成本详情”。
  - 桌面严格只读详情态复验：
    - 结论：通过。
    - 证据目录：`D:\projects-test\cgc-pms\output\m18-cost-target-readonly-acceptance\`
    - 临时记录：`M18_TEMP_READONLY_1783210938436`
    - 临时记录 ID：`2073563388532146178`
    - 验后已删除。
    - 复验要点：标题为“目标成本详情”；无保存 / 提交；控件只读；详情期间只发生 GET 查询接口；编辑入口回归通过；清理后列表 `0` 条。
  - 移动严格只读详情态复验：
    - 结论：通过。
    - 证据目录：`D:\projects-test\cgc-pms\output\m18-cost-target-readonly-mobile-acceptance\`
    - 临时记录：`M18_TEMP_READONLY_MOBILE_1783211717822`
    - 临时记录 ID：`2073566375023394817`
    - 验后已删除。
    - 复验要点：移动卡片查看详情标题为“目标成本详情”；无保存 / 提交；`11` 个主要控件 `disabled` / `readonly`；详情期间只发生 GET 查询接口；清理后 `total=0`；API / 控制台错误 `0`。
  - 边界说明：
    - 移动端编辑入口不是本次“严格只读详情态”目标，也不是原主线18移动端必须项，不作为阻塞。
    - 若后续要求移动端编辑目标成本，应另立需求。

## 阻塞项

- 无

## 非阻塞项

- `/cost/summary` 移动态未选项目时不存在 `.cost-summary-mobile-list`，当前判定为合理空态，不视为阻塞。
- 本轮未强造持久业务数据验证真实金额写入回归，金额链路结论仍以现有数据、源码和测试证据为主。
- `/cost-target/index` 激活仅验证确认弹窗真实可触达，未最终确认；该边界用于避免制造不可清理状态，符合本轮验收约束。

## 模型分档复盘

- 契约复核任务使用较高档位子任务结论沉淀后归档：合理。该阶段需要判断哪些是必改项、哪些不是后端缺口，不能按机械摘录处理。
- 前端实现型任务集中在单页筛选、分页、路由权限和移动端视图，使用中档实现配置：合理，未出现必须升到高推理才能收口的证据。
- 真实浏览器验收与上线裁决阶段需要结合运行态、API、控制台、移动端证据做排除式判断，验收阶段升档：合理。
- 前端运行态刷新任务使用低推理运维档：合理。问题已证明是运行态陈旧，不是源码实现错误。
- 本次归档任务使用 `gpt-5.4 / low`：合理。任务目标是固定格式证据归档，不新增判断链路，不重做实现或上线裁决。
- 分档复盘结论：
  - 需要升档的任务：真实浏览器验收、上线裁决、运行态与源码不一致归因。
  - 可以降档的任务：最终质量归档、证据摘录、残留清理记录。
  - 不合理的统一分档应避免：不能把实现、真实验收、运维刷新、归档四类任务长期放在同一模型档位处理。

## 运行残留与清理

- 未发现 Playwright / Chromium 残留进程。
- 既有 Node / dev server 按要求保留，供继续复验使用，本轮未清理用户已有服务。
- `output` 证据目录保留，不删除：
  - `D:\projects-test\cgc-pms\output\m18-cost-management-acceptance\`
  - `D:\projects-test\cgc-pms\output\m18-cost-management-acceptance-rerun\`
  - `D:\projects-test\cgc-pms\output\m18-cost-target-data-acceptance-final\`
  - `D:\projects-test\cgc-pms\output\m18-cost-target-readonly-acceptance\`
  - `D:\projects-test\cgc-pms\output\m18-cost-target-readonly-mobile-acceptance\`
