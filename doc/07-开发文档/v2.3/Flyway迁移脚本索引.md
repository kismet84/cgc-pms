# Flyway 迁移脚本索引

> **目录**: `backend/src/main/resources/db/migration/`
> **版本**: V1 ~ V50（共 50 个）
> **规则**: **只追加不修改** — 修改已应用脚本会导致 Flyway checksum 校验失败，后端无法启动。

---

## 一、按表名快速查找

| 表名 | 模块 | 建表版本 | 后续修改版本 |
|------|------|----------|-------------|
| `sys_user` | 系统-用户 | V1 | V34（新增 org_id） |
| `sys_role` | 系统-角色 | V1 | — |
| `sys_menu` | 系统-菜单权限 | V1 | V11（补审计列） |
| `sys_user_role` | 系统-用户角色关联 | V1 | — |
| `sys_role_menu` | 系统-角色菜单关联 | V1 | — |
| `sys_dict_type` | 系统-字典类型 | V5 | — |
| `sys_dict_data` | 系统-字典数据 | V5 | — |
| `sys_file` | 系统-文件管理 | V7 | — |
| `sys_notification` | 系统-站内通知 | V37 | V45（审计列统一） |
| `sys_user_preference` | 系统-用户偏好 | V47 | — |
| `pm_project` | 项目-项目 | V2 | — |
| `pm_project_member` | 项目-成员 | V34 | V45（审计列统一） |
| `md_partner` | 合作方 | V2 | — |
| `md_material` | 物料字典 | V12 | — |
| `ct_contract` | 合同-主表 | V2 | V18（新增 paid_amount）、V20（新增 cost_generated_flag） |
| `ct_contract_item` | 合同-明细 | V2 | V10（补审计列） |
| `ct_contract_payment_term` | 合同-付款条款 | V2 | V10（补审计列） |
| `ct_contract_change` | 合同-变更 | V23 | V45（审计列统一） |
| `wf_template` | 审批-模板 | V3 | — |
| `wf_template_node` | 审批-模板节点 | V3 | — |
| `wf_instance` | 审批-实例 | V3 | V31（business_summary JSON→TEXT） |
| `wf_node_instance` | 审批-节点实例 | V3 | — |
| `wf_task` | 审批-任务 | V3 | — |
| `wf_record` | 审批-记录 | V3 | — |
| `wf_idempotency` | 审批-幂等 | V3 | — |
| `wf_cc` | 审批-抄送 | V38 | V45（审计列统一） |
| `cost_subject` | 成本-科目 | V4 | V21（补审计列） |
| `cost_item` | 成本-明细 | V4 | V25（回填 cost_subject_id） |
| `cost_summary` | 成本-汇总 | V12 | V26（新增 cost_target_id）、V27（公式修正回填） |
| `cost_target` | 成本-目标 | V22 | V45（审计列统一） |
| `cost_target_item` | 成本-目标明细 | V22 | V45（审计列统一） |
| `pay_application` | 付款-申请 | V4 | V19（新增 remark） |
| `pay_application_basis` | 付款-申请依据 | V12 | — |
| `pay_record` | 付款-记录 | V4 | — |
| `pay_invoice` | 付款-发票 | V36 | V45（审计列统一）、V48（新增 seller/buyer）、V49（新增 seller_tax_no）、V50（唯一约束修正） |
| `mat_warehouse` | 库存-仓库 | V35 | V45（审计列统一） |
| `mat_stock` | 库存-余额 | V35 | V45（审计列统一） |
| `mat_stock_txn` | 库存-流水 | V35 | V45（审计列统一） |
| `mat_purchase_request` | 采购-申请 | V35 | V45（审计列统一） |
| `mat_purchase_request_item` | 采购-申请明细 | V35 | V45（审计列统一）、V46（material_id 索引） |
| `mat_purchase_order` | 采购-订单 | V12 | — |
| `mat_purchase_order_item` | 采购-订单明细 | V12 | — |
| `mat_receipt` | 收货-主表 | V12 | — |
| `mat_receipt_item` | 收货-明细 | V12 | — |
| `sub_task` | 分包-任务 | V12 | — |
| `sub_measure` | 分包-计量 | V12 | — |
| `sub_measure_item` | 分包-计量明细 | V12 | — |
| `var_order` | 签证-变更令 | V12 | — |
| `var_order_item` | 签证-变更令明细 | V12 | — |
| `stl_settlement` | 结算-主表 | V12 | V24（字段增强） |
| `stl_settlement_item` | 结算-明细 | V12 | V24（字段增强） |
| `alert_log` | 预警-日志 | V24 | V43（新增 contract_id）、V45（审计列统一） |
| `org_company` | 组织-公司 | V33 | V45（审计列统一） |
| `org_department` | 组织-部门 | V33 | V45（审计列统一） |
| `org_position` | 组织-岗位 | V33 | V45（审计列统一） |

---

## 二、按版本号逐项说明

### V1 ~ V8：核心基础

| 版本 | 类型 | 说明 |
|------|------|------|
| V1 | **DDL** | RBAC 五张表：sys_user、sys_role、sys_menu、sys_user_role、sys_role_menu |
| V2 | **DDL** | 核心业务四张表：pm_project、md_partner、ct_contract、ct_contract_item、ct_contract_payment_term |
| V3 | **DDL** | 审批引擎六张表：wf_template、wf_template_node、wf_instance、wf_node_instance、wf_task、wf_record、wf_idempotency |
| V4 | **DDL** | 成本+付款四张表：cost_subject、cost_item、pay_application、pay_record |
| V5 | **DDL/DML** | 字典表 sys_dict_type + sys_dict_data 及种子数据（合同类型、项目状态等枚举） |
| V6 | **DML** | 演示数据：管理员 admin/admin123、SUPER_ADMIN/ADMIN 角色、Phase1 菜单权限 |
| V7 | **DDL** | 文件表 sys_file（MinIO 对象存储） |
| V8 | **DDL** | 为 sys_user/pm_project/md_partner/ct_contract 补充 created_at 排序索引 |

### V9 ~ V19：审计补齐 + 审批模板种子

| 版本 | 类型 | 说明 |
|------|------|------|
| V9 | **DML** | 合同审批模板种子（3 节点顺序审批） |
| V10 | **DDL** | ct_contract_item / ct_contract_payment_term 补缺失审计列 |
| V11 | **DDL** | sys_menu 补缺失审计列 |
| V12 | **DDL** | Phase2 业务表（14 张）：md_material、mat_purchase_order(_item)、mat_receipt(_item)、sub_task、sub_measure(_item)、var_order(_item)、pay_application_basis、cost_summary、stl_settlement(_item) |
| V13 | **DML** | 采购审批模板种子 |
| V14 | **DML** | 材料验收审批模板种子 |
| V15 | **DML** | 分包计量审批模板种子 |
| V16 | **DML** | 付款审批模板种子 |
| V17 | **DML** | 签证变更审批模板种子 |
| V18 | **DDL** | ct_contract 新增 paid_amount（累计已付金额） |
| V19 | **DDL** | pay_application 新增 remark |

### V20 ~ V31：成本体系完善

| 版本 | 类型 | 说明 |
|------|------|------|
| V20 | **DDL** | ct_contract 新增 cost_generated_flag（成本生成标识） |
| V21 | **DDL** | cost_subject 补审计列 |
| V22 | **DDL** | 成本目标体系：cost_target + cost_target_item（多版本，应用层保证唯一生效版本） |
| V23 | **DDL** | 合同变更表 ct_contract_change（正式变更，非现场签证） |
| V24 | **DDL** | 结算字段增强 + 预警记录表 alert_log + 成本汇总索引 |
| V25 | **DML** | 回填 cost_item.cost_subject_id（修复 4 个 CostGenerationStrategy 未设置科目 ID） |
| V26 | **DDL** | cost_summary 新增 cost_target_id |
| V27 | **DML** | 动态成本公式修正 + 数据回填 |
| V28 | **DML** | 合同变更审批模板种子 |
| V29 | **DML** | 结算审批模板种子 |
| V30 | **DML** | 成本目标审批模板种子 |
| V31 | **DDL** | wf_instance.business_summary JSON → TEXT（解决 JSON 类型兼容问题） |

### V32 ~ V42：组织 + 库存 + 协作 + 种子数据

| 版本 | 类型 | 说明 |
|------|------|------|
| V32 | **DML** | 补充 9 个业务类型审批提交权限码（contract/purchase/receipt/subcontract/payment/variation/change/settlement/cost-target:submit） |
| V33 | **DDL** | 三级组织架构：org_company、org_department（树形自引用）、org_position |
| V34 | **DDL** | pm_project_member 表 + sys_user.org_id（用户关联组织） |
| V35 | **DDL** | 库存体系：mat_warehouse、mat_stock（乐观锁 version）、mat_stock_txn、mat_purchase_request、mat_purchase_request_item |
| V36 | **DDL** | 发票表 pay_invoice |
| V37 | **DDL** | 站内通知表 sys_notification（冗余 tenant_id + user_id，支持 SSE/定时任务路径） |
| V38 | **DDL** | 审批抄送表 wf_cc（独立 join 表，不修改审批引擎核心） |
| V39 | **DML** | Phase4 菜单权限种子（ID 区间 700-799） |
| V40 | **DML** | Phase4 权限码修正 |
| V41 | **DML** | 遗留权限码修正 |
| V42 | **DML** | 种子数据补齐：物料字典(5 种)、仓库(2 个)、成本科目树(6 节点)、业务角色(材料员/财务)及角色菜单绑定 |

### V43 ~ V50：收敛与修复

| 版本 | 类型 | 说明 |
|------|------|------|
| V43 | **DDL** | alert_log 新增 contract_id 冗余字段及索引 |
| V44 | **DML** | 审批操作权限码种子（workflow:approve/reject/transfer/add-sign/withdraw/resubmit） |
| V45 | **DDL** | **审计列统一**：14 张 V22+ 表的 created_time/updated_time 重命名为 created_at/updated_at |
| V46 | **DDL** | mat_purchase_request_item 物料索引（幂等守护） |
| V47 | **DDL** | 用户偏好表 sys_user_preference（JSON 文本存储个性化设置） |
| V48 | **DDL** | pay_invoice 新增 seller_name/buyer_name |
| V49 | **DDL** | pay_invoice 新增 seller_tax_no |
| V50 | **DDL** | pay_invoice 唯一约束修正：uk_pi_tenant_invoice_no → uk_pi_tenant_invoice_no_del（纳入 deleted_flag） |

---

## 三、设计约定速查

| 约定 | 说明 |
|------|------|
| ID 策略 | 全部雪花 ID（MyBatis-Plus ASSIGN_ID），数据库无 AUTO_INCREMENT |
| 多租户 | 所有业务表含 `tenant_id BIGINT NOT NULL DEFAULT 0`，唯一约束含 tenant_id |
| 逻辑删除 | `deleted_flag TINYINT NOT NULL DEFAULT 0`（0=正常，1=已删除） |
| 审计列 | `created_by` + `created_at` + `updated_by` + `updated_at`（V45 前早期/中期命名不统一） |
| 字符集 | `utf8mb4` + `utf8mb4_0900_ai_ci` |
| 幂等建表 | `CREATE TABLE IF NOT EXISTS` |
| 幂等种子 | `INSERT IGNORE INTO` |
| 自增特例 | 仅 sys_user_preference 使用 AUTO_INCREMENT（V47） |
| H2 兼容 | V47 起注意不写 ENGINE/COLLATE/反引号等 MySQL 专有语法 |

---

## 四、常见问题

### Q: 修改已应用脚本会怎样？
**A**: Flyway 启动时校验 checksum，不匹配则抛 `FlywayValidateException`，后端无法启动。修复需手动进数据库改 `flyway_schema_history` 表。

### Q: 新增表/改表结构怎么做？
**A**: 创建新的 `V51__xxx.sql`（或更高版本号），不修改已有 V1~V50。

### Q: 脚本里 `created_time` vs `created_at` 混用怎么回事？
**A**: 历史遗留。V1~V21 用 `created_at`，V22~V38 用 `created_time`，V45 已将后者全部统一为 `created_at`。

### Q: 哪些表没有完整的审计列？
**A**: `sys_notification` 和 `wf_cc` 无 updated_time/updated_by/deleted_flag，设计如此（只增不删不改）。`wf_idempotency` 无审计列（纯技术表）。
