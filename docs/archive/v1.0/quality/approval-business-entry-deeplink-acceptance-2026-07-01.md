# 第 6 条主线审批业务单据深链定位增强质量归档（2026-07-01）

## 1. 最终结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 是否建议关闭第 6 条主线：建议关闭
- 是否需要追加修复包：不需要

第 6 条主线目标是把第 5 条主线的“查看业务单据”入口从“采购申请 / 分包计量跳列表”增强为按 `businessId` 精确定位并自动打开目标业务单据现有查看弹窗。本轮前端实现、安全复核、运行态刷新、真实页面复验均已回传通过；只读核对 evidence 与 requests 也能支撑正式验收通过。

## 2. 实际修改文件

本主线实际修改范围如下：

1. `frontend-admin/src/pages/approval/todo.vue`
2. `frontend-admin/src/pages/approval/detail.vue`
3. `frontend-admin/src/pages/inventory/purchase-request.vue`
4. `frontend-admin/src/pages/subcontract/measure.vue`
5. `frontend-admin/src/pages/approval/__tests__/ApprovalWorkList.test.ts`
6. `frontend-admin/src/pages/approval/__tests__/ApprovalConfirm.test.ts`
7. `frontend-admin/src/pages/inventory/__tests__/purchase-request.test.ts`
8. `frontend-admin/src/pages/subcontract/__tests__/measure.test.ts`

本归档任务未修改业务代码，未重启服务，未提交 git。

## 3. 命令级验证结果

前端实现回传的命令级验证结果如下：

| 验证项 | 结果 |
|---|---|
| 审批相关测试 | 15/15 通过 |
| inventory / subcontract 相关测试 | 10/10 通过 |
| `pnpm build` | 通过 |
| `git diff --check` | 通过 |

本归档任务未重新运行上述命令，仅对归档文件、证据摘要、请求日志和忽略状态做只读核对。

## 4. 浏览器复验证据

证据目录：

`D:/projects-test/cgc-pms/output/playwright/approval-business-entry-deeplink-acceptance-20260701/`

证据文件包含：

1. `1-approval-done-purchase-readonly-detail.png`
2. `2-purchase-request-autolocate.yml`
3. `3-sub-measure-autolocate.yml`
4. `4-contract-deeplink.yml`
5. `5-contract-detail.png`
6. `6-contract-detail-1920x1080.png`
7. `evidence-summary.txt`
8. `requests.txt`

只读核对结果：

| 验收链路 | 证据 |
|---|---|
| 采购申请审批深链定位 | 从 `/approval/done` 点击“查看业务单据”后进入 `/inventory/purchase-request`，自动出现“查看采购申请”弹窗 |
| 采购申请接口命中 | `requests.txt` 命中 `/api/purchase-requests/970000000000005101` 与 `/api/purchase-requests/970000000000005101/items`，均 200 |
| 分包计量审批深链定位 | 从 `/approval/done` 点击“查看业务单据”后进入 `/subcontract/measure`，自动出现“查看分包计量”弹窗 |
| 分包计量接口命中 | `requests.txt` 命中 `/api/sub-measures/978000000000000302` 与 `/api/sub-measures/978000000000000302/items`，均 200 |
| 合同旧链路回归 | 点击“查看业务单据”后进入 `/contract/978000000000000102` |
| 合同接口命中 | `requests.txt` 命中 `/api/contracts/978000000000000102`，并继续命中 items、payment-terms、approval-records |
| 只读口径抽查 | `/approval/done` 详情保留“查看业务单据”和审批记录，未见 `同意 / 驳回 / 撤回 / 重提` |

## 5. 安全边界结论

安全复核结论：通过。

依据：

1. 采购申请自动打开前经过 `getPurchaseRequestDetail` / `/api/purchase-requests/{id}` 与明细接口。
2. 分包计量自动打开前经过 `getMeasureDetail` / `/api/sub-measures/{id}` 与明细接口。
3. 本次没有把 workflow 详情权限提升为目标业务单据查看权限。
4. `businessId` 查询参数只是定位输入，不作为授权依据。
5. 目标模块无权限、跨租户或单据不存在时，仍依赖目标模块现有错误态，不由 workflow 侧兜底渲染业务字段。

结论：未发现绕过目标模块鉴权、跨租户泄露或提升 workflow 权限含义的新增风险。

## 6. 正式验收达成情况

| 验收标准 | 结论 |
|---|---|
| 合同审批现有深链不回退 | 通过 |
| 采购申请审批可按 `businessId` 自动定位并呈现目标采购申请 | 通过 |
| 分包计量审批可按 `businessId` 自动定位并呈现目标分包计量 | 通过 |
| 内嵌详情与独立审批详情页策略一致 | 通过 |
| 未识别类型或缺少 `businessId` 时不展示入口 | 通过，依据前端测试回传 |
| 自动打开前经过目标模块详情接口 | 通过 |
| 无权限、跨租户、单据不存在时不泄露业务字段 | 通过，依据安全复核；真实负向样本未强制构造 |
| 前端定向单测通过 | 通过 |
| `pnpm build` 通过 | 通过 |
| 运行态刷新后真实浏览器矩阵通过 | 通过 |
| 质量归档报告完成 | 通过 |

## 7. 剩余风险

1. 负向权限样本未作为硬门禁真实构造，当前由安全静态复核兜底；后续如有可用账号 / 数据组合，可补一组无权限访问回归。
2. 项目级数据范围强化不在第 6 条主线内，若后续要收紧数据范围，需要另拆安全 / 权限小包。
3. `businessId` query 会在自动打开成功或失败后清理；若未来加入更复杂筛选、分页或返回审批详情能力，需要继续验证 query 清理不影响目标页状态。
4. 当前工作区仍存在第 6 条主线前端改动和 Playwright 证据产物，需由主负责人在合并或清理阶段统一裁决。

## 8. 下一步建议

1. 主负责人可按“通过 / 非阻塞”关闭第 6 条主线。
2. 不需要拆 workflow 后端改造包。
3. 不需要新增数据库字段、索引或 Flyway migration。
4. 不需要扩为审批中心全量导航重构。
5. 若后续需要无权限负向样本或项目级数据范围强化，单独拆小包处理。

## 9. 归档核对

- 报告路径：`D:/projects-test/cgc-pms/docs/quality/approval-business-entry-deeplink-acceptance-2026-07-01.md`
- `docs/` 忽略状态：`.gitignore:84:docs/`，因此本报告默认不进入 git status。
- 本次归档未修改业务代码、未重启服务、未提交 git。
