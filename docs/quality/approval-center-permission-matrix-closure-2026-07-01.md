# 第 8 条主线审批中心权限账号矩阵与权限感知入口优化质量归档（2026-07-01）

## 1. 最终结论

- 结论：通过
- 阻塞 / 非阻塞：非阻塞
- 是否建议关闭第 8 条主线：建议关闭
- 是否建议上线 / 合并：可进入主负责人最终裁决

第 8 条主线定位为“审批中心最小权限账号矩阵与权限感知入口优化”。基于小包 1 至小包 4 的回传结论、正式计划和本地 Playwright evidence 抽查，本主线已完成最小账号矩阵、demo-only seed、前端权限感知入口、真实浏览器权限矩阵验收与安全边界复核。当前未发现需要回灌为本主线阻塞项的剩余风险。

## 2. 小包 pass/fail 摘要

| 小包 | 内容 | 结论 | 阻塞状态 |
|---|---|---|---|
| 小包 1 | 账号矩阵设计与 seed 必要性裁决 | 通过 | 非阻塞 |
| 小包 2 | 最小 seed / 后端边界确认 | 通过 | 非阻塞 |
| 小包 3 | 前端入口展示最小实现 | 通过 | 非阻塞 |
| 小包 4 | 浏览器矩阵验收、安全复核与质量归档 | 通过 | 非阻塞 |

## 3. 实际范围与关键产物

### 账号矩阵

1. `admin`：全权限正向账号，沿用 `admin/admin123`。
2. `workflow-only`：可见 workflow 详情，但缺合同、采购申请、分包计量目标业务查询权限。
3. `cc-readonly`：可见 `/approval/cc` 抄送详情，但不具备审批动作。
4. `non-participant`：同租户非参与人，不应看到无关审批详情。

### 数据与迁移

1. V110 / V111 migration 已完成。
2. H2 / 静态测试通过。
3. 后端零业务代码改动成立。
4. 运行态 Flyway `111 success=1`。
5. workflow-only 三类主样本、`wf_instance`、首节点、admin pending task、SUBMIT record 均已落库。

### 前端

1. 权限感知入口实现完成。
2. 复用审批模块内入口与权限映射口径。
3. 缺目标权限时入口 disabled，并保持泛化拒绝提示。
4. 定向 vitest 与 `pnpm build` 通过。

## 4. 验证结果

| 类别 | 验证项 | 结果 |
|---|---|---|
| 小包 1 | 四类账号矩阵冻结 | 通过 |
| 小包 2 | V110 + V111 migration、H2 / 静态测试、运行态 Flyway 与样本落库 | 通过 |
| 小包 3 | 前端权限感知入口、定向 vitest、`pnpm build` | 通过 |
| 小包 4 | 真实浏览器权限矩阵、安全复核 | 通过 |
| 归档任务 | 文件写入、文件存在、`docs/` 忽略状态 | 通过 |

本归档任务未重新运行测试、构建、迁移或浏览器复验，仅对正式计划和 evidence 文件做只读核对。

## 5. 浏览器验收证据

证据目录：

`D:/projects-test/cgc-pms/output/playwright/approval-center-permission-matrix-acceptance-20260701/`

关键 evidence 文件：

1. `evidence.json`
2. `admin-positive.json`
3. `admin-contract-detail.png`
4. `admin-contract-business.png`
5. `admin-purchase-detail.png`
6. `admin-purchase-business.png`
7. `admin-submeasure-detail.png`
8. `admin-submeasure-business.png`
9. `workflow-only-contract.png`
10. `workflow-only-purchase.png`
11. `workflow-only-submeasure.png`
12. `cc-readonly-list.png`
13. `cc-readonly-detail.png`
14. `non-participant-todo.png`

`evidence.json` 抽查结论：

| 账号 | 验收点 | 结果 |
|---|---|---|
| `admin` | 合同、采购申请、分包计量三类业务入口可点击，目标业务单据可达 | 通过 |
| `demo_workflow_only` | 三类 workflow 详情可见，但 `查看业务单据` 为 disabled | 通过 |
| `demo_workflow_only` | 点击 disabled 入口未命中目标详情接口，`apiHits: []` | 通过 |
| `demo_workflow_only` | DOM 未泄露目标业务字段，`leaked: []` | 通过 |
| `demo_cc_readonly` | `/approval/cc` 详情只读，无 `同意 / 驳回 / 撤回 / 重提` | 通过 |
| `demo_cc_readonly` | `查看业务单据` 为 disabled | 通过 |
| `demo_non_participant` | 详情探针 `code=0, data=null` | 通过 |

`evidence.json` summary 显示：

- `adminPass: true`
- `workflowOnlyPass: true`
- `ccReadonlyPass: true`
- `nonParticipantPass: true`

## 6. 安全边界结论

安全边界通过：

1. workflow 详情权限未被提升为目标业务详情权限。
2. 前端 disabled 入口只是体验优化，不替代后端授权。
3. 目标业务详情接口仍承担真实鉴权边界。
4. workflow-only 账号未命中合同、采购申请、分包计量目标详情接口。
5. 无目标业务权限时未泄露合同、采购申请、分包计量详情字段。
6. `cc-readonly` 抄送详情未误显审批动作。
7. `non-participant` 对无关审批详情返回 `data=null`。

## 7. 代码质量裁决

本主线未引入过度抽象，符合最小可行实现边界：

1. 未新增权限配置中心。
2. 未新增入口注册体系。
3. 未新增 workflow 业务详情聚合接口。
4. 未让 workflow 详情接口返回目标业务权限结果。
5. 未重构全站 RBAC、菜单权限或数据权限体系。
6. 未新增表、字段、索引。
7. 未扩展到付款申请或更多业务类型。

当前实现路线是低成本、可验收的局部增强：用最小 demo-only 账号矩阵补真实浏览器证据，用前端现有权限集合做入口 disabled 展示，用目标业务模块继续承担真实权限边界。

## 8. 剩余风险

1. 本主线只覆盖合同、采购申请、分包计量三类冻结业务；付款申请或更多业务类型需另开主线。
2. 本主线不覆盖第二租户浏览器硬验。
3. 本主线不覆盖复杂 `data_scope` 全矩阵。
4. 本主线不覆盖项目级强隔离专项。
5. 权限感知入口当前是体验优化，不是安全授权边界；后续仍应以目标业务接口鉴权为准。

上述风险均属于后续不回灌项，不构成本主线阻塞。

## 9. 是否可关闭第 8 条主线

建议关闭。依据：

1. 四类账号矩阵已冻结并完成验收。
2. V110 / V111 seed 与运行态 Flyway 已通过。
3. 前端权限感知入口实现、定向测试和构建均通过。
4. 真实浏览器验收覆盖 admin 正向、workflow-only 负向、cc-readonly 只读、non-participant 不可见。
5. 安全复核未发现权限边界扩大。
6. 后续不回灌项已明确。
7. 质量归档报告已完成。

## 10. 后续不回灌项

以下事项不得回灌为第 8 条主线阻塞项：

1. 付款申请或更多 workflow 业务类型接入。
2. 第二租户浏览器硬验。
3. 复杂 `data_scope` 全矩阵。
4. 项目级强隔离专项。
5. 权限配置中心或入口注册体系。
6. 审批中心 UI 大改版。

如后续需要处理，应另开主线或专项。

## 11. 归档核对

- 报告路径：`D:/projects-test/cgc-pms/docs/quality/approval-center-permission-matrix-closure-2026-07-01.md`
- `docs/` 忽略状态：`.gitignore:84:docs/`，因此本报告默认不进入 git status。
- 本次归档未修改业务代码、未重启服务、未提交 git。
