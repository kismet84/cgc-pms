> 文档版本：V1.1 正式修订版  
> 输出日期：2026-06-10  
> 项目名称：建筑工程总包项目全过程管理系统  
> 架构基线：模块化单体优先、MySQL 8.0、统一审批引擎、统一 API 契约、PC Web 优先、移动端后置启动  

# 数据库设计方案 MySQL 8 正式版

## 1. 数据库基线

| 项目 | 定版 |
|---|---|
| 默认数据库 | MySQL 8.0 |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_0900_ai_ci`，如需兼容旧版可用 `utf8mb4_general_ci` |
| 存储引擎 | InnoDB |
| JSON 配置 | 使用 MySQL `JSON` 字段 |
| DDL 管理 | Flyway 迁移脚本优先 |
| ID 策略 | 默认后端统一生成雪花 ID，数据库不强制 `AUTO_INCREMENT` |
| 删除策略 | 逻辑删除 `deleted_flag` |
| 时间字段 | `DATETIME`，由应用层统一时区 |
| 金额字段 | `DECIMAL(18,2)` 或 `DECIMAL(18,4)` |

## 2. ID 策略说明

本项目默认采用后端统一 ID 生成器，例如雪花 ID。数据库主键写法：

```sql
id BIGINT NOT NULL COMMENT '主键 ID'
```

不默认使用：

```sql
AUTO_INCREMENT
```

原因：

```text
便于未来分布式扩展
便于移动端离线草稿和服务端合并
避免强依赖数据库自增
前端 JSON 中 ID 统一字符串传输
```

如实施团队明确只采用单库自增，也可将主键策略调整为 `BIGINT AUTO_INCREMENT`，但必须在全库统一。

## 3. 数据库分层

| 前缀 | 模块 | 示例 |
|---|---|---|
| `sys_` | 系统权限 | 用户、角色、菜单、字典 |
| `org_` | 组织架构 | 公司、部门、岗位 |
| `pm_` | 项目管理 | 项目、项目成员、项目目标 |
| `md_` | 主数据 | 合作方、材料、成本科目 |
| `ct_` | 合同管理 | 合同、合同清单、付款条件、合同变更 |
| `mat_` | 材料设备 | 采购申请、采购订单、验收、入库、出库 |
| `sub_` | 分包管理 | 分包任务、分包进度、分包计量 |
| `cost_` | 成本管理 | 目标成本、成本明细、动态成本 |
| `pay_` | 付款管理 | 付款申请、付款记录、发票 |
| `var_` | 变更签证 | 签证、变更、索赔 |
| `stl_` | 结算管理 | 总包结算、分包结算、采购结算 |
| `wf_` | 审批流程 | 模板、实例、节点、任务、记录 |
| `doc_` | 文件资料 | 文件、目录、业务附件 |
| `log_` | 日志审计 | 操作日志、登录日志 |

## 4. 公共字段

所有核心业务表建议包含：

```sql
tenant_id      BIGINT NOT NULL COMMENT '租户/公司 ID',
project_id     BIGINT NULL COMMENT '项目 ID',
org_id         BIGINT NULL COMMENT '组织/项目部 ID',
status         VARCHAR(50) NOT NULL DEFAULT 'NORMAL' COMMENT '业务状态',
approval_status VARCHAR(50) NULL COMMENT '审批状态',
created_by     BIGINT NULL COMMENT '创建人',
created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updated_by     BIGINT NULL COMMENT '更新人',
updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
deleted_flag   TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
remark         VARCHAR(500) NULL COMMENT '备注'
```

## 5. MVP 表清单

### 5.1 基础与权限

```text
sys_user
sys_role
sys_menu
sys_user_role
sys_role_menu
sys_dict_type
sys_dict_item
org_company
org_department
org_position
doc_file
doc_relation
```

### 5.2 核心业务

```text
pm_project
pm_project_member
md_partner
md_material
md_cost_subject
ct_contract
ct_contract_item
ct_payment_term
ct_contract_change
mat_purchase_request
mat_purchase_request_item
mat_purchase_order
mat_purchase_order_item
mat_receipt
mat_receipt_item
mat_stock
mat_stock_record
sub_task
sub_progress
sub_measure
sub_measure_item
cost_target
cost_item
pay_request
pay_request_basis
pay_record
pay_invoice
var_order
var_order_item
stl_settlement
stl_settlement_item
```

### 5.3 审批流程

```text
wf_template
wf_template_node
wf_instance
wf_node_instance
wf_task
wf_record
wf_idempotency
```

## 6. 核心 DDL 示例

### 6.1 项目表 `pm_project`

```sql
CREATE TABLE pm_project (
  id BIGINT NOT NULL COMMENT '项目 ID',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  project_code VARCHAR(64) NOT NULL COMMENT '项目编号',
  project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
  project_type VARCHAR(50) NULL COMMENT '项目类型',
  project_address VARCHAR(300) NULL COMMENT '项目地址',
  owner_unit VARCHAR(200) NULL COMMENT '建设单位',
  supervisor_unit VARCHAR(200) NULL COMMENT '监理单位',
  design_unit VARCHAR(200) NULL COMMENT '设计单位',
  contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '总包合同金额',
  target_cost DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '目标成本',
  planned_start_date DATE NULL COMMENT '计划开工日期',
  planned_end_date DATE NULL COMMENT '计划竣工日期',
  actual_start_date DATE NULL COMMENT '实际开工日期',
  actual_end_date DATE NULL COMMENT '实际竣工日期',
  project_manager_id BIGINT NULL COMMENT '项目经理 ID',
  status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
  created_by BIGINT NULL COMMENT '创建人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
  remark VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pm_project_code (tenant_id, project_code),
  KEY idx_pm_project_manager (project_manager_id),
  KEY idx_pm_project_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';
```

### 6.2 合同主表 `ct_contract`

```sql
CREATE TABLE ct_contract (
  id BIGINT NOT NULL COMMENT '合同 ID',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  partner_id BIGINT NOT NULL COMMENT '合作方 ID',
  contract_code VARCHAR(64) NOT NULL COMMENT '合同编号',
  contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
  contract_type VARCHAR(50) NOT NULL COMMENT '合同类型',
  party_a VARCHAR(200) NULL COMMENT '甲方',
  party_b VARCHAR(200) NULL COMMENT '乙方',
  contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '合同金额',
  change_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已生效变更金额',
  current_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '当前合同金额',
  tax_rate DECIMAL(6,2) NULL COMMENT '税率',
  tax_amount DECIMAL(18,2) NULL COMMENT '税额',
  amount_without_tax DECIMAL(18,2) NULL COMMENT '不含税金额',
  signed_date DATE NULL COMMENT '签订日期',
  start_date DATE NULL COMMENT '合同开始日期',
  end_date DATE NULL COMMENT '合同结束日期',
  payment_method VARCHAR(100) NULL COMMENT '付款方式',
  settlement_method VARCHAR(100) NULL COMMENT '结算方式',
  warranty_rate DECIMAL(6,2) NULL COMMENT '质保金比例',
  warranty_amount DECIMAL(18,2) NULL COMMENT '质保金金额',
  paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已付款金额',
  payment_ratio DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT '付款比例',
  contract_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '合同业务状态',
  approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  settlement_status VARCHAR(50) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态',
  archive_status VARCHAR(50) NOT NULL DEFAULT 'NOT_ARCHIVED' COMMENT '归档状态',
  risk_level VARCHAR(50) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_flag TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code),
  KEY idx_ct_contract_project (project_id),
  KEY idx_ct_contract_partner (partner_id),
  KEY idx_ct_contract_type_status (tenant_id, contract_type, contract_status),
  KEY idx_ct_contract_approval (tenant_id, approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同主表';
```

### 6.3 成本明细表 `cost_item`

```sql
CREATE TABLE cost_item (
  id BIGINT NOT NULL COMMENT '成本明细 ID',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  contract_id BIGINT NULL COMMENT '合同 ID',
  partner_id BIGINT NULL COMMENT '合作方 ID',
  cost_type VARCHAR(50) NOT NULL COMMENT '成本类型',
  cost_subject_id BIGINT NULL COMMENT '成本科目 ID',
  amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本金额',
  source_type VARCHAR(50) NOT NULL COMMENT '来源类型',
  source_id BIGINT NOT NULL COMMENT '来源单据 ID',
  source_item_id BIGINT NULL COMMENT '来源明细 ID',
  cost_status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED' COMMENT '成本状态',
  occurred_date DATE NULL COMMENT '发生日期',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_flag TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cost_source_item (source_type, source_id, source_item_id, cost_type),
  KEY idx_cost_project (tenant_id, project_id),
  KEY idx_cost_contract (contract_id),
  KEY idx_cost_type (tenant_id, cost_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本明细表';
```

### 6.4 审批实例表 `wf_instance`

```sql
CREATE TABLE wf_instance (
  id BIGINT NOT NULL COMMENT '审批实例 ID',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  template_id BIGINT NOT NULL COMMENT '模板 ID',
  business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
  business_id BIGINT NOT NULL COMMENT '业务 ID',
  project_id BIGINT NULL COMMENT '项目 ID',
  contract_id BIGINT NULL COMMENT '合同 ID',
  title VARCHAR(300) NOT NULL COMMENT '审批标题',
  amount DECIMAL(18,2) NULL COMMENT '审批金额',
  instance_status VARCHAR(50) NOT NULL DEFAULT 'RUNNING' COMMENT '实例状态',
  current_round INT NOT NULL DEFAULT 1 COMMENT '当前轮次',
  resubmit_count INT NOT NULL DEFAULT 0 COMMENT '重新提交次数',
  business_revision INT NOT NULL DEFAULT 1 COMMENT '业务版本',
  initiator_id BIGINT NOT NULL COMMENT '发起人',
  business_summary JSON NULL COMMENT '业务摘要',
  variables JSON NULL COMMENT '流程变量',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  ended_at DATETIME NULL COMMENT '结束时间',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_flag TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_wf_instance_business (business_type, business_id),
  KEY idx_wf_instance_status (tenant_id, instance_status),
  KEY idx_wf_instance_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例表';
```

### 6.5 审批任务表 `wf_task`

```sql
CREATE TABLE wf_task (
  id BIGINT NOT NULL COMMENT '审批任务 ID',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  instance_id BIGINT NOT NULL COMMENT '审批实例 ID',
  node_instance_id BIGINT NOT NULL COMMENT '节点实例 ID',
  business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
  business_id BIGINT NOT NULL COMMENT '业务 ID',
  round_no INT NOT NULL COMMENT '审批轮次',
  approver_id BIGINT NOT NULL COMMENT '审批人 ID',
  task_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  action_type VARCHAR(50) NULL COMMENT '处理动作',
  comment VARCHAR(1000) NULL COMMENT '处理意见',
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  handled_at DATETIME NULL COMMENT '处理时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_flag TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_wf_task_approver_status (tenant_id, approver_id, task_status),
  KEY idx_wf_task_instance (instance_id),
  KEY idx_wf_task_node (node_instance_id),
  KEY idx_wf_task_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务表';
```

## 7. 审批 JSON 字段

```sql
pass_rule_json JSON NULL COMMENT '节点通过规则',
reject_rule_json JSON NULL COMMENT '节点驳回规则',
form_schema JSON NULL COMMENT '动态表单结构',
condition_rule JSON NULL COMMENT '流程匹配条件',
node_config JSON NULL COMMENT '节点扩展配置'
```

## 8. Flyway 迁移规范

建议目录：

```text
backend/src/main/resources/db/migration
```

命名规范：

```text
V1__init_base_tables.sql
V2__init_business_tables.sql
V3__init_workflow_tables.sql
V4__init_indexes.sql
V5__init_dict_data.sql
V6__add_cost_idempotent_index.sql
```

禁止修改已上线版本脚本。新增结构必须创建新 migration。

## 9. 字典初始化

必须初始化以下字典：

```text
contract_type
contract_status
approval_status
business_type
action_type
node_status
task_status
cost_type
payment_type
settlement_status
archive_status
risk_level
partner_type
material_unit
```

示例：

```sql
INSERT INTO sys_dict_type(id, dict_code, dict_name, status)
VALUES (1001, 'contract_type', '合同类型', 'NORMAL');

INSERT INTO sys_dict_item(id, dict_code, item_code, item_name, sort_no, status)
VALUES
(100101, 'contract_type', 'GENERAL_CONTRACT', '总包合同', 1, 'NORMAL'),
(100102, 'contract_type', 'SUBCONTRACT', '分包合同', 2, 'NORMAL'),
(100103, 'contract_type', 'PURCHASE', '采购合同', 3, 'NORMAL'),
(100104, 'contract_type', 'LEASE', '租赁合同', 4, 'NORMAL'),
(100105, 'contract_type', 'SERVICE', '服务合同', 5, 'NORMAL');
```

## 10. 索引原则

| 场景 | 索引建议 |
|---|---|
| 租户隔离 | `tenant_id` 参与高频查询索引 |
| 项目视图 | `project_id` 必须建索引 |
| 合同引用 | `contract_id` 必须建索引 |
| 合作方查询 | `partner_id`、`partner_name` |
| 审批待办 | `tenant_id + approver_id + task_status` |
| 审批业务反查 | `business_type + business_id` |
| 成本幂等 | `source_type + source_id + source_item_id + cost_type` |
| 逻辑删除 | 高频查询条件统一带 `deleted_flag = 0` |

## 11. 交付要求

数据库交付物必须包括：

```text
可执行 Flyway 脚本
初始化字典数据
测试环境初始化说明
回滚策略说明
索引说明
数据备份脚本
慢 SQL 检查规则
```
