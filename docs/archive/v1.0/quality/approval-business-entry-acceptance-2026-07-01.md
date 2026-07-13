# 第 5 条主线审批详情业务入口增强质量归档（2026-07-01）

## 1. 最终结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 是否建议关闭第 5 条主线：建议关闭
- 是否需要立即追加修复包：不需要

第 5 条主线只处理 GAP-005：审批详情缺少显式业务单据入口。基于前端实现、单测与构建、安全复核、运行态刷新、真实页面复验的已回传结论，本次增强满足正式计划书 MVP 目标：内嵌审批详情与独立审批详情页均补充“查看业务单据”入口，合同审批支持按 ID 跳转合同详情，采购申请与分包计量按本阶段约定跳转列表页，未识别业务类型或缺少 `businessId` 不展示入口。

## 2. 实际修改文件

本主线实际修改范围如下：

1. `frontend-admin/src/pages/approval/todo.vue`
2. `frontend-admin/src/pages/approval/detail.vue`
3. `frontend-admin/src/pages/approval/__tests__/ApprovalWorkList.test.ts`
4. `frontend-admin/src/pages/approval/__tests__/ApprovalConfirm.test.ts`

本归档任务未修改业务代码，未重启服务，未提交 git。

## 3. 命令级验证结果

前端实现回传的命令级验证结果如下：

| 验证项 | 结果 |
|---|---|
| `ApprovalWorkList.test.ts` | 10/10 通过 |
| `ApprovalConfirm.test.ts` | 5/5 通过 |
| `pnpm build` | 通过 |
| `git diff --check` | 通过 |

本归档任务未重新运行上述命令，仅对归档文件存在性、关键结论字段和 evidence 文件做只读核对。

## 4. 浏览器复验证据

证据目录：

`D:/projects-test/cgc-pms/output/playwright/approval-business-entry-acceptance-20260701/`

证据文件包含：

1. `01-contract-entry.png`
2. `01-contract-target.png`
3. `02-purchase-entry.png`
4. `02-purchase-target.png`
5. `03-submeasure-entry.png`
6. `03-submeasure-target.png`
7. `04-cc-readonly-check.png`
8. `evidence.json`

`evidence.json` 只读核对结果：

| 链路 | 复验结论 |
|---|---|
| 合同审批 | 从 `/approval/done` 点击“查看业务单据”后进入 `http://localhost:5173/contract/978000000000000102`，`pass: true` |
| 采购申请审批 | 从 `/approval/done` 点击后进入 `http://localhost:5173/inventory/purchase-request`，`pass: true` |
| 分包计量审批 | 从 `/approval/done` 点击后进入 `http://localhost:5173/subcontract/measure`，`pass: true` |
| `/approval/cc` 只读抽查 | 详情仍可查看，`visibleActions: []`，未见 `同意 / 驳回 / 撤回 / 重提` |

## 5. 安全边界结论

安全复核结论：通过，未发现本次新增入口绕过业务模块鉴权的风险。

依据：

1. 本次改动仅执行前端 `router.push(path)` 导航。
2. 未调用合同、采购申请、分包计量业务详情 API。
3. 合同审批跳转后仍进入 `/contract/${businessId}`，后续由合同模块和 `/contracts/{id}` 权限链处理。
4. 采购申请与分包计量当前只跳列表，不自动打开详情，不模拟业务详情查询。
5. workflow 详情只提供 `businessType / businessId` 用于导航，不授予业务单据查看权。

结论：当前安全边界符合正式计划书要求；有审批详情权限但没有目标业务模块权限的用户，被目标业务模块拒绝访问属于正确边界，不属于本主线失败。

## 6. MVP 边界

本主线明确接受以下 MVP 限制：

1. 采购申请审批只跳 `/inventory/purchase-request`，不做按 ID 深链。
2. 分包计量审批只跳 `/subcontract/measure`，不做按 ID 深链。
3. 未识别 `businessType` 或缺少 `businessId` 时不展示入口，避免误导。
4. 不新增 workflow 后端接口。
5. 不修改 workflow 详情返回契约。
6. 不重做审批中心全量导航、搜索、高级筛选、移动端或新审批页面。

## 7. 剩余风险

1. 采购申请和分包计量仍不能从审批详情按 ID 精确打开业务单据；这是本主线明确接受的 MVP 限制，非阻塞。
2. 若后续产品要求“仅在用户拥有目标业务模块权限时展示入口”，需要另拆权限感知 UX 小包；当前由目标页面和接口负责拒绝访问。
3. 本归档基于前置助手回传和现有 evidence 文件核对，未重新执行单测、构建或浏览器复验。
4. 当前工作区仍存在本主线前端改动和 Playwright 证据产物，需由主负责人在合并或清理阶段统一裁决。

## 8. 下一步建议

1. 主负责人可按“通过 / 非阻塞”关闭第 5 条主线。
2. 不需要立即拆后端权限修复包。
3. 不需要立即拆审批中心全量导航重构包。
4. 若后续要增强采购申请或分包计量按 ID 深链，应单独拆小包，先补目标业务页面路由能力和权限验收，再接入审批详情入口。

## 9. 归档核对

- 报告路径：`D:/projects-test/cgc-pms/docs/quality/approval-business-entry-acceptance-2026-07-01.md`
- `docs/` 忽略状态：`.gitignore:84:docs/`，因此本报告默认不进入 git status。
- 本次归档未修改业务代码、未重启服务、未提交 git。
