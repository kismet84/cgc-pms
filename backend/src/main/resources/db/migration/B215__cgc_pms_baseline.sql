
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `account_receivable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account_receivable` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `receivable_type` varchar(32) NOT NULL,
  `receivable_code` varchar(64) NOT NULL,
  `original_amount` decimal(18,2) NOT NULL,
  `collected_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `credited_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `outstanding_amount` decimal(18,2) NOT NULL,
  `due_date` date NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receivable_code` (`tenant_id`,`receivable_code`,`deleted_flag`),
  UNIQUE KEY `uk_receivable_settlement_type` (`tenant_id`,`settlement_id`,`receivable_type`,`deleted_flag`),
  KEY `idx_receivable_due` (`tenant_id`,`status`,`due_date`),
  KEY `fk_receivable_project` (`project_id`),
  KEY `fk_receivable_contract` (`contract_id`),
  KEY `fk_receivable_settlement` (`settlement_id`),
  KEY `fk_receivable_customer` (`customer_id`),
  CONSTRAINT `fk_receivable_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_receivable_customer` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_receivable_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_receivable_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `owner_settlement` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_receivable_amount` CHECK (((`original_amount` > 0) and (`collected_amount` >= 0) and (`credited_amount` >= 0) and (`outstanding_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `accounting_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounting_entry` (
  `id` bigint NOT NULL COMMENT '凭证ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `entry_code` varchar(64) NOT NULL COMMENT '凭证号',
  `entry_date` date NOT NULL COMMENT '凭证日期',
  `entry_type` varchar(50) NOT NULL COMMENT 'BID_COST/MATERIAL/LABOR/OVERHEAD/REVENUE/SETTLEMENT',
  `source_type` varchar(50) NOT NULL COMMENT '来源类型（与cost_item.source_type对应）',
  `source_id` bigint NOT NULL COMMENT '来源单据ID',
  `entry_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED/REVERSED',
  `total_debit` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_credit` decimal(18,2) NOT NULL DEFAULT '0.00',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `contract_id` bigint DEFAULT NULL,
  `pay_application_id` bigint DEFAULT NULL,
  `pay_record_id` bigint DEFAULT NULL,
  `posted_at` datetime DEFAULT NULL,
  `reversed_at` datetime DEFAULT NULL,
  `reversed_entry_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `external_sync_status` varchar(32) DEFAULT NULL,
  `external_sync_at` datetime DEFAULT NULL,
  `collection_record_id` bigint DEFAULT NULL,
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `posted_by` bigint DEFAULT NULL,
  `period_id` bigint DEFAULT NULL,
  `adjustment_flag` tinyint NOT NULL DEFAULT '0',
  `original_entry_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entry_code` (`tenant_id`,`entry_code`,`deleted_flag`),
  UNIQUE KEY `uk_entry_pay_record` (`tenant_id`,`pay_record_id`,`entry_type`,`deleted_flag`),
  UNIQUE KEY `uk_entry_collection` (`tenant_id`,`collection_record_id`,`entry_type`,`deleted_flag`),
  KEY `idx_entry_source` (`source_type`,`source_id`),
  KEY `idx_entry_date` (`entry_date`),
  KEY `fk_entry_project` (`project_id`),
  KEY `fk_entry_contract` (`contract_id`),
  KEY `fk_entry_pay_application` (`pay_application_id`),
  KEY `fk_entry_pay_record` (`pay_record_id`),
  KEY `fk_entry_reversed_entry` (`reversed_entry_id`),
  KEY `fk_entry_collection` (`collection_record_id`),
  KEY `idx_accounting_entry_period` (`tenant_id`,`entry_date`,`entry_status`,`review_status`),
  KEY `fk_accounting_entry_period` (`period_id`),
  KEY `fk_accounting_entry_original` (`original_entry_id`),
  CONSTRAINT `fk_accounting_entry_original` FOREIGN KEY (`original_entry_id`) REFERENCES `accounting_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_accounting_entry_period` FOREIGN KEY (`period_id`) REFERENCES `finance_period` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_collection` FOREIGN KEY (`collection_record_id`) REFERENCES `collection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_pay_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_pay_record` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_entry_reversed_entry` FOREIGN KEY (`reversed_entry_id`) REFERENCES `accounting_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_entry_credit_non_neg` CHECK ((`total_credit` >= 0)),
  CONSTRAINT `chk_entry_debit_non_neg` CHECK ((`total_debit` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会计凭证主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `accounting_entry_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounting_entry_line` (
  `id` bigint NOT NULL COMMENT '分录行ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `entry_id` bigint NOT NULL COMMENT '凭证ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT '行号',
  `direction` varchar(10) NOT NULL COMMENT 'DEBIT借方 / CREDIT贷方',
  `cost_subject_id` bigint DEFAULT NULL,
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `account_code` varchar(64) DEFAULT NULL,
  `account_name` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entry_line_no` (`tenant_id`,`entry_id`,`line_no`,`deleted_flag`),
  KEY `idx_entry_line` (`entry_id`),
  KEY `idx_entry_line_subject` (`cost_subject_id`),
  CONSTRAINT `fk_entry_line_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_entry_line_amount_positive` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_entry_line_direction` CHECK ((`direction` in (_utf8mb4'DEBIT',_utf8mb4'CREDIT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会计凭证明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `alert_lifecycle_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_lifecycle_event` (
  `id` bigint NOT NULL COMMENT '生命周期事件ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `alert_id` bigint NOT NULL COMMENT '预警ID',
  `event_type` varchar(50) NOT NULL COMMENT 'CREATED/READ/ACKNOWLEDGED/ESCALATED_L1/ESCALATED_L2/STATUS_CHANGED/AUTO_ARCHIVED',
  `from_status` varchar(20) DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(20) DEFAULT NULL COMMENT '目标状态',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `remark` varchar(500) DEFAULT NULL COMMENT '操作说明',
  `occurred_at` datetime NOT NULL COMMENT '发生时间',
  `payload_json` text NOT NULL COMMENT '事件快照JSON',
  `payload_hash` char(64) NOT NULL COMMENT '事件快照SHA-256',
  PRIMARY KEY (`id`),
  KEY `fk_alert_lifecycle_alert` (`alert_id`),
  KEY `idx_alert_lifecycle_trace` (`tenant_id`,`alert_id`,`occurred_at`,`id`),
  CONSTRAINT `fk_alert_lifecycle_alert` FOREIGN KEY (`alert_id`) REFERENCES `alert_log` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警不可变生命周期事件';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `alert_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_log` (
  `id` bigint NOT NULL COMMENT '预警ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `alert_domain` varchar(50) DEFAULT NULL COMMENT '预警业务分类',
  `alert_category` varchar(50) DEFAULT NULL COMMENT '细分类标签',
  `source_type` varchar(50) DEFAULT NULL COMMENT '来源业务类型',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务ID',
  `dedup_key` varchar(200) DEFAULT NULL COMMENT '去重键',
  `rule_type` varchar(100) NOT NULL COMMENT '预警规则类型',
  `severity` varchar(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度：HIGH高，MEDIUM中，LOW低',
  `message` text COMMENT '预警消息内容',
  `triggered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：0未读，1已读',
  `read_by` bigint DEFAULT NULL COMMENT '首次阅读人',
  `read_at` datetime DEFAULT NULL COMMENT '首次阅读时间',
  `acknowledged_by` bigint DEFAULT NULL COMMENT '接单责任人',
  `acknowledged_at` datetime DEFAULT NULL COMMENT '接单时间',
  `response_due_at` datetime DEFAULT NULL COMMENT '响应期限',
  `resolution_due_at` datetime DEFAULT NULL COMMENT '处置期限',
  `escalation_level` int NOT NULL DEFAULT '0' COMMENT '升级级别：0未升级/1响应超时/2处置超时',
  `last_escalated_at` datetime DEFAULT NULL COMMENT '最近升级时间',
  `process_status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN/PROCESSED/ARCHIVED/INVALID',
  `processed_at` datetime DEFAULT NULL COMMENT '处理时间',
  `processed_by` bigint DEFAULT NULL COMMENT '处理人',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `archived_by` bigint DEFAULT NULL COMMENT '归档/失效操作人',
  `status_remark` varchar(500) DEFAULT NULL COMMENT '状态备注',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_alert_project` (`project_id`),
  KEY `idx_alert_tenant` (`tenant_id`),
  KEY `idx_alert_read` (`is_read`),
  KEY `idx_alert_triggered` (`triggered_at`),
  KEY `idx_alert_contract` (`contract_id`),
  KEY `idx_alert_domain` (`alert_domain`),
  KEY `idx_alert_source` (`source_type`,`source_id`),
  KEY `idx_alert_list_filter` (`tenant_id`,`project_id`,`rule_type`,`alert_domain`,`is_read`,`triggered_at`),
  KEY `idx_alert_process_status` (`process_status`),
  KEY `idx_alert_dedup_window` (`tenant_id`,`dedup_key`,`process_status`,`triggered_at`),
  KEY `idx_alert_ack_due` (`tenant_id`,`process_status`,`acknowledged_at`,`response_due_at`),
  KEY `idx_alert_escalation_due` (`tenant_id`,`process_status`,`escalation_level`,`resolution_due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `alert_notification_send_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_notification_send_record` (
  `id` bigint NOT NULL COMMENT '发送记录ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `alert_id` bigint NOT NULL COMMENT '预警ID',
  `event_type` varchar(50) NOT NULL COMMENT '事件类型：ALERT_CREATED / STATUS_CHANGED',
  `channel` varchar(50) NOT NULL COMMENT '渠道：IN_APP / EMAIL / WECHAT',
  `target_user_id` bigint DEFAULT NULL COMMENT '目标用户ID',
  `biz_notification_id` bigint DEFAULT NULL COMMENT '站内信ID',
  `send_status` varchar(50) NOT NULL COMMENT '发送状态：SENT / SKIPPED / FAILED',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败或跳过原因',
  `requested_at` datetime NOT NULL COMMENT '请求发送时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_ansr_alert_event` (`tenant_id`,`alert_id`,`event_type`),
  KEY `idx_ansr_channel_status` (`tenant_id`,`channel`,`send_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警通知渠道发送记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `alert_rule_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_rule_config` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `rule_type` varchar(100) NOT NULL COMMENT '规则类型',
  `alert_domain` varchar(50) NOT NULL COMMENT '业务域',
  `alert_category` varchar(50) NOT NULL COMMENT '细分类标签',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用',
  `dedup_hours` int NOT NULL DEFAULT '24' COMMENT '去重窗口小时数',
  `window_days` int DEFAULT NULL COMMENT '规则窗口天数',
  `threshold_ratio` decimal(10,4) DEFAULT NULL COMMENT '阈值比例',
  `severity_override` varchar(20) DEFAULT NULL COMMENT '严重度覆盖',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_rule_config` (`tenant_id`,`rule_type`),
  KEY `idx_alert_rule_config_enabled` (`tenant_id`,`enabled`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警规则配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `approval_routing_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_routing_rule` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `rule_name` varchar(200) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `min_amount` decimal(18,2) DEFAULT NULL,
  `max_amount` decimal(18,2) DEFAULT NULL,
  `contract_type` varchar(64) DEFAULT NULL,
  `expense_category` varchar(64) DEFAULT NULL,
  `workflow_template_id` bigint NOT NULL,
  `priority` int NOT NULL DEFAULT '100',
  `enabled_flag` tinyint NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `rule_signature` varchar(512) NOT NULL DEFAULT '' COMMENT '标准化匹配条件签名',
  `active_rule_token` bigint NOT NULL DEFAULT '0' COMMENT '启用规则为0，停用规则为自身ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_routing_exact_active` (`tenant_id`,`rule_signature`,`priority`,`active_rule_token`),
  KEY `idx_approval_routing_match` (`tenant_id`,`business_type`,`enabled_flag`,`priority`),
  KEY `fk_approval_routing_template` (`workflow_template_id`),
  KEY `fk_approval_routing_template_195` (`tenant_id`,`workflow_template_id`),
  CONSTRAINT `fk_approval_routing_template_195` FOREIGN KEY (`tenant_id`, `workflow_template_id`) REFERENCES `wf_template` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_approval_routing_enabled_195` CHECK ((`enabled_flag` in (0,1))),
  CONSTRAINT `ck_approval_routing_priority_195` CHECK ((`priority` >= 0)),
  CONSTRAINT `ck_approval_routing_range_195` CHECK ((((`min_amount` is null) or (`min_amount` >= 0)) and ((`max_amount` is null) or (`max_amount` >= 0)) and ((`min_amount` is null) or (`max_amount` is null) or (`min_amount` <= `max_amount`)))),
  CONSTRAINT `ck_approval_routing_token_195` CHECK ((((`enabled_flag` = 1) and (`active_rule_token` = 0)) or ((`enabled_flag` = 0) and (`active_rule_token` = `id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bank_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bank_receipt` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `endpoint_id` bigint NOT NULL,
  `bank_txn_no` varchar(128) NOT NULL,
  `account_no_masked` varchar(64) DEFAULT NULL,
  `transaction_time` datetime NOT NULL,
  `direction` varchar(8) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `counterparty_name` varchar(200) DEFAULT NULL,
  `purpose_text` varchar(500) DEFAULT NULL,
  `match_status` varchar(32) NOT NULL DEFAULT 'UNMATCHED',
  `pay_record_id` bigint DEFAULT NULL,
  `cash_journal_id` bigint DEFAULT NULL,
  `confidence` decimal(5,4) DEFAULT NULL,
  `raw_payload_json` longtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `matched_at` datetime DEFAULT NULL,
  `collection_record_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL COMMENT '入账所属项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '入账所属合同ID',
  `customer_id` bigint DEFAULT NULL COMMENT '付款客户ID',
  `fund_account_id` bigint DEFAULT NULL COMMENT '收款资金账户ID',
  `allocation_json` longtext COMMENT '银行回单对应应收分配(JSON数组)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_receipt_txn` (`tenant_id`,`endpoint_id`,`bank_txn_no`),
  UNIQUE KEY `uk_bank_receipt_collection` (`tenant_id`,`collection_record_id`),
  KEY `idx_bank_receipt_match` (`tenant_id`,`match_status`,`transaction_time`),
  KEY `fk_bank_receipt_endpoint` (`endpoint_id`),
  KEY `fk_bank_receipt_pay` (`pay_record_id`),
  KEY `fk_bank_receipt_journal` (`cash_journal_id`),
  KEY `fk_bank_receipt_collection` (`collection_record_id`),
  KEY `idx_bank_receipt_collection_context` (`tenant_id`,`project_id`,`contract_id`,`customer_id`),
  KEY `fk_bank_receipt_project` (`project_id`),
  KEY `fk_bank_receipt_contract` (`contract_id`),
  KEY `fk_bank_receipt_customer` (`customer_id`),
  KEY `fk_bank_receipt_fund_account` (`fund_account_id`),
  CONSTRAINT `fk_bank_receipt_collection_194` FOREIGN KEY (`tenant_id`, `collection_record_id`) REFERENCES `collection_record` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_customer` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `finance_integration_endpoint` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_fund_account` FOREIGN KEY (`fund_account_id`) REFERENCES `fund_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_journal` FOREIGN KEY (`cash_journal_id`) REFERENCES `cash_journal_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_pay` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bank_receipt_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_bank_receipt_allocation_json_194` CHECK (((`allocation_json` is null) or json_valid(`allocation_json`))),
  CONSTRAINT `ck_bank_receipt_allocation_size` CHECK (((`allocation_json` is null) or (length(`allocation_json`) <= 1048576))),
  CONSTRAINT `ck_bank_receipt_context_194` CHECK (((`direction` = _utf8mb4'OUT') or ((`project_id` is null) and (`contract_id` is null) and (`customer_id` is null) and (`fund_account_id` is null)) or ((`project_id` is not null) and (`contract_id` is not null) and (`customer_id` is not null) and (`fund_account_id` is not null)))),
  CONSTRAINT `ck_bank_receipt_direction_194` CHECK ((`direction` in (_utf8mb4'IN',_utf8mb4'OUT'))),
  CONSTRAINT `ck_bank_receipt_link_194` CHECK ((((`collection_record_id` is null) or (`direction` = _utf8mb4'IN')) and ((`pay_record_id` is null) or (`direction` = _utf8mb4'OUT')))),
  CONSTRAINT `ck_bank_receipt_raw_json` CHECK ((json_valid(`raw_payload_json`) and (length(`raw_payload_json`) <= 1048576))),
  CONSTRAINT `ck_bank_receipt_status_194` CHECK ((`match_status` in (_utf8mb4'UNMATCHED',_utf8mb4'MATCHED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'IGNORED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bid_cost`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_cost` (
  `id` bigint NOT NULL COMMENT '投标项目ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint DEFAULT NULL COMMENT '中标后关联的项目ID，未中标时为NULL',
  `bid_project_name` varchar(200) NOT NULL COMMENT '投标项目名称',
  `bid_status` varchar(50) NOT NULL DEFAULT 'BIDDING' COMMENT 'BIDDING投标中/WON已中标/LOST未中标',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bid_project` (`project_id`),
  KEY `idx_bid_status` (`bid_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招投标前期费用头表 - 金额由cost_item聚合';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bid_cost_target_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_cost_target_transfer` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `bid_cost_id` bigint NOT NULL COMMENT 'æŠ•æ ‡æˆæœ¬ID',
  `project_id` bigint NOT NULL COMMENT 'é¡¹ç›®ID',
  `target_id` bigint NOT NULL COMMENT 'ç›®æ ‡æˆæœ¬ç‰ˆæœ¬ID',
  `mapping_version_id` bigint NOT NULL COMMENT 'æ˜ å°„ç‰ˆæœ¬ID',
  `transfer_code` varchar(64) NOT NULL COMMENT 'è½¬å…¥å•å·',
  `idempotency_key` varchar(128) NOT NULL COMMENT 'å¹‚ç­‰é”®',
  `total_amount` decimal(18,2) NOT NULL COMMENT 'è½¬å…¥æ€»é¢',
  `status` varchar(16) NOT NULL DEFAULT 'POSTED' COMMENT 'äº‹å®žçŠ¶æ€',
  `approval_instance_id` bigint NOT NULL COMMENT 'å®¡æ‰¹å®žä¾‹ID',
  `reversal_of_id` bigint DEFAULT NULL COMMENT 'è¢«å†²é”€äº‹å®žID',
  `posted_by` bigint NOT NULL COMMENT 'è®°è´¦äººID',
  `posted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'è®°è´¦æ—¶é—´',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bid_target_transfer_code` (`tenant_id`,`transfer_code`),
  UNIQUE KEY `uk_bid_target_transfer_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_bid_target_transfer_reversal` (`tenant_id`,`reversal_of_id`),
  KEY `idx_bid_target_transfer_trace` (`tenant_id`,`bid_cost_id`,`project_id`,`target_id`,`status`),
  KEY `fk_bid_target_transfer_bid` (`bid_cost_id`),
  KEY `fk_bid_target_transfer_project` (`project_id`),
  KEY `fk_bid_target_transfer_target` (`target_id`),
  KEY `fk_bid_target_transfer_mapping` (`mapping_version_id`),
  KEY `fk_bid_target_transfer_approval` (`approval_instance_id`),
  KEY `fk_bid_target_transfer_reversal` (`reversal_of_id`),
  CONSTRAINT `fk_bid_target_transfer_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_bid` FOREIGN KEY (`bid_cost_id`) REFERENCES `bid_cost` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_mapping` FOREIGN KEY (`mapping_version_id`) REFERENCES `cost_subject_mapping_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_reversal` FOREIGN KEY (`reversal_of_id`) REFERENCES `bid_cost_target_transfer` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_target` FOREIGN KEY (`target_id`) REFERENCES `cost_target` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_bid_target_transfer_amount` CHECK ((`total_amount` <> 0)),
  CONSTRAINT `ck_bid_target_transfer_status` CHECK ((`status` in (_utf8mb4'POSTED',_utf8mb4'REVERSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标成本转入目标成本不可变事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bid_cost_target_transfer_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_cost_target_transfer_line` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `transfer_id` bigint NOT NULL COMMENT 'è½¬å…¥äº‹å®žID',
  `source_cost_item_id` bigint NOT NULL COMMENT 'æ¥æºæˆæœ¬æ˜Žç»†ID',
  `source_subject_id` bigint NOT NULL COMMENT 'æ¥æºæˆæœ¬ç§‘ç›®ID',
  `target_subject_id` bigint NOT NULL COMMENT 'ç›®æ ‡æˆæœ¬ç§‘ç›®ID',
  `amount` decimal(18,2) NOT NULL COMMENT 'è½¬å…¥é‡‘é¢',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bid_target_transfer_source` (`tenant_id`,`transfer_id`,`source_cost_item_id`),
  KEY `idx_bid_target_transfer_line_target` (`tenant_id`,`target_subject_id`),
  KEY `fk_bid_target_transfer_line_header` (`transfer_id`),
  KEY `fk_bid_target_transfer_line_cost` (`source_cost_item_id`),
  KEY `fk_bid_target_transfer_line_source_subject` (`source_subject_id`),
  KEY `fk_bid_target_transfer_line_target_subject` (`target_subject_id`),
  CONSTRAINT `fk_bid_target_transfer_line_cost` FOREIGN KEY (`source_cost_item_id`) REFERENCES `cost_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_line_header` FOREIGN KEY (`transfer_id`) REFERENCES `bid_cost_target_transfer` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_line_source_subject` FOREIGN KEY (`source_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bid_target_transfer_line_target_subject` FOREIGN KEY (`target_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_bid_target_transfer_line_amount` CHECK ((`amount` <> 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标成本转入明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bid_deposit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_deposit` (
  `id` bigint NOT NULL COMMENT '保证金ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `bid_cost_id` bigint NOT NULL COMMENT '关联投标项目',
  `deposit_type` varchar(50) NOT NULL COMMENT 'BID投标保证金/PERFORMANCE履约保证金',
  `deposit_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `returned_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '已退回金额',
  `deposit_status` varchar(50) NOT NULL DEFAULT 'PAID' COMMENT 'PAID已缴/RETURNED已退回/FORFEITED已没收',
  `paid_date` date DEFAULT NULL,
  `returned_date` date DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bid_deposit_bid` (`bid_cost_id`),
  KEY `idx_bid_deposit_status` (`deposit_status`),
  CONSTRAINT `chk_bid_returned_amount` CHECK (((`returned_amount` is null) or ((`returned_amount` >= 0) and (`returned_amount` <= `deposit_amount`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标保证金表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_default_binding`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_default_binding` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `business_type` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
  `template_id` bigint NOT NULL COMMENT '默认模板ID',
  `template_version_id` bigint NOT NULL COMMENT '默认已发布版本ID',
  `lock_version` int NOT NULL DEFAULT '0' COMMENT '默认绑定CAS版本',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tenant_id`,`business_type`),
  KEY `idx_document_default_version` (`tenant_id`,`template_version_id`,`template_id`),
  KEY `fk_document_default_template` (`tenant_id`,`template_id`),
  CONSTRAINT `fk_document_default_template` FOREIGN KEY (`tenant_id`, `template_id`) REFERENCES `biz_document_template` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_document_default_version` FOREIGN KEY (`tenant_id`, `template_version_id`, `template_id`) REFERENCES `biz_document_template_version` (`tenant_id`, `id`, `template_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_document_default_business` CHECK ((`business_type` in (_utf8mb4'PAYMENT',_utf8mb4'SETTLEMENT'))),
  CONSTRAINT `ck_document_default_lock_version` CHECK ((`lock_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户业务类型默认模板版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_generation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_generation` (
  `id` bigint NOT NULL COMMENT '生成事实ID，雪花ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `generation_no` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '人类可读生成编号',
  `business_type` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
  `business_id` bigint NOT NULL COMMENT '源业务ID',
  `template_id` bigint NOT NULL COMMENT '实际模板ID',
  `template_version_id` bigint NOT NULL COMMENT '实际不可变版本ID',
  `schema_version` varchar(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据契约版本快照',
  `source_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化源数据SHA-256',
  `output_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '归档PDF SHA-256',
  `renderer_id` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渲染器标识',
  `renderer_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渲染器版本',
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RENDERING/SUCCEEDED/FAILED',
  `file_id` bigint DEFAULT NULL COMMENT '成功归档的sys_file.id',
  `idempotency_key` varchar(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内生成幂等键',
  `retry_of_generation_id` bigint DEFAULT NULL COMMENT '失败重试来源生成事实ID',
  `failure_code` varchar(80) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定失败码，不存堆栈',
  `requested_by` bigint NOT NULL COMMENT '请求用户',
  `requested_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
  `completed_at` datetime DEFAULT NULL COMMENT '生成完成时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_generation_no` (`tenant_id`,`generation_no`),
  UNIQUE KEY `uk_document_generation_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_document_generation_business` (`tenant_id`,`business_type`,`business_id`,`requested_at`),
  KEY `idx_document_generation_status` (`tenant_id`,`status`,`requested_at`),
  KEY `idx_document_generation_version` (`tenant_id`,`template_version_id`,`template_id`),
  KEY `idx_document_generation_file` (`file_id`),
  KEY `idx_document_generation_retry` (`retry_of_generation_id`),
  CONSTRAINT `fk_document_generation_file` FOREIGN KEY (`file_id`) REFERENCES `sys_file` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_document_generation_retry` FOREIGN KEY (`retry_of_generation_id`) REFERENCES `biz_document_generation` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_document_generation_version` FOREIGN KEY (`tenant_id`, `template_version_id`, `template_id`) REFERENCES `biz_document_template_version` (`tenant_id`, `id`, `template_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_document_generation_business` CHECK ((`business_type` in (_utf8mb4'PAYMENT',_utf8mb4'SETTLEMENT'))),
  CONSTRAINT `ck_document_generation_completion` CHECK ((((`status` = _utf8mb4'SUCCEEDED') and (`file_id` is not null) and (`output_sha256` is not null) and (`completed_at` is not null) and (`failure_code` is null)) or ((`status` = _utf8mb4'FAILED') and (`file_id` is null) and (`output_sha256` is null) and (`completed_at` is not null) and (`failure_code` is not null)) or ((`status` in (_utf8mb4'PENDING',_utf8mb4'RENDERING')) and (`file_id` is null) and (`output_sha256` is null) and (`completed_at` is null) and (`failure_code` is null)))),
  CONSTRAINT `ck_document_generation_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'RENDERING',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可审计PDF生成事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_template` (
  `id` bigint NOT NULL COMMENT '模板ID，雪花ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `template_code` varchar(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内稳定模板编码',
  `template_name` varchar(200) NOT NULL COMMENT '模板名称',
  `business_type` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
  `engine_type` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HTML_PDF' COMMENT '受限HTML/CSS转PDF',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许继续维护和发布',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动模板唯一键辅助列',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_template_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_document_template_code` (`tenant_id`,`template_code`,`active_unique_token`),
  KEY `idx_document_template_business` (`tenant_id`,`business_type`,`enabled`,`deleted_flag`),
  CONSTRAINT `ck_document_template_business` CHECK ((`business_type` in (_utf8mb4'PAYMENT',_utf8mb4'SETTLEMENT'))),
  CONSTRAINT `ck_document_template_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_document_template_engine` CHECK ((`engine_type` = _utf8mb4'HTML_PDF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务单据模板定义';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_template_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_template_version` (
  `id` bigint NOT NULL COMMENT '模板版本ID，雪花ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `version_no` int NOT NULL COMMENT '从1递增的版本号',
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
  `schema_version` varchar(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据契约版本',
  `template_content` mediumtext NOT NULL COMMENT '受限HTML/CSS模板正文',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板正文SHA-256',
  `field_manifest` json NOT NULL COMMENT '允许字段清单快照',
  `published_by` bigint DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_template_version_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_document_template_version_owner` (`tenant_id`,`id`,`template_id`),
  UNIQUE KEY `uk_document_template_version_no` (`tenant_id`,`template_id`,`version_no`),
  KEY `idx_document_template_version_status` (`tenant_id`,`template_id`,`status`,`version_no`),
  CONSTRAINT `fk_document_template_version_template` FOREIGN KEY (`tenant_id`, `template_id`) REFERENCES `biz_document_template` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_document_template_publish_metadata` CHECK ((((`status` = _utf8mb4'DRAFT') and (`published_by` is null) and (`published_at` is null)) or ((`status` in (_utf8mb4'PUBLISHED',_utf8mb4'DISABLED')) and (`published_by` is not null) and (`published_at` is not null)))),
  CONSTRAINT `ck_document_template_version_no` CHECK ((`version_no` > 0)),
  CONSTRAINT `ck_document_template_version_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变发布的业务单据模板版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `budget_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `budget_ledger` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `budget_id` bigint NOT NULL,
  `budget_line_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `entry_type` varchar(32) NOT NULL COMMENT 'RESERVE/RELEASE/CONSUME/REVERSE/ADJUST',
  `amount` decimal(18,2) NOT NULL,
  `reserved_balance` decimal(18,2) NOT NULL,
  `consumed_balance` decimal(18,2) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget_ledger_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `fk_budget_ledger_budget` (`budget_id`),
  KEY `fk_budget_ledger_line` (`budget_line_id`),
  KEY `fk_budget_ledger_project` (`project_id`),
  KEY `idx_budget_ledger_business` (`tenant_id`,`business_type`,`business_id`),
  KEY `idx_budget_ledger_line_time` (`tenant_id`,`budget_line_id`,`created_at`),
  CONSTRAINT `fk_budget_ledger_budget` FOREIGN KEY (`budget_id`) REFERENCES `project_budget` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_ledger_line` FOREIGN KEY (`budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_ledger_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变预算占用与消耗台账';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `budget_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `budget_operation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `operation_type` varchar(32) NOT NULL,
  `project_id` bigint NOT NULL,
  `from_budget_line_id` bigint DEFAULT NULL,
  `to_budget_line_id` bigint DEFAULT NULL,
  `contract_allocation_id` bigint DEFAULT NULL,
  `amount` decimal(18,2) NOT NULL,
  `status` varchar(32) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget_operation_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_budget_operation_project` (`tenant_id`,`project_id`,`created_at`),
  KEY `fk_budget_operation_project` (`project_id`),
  KEY `fk_budget_operation_from_line` (`from_budget_line_id`),
  KEY `fk_budget_operation_to_line` (`to_budget_line_id`),
  KEY `fk_budget_operation_allocation` (`contract_allocation_id`),
  CONSTRAINT `fk_budget_operation_allocation` FOREIGN KEY (`contract_allocation_id`) REFERENCES `contract_budget_allocation` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_operation_from_line` FOREIGN KEY (`from_budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_operation_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_operation_to_line` FOREIGN KEY (`to_budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `business_matter_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `business_matter_registry` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `matter_key` varchar(100) NOT NULL,
  `source_type` varchar(30) NOT NULL,
  `source_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `active_token` tinyint DEFAULT '1',
  `resolved_at` datetime DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `resolution_note` varchar(500) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_matter_active` (`tenant_id`,`project_id`,`matter_key`,`active_token`),
  UNIQUE KEY `uk_business_matter_source` (`tenant_id`,`source_type`,`source_id`,`active_token`),
  KEY `fk_business_matter_project` (`project_id`),
  KEY `fk_business_matter_contract` (`contract_id`),
  KEY `idx_business_matter_contract` (`tenant_id`,`contract_id`,`status`),
  CONSTRAINT `fk_business_matter_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_business_matter_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同变更与现场签证跨域事项唯一登记';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_forecast`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_forecast` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint DEFAULT NULL,
  `forecast_date` date NOT NULL,
  `scenario` varchar(32) NOT NULL,
  `inflow_amount` decimal(18,2) NOT NULL,
  `outflow_amount` decimal(18,2) NOT NULL,
  `financing_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `confidence` decimal(5,4) NOT NULL DEFAULT '1.0000',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cash_forecast_date` (`tenant_id`,`scenario`,`forecast_date`),
  KEY `fk_cash_forecast_project` (`project_id`),
  CONSTRAINT `fk_cash_forecast_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_forecast_cycle`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_forecast_cycle` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `cycle_code` varchar(64) NOT NULL,
  `forecast_name` varchar(200) NOT NULL,
  `as_of_date` date NOT NULL,
  `horizon_start` date NOT NULL,
  `horizon_end` date NOT NULL,
  `scenario` varchar(32) NOT NULL,
  `opening_balance` decimal(18,2) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `version_no` int NOT NULL,
  `previous_cycle_id` bigint DEFAULT NULL,
  `source_cutoff_at` datetime NOT NULL,
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `approval_comment` varchar(500) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_forecast_cycle_code` (`tenant_id`,`cycle_code`),
  UNIQUE KEY `uk_cash_forecast_cycle_version` (`tenant_id`,`project_id`,`scenario`,`version_no`),
  KEY `idx_cash_forecast_cycle_active` (`tenant_id`,`project_id`,`scenario`,`status`,`as_of_date`),
  KEY `fk_cash_forecast_cycle_project` (`project_id`),
  KEY `fk_cash_forecast_cycle_previous` (`previous_cycle_id`),
  CONSTRAINT `fk_cash_forecast_cycle_previous` FOREIGN KEY (`previous_cycle_id`) REFERENCES `cash_forecast_cycle` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_forecast_cycle_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cash_forecast_cycle_dates` CHECK (((`as_of_date` <= `horizon_start`) and (`horizon_start` <= `horizon_end`))),
  CONSTRAINT `ck_cash_forecast_cycle_opening` CHECK ((`opening_balance` >= 0)),
  CONSTRAINT `ck_cash_forecast_cycle_scenario` CHECK ((`scenario` in (_utf8mb4'BASE',_utf8mb4'OPTIMISTIC',_utf8mb4'CONSERVATIVE'))),
  CONSTRAINT `ck_cash_forecast_cycle_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'APPROVED',_utf8mb4'SUPERSEDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_forecast_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_forecast_line` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `cycle_id` bigint NOT NULL,
  `forecast_date` date NOT NULL,
  `planned_inflow` decimal(18,2) NOT NULL DEFAULT '0.00',
  `planned_outflow` decimal(18,2) NOT NULL DEFAULT '0.00',
  `financing_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `projected_balance` decimal(18,2) NOT NULL,
  `gap_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `actual_inflow` decimal(18,2) NOT NULL DEFAULT '0.00',
  `actual_outflow` decimal(18,2) NOT NULL DEFAULT '0.00',
  `inflow_variance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `outflow_variance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `source_summary_json` json DEFAULT NULL,
  `actual_refreshed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_forecast_line` (`tenant_id`,`cycle_id`,`forecast_date`),
  KEY `fk_cash_forecast_line_cycle` (`cycle_id`),
  CONSTRAINT `fk_cash_forecast_line_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `cash_forecast_cycle` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cash_forecast_line_amounts` CHECK (((`planned_inflow` >= 0) and (`planned_outflow` >= 0) and (`financing_amount` >= 0) and (`gap_amount` >= 0) and (`actual_inflow` >= 0) and (`actual_outflow` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_funding_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_funding_action` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `cycle_id` bigint NOT NULL,
  `line_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `planned_date` date NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PROPOSED',
  `source_type` varchar(32) DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `completed_by` bigint DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `actual_amount` decimal(18,2) DEFAULT NULL,
  `completion_reference` varchar(128) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cash_funding_action` (`tenant_id`,`cycle_id`,`status`,`planned_date`),
  KEY `fk_cash_funding_action_cycle` (`cycle_id`),
  KEY `fk_cash_funding_action_line` (`line_id`),
  KEY `fk_cash_funding_action_project` (`project_id`),
  CONSTRAINT `fk_cash_funding_action_cycle` FOREIGN KEY (`cycle_id`) REFERENCES `cash_forecast_cycle` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_funding_action_line` FOREIGN KEY (`line_id`) REFERENCES `cash_forecast_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_funding_action_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cash_funding_action_amount` CHECK (((`amount` > 0) and ((`actual_amount` is null) or (`actual_amount` > 0)))),
  CONSTRAINT `ck_cash_funding_action_status` CHECK ((`status` in (_utf8mb4'PROPOSED',_utf8mb4'SUBMITTED',_utf8mb4'APPROVED',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_cash_funding_action_type` CHECK ((`action_type` in (_utf8mb4'ACCELERATE_COLLECTION',_utf8mb4'DEFER_PAYMENT',_utf8mb4'FUND_TRANSFER',_utf8mb4'FINANCING')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_journal_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_journal_change_log` (
  `id` bigint NOT NULL COMMENT '变更日志ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `journal_entry_id` bigint NOT NULL COMMENT '日记账流水ID',
  `action` varchar(32) NOT NULL COMMENT 'REOPEN/UPDATE_AFTER_REOPEN/REARCHIVE/REVERSE',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `before_snapshot` json DEFAULT NULL COMMENT '变更前快照',
  `after_snapshot` json DEFAULT NULL COMMENT '变更后快照',
  `operator_id` bigint NOT NULL COMMENT '操作人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cash_journal_change_entry` (`tenant_id`,`journal_entry_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金日记账不可变变更日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_journal_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_journal_entry` (
  `id` bigint NOT NULL COMMENT '日记账流水ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `entry_no` varchar(64) NOT NULL COMMENT '流水号',
  `account_id` bigint DEFAULT NULL COMMENT '资金账户ID',
  `direction` varchar(8) NOT NULL COMMENT 'IN/OUT',
  `amount` decimal(18,2) NOT NULL COMMENT '金额',
  `business_date` date NOT NULL COMMENT '业务日期',
  `counterparty_name` varchar(200) DEFAULT NULL COMMENT '往来单位',
  `summary` varchar(500) NOT NULL COMMENT '摘要',
  `project_id` bigint DEFAULT NULL,
  `contract_id` bigint DEFAULT NULL,
  `source_type` varchar(32) NOT NULL COMMENT 'MANUAL/PAY_RECORD/REVERSAL',
  `source_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL COMMENT 'DRAFT/PENDING_ARCHIVE/ARCHIVED/REVERSED',
  `closure_due_at` datetime NOT NULL,
  `archived_by` bigint DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL,
  `reverse_of_entry_id` bigint DEFAULT NULL,
  `reversal_entry_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `pay_application_id` bigint DEFAULT NULL,
  `approval_instance_id` bigint DEFAULT NULL,
  `pay_record_id` bigint DEFAULT NULL,
  `collection_record_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_journal_entry_no` (`tenant_id`,`entry_no`,`deleted_flag`),
  UNIQUE KEY `uk_cash_journal_source` (`tenant_id`,`source_type`,`source_id`,`deleted_flag`),
  UNIQUE KEY `uk_cash_journal_pay_record` (`tenant_id`,`pay_record_id`,`deleted_flag`),
  UNIQUE KEY `uk_cash_journal_collection` (`tenant_id`,`collection_record_id`,`deleted_flag`),
  KEY `idx_cash_journal_account_date` (`tenant_id`,`account_id`,`business_date`,`id`),
  KEY `idx_cash_journal_closure` (`tenant_id`,`status`,`closure_due_at`),
  KEY `idx_cash_journal_project_contract` (`tenant_id`,`project_id`,`contract_id`),
  KEY `fk_cash_journal_pay_application` (`pay_application_id`),
  KEY `fk_cash_journal_approval_instance` (`approval_instance_id`),
  KEY `fk_cash_journal_pay_record` (`pay_record_id`),
  KEY `fk_cash_journal_account` (`account_id`),
  KEY `fk_cash_journal_project` (`project_id`),
  KEY `fk_cash_journal_contract` (`contract_id`),
  KEY `fk_cash_journal_reverse_of` (`reverse_of_entry_id`),
  KEY `fk_cash_journal_reversal` (`reversal_entry_id`),
  KEY `idx_cash_journal_finance_recon` (`tenant_id`,`project_id`,`status`,`business_date`),
  KEY `fk_cash_journal_collection` (`collection_record_id`),
  CONSTRAINT `fk_cash_journal_account` FOREIGN KEY (`account_id`) REFERENCES `fund_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_approval_instance` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_collection` FOREIGN KEY (`collection_record_id`) REFERENCES `collection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_pay_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_pay_record` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_reversal` FOREIGN KEY (`reversal_entry_id`) REFERENCES `cash_journal_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cash_journal_reverse_of` FOREIGN KEY (`reverse_of_entry_id`) REFERENCES `cash_journal_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cash_journal_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `ck_cash_journal_direction` CHECK ((`direction` in (_utf8mb4'IN',_utf8mb4'OUT'))),
  CONSTRAINT `ck_cash_journal_source_type` CHECK ((`source_type` in (_utf8mb4'MANUAL',_utf8mb4'PAY_RECORD',_utf8mb4'COLLECTION_RECORD',_utf8mb4'REVERSAL'))),
  CONSTRAINT `ck_cash_journal_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_ARCHIVE',_utf8mb4'ARCHIVED',_utf8mb4'REVERSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金日记账流水';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `closeout_archive_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closeout_archive_transfer` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `closeout_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `transfer_code` varchar(64) NOT NULL,
  `transfer_date` date NOT NULL,
  `recipient_organization` varchar(200) NOT NULL,
  `recipient_name` varchar(100) NOT NULL,
  `archive_location` varchar(300) NOT NULL,
  `transfer_scope` varchar(2000) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `accepted_by` bigint DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_closeout_archive_code` (`tenant_id`,`transfer_code`,`deleted_flag`),
  UNIQUE KEY `uk_closeout_archive_closeout` (`tenant_id`,`closeout_id`,`deleted_flag`),
  KEY `fk_closeout_archive_closeout` (`closeout_id`),
  KEY `fk_closeout_archive_project` (`project_id`),
  CONSTRAINT `fk_closeout_archive_closeout` FOREIGN KEY (`closeout_id`) REFERENCES `project_closeout` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_archive_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `closeout_defect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closeout_defect` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `closeout_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `warranty_id` bigint NOT NULL,
  `defect_code` varchar(64) NOT NULL,
  `defect_title` varchar(200) NOT NULL,
  `defect_description` varchar(2000) NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  `rectification_deadline` date NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `rectification_content` varchar(2000) DEFAULT NULL,
  `rectified_by` bigint DEFAULT NULL,
  `rectified_at` datetime DEFAULT NULL,
  `verified_by` bigint DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `verification_comment` varchar(1000) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_closeout_defect_code` (`tenant_id`,`defect_code`,`deleted_flag`),
  KEY `idx_closeout_defect_status` (`tenant_id`,`closeout_id`,`status`),
  KEY `fk_closeout_defect_closeout` (`closeout_id`),
  KEY `fk_closeout_defect_project` (`project_id`),
  KEY `fk_closeout_defect_warranty` (`warranty_id`),
  CONSTRAINT `fk_closeout_defect_closeout` FOREIGN KEY (`closeout_id`) REFERENCES `project_closeout` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_defect_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_defect_warranty` FOREIGN KEY (`warranty_id`) REFERENCES `closeout_warranty` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `closeout_final_acceptance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closeout_final_acceptance` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `closeout_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `acceptance_code` varchar(64) NOT NULL,
  `acceptance_date` date NOT NULL,
  `organizer` varchar(200) NOT NULL,
  `participant_summary` varchar(1000) NOT NULL,
  `conclusion` varchar(32) NOT NULL,
  `acceptance_summary` varchar(2000) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_closeout_final_closeout` (`tenant_id`,`closeout_id`,`deleted_flag`),
  UNIQUE KEY `uk_closeout_final_code` (`tenant_id`,`acceptance_code`,`deleted_flag`),
  KEY `fk_closeout_final_closeout` (`closeout_id`),
  KEY `fk_closeout_final_project` (`project_id`),
  KEY `fk_closeout_final_approval` (`approval_instance_id`),
  CONSTRAINT `fk_closeout_final_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_final_closeout` FOREIGN KEY (`closeout_id`) REFERENCES `project_closeout` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_final_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `closeout_section_acceptance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closeout_section_acceptance` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `closeout_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `wbs_task_id` bigint NOT NULL,
  `quality_inspection_id` bigint NOT NULL,
  `acceptance_code` varchar(64) NOT NULL,
  `acceptance_name` varchar(200) NOT NULL,
  `acceptance_date` date NOT NULL,
  `conclusion` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_closeout_section_code` (`tenant_id`,`acceptance_code`,`deleted_flag`),
  UNIQUE KEY `uk_closeout_section_wbs` (`tenant_id`,`closeout_id`,`wbs_task_id`,`deleted_flag`),
  KEY `fk_closeout_section_closeout` (`closeout_id`),
  KEY `fk_closeout_section_project` (`project_id`),
  KEY `fk_closeout_section_wbs` (`wbs_task_id`),
  KEY `fk_closeout_section_quality` (`quality_inspection_id`),
  CONSTRAINT `fk_closeout_section_closeout` FOREIGN KEY (`closeout_id`) REFERENCES `project_closeout` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_section_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_section_quality` FOREIGN KEY (`quality_inspection_id`) REFERENCES `qs_inspection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_section_wbs` FOREIGN KEY (`wbs_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `closeout_warranty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closeout_warranty` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `closeout_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `receivable_id` bigint NOT NULL,
  `warranty_code` varchar(64) NOT NULL,
  `warranty_amount` decimal(18,2) NOT NULL,
  `warranty_start_date` date NOT NULL,
  `warranty_end_date` date NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `released_by` bigint DEFAULT NULL,
  `released_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_closeout_warranty_code` (`tenant_id`,`warranty_code`,`deleted_flag`),
  UNIQUE KEY `uk_closeout_warranty_receivable` (`tenant_id`,`receivable_id`,`deleted_flag`),
  KEY `fk_closeout_warranty_closeout` (`closeout_id`),
  KEY `fk_closeout_warranty_project` (`project_id`),
  KEY `fk_closeout_warranty_contract` (`contract_id`),
  KEY `fk_closeout_warranty_receivable` (`receivable_id`),
  CONSTRAINT `fk_closeout_warranty_closeout` FOREIGN KEY (`closeout_id`) REFERENCES `project_closeout` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_warranty_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_warranty_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_closeout_warranty_receivable` FOREIGN KEY (`receivable_id`) REFERENCES `account_receivable` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_closeout_warranty_amount` CHECK ((`warranty_amount` > 0)),
  CONSTRAINT `ck_closeout_warranty_dates` CHECK ((`warranty_end_date` >= `warranty_start_date`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `collection_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_allocation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `collection_id` bigint NOT NULL,
  `receivable_id` bigint NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL,
  `allocation_type` varchar(32) NOT NULL DEFAULT 'COLLECTION',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_receivable` (`tenant_id`,`collection_id`,`receivable_id`,`allocation_type`),
  KEY `fk_collection_alloc_collection` (`collection_id`),
  KEY `fk_collection_alloc_receivable` (`receivable_id`),
  CONSTRAINT `fk_collection_alloc_collection` FOREIGN KEY (`collection_id`) REFERENCES `collection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_alloc_receivable` FOREIGN KEY (`receivable_id`) REFERENCES `account_receivable` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collection_alloc_amount` CHECK ((`allocated_amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `collection_forecast`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_forecast` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `forecast_date` date NOT NULL,
  `scenario` varchar(32) NOT NULL,
  `expected_amount` decimal(18,2) NOT NULL,
  `confidence` decimal(5,4) NOT NULL DEFAULT '1.0000',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_collection_forecast` (`tenant_id`,`scenario`,`forecast_date`),
  KEY `fk_collection_forecast_project` (`project_id`),
  KEY `fk_collection_forecast_contract` (`contract_id`),
  CONSTRAINT `fk_collection_forecast_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_forecast_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `collection_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_record` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `fund_account_id` bigint NOT NULL,
  `collection_code` varchar(64) NOT NULL,
  `external_txn_no` varchar(128) NOT NULL,
  `collected_at` datetime NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `unallocated_amount` decimal(18,2) NOT NULL,
  `payer_name` varchar(200) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS',
  `reversed_at` datetime DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `attachment_count` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_code` (`tenant_id`,`collection_code`,`deleted_flag`),
  UNIQUE KEY `uk_collection_external_txn` (`tenant_id`,`external_txn_no`,`deleted_flag`),
  UNIQUE KEY `uk_collection_tenant_id` (`tenant_id`,`id`),
  KEY `idx_collection_contract` (`tenant_id`,`contract_id`,`collected_at`),
  KEY `fk_collection_project` (`project_id`),
  KEY `fk_collection_contract` (`contract_id`),
  KEY `fk_collection_customer` (`customer_id`),
  KEY `fk_collection_account` (`fund_account_id`),
  CONSTRAINT `fk_collection_account` FOREIGN KEY (`fund_account_id`) REFERENCES `fund_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_customer` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_collection_amount` CHECK (((`amount` > 0) and (`allocated_amount` >= 0) and (`unallocated_amount` >= 0) and (`amount` = (`allocated_amount` + `unallocated_amount`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `collection_reversal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_reversal` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `collection_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_reversal_key` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_collection_reversal_record` (`tenant_id`,`collection_id`),
  KEY `fk_collection_reversal_record` (`collection_id`),
  CONSTRAINT `fk_collection_reversal_record` FOREIGN KEY (`collection_id`) REFERENCES `collection_record` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `collection_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_schedule` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `receivable_id` bigint DEFAULT NULL,
  `planned_date` date NOT NULL,
  `planned_amount` decimal(18,2) NOT NULL,
  `collected_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `reminder_days` int NOT NULL DEFAULT '7',
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED',
  `note` varchar(500) NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_collection_schedule_due` (`tenant_id`,`status`,`planned_date`),
  KEY `fk_collection_schedule_project` (`project_id`),
  KEY `fk_collection_schedule_contract` (`contract_id`),
  KEY `fk_collection_schedule_receivable` (`receivable_id`),
  CONSTRAINT `fk_collection_schedule_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_schedule_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_collection_schedule_receivable` FOREIGN KEY (`receivable_id`) REFERENCES `account_receivable` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `contract_budget_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_budget_allocation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `budget_line_id` bigint NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL,
  `reserved_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `consumed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_budget_line` (`tenant_id`,`contract_id`,`budget_line_id`,`deleted_flag`),
  KEY `fk_contract_budget_project` (`project_id`),
  KEY `fk_contract_budget_contract` (`contract_id`),
  KEY `fk_contract_budget_line` (`budget_line_id`),
  KEY `idx_contract_budget_project` (`tenant_id`,`project_id`),
  CONSTRAINT `fk_contract_budget_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contract_budget_line` FOREIGN KEY (`budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contract_budget_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同预算科目分配';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `contract_revenue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_revenue` (
  `id` bigint NOT NULL COMMENT '收入确认ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint NOT NULL COMMENT '主合同ID（对甲方的总包合同）',
  `revenue_code` varchar(64) NOT NULL COMMENT '收入确认单号',
  `revenue_date` date NOT NULL COMMENT '收入确认日期',
  `progress_percent` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '累计履约进度(%)',
  `progress_desc` varchar(500) DEFAULT NULL COMMENT '进度描述',
  `revenue_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '本期确认收入（不含税）',
  `revenue_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '销项税额',
  `revenue_amount_with_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '含税收入',
  `billed_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '本期向业主结算金额',
  `billed_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '结算税额',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',
  `cost_item_id` bigint DEFAULT NULL COMMENT '审批通过后生成的 cost_item 记录ID',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `approval_instance_id` bigint DEFAULT NULL,
  `formula_version` varchar(64) NOT NULL DEFAULT 'REVENUE_PROGRESS_V1',
  `attachment_count` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_code` (`tenant_id`,`revenue_code`,`deleted_flag`),
  KEY `idx_revenue_contract` (`contract_id`),
  KEY `idx_revenue_date` (`revenue_date`),
  KEY `fk_contract_revenue_project` (`project_id`),
  KEY `fk_contract_revenue_approval` (`approval_instance_id`),
  CONSTRAINT `fk_contract_revenue_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contract_revenue_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contract_revenue_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_revenue_amount_non_neg` CHECK ((`revenue_amount` >= 0)),
  CONSTRAINT `chk_revenue_progress` CHECK (((`progress_percent` >= 0) and (`progress_percent` <= 100))),
  CONSTRAINT `chk_revenue_tax_non_neg` CHECK ((`revenue_tax` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业主收入确认表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_corrective_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_corrective_action` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `forecast_id` bigint NOT NULL,
  `action_code` varchar(64) NOT NULL,
  `action_title` varchar(200) NOT NULL,
  `root_cause` varchar(500) NOT NULL,
  `action_plan` varchar(1000) NOT NULL,
  `expected_saving_amount` decimal(18,2) NOT NULL,
  `actual_saving_amount` decimal(18,2) DEFAULT NULL,
  `responsible_user_id` bigint NOT NULL,
  `due_date` date NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `result_description` varchar(1000) DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_corrective_code` (`tenant_id`,`project_id`,`action_code`,`deleted_flag`),
  KEY `idx_cost_corrective_forecast` (`tenant_id`,`forecast_id`,`status`,`due_date`),
  KEY `fk_cost_corrective_project` (`project_id`),
  KEY `fk_cost_corrective_forecast` (`forecast_id`),
  KEY `fk_cost_corrective_approval` (`approval_instance_id`),
  CONSTRAINT `fk_cost_corrective_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_corrective_forecast` FOREIGN KEY (`forecast_id`) REFERENCES `cost_forecast` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_corrective_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_corrective_amount` CHECK (((`expected_saving_amount` > 0) and ((`actual_saving_amount` is null) or (`actual_saving_amount` >= 0)))),
  CONSTRAINT `ck_cost_corrective_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CLOSED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本偏差纠偏措施';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_forecast`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_forecast` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `cost_target_id` bigint NOT NULL,
  `forecast_code` varchar(64) NOT NULL,
  `forecast_name` varchar(200) NOT NULL,
  `version_no` int NOT NULL,
  `forecast_date` date NOT NULL,
  `bid_cost_amount` decimal(18,2) NOT NULL,
  `target_cost_amount` decimal(18,2) NOT NULL,
  `responsibility_amount` decimal(18,2) NOT NULL,
  `committed_cost_amount` decimal(18,2) NOT NULL,
  `actual_cost_amount` decimal(18,2) NOT NULL,
  `estimated_remaining_amount` decimal(18,2) NOT NULL,
  `forecast_at_completion_amount` decimal(18,2) NOT NULL,
  `contract_income_amount` decimal(18,2) NOT NULL,
  `forecast_profit_amount` decimal(18,2) NOT NULL,
  `cost_variance_amount` decimal(18,2) NOT NULL,
  `profit_margin` decimal(9,6) NOT NULL DEFAULT '0.000000',
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `formula_version` varchar(40) NOT NULL DEFAULT 'COST_EAC_V1',
  `confirmed_at` datetime DEFAULT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_forecast_code` (`tenant_id`,`project_id`,`forecast_code`,`deleted_flag`),
  UNIQUE KEY `uk_cost_forecast_version` (`tenant_id`,`project_id`,`version_no`,`deleted_flag`),
  KEY `idx_cost_forecast_project_status` (`tenant_id`,`project_id`,`status`,`forecast_date`),
  KEY `fk_cost_forecast_project` (`project_id`),
  KEY `fk_cost_forecast_target` (`cost_target_id`),
  CONSTRAINT `fk_cost_forecast_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_forecast_target` FOREIGN KEY (`cost_target_id`) REFERENCES `cost_target` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_forecast_amount` CHECK (((`bid_cost_amount` >= 0) and (`target_cost_amount` >= 0) and (`responsibility_amount` >= 0) and (`committed_cost_amount` >= 0) and (`actual_cost_amount` >= 0) and (`estimated_remaining_amount` >= 0) and (`forecast_at_completion_amount` >= 0))),
  CONSTRAINT `ck_cost_forecast_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTION_REQUIRED',_utf8mb4'CONTROLLED',_utf8mb4'SUPERSEDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目完工成本与利润预测版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_forecast_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_forecast_item` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `forecast_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `cost_subject_id` bigint NOT NULL,
  `bid_cost_amount` decimal(18,2) NOT NULL,
  `target_cost_amount` decimal(18,2) NOT NULL,
  `responsibility_amount` decimal(18,2) NOT NULL,
  `committed_cost_amount` decimal(18,2) NOT NULL,
  `actual_cost_amount` decimal(18,2) NOT NULL,
  `estimated_remaining_amount` decimal(18,2) NOT NULL,
  `forecast_at_completion_amount` decimal(18,2) NOT NULL,
  `cost_variance_amount` decimal(18,2) NOT NULL,
  `responsible_user_id` bigint DEFAULT NULL,
  `responsibility_unit` varchar(200) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_forecast_item` (`tenant_id`,`forecast_id`,`cost_subject_id`),
  KEY `idx_cost_forecast_item_project` (`tenant_id`,`project_id`,`cost_subject_id`),
  KEY `fk_cost_forecast_item_forecast` (`forecast_id`),
  KEY `fk_cost_forecast_item_project` (`project_id`),
  KEY `fk_cost_forecast_item_subject` (`cost_subject_id`),
  CONSTRAINT `fk_cost_forecast_item_forecast` FOREIGN KEY (`forecast_id`) REFERENCES `cost_forecast` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_forecast_item_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_forecast_item_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_forecast_item_amount` CHECK (((`bid_cost_amount` >= 0) and (`target_cost_amount` >= 0) and (`responsibility_amount` >= 0) and (`committed_cost_amount` >= 0) and (`actual_cost_amount` >= 0) and (`estimated_remaining_amount` >= 0) and (`forecast_at_completion_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完工成本预测科目快照';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_item` (
  `id` bigint NOT NULL COMMENT '成本ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方ID',
  `cost_subject_id` bigint DEFAULT NULL COMMENT '成本科目ID',
  `cost_type` varchar(50) NOT NULL COMMENT '材料/分包/机械/人工/签证/管理费等',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '成本金额',
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `amount_without_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `source_type` varchar(50) NOT NULL COMMENT '来源类型，如 MAT_RECEIPT/SUB_MEASURE/VAR_ORDER',
  `source_id` bigint NOT NULL COMMENT '来源单据主表ID',
  `source_item_id` bigint NOT NULL DEFAULT '0' COMMENT '来源单据明细ID，不按明细拆分时为0',
  `cost_date` date NOT NULL COMMENT '成本发生日期',
  `cost_status` varchar(50) NOT NULL DEFAULT 'CONFIRMED' COMMENT '暂估/已确认/已结算/已冲销',
  `generated_flag` tinyint NOT NULL DEFAULT '1' COMMENT '是否系统生成：0否，1是',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动成本事实唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_item_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_cost_source` (`tenant_id`,`source_type`,`source_id`,`source_item_id`,`cost_type`,`active_unique_token`),
  KEY `idx_cost_project` (`project_id`),
  KEY `idx_cost_contract` (`contract_id`),
  KEY `idx_cost_source` (`source_type`,`source_id`),
  KEY `idx_cost_subject` (`cost_subject_id`),
  KEY `idx_cost_date` (`cost_date`),
  KEY `idx_cost_item_tenant_project_date` (`tenant_id`,`project_id`,`cost_date`,`deleted_flag`),
  KEY `idx_cost_item_tenant_contract_date` (`tenant_id`,`contract_id`,`cost_date`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_subject` (
  `id` bigint NOT NULL COMMENT '成本科目ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父科目ID，0表示根节点',
  `subject_code` varchar(64) NOT NULL COMMENT '科目编码',
  `subject_name` varchar(200) NOT NULL COMMENT '科目名称',
  `subject_type` varchar(50) DEFAULT NULL COMMENT '科目类型：材料/分包/机械/人工/管理费等',
  `level` int NOT NULL DEFAULT '1' COMMENT '科目层级',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `account_category` varchar(20) NOT NULL DEFAULT 'COST' COMMENT '科目大类：COST成本，REVENUE收入，SETTLEMENT结算，RECEIVABLE应收',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_subject_code` (`tenant_id`,`subject_code`,`active_unique_token`),
  KEY `idx_cost_subject_parent` (`parent_id`),
  KEY `idx_cost_subject_type` (`subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本科目表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_subject_assignment_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_subject_assignment_rule` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `mapping_version_id` bigint NOT NULL COMMENT 'æ˜ å°„ç‰ˆæœ¬ID',
  `rule_code` varchar(64) NOT NULL COMMENT 'è§„åˆ™ç¼–ç ',
  `source_type` varchar(64) NOT NULL COMMENT 'ä¸šåŠ¡æ¥æºç±»åž‹',
  `business_category` varchar(64) NOT NULL DEFAULT '*' COMMENT 'ä¸šåŠ¡åˆ†ç±»',
  `project_id` bigint DEFAULT NULL COMMENT 'é¡¹ç›®ID',
  `cost_subject_id` bigint NOT NULL COMMENT 'æˆæœ¬ç§‘ç›®ID',
  `priority` int NOT NULL DEFAULT '100' COMMENT 'åŒ¹é…ä¼˜å…ˆçº§',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'è§„åˆ™çŠ¶æ€',
  `effective_from` date NOT NULL COMMENT 'ç”Ÿæ•ˆå¼€å§‹æ—¥æœŸ',
  `effective_to` date DEFAULT NULL COMMENT 'ç”Ÿæ•ˆç»“æŸæ—¥æœŸ',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬',
  `created_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_by` bigint DEFAULT NULL COMMENT 'æ›´æ–°äººID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_subject_rule_code` (`tenant_id`,`rule_code`),
  KEY `idx_cost_subject_rule_match` (`tenant_id`,`source_type`,`business_category`,`project_id`,`status`,`priority`),
  KEY `fk_cost_subject_rule_version` (`mapping_version_id`),
  KEY `fk_cost_subject_rule_project` (`project_id`),
  KEY `fk_cost_subject_rule_subject` (`cost_subject_id`),
  CONSTRAINT `fk_cost_subject_rule_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_subject_rule_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_subject_rule_version` FOREIGN KEY (`mapping_version_id`) REFERENCES `cost_subject_mapping_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_subject_rule_dates` CHECK (((`effective_to` is null) or (`effective_to` >= `effective_from`))),
  CONSTRAINT `ck_cost_subject_rule_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='显式成本归集规则';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_subject_legacy_cleanup_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_subject_legacy_cleanup_audit` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `legacy_subject_id` bigint NOT NULL COMMENT 'æ—§æˆæœ¬ç§‘ç›®ID',
  `legacy_subject_code` varchar(64) NOT NULL COMMENT 'æ—§æˆæœ¬ç§‘ç›®ç¼–ç ',
  `legacy_subject_name` varchar(200) NOT NULL COMMENT 'æ—§æˆæœ¬ç§‘ç›®åç§°',
  `replacement_subject_id` bigint NOT NULL COMMENT 'æ›¿ä»£æˆæœ¬ç§‘ç›®ID',
  `replacement_subject_code` varchar(64) NOT NULL COMMENT 'æ›¿ä»£æˆæœ¬ç§‘ç›®ç¼–ç ',
  `replacement_subject_name` varchar(200) NOT NULL COMMENT 'æ›¿ä»£æˆæœ¬ç§‘ç›®åç§°',
  `migration_reason` varchar(500) NOT NULL COMMENT 'è¿ç§»åŽŸå› ',
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'è¿ç§»æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_cost_subject_cleanup` (`tenant_id`,`legacy_subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='旧成本科目删除与替代审计';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_subject_mapping_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_subject_mapping_item` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `mapping_version_id` bigint NOT NULL COMMENT 'æ˜ å°„ç‰ˆæœ¬ID',
  `source_subject_id` bigint NOT NULL COMMENT 'åŽ†å²æ¥æºç§‘ç›®ID',
  `target_group_code` varchar(64) NOT NULL COMMENT 'ç›®æ ‡ç§‘ç›®ç»„ç¼–ç ',
  `target_subject_id` bigint DEFAULT NULL COMMENT 'ç›®æ ‡ç§‘ç›®ID',
  `historical_display_name` varchar(200) NOT NULL COMMENT 'åŽ†å²æ˜¾ç¤ºåç§°',
  `mapping_reason` varchar(500) DEFAULT NULL COMMENT 'æ˜ å°„åŽŸå› ',
  `created_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_subject_mapping_item` (`tenant_id`,`mapping_version_id`,`source_subject_id`),
  KEY `idx_cost_subject_mapping_target` (`tenant_id`,`target_group_code`,`target_subject_id`),
  KEY `fk_cost_subject_mapping_version` (`mapping_version_id`),
  KEY `fk_cost_subject_mapping_source` (`source_subject_id`),
  KEY `fk_cost_subject_mapping_target` (`target_subject_id`),
  CONSTRAINT `fk_cost_subject_mapping_source` FOREIGN KEY (`source_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_subject_mapping_target` FOREIGN KEY (`target_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_subject_mapping_version` FOREIGN KEY (`mapping_version_id`) REFERENCES `cost_subject_mapping_version` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史成本科目到V2口径映射';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_subject_mapping_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_subject_mapping_version` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `version_code` varchar(64) NOT NULL COMMENT 'æ˜ å°„ç‰ˆæœ¬ç¼–ç ',
  `version_name` varchar(200) NOT NULL COMMENT 'æ˜ å°„ç‰ˆæœ¬åç§°',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'ç‰ˆæœ¬çŠ¶æ€',
  `effective_date` date DEFAULT NULL COMMENT 'ç”Ÿæ•ˆæ—¥æœŸ',
  `approval_instance_id` bigint DEFAULT NULL COMMENT 'å®¡æ‰¹å®žä¾‹ID',
  `activated_by` bigint DEFAULT NULL COMMENT 'å¯ç”¨äººID',
  `activated_at` datetime DEFAULT NULL COMMENT 'å¯ç”¨æ—¶é—´',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬',
  `created_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_by` bigint DEFAULT NULL COMMENT 'æ›´æ–°äººID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_subject_mapping_version` (`tenant_id`,`version_code`),
  KEY `idx_cost_subject_mapping_status` (`tenant_id`,`status`,`effective_date`),
  KEY `fk_cost_subject_mapping_approval` (`approval_instance_id`),
  CONSTRAINT `fk_cost_subject_mapping_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_subject_mapping_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本科目V2映射版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_summary` (
  `id` bigint NOT NULL COMMENT '成本汇总ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `summary_date` date NOT NULL COMMENT '汇总日期',
  `cost_subject_id` bigint DEFAULT NULL COMMENT '成本科目ID',
  `cost_target_id` bigint DEFAULT NULL COMMENT '关联的目标成本版本ID，关联cost_target.id',
  `target_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '目标成本',
  `contract_locked_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '合同锁定成本',
  `actual_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '实际成本',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '已付金额',
  `estimated_remaining_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '预计剩余成本',
  `dynamic_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '动态成本',
  `contract_income` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '合同收入',
  `confirmed_revenue` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '累计已确认收入（按履约进度，来源contract_revenue）',
  `expected_profit` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '预期利润',
  `cost_deviation` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '成本偏差',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `cost_forecast_id` bigint DEFAULT NULL COMMENT '采用的完工预测版本',
  `responsibility_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '责任预算',
  `forecast_at_completion_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '完工预测成本',
  `forecast_profit` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '预测利润',
  `profit_margin` decimal(9,6) NOT NULL DEFAULT '0.000000' COMMENT '预测利润率',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_summary` (`project_id`,`summary_date`,`cost_subject_id`,`deleted_flag`),
  KEY `idx_cs_project` (`project_id`),
  KEY `idx_cs_date` (`summary_date`),
  KEY `idx_summary_tenant_project` (`tenant_id`,`project_id`),
  KEY `idx_summary_subject` (`project_id`,`cost_subject_id`),
  KEY `fk_cost_summary_forecast` (`cost_forecast_id`),
  CONSTRAINT `fk_cost_summary_forecast` FOREIGN KEY (`cost_forecast_id`) REFERENCES `cost_forecast` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态成本汇总表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_target` (
  `id` bigint NOT NULL COMMENT '目标成本ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `version_no` varchar(50) NOT NULL COMMENT '版本号',
  `version_name` varchar(200) DEFAULT NULL COMMENT '版本名称',
  `total_target_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '目标成本总额',
  `is_active` tinyint NOT NULL DEFAULT '0' COMMENT '是否生效版本：0否，1是。同一项目仅允许一个生效版本',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '业务状态：DRAFT草稿，APPROVING审批中，ACTIVE已生效，CANCELLED已作废',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `total_bid_cost_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '投标成本基准总额',
  `total_responsibility_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '责任预算总额',
  `approval_instance_id` bigint DEFAULT NULL COMMENT '审批实例',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_target_version` (`tenant_id`,`project_id`,`version_no`,`deleted_flag`),
  KEY `idx_cost_target_project` (`project_id`),
  KEY `idx_cost_target_active` (`project_id`,`is_active`),
  KEY `fk_cost_target_approval` (`approval_instance_id`),
  CONSTRAINT `fk_cost_target_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_target_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_target_closed_loop_amount` CHECK (((`total_bid_cost_amount` >= 0) and (`total_target_amount` >= 0) and (`total_responsibility_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标成本表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cost_target_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_target_item` (
  `id` bigint NOT NULL COMMENT '目标成本明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `target_id` bigint NOT NULL COMMENT '目标成本ID，关联cost_target.id',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `cost_subject_id` bigint NOT NULL COMMENT '成本科目ID，关联cost_subject.id',
  `target_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '目标金额',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `bid_cost_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '投标成本科目快照',
  `responsibility_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '责任预算科目金额',
  `responsible_user_id` bigint DEFAULT NULL COMMENT '责任人',
  `responsibility_unit` varchar(200) DEFAULT NULL COMMENT '责任单位/部门',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_target_item_subject` (`tenant_id`,`target_id`,`cost_subject_id`,`deleted_flag`),
  KEY `idx_cost_target_item_target` (`target_id`),
  KEY `idx_cost_target_item_subject` (`cost_subject_id`),
  KEY `idx_cost_target_item_project` (`project_id`),
  CONSTRAINT `fk_cost_target_item_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cost_target_item_target` FOREIGN KEY (`target_id`) REFERENCES `cost_target` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_cost_target_item_closed_loop_amount` CHECK (((`bid_cost_amount` >= 0) and (`target_amount` >= 0) and (`responsibility_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标成本明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ct_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ct_contract` (
  `id` bigint NOT NULL COMMENT '合同ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_code` varchar(64) NOT NULL COMMENT '合同编号',
  `contract_name` varchar(200) NOT NULL COMMENT '合同名称',
  `contract_type` varchar(50) NOT NULL COMMENT '总包/分包/采购/租赁/服务等',
  `party_a_id` bigint DEFAULT NULL COMMENT '甲方合作方ID',
  `party_b_id` bigint DEFAULT NULL COMMENT '乙方合作方ID',
  `contract_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '原合同金额',
  `current_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '当前合同金额=原合同金额+已生效变更',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '累计已付金额',
  `tax_rate` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `amount_without_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `signed_date` date DEFAULT NULL COMMENT '签订日期',
  `start_date` date DEFAULT NULL COMMENT '合同开始日期',
  `end_date` date DEFAULT NULL COMMENT '合同结束日期',
  `payment_method` varchar(100) DEFAULT NULL COMMENT '付款方式',
  `settlement_method` varchar(100) DEFAULT NULL COMMENT '结算方式',
  `contract_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '合同业务状态',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `cost_generated_flag` tinyint NOT NULL DEFAULT '0' COMMENT '成本生成标识：0未生成，1已生成',
  `settlement_amount` decimal(18,2) DEFAULT NULL COMMENT '结算金额（结算审批通过后回写）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ct_contract_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_ct_contract_code` (`tenant_id`,`contract_code`,`active_unique_token`),
  KEY `idx_ct_contract_project` (`project_id`),
  KEY `idx_ct_contract_type` (`contract_type`),
  KEY `idx_ct_contract_status` (`contract_status`,`approval_status`),
  KEY `idx_ct_contract_created_at` (`created_at`),
  KEY `idx_ct_contract_tenant_project_status` (`tenant_id`,`project_id`,`contract_status`,`approval_status`,`deleted_flag`),
  CONSTRAINT `fk_contract_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ct_contract_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ct_contract_change` (
  `id` bigint NOT NULL COMMENT '合同变更ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `change_code` varchar(64) NOT NULL COMMENT '变更编号，自动生成',
  `change_name` varchar(200) NOT NULL COMMENT '变更名称',
  `change_type` varchar(50) NOT NULL COMMENT '变更类型：AMOUNT金额变更，DURATION工期变更，CLAUSE条款变更',
  `before_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变更前合同金额',
  `change_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变更金额（正数为增，负数为减）',
  `after_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变更后合同金额',
  `reason` text COMMENT '变更原因',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回',
  `effective_flag` tinyint NOT NULL DEFAULT '0' COMMENT '生效标识：0未生效，1已生效',
  `cost_generated_flag` tinyint NOT NULL DEFAULT '0' COMMENT '成本生成标识：0未生成，1已生成',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `business_matter_key` varchar(100) DEFAULT NULL COMMENT '跨域业务事项唯一键',
  `source_var_order_id` bigint DEFAULT NULL COMMENT '来源变更签证',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ct_change_source_var_order` (`tenant_id`,`source_var_order_id`),
  UNIQUE KEY `uk_ct_change_code` (`tenant_id`,`change_code`,`active_unique_token`),
  KEY `idx_change_contract` (`contract_id`),
  KEY `idx_change_project` (`project_id`),
  KEY `idx_ct_change_matter_key` (`tenant_id`,`project_id`,`business_matter_key`),
  KEY `fk_ct_change_source_var_order` (`source_var_order_id`),
  CONSTRAINT `fk_ct_change_source_var_order` FOREIGN KEY (`source_var_order_id`) REFERENCES `var_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同变更表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ct_contract_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ct_contract_item` (
  `id` bigint NOT NULL COMMENT '合同明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `item_code` varchar(64) DEFAULT NULL COMMENT '明细编号',
  `item_name` varchar(200) NOT NULL COMMENT '明细名称/清单项名称',
  `item_spec` varchar(300) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(50) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '数量',
  `unit_price` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '单价',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '金额（含税）',
  `tax_rate` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `amount_without_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  PRIMARY KEY (`id`),
  KEY `idx_ct_contract_item_contract` (`contract_id`,`sort_order`),
  KEY `idx_ct_contract_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ct_contract_payment_term`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ct_contract_payment_term` (
  `id` bigint NOT NULL COMMENT '付款条款ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `term_name` varchar(200) NOT NULL COMMENT '条款名称/付款节点名称',
  `payment_ratio` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '付款比例(%)',
  `payment_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '付款金额',
  `payment_condition` varchar(500) DEFAULT NULL COMMENT '付款条件',
  `planned_date` date DEFAULT NULL COMMENT '计划付款日期',
  `actual_date` date DEFAULT NULL COMMENT '实际付款日期',
  `term_status` varchar(50) NOT NULL DEFAULT 'PENDING' COMMENT '条款状态：PENDING待付，PARTIAL部分付款，PAID已付清',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  PRIMARY KEY (`id`),
  KEY `idx_ct_payment_term_contract` (`contract_id`,`sort_order`),
  KEY `idx_ct_payment_term_status` (`term_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同付款条款表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customer_credit_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_credit_profile` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `customer_id` bigint NOT NULL,
  `credit_limit` decimal(18,2) NOT NULL DEFAULT '0.00',
  `risk_level` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `dso_days` int NOT NULL DEFAULT '0',
  `overdue_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `score` decimal(8,2) NOT NULL DEFAULT '100.00',
  `formula_version` varchar(64) NOT NULL DEFAULT 'CUSTOMER_CREDIT_V1',
  `refreshed_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_credit` (`tenant_id`,`customer_id`),
  KEY `fk_customer_credit_partner` (`customer_id`),
  CONSTRAINT `fk_customer_credit_partner` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dashboard_finance_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_finance_snapshot` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  `formula_version` varchar(64) NOT NULL,
  `contract_amount` decimal(18,2) NOT NULL,
  `approved_unpaid_amount` decimal(18,2) NOT NULL,
  `paid_amount` decimal(18,2) NOT NULL,
  `budget_amount` decimal(18,2) NOT NULL,
  `budget_reserved` decimal(18,2) NOT NULL,
  `budget_consumed` decimal(18,2) NOT NULL,
  `cash_inflow` decimal(18,2) NOT NULL,
  `cash_outflow` decimal(18,2) NOT NULL,
  `actual_cost` decimal(18,2) NOT NULL,
  `profit_amount` decimal(18,2) NOT NULL,
  `refreshed_at` datetime NOT NULL,
  `refresh_mode` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dashboard_snapshot` (`tenant_id`,`project_id`,`snapshot_date`),
  KEY `idx_dashboard_snapshot_date` (`tenant_id`,`snapshot_date`),
  KEY `fk_dashboard_snapshot_project` (`project_id`),
  CONSTRAINT `fk_dashboard_snapshot_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `expense_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense_application` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `cost_subject_id` bigint NOT NULL,
  `budget_line_id` bigint NOT NULL,
  `payee_partner_id` bigint NOT NULL,
  `expense_code` varchar(64) NOT NULL,
  `expense_category` varchar(64) NOT NULL,
  `expense_date` date NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `converted_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `description` varchar(500) NOT NULL,
  `approval_status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expense_code` (`tenant_id`,`expense_code`,`deleted_flag`),
  KEY `fk_expense_project` (`project_id`),
  KEY `fk_expense_contract` (`contract_id`),
  KEY `fk_expense_subject` (`cost_subject_id`),
  KEY `fk_expense_budget_line` (`budget_line_id`),
  KEY `fk_expense_partner` (`payee_partner_id`),
  KEY `idx_expense_project_status` (`tenant_id`,`project_id`,`approval_status`),
  KEY `idx_expense_contract` (`tenant_id`,`contract_id`),
  CONSTRAINT `fk_expense_budget_line` FOREIGN KEY (`budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expense_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expense_partner` FOREIGN KEY (`payee_partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expense_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expense_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用申请';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_account_reconciliation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_account_reconciliation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `period_id` bigint NOT NULL,
  `account_type` varchar(16) NOT NULL,
  `expected_amount` decimal(18,2) NOT NULL,
  `ledger_amount` decimal(18,2) NOT NULL,
  `difference_amount` decimal(18,2) NOT NULL,
  `status` varchar(16) NOT NULL,
  `detail_json` longtext,
  `reconciled_by` bigint DEFAULT NULL,
  `reconciled_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_account_recon` (`tenant_id`,`period_id`,`account_type`),
  KEY `fk_finance_account_recon_period` (`period_id`),
  CONSTRAINT `fk_finance_account_recon_period` FOREIGN KEY (`period_id`) REFERENCES `finance_period` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_finance_account_recon_status` CHECK ((`status` in (_utf8mb4'MATCHED',_utf8mb4'EXCEPTION'))),
  CONSTRAINT `ck_finance_account_recon_type` CHECK ((`account_type` in (_utf8mb4'AR',_utf8mb4'AP')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_alert` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `alert_type` varchar(64) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `severity` varchar(16) NOT NULL,
  `due_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `message` varchar(1000) NOT NULL,
  `alert_key` varchar(200) NOT NULL,
  `alert_log_id` bigint DEFAULT NULL COMMENT '驾驶舱权威预警事实ID',
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `handle_note` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_alert_key` (`tenant_id`,`alert_key`),
  UNIQUE KEY `uk_finance_alert_log` (`alert_log_id`),
  KEY `idx_finance_alert_status` (`tenant_id`,`status`,`severity`,`due_at`),
  CONSTRAINT `fk_finance_alert_log` FOREIGN KEY (`alert_log_id`) REFERENCES `alert_log` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_audit_event` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `event_type` varchar(64) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `event_at` datetime NOT NULL,
  `archive_bucket` varchar(32) NOT NULL DEFAULT 'HOT',
  `payload_json` longtext NOT NULL,
  `payload_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_finance_audit_search` (`tenant_id`,`business_type`,`business_id`,`event_at`),
  KEY `idx_finance_audit_archive` (`tenant_id`,`archive_bucket`,`event_at`),
  CONSTRAINT `ck_fin_audit_payload_json` CHECK ((json_valid(`payload_json`) and (length(`payload_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_bank_reconciliation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_bank_reconciliation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `period_id` bigint NOT NULL,
  `bank_receipt_id` bigint NOT NULL,
  `direction` varchar(8) NOT NULL,
  `business_type` varchar(32) DEFAULT NULL,
  `business_id` bigint DEFAULT NULL,
  `cash_journal_id` bigint DEFAULT NULL,
  `bank_amount` decimal(18,2) NOT NULL,
  `business_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `difference_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `status` varchar(16) NOT NULL,
  `match_method` varchar(16) NOT NULL DEFAULT 'AUTO',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `resolution_note` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_bank_recon` (`tenant_id`,`period_id`,`bank_receipt_id`),
  KEY `fk_finance_bank_recon_period` (`period_id`),
  KEY `fk_finance_bank_recon_receipt` (`bank_receipt_id`),
  KEY `fk_finance_bank_recon_journal` (`cash_journal_id`),
  CONSTRAINT `fk_finance_bank_recon_journal` FOREIGN KEY (`cash_journal_id`) REFERENCES `cash_journal_entry` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_bank_recon_period` FOREIGN KEY (`period_id`) REFERENCES `finance_period` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_bank_recon_receipt` FOREIGN KEY (`bank_receipt_id`) REFERENCES `bank_receipt` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_finance_bank_recon_status` CHECK ((`status` in (_utf8mb4'MATCHED',_utf8mb4'EXCEPTION',_utf8mb4'RESOLVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_cost_allocation_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_cost_allocation_batch` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `batch_code` varchar(64) NOT NULL COMMENT 'åˆ†æ‘Šæ‰¹æ¬¡å·',
  `source_type` varchar(32) NOT NULL COMMENT 'æ¥æºç±»åž‹',
  `source_id` bigint NOT NULL COMMENT 'æ¥æºå•æ®ID',
  `source_amount` decimal(18,2) NOT NULL COMMENT 'å¾…åˆ†æ‘Šé‡‘é¢',
  `allocation_basis` varchar(32) NOT NULL COMMENT 'åˆ†æ‘Šä¾æ®',
  `accounting_period` char(7) NOT NULL COMMENT 'ä¼šè®¡æœŸé—´',
  `cost_subject_id` bigint NOT NULL COMMENT 'è´¢åŠ¡è´¹ç”¨æˆæœ¬ç§‘ç›®ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT 'å¹‚ç­‰é”®',
  `status` varchar(16) NOT NULL DEFAULT 'POSTED' COMMENT 'äº‹å®žçŠ¶æ€',
  `approval_instance_id` bigint NOT NULL COMMENT 'å®¡æ‰¹å®žä¾‹ID',
  `reversal_of_id` bigint DEFAULT NULL COMMENT 'è¢«å†²é”€æ‰¹æ¬¡ID',
  `posted_by` bigint NOT NULL COMMENT 'è®°è´¦äººID',
  `posted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'è®°è´¦æ—¶é—´',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_cost_batch_code` (`tenant_id`,`batch_code`),
  UNIQUE KEY `uk_finance_cost_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_finance_cost_reversal` (`tenant_id`,`reversal_of_id`),
  KEY `idx_finance_cost_source` (`tenant_id`,`source_type`,`source_id`,`status`),
  KEY `fk_finance_cost_subject` (`cost_subject_id`),
  KEY `fk_finance_cost_approval` (`approval_instance_id`),
  KEY `fk_finance_cost_reversal` (`reversal_of_id`),
  CONSTRAINT `fk_finance_cost_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_cost_reversal` FOREIGN KEY (`reversal_of_id`) REFERENCES `finance_cost_allocation_batch` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_cost_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_finance_cost_amount` CHECK ((`source_amount` <> 0)),
  CONSTRAINT `ck_finance_cost_basis` CHECK ((`allocation_basis` in (_utf8mb4'DIRECT_PROJECT',_utf8mb4'BENEFIT_AMOUNT',_utf8mb4'OCCUPIED_DAYS',_utf8mb4'CONTRACT_AMOUNT_EXCEPTION'))),
  CONSTRAINT `ck_finance_cost_source` CHECK ((`source_type` in (_utf8mb4'ACCOUNTING_ENTRY_LINE',_utf8mb4'EXPENSE_APPLICATION'))),
  CONSTRAINT `ck_finance_cost_status` CHECK ((`status` in (_utf8mb4'POSTED',_utf8mb4'REVERSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目财务费用分摊批次';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_cost_allocation_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_cost_allocation_line` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `batch_id` bigint NOT NULL COMMENT 'åˆ†æ‘Šæ‰¹æ¬¡ID',
  `project_id` bigint NOT NULL COMMENT 'é¡¹ç›®ID',
  `basis_value` decimal(18,6) NOT NULL COMMENT 'åˆ†æ‘ŠåŸºæ•°',
  `allocated_amount` decimal(18,2) NOT NULL COMMENT 'åˆ†æ‘Šé‡‘é¢',
  `cost_item_id` bigint DEFAULT NULL COMMENT 'ç”Ÿæˆæˆæœ¬æ˜Žç»†ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_cost_project` (`tenant_id`,`batch_id`,`project_id`),
  KEY `idx_finance_cost_line_project` (`tenant_id`,`project_id`),
  KEY `fk_finance_cost_line_batch` (`batch_id`),
  KEY `fk_finance_cost_line_project` (`project_id`),
  KEY `fk_finance_cost_line_cost` (`cost_item_id`),
  CONSTRAINT `fk_finance_cost_line_batch` FOREIGN KEY (`batch_id`) REFERENCES `finance_cost_allocation_batch` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_cost_line_cost` FOREIGN KEY (`cost_item_id`) REFERENCES `cost_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_finance_cost_line_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_finance_cost_line_values` CHECK (((`basis_value` > 0) and (`allocated_amount` <> 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目财务费用分摊明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_import_batch` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `import_type` varchar(32) NOT NULL,
  `project_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_hash` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `total_rows` int NOT NULL DEFAULT '0',
  `valid_rows` int NOT NULL DEFAULT '0',
  `invalid_rows` int NOT NULL DEFAULT '0',
  `diff_summary_json` longtext,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `applied_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_import_hash` (`tenant_id`,`import_type`,`project_id`,`file_hash`),
  KEY `fk_finance_import_project` (`project_id`),
  CONSTRAINT `fk_finance_import_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_fin_import_summary_json` CHECK (((`diff_summary_json` is null) or (json_valid(`diff_summary_json`) and (length(`diff_summary_json`) <= 1048576))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_import_row`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_import_row` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `batch_id` bigint NOT NULL,
  `row_no` int NOT NULL,
  `business_key` varchar(128) DEFAULT NULL,
  `input_json` longtext NOT NULL,
  `diff_json` longtext,
  `validation_status` varchar(32) NOT NULL,
  `validation_message` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_import_row` (`batch_id`,`row_no`),
  KEY `idx_finance_import_row_status` (`tenant_id`,`batch_id`,`validation_status`),
  CONSTRAINT `fk_finance_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `finance_import_batch` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_fin_import_diff_json` CHECK (((`diff_json` is null) or (json_valid(`diff_json`) and (length(`diff_json`) <= 1048576)))),
  CONSTRAINT `ck_fin_import_input_json` CHECK ((json_valid(`input_json`) and (length(`input_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_integration_endpoint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_integration_endpoint` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `endpoint_type` varchar(32) NOT NULL,
  `endpoint_code` varchar(64) NOT NULL,
  `endpoint_name` varchar(200) NOT NULL,
  `base_url` varchar(500) DEFAULT NULL,
  `credential_ref` varchar(200) DEFAULT NULL,
  `callback_secret_hash` varchar(64) DEFAULT NULL,
  `enabled_flag` tinyint NOT NULL DEFAULT '1',
  `config_json` longtext,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_endpoint_code` (`tenant_id`,`endpoint_code`),
  CONSTRAINT `ck_fin_endpoint_config_json` CHECK (((`config_json` is null) or (json_valid(`config_json`) and (length(`config_json`) <= 1048576))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_integration_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_integration_message` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `endpoint_id` bigint NOT NULL,
  `direction` varchar(16) NOT NULL,
  `message_type` varchar(64) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `payload_json` longtext NOT NULL,
  `response_json` longtext,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `processed_at` datetime DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_integration_key` (`tenant_id`,`endpoint_id`,`direction`,`idempotency_key`),
  KEY `idx_finance_integration_dispatch` (`tenant_id`,`status`,`next_retry_at`),
  KEY `fk_finance_message_endpoint` (`endpoint_id`),
  CONSTRAINT `fk_finance_message_endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `finance_integration_endpoint` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_fin_message_payload_json` CHECK ((json_valid(`payload_json`) and (length(`payload_json`) <= 1048576))),
  CONSTRAINT `ck_fin_message_response_json` CHECK (((`response_json` is null) or (json_valid(`response_json`) and (length(`response_json`) <= 1048576))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_period` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `period_code` varchar(16) NOT NULL,
  `fiscal_year` int NOT NULL,
  `fiscal_month` int NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `last_check_at` datetime DEFAULT NULL,
  `issue_count` int NOT NULL DEFAULT '0',
  `closed_by` bigint DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `close_comment` varchar(500) DEFAULT NULL,
  `reopened_by` bigint DEFAULT NULL,
  `reopened_at` datetime DEFAULT NULL,
  `reopen_reason` varchar(500) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_period` (`tenant_id`,`fiscal_year`,`fiscal_month`),
  CONSTRAINT `ck_finance_period_month` CHECK ((`fiscal_month` between 1 and 12)),
  CONSTRAINT `ck_finance_period_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CHECKING',_utf8mb4'CLOSED',_utf8mb4'REOPENED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_period_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_period_check` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `period_id` bigint NOT NULL,
  `check_type` varchar(64) NOT NULL,
  `check_status` varchar(16) NOT NULL,
  `issue_count` int NOT NULL DEFAULT '0',
  `detail_json` longtext,
  `checked_by` bigint DEFAULT NULL,
  `checked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_period_check` (`tenant_id`,`period_id`,`check_type`),
  KEY `fk_finance_period_check_period` (`period_id`),
  CONSTRAINT `fk_finance_period_check_period` FOREIGN KEY (`period_id`) REFERENCES `finance_period` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_finance_period_check_status` CHECK ((`check_status` in (_utf8mb4'PASS',_utf8mb4'FAIL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_reconciliation_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_reconciliation_issue` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `run_id` bigint NOT NULL,
  `dimension_type` varchar(64) NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `issue_code` varchar(64) NOT NULL,
  `expected_amount` decimal(18,2) DEFAULT NULL,
  `actual_amount` decimal(18,2) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `detail` varchar(1000) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_finance_recon_issue` (`tenant_id`,`run_id`,`status`),
  KEY `fk_finance_recon_issue_run` (`run_id`),
  CONSTRAINT `fk_finance_recon_issue_run` FOREIGN KEY (`run_id`) REFERENCES `finance_reconciliation_run` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `finance_reconciliation_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `finance_reconciliation_run` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `business_date` date NOT NULL,
  `run_type` varchar(32) NOT NULL DEFAULT 'DAILY',
  `status` varchar(32) NOT NULL,
  `issue_count` int NOT NULL DEFAULT '0',
  `summary_json` longtext,
  `started_at` datetime NOT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_recon_day` (`tenant_id`,`business_date`,`run_type`),
  KEY `idx_finance_recon_status` (`tenant_id`,`status`,`business_date`),
  CONSTRAINT `ck_fin_recon_summary_json` CHECK (((`summary_json` is null) or (json_valid(`summary_json`) and (length(`summary_json`) <= 1048576))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fund_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fund_account` (
  `id` bigint NOT NULL COMMENT '资金账户ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `account_code` varchar(64) NOT NULL COMMENT '租户内账户编码',
  `account_name` varchar(128) NOT NULL COMMENT '账户名称',
  `account_type` varchar(16) NOT NULL COMMENT 'CASH/BANK',
  `bank_name` varchar(128) DEFAULT NULL COMMENT '开户行',
  `bank_account_no` varchar(128) DEFAULT NULL COMMENT '银行账号',
  `opening_date` date NOT NULL COMMENT '期初日期',
  `opening_balance` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '期初余额',
  `enabled_flag` tinyint NOT NULL DEFAULT '1' COMMENT '1启用，0停用',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_account_code` (`tenant_id`,`account_code`,`deleted_flag`),
  KEY `idx_fund_account_tenant_enabled` (`tenant_id`,`enabled_flag`,`deleted_flag`),
  CONSTRAINT `ck_fund_account_opening_balance` CHECK ((`opening_balance` >= 0)),
  CONSTRAINT `ck_fund_account_type` CHECK ((`account_type` in (_utf8mb4'CASH',_utf8mb4'BANK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业资金账户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fund_pool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fund_pool` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `pool_code` varchar(64) NOT NULL,
  `pool_name` varchar(200) NOT NULL,
  `currency_code` varchar(8) NOT NULL DEFAULT 'CNY',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `control_mode` varchar(32) NOT NULL DEFAULT 'QUOTA',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_pool_code` (`tenant_id`,`pool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fund_pool_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fund_pool_member` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `pool_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `fund_account_id` bigint NOT NULL,
  `quota_amount` decimal(18,2) NOT NULL,
  `occupied_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_pool_member` (`tenant_id`,`pool_id`,`company_id`,`fund_account_id`),
  KEY `fk_fund_pool_member_pool` (`pool_id`),
  KEY `fk_fund_pool_member_account` (`fund_account_id`),
  CONSTRAINT `fk_fund_pool_member_account` FOREIGN KEY (`fund_account_id`) REFERENCES `fund_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_fund_pool_member_pool` FOREIGN KEY (`pool_id`) REFERENCES `fund_pool` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fund_pool_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fund_pool_transaction` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `pool_id` bigint NOT NULL,
  `from_member_id` bigint DEFAULT NULL,
  `to_member_id` bigint DEFAULT NULL,
  `transaction_type` varchar(32) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `status` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `external_txn_no` varchar(128) DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_pool_txn_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_fund_pool_txn` (`tenant_id`,`pool_id`,`occurred_at`),
  KEY `fk_fund_pool_txn_pool` (`pool_id`),
  KEY `fk_fund_pool_txn_from` (`from_member_id`),
  KEY `fk_fund_pool_txn_to` (`to_member_id`),
  CONSTRAINT `fk_fund_pool_txn_from` FOREIGN KEY (`from_member_id`) REFERENCES `fund_pool_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_fund_pool_txn_pool` FOREIGN KEY (`pool_id`) REFERENCES `fund_pool` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_fund_pool_txn_to` FOREIGN KEY (`to_member_id`) REFERENCES `fund_pool_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_ocr_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_ocr_review` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `invoice_id` bigint NOT NULL,
  `raw_result_json` longtext NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `comparison_json` longtext,
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_note` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_invoice_ocr_review` (`tenant_id`,`review_status`,`confidence`),
  KEY `fk_invoice_ocr_review_invoice` (`invoice_id`),
  CONSTRAINT `fk_invoice_ocr_review_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pay_invoice` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_invoice_ocr_comparison_json` CHECK (((`comparison_json` is null) or (json_valid(`comparison_json`) and (length(`comparison_json`) <= 1048576)))),
  CONSTRAINT `ck_invoice_ocr_raw_json` CHECK ((json_valid(`raw_result_json`) and (length(`raw_result_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_payment_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_payment_allocation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `invoice_id` bigint NOT NULL,
  `pay_record_id` bigint NOT NULL,
  `pay_application_id` bigint NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_alloc_record` (`tenant_id`,`invoice_id`,`pay_record_id`),
  KEY `fk_invoice_alloc_invoice` (`invoice_id`),
  KEY `fk_invoice_alloc_record` (`pay_record_id`),
  KEY `fk_invoice_alloc_application` (`pay_application_id`),
  KEY `idx_invoice_alloc_record` (`tenant_id`,`pay_record_id`),
  CONSTRAINT `fk_invoice_alloc_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_alloc_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pay_invoice` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_alloc_record` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票付款金额分配';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_material_return`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_material_return` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `requisition_id` bigint NOT NULL,
  `return_code` varchar(50) NOT NULL,
  `return_date` date NOT NULL,
  `status` varchar(30) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `confirmed_by` bigint NOT NULL,
  `confirmed_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` text,
  `reversed_by` bigint DEFAULT NULL COMMENT '冲销人',
  `reversed_at` datetime DEFAULT NULL COMMENT '冲销时间',
  `reversal_reason` varchar(500) DEFAULT NULL COMMENT '冲销原因',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_return_code` (`tenant_id`,`return_code`),
  UNIQUE KEY `uk_material_return_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_material_return_tenant_id` (`tenant_id`,`id`),
  KEY `idx_material_return_requisition` (`tenant_id`,`requisition_id`),
  KEY `fk_material_return_project` (`tenant_id`,`project_id`),
  KEY `fk_material_return_contract` (`tenant_id`,`contract_id`),
  KEY `fk_material_return_warehouse` (`tenant_id`,`warehouse_id`),
  KEY `idx_material_return_status` (`tenant_id`,`status`,`return_date`),
  CONSTRAINT `fk_material_return_contract` FOREIGN KEY (`tenant_id`, `contract_id`) REFERENCES `ct_contract` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_project` FOREIGN KEY (`tenant_id`, `project_id`) REFERENCES `pm_project` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_requisition` FOREIGN KEY (`tenant_id`, `requisition_id`) REFERENCES `mat_requisition` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_warehouse` FOREIGN KEY (`tenant_id`, `warehouse_id`) REFERENCES `mat_warehouse` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_material_return_amount` CHECK ((`total_amount` >= 0)),
  CONSTRAINT `ck_material_return_status` CHECK ((`status` in (_utf8mb4'CONFIRMED',_utf8mb4'REVERSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料退料确认单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_material_return_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_material_return_item` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `return_id` bigint NOT NULL,
  `requisition_item_id` bigint NOT NULL,
  `original_stock_txn_id` bigint NOT NULL,
  `original_cost_item_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `unit_cost` decimal(18,6) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_return_item_source` (`tenant_id`,`return_id`,`requisition_item_id`,`original_stock_txn_id`),
  KEY `idx_material_return_item_header` (`tenant_id`,`return_id`),
  KEY `idx_material_return_item_requisition` (`tenant_id`,`requisition_item_id`),
  KEY `idx_material_return_item_txn` (`tenant_id`,`original_stock_txn_id`),
  KEY `fk_material_return_item_cost` (`tenant_id`,`original_cost_item_id`),
  KEY `fk_material_return_item_material` (`tenant_id`,`material_id`),
  CONSTRAINT `fk_material_return_item_cost` FOREIGN KEY (`tenant_id`, `original_cost_item_id`) REFERENCES `cost_item` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_item_header` FOREIGN KEY (`tenant_id`, `return_id`) REFERENCES `mat_material_return` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_item_material` FOREIGN KEY (`tenant_id`, `material_id`) REFERENCES `md_material` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_item_requisition` FOREIGN KEY (`tenant_id`, `requisition_item_id`) REFERENCES `mat_requisition_item` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_material_return_item_stock_txn` FOREIGN KEY (`tenant_id`, `original_stock_txn_id`) REFERENCES `mat_stock_txn` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_material_return_item_quantity` CHECK ((`quantity` > 0)),
  CONSTRAINT `ck_material_return_item_value` CHECK (((`unit_cost` >= 0) and (`amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料退料明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_purchase_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_purchase_order` (
  `id` bigint NOT NULL COMMENT '采购订单ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `request_id` bigint DEFAULT NULL COMMENT '采购申请ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `order_code` varchar(64) NOT NULL COMMENT '订单编号',
  `order_type` varchar(50) DEFAULT NULL COMMENT '订单类型',
  `order_date` date DEFAULT NULL COMMENT '订单日期',
  `delivery_date` date DEFAULT NULL COMMENT '预计交付日期',
  `delivery_terms` varchar(500) DEFAULT NULL COMMENT '交付条件',
  `exception_purchase_flag` tinyint NOT NULL DEFAULT '0' COMMENT '无申请来源的例外采购标识',
  `exception_reason` varchar(500) DEFAULT NULL COMMENT '例外采购原因',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '订单总金额',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `order_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '订单状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_po_code` (`tenant_id`,`order_code`,`active_unique_token`),
  KEY `idx_mat_po_project` (`project_id`),
  KEY `idx_mat_po_contract` (`contract_id`),
  KEY `idx_mat_po_partner` (`partner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_purchase_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_purchase_order_item` (
  `id` bigint NOT NULL COMMENT '订单明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '采购订单ID',
  `request_item_id` bigint DEFAULT NULL COMMENT '来源采购申请明细ID；手工订单允许为空',
  `budget_line_id` bigint DEFAULT NULL COMMENT '项目预算科目ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '材料名称',
  `specification` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '采购数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `tax_rate` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '税率百分比',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '税额',
  `amount_without_tax` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '不含税金额',
  `received_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已收货数量',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `wbs_task_id` bigint DEFAULT NULL COMMENT '来源WBS任务ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_poi_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mat_poi_order` (`order_id`),
  KEY `idx_mat_poi_material` (`material_id`),
  KEY `idx_mat_poi_request_item` (`tenant_id`,`request_item_id`,`deleted_flag`),
  KEY `idx_mpo_item_budget` (`tenant_id`,`budget_line_id`,`deleted_flag`),
  KEY `fk_mpo_item_request` (`request_item_id`),
  KEY `idx_mat_poi_wbs` (`tenant_id`,`wbs_task_id`),
  CONSTRAINT `fk_mat_poi_budget` FOREIGN KEY (`tenant_id`, `budget_line_id`) REFERENCES `project_budget_line` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mat_poi_wbs` FOREIGN KEY (`tenant_id`, `wbs_task_id`) REFERENCES `project_wbs_task` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mpo_item_order` FOREIGN KEY (`order_id`) REFERENCES `mat_purchase_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mpo_item_request` FOREIGN KEY (`request_item_id`) REFERENCES `mat_purchase_request_item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_purchase_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_purchase_request` (
  `id` bigint NOT NULL COMMENT '采购申请ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '关联采购合同',
  `purpose` varchar(500) DEFAULT NULL COMMENT '采购用途/施工部位说明',
  `request_code` varchar(50) NOT NULL COMMENT '申请编号，PR-yyyyMMdd-XXX',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回，WITHDRAWN已撤回',
  `status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '业务状态：DRAFT草稿，APPROVED已通过，CONVERTED已转采购订单',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_pr_code` (`tenant_id`,`request_code`,`deleted_flag`),
  KEY `idx_mpr_tenant` (`tenant_id`),
  KEY `idx_mpr_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_purchase_request_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_purchase_request_item` (
  `id` bigint NOT NULL COMMENT '明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `request_id` bigint NOT NULL COMMENT '采购申请ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `budget_line_id` bigint DEFAULT NULL COMMENT '项目预算科目ID',
  `sub_task_id` bigint DEFAULT NULL COMMENT '分包任务ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '申请数量',
  `estimated_unit_price` decimal(18,4) DEFAULT NULL COMMENT '申请估算单价',
  `estimated_amount` decimal(18,2) DEFAULT NULL COMMENT '申请估算金额',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `planned_date` date DEFAULT NULL COMMENT '期望到货日期',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `wbs_task_id` bigint DEFAULT NULL COMMENT 'WBS任务ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_pri_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mpi_request` (`request_id`),
  KEY `idx_mpi_material` (`material_id`),
  KEY `idx_mpr_item_budget` (`tenant_id`,`budget_line_id`,`deleted_flag`),
  KEY `idx_mpr_item_sub_task` (`tenant_id`,`sub_task_id`,`deleted_flag`),
  KEY `fk_mpr_item_sub_task` (`sub_task_id`),
  KEY `idx_mat_pri_wbs` (`tenant_id`,`wbs_task_id`),
  CONSTRAINT `fk_mat_pri_budget` FOREIGN KEY (`tenant_id`, `budget_line_id`) REFERENCES `project_budget_line` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mat_pri_wbs` FOREIGN KEY (`tenant_id`, `wbs_task_id`) REFERENCES `project_wbs_task` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mpr_item_request` FOREIGN KEY (`request_id`) REFERENCES `mat_purchase_request` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mpr_item_sub_task` FOREIGN KEY (`sub_task_id`) REFERENCES `sub_task` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_quality_disposition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_quality_disposition` (
  `id` bigint NOT NULL COMMENT '质量处置ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `receipt_id` bigint NOT NULL COMMENT '验收单ID',
  `receipt_item_id` bigint NOT NULL COMMENT '验收明细ID',
  `rejected_quantity` decimal(18,4) NOT NULL COMMENT '不合格数量',
  `disposition_action` varchar(32) NOT NULL DEFAULT 'RETURN_TO_SUPPLIER' COMMENT '处置动作',
  `status` varchar(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/CANCELLED',
  `resolved_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已处置数量',
  `resolved_at` datetime DEFAULT NULL COMMENT '完成时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '处置说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quality_disposition_item` (`tenant_id`,`receipt_item_id`),
  KEY `idx_quality_disposition_status` (`tenant_id`,`project_id`,`status`),
  KEY `fk_quality_disposition_receipt` (`tenant_id`,`receipt_id`),
  CONSTRAINT `fk_quality_disposition_item` FOREIGN KEY (`tenant_id`, `receipt_item_id`) REFERENCES `mat_receipt_item` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_quality_disposition_receipt` FOREIGN KEY (`tenant_id`, `receipt_id`) REFERENCES `mat_receipt` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_quality_disposition_action` CHECK ((`disposition_action` in (_utf8mb4'RETURN_TO_SUPPLIER',_utf8mb4'REWORK',_utf8mb4'CONCESSION',_utf8mb4'SCRAP'))),
  CONSTRAINT `ck_quality_disposition_qty` CHECK (((`rejected_quantity` > 0) and (`resolved_quantity` >= 0) and (`resolved_quantity` <= `rejected_quantity`))),
  CONSTRAINT `ck_quality_disposition_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'RESOLVED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收不合格处置事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_receipt` (
  `id` bigint NOT NULL COMMENT '验收ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `order_id` bigint DEFAULT NULL COMMENT '采购订单ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `receipt_code` varchar(64) NOT NULL COMMENT '验收单号',
  `receipt_date` date DEFAULT NULL COMMENT '验收日期',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `receiver_id` bigint DEFAULT NULL COMMENT '验收人ID',
  `receipt_mode` varchar(30) NOT NULL DEFAULT 'INVENTORY' COMMENT 'INVENTORY入库；DIRECT_CONSUMPTION直耗',
  `quality_status` varchar(50) DEFAULT NULL COMMENT '质量状态',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '验收总金额',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `cost_generated_flag` tinyint NOT NULL DEFAULT '0' COMMENT '成本生成标识：0未生成，1已生成',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_receipt_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_mat_receipt_code` (`tenant_id`,`receipt_code`,`active_unique_token`),
  KEY `idx_mat_receipt_project` (`project_id`),
  KEY `idx_mat_receipt_order` (`order_id`),
  KEY `idx_mat_receipt_contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_receipt_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_receipt_item` (
  `id` bigint NOT NULL COMMENT '验收明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `receipt_id` bigint NOT NULL COMMENT '验收单ID',
  `order_item_id` bigint DEFAULT NULL COMMENT '订单明细ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料ID',
  `actual_quantity` decimal(18,4) DEFAULT NULL COMMENT '实际到货数量',
  `qualified_quantity` decimal(18,4) DEFAULT NULL COMMENT '合格数量',
  `unqualified_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '不合格数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `use_location` varchar(200) DEFAULT NULL COMMENT '使用部位',
  `batch_no` varchar(100) DEFAULT NULL COMMENT '批号',
  `disposition_type` varchar(30) DEFAULT NULL COMMENT 'RETURN/REPLACE/CONCESSION',
  `disposition_status` varchar(30) DEFAULT NULL COMMENT 'PENDING/COMPLETED',
  `disposition_reason` varchar(500) DEFAULT NULL COMMENT '不合格处置原因',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `wbs_task_id` bigint DEFAULT NULL COMMENT '来源WBS任务ID',
  `budget_line_id` bigint DEFAULT NULL COMMENT '来源项目预算行ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_ri_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mat_ri_receipt` (`receipt_id`),
  KEY `idx_mat_ri_material` (`material_id`),
  KEY `idx_receipt_item_disposition` (`tenant_id`,`disposition_status`,`deleted_flag`),
  KEY `fk_receipt_item_order` (`order_item_id`),
  KEY `idx_mat_ri_wbs` (`tenant_id`,`wbs_task_id`),
  KEY `idx_mat_ri_budget` (`tenant_id`,`budget_line_id`),
  CONSTRAINT `fk_mat_ri_budget` FOREIGN KEY (`tenant_id`, `budget_line_id`) REFERENCES `project_budget_line` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mat_ri_wbs` FOREIGN KEY (`tenant_id`, `wbs_task_id`) REFERENCES `project_wbs_task` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_receipt_item_header` FOREIGN KEY (`receipt_id`) REFERENCES `mat_receipt` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_receipt_item_order` FOREIGN KEY (`order_item_id`) REFERENCES `mat_purchase_order_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_mat_ri_quantity_193` CHECK (((`actual_quantity` > 0) and (`qualified_quantity` >= 0) and (`qualified_quantity` <= `actual_quantity`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_requisition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_requisition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '领料申请ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '关联合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方/供应商ID',
  `requisition_code` varchar(50) NOT NULL COMMENT '申请编号 REQ-yyyyMMdd-XXX',
  `requisition_date` date NOT NULL COMMENT '申请日期',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `requisitioner_id` bigint DEFAULT NULL COMMENT '领料人ID',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT/APPROVING/APPROVED/REJECTED',
  `total_amount` decimal(18,4) DEFAULT '0.0000' COMMENT '总金额（明细金额合计）',
  `stock_out_flag` tinyint NOT NULL DEFAULT '0' COMMENT '出库标记：0未出库，1已出库',
  `stock_out_by` bigint DEFAULT NULL COMMENT '实际出库操作人',
  `stock_out_at` datetime DEFAULT NULL COMMENT '实际出库时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_code` (`tenant_id`,`requisition_code`),
  UNIQUE KEY `uk_mat_requisition_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mr_tenant` (`tenant_id`),
  KEY `idx_mr_project` (`project_id`),
  KEY `idx_mr_contract` (`contract_id`),
  KEY `idx_mr_warehouse` (`warehouse_id`),
  KEY `idx_mr_approval_status` (`approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领料申请主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_requisition_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_requisition_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '领料申请明细ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `requisition_id` bigint NOT NULL COMMENT '领料申请ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '申请数量',
  `unit_price` decimal(18,4) DEFAULT '0.0000' COMMENT '参考单价',
  `amount` decimal(18,4) DEFAULT '0.0000' COMMENT '金额（quantity × unit_price）',
  `use_location` varchar(200) DEFAULT NULL COMMENT '使用部位/用途',
  `batch_no` varchar(100) DEFAULT NULL COMMENT '批次号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_requisition_item_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mri_tenant` (`tenant_id`),
  KEY `idx_mri_requisition` (`requisition_id`),
  KEY `idx_mri_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领料申请明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_stock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_stock` (
  `id` bigint NOT NULL COMMENT '库存ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `material_id` bigint NOT NULL COMMENT '物料ID，关联md_material.id',
  `available_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '可用数量',
  `inventory_value` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '库存价值',
  `average_unit_cost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '移动加权平均单价',
  `safety_stock_qty` decimal(18,4) NOT NULL DEFAULT '10.0000' COMMENT '安全库存阈值',
  `replenishment_target_qty` decimal(18,4) DEFAULT NULL COMMENT '人工补货目标量；NULL 回退安全库存阈值',
  `replenishment_lead_days` int DEFAULT NULL COMMENT '人工补货提前期（自然日）；NULL 不预填计划日期',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '库存活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ms_warehouse_material` (`tenant_id`,`warehouse_id`,`material_id`,`active_unique_token`),
  KEY `idx_ms_tenant` (`tenant_id`),
  KEY `idx_ms_warehouse` (`warehouse_id`),
  KEY `idx_ms_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存余额表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_stock_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_stock_transfer` (
  `id` bigint NOT NULL COMMENT '调拨事实ID，雪花ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '两端仓库所属项目ID',
  `source_stock_id` bigint NOT NULL COMMENT '来源库存ID',
  `target_stock_id` bigint NOT NULL COMMENT '目标库存ID',
  `source_warehouse_id` bigint NOT NULL COMMENT '来源仓库ID',
  `target_warehouse_id` bigint NOT NULL COMMENT '目标仓库ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '调拨数量',
  `unit_cost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '来源移动加权平均单位成本',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '调拨库存价值',
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内幂等键',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING处理中；COMPLETED已完成',
  `completed_at` datetime DEFAULT NULL COMMENT '原子过账完成时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '调拨原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_transfer_tenant_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_stock_transfer_project_created` (`tenant_id`,`project_id`,`created_at`),
  KEY `idx_stock_transfer_source_stock` (`tenant_id`,`source_stock_id`),
  KEY `idx_stock_transfer_target_stock` (`tenant_id`,`target_stock_id`),
  KEY `fk_stock_transfer_source_stock` (`source_stock_id`),
  KEY `fk_stock_transfer_target_stock` (`target_stock_id`),
  CONSTRAINT `fk_stock_transfer_source_stock` FOREIGN KEY (`source_stock_id`) REFERENCES `mat_stock` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_stock_transfer_target_stock` FOREIGN KEY (`target_stock_id`) REFERENCES `mat_stock` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_stock_transfer_route` CHECK (((`source_stock_id` <> `target_stock_id`) and (`source_warehouse_id` <> `target_warehouse_id`))),
  CONSTRAINT `ck_stock_transfer_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'COMPLETED'))),
  CONSTRAINT `ck_stock_transfer_values` CHECK (((`quantity` > 0) and (`unit_cost` >= 0) and (`amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同项目跨仓库存调拨事实表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_stock_txn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_stock_txn` (
  `id` bigint NOT NULL COMMENT '流水ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `txn_type` varchar(20) NOT NULL COMMENT '交易类型：IN入库，OUT出库，ADJUST调整',
  `quantity` decimal(18,4) NOT NULL COMMENT '交易数量（入库为正，出库为负或正数由服务层控制）',
  `available_after` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '交易后可用量',
  `unit_cost` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '本次移动单位成本',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '本次移动库存价值',
  `source_type` varchar(50) DEFAULT NULL COMMENT '来源业务类型',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务ID',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源业务明细ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_stock_txn_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_stock_txn_source_line` (`tenant_id`,`txn_type`,`source_type`,`source_id`,`source_line_id`),
  KEY `idx_mst_tenant` (`tenant_id`),
  KEY `idx_mst_warehouse_material` (`warehouse_id`,`material_id`),
  KEY `idx_mst_source` (`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mat_warehouse`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mat_warehouse` (
  `id` bigint NOT NULL COMMENT '仓库ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '所属项目ID',
  `warehouse_code` varchar(50) NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(200) NOT NULL COMMENT '仓库名称',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mat_warehouse_tenant_id` (`tenant_id`,`id`),
  KEY `idx_mw_tenant` (`tenant_id`),
  KEY `idx_mw_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `md_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_material` (
  `id` bigint NOT NULL COMMENT '材料ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `material_code` varchar(64) NOT NULL COMMENT '材料编码',
  `material_name` varchar(200) NOT NULL COMMENT '材料名称',
  `category_id` bigint DEFAULT NULL COMMENT '材料类别ID',
  `specification` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `brand` varchar(100) DEFAULT NULL COMMENT '品牌',
  `default_tax_rate` decimal(6,2) DEFAULT NULL COMMENT '默认税率',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md_material_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_md_material_code` (`tenant_id`,`material_code`,`active_unique_token`),
  KEY `idx_md_material_category` (`category_id`),
  KEY `fk_md_material_category` (`tenant_id`,`category_id`),
  CONSTRAINT `fk_md_material_category` FOREIGN KEY (`tenant_id`, `category_id`) REFERENCES `md_material_category` (`tenant_id`, `id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料字典表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `md_material_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_material_category` (
  `id` bigint NOT NULL COMMENT '材料分类ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `parent_id` bigint DEFAULT NULL COMMENT '上级分类ID',
  `category_code` varchar(64) NOT NULL COMMENT '分类编码，租户内永久唯一',
  `category_name` varchar(128) NOT NULL COMMENT '分类名称',
  `level_no` int NOT NULL DEFAULT '1' COMMENT '树层级，从1开始',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '同级排序号',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE/DISABLE',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_category_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_material_category_code` (`tenant_id`,`category_code`),
  KEY `idx_material_category_parent` (`tenant_id`,`parent_id`,`order_num`),
  CONSTRAINT `fk_material_category_parent` FOREIGN KEY (`tenant_id`, `parent_id`) REFERENCES `md_material_category` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_material_category_level` CHECK ((`level_no` >= 1)),
  CONSTRAINT `ck_material_category_status` CHECK ((`status` in (_utf8mb4'ENABLE',_utf8mb4'DISABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料分类主数据';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `md_partner`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_partner` (
  `id` bigint NOT NULL COMMENT '合作方ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `partner_code` varchar(64) NOT NULL COMMENT '合作方编号',
  `partner_name` varchar(200) NOT NULL COMMENT '合作方名称',
  `partner_type` varchar(50) NOT NULL COMMENT '供应商/分包商/租赁商/服务商等',
  `credit_code` varchar(100) DEFAULT NULL COMMENT '统一社会信用代码',
  `legal_person` varchar(100) DEFAULT NULL COMMENT '法人',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行账号',
  `qualification_level` varchar(100) DEFAULT NULL COMMENT '资质等级',
  `blacklist_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否黑名单：0否，1是',
  `risk_level` varchar(50) DEFAULT NULL COMMENT '风险等级',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `default_lead_days` int DEFAULT NULL,
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md_partner_code` (`tenant_id`,`partner_code`,`active_unique_token`),
  KEY `idx_md_partner_name` (`partner_name`),
  KEY `idx_md_partner_type` (`partner_type`),
  KEY `idx_md_partner_blacklist` (`blacklist_flag`),
  KEY `idx_md_partner_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作方表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `measurement_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `measurement_period` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `period_code` varchar(32) NOT NULL,
  `period_name` varchar(100) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `cutoff_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'OPEN',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_measurement_period` (`tenant_id`,`contract_id`,`period_code`,`deleted_flag`),
  KEY `idx_measurement_period_project` (`tenant_id`,`project_id`,`start_date`,`end_date`),
  KEY `fk_measurement_period_project` (`project_id`),
  KEY `fk_measurement_period_contract` (`contract_id`),
  CONSTRAINT `fk_measurement_period_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_measurement_period_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_measurement_period_dates` CHECK (((`start_date` <= `end_date`) and (`cutoff_date` >= `start_date`))),
  CONSTRAINT `ck_measurement_period_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `org_company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_company` (
  `id` bigint NOT NULL COMMENT '公司ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_code` varchar(50) NOT NULL COMMENT '公司编码',
  `company_name` varchar(200) NOT NULL COMMENT '公司名称',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oc_tenant_code` (`tenant_id`,`company_code`,`active_unique_token`),
  KEY `idx_oc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `org_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_department` (
  `id` bigint NOT NULL COMMENT '部门ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `parent_id` bigint DEFAULT NULL COMMENT '上级部门ID，NULL为根部门',
  `dept_code` varchar(50) NOT NULL COMMENT '部门编码',
  `dept_name` varchar(200) NOT NULL COMMENT '部门名称',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_od_tenant` (`tenant_id`),
  KEY `idx_od_company` (`company_id`),
  KEY `idx_od_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（自引用树形结构）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `org_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_position` (
  `id` bigint NOT NULL COMMENT '岗位ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司ID',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `position_code` varchar(50) NOT NULL COMMENT '岗位编码',
  `position_name` varchar(200) NOT NULL COMMENT '岗位名称',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_position_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_op_tenant_code` (`tenant_id`,`position_code`,`active_unique_token`),
  KEY `idx_op_tenant` (`tenant_id`),
  KEY `idx_op_company` (`company_id`),
  KEY `idx_op_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `org_user_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `org_user_position` (
  `id` bigint NOT NULL COMMENT '用户岗位关系ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `position_id` bigint NOT NULL COMMENT '岗位ID',
  `primary_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否主岗位',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `effective_from` date DEFAULT NULL COMMENT '生效日期',
  `effective_to` date DEFAULT NULL COMMENT '失效日期',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_user_position` (`tenant_id`,`user_id`,`position_id`),
  KEY `idx_org_position_users` (`tenant_id`,`position_id`,`status`),
  CONSTRAINT `fk_org_user_position_position` FOREIGN KEY (`tenant_id`, `position_id`) REFERENCES `org_position` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_org_user_position_user` FOREIGN KEY (`tenant_id`, `user_id`) REFERENCES `sys_user` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_org_user_position_dates` CHECK (((`effective_from` is null) or (`effective_to` is null) or (`effective_from` <= `effective_to`))),
  CONSTRAINT `ck_org_user_position_primary` CHECK ((`primary_flag` in (0,1))),
  CONSTRAINT `ck_org_user_position_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户岗位关系';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `overhead_allocation_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `overhead_allocation_record` (
  `id` bigint NOT NULL COMMENT '分摊记录ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `rule_id` bigint NOT NULL COMMENT '分摊规则ID',
  `source_project_id` bigint NOT NULL COMMENT '费用发生项目ID',
  `target_project_id` bigint NOT NULL COMMENT '分摊目标项目ID',
  `cost_subject_id` bigint NOT NULL COMMENT '科目ID',
  `allocation_date` date NOT NULL COMMENT '分摊日期',
  `source_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '原始费用金额',
  `allocated_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '分摊金额',
  `allocation_ratio` decimal(5,4) NOT NULL DEFAULT '0.0000' COMMENT '分摊比例',
  `status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/POSTED',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alloc_rule` (`rule_id`),
  KEY `idx_alloc_date` (`allocation_date`),
  KEY `idx_alloc_target_project` (`target_project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='间接费用分摊记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `overhead_allocation_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `overhead_allocation_rule` (
  `id` bigint NOT NULL COMMENT '分摊规则ID',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `cost_subject_id` bigint NOT NULL COMMENT '间接费用科目ID（5401.04.xx）',
  `allocation_basis` varchar(50) NOT NULL COMMENT 'DIRECT_LABOR直接人工比例/CONTRACT_AMOUNT合同额比例/USAGE实际使用',
  `allocation_cycle` varchar(20) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY按月/PER_OCCURRENCE按次',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` text COMMENT '分摊说明/备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_allocation_subject` (`tenant_id`,`cost_subject_id`,`deleted_flag`),
  KEY `idx_allocation_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='间接费用分摊规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `overhead_allocation_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `overhead_allocation_run` (
  `id` bigint NOT NULL COMMENT '执行ID（雪花ID）',
  `tenant_id` bigint NOT NULL,
  `rule_id` bigint NOT NULL COMMENT '分摊规则ID',
  `period` date NOT NULL COMMENT '目标自然月月末',
  `trigger_type` varchar(20) NOT NULL COMMENT 'MANUAL/SCHEDULED',
  `executed_by` bigint DEFAULT NULL COMMENT '手工执行用户；定时任务为空',
  `run_status` varchar(30) NOT NULL COMMENT 'PENDING/SUCCESS/SKIPPED_ZERO/SKIPPED_NO_WEIGHT',
  `allocated_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cost_item_count` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_overhead_run_period` (`tenant_id`,`rule_id`,`period`,`deleted_flag`),
  KEY `idx_overhead_run_tenant_period` (`tenant_id`,`period`),
  KEY `idx_overhead_run_status` (`run_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='间接费月度分摊执行事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `owner_measurement_review_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `owner_measurement_review_line` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `submission_id` bigint NOT NULL,
  `measurement_line_id` bigint NOT NULL,
  `submitted_quantity` decimal(18,4) NOT NULL,
  `submitted_amount` decimal(18,2) NOT NULL,
  `confirmed_quantity` decimal(18,4) DEFAULT NULL,
  `confirmed_amount` decimal(18,2) DEFAULT NULL,
  `deducted_amount` decimal(18,2) DEFAULT NULL,
  `deduction_reason` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_review_measure_line` (`tenant_id`,`submission_id`,`measurement_line_id`),
  KEY `fk_owner_review_submission` (`submission_id`),
  KEY `fk_owner_review_measure_line` (`measurement_line_id`),
  CONSTRAINT `fk_owner_review_measure_line` FOREIGN KEY (`measurement_line_id`) REFERENCES `production_measurement_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_review_submission` FOREIGN KEY (`submission_id`) REFERENCES `owner_measurement_submission` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_owner_review_quantity` CHECK (((`submitted_quantity` > 0) and ((`confirmed_quantity` is null) or ((`confirmed_quantity` >= 0) and (`confirmed_quantity` <= `submitted_quantity`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `owner_measurement_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `owner_measurement_submission` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `measurement_id` bigint NOT NULL,
  `submission_code` varchar(64) NOT NULL,
  `revision_no` int NOT NULL,
  `submitted_at` datetime NOT NULL,
  `external_document_no` varchar(128) DEFAULT NULL,
  `submitted_amount` decimal(18,2) NOT NULL,
  `confirmed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `deducted_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `reviewer_name` varchar(100) DEFAULT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `attachment_count` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_measure_submission_code` (`tenant_id`,`submission_code`,`deleted_flag`),
  UNIQUE KEY `uk_owner_measure_revision` (`tenant_id`,`measurement_id`,`revision_no`,`deleted_flag`),
  KEY `idx_owner_measure_submission` (`tenant_id`,`project_id`,`status`,`submitted_at`),
  KEY `fk_owner_measure_submission_project` (`project_id`),
  KEY `fk_owner_measure_submission_contract` (`contract_id`),
  KEY `fk_owner_measure_submission_measure` (`measurement_id`),
  CONSTRAINT `fk_owner_measure_submission_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_measure_submission_measure` FOREIGN KEY (`measurement_id`) REFERENCES `production_measurement` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_measure_submission_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_owner_measure_submission_amount` CHECK (((`submitted_amount` > 0) and (`confirmed_amount` >= 0) and (`deducted_amount` >= 0) and ((`confirmed_amount` + `deducted_amount`) <= `submitted_amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `owner_settlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `owner_settlement` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `revenue_id` bigint DEFAULT NULL,
  `settlement_code` varchar(64) NOT NULL,
  `settlement_period` varchar(32) NOT NULL,
  `settlement_date` date NOT NULL,
  `gross_amount` decimal(18,2) NOT NULL,
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `retention_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `net_receivable_amount` decimal(18,2) NOT NULL,
  `due_date` date NOT NULL,
  `customer_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `attachment_count` int NOT NULL DEFAULT '0',
  `formula_version` varchar(64) NOT NULL DEFAULT 'OWNER_SETTLEMENT_V1',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `production_measurement_id` bigint DEFAULT NULL,
  `owner_submission_id` bigint DEFAULT NULL,
  `reported_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `deducted_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `settlement_type` varchar(32) NOT NULL DEFAULT 'PROGRESS' COMMENT 'PROGRESS/FINAL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_settlement_code` (`tenant_id`,`settlement_code`,`deleted_flag`),
  UNIQUE KEY `uk_owner_settlement_submission` (`tenant_id`,`owner_submission_id`,`deleted_flag`),
  KEY `idx_owner_settlement_contract` (`tenant_id`,`contract_id`,`settlement_date`),
  KEY `fk_owner_settlement_project` (`project_id`),
  KEY `fk_owner_settlement_contract` (`contract_id`),
  KEY `fk_owner_settlement_revenue` (`revenue_id`),
  KEY `fk_owner_settlement_customer` (`customer_id`),
  KEY `fk_owner_settlement_approval` (`approval_instance_id`),
  KEY `fk_owner_settlement_measurement` (`production_measurement_id`),
  KEY `fk_owner_settlement_submission` (`owner_submission_id`),
  CONSTRAINT `fk_owner_settlement_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_customer` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_measurement` FOREIGN KEY (`production_measurement_id`) REFERENCES `production_measurement` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_revenue` FOREIGN KEY (`revenue_id`) REFERENCES `contract_revenue` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_owner_settlement_submission` FOREIGN KEY (`owner_submission_id`) REFERENCES `owner_measurement_submission` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_owner_settlement_amount` CHECK (((`gross_amount` > 0) and (`tax_amount` >= 0) and (`retention_amount` >= 0) and (`net_receivable_amount` = (`gross_amount` - `retention_amount`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pay_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_application` (
  `id` bigint NOT NULL COMMENT '付款申请ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方ID',
  `apply_code` varchar(64) NOT NULL COMMENT '申请单编号',
  `apply_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '申请金额',
  `approved_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '批准金额',
  `actual_pay_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '实际付款金额',
  `pay_type` varchar(50) NOT NULL COMMENT '付款类型：预付款/进度款/结算款/质保金等',
  `pay_status` varchar(50) NOT NULL DEFAULT 'PENDING' COMMENT '付款状态：PENDING待付，PARTIAL部分付款，PAID已付清',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `apply_reason` varchar(1000) DEFAULT NULL COMMENT '申请事由',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `cost_subject_id` bigint DEFAULT NULL,
  `budget_line_id` bigint DEFAULT NULL,
  `expense_category` varchar(64) DEFAULT NULL,
  `approval_instance_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `integrity_version` varchar(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_application_code` (`tenant_id`,`apply_code`,`active_unique_token`),
  KEY `idx_pay_application_project` (`project_id`),
  KEY `idx_pay_application_contract` (`contract_id`),
  KEY `idx_pay_application_partner` (`partner_id`),
  KEY `idx_pay_application_status` (`pay_status`,`approval_status`),
  KEY `idx_pay_application_tenant_contract_approval` (`tenant_id`,`contract_id`,`approval_status`,`deleted_flag`),
  KEY `idx_pay_application_tenant_project_status` (`tenant_id`,`project_id`,`pay_status`,`approval_status`,`deleted_flag`),
  KEY `idx_pay_application_integrity` (`tenant_id`,`integrity_version`,`approval_status`),
  KEY `fk_pay_app_subject` (`cost_subject_id`),
  KEY `fk_pay_app_budget_line` (`budget_line_id`),
  KEY `fk_pay_app_approval` (`approval_instance_id`),
  CONSTRAINT `fk_pay_app_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_app_budget_line` FOREIGN KEY (`budget_line_id`) REFERENCES `project_budget_line` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_app_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_app_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_app_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_app_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pay_application_basis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_application_basis` (
  `id` bigint NOT NULL COMMENT '关联ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `pay_application_id` bigint NOT NULL COMMENT '付款申请ID',
  `basis_type` varchar(50) NOT NULL COMMENT '依据类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证',
  `basis_id` bigint NOT NULL COMMENT '依据单据ID',
  `basis_amount` decimal(18,2) NOT NULL COMMENT '依据金额',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_pab_application` (`pay_application_id`),
  KEY `idx_pab_basis` (`basis_type`,`basis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款依据关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pay_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_invoice` (
  `id` bigint NOT NULL COMMENT '发票ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `pay_application_id` bigint DEFAULT NULL COMMENT '付款申请ID，关联pay_application.id',
  `pay_record_id` bigint DEFAULT NULL COMMENT '付款记录ID，关联pay_record.id',
  `invoice_no` varchar(100) NOT NULL COMMENT '发票号码',
  `invoice_type` varchar(50) NOT NULL DEFAULT 'VAT_SPECIAL' COMMENT '发票类型：VAT_SPECIAL增值税专票，VAT_NORMAL增值税普票，OTHER其他',
  `invoice_amount` decimal(18,2) NOT NULL COMMENT '发票金额',
  `tax_rate` decimal(5,2) DEFAULT NULL COMMENT '税率（百分比，如13.00）',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '税额',
  `invoice_date` date DEFAULT NULL COMMENT '开票日期',
  `verify_status` varchar(50) NOT NULL DEFAULT 'PENDING' COMMENT '核验状态：PENDING待核验，VERIFIED已认证，ABNORMAL异常',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  `seller_name` varchar(200) DEFAULT NULL COMMENT '卖方名称',
  `buyer_name` varchar(200) DEFAULT NULL COMMENT '买方名称',
  `buyer_tax_no` varchar(50) DEFAULT NULL COMMENT '买方税号',
  `seller_tax_no` varchar(50) DEFAULT NULL COMMENT '卖方税号',
  `project_id` bigint DEFAULT NULL,
  `contract_id` bigint DEFAULT NULL,
  `partner_id` bigint DEFAULT NULL,
  `document_type` varchar(32) NOT NULL DEFAULT 'ELECTRONIC_INVOICE',
  `integrity_version` varchar(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED',
  `version` int NOT NULL DEFAULT '0',
  `exception_status` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `exception_reason` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_tenant_invoice_no_del` (`tenant_id`,`invoice_no`,`deleted_flag`),
  KEY `idx_pi_tenant` (`tenant_id`),
  KEY `idx_pi_pay_app` (`pay_application_id`),
  KEY `idx_pi_pay_record` (`pay_record_id`),
  KEY `fk_invoice_project` (`project_id`),
  KEY `fk_invoice_contract` (`contract_id`),
  KEY `fk_invoice_partner` (`partner_id`),
  KEY `idx_invoice_exception` (`tenant_id`,`exception_status`,`invoice_date`),
  CONSTRAINT `fk_invoice_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_pay_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_pay_record` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pay_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_record` (
  `id` bigint NOT NULL COMMENT '付款记录ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID',
  `pay_application_id` bigint NOT NULL COMMENT '付款申请ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方ID',
  `pay_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '付款金额',
  `pay_date` date NOT NULL COMMENT '付款日期',
  `pay_method` varchar(50) DEFAULT NULL COMMENT '付款方式：银行转账/承兑汇票/现金等',
  `voucher_no` varchar(100) DEFAULT NULL COMMENT '付款凭证号',
  `pay_status` varchar(50) NOT NULL DEFAULT 'SUCCESS' COMMENT '付款状态：SUCCESS成功，FAILED失败，PROCESSING处理中',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `external_txn_no` varchar(128) DEFAULT NULL COMMENT '外部交易流水号',
  `fund_account_id` bigint DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `reversed_record_id` bigint DEFAULT NULL,
  `reversed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `reversal_type` varchar(32) DEFAULT NULL COMMENT 'REVERSAL/REFUND',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_txn_no` (`tenant_id`,`external_txn_no`,`deleted_flag`),
  KEY `idx_pay_record_application` (`pay_application_id`),
  KEY `idx_pay_record_contract` (`contract_id`),
  KEY `idx_pay_record_partner` (`partner_id`),
  KEY `idx_pay_record_date` (`pay_date`),
  KEY `idx_pay_record_tenant_contract_date` (`tenant_id`,`contract_id`,`pay_date`,`deleted_flag`),
  KEY `fk_pay_record_fund_account` (`fund_account_id`),
  KEY `fk_pay_record_reversed_record` (`reversed_record_id`),
  KEY `fk_pay_record_project` (`project_id`),
  KEY `idx_pay_record_finance_recon` (`tenant_id`,`project_id`,`pay_status`,`paid_at`),
  CONSTRAINT `fk_pay_record_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_record_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_record_fund_account` FOREIGN KEY (`fund_account_id`) REFERENCES `fund_account` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_record_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_record_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_record_reversed_record` FOREIGN KEY (`reversed_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_application_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_application_source` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `pay_application_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL COMMENT 'EXPENSE/SETTLEMENT/DIRECT',
  `source_ref_id` bigint NOT NULL COMMENT '来源ID；DIRECT时等于付款申请ID',
  `expense_id` bigint DEFAULT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `sub_measure_id` bigint DEFAULT NULL,
  `source_amount` decimal(18,2) NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_source` (`tenant_id`,`pay_application_id`,`source_type`,`source_ref_id`,`deleted_flag`),
  KEY `fk_payment_source_application` (`pay_application_id`),
  KEY `fk_payment_source_expense` (`expense_id`),
  KEY `fk_payment_source_settlement` (`settlement_id`),
  KEY `idx_payment_source_ref` (`tenant_id`,`source_type`,`source_ref_id`),
  KEY `fk_payment_source_sub_measure` (`sub_measure_id`),
  KEY `idx_payment_source_sub_measure` (`tenant_id`,`sub_measure_id`,`deleted_flag`),
  CONSTRAINT `fk_payment_source_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_source_expense` FOREIGN KEY (`expense_id`) REFERENCES `expense_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_source_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `stl_settlement` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_source_sub_measure` FOREIGN KEY (`sub_measure_id`) REFERENCES `sub_measure` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_payment_source_reference` CHECK ((((`source_type` = _utf8mb4'EXPENSE') and (`expense_id` is not null) and (`settlement_id` is null) and (`sub_measure_id` is null) and (`source_ref_id` = `expense_id`)) or ((`source_type` = _utf8mb4'SETTLEMENT') and (`settlement_id` is not null) and (`expense_id` is null) and (`sub_measure_id` is null) and (`source_ref_id` = `settlement_id`)) or ((`source_type` = _utf8mb4'SUB_MEASURE') and (`sub_measure_id` is not null) and (`expense_id` is null) and (`settlement_id` is null) and (`source_ref_id` = `sub_measure_id`)) or ((`source_type` = _utf8mb4'DIRECT') and (`expense_id` is null) and (`settlement_id` is null) and (`sub_measure_id` is null) and (`source_ref_id` = `pay_application_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款申请统一来源';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_record_source_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_record_source_allocation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `pay_record_id` bigint NOT NULL,
  `payment_source_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_ref_id` bigint NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_alloc_record_source` (`tenant_id`,`pay_record_id`,`payment_source_id`),
  KEY `fk_pay_alloc_record` (`pay_record_id`),
  KEY `fk_pay_alloc_source` (`payment_source_id`),
  KEY `idx_pay_alloc_source` (`tenant_id`,`payment_source_id`),
  CONSTRAINT `fk_pay_alloc_record` FOREIGN KEY (`pay_record_id`) REFERENCES `pay_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pay_alloc_source` FOREIGN KEY (`payment_source_id`) REFERENCES `payment_application_source` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款记录来源金额分配';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_schedule` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `pay_application_id` bigint DEFAULT NULL,
  `schedule_name` varchar(200) NOT NULL,
  `planned_date` date NOT NULL,
  `planned_amount` decimal(18,2) NOT NULL,
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `reminder_days` int NOT NULL DEFAULT '7',
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_payment_schedule_due` (`tenant_id`,`status`,`planned_date`),
  KEY `fk_payment_schedule_project` (`project_id`),
  KEY `fk_payment_schedule_contract` (`contract_id`),
  KEY `fk_payment_schedule_application` (`pay_application_id`),
  CONSTRAINT `fk_payment_schedule_application` FOREIGN KEY (`pay_application_id`) REFERENCES `pay_application` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_schedule_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_schedule_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pm_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pm_project` (
  `id` bigint NOT NULL COMMENT '项目ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织/项目部ID',
  `project_code` varchar(64) NOT NULL COMMENT '项目编号',
  `project_name` varchar(200) NOT NULL COMMENT '项目名称',
  `project_type` varchar(50) DEFAULT NULL COMMENT '项目类型',
  `project_address` varchar(300) DEFAULT NULL COMMENT '项目地址',
  `owner_unit` varchar(200) DEFAULT NULL COMMENT '建设单位',
  `supervisor_unit` varchar(200) DEFAULT NULL COMMENT '监理单位',
  `design_unit` varchar(200) DEFAULT NULL COMMENT '设计单位',
  `contract_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '总包合同金额',
  `target_cost` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '目标成本',
  `planned_start_date` date DEFAULT NULL COMMENT '计划开工日期',
  `planned_end_date` date DEFAULT NULL COMMENT '计划竣工日期',
  `actual_start_date` date DEFAULT NULL COMMENT '实际开工日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际竣工日期',
  `project_manager_id` bigint DEFAULT NULL COMMENT '项目经理用户ID',
  `status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
  `approval_status` varchar(50) DEFAULT NULL COMMENT '审批状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_pm_project_code` (`tenant_id`,`project_code`,`active_unique_token`),
  KEY `idx_pm_project_name` (`project_name`),
  KEY `idx_pm_project_status` (`status`),
  KEY `idx_pm_project_manager` (`project_manager_id`),
  KEY `idx_pm_project_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pm_project_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pm_project_member` (
  `id` bigint NOT NULL COMMENT '成员ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_code` varchar(50) NOT NULL COMMENT '项目角色：PM项目经理，CM商务经理，CSTM成本经理，MAT材料员，SUBC分包管理员，FIN财务，OTH其他',
  `position_name` varchar(200) DEFAULT NULL COMMENT '岗位名称（可覆盖用户默认岗位）',
  `start_date` date DEFAULT NULL COMMENT '加入日期',
  `end_date` date DEFAULT NULL COMMENT '退出日期',
  `status` varchar(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE在职，INACTIVE已退出',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ppm_project_user` (`project_id`,`user_id`),
  KEY `idx_ppm_tenant` (`tenant_id`),
  KEY `idx_ppm_project` (`project_id`),
  KEY `idx_ppm_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_measurement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_measurement` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `period_id` bigint NOT NULL,
  `measure_code` varchar(64) NOT NULL,
  `measure_date` date NOT NULL,
  `current_reported_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cumulative_reported_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `approval_status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `attachment_count` int NOT NULL DEFAULT '0',
  `formula_version` varchar(64) NOT NULL DEFAULT 'PRODUCTION_MEASUREMENT_V1',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_measure_code` (`tenant_id`,`measure_code`,`deleted_flag`),
  UNIQUE KEY `uk_production_measure_period` (`tenant_id`,`contract_id`,`period_id`,`deleted_flag`),
  KEY `idx_production_measure_project` (`tenant_id`,`project_id`,`measure_date`,`status`),
  KEY `fk_production_measure_project` (`project_id`),
  KEY `fk_production_measure_contract` (`contract_id`),
  KEY `fk_production_measure_period` (`period_id`),
  KEY `fk_production_measure_approval` (`approval_instance_id`),
  CONSTRAINT `fk_production_measure_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_production_measure_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_production_measure_period` FOREIGN KEY (`period_id`) REFERENCES `measurement_period` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_production_measure_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_production_measure_amount` CHECK (((`current_reported_amount` >= 0) and (`cumulative_reported_amount` >= `current_reported_amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_measurement_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_measurement_line` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `measurement_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `contract_item_id` bigint DEFAULT NULL,
  `contract_change_id` bigint DEFAULT NULL,
  `item_code` varchar(64) DEFAULT NULL,
  `item_name` varchar(200) NOT NULL,
  `item_spec` varchar(300) DEFAULT NULL,
  `unit` varchar(50) DEFAULT NULL,
  `contract_quantity` decimal(18,4) NOT NULL,
  `prior_approved_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `current_reported_quantity` decimal(18,4) NOT NULL,
  `cumulative_reported_quantity` decimal(18,4) NOT NULL,
  `unit_price` decimal(18,4) NOT NULL,
  `current_reported_amount` decimal(18,2) NOT NULL,
  `cumulative_reported_amount` decimal(18,2) NOT NULL,
  `evidence_count` int NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_measure_item` (`tenant_id`,`measurement_id`,`contract_item_id`),
  UNIQUE KEY `uk_production_measure_change` (`tenant_id`,`measurement_id`,`contract_change_id`),
  KEY `idx_production_measure_line_item` (`tenant_id`,`contract_item_id`),
  KEY `idx_production_measure_line_change` (`tenant_id`,`contract_change_id`),
  KEY `fk_production_measure_line_header` (`measurement_id`),
  KEY `fk_production_measure_line_item` (`contract_item_id`),
  KEY `fk_production_measure_line_change` (`contract_change_id`),
  CONSTRAINT `fk_production_measure_line_change` FOREIGN KEY (`contract_change_id`) REFERENCES `ct_contract_change` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_production_measure_line_header` FOREIGN KEY (`measurement_id`) REFERENCES `production_measurement` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_production_measure_line_item` FOREIGN KEY (`contract_item_id`) REFERENCES `ct_contract_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_production_measure_line_amount` CHECK (((`unit_price` >= 0) and (`current_reported_amount` >= 0) and (`cumulative_reported_amount` >= `current_reported_amount`))),
  CONSTRAINT `ck_production_measure_line_quantity` CHECK (((`contract_quantity` > 0) and (`prior_approved_quantity` >= 0) and (`current_reported_quantity` > 0) and (`cumulative_reported_quantity` = (`prior_approved_quantity` + `current_reported_quantity`)) and (`cumulative_reported_quantity` <= `contract_quantity`))),
  CONSTRAINT `ck_production_measure_line_source` CHECK ((((`contract_item_id` is not null) and (`contract_change_id` is null)) or ((`contract_item_id` is null) and (`contract_change_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_budget`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_budget` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `version_no` varchar(32) NOT NULL,
  `budget_name` varchar(200) NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  `approval_status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `active_flag` tinyint NOT NULL DEFAULT '0',
  `active_token` bigint DEFAULT NULL COMMENT '激活时等于project_id，用于数据库保证单项目唯一生效版本',
  `effective_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_budget_version` (`tenant_id`,`project_id`,`version_no`,`deleted_flag`),
  UNIQUE KEY `uk_project_budget_active` (`tenant_id`,`active_token`),
  KEY `fk_project_budget_project` (`project_id`),
  KEY `idx_project_budget_project_status` (`tenant_id`,`project_id`,`status`),
  CONSTRAINT `fk_project_budget_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目预算版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_budget_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_budget_line` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `budget_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `cost_subject_id` bigint NOT NULL,
  `budget_amount` decimal(18,2) NOT NULL,
  `reserved_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `consumed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget_line_subject` (`tenant_id`,`budget_id`,`cost_subject_id`,`deleted_flag`),
  UNIQUE KEY `uk_budget_line_tenant_id` (`tenant_id`,`id`),
  KEY `fk_budget_line_budget` (`budget_id`),
  KEY `fk_budget_line_project` (`project_id`),
  KEY `fk_budget_line_subject` (`cost_subject_id`),
  KEY `idx_budget_line_project_subject` (`tenant_id`,`project_id`,`cost_subject_id`),
  CONSTRAINT `fk_budget_line_budget` FOREIGN KEY (`budget_id`) REFERENCES `project_budget` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_line_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_budget_line_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目预算科目';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_closeout`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_closeout` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `closeout_code` varchar(64) NOT NULL,
  `planned_completion_date` date NOT NULL,
  `actual_completion_date` date DEFAULT NULL,
  `status` varchar(40) NOT NULL DEFAULT 'INITIATED',
  `final_owner_settlement_id` bigint DEFAULT NULL,
  `tail_collection_verified_at` datetime DEFAULT NULL,
  `closed_by` bigint DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_closeout_project` (`tenant_id`,`project_id`,`deleted_flag`),
  UNIQUE KEY `uk_project_closeout_code` (`tenant_id`,`closeout_code`,`deleted_flag`),
  UNIQUE KEY `uk_project_closeout_settlement` (`tenant_id`,`final_owner_settlement_id`),
  KEY `fk_project_closeout_project` (`project_id`),
  KEY `fk_project_closeout_settlement` (`final_owner_settlement_id`),
  CONSTRAINT `fk_project_closeout_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_closeout_settlement` FOREIGN KEY (`final_owner_settlement_id`) REFERENCES `owner_settlement` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_corrective_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_corrective_action` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `schedule_plan_id` bigint NOT NULL,
  `snapshot_id` bigint NOT NULL,
  `alert_id` bigint DEFAULT NULL,
  `action_code` varchar(64) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `action_plan` varchar(1000) NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  `due_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `generated_revision_plan_id` bigint DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_corrective_code` (`tenant_id`,`project_id`,`action_code`,`deleted_flag`),
  UNIQUE KEY `uk_project_corrective_snapshot` (`tenant_id`,`snapshot_id`,`deleted_flag`),
  KEY `fk_project_corrective_project` (`project_id`),
  KEY `fk_project_corrective_schedule` (`schedule_plan_id`),
  KEY `fk_project_corrective_snapshot` (`snapshot_id`),
  KEY `fk_project_corrective_alert` (`alert_id`),
  KEY `fk_project_corrective_approval` (`approval_instance_id`),
  KEY `fk_project_corrective_revision` (`generated_revision_plan_id`),
  CONSTRAINT `fk_project_corrective_alert` FOREIGN KEY (`alert_id`) REFERENCES `alert_log` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_corrective_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_corrective_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_corrective_revision` FOREIGN KEY (`generated_revision_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_corrective_schedule` FOREIGN KEY (`schedule_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_corrective_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `project_progress_snapshot` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_corrective_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_cost_subject_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_cost_subject_scope` (
  `id` bigint NOT NULL COMMENT 'ä¸»é”®',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'ç§Ÿæˆ·ID',
  `project_id` bigint NOT NULL COMMENT 'é¡¹ç›®ID',
  `cost_subject_id` bigint NOT NULL COMMENT 'æˆæœ¬ç§‘ç›®ID',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `effective_from` date NOT NULL COMMENT 'ç”Ÿæ•ˆå¼€å§‹æ—¥æœŸ',
  `effective_to` date DEFAULT NULL COMMENT 'ç”Ÿæ•ˆç»“æŸæ—¥æœŸ',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬',
  `created_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_by` bigint DEFAULT NULL COMMENT 'æ›´æ–°äººID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_cost_subject_scope` (`tenant_id`,`project_id`,`cost_subject_id`),
  KEY `idx_project_cost_subject_enabled` (`tenant_id`,`project_id`,`enabled`),
  KEY `fk_project_cost_subject_project` (`project_id`),
  KEY `fk_project_cost_subject_subject` (`cost_subject_id`),
  CONSTRAINT `fk_project_cost_subject_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_cost_subject_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_cost_subject_dates` CHECK (((`effective_to` is null) or (`effective_to` >= `effective_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目可用成本科目范围';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_period_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_period_plan` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `schedule_plan_id` bigint NOT NULL,
  `parent_period_plan_id` bigint DEFAULT NULL,
  `period_type` varchar(20) NOT NULL,
  `period_code` varchar(64) NOT NULL,
  `period_name` varchar(200) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_period_code` (`tenant_id`,`project_id`,`period_code`,`deleted_flag`),
  KEY `idx_project_period_date` (`tenant_id`,`project_id`,`period_type`,`start_date`,`end_date`,`status`),
  KEY `fk_project_period_project` (`project_id`),
  KEY `fk_project_period_schedule` (`schedule_plan_id`),
  KEY `fk_project_period_parent` (`parent_period_plan_id`),
  KEY `fk_project_period_approval` (`approval_instance_id`),
  CONSTRAINT `fk_project_period_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_period_parent` FOREIGN KEY (`parent_period_plan_id`) REFERENCES `project_period_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_period_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_period_schedule` FOREIGN KEY (`schedule_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_period_dates` CHECK ((`start_date` <= `end_date`)),
  CONSTRAINT `ck_project_period_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_project_period_type` CHECK ((`period_type` in (_utf8mb4'MONTHLY',_utf8mb4'WEEKLY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_period_plan_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_period_plan_item` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `period_plan_id` bigint NOT NULL,
  `wbs_task_id` bigint NOT NULL,
  `target_progress` decimal(7,4) NOT NULL,
  `planned_quantity` decimal(18,4) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_period_item` (`tenant_id`,`period_plan_id`,`wbs_task_id`),
  KEY `fk_project_period_item_plan` (`period_plan_id`),
  KEY `fk_project_period_item_task` (`wbs_task_id`),
  CONSTRAINT `fk_project_period_item_plan` FOREIGN KEY (`period_plan_id`) REFERENCES `project_period_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_period_item_task` FOREIGN KEY (`wbs_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_period_item_progress` CHECK (((`target_progress` >= 0) and (`target_progress` <= 100) and ((`planned_quantity` is null) or (`planned_quantity` >= 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_progress_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_progress_snapshot` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `schedule_plan_id` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  `source_daily_log_id` bigint DEFAULT NULL,
  `planned_progress` decimal(7,4) NOT NULL,
  `actual_progress` decimal(7,4) NOT NULL,
  `deviation_percent` decimal(7,4) NOT NULL,
  `lagging_task_count` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL,
  `formula_version` varchar(40) NOT NULL DEFAULT 'SCHEDULE_PROGRESS_V1',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_progress_snapshot` (`tenant_id`,`schedule_plan_id`,`snapshot_date`),
  KEY `fk_project_snapshot_project` (`project_id`),
  KEY `fk_project_snapshot_schedule` (`schedule_plan_id`),
  KEY `fk_project_snapshot_log` (`source_daily_log_id`),
  CONSTRAINT `fk_project_snapshot_log` FOREIGN KEY (`source_daily_log_id`) REFERENCES `site_daily_log` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_snapshot_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_snapshot_schedule` FOREIGN KEY (`schedule_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_snapshot_progress` CHECK (((`planned_progress` >= 0) and (`planned_progress` <= 100) and (`actual_progress` >= 0) and (`actual_progress` <= 100))),
  CONSTRAINT `ck_project_snapshot_status` CHECK ((`status` in (_utf8mb4'ON_TRACK',_utf8mb4'LAGGING',_utf8mb4'OVERDUE',_utf8mb4'COMPLETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_schedule_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_schedule_plan` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(200) NOT NULL,
  `plan_type` varchar(20) NOT NULL DEFAULT 'BASELINE',
  `version_no` int NOT NULL,
  `parent_plan_id` bigint DEFAULT NULL,
  `corrective_action_id` bigint DEFAULT NULL,
  `planned_start_date` date NOT NULL,
  `planned_end_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `activated_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_schedule_code` (`tenant_id`,`project_id`,`plan_code`,`deleted_flag`),
  UNIQUE KEY `uk_project_schedule_version` (`tenant_id`,`project_id`,`version_no`,`deleted_flag`),
  KEY `idx_project_schedule_active` (`tenant_id`,`project_id`,`status`),
  KEY `fk_project_schedule_project` (`project_id`),
  KEY `fk_project_schedule_parent` (`parent_plan_id`),
  KEY `fk_project_schedule_approval` (`approval_instance_id`),
  KEY `fk_project_schedule_corrective` (`corrective_action_id`),
  CONSTRAINT `fk_project_schedule_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_schedule_corrective` FOREIGN KEY (`corrective_action_id`) REFERENCES `project_corrective_action` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_schedule_parent` FOREIGN KEY (`parent_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_schedule_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_schedule_dates` CHECK ((`planned_start_date` <= `planned_end_date`)),
  CONSTRAINT `ck_project_schedule_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING',_utf8mb4'ACTIVE',_utf8mb4'REJECTED',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `ck_project_schedule_type` CHECK ((`plan_type` in (_utf8mb4'BASELINE',_utf8mb4'REVISION')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_wbs_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_wbs_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `schedule_plan_id` bigint NOT NULL,
  `parent_task_id` bigint DEFAULT NULL,
  `predecessor_task_id` bigint DEFAULT NULL,
  `task_code` varchar(64) NOT NULL,
  `task_name` varchar(200) NOT NULL,
  `work_area` varchar(200) DEFAULT NULL,
  `responsible_user_id` bigint DEFAULT NULL,
  `planned_start_date` date NOT NULL,
  `planned_end_date` date NOT NULL,
  `weight_percent` decimal(7,4) NOT NULL,
  `planned_quantity` decimal(18,4) DEFAULT NULL,
  `unit` varchar(30) DEFAULT NULL,
  `actual_start_date` date DEFAULT NULL,
  `actual_end_date` date DEFAULT NULL,
  `actual_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `actual_progress` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `status` varchar(20) NOT NULL DEFAULT 'NOT_STARTED',
  `sort_order` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_wbs_code` (`tenant_id`,`schedule_plan_id`,`task_code`,`deleted_flag`),
  UNIQUE KEY `uk_project_wbs_tenant_id` (`tenant_id`,`id`),
  KEY `idx_project_wbs_project_date` (`tenant_id`,`project_id`,`planned_start_date`,`planned_end_date`),
  KEY `fk_project_wbs_project` (`project_id`),
  KEY `fk_project_wbs_schedule` (`schedule_plan_id`),
  KEY `fk_project_wbs_parent` (`parent_task_id`),
  KEY `fk_project_wbs_predecessor` (`predecessor_task_id`),
  CONSTRAINT `fk_project_wbs_parent` FOREIGN KEY (`parent_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_wbs_predecessor` FOREIGN KEY (`predecessor_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_wbs_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_project_wbs_schedule` FOREIGN KEY (`schedule_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_project_wbs_dates` CHECK ((`planned_start_date` <= `planned_end_date`)),
  CONSTRAINT `ck_project_wbs_progress` CHECK (((`actual_progress` >= 0) and (`actual_progress` <= 100) and (`actual_quantity` >= 0))),
  CONSTRAINT `ck_project_wbs_weight` CHECK (((`weight_percent` > 0) and (`weight_percent` <= 100)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_consequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_consequence` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `issue_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `consequence_code` varchar(64) NOT NULL,
  `decision_type` varchar(20) NOT NULL COMMENT 'NONE/FINE/REWORK_COST/BOTH',
  `fine_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `rework_cost_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `evaluation_score` decimal(5,2) NOT NULL,
  `evaluation_comment` varchar(1000) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `cost_item_id` bigint DEFAULT NULL,
  `evaluation_id` bigint DEFAULT NULL,
  `posted_by` bigint DEFAULT NULL,
  `posted_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `cost_subject_id` bigint DEFAULT NULL COMMENT 'V2质量安全成本末级科目',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_consequence_issue` (`tenant_id`,`issue_id`,`deleted_flag`),
  UNIQUE KEY `uk_qs_consequence_code` (`tenant_id`,`project_id`,`consequence_code`,`deleted_flag`),
  KEY `idx_qs_consequence_partner` (`tenant_id`,`partner_id`,`status`),
  KEY `fk_qs_consequence_issue` (`issue_id`),
  KEY `fk_qs_consequence_project` (`project_id`),
  KEY `fk_qs_consequence_partner` (`partner_id`),
  KEY `fk_qs_consequence_contract` (`contract_id`),
  KEY `fk_qs_consequence_cost` (`cost_item_id`),
  KEY `fk_qs_consequence_evaluation` (`evaluation_id`),
  KEY `fk_qs_consequence_subject` (`cost_subject_id`),
  CONSTRAINT `fk_qs_consequence_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_cost` FOREIGN KEY (`cost_item_id`) REFERENCES `cost_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_evaluation` FOREIGN KEY (`evaluation_id`) REFERENCES `qs_partner_evaluation` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_issue` FOREIGN KEY (`issue_id`) REFERENCES `qs_issue` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_consequence_subject` FOREIGN KEY (`cost_subject_id`) REFERENCES `cost_subject` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_consequence_amount` CHECK (((`fine_amount` >= 0) and (`rework_cost_amount` >= 0))),
  CONSTRAINT `ck_qs_consequence_decision` CHECK ((`decision_type` in (_utf8mb4'NONE',_utf8mb4'FINE',_utf8mb4'REWORK_COST',_utf8mb4'BOTH'))),
  CONSTRAINT `ck_qs_consequence_score` CHECK (((`evaluation_score` >= 0) and (`evaluation_score` <= 100))),
  CONSTRAINT `ck_qs_consequence_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'POSTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问题处罚与成本后果';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_inspection_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_inspection_plan` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(200) NOT NULL,
  `inspection_type` varchar(16) NOT NULL COMMENT 'QUALITY/SAFETY',
  `frequency_type` varchar(16) NOT NULL COMMENT 'SINGLE/WEEKLY/MONTHLY',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `activated_by` bigint DEFAULT NULL,
  `activated_at` datetime DEFAULT NULL,
  `completed_by` bigint DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_plan_code` (`tenant_id`,`project_id`,`plan_code`,`deleted_flag`),
  KEY `idx_qs_plan_project_status` (`tenant_id`,`project_id`,`status`,`start_date`),
  KEY `fk_qs_plan_project` (`project_id`),
  CONSTRAINT `fk_qs_plan_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_plan_dates` CHECK ((`end_date` >= `start_date`)),
  CONSTRAINT `ck_qs_plan_frequency` CHECK ((`frequency_type` in (_utf8mb4'SINGLE',_utf8mb4'WEEKLY',_utf8mb4'MONTHLY'))),
  CONSTRAINT `ck_qs_plan_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_qs_plan_type` CHECK ((`inspection_type` in (_utf8mb4'QUALITY',_utf8mb4'SAFETY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量安全检查计划';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_inspection_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_inspection_record` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `plan_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `inspection_code` varchar(64) NOT NULL,
  `inspection_date` date NOT NULL,
  `location` varchar(200) NOT NULL,
  `inspector_user_id` bigint NOT NULL,
  `conclusion` varchar(16) NOT NULL DEFAULT 'PENDING',
  `summary` varchar(1000) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_inspection_code` (`tenant_id`,`project_id`,`inspection_code`,`deleted_flag`),
  KEY `idx_qs_inspection_plan` (`tenant_id`,`plan_id`,`inspection_date`,`status`),
  KEY `fk_qs_inspection_plan` (`plan_id`),
  KEY `fk_qs_inspection_project` (`project_id`),
  CONSTRAINT `fk_qs_inspection_plan` FOREIGN KEY (`plan_id`) REFERENCES `qs_inspection_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_inspection_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_inspection_conclusion` CHECK ((`conclusion` in (_utf8mb4'PENDING',_utf8mb4'PASS',_utf8mb4'ISSUES'))),
  CONSTRAINT `ck_qs_inspection_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量安全检查记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_issue` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `plan_id` bigint NOT NULL,
  `inspection_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `issue_code` varchar(64) NOT NULL,
  `issue_type` varchar(16) NOT NULL COMMENT 'QUALITY/SAFETY',
  `category` varchar(100) NOT NULL,
  `severity` varchar(16) NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` varchar(2000) NOT NULL,
  `responsible_kind` varchar(16) NOT NULL COMMENT 'INTERNAL/PARTNER',
  `responsible_partner_id` bigint DEFAULT NULL,
  `responsible_user_id` bigint NOT NULL,
  `due_date` date NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `closed_by` bigint DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_issue_code` (`tenant_id`,`project_id`,`issue_code`,`deleted_flag`),
  KEY `idx_qs_issue_record` (`tenant_id`,`inspection_id`,`status`),
  KEY `idx_qs_issue_responsible` (`tenant_id`,`responsible_user_id`,`due_date`,`status`),
  KEY `idx_qs_issue_partner` (`tenant_id`,`responsible_partner_id`,`status`),
  KEY `fk_qs_issue_plan` (`plan_id`),
  KEY `fk_qs_issue_inspection` (`inspection_id`),
  KEY `fk_qs_issue_project` (`project_id`),
  KEY `fk_qs_issue_partner` (`responsible_partner_id`),
  CONSTRAINT `fk_qs_issue_inspection` FOREIGN KEY (`inspection_id`) REFERENCES `qs_inspection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_issue_partner` FOREIGN KEY (`responsible_partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_issue_plan` FOREIGN KEY (`plan_id`) REFERENCES `qs_inspection_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_issue_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_issue_responsible` CHECK ((((`responsible_kind` = _utf8mb4'INTERNAL') and (`responsible_partner_id` is null)) or ((`responsible_kind` = _utf8mb4'PARTNER') and (`responsible_partner_id` is not null)))),
  CONSTRAINT `ck_qs_issue_severity` CHECK ((`severity` in (_utf8mb4'LOW',_utf8mb4'MEDIUM',_utf8mb4'HIGH',_utf8mb4'CRITICAL'))),
  CONSTRAINT `ck_qs_issue_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'RECTIFYING',_utf8mb4'PENDING_REINSPECTION',_utf8mb4'CLOSED'))),
  CONSTRAINT `ck_qs_issue_type` CHECK ((`issue_type` in (_utf8mb4'QUALITY',_utf8mb4'SAFETY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质量安全问题单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_partner_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_partner_evaluation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `consequence_id` bigint NOT NULL,
  `issue_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `evaluation_type` varchar(16) NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `evaluation_comment` varchar(1000) NOT NULL,
  `evaluated_by` bigint NOT NULL,
  `evaluated_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_partner_evaluation` (`tenant_id`,`consequence_id`,`deleted_flag`),
  KEY `idx_qs_partner_score` (`tenant_id`,`partner_id`,`evaluation_type`,`evaluated_at`),
  KEY `fk_qs_eval_consequence` (`consequence_id`),
  KEY `fk_qs_eval_issue` (`issue_id`),
  KEY `fk_qs_eval_project` (`project_id`),
  KEY `fk_qs_eval_partner` (`partner_id`),
  CONSTRAINT `fk_qs_eval_consequence` FOREIGN KEY (`consequence_id`) REFERENCES `qs_consequence` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_eval_issue` FOREIGN KEY (`issue_id`) REFERENCES `qs_issue` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_eval_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_eval_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_eval_score` CHECK (((`score` >= 0) and (`score` <= 100))),
  CONSTRAINT `ck_qs_eval_type` CHECK ((`evaluation_type` in (_utf8mb4'QUALITY',_utf8mb4'SAFETY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商或分包商质量安全评价事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qs_rectification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qs_rectification` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `issue_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `round_no` int NOT NULL,
  `action_description` varchar(2000) NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  `planned_complete_date` date NOT NULL,
  `actual_completed_at` datetime DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `reinspection_comment` varchar(1000) DEFAULT NULL,
  `reinspected_by` bigint DEFAULT NULL,
  `reinspected_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qs_rectification_round` (`tenant_id`,`issue_id`,`round_no`,`deleted_flag`),
  KEY `idx_qs_rectification_issue` (`tenant_id`,`issue_id`,`status`),
  KEY `fk_qs_rectification_issue` (`issue_id`),
  KEY `fk_qs_rectification_project` (`project_id`),
  CONSTRAINT `fk_qs_rectification_issue` FOREIGN KEY (`issue_id`) REFERENCES `qs_issue` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_qs_rectification_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_qs_rectification_round` CHECK ((`round_no` > 0)),
  CONSTRAINT `ck_qs_rectification_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'PASSED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问题整改与复验轮次';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `receivable_adjustment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receivable_adjustment` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `receivable_id` bigint NOT NULL,
  `adjustment_type` varchar(32) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receivable_adjustment_key` (`tenant_id`,`idempotency_key`),
  KEY `fk_receivable_adjustment_ar` (`receivable_id`),
  CONSTRAINT `fk_receivable_adjustment_ar` FOREIGN KEY (`receivable_id`) REFERENCES `account_receivable` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_receivable_adjustment_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_audit_event` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `event_type` varchar(64) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `event_at` datetime NOT NULL,
  `archive_bucket` varchar(32) NOT NULL DEFAULT 'HOT',
  `payload_json` longtext NOT NULL,
  `payload_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_revenue_audit_search` (`tenant_id`,`business_type`,`business_id`,`event_at`),
  KEY `idx_revenue_audit_archive` (`tenant_id`,`archive_bucket`,`event_at`),
  CONSTRAINT `ck_revenue_audit_payload_json` CHECK ((json_valid(`payload_json`) and (length(`payload_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_dashboard_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_dashboard_snapshot` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  `formula_version` varchar(64) NOT NULL,
  `confirmed_revenue` decimal(18,2) NOT NULL,
  `settled_amount` decimal(18,2) NOT NULL,
  `receivable_amount` decimal(18,2) NOT NULL,
  `outstanding_amount` decimal(18,2) NOT NULL,
  `overdue_amount` decimal(18,2) NOT NULL,
  `collected_amount` decimal(18,2) NOT NULL,
  `invoiced_amount` decimal(18,2) NOT NULL,
  `collection_rate` decimal(12,6) NOT NULL,
  `refreshed_at` datetime NOT NULL,
  `refresh_mode` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_snapshot` (`tenant_id`,`project_id`,`snapshot_date`),
  KEY `fk_revenue_snapshot_project` (`project_id`),
  CONSTRAINT `fk_revenue_snapshot_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_external_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_external_sync` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `endpoint_id` bigint NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `message_id` bigint DEFAULT NULL,
  `sync_status` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `synced_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_external_sync` (`tenant_id`,`endpoint_id`,`idempotency_key`),
  KEY `fk_revenue_sync_endpoint` (`endpoint_id`),
  KEY `fk_revenue_sync_message` (`message_id`),
  CONSTRAINT `fk_revenue_sync_endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `finance_integration_endpoint` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_revenue_sync_message` FOREIGN KEY (`message_id`) REFERENCES `finance_integration_message` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_import_batch` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `import_type` varchar(32) NOT NULL,
  `project_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_hash` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `total_rows` int NOT NULL,
  `valid_rows` int NOT NULL,
  `invalid_rows` int NOT NULL,
  `diff_summary_json` longtext,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `applied_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_import_hash` (`tenant_id`,`import_type`,`project_id`,`file_hash`),
  KEY `fk_revenue_import_project` (`project_id`),
  CONSTRAINT `fk_revenue_import_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_revenue_import_summary_json` CHECK (((`diff_summary_json` is null) or (json_valid(`diff_summary_json`) and (length(`diff_summary_json`) <= 1048576))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_import_row`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_import_row` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `batch_id` bigint NOT NULL,
  `row_no` int NOT NULL,
  `input_json` longtext NOT NULL,
  `diff_json` longtext,
  `validation_status` varchar(32) NOT NULL,
  `validation_message` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_import_row` (`batch_id`,`row_no`),
  CONSTRAINT `fk_revenue_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `revenue_import_batch` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_revenue_import_diff_json` CHECK (((`diff_json` is null) or (json_valid(`diff_json`) and (length(`diff_json`) <= 1048576)))),
  CONSTRAINT `ck_revenue_import_input_json` CHECK ((json_valid(`input_json`) and (length(`input_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_reconciliation_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_reconciliation_issue` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `run_id` bigint NOT NULL,
  `dimension_type` varchar(64) NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `issue_code` varchar(64) NOT NULL,
  `expected_amount` decimal(18,2) DEFAULT NULL,
  `actual_amount` decimal(18,2) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `detail` varchar(1000) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_revenue_recon_issue` (`tenant_id`,`run_id`,`status`),
  KEY `fk_revenue_recon_issue_run` (`run_id`),
  CONSTRAINT `fk_revenue_recon_issue_run` FOREIGN KEY (`run_id`) REFERENCES `revenue_reconciliation_run` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `revenue_reconciliation_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revenue_reconciliation_run` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `business_date` date NOT NULL,
  `status` varchar(32) NOT NULL,
  `issue_count` int NOT NULL DEFAULT '0',
  `started_at` datetime NOT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revenue_recon_day` (`tenant_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sales_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_invoice` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `invoice_code` varchar(64) DEFAULT NULL,
  `invoice_no` varchar(128) NOT NULL,
  `invoice_type` varchar(32) NOT NULL,
  `invoice_date` date NOT NULL,
  `amount_without_tax` decimal(18,2) NOT NULL,
  `tax_amount` decimal(18,2) NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `status` varchar(32) NOT NULL DEFAULT 'ISSUED',
  `verification_status` varchar(32) NOT NULL DEFAULT 'UNVERIFIED',
  `attachment_count` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_invoice_no` (`tenant_id`,`invoice_no`,`deleted_flag`),
  KEY `idx_sales_invoice_contract` (`tenant_id`,`contract_id`,`invoice_date`),
  KEY `fk_sales_invoice_project` (`project_id`),
  KEY `fk_sales_invoice_contract` (`contract_id`),
  KEY `fk_sales_invoice_customer` (`customer_id`),
  CONSTRAINT `fk_sales_invoice_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sales_invoice_customer` FOREIGN KEY (`customer_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sales_invoice_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sales_invoice_amount` CHECK (((`amount_without_tax` >= 0) and (`tax_amount` >= 0) and (`total_amount` = (`amount_without_tax` + `tax_amount`)) and (`allocated_amount` >= 0) and (`allocated_amount` <= `total_amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sales_invoice_allocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_invoice_allocation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `invoice_id` bigint NOT NULL,
  `receivable_id` bigint NOT NULL,
  `allocated_amount` decimal(18,2) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_invoice_receivable` (`tenant_id`,`invoice_id`,`receivable_id`),
  KEY `fk_sales_alloc_invoice` (`invoice_id`),
  KEY `fk_sales_alloc_receivable` (`receivable_id`),
  CONSTRAINT `fk_sales_alloc_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `sales_invoice` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sales_alloc_receivable` FOREIGN KEY (`receivable_id`) REFERENCES `account_receivable` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sales_alloc_amount` CHECK ((`allocated_amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sales_invoice_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_invoice_review` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `invoice_id` bigint NOT NULL,
  `raw_result_json` longtext NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `comparison_json` longtext,
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_note` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sales_invoice_review` (`tenant_id`,`review_status`,`confidence`),
  KEY `fk_sales_invoice_review_invoice` (`invoice_id`),
  CONSTRAINT `fk_sales_invoice_review_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `sales_invoice` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sales_invoice_comparison_json` CHECK (((`comparison_json` is null) or (json_valid(`comparison_json`) and (length(`comparison_json`) <= 1048576)))),
  CONSTRAINT `ck_sales_invoice_raw_json` CHECK ((json_valid(`raw_result_json`) and (length(`raw_result_json`) <= 1048576)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlement_sub_measure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_sub_measure` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `settlement_id` bigint NOT NULL,
  `sub_measure_id` bigint NOT NULL,
  `reported_amount_snapshot` decimal(18,2) NOT NULL,
  `approved_amount_snapshot` decimal(18,2) NOT NULL,
  `deduction_amount_snapshot` decimal(18,2) NOT NULL,
  `net_amount_snapshot` decimal(18,2) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_measure` (`tenant_id`,`settlement_id`,`sub_measure_id`),
  UNIQUE KEY `uk_measure_final_settlement` (`tenant_id`,`sub_measure_id`),
  KEY `fk_settlement_measure_settlement` (`settlement_id`),
  KEY `fk_settlement_measure_measure` (`sub_measure_id`),
  KEY `idx_settlement_measure_trace` (`tenant_id`,`sub_measure_id`,`settlement_id`),
  CONSTRAINT `fk_settlement_measure_measure` FOREIGN KEY (`sub_measure_id`) REFERENCES `sub_measure` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_settlement_measure_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `stl_settlement` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='终期结算分包计量快照关系';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `site_daily_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_daily_log` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `report_date` date NOT NULL,
  `construction_content` text NOT NULL,
  `issues_delays` text,
  `next_day_plan` text,
  `weather_summary` varchar(200) DEFAULT NULL COMMENT '人工天气摘要',
  `on_site_headcount` int DEFAULT NULL COMMENT '在场人数；NULL 未填写，0 明确无人',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_daily_project_date` (`tenant_id`,`project_id`,`report_date`),
  KEY `idx_site_daily_project_date` (`tenant_id`,`project_id`,`report_date`,`status`),
  CONSTRAINT `ck_site_daily_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目现场日报';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `site_daily_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_daily_progress` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `daily_log_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `schedule_plan_id` bigint NOT NULL,
  `weekly_plan_id` bigint NOT NULL,
  `wbs_task_id` bigint NOT NULL,
  `previous_progress` decimal(7,4) NOT NULL,
  `current_progress` decimal(7,4) NOT NULL,
  `completed_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `work_description` varchar(500) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_daily_progress` (`tenant_id`,`daily_log_id`,`wbs_task_id`),
  KEY `idx_site_daily_progress_task` (`tenant_id`,`wbs_task_id`,`daily_log_id`),
  KEY `fk_site_daily_progress_log` (`daily_log_id`),
  KEY `fk_site_daily_progress_project` (`project_id`),
  KEY `fk_site_daily_progress_schedule` (`schedule_plan_id`),
  KEY `fk_site_daily_progress_week` (`weekly_plan_id`),
  KEY `fk_site_daily_progress_task` (`wbs_task_id`),
  CONSTRAINT `fk_site_daily_progress_log` FOREIGN KEY (`daily_log_id`) REFERENCES `site_daily_log` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_site_daily_progress_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_site_daily_progress_schedule` FOREIGN KEY (`schedule_plan_id`) REFERENCES `project_schedule_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_site_daily_progress_task` FOREIGN KEY (`wbs_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_site_daily_progress_week` FOREIGN KEY (`weekly_plan_id`) REFERENCES `project_period_plan` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_site_daily_progress_value` CHECK (((`previous_progress` >= 0) and (`current_progress` >= `previous_progress`) and (`current_progress` <= 100) and (`completed_quantity` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_bid_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_bid_evaluation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `sourcing_event_id` bigint NOT NULL,
  `quote_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `commercial_score` decimal(5,2) NOT NULL,
  `technical_score` decimal(5,2) NOT NULL,
  `delivery_score` decimal(5,2) NOT NULL,
  `quality_score` decimal(5,2) NOT NULL,
  `total_score` decimal(5,2) NOT NULL,
  `evaluation_comment` varchar(1000) NOT NULL,
  `evaluated_by` bigint NOT NULL,
  `evaluated_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_bid_evaluation` (`tenant_id`,`quote_id`,`deleted_flag`),
  KEY `idx_sp_bid_event_score` (`tenant_id`,`sourcing_event_id`,`total_score`),
  KEY `fk_sp_bid_event` (`sourcing_event_id`),
  KEY `fk_sp_bid_quote` (`quote_id`),
  KEY `fk_sp_bid_partner` (`partner_id`),
  CONSTRAINT `fk_sp_bid_event` FOREIGN KEY (`sourcing_event_id`) REFERENCES `sp_sourcing_event` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_bid_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_bid_quote` FOREIGN KEY (`quote_id`) REFERENCES `sp_supplier_quote` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_bid_scores` CHECK (((`commercial_score` between 0 and 100) and (`technical_score` between 0 and 100) and (`delivery_score` between 0 and 100) and (`quality_score` between 0 and 100) and (`total_score` between 0 and 100)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商比价评审';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_blacklist_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_blacklist_record` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `performance_evaluation_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `action_type` varchar(12) NOT NULL DEFAULT 'ADD',
  `reason` varchar(1000) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_comment` varchar(1000) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_blacklist_evaluation` (`tenant_id`,`performance_evaluation_id`,`deleted_flag`),
  KEY `idx_sp_blacklist_partner` (`tenant_id`,`partner_id`,`status`),
  KEY `fk_sp_blacklist_evaluation` (`performance_evaluation_id`),
  KEY `fk_sp_blacklist_partner` (`partner_id`),
  KEY `fk_sp_blacklist_project` (`project_id`),
  CONSTRAINT `fk_sp_blacklist_evaluation` FOREIGN KEY (`performance_evaluation_id`) REFERENCES `sp_performance_evaluation` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_blacklist_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_blacklist_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_blacklist_action` CHECK ((`action_type` = _utf8mb4'ADD')),
  CONSTRAINT `ck_sp_blacklist_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'APPROVED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商黑名单审批记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_performance_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_performance_evaluation` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `evaluation_code` varchar(64) NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `delivery_score` decimal(5,2) NOT NULL,
  `quality_score` decimal(5,2) NOT NULL,
  `service_score` decimal(5,2) NOT NULL,
  `commercial_score` decimal(5,2) NOT NULL,
  `total_score` decimal(5,2) NOT NULL,
  `grade` varchar(8) NOT NULL,
  `on_time_flag` tinyint NOT NULL,
  `approved_receipt_count` int NOT NULL DEFAULT '0',
  `unqualified_receipt_count` int NOT NULL DEFAULT '0',
  `return_count` int NOT NULL DEFAULT '0',
  `finalized_settlement_count` int NOT NULL DEFAULT '0',
  `quality_safety_fact_count` int NOT NULL DEFAULT '0',
  `quality_safety_average` decimal(5,2) DEFAULT NULL,
  `evaluation_comment` varchar(1000) NOT NULL,
  `recommend_blacklist` tinyint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_performance_order` (`tenant_id`,`purchase_order_id`,`deleted_flag`),
  UNIQUE KEY `uk_sp_performance_code` (`tenant_id`,`evaluation_code`,`deleted_flag`),
  KEY `idx_sp_performance_partner` (`tenant_id`,`partner_id`,`status`,`period_end`),
  KEY `fk_sp_performance_project` (`project_id`),
  KEY `fk_sp_performance_partner` (`partner_id`),
  KEY `fk_sp_performance_contract` (`contract_id`),
  KEY `fk_sp_performance_order` (`purchase_order_id`),
  CONSTRAINT `fk_sp_performance_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_performance_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `mat_purchase_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_performance_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_performance_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_performance_dates` CHECK ((`period_end` >= `period_start`)),
  CONSTRAINT `ck_sp_performance_scores` CHECK (((`delivery_score` between 0 and 100) and (`quality_score` between 0 and 100) and (`service_score` between 0 and 100) and (`commercial_score` between 0 and 100) and (`total_score` between 0 and 100))),
  CONSTRAINT `ck_sp_performance_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CONFIRMED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商履约综合评价';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_sourcing_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_sourcing_event` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `purchase_request_id` bigint NOT NULL,
  `sourcing_code` varchar(64) NOT NULL,
  `sourcing_title` varchar(200) NOT NULL,
  `sourcing_type` varchar(16) NOT NULL COMMENT 'INQUIRY/TENDER',
  `deadline` datetime NOT NULL,
  `currency_code` varchar(8) NOT NULL DEFAULT 'CNY',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `awarded_quote_id` bigint DEFAULT NULL,
  `awarded_partner_id` bigint DEFAULT NULL,
  `contract_id` bigint DEFAULT NULL,
  `award_reason` varchar(1000) DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `awarded_by` bigint DEFAULT NULL,
  `awarded_at` datetime DEFAULT NULL,
  `contracted_by` bigint DEFAULT NULL,
  `contracted_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_sourcing_code` (`tenant_id`,`sourcing_code`,`deleted_flag`),
  UNIQUE KEY `uk_sp_sourcing_request` (`tenant_id`,`purchase_request_id`,`deleted_flag`),
  KEY `idx_sp_sourcing_project_status` (`tenant_id`,`project_id`,`status`,`deadline`),
  KEY `fk_sp_sourcing_project` (`project_id`),
  KEY `fk_sp_sourcing_request` (`purchase_request_id`),
  KEY `fk_sp_sourcing_partner` (`awarded_partner_id`),
  KEY `fk_sp_sourcing_contract` (`contract_id`),
  KEY `fk_sp_sourcing_award_quote` (`awarded_quote_id`),
  CONSTRAINT `fk_sp_sourcing_award_quote` FOREIGN KEY (`awarded_quote_id`) REFERENCES `sp_supplier_quote` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_sourcing_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_sourcing_partner` FOREIGN KEY (`awarded_partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_sourcing_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_sourcing_request` FOREIGN KEY (`purchase_request_id`) REFERENCES `mat_purchase_request` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_sourcing_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'EVALUATING',_utf8mb4'AWARDED',_utf8mb4'CONTRACTED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `ck_sp_sourcing_type` CHECK ((`sourcing_type` in (_utf8mb4'INQUIRY',_utf8mb4'TENDER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商询价招标事件';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_sourcing_supplier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_sourcing_supplier` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `sourcing_event_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `invitation_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `invited_at` datetime DEFAULT NULL,
  `responded_at` datetime DEFAULT NULL,
  `disqualification_reason` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_sourcing_supplier` (`tenant_id`,`sourcing_event_id`,`partner_id`,`deleted_flag`),
  KEY `idx_sp_supplier_partner` (`tenant_id`,`partner_id`,`invitation_status`),
  KEY `fk_sp_supplier_event` (`sourcing_event_id`),
  KEY `fk_sp_supplier_partner` (`partner_id`),
  CONSTRAINT `fk_sp_supplier_event` FOREIGN KEY (`sourcing_event_id`) REFERENCES `sp_sourcing_event` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_supplier_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_supplier_status` CHECK ((`invitation_status` in (_utf8mb4'PENDING',_utf8mb4'INVITED',_utf8mb4'DECLINED',_utf8mb4'QUOTED',_utf8mb4'DISQUALIFIED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招采受邀供应商';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_supplier_quote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_supplier_quote` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `sourcing_event_id` bigint NOT NULL,
  `sourcing_supplier_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `quote_code` varchar(64) NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  `tax_rate` decimal(8,4) NOT NULL DEFAULT '0.0000',
  `delivery_days` int NOT NULL,
  `validity_date` date NOT NULL,
  `commercial_terms` varchar(2000) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_quote_supplier` (`tenant_id`,`sourcing_event_id`,`partner_id`,`deleted_flag`),
  UNIQUE KEY `uk_sp_quote_code` (`tenant_id`,`quote_code`,`deleted_flag`),
  KEY `idx_sp_quote_event_status` (`tenant_id`,`sourcing_event_id`,`status`,`total_amount`),
  KEY `fk_sp_quote_event` (`sourcing_event_id`),
  KEY `fk_sp_quote_invitation` (`sourcing_supplier_id`),
  KEY `fk_sp_quote_partner` (`partner_id`),
  CONSTRAINT `fk_sp_quote_event` FOREIGN KEY (`sourcing_event_id`) REFERENCES `sp_sourcing_event` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_quote_invitation` FOREIGN KEY (`sourcing_supplier_id`) REFERENCES `sp_sourcing_supplier` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_quote_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_quote_amount` CHECK ((`total_amount` > 0)),
  CONSTRAINT `ck_sp_quote_delivery` CHECK ((`delivery_days` >= 0)),
  CONSTRAINT `ck_sp_quote_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'WINNER',_utf8mb4'LOST',_utf8mb4'INVALID')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商报价';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_supplier_return`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_supplier_return` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `partner_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `receipt_id` bigint NOT NULL,
  `warehouse_id` bigint DEFAULT NULL COMMENT '退货出库仓库ID',
  `return_code` varchar(64) NOT NULL,
  `return_date` date NOT NULL,
  `return_quantity` decimal(18,4) NOT NULL,
  `return_amount` decimal(18,2) NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '外部幂等键',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `reversed_by` bigint DEFAULT NULL COMMENT '冲销人',
  `reversed_at` datetime DEFAULT NULL COMMENT '冲销时间',
  `reversal_reason` varchar(500) DEFAULT NULL COMMENT '冲销原因',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sp_supplier_return_code` (`tenant_id`,`return_code`,`deleted_flag`),
  UNIQUE KEY `uk_sp_supplier_return_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_sp_supplier_return_idem` (`tenant_id`,`idempotency_key`),
  KEY `idx_sp_supplier_return_order` (`tenant_id`,`purchase_order_id`,`status`,`return_date`),
  KEY `fk_sp_return_project` (`project_id`),
  KEY `fk_sp_return_partner` (`partner_id`),
  KEY `fk_sp_return_contract` (`contract_id`),
  KEY `fk_sp_return_order` (`purchase_order_id`),
  KEY `fk_sp_return_receipt` (`receipt_id`),
  CONSTRAINT `fk_sp_return_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_return_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `mat_purchase_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_return_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_return_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sp_return_receipt` FOREIGN KEY (`receipt_id`) REFERENCES `mat_receipt` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sp_return_amount` CHECK ((`return_amount` >= 0)),
  CONSTRAINT `ck_sp_return_quantity` CHECK ((`return_quantity` > 0)),
  CONSTRAINT `ck_sp_return_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CONFIRMED',_utf8mb4'REVERSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商退货事实';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sp_supplier_return_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sp_supplier_return_item` (
  `id` bigint NOT NULL COMMENT '供应商退货明细ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `return_id` bigint NOT NULL COMMENT '供应商退货ID',
  `receipt_item_id` bigint NOT NULL COMMENT '原验收明细ID',
  `order_item_id` bigint NOT NULL COMMENT '采购订单明细ID',
  `quality_disposition_id` bigint DEFAULT NULL COMMENT '不合格处置ID；为空表示已入库合格品退货',
  `original_stock_txn_id` bigint DEFAULT NULL COMMENT '原验收入库流水ID',
  `original_cost_item_id` bigint DEFAULT NULL COMMENT '原直耗成本ID',
  `material_id` bigint NOT NULL COMMENT '材料ID',
  `return_source` varchar(20) NOT NULL COMMENT 'QUALIFIED/REJECTED',
  `quantity` decimal(18,4) NOT NULL COMMENT '退货数量',
  `unit_cost` decimal(18,4) NOT NULL COMMENT '单位成本',
  `amount` decimal(18,2) NOT NULL COMMENT '退货金额',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_return_receipt_line` (`tenant_id`,`return_id`,`receipt_item_id`),
  KEY `idx_supplier_return_item_source` (`tenant_id`,`receipt_item_id`,`return_source`),
  KEY `fk_supplier_return_item_disposition` (`quality_disposition_id`),
  KEY `fk_supplier_return_item_stock` (`original_stock_txn_id`),
  KEY `fk_supplier_return_item_cost` (`original_cost_item_id`),
  KEY `fk_supplier_return_item_material` (`material_id`),
  CONSTRAINT `fk_supplier_return_item_cost` FOREIGN KEY (`original_cost_item_id`) REFERENCES `cost_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_supplier_return_item_disposition` FOREIGN KEY (`quality_disposition_id`) REFERENCES `mat_quality_disposition` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_supplier_return_item_head` FOREIGN KEY (`tenant_id`, `return_id`) REFERENCES `sp_supplier_return` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_supplier_return_item_material` FOREIGN KEY (`material_id`) REFERENCES `md_material` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_supplier_return_item_receipt` FOREIGN KEY (`tenant_id`, `receipt_item_id`) REFERENCES `mat_receipt_item` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_supplier_return_item_stock` FOREIGN KEY (`original_stock_txn_id`) REFERENCES `mat_stock_txn` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_supplier_return_item_qty` CHECK (((`quantity` > 0) and (`unit_cost` >= 0) and (`amount` >= 0))),
  CONSTRAINT `ck_supplier_return_item_source` CHECK ((`return_source` in (_utf8mb4'QUALIFIED',_utf8mb4'REJECTED'))),
  CONSTRAINT `ck_supplier_return_item_source_ref` CHECK ((((`return_source` = _utf8mb4'QUALIFIED') and (`quality_disposition_id` is null) and ((`original_stock_txn_id` is not null) or (`original_cost_item_id` is not null))) or ((`return_source` = _utf8mb4'REJECTED') and (`quality_disposition_id` is not null) and (`original_stock_txn_id` is null) and (`original_cost_item_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商退货明细';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stl_settlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stl_settlement` (
  `id` bigint NOT NULL COMMENT '结算ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方ID',
  `settlement_code` varchar(64) NOT NULL COMMENT '结算单号',
  `settlement_type` varchar(50) DEFAULT NULL COMMENT '结算类型',
  `contract_amount` decimal(18,2) DEFAULT NULL COMMENT '合同金额',
  `change_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变更金额',
  `measured_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '计量金额',
  `deduction_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '扣款金额',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '已付金额',
  `final_amount` decimal(18,2) DEFAULT NULL COMMENT '结算金额',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `unpaid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '未付金额',
  `warranty_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '质保金金额',
  `settlement_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '结算状态：DRAFT草稿，FINALIZED已定案，CANCELLED已作废',
  `finalized_at` datetime DEFAULT NULL COMMENT '定案时间',
  `amount_formula_version` varchar(64) NOT NULL DEFAULT 'LEGACY_UNVERIFIED' COMMENT '结算金额口径版本；历史数据核对后方可回填当前版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stl_settlement_code` (`tenant_id`,`settlement_code`,`deleted_flag`),
  UNIQUE KEY `uk_stl_settlement_contract` (`tenant_id`,`contract_id`,`deleted_flag`),
  KEY `idx_stl_project` (`project_id`),
  KEY `idx_stl_contract` (`contract_id`),
  KEY `fk_settlement_partner` (`partner_id`),
  CONSTRAINT `fk_settlement_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_settlement_partner` FOREIGN KEY (`partner_id`) REFERENCES `md_partner` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_settlement_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stl_settlement_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stl_settlement_item` (
  `id` bigint NOT NULL COMMENT '结算明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `settlement_id` bigint NOT NULL COMMENT '结算单ID',
  `item_name` varchar(200) DEFAULT NULL COMMENT '清单项名称',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `cost_subject_id` bigint DEFAULT NULL COMMENT '成本科目ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `source_type` varchar(50) DEFAULT NULL COMMENT '来源类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证，CT_CONTRACT合同',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  PRIMARY KEY (`id`),
  KEY `idx_stl_si_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sub_measure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sub_measure` (
  `id` bigint NOT NULL COMMENT '计量ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '分包合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '分包商ID',
  `sub_task_id` bigint DEFAULT NULL COMMENT '关联分包任务ID',
  `measure_code` varchar(64) NOT NULL COMMENT '计量单号',
  `measure_period` varchar(50) DEFAULT NULL COMMENT '计量周期',
  `measure_date` date DEFAULT NULL COMMENT '计量日期',
  `reported_amount` decimal(18,2) DEFAULT NULL COMMENT '上报金额',
  `approved_amount` decimal(18,2) DEFAULT NULL COMMENT '审定金额',
  `deduction_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '扣款金额',
  `net_amount` decimal(18,2) DEFAULT NULL COMMENT '净计量金额',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `cost_generated_flag` tinyint NOT NULL DEFAULT '0' COMMENT '成本生成标识：0未生成，1已生成',
  `status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '计量状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_measure_code` (`tenant_id`,`measure_code`,`deleted_flag`),
  KEY `idx_sub_measure_project` (`project_id`),
  KEY `idx_sub_measure_contract` (`contract_id`),
  KEY `idx_sub_measure_sub_task_id` (`sub_task_id`),
  KEY `idx_sub_measure_payment_context` (`tenant_id`,`project_id`,`contract_id`,`partner_id`,`approval_status`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包计量表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sub_measure_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sub_measure_item` (
  `id` bigint NOT NULL COMMENT '计量明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `measure_id` bigint NOT NULL COMMENT '计量单ID',
  `contract_item_id` bigint DEFAULT NULL COMMENT '合同清单项ID',
  `item_name` varchar(200) DEFAULT NULL COMMENT '清单项名称',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `contract_quantity` decimal(18,4) DEFAULT NULL COMMENT '合同数量',
  `current_quantity` decimal(18,4) DEFAULT NULL COMMENT '本期数量',
  `cumulative_quantity` decimal(18,4) DEFAULT NULL COMMENT '累计数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_sub_mi_measure` (`measure_id`),
  KEY `idx_sub_measure_item_contract` (`tenant_id`,`measure_id`,`contract_item_id`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包计量明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sub_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sub_task` (
  `id` bigint NOT NULL COMMENT '分包任务ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '分包合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '分包商ID',
  `predecessor_task_id` bigint DEFAULT NULL COMMENT '单一前置分包任务ID',
  `task_code` varchar(64) NOT NULL COMMENT '任务编号',
  `task_name` varchar(200) NOT NULL COMMENT '任务名称',
  `work_area` varchar(200) DEFAULT NULL COMMENT '施工区域',
  `planned_start_date` date DEFAULT NULL COMMENT '计划开始日期',
  `planned_end_date` date DEFAULT NULL COMMENT '计划结束日期',
  `actual_start_date` date DEFAULT NULL COMMENT '实际开始日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际结束日期',
  `progress_percent` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '进度百分比',
  `status` varchar(50) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态：NOT_STARTED未开始，IN_PROGRESS进行中，COMPLETED已完成',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_task_code` (`tenant_id`,`task_code`,`deleted_flag`),
  KEY `idx_sub_task_project` (`project_id`),
  KEY `idx_sub_task_contract` (`contract_id`),
  KEY `idx_sub_task_predecessor` (`predecessor_task_id`),
  CONSTRAINT `fk_sub_task_predecessor` FOREIGN KEY (`predecessor_task_id`) REFERENCES `sub_task` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL COMMENT '字典数据ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `dict_type_id` bigint NOT NULL COMMENT '字典类型ID',
  `dict_label` varchar(200) NOT NULL COMMENT '字典标签（显示文本）',
  `dict_value` varchar(200) NOT NULL COMMENT '字典键值',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式类名',
  `list_class` varchar(100) DEFAULT NULL COMMENT '回显样式',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_data` (`dict_type_id`,`dict_value`),
  KEY `idx_sys_dict_data_type` (`dict_type_id`,`order_num`),
  KEY `idx_sys_dict_data_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '字典类型ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `dict_code` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(200) NOT NULL COMMENT '字典名称',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`tenant_id`,`dict_code`),
  KEY `idx_sys_dict_type_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL COMMENT '文件ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型（如CONTRACT、PROJECT等）',
  `business_id` bigint NOT NULL COMMENT '业务ID',
  `file_name` varchar(255) NOT NULL COMMENT '存储文件名（UUID.扩展名）',
  `original_name` varchar(500) NOT NULL COMMENT '原始文件名',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `content_type` varchar(200) DEFAULT NULL COMMENT '文件MIME类型',
  `storage_path` varchar(500) NOT NULL COMMENT 'MinIO对象路径（businessType/businessId/fileName）',
  `bucket_name` varchar(100) NOT NULL DEFAULT 'cgc-pms' COMMENT 'MinIO桶名称',
  `virus_scan_status` varchar(20) NOT NULL DEFAULT 'NOT_SCANNED' COMMENT '病毒扫描状态',
  `virus_scan_detail` varchar(255) DEFAULT NULL COMMENT '病毒特征或失败摘要',
  `virus_scanned_at` datetime(3) DEFAULT NULL COMMENT '病毒扫描完成时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `document_type` varchar(32) NOT NULL DEFAULT 'OTHER',
  PRIMARY KEY (`id`),
  KEY `idx_sys_file_tenant` (`tenant_id`),
  KEY `idx_sys_file_business` (`business_type`,`business_id`),
  KEY `idx_sys_file_created_at` (`created_at`),
  KEY `idx_sys_file_virus_scan_status` (`tenant_id`,`virus_scan_status`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '菜单ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID，0表示根节点',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_type` varchar(20) NOT NULL COMMENT '菜单类型：DIR目录，MENU菜单，BUTTON按钮',
  `path` varchar(300) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(300) DEFAULT NULL COMMENT '组件路径',
  `perms` varchar(200) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否可见：0隐藏，1显示',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_tenant_id` (`tenant_id`,`id`),
  KEY `idx_sys_menu_parent` (`parent_id`),
  KEY `idx_sys_menu_type` (`menu_type`),
  KEY `idx_sys_menu_order` (`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification` (
  `id` bigint NOT NULL COMMENT '通知ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID（显式冗余，非 UserContext）',
  `user_id` bigint NOT NULL COMMENT '接收人用户ID',
  `title` varchar(500) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `biz_type` varchar(100) DEFAULT NULL COMMENT '业务类型：WORKFLOW_APPROVAL审批，WORKFLOW_REJECT驳回，WORKFLOW_CC抄送，ALERT预警，SYSTEM系统',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID（审批实例/预警等）',
  `notify_type` varchar(50) NOT NULL DEFAULT 'INFO' COMMENT '通知类型：INFO信息，WARNING警告，ERROR错误',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '已读标记：0未读，1已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sn_tenant_user_read` (`tenant_id`,`user_id`,`is_read`),
  KEY `idx_sn_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息通知表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_operation_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `operation_type` varchar(50) NOT NULL COMMENT 'LOGIN/LOGOUT/CREATE/UPDATE/DELETE/SUBMIT/APPROVE/UPLOAD/DOWNLOAD',
  `business_type` varchar(50) DEFAULT NULL COMMENT 'SETTLEMENT/CONTRACT/RECEIPT/INVOICE etc',
  `business_id` varchar(100) DEFAULT NULL,
  `http_method` varchar(10) DEFAULT NULL,
  `request_path` varchar(500) DEFAULT NULL,
  `success_flag` tinyint(1) NOT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `source_ip` varchar(50) DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_tenant_biz` (`tenant_id`,`business_type`,`business_id`),
  KEY `idx_tenant_user_created` (`tenant_id`,`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '角色ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `role_type` varchar(50) NOT NULL DEFAULT 'CUSTOM' COMMENT '角色类型：SYSTEM系统内置，CUSTOM自定义',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `data_scope` varchar(50) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL全部，DEPT本部门，DEPT_AND_CHILD本部门及以下，SELF仅本人，CUSTOM自定义',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `role_level` int NOT NULL DEFAULT '2',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`role_code`,`active_unique_token`),
  KEY `idx_sys_role_name` (`role_name`),
  KEY `idx_sys_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL COMMENT '主键ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`tenant_id`,`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_menu` (`tenant_id`,`menu_id`),
  CONSTRAINT `fk_sys_role_menu_menu` FOREIGN KEY (`tenant_id`, `menu_id`) REFERENCES `sys_menu` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_role_menu_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role_menu_audit_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu_audit_snapshot` (
  `id` bigint NOT NULL COMMENT '快照ID，雪花ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `operator_id` bigint DEFAULT NULL COMMENT '操作者用户ID',
  `role_id` bigint NOT NULL COMMENT '目标角色ID',
  `before_menu_ids` text COMMENT '变更前菜单ID快照',
  `after_menu_ids` text COMMENT '变更后菜单ID快照',
  `success_flag` tinyint(1) NOT NULL COMMENT '是否成功：1成功，0失败',
  `error_summary` varchar(500) DEFAULT NULL COMMENT '失败错误摘要',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_role_menu_audit_tenant_role_created` (`tenant_id`,`role_id`,`created_at`),
  KEY `idx_role_menu_audit_tenant_operator_created` (`tenant_id`,`operator_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单绑定变更审计快照';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_type_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_type_registry` (
  `id` bigint NOT NULL COMMENT '注册项ID',
  `type_domain` varchar(64) NOT NULL COMMENT '类型域，例如WORKFLOW_BUSINESS_TYPE',
  `type_code` varchar(64) NOT NULL COMMENT '类型编码',
  `owner_module` varchar(64) NOT NULL COMMENT '权威维护模块',
  `contract_version` varchar(16) NOT NULL COMMENT '契约版本',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DEPRECATED',
  `description` varchar(500) NOT NULL COMMENT '语义与引用边界',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_registry_domain_code` (`type_domain`,`type_code`),
  CONSTRAINT `ck_type_registry_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DEPRECATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多态业务类型契约注册表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '用户ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(200) NOT NULL COMMENT '登录密码（加密存储）',
  `real_name` varchar(100) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(50) DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织ID，关联org_company.id',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `status` varchar(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
  `is_admin` tinyint NOT NULL DEFAULT '0' COMMENT '是否超级管理员：0否，1是',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `active_unique_token` bigint GENERATED ALWAYS AS ((case when (`deleted_flag` = 0) then 0 else `id` end)) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_sys_user_username` (`tenant_id`,`username`,`active_unique_token`),
  KEY `idx_sys_user_real_name` (`real_name`),
  KEY `idx_sys_user_phone` (`phone`),
  KEY `idx_sys_user_status` (`status`),
  KEY `idx_sys_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `preferences` text COLLATE utf8mb4_unicode_ci COMMENT '偏好设置，JSON 格式',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` smallint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL COMMENT '主键ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`tenant_id`,`user_id`,`role_id`),
  KEY `idx_sys_user_role_role` (`tenant_id`,`role_id`),
  CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`tenant_id`, `user_id`) REFERENCES `sys_user` (`tenant_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_acceptance_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_acceptance_archive` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_version_id` bigint NOT NULL,
  `construction_reference_id` bigint NOT NULL,
  `quality_inspection_id` bigint NOT NULL,
  `archive_code` varchar(64) NOT NULL,
  `acceptance_date` date NOT NULL,
  `acceptance_conclusion` varchar(20) NOT NULL,
  `archive_location` varchar(300) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `archived_by` bigint DEFAULT NULL,
  `archived_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_archive_code` (`tenant_id`,`project_id`,`archive_code`,`deleted_flag`),
  UNIQUE KEY `uk_tech_archive_reference` (`tenant_id`,`construction_reference_id`,`deleted_flag`),
  KEY `fk_tech_archive_project` (`project_id`),
  KEY `fk_tech_archive_version` (`drawing_version_id`),
  KEY `fk_tech_archive_reference` (`construction_reference_id`),
  KEY `fk_tech_archive_inspection` (`quality_inspection_id`),
  CONSTRAINT `fk_tech_archive_inspection` FOREIGN KEY (`quality_inspection_id`) REFERENCES `qs_inspection_record` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_archive_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_archive_reference` FOREIGN KEY (`construction_reference_id`) REFERENCES `tech_construction_reference` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_archive_version` FOREIGN KEY (`drawing_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_archive_conclusion` CHECK ((`acceptance_conclusion` in (_utf8mb4'PASS',_utf8mb4'CONDITIONAL_PASS'))),
  CONSTRAINT `ck_tech_archive_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_construction_reference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_construction_reference` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_version_id` bigint NOT NULL,
  `disclosure_id` bigint NOT NULL,
  `daily_log_id` bigint NOT NULL,
  `wbs_task_id` bigint NOT NULL,
  `reference_date` date NOT NULL,
  `work_area` varchar(200) NOT NULL,
  `reference_description` varchar(1000) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'RECORDED',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_construction_reference` (`tenant_id`,`drawing_version_id`,`daily_log_id`,`wbs_task_id`,`deleted_flag`),
  KEY `idx_tech_construction_reference_project` (`tenant_id`,`project_id`,`reference_date`),
  KEY `fk_tech_reference_project` (`project_id`),
  KEY `fk_tech_reference_version` (`drawing_version_id`),
  KEY `fk_tech_reference_disclosure` (`disclosure_id`),
  KEY `fk_tech_reference_daily_log` (`daily_log_id`),
  KEY `fk_tech_reference_wbs` (`wbs_task_id`),
  CONSTRAINT `fk_tech_reference_daily_log` FOREIGN KEY (`daily_log_id`) REFERENCES `site_daily_log` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_reference_disclosure` FOREIGN KEY (`disclosure_id`) REFERENCES `tech_disclosure` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_reference_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_reference_version` FOREIGN KEY (`drawing_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_reference_wbs` FOREIGN KEY (`wbs_task_id`) REFERENCES `project_wbs_task` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_reference_status` CHECK ((`status` in (_utf8mb4'RECORDED',_utf8mb4'VOID')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_disclosure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_disclosure` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_version_id` bigint NOT NULL,
  `scheme_id` bigint DEFAULT NULL,
  `disclosure_code` varchar(64) NOT NULL,
  `disclosure_title` varchar(200) NOT NULL,
  `disclosure_date` date NOT NULL,
  `presenter_user_id` bigint NOT NULL,
  `recipient_summary` varchar(500) NOT NULL,
  `disclosure_content` varchar(2000) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_disclosure_code` (`tenant_id`,`project_id`,`disclosure_code`,`deleted_flag`),
  KEY `fk_tech_disclosure_project` (`project_id`),
  KEY `fk_tech_disclosure_version` (`drawing_version_id`),
  KEY `fk_tech_disclosure_scheme` (`scheme_id`),
  CONSTRAINT `fk_tech_disclosure_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_disclosure_scheme` FOREIGN KEY (`scheme_id`) REFERENCES `technical_scheme` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_disclosure_version` FOREIGN KEY (`drawing_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_disclosure_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CONFIRMED',_utf8mb4'VOID')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_drawing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_drawing` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_code` varchar(64) NOT NULL,
  `drawing_name` varchar(200) NOT NULL,
  `specialty` varchar(50) NOT NULL,
  `source_organization` varchar(200) NOT NULL,
  `current_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_drawing_code` (`tenant_id`,`project_id`,`drawing_code`,`deleted_flag`),
  KEY `idx_tech_drawing_project` (`tenant_id`,`project_id`,`specialty`,`status`),
  KEY `fk_tech_drawing_project` (`project_id`),
  CONSTRAINT `fk_tech_drawing_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_drawing_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_drawing_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_drawing_review` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_version_id` bigint NOT NULL,
  `review_code` varchar(64) NOT NULL,
  `review_date` date NOT NULL,
  `chair_user_id` bigint NOT NULL,
  `participant_summary` varchar(500) NOT NULL,
  `conclusion` varchar(20) NOT NULL,
  `review_summary` varchar(1000) NOT NULL,
  `requires_rfi` tinyint NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_drawing_review_code` (`tenant_id`,`project_id`,`review_code`,`deleted_flag`),
  UNIQUE KEY `uk_tech_drawing_review_version` (`tenant_id`,`drawing_version_id`,`deleted_flag`),
  KEY `fk_tech_drawing_review_project` (`project_id`),
  KEY `fk_tech_drawing_review_version` (`drawing_version_id`),
  CONSTRAINT `fk_tech_drawing_review_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_drawing_review_version` FOREIGN KEY (`drawing_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_drawing_review_conclusion` CHECK ((`conclusion` in (_utf8mb4'PASS',_utf8mb4'CONDITIONAL',_utf8mb4'REJECTED'))),
  CONSTRAINT `ck_tech_drawing_review_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'CONFIRMED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_drawing_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_drawing_version` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_id` bigint NOT NULL,
  `version_no` varchar(30) NOT NULL,
  `previous_version_id` bigint DEFAULT NULL,
  `source_rfi_id` bigint DEFAULT NULL,
  `received_at` datetime NOT NULL,
  `received_by` bigint NOT NULL,
  `change_summary` varchar(500) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'RECEIVED',
  `approved_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_drawing_version` (`tenant_id`,`drawing_id`,`version_no`,`deleted_flag`),
  KEY `idx_tech_drawing_version_status` (`tenant_id`,`project_id`,`status`),
  KEY `fk_tech_drawing_version_project` (`project_id`),
  KEY `fk_tech_drawing_version_drawing` (`drawing_id`),
  KEY `fk_tech_drawing_version_previous` (`previous_version_id`),
  KEY `fk_tech_drawing_version_source_rfi` (`source_rfi_id`),
  CONSTRAINT `fk_tech_drawing_version_drawing` FOREIGN KEY (`drawing_id`) REFERENCES `tech_drawing` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_drawing_version_previous` FOREIGN KEY (`previous_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_drawing_version_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_drawing_version_source_rfi` FOREIGN KEY (`source_rfi_id`) REFERENCES `tech_rfi` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_drawing_version_status` CHECK ((`status` in (_utf8mb4'RECEIVED',_utf8mb4'UNDER_REVIEW',_utf8mb4'RFI_PENDING',_utf8mb4'APPROVED',_utf8mb4'SUPERSEDED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技术事项ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `item_type` varchar(50) NOT NULL COMMENT '事项类型：TECH_PLAN/DESIGN_COORDINATION/TECH_REVIEW/TECH_ISSUE',
  `item_code` varchar(50) NOT NULL COMMENT '事项编码',
  `item_title` varchar(200) NOT NULL COMMENT '事项标题',
  `item_level` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '事项等级：NORMAL/HIGH/URGENT',
  `item_status` varchar(30) NOT NULL DEFAULT 'OPEN' COMMENT '事项状态：OPEN/PENDING/CLOSED/OVERDUE',
  `discovered_at` datetime DEFAULT NULL COMMENT '发现时间',
  `due_date` datetime DEFAULT NULL COMMENT '截止时间',
  `closed_at` datetime DEFAULT NULL COMMENT '关闭时间',
  `responsible_user_id` bigint DEFAULT NULL COMMENT '责任人',
  `created_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `source_type` varchar(40) DEFAULT NULL COMMENT '真实技术业务来源类型',
  `source_id` bigint DEFAULT NULL COMMENT '真实技术业务来源ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_item_code` (`tenant_id`,`item_code`),
  UNIQUE KEY `uk_tech_item_source` (`tenant_id`,`source_type`,`source_id`),
  KEY `idx_tech_item_tenant_project` (`tenant_id`,`project_id`),
  KEY `idx_tech_item_tenant_type_status` (`tenant_id`,`item_type`,`item_status`),
  KEY `idx_tech_item_tenant_level_status` (`tenant_id`,`item_level`,`item_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='总工程师技术事项表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_rfi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_rfi` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `drawing_version_id` bigint NOT NULL,
  `review_id` bigint NOT NULL,
  `rfi_code` varchar(64) NOT NULL,
  `subject` varchar(200) NOT NULL,
  `question` varchar(2000) NOT NULL,
  `priority` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `raised_by` bigint NOT NULL,
  `raised_at` datetime NOT NULL,
  `response_due_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `closed_by` bigint DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_rfi_code` (`tenant_id`,`project_id`,`rfi_code`,`deleted_flag`),
  KEY `idx_tech_rfi_status` (`tenant_id`,`project_id`,`status`,`response_due_date`),
  KEY `fk_tech_rfi_project` (`project_id`),
  KEY `fk_tech_rfi_drawing_version` (`drawing_version_id`),
  KEY `fk_tech_rfi_review` (`review_id`),
  CONSTRAINT `fk_tech_rfi_drawing_version` FOREIGN KEY (`drawing_version_id`) REFERENCES `tech_drawing_version` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_rfi_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tech_rfi_review` FOREIGN KEY (`review_id`) REFERENCES `tech_drawing_review` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_rfi_priority` CHECK ((`priority` in (_utf8mb4'NORMAL',_utf8mb4'HIGH',_utf8mb4'URGENT'))),
  CONSTRAINT `ck_tech_rfi_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'RESPONDED',_utf8mb4'CHANGE_PENDING',_utf8mb4'CLOSED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tech_rfi_response`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_rfi_response` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `rfi_id` bigint NOT NULL,
  `response_no` int NOT NULL,
  `response_content` varchar(2000) NOT NULL,
  `change_required` tinyint NOT NULL DEFAULT '0',
  `responder_name` varchar(100) NOT NULL,
  `responded_by` bigint NOT NULL,
  `responded_at` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_rfi_response_no` (`tenant_id`,`rfi_id`,`response_no`),
  KEY `fk_tech_rfi_response_rfi` (`rfi_id`),
  CONSTRAINT `fk_tech_rfi_response_rfi` FOREIGN KEY (`rfi_id`) REFERENCES `tech_rfi` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tech_rfi_response_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'ACCEPTED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `technical_scheme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `technical_scheme` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `scheme_code` varchar(64) NOT NULL,
  `scheme_name` varchar(200) NOT NULL,
  `scheme_type` varchar(30) NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  `planned_effective_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `approval_instance_id` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_technical_scheme_code` (`tenant_id`,`project_id`,`scheme_code`,`deleted_flag`),
  KEY `idx_technical_scheme_status` (`tenant_id`,`project_id`,`status`),
  KEY `fk_technical_scheme_project` (`project_id`),
  KEY `fk_technical_scheme_approval` (`approval_instance_id`),
  CONSTRAINT `fk_technical_scheme_approval` FOREIGN KEY (`approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_technical_scheme_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_technical_scheme_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'SUPERSEDED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `ck_technical_scheme_type` CHECK ((`scheme_type` in (_utf8mb4'GENERAL',_utf8mb4'SPECIAL',_utf8mb4'CONSTRUCTION_ORGANIZATION',_utf8mb4'METHOD_STATEMENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `v_business_audit_event`;
/*!50001 DROP VIEW IF EXISTS `v_business_audit_event`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_business_audit_event` AS SELECT
 1 AS `event_domain`,
 1 AS `id`,
 1 AS `tenant_id`,
 1 AS `event_type`,
 1 AS `business_type`,
 1 AS `business_id`,
 1 AS `project_id`,
 1 AS `operator_id`,
 1 AS `event_at`,
 1 AS `archive_bucket`,
 1 AS `payload_json`,
 1 AS `payload_hash`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_reconciliation_issue`;
/*!50001 DROP VIEW IF EXISTS `v_reconciliation_issue`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_reconciliation_issue` AS SELECT
 1 AS `issue_domain`,
 1 AS `id`,
 1 AS `tenant_id`,
 1 AS `run_id`,
 1 AS `dimension_type`,
 1 AS `business_id`,
 1 AS `issue_code`,
 1 AS `expected_amount`,
 1 AS `actual_amount`,
 1 AS `status`,
 1 AS `detail`,
 1 AS `created_at`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `var_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `var_order` (
  `id` bigint NOT NULL COMMENT '变更签证ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `partner_id` bigint DEFAULT NULL COMMENT '合作方ID',
  `var_code` varchar(64) NOT NULL COMMENT '变更编号',
  `var_name` varchar(200) NOT NULL COMMENT '变更名称',
  `var_type` varchar(50) DEFAULT NULL COMMENT '变更类型',
  `direction` varchar(20) DEFAULT NULL COMMENT '变更方向：ADD增加，REDUCE减少',
  `reported_amount` decimal(18,2) DEFAULT NULL COMMENT '上报金额',
  `approved_amount` decimal(18,2) DEFAULT NULL COMMENT '审定金额',
  `confirmed_amount` decimal(18,2) DEFAULT NULL COMMENT '确认金额',
  `owner_confirm_flag` tinyint NOT NULL DEFAULT '0' COMMENT '业主确认标识：0未确认，1已确认',
  `impact_days` int NOT NULL DEFAULT '0' COMMENT '影响工期天数',
  `approval_status` varchar(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  `cost_generated_flag` tinyint NOT NULL DEFAULT '0' COMMENT '成本生成标识：0未生成，1已生成',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `business_matter_key` varchar(100) DEFAULT NULL COMMENT '跨域业务事项唯一键',
  `event_date` date DEFAULT NULL COMMENT '变更事件日期',
  `claim_deadline` date DEFAULT NULL COMMENT '合同约定索赔申报截止日',
  `event_description` varchar(1000) DEFAULT NULL COMMENT '现场事件及影响说明',
  `cause_category` varchar(64) DEFAULT NULL COMMENT '原因分类',
  `responsible_party` varchar(200) DEFAULT NULL COMMENT '责任方',
  `estimated_cost_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '内部成本测算金额',
  `owner_status` varchar(32) NOT NULL DEFAULT 'NOT_READY' COMMENT '业主申报生命周期',
  `internal_approval_instance_id` bigint DEFAULT NULL COMMENT '内部审批实例',
  `generated_contract_change_id` bigint DEFAULT NULL COMMENT '业主核定后生成的正式合同变更',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_var_order_code` (`tenant_id`,`var_code`,`deleted_flag`),
  KEY `idx_var_order_project` (`project_id`),
  KEY `idx_var_order_contract` (`contract_id`),
  KEY `idx_var_order_matter_key` (`tenant_id`,`project_id`,`business_matter_key`),
  KEY `fk_var_order_internal_approval` (`internal_approval_instance_id`),
  KEY `fk_var_order_generated_change` (`generated_contract_change_id`),
  KEY `idx_var_order_owner_status` (`tenant_id`,`project_id`,`owner_status`,`event_date`),
  CONSTRAINT `fk_var_order_generated_change` FOREIGN KEY (`generated_contract_change_id`) REFERENCES `ct_contract_change` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_var_order_internal_approval` FOREIGN KEY (`internal_approval_instance_id`) REFERENCES `wf_instance` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_var_order_estimated_cost` CHECK ((`estimated_cost_amount` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `var_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `var_order_item` (
  `id` bigint NOT NULL COMMENT '变更明细ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `var_order_id` bigint NOT NULL COMMENT '变更签证ID',
  `item_name` varchar(200) DEFAULT NULL COMMENT '清单项名称',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `cost_subject_id` bigint DEFAULT NULL COMMENT '成本科目ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `claim_unit_price` decimal(18,4) DEFAULT NULL COMMENT '对业主申报单价',
  `claim_amount` decimal(18,2) DEFAULT NULL COMMENT '对业主申报金额',
  PRIMARY KEY (`id`),
  KEY `idx_var_oi_order` (`var_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `variation_owner_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `variation_owner_submission` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `var_order_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `submission_code` varchar(64) NOT NULL,
  `external_document_no` varchar(128) NOT NULL,
  `submitted_amount` decimal(18,2) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime NOT NULL,
  `submitted_by` bigint NOT NULL,
  `response_document_no` varchar(128) DEFAULT NULL,
  `response_comment` varchar(500) DEFAULT NULL,
  `confirmed_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `reviewed_at` datetime DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `generated_contract_change_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_flag` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variation_owner_submission_code` (`tenant_id`,`submission_code`,`deleted_flag`),
  UNIQUE KEY `uk_variation_owner_submission_revision` (`tenant_id`,`var_order_id`,`revision_no`,`deleted_flag`),
  KEY `idx_variation_owner_submission_status` (`tenant_id`,`project_id`,`status`,`submitted_at`),
  KEY `fk_variation_owner_submission_project` (`project_id`),
  KEY `fk_variation_owner_submission_contract` (`contract_id`),
  KEY `fk_variation_owner_submission_order` (`var_order_id`),
  KEY `fk_variation_owner_submission_change` (`generated_contract_change_id`),
  CONSTRAINT `fk_variation_owner_submission_change` FOREIGN KEY (`generated_contract_change_id`) REFERENCES `ct_contract_change` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_variation_owner_submission_contract` FOREIGN KEY (`contract_id`) REFERENCES `ct_contract` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_variation_owner_submission_order` FOREIGN KEY (`var_order_id`) REFERENCES `var_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_variation_owner_submission_project` FOREIGN KEY (`project_id`) REFERENCES `pm_project` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_variation_owner_submission_amount` CHECK (((`submitted_amount` > 0) and (`confirmed_amount` >= 0) and (`confirmed_amount` <= `submitted_amount`))),
  CONSTRAINT `ck_variation_owner_submission_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'RETURNED',_utf8mb4'CONFIRMED',_utf8mb4'CHANGE_PENDING',_utf8mb4'CHANGE_EFFECTIVE',_utf8mb4'CHANGE_REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证对业主申报版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `variation_owner_submission_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `variation_owner_submission_item` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `submission_id` bigint NOT NULL,
  `var_order_item_id` bigint NOT NULL,
  `item_name` varchar(200) NOT NULL,
  `unit` varchar(20) DEFAULT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `claimed_unit_price` decimal(18,4) NOT NULL,
  `claimed_amount` decimal(18,2) NOT NULL,
  `confirmed_amount` decimal(18,2) DEFAULT NULL,
  `reduction_reason` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variation_owner_submission_item` (`tenant_id`,`submission_id`,`var_order_item_id`),
  KEY `fk_variation_owner_item_submission` (`submission_id`),
  KEY `fk_variation_owner_item_source` (`var_order_item_id`),
  CONSTRAINT `fk_variation_owner_item_source` FOREIGN KEY (`var_order_item_id`) REFERENCES `var_order_item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_variation_owner_item_submission` FOREIGN KEY (`submission_id`) REFERENCES `variation_owner_submission` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_variation_owner_item_amount` CHECK (((`quantity` > 0) and (`claimed_unit_price` >= 0) and (`claimed_amount` > 0) and ((`confirmed_amount` is null) or ((`confirmed_amount` >= 0) and (`confirmed_amount` <= `claimed_amount`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证业主申报及核定明细快照';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_cc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_cc` (
  `id` bigint NOT NULL COMMENT '抄送ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `instance_id` bigint NOT NULL COMMENT '审批实例ID，关联wf_instance.id',
  `cc_user_id` bigint NOT NULL COMMENT '抄送人用户ID',
  `cc_user_name` varchar(100) NOT NULL COMMENT '抄送人姓名（冗余，避免联表）',
  `business_type` varchar(100) DEFAULT NULL COMMENT '业务类型',
  `business_id` bigint DEFAULT NULL COMMENT '业务ID',
  `title` varchar(500) DEFAULT NULL COMMENT '审批标题（冗余）',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '已读标记：0未读，1已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抄送时间',
  PRIMARY KEY (`id`),
  KEY `idx_wc_tenant_ccuser` (`tenant_id`,`cc_user_id`),
  KEY `idx_wc_instance` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批抄送表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_idempotency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_idempotency` (
  `id` bigint NOT NULL COMMENT '幂等记录ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `business_id` bigint DEFAULT NULL COMMENT '业务ID',
  `request_hash` varchar(128) DEFAULT NULL COMMENT '请求摘要',
  `response_json` json DEFAULT NULL COMMENT '首次响应结果',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_idempotency` (`tenant_id`,`user_id`,`idempotency_key`),
  KEY `idx_wf_idempotency_expired` (`expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批幂等表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_instance` (
  `id` bigint NOT NULL COMMENT '审批实例ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `template_id` bigint NOT NULL COMMENT '审批模板ID',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `business_id` bigint NOT NULL COMMENT '业务单据ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID',
  `contract_id` bigint DEFAULT NULL COMMENT '合同ID',
  `title` varchar(300) NOT NULL COMMENT '审批标题',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '审批金额',
  `instance_status` varchar(50) NOT NULL DEFAULT 'RUNNING' COMMENT '实例状态',
  `current_round` int NOT NULL DEFAULT '1' COMMENT '当前审批轮次',
  `resubmit_count` int NOT NULL DEFAULT '0' COMMENT '重新提交次数',
  `business_revision` int NOT NULL DEFAULT '1' COMMENT '业务版本',
  `initiator_id` bigint NOT NULL COMMENT '发起人ID',
  `business_summary` text COMMENT '业务摘要',
  `variables` json DEFAULT NULL COMMENT '流程变量',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_instance_business` (`business_type`,`business_id`,`deleted_flag`),
  KEY `idx_wf_instance_initiator` (`initiator_id`,`instance_status`),
  KEY `idx_wf_instance_project` (`project_id`),
  KEY `idx_wf_instance_status` (`instance_status`,`current_round`),
  KEY `idx_wf_instance_tenant_status_started` (`tenant_id`,`instance_status`,`started_at`,`deleted_flag`),
  KEY `idx_wf_instance_tenant_business` (`tenant_id`,`business_type`,`business_id`,`deleted_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批实例表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_node_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_node_instance` (
  `id` bigint NOT NULL COMMENT '节点实例ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `instance_id` bigint NOT NULL COMMENT '审批实例ID',
  `template_node_id` bigint DEFAULT NULL COMMENT '模板节点ID',
  `node_code` varchar(64) NOT NULL COMMENT '节点编码',
  `node_name` varchar(200) NOT NULL COMMENT '节点名称',
  `node_order` int NOT NULL COMMENT '节点顺序',
  `approve_mode` varchar(50) NOT NULL COMMENT '审批模式',
  `node_status` varchar(50) NOT NULL DEFAULT 'WAITING' COMMENT '节点状态',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '审批轮次',
  `pass_rule_json` json DEFAULT NULL COMMENT '节点通过规则',
  `reject_rule_json` json DEFAULT NULL COMMENT '节点驳回规则',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_wf_node_instance_instance` (`instance_id`,`round_no`,`node_order`),
  KEY `idx_wf_node_instance_status` (`node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批节点实例表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_record` (
  `id` bigint NOT NULL COMMENT '审批记录ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `instance_id` bigint NOT NULL COMMENT '审批实例ID',
  `node_instance_id` bigint DEFAULT NULL COMMENT '节点实例ID，提交/撤回等可为空',
  `task_id` bigint DEFAULT NULL COMMENT '审批任务ID',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '审批轮次',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `business_id` bigint NOT NULL COMMENT '业务单据ID',
  `node_code` varchar(64) DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `action_type` varchar(50) NOT NULL COMMENT '动作类型',
  `action_name` varchar(100) NOT NULL COMMENT '动作名称',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
  `record_status` varchar(50) NOT NULL DEFAULT 'EFFECTIVE' COMMENT '记录状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_wf_record_instance` (`instance_id`,`round_no`,`created_at`),
  KEY `idx_wf_record_task` (`task_id`),
  KEY `idx_wf_record_node` (`node_instance_id`),
  KEY `idx_wf_record_business` (`business_type`,`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_task` (
  `id` bigint NOT NULL COMMENT '审批任务ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `instance_id` bigint NOT NULL COMMENT '审批实例ID',
  `node_instance_id` bigint NOT NULL COMMENT '节点实例ID',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `business_id` bigint NOT NULL COMMENT '业务单据ID',
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审批人姓名',
  `task_status` varchar(50) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '审批轮次',
  `task_version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本',
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `action_type` varchar(50) DEFAULT NULL COMMENT '处理动作',
  `comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_wf_task_todo` (`approver_id`,`task_status`,`received_at`),
  KEY `idx_wf_task_instance` (`instance_id`,`round_no`),
  KEY `idx_wf_task_node` (`node_instance_id`),
  KEY `idx_wf_task_business` (`business_type`,`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_template` (
  `id` bigint NOT NULL COMMENT '审批模板ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(200) NOT NULL COMMENT '模板名称',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：0否，1是',
  `amount_min` decimal(18,2) DEFAULT NULL COMMENT '适用金额下限',
  `amount_max` decimal(18,2) DEFAULT NULL COMMENT '适用金额上限',
  `condition_rule` json DEFAULT NULL COMMENT '流程匹配条件',
  `form_schema` json DEFAULT NULL COMMENT '动态表单Schema',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_template_code` (`tenant_id`,`template_code`,`deleted_flag`),
  UNIQUE KEY `uk_wf_template_tenant_id` (`tenant_id`,`id`),
  KEY `idx_wf_template_business` (`business_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批模板表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wf_template_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wf_template_node` (
  `id` bigint NOT NULL COMMENT '模板节点ID，雪花ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `node_code` varchar(64) NOT NULL COMMENT '节点编码',
  `node_name` varchar(200) NOT NULL COMMENT '节点名称',
  `node_order` int NOT NULL COMMENT '节点顺序',
  `node_type` varchar(50) NOT NULL DEFAULT 'APPROVAL' COMMENT '节点类型',
  `approve_mode` varchar(50) NOT NULL DEFAULT 'SEQUENTIAL' COMMENT '审批模式',
  `approver_config` json NOT NULL COMMENT '审批人配置',
  `pass_rule_json` json DEFAULT NULL COMMENT '节点通过规则',
  `reject_rule_json` json DEFAULT NULL COMMENT '节点驳回规则',
  `condition_rule` json DEFAULT NULL COMMENT '节点执行条件',
  `node_config` json DEFAULT NULL COMMENT '节点扩展配置',
  `allow_transfer` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许转办：0否，1是',
  `allow_add_sign` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许加签：0否，1是',
  `timeout_hours` int DEFAULT NULL COMMENT '超时时间（小时）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_flag` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_template_node_code` (`template_id`,`node_code`),
  KEY `idx_wf_template_node_template` (`template_id`,`node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批模板节点表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50001 DROP VIEW IF EXISTS `v_business_audit_event`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`cgc`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `v_business_audit_event` AS select 'FINANCE' AS `event_domain`,`finance_audit_event`.`id` AS `id`,`finance_audit_event`.`tenant_id` AS `tenant_id`,`finance_audit_event`.`event_type` AS `event_type`,`finance_audit_event`.`business_type` AS `business_type`,`finance_audit_event`.`business_id` AS `business_id`,`finance_audit_event`.`project_id` AS `project_id`,`finance_audit_event`.`operator_id` AS `operator_id`,`finance_audit_event`.`event_at` AS `event_at`,`finance_audit_event`.`archive_bucket` AS `archive_bucket`,`finance_audit_event`.`payload_json` AS `payload_json`,`finance_audit_event`.`payload_hash` AS `payload_hash` from `finance_audit_event` union all select 'REVENUE' AS `event_domain`,`revenue_audit_event`.`id` AS `id`,`revenue_audit_event`.`tenant_id` AS `tenant_id`,`revenue_audit_event`.`event_type` AS `event_type`,`revenue_audit_event`.`business_type` AS `business_type`,`revenue_audit_event`.`business_id` AS `business_id`,`revenue_audit_event`.`project_id` AS `project_id`,`revenue_audit_event`.`operator_id` AS `operator_id`,`revenue_audit_event`.`event_at` AS `event_at`,`revenue_audit_event`.`archive_bucket` AS `archive_bucket`,`revenue_audit_event`.`payload_json` AS `payload_json`,`revenue_audit_event`.`payload_hash` AS `payload_hash` from `revenue_audit_event` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_reconciliation_issue`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`cgc`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `v_reconciliation_issue` AS select 'FINANCE' AS `issue_domain`,`finance_reconciliation_issue`.`id` AS `id`,`finance_reconciliation_issue`.`tenant_id` AS `tenant_id`,`finance_reconciliation_issue`.`run_id` AS `run_id`,`finance_reconciliation_issue`.`dimension_type` AS `dimension_type`,`finance_reconciliation_issue`.`business_id` AS `business_id`,`finance_reconciliation_issue`.`issue_code` AS `issue_code`,`finance_reconciliation_issue`.`expected_amount` AS `expected_amount`,`finance_reconciliation_issue`.`actual_amount` AS `actual_amount`,`finance_reconciliation_issue`.`status` AS `status`,`finance_reconciliation_issue`.`detail` AS `detail`,`finance_reconciliation_issue`.`created_at` AS `created_at` from `finance_reconciliation_issue` union all select 'REVENUE' AS `issue_domain`,`revenue_reconciliation_issue`.`id` AS `id`,`revenue_reconciliation_issue`.`tenant_id` AS `tenant_id`,`revenue_reconciliation_issue`.`run_id` AS `run_id`,`revenue_reconciliation_issue`.`dimension_type` AS `dimension_type`,`revenue_reconciliation_issue`.`business_id` AS `business_id`,`revenue_reconciliation_issue`.`issue_code` AS `issue_code`,`revenue_reconciliation_issue`.`expected_amount` AS `expected_amount`,`revenue_reconciliation_issue`.`actual_amount` AS `actual_amount`,`revenue_reconciliation_issue`.`status` AS `status`,`revenue_reconciliation_issue`.`detail` AS `detail`,`revenue_reconciliation_issue`.`created_at` AS `created_at` from `revenue_reconciliation_issue` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- CGC-PMS B215 deterministic system seed whitelist.
SET FOREIGN_KEY_CHECKS=0;
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (1,0,'SUPER_ADMIN','超级管理员','SYSTEM','ENABLE','ALL',1,'2026-07-18 16:48:32',NULL,'2026-07-18 16:48:56',0,'系统内置，拥有全部权限',0);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (2,0,'PROJECT_MANAGER','项目经理','CUSTOM','ENABLE','DEPT_AND_CHILD',1,'2026-07-18 16:48:32',NULL,'2026-07-18 16:48:32',0,'项目经理角色',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (3,0,'COMMON_USER','普通用户','CUSTOM','ENABLE','SELF',1,'2026-07-18 16:48:32',NULL,'2026-07-18 16:48:32',0,'普通业务用户',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (4,0,'COMMERCIAL_MANAGER','商务经理','BUSINESS','ENABLE','ALL',1,'2026-07-18 16:49:00',NULL,'2026-07-18 16:49:00',0,'第15条主线：商务经理正式角色',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (5,0,'MATERIAL_CLERK','材料员','BUSINESS','ENABLE','3',1,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,'负责材料验收、出入库管理',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (6,0,'FINANCE','财务人员','BUSINESS','ENABLE','3',1,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,'负责付款、发票、结算相关操作',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (7,0,'PURCHASE_MANAGER','采购经理','BUSINESS','ENABLE','ALL',1,'2026-07-18 16:48:59',NULL,'2026-07-18 16:48:59',0,'第二阶段：采购经理默认角色',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (8,0,'PRODUCTION_MANAGER','生产经理','BUSINESS','ENABLE','ALL',1,'2026-07-18 16:48:59',NULL,'2026-07-18 16:48:59',0,'第二阶段：生产经理默认角色',2);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `data_scope`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`, `role_level`) VALUES (9,0,'CHIEF_ENGINEER','总工程师','BUSINESS','ENABLE','ALL',1,'2026-07-18 16:48:59',NULL,'2026-07-18 16:48:59',0,'总工程师默认角色',2);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1,0,900,'首页驾驶舱','MENU','/dashboard','dashboard/index','dashboard:view','dashboard',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2,0,0,'项目管理','DIR','/project',NULL,NULL,'project',2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (3,0,0,'合同管理','DIR','/contract',NULL,NULL,'contract',3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (4,0,0,'合作方管理','DIR','/partner',NULL,NULL,'partner',4,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (5,0,0,'系统设置','DIR','/system',NULL,NULL,'setting',5,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (201,0,901,'项目列表','MENU','/project/list','project/index',NULL,'list',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (202,0,201,'新增项目','BUTTON',NULL,NULL,'project:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (203,0,201,'编辑项目','BUTTON',NULL,NULL,'project:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (204,0,201,'删除项目','BUTTON',NULL,NULL,NULL,NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (301,0,902,'合同台账','MENU','/contract/ledger','contract/ContractLedgerPage',NULL,'list',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:00',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (302,0,301,'新增合同','BUTTON',NULL,NULL,'contract:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (303,0,301,'编辑合同','BUTTON',NULL,NULL,'contract:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (304,0,301,'删除合同','BUTTON',NULL,NULL,'contract:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (305,0,301,'提交审批','BUTTON',NULL,NULL,'contract:submit',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (401,0,901,'合作方管理','MENU','/partner','partner/index',NULL,'list',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:00',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (402,0,401,'新增合作方','BUTTON',NULL,NULL,'partner:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (403,0,401,'编辑合作方','BUTTON',NULL,NULL,'partner:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (404,0,401,'删除合作方','BUTTON',NULL,NULL,'partner:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:32',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (501,0,909,'用户管理','MENU','/system/users','system/users/index',NULL,'user',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (502,0,909,'角色管理','MENU','/system/roles','system/roles/index',NULL,'role',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (503,0,5,'菜单管理','MENU','menu','system/menu/index',NULL,'menu',3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (504,0,909,'字典管理','MENU','/system/dict','system/dict/index','system:dict:list','dict',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:32','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (600,0,0,'合同提交审批','BUTTON',NULL,NULL,'contract:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (601,0,0,'采购订单提交审批','BUTTON',NULL,NULL,'purchase:order:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (602,0,0,'材料验收提交审批','BUTTON',NULL,NULL,'receipt:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (603,0,0,'分包计量提交审批','BUTTON',NULL,NULL,'subcontract:measure:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (604,0,0,'付款申请提交审批','BUTTON',NULL,NULL,'payment:app:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (605,0,0,'签证变更提交审批','BUTTON',NULL,NULL,'variation:order:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (606,0,0,'合同变更提交审批','BUTTON',NULL,NULL,'contract:change:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (607,0,0,'结算提交审批','BUTTON',NULL,NULL,'settlement:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (608,0,0,'成本目标提交审批','BUTTON',NULL,NULL,'cost:target:submit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (613,0,0,'审批同意','BUTTON',NULL,NULL,'workflow:approve',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (614,0,0,'审批驳回','BUTTON',NULL,NULL,'workflow:reject',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (615,0,0,'审批转办','BUTTON',NULL,NULL,'workflow:transfer',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (616,0,0,'审批加签','BUTTON',NULL,NULL,'workflow:add-sign',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (617,0,0,'审批撤回','BUTTON',NULL,NULL,'workflow:withdraw',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (618,0,0,'审批重新提交','BUTTON',NULL,NULL,'workflow:resubmit',NULL,0,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:38','2026-07-18 16:48:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (700,0,0,'组织架构','DIR','/org',NULL,NULL,'apartment',6,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (701,0,901,'组织架构','MENU','/org','org/index','org:list','apartment',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (702,0,701,'新增组织','BUTTON',NULL,NULL,'org:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (703,0,701,'编辑组织','BUTTON',NULL,NULL,'org:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (704,0,701,'删除组织','BUTTON',NULL,NULL,'org:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (710,0,0,'库存管理','DIR','/inventory',NULL,NULL,'warehouse',7,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (711,0,2,'项目成员','BUTTON',NULL,NULL,'project:member:list',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (712,0,2,'新增成员','BUTTON',NULL,NULL,'project:member:add',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (713,0,2,'编辑成员','BUTTON',NULL,NULL,'project:member:edit',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (714,0,2,'删除成员','BUTTON',NULL,NULL,'project:member:delete',NULL,7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (720,0,0,'发票管理','DIR','/invoice',NULL,NULL,'file-text',8,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (721,0,504,'新增字典','BUTTON',NULL,NULL,'system:dict:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (722,0,504,'编辑字典','BUTTON',NULL,NULL,'system:dict:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (723,0,504,'删除字典','BUTTON',NULL,NULL,'system:dict:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (730,0,0,'消息中心','DIR','/notification',NULL,NULL,'bell',9,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (731,0,904,'仓库管理','MENU','/inventory/warehouse','inventory/warehouse','inventory:warehouse:list','home',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (732,0,904,'库存台账','MENU','/inventory/stock','inventory/stock','inventory:stock:list','bar-chart',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (733,0,904,'出入库管理','MENU','/inventory/transaction','inventory/transaction','inventory:transaction:list','swap',6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (734,0,904,'采购申请','MENU','/inventory/purchase-request','inventory/purchase-request','purchase:request:list','shopping-cart',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (735,0,731,'新增仓库','BUTTON',NULL,NULL,'inventory:warehouse:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (736,0,731,'编辑仓库','BUTTON',NULL,NULL,'inventory:warehouse:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (737,0,731,'删除仓库','BUTTON',NULL,NULL,'inventory:warehouse:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (738,0,733,'新增入库','BUTTON',NULL,NULL,'inventory:transaction:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (739,0,734,'新增采购申请','BUTTON',NULL,NULL,'purchase:request:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (740,0,734,'提交审批','BUTTON',NULL,NULL,'purchase:request:submit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (751,0,906,'发票管理','MENU','/invoice','invoice/index',NULL,'file-text',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (752,0,751,'新增发票','BUTTON',NULL,NULL,'invoice:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (753,0,751,'编辑发票','BUTTON',NULL,NULL,'invoice:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (754,0,751,'删除发票','BUTTON',NULL,NULL,'invoice:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (755,0,751,'核验发票','BUTTON',NULL,NULL,'invoice:verify',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (761,0,730,'消息列表','MENU','index','notification/index',NULL,'bell',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:49:00',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (762,0,751,'查询发票','BUTTON',NULL,NULL,'invoice:query',NULL,0,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (763,0,761,'查看消息','BUTTON',NULL,NULL,'notification:view',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (764,0,761,'标记已读','BUTTON',NULL,NULL,'notification:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (765,0,0,'预警中心','DIR','/alert',NULL,NULL,'alert',10,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (766,0,900,'预警中心','MENU','/alert','alert/index','alert:view','alert',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (767,0,766,'标记已读','BUTTON',NULL,NULL,'alert:edit',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (768,0,766,'批量评估','BUTTON',NULL,NULL,'alert:evaluate',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (800,0,5,'用户查询','BUTTON',NULL,NULL,'system:user:query',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (801,0,5,'角色查询','BUTTON',NULL,NULL,'system:role:query',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (802,0,5,'菜单查询','BUTTON',NULL,NULL,'system:menu:query',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (803,0,2,'项目查询','BUTTON',NULL,NULL,'project:query',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (804,0,3,'合同查询','BUTTON',NULL,NULL,'contract:query',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (805,0,4,'合作方查询','BUTTON',NULL,NULL,'partner:query',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (806,0,700,'组织查询','BUTTON',NULL,NULL,'org:query',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (807,0,1,'项目经理驾驶舱','BUTTON',NULL,NULL,'dashboard:project-manager:view',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (808,0,1,'商务经理驾驶舱','BUTTON',NULL,NULL,'dashboard:business-manager:view',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (809,0,1,'成本经理驾驶舱','BUTTON',NULL,NULL,'dashboard:cost-manager:view',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (810,0,1,'财务驾驶舱','BUTTON',NULL,NULL,'dashboard:finance:view',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (811,0,1,'管理层驾驶舱','BUTTON',NULL,NULL,'dashboard:management:view',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (812,0,1,'成本明细下钻','BUTTON',NULL,NULL,'dashboard:cost-breakdown:view',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:37','2026-07-18 16:48:37',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (813,0,1,'采购经理驾驶舱','BUTTON',NULL,NULL,'dashboard:purchase-manager:view',NULL,7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (814,0,1,'生产经理驾驶舱','BUTTON',NULL,NULL,'dashboard:production-manager:view',NULL,8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (815,0,1,'总工程师驾驶舱','BUTTON',NULL,NULL,'dashboard:chief-engineer:view',NULL,9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (900,0,0,'工作台','DIR','/workbench',NULL,NULL,'home',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (901,0,0,'数据中心','DIR','/master-data',NULL,NULL,'project',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (902,0,0,'合同管理','DIR','/contract-domain',NULL,NULL,'contract',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (903,0,0,'成本管理','DIR','/cost-domain',NULL,NULL,'dollar',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (904,0,0,'采购与库存','DIR','/procurement-inventory',NULL,NULL,'shopping-cart',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (905,0,0,'分包管理','DIR','/subcontract-domain',NULL,NULL,'branches',6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (906,0,0,'付款与发票','DIR','/payment-invoice',NULL,NULL,'account-book',7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (907,0,0,'结算管理','DIR','/settlement-domain',NULL,NULL,'account-book',8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (908,0,0,'审批中心','DIR','/approval-center',NULL,NULL,'audit',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (909,0,0,'系统管理','DIR','/system-management',NULL,NULL,'setting',10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (917,0,904,'领料申请','MENU','/inventory/material-requisition','requisition/index','requisition:query','profile',7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (918,0,917,'提交领料审批','BUTTON',NULL,NULL,'requisition:submit',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (919,0,917,'新增领料申请','BUTTON',NULL,NULL,'requisition:add',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (920,0,901,'材料字典','MENU','/material/dictionary','material/dictionary','material:dict:list','database',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (921,0,902,'变更签证','MENU','/variation/order','variation/order','variation:order:query','swap',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (930,0,901,'成本科目','MENU','/cost/subject','cost-subject/index','cost:query','profile',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (931,0,903,'成本台账','MENU','/cost/ledger','cost/ledger','cost:ledger:query','account-book',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (932,0,903,'动态成本汇总','MENU','/cost/summary','cost/summary','cost:summary:view','fund',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (933,0,903,'目标成本','MENU','/cost-target/index','cost-target/index','cost:target:query','aim',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (940,0,904,'采购订单','MENU','/purchase/order','purchase/order','purchase:order:query','shopping-cart',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (941,0,904,'材料验收','MENU','/purchase/receipt','receipt/index','receipt:query','check-square',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (942,0,905,'分包任务','MENU','/subcontract/task','subcontract/task','subtask:query','branches',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (943,0,905,'分包计量','MENU','/subcontract/measure','subcontract/measure','subcontract:measure:query','calculator',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (944,0,906,'付款申请','MENU','/payment/application','payment/index','payment:app:query','dollar',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (945,0,907,'结算列表','MENU','/settlement/list','settlement/index','settlement:query','account-book',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (946,0,908,'我的待办','MENU','/approval/todo','approval/todo',NULL,'clock-circle',1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (947,0,908,'我的已办','MENU','/approval/done','approval/todo',NULL,'check-circle',2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (948,0,908,'抄送我的','MENU','/approval/cc','approval/todo',NULL,'mail',3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (949,0,901,'审批流程','MENU','/approval/process','approval/process',NULL,'deployment',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:59',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (950,0,909,'数据管理','MENU','/system/data','system/data/index',NULL,'database',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:48:40','2026-07-18 16:48:40',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (951,0,906,'付款记录查询','BUTTON',NULL,NULL,'payment:record:query',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (952,0,906,'资金日记账','MENU','/cash-journal','cash-journal/index','cashbook:journal:query','account-book',4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (953,0,952,'维护资金流水','BUTTON',NULL,NULL,'cashbook:journal:maintain',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (954,0,952,'导出资金流水','BUTTON',NULL,NULL,'cashbook:journal:export',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (955,0,952,'管理资金账户','BUTTON',NULL,NULL,'cashbook:account:manage',NULL,3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (956,0,734,'编辑采购申请','BUTTON',NULL,NULL,'purchase:request:edit',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (957,0,734,'删除采购申请','BUTTON',NULL,NULL,'purchase:request:delete',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:01','2026-07-18 16:49:01',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (958,0,201,'现场日报','MENU','/site/daily-log','site/daily-log','site:daily:query','file-text',8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:02','2026-07-18 16:49:02',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (959,0,958,'维护现场日报','BUTTON',NULL,NULL,'site:daily:edit',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:02','2026-07-18 16:49:02',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (960,0,906,'会计凭证','MENU','/accounting-entry','accounting-entry/index','accounting:query','account-book',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (961,0,960,'过账与冲销','BUTTON',NULL,NULL,'accounting:edit',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (962,0,903,'投标成本','MENU','/bid-cost','bid-cost/index','bid:query','fund',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (963,0,962,'新建投标项目','BUTTON',NULL,NULL,'bid:add',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (964,0,962,'编辑投标项目','BUTTON',NULL,NULL,'bid:edit',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (965,0,962,'删除投标项目','BUTTON',NULL,NULL,'bid:delete',NULL,3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (966,0,962,'变更投标状态','BUTTON',NULL,NULL,'bid:status',NULL,4,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:03','2026-07-18 16:49:03',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1020,0,2,'项目预算','MENU','budget','project/budget/index','budget:query','fund',8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1021,0,1020,'新增预算','BUTTON',NULL,NULL,'budget:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1022,0,1020,'编辑预算','BUTTON',NULL,NULL,'budget:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1023,0,1020,'删除预算','BUTTON',NULL,NULL,'budget:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1024,0,1020,'提交预算','BUTTON',NULL,NULL,'budget:submit',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1025,0,201,'项目状态变更','BUTTON',NULL,NULL,'project:status',NULL,5,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1026,0,201,'提交项目审批','BUTTON',NULL,NULL,'project:submit',NULL,6,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:38','2026-07-18 16:49:38',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1030,0,2,'费用申请','MENU','expense','expense/index','expense:query','receipt',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1031,0,1030,'新增费用申请','BUTTON',NULL,NULL,'expense:add',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1032,0,1030,'编辑费用申请','BUTTON',NULL,NULL,'expense:edit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1033,0,1030,'删除费用申请','BUTTON',NULL,NULL,'expense:delete',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1034,0,1030,'提交费用申请','BUTTON',NULL,NULL,'expense:submit',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:05','2026-07-18 16:49:05',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1040,0,2,'付款全链路追溯','MENU','payment-trace','payment/trace','payment:trace:query','connection',10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:07','2026-07-18 16:49:07',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1041,0,941,'供应商退货','BUTTON',NULL,NULL,'receipt:return',NULL,20,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:17','2026-07-18 16:49:17',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1042,0,917,'材料退料','BUTTON',NULL,NULL,'requisition:return',NULL,20,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:17','2026-07-18 16:49:17',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1043,0,917,'领料实际出库','BUTTON',NULL,NULL,'requisition:stock-out',NULL,21,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:17','2026-07-18 16:49:17',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1044,0,941,'采购全链追溯','BUTTON',NULL,NULL,'procurement:trace:query',NULL,22,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:17','2026-07-18 16:49:17',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1060,0,2,'资金运营','MENU','finance-operations','finance-operations/index','finance:operations:query','fund',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:09','2026-07-18 16:49:09',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1061,0,1060,'资金运营维护','BUTTON',NULL,NULL,'finance:operations:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:09','2026-07-18 16:49:09',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1062,0,1060,'执行财务对账','BUTTON',NULL,NULL,'finance:reconciliation:run',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:09','2026-07-18 16:49:09',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1063,0,906,'付款冲销','BUTTON',NULL,NULL,'payment:record:reverse',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:09','2026-07-18 16:49:09',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1064,0,1060,'资金分析维护','BUTTON',NULL,NULL,'finance:analytics:maintain',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:10','2026-07-18 16:49:10',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1065,0,1060,'财务审计导出','BUTTON',NULL,NULL,'finance:audit:export',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:10','2026-07-18 16:49:10',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1066,0,1060,'外部财务集成','BUTTON',NULL,NULL,'finance:integration:maintain',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:12','2026-07-18 16:49:12',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1067,0,1060,'资金池维护','BUTTON',NULL,NULL,'finance:pool:maintain',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:12','2026-07-18 16:49:12',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1070,0,2,'收入与回款','MENU','revenue','revenue/index','revenue:operations:query','fund',10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:14','2026-07-18 16:49:14',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1071,0,1070,'维护收入业务','BUTTON',NULL,NULL,'revenue:operations:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:14','2026-07-18 16:49:14',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1072,0,1070,'提交业主结算','BUTTON',NULL,NULL,'revenue:settlement:submit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:14','2026-07-18 16:49:14',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1073,0,1070,'回款冲销','BUTTON',NULL,NULL,'revenue:collection:reverse',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:14','2026-07-18 16:49:14',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1074,0,1070,'收入审计导出','BUTTON',NULL,NULL,'revenue:audit:export',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:14','2026-07-18 16:49:14',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1080,0,2,'产值计量','MENU','production-measurement','production-measurement/index','measurement:query','calculator',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:15','2026-07-18 16:49:15',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1081,0,1080,'维护计量','BUTTON',NULL,NULL,'measurement:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:15','2026-07-18 16:49:15',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1082,0,1080,'提交内部审批','BUTTON',NULL,NULL,'measurement:submit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:15','2026-07-18 16:49:15',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1083,0,1080,'提交业主报量','BUTTON',NULL,NULL,'measurement:owner:submit',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:15','2026-07-18 16:49:15',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1084,0,1080,'登记业主核定','BUTTON',NULL,NULL,'measurement:owner:review',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:15','2026-07-18 16:49:15',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1085,0,2,'项目计划','MENU','project-schedule','project-schedule/index','schedule:query','schedule',8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:20','2026-07-18 16:49:20',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1086,0,1085,'维护项目计划','BUTTON',NULL,NULL,'schedule:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:20','2026-07-18 16:49:20',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1087,0,1085,'提交计划审批','BUTTON',NULL,NULL,'schedule:submit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:20','2026-07-18 16:49:20',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1088,0,1085,'填报实际进度','BUTTON',NULL,NULL,'schedule:progress',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:20','2026-07-18 16:49:20',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1089,0,1085,'发起纠偏审批','BUTTON',NULL,NULL,'schedule:correct',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:20','2026-07-18 16:49:20',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1090,0,921,'提交业主申报','BUTTON',NULL,NULL,'variation:owner:submit',NULL,10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1091,0,921,'登记业主核定','BUTTON',NULL,NULL,'variation:owner:review',NULL,11,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1092,0,921,'变更全链追溯','BUTTON',NULL,NULL,'variation:trace',NULL,12,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1093,0,903,'动态利润控制','MENU','/cost/control','cost/control','cost:control:query','fund',5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1094,0,1093,'维护完工预测','BUTTON',NULL,NULL,'cost:forecast:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1095,0,1093,'确认完工预测','BUTTON',NULL,NULL,'cost:forecast:confirm',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1096,0,1093,'维护纠偏措施','BUTTON',NULL,NULL,'cost:corrective:maintain',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1097,0,1093,'提交与关闭纠偏','BUTTON',NULL,NULL,'cost:corrective:submit',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:21','2026-07-18 16:49:21',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1098,0,2,'质量安全整改','MENU','quality-safety','quality-safety/index','quality:safety:query','safety-certificate',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1099,0,1098,'维护检查计划','BUTTON',NULL,NULL,'quality:safety:plan:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1100,0,1098,'维护检查记录与问题','BUTTON',NULL,NULL,'quality:safety:inspection:maintain',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1101,0,1098,'提交整改','BUTTON',NULL,NULL,'quality:safety:rectify',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1102,0,1098,'复验与关闭','BUTTON',NULL,NULL,'quality:safety:reinspect',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1103,0,1098,'处罚成本与评价','BUTTON',NULL,NULL,'quality:safety:consequence',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:22','2026-07-18 16:49:22',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1104,0,2,'供应商招采履约','MENU','supplier-sourcing','supplier-sourcing/index','supplier:sourcing:query','team',10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1105,0,1104,'维护询价招标','BUTTON',NULL,NULL,'supplier:sourcing:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1106,0,1104,'维护供应商报价','BUTTON',NULL,NULL,'supplier:sourcing:quote',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1107,0,1104,'执行比价评审','BUTTON',NULL,NULL,'supplier:sourcing:evaluate',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1108,0,1104,'执行定标与合同关联','BUTTON',NULL,NULL,'supplier:sourcing:award',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1109,0,1104,'确认履约评价','BUTTON',NULL,NULL,'supplier:performance:evaluate',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1110,0,1104,'审核供应商黑名单','BUTTON',NULL,NULL,'supplier:blacklist:review',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1111,0,2,'技术管理','MENU','technical-management','technical-management/index','technical:query','file-search',9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1112,0,1111,'维护技术方案','BUTTON',NULL,NULL,'technical:scheme:maintain',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1113,0,1111,'提交技术方案','BUTTON',NULL,NULL,'technical:scheme:submit',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1114,0,1111,'接收图纸版本','BUTTON',NULL,NULL,'technical:drawing:receive',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1115,0,1111,'确认图纸会审','BUTTON',NULL,NULL,'technical:drawing:review',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1116,0,1111,'发起RFI','BUTTON',NULL,NULL,'technical:rfi:raise',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1117,0,1111,'回复RFI','BUTTON',NULL,NULL,'technical:rfi:respond',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1118,0,1111,'确认RFI回复','BUTTON',NULL,NULL,'technical:rfi:accept',NULL,7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1119,0,1111,'技术交底与施工引用','BUTTON',NULL,NULL,'technical:disclosure:maintain',NULL,8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1120,0,1111,'验收归档','BUTTON',NULL,NULL,'technical:archive:confirm',NULL,9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:23','2026-07-18 16:49:23',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1121,0,2,'项目竣工收尾','MENU','project-closeout','project-closeout/index','closeout:query','check-circle',13,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1122,0,1121,'发起项目收尾','BUTTON',NULL,NULL,'closeout:initiate',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1123,0,1121,'维护分项验收','BUTTON',NULL,NULL,'closeout:section:maintain',NULL,2,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1124,0,1121,'提交竣工验收','BUTTON',NULL,NULL,'closeout:acceptance:submit',NULL,3,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1125,0,1121,'绑定竣工结算','BUTTON',NULL,NULL,'closeout:settlement:bind',NULL,4,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1126,0,1121,'确认尾款回收','BUTTON',NULL,NULL,'closeout:collection:verify',NULL,5,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1127,0,1121,'维护质保责任','BUTTON',NULL,NULL,'closeout:warranty:maintain',NULL,6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1128,0,1121,'整改缺陷','BUTTON',NULL,NULL,'closeout:defect:maintain',NULL,7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1129,0,1121,'复验缺陷','BUTTON',NULL,NULL,'closeout:defect:verify',NULL,8,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1130,0,1121,'移交竣工档案','BUTTON',NULL,NULL,'closeout:archive:maintain',NULL,9,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1131,0,1121,'关闭项目','BUTTON',NULL,NULL,'closeout:close',NULL,10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:24','2026-07-18 16:49:24',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1132,0,906,'财务月结','MENU','/financial-close','financial-close/index','finance:close:query','account-book',6,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1133,0,1132,'复核会计凭证','BUTTON',NULL,NULL,'accounting:review',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1134,0,1132,'凭证过账','BUTTON',NULL,NULL,'accounting:post',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1135,0,1132,'运行月结检查','BUTTON',NULL,NULL,'finance:close:check',NULL,3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1136,0,1132,'执行月结','BUTTON',NULL,NULL,'finance:close:close',NULL,4,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1137,0,1132,'反结账','BUTTON',NULL,NULL,'finance:close:reopen',NULL,5,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1138,0,1132,'处理对账差异','BUTTON',NULL,NULL,'finance:close:reconcile',NULL,6,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1139,0,1132,'创建调整凭证','BUTTON',NULL,NULL,'accounting:adjustment:add',NULL,7,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:25','2026-07-18 16:49:25',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1140,0,906,'资金计划与现金预测','MENU','/cash-forecast','cash-forecast/index','finance:forecast:query','fund',7,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1141,0,1140,'维护资金预测','BUTTON',NULL,NULL,'finance:forecast:maintain',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1142,0,1140,'提交资金预测','BUTTON',NULL,NULL,'finance:forecast:submit',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1143,0,1140,'审批资金预测','BUTTON',NULL,NULL,'finance:forecast:approve',NULL,3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1144,0,1140,'刷新实际偏差','BUTTON',NULL,NULL,'finance:forecast:refresh',NULL,4,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1145,0,1140,'维护缺口措施','BUTTON',NULL,NULL,'finance:forecast:action',NULL,5,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1146,0,1140,'审批缺口措施','BUTTON',NULL,NULL,'finance:forecast:action:approve',NULL,6,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:26','2026-07-18 16:49:26',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (1420,0,732,'维护安全库存阈值','BUTTON',NULL,NULL,'inventory:stock:edit',NULL,1,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:02','2026-07-18 16:49:02',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2120,0,5,'业务单据模板','MENU','document-template',NULL,'document:template:query','document',20,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2121,0,2120,'维护业务单据模板','BUTTON',NULL,NULL,'document:template:edit',NULL,1,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2122,0,2120,'发布业务单据模板','BUTTON',NULL,NULL,'document:template:publish',NULL,2,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2123,0,2120,'生成业务单据PDF','BUTTON',NULL,NULL,'document:generate',NULL,3,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2124,0,2120,'查询业务单据历史','BUTTON',NULL,NULL,'document:history:query',NULL,4,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2125,0,2120,'下载业务单据PDF','BUTTON',NULL,NULL,'document:download',NULL,5,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2126,0,2120,'审计下载业务单据PDF','BUTTON',NULL,NULL,'document:audit:download',NULL,6,'ENABLE',0,NULL,NULL,NULL,'2026-07-18 16:49:43','2026-07-18 16:49:43',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2130,0,930,'维护成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:edit',NULL,10,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2131,0,930,'启用成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:activate',NULL,11,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2132,0,930,'维护归集规则','BUTTON',NULL,NULL,'cost:subject:rule:edit',NULL,12,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2133,0,930,'维护项目适用范围','BUTTON',NULL,NULL,'cost:subject:scope:edit',NULL,13,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2134,0,930,'查询影响与对账','BUTTON',NULL,NULL,'cost:subject:audit:query',NULL,14,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2135,0,930,'执行投标成本转入','BUTTON',NULL,NULL,'cost:subject:bid-transfer',NULL,15,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2136,0,930,'执行财务费用分摊','BUTTON',NULL,NULL,'cost:subject:finance-allocate',NULL,16,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2137,0,930,'查询成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:query',NULL,17,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2138,0,930,'查询归集规则','BUTTON',NULL,NULL,'cost:subject:rule:query',NULL,18,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`, `visible`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`) VALUES (2139,0,930,'查询项目适用范围','BUTTON',NULL,NULL,'cost:subject:scope:query',NULL,19,'ENABLE',1,NULL,NULL,NULL,'2026-07-18 16:49:47','2026-07-18 16:49:47',0);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (1,0,1,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (2,0,1,2);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (3,0,1,3);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (4,0,1,4);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (5,0,1,5);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (201,0,1,201);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (202,0,1,202);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (203,0,1,203);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (204,0,1,204);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (301,0,1,301);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (302,0,1,302);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (303,0,1,303);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (304,0,1,304);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (305,0,1,305);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (401,0,1,401);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (402,0,1,402);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (403,0,1,403);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (404,0,1,404);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (501,0,1,501);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (502,0,1,502);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (503,0,1,503);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (504,0,1,504);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10653,0,1,613);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10654,0,1,614);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10655,0,1,615);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10656,0,1,616);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10657,0,1,617);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10658,0,1,618);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10700,0,1,700);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10701,0,1,701);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10702,0,1,702);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10703,0,1,703);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10704,0,1,704);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10710,0,1,710);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10711,0,1,711);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10712,0,1,712);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10713,0,1,713);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10714,0,1,714);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10720,0,1,720);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10721,0,1,721);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10722,0,1,722);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10723,0,1,723);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10730,0,1,730);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10731,0,1,731);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10732,0,1,732);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10733,0,1,733);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10734,0,1,734);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10735,0,1,735);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10736,0,1,736);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10737,0,1,737);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10738,0,1,738);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10739,0,1,739);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10740,0,1,740);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10751,0,1,751);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10752,0,1,752);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10753,0,1,753);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10754,0,1,754);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10755,0,1,755);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10761,0,1,761);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10782,0,1,762);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10783,0,1,763);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10784,0,1,764);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10785,0,1,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10786,0,1,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10787,0,1,767);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10788,0,1,768);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10830,0,1,800);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10831,0,1,801);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10832,0,1,802);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10833,0,1,803);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10834,0,1,804);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10835,0,1,805);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10836,0,1,806);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10837,0,1,807);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10838,0,1,808);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10839,0,1,809);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10840,0,1,810);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10841,0,1,811);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10842,0,1,812);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10843,0,1,813);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (10844,0,1,814);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600900,0,1,900);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600901,0,1,901);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600902,0,1,902);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600903,0,1,903);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600904,0,1,904);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600905,0,1,905);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600906,0,1,906);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600907,0,1,907);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600908,0,1,908);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600909,0,1,909);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (128001,0,1,917);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (128002,0,1,918);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (130001,0,1,919);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600920,0,1,920);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600921,0,1,921);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600930,0,1,930);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600931,0,1,931);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600932,0,1,932);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600933,0,1,933);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600940,0,1,940);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600941,0,1,941);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600942,0,1,942);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600943,0,1,943);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600944,0,1,944);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600945,0,1,945);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600946,0,1,946);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600947,0,1,947);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600948,0,1,948);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600949,0,1,949);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (600950,0,1,950);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (136952,0,1,952);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (136953,0,1,953);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (136954,0,1,954);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (136955,0,1,955);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (141958,0,1,958);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (141959,0,1,959);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (148001960,0,1,960);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (148001961,0,1,961);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (150001962,0,1,962);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (151001963,0,1,963);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (152001964,0,1,964);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (153001965,0,1,965);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (154001966,0,1,966);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011020,0,1,1020);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011021,0,1,1021);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011022,0,1,1022);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011023,0,1,1023);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011024,0,1,1024);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158011025,0,1,1025);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (197011026,0,1,1026);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160011030,0,1,1030);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160011031,0,1,1031);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160011032,0,1,1032);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160011033,0,1,1033);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160011034,0,1,1034);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (165011040,0,1,1040);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183011041,0,1,1041);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183011042,0,1,1042);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183011043,0,1,1043);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183011044,0,1,1044);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (170011060,0,1,1060);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011061,0,1,1061);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011062,0,1,1062);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011063,0,1,1063);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011064,0,1,1064);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (172011065,0,1,1065);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011066,0,1,1066);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011067,0,1,1067);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011070,0,1,1070);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011071,0,1,1071);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011072,0,1,1072);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011073,0,1,1073);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171011074,0,1,1074);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175011080,0,1,1080);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175011081,0,1,1081);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175011082,0,1,1082);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175011083,0,1,1083);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175011084,0,1,1084);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185011085,0,1,1085);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185011086,0,1,1086);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185011087,0,1,1087);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185011088,0,1,1088);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185011089,0,1,1089);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186011090,0,1,1090);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186011091,0,1,1091);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186011092,0,1,1092);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187011093,0,1,1093);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187011094,0,1,1094);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187011095,0,1,1095);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187011096,0,1,1096);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187011097,0,1,1097);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011098,0,1,1098);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011099,0,1,1099);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011100,0,1,1100);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011101,0,1,1101);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011102,0,1,1102);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188011103,0,1,1103);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011104,0,1,1104);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011105,0,1,1105);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011106,0,1,1106);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011107,0,1,1107);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011108,0,1,1108);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011109,0,1,1109);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189011110,0,1,1110);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011111,0,1,1111);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011112,0,1,1112);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011113,0,1,1113);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011114,0,1,1114);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011115,0,1,1115);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011116,0,1,1116);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011117,0,1,1117);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011118,0,1,1118);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011119,0,1,1119);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190011120,0,1,1120);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011121,0,1,1121);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011122,0,1,1122);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011123,0,1,1123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011124,0,1,1124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011125,0,1,1125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011126,0,1,1126);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011127,0,1,1127);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011128,0,1,1128);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011129,0,1,1129);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011130,0,1,1130);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191011131,0,1,1131);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011132,0,1,1132);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011133,0,1,1133);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011134,0,1,1134);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011135,0,1,1135);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011136,0,1,1136);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011137,0,1,1137);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011138,0,1,1138);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192011139,0,1,1139);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011140,0,1,1140);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011141,0,1,1141);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011142,0,1,1142);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011143,0,1,1143);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011144,0,1,1144);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011145,0,1,1145);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193011146,0,1,1146);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212012120,0,1,2120);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212012121,0,1,2121);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212012122,0,1,2122);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212112123,0,1,2123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212112124,0,1,2124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212112125,0,1,2125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212212126,0,1,2126);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012130,0,1,2130);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012131,0,1,2131);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012132,0,1,2132);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012133,0,1,2133);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012134,0,1,2134);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213112135,0,1,2135);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213212136,0,1,2136);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012137,0,1,2137);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012138,0,1,2138);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213012139,0,1,2139);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (123001,0,2,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (123002,0,2,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (143958,0,2,958);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021020,0,2,1020);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021021,0,2,1021);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021022,0,2,1022);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021023,0,2,1023);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021024,0,2,1024);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (158021025,0,2,1025);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (197021026,0,2,1026);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160021030,0,2,1030);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160021031,0,2,1031);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160021032,0,2,1032);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160021033,0,2,1033);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160021034,0,2,1034);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (165021040,0,2,1040);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183021041,0,2,1041);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183021042,0,2,1042);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183021043,0,2,1043);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183021044,0,2,1044);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (170021060,0,2,1060);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171021070,0,2,1070);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171021071,0,2,1071);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171021072,0,2,1072);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171021073,0,2,1073);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171021074,0,2,1074);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175021080,0,2,1080);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175021081,0,2,1081);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175021082,0,2,1082);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175021083,0,2,1083);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175021084,0,2,1084);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185021085,0,2,1085);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185021086,0,2,1086);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185021087,0,2,1087);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185021088,0,2,1088);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (185021089,0,2,1089);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186021090,0,2,1090);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186021091,0,2,1091);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186021092,0,2,1092);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187021093,0,2,1093);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187021094,0,2,1094);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187021095,0,2,1095);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187021096,0,2,1096);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187021097,0,2,1097);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021098,0,2,1098);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021099,0,2,1099);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021100,0,2,1100);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021101,0,2,1101);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021102,0,2,1102);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (188021103,0,2,1103);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021104,0,2,1104);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021105,0,2,1105);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021106,0,2,1106);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021107,0,2,1107);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021108,0,2,1108);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021109,0,2,1109);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189021110,0,2,1110);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021111,0,2,1111);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021112,0,2,1112);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021113,0,2,1113);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021114,0,2,1114);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021115,0,2,1115);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021116,0,2,1116);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021117,0,2,1117);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021118,0,2,1118);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021119,0,2,1119);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190021120,0,2,1120);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021121,0,2,1121);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021122,0,2,1122);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021123,0,2,1123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021124,0,2,1124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021125,0,2,1125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021126,0,2,1126);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021127,0,2,1127);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021128,0,2,1128);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021129,0,2,1129);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021130,0,2,1130);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191021131,0,2,1131);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193921140,0,2,1140);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212122123,0,2,2123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212122124,0,2,2124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212122125,0,2,2125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213122135,0,2,2135);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120001,0,4,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120004,0,4,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120005,0,4,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120002,0,4,803);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120003,0,4,808);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (70001,0,5,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (70005,0,5,5);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50710,0,5,710);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50711,0,5,711);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50712,0,5,712);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50713,0,5,713);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50714,0,5,714);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50720,0,5,720);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50721,0,5,721);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50722,0,5,722);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50723,0,5,723);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50730,0,5,730);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50731,0,5,731);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50732,0,5,732);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50733,0,5,733);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50734,0,5,734);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50735,0,5,735);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50736,0,5,736);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50737,0,5,737);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50738,0,5,738);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50739,0,5,739);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (50740,0,5,740);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (70765,0,5,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (70766,0,5,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (700005900,0,5,900);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (700005904,0,5,904);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (128003,0,5,917);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (128004,0,5,918);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (80001,0,6,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (80608,0,6,608);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60720,0,6,720);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60751,0,6,751);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60752,0,6,752);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60753,0,6,753);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60754,0,6,754);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60755,0,6,755);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60762,0,6,762);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60765,0,6,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60766,0,6,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60767,0,6,767);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60768,0,6,768);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (60810,0,6,810);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (700006900,0,6,900);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (700006906,0,6,906);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (135907,0,6,907);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (135944,0,6,944);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (135945,0,6,945);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (135951,0,6,951);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137952,0,6,952);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137953,0,6,953);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137954,0,6,954);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (148006960,0,6,960);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (148006961,0,6,961);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160061030,0,6,1030);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160061031,0,6,1031);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160061032,0,6,1032);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160061033,0,6,1033);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (160061034,0,6,1034);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (165061040,0,6,1040);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (170061060,0,6,1060);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061061,0,6,1061);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061062,0,6,1062);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061063,0,6,1063);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061064,0,6,1064);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (172061065,0,6,1065);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061066,0,6,1066);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061067,0,6,1067);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061070,0,6,1070);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061071,0,6,1071);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061072,0,6,1072);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061073,0,6,1073);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (171061074,0,6,1074);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175061080,0,6,1080);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175061081,0,6,1081);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175061082,0,6,1082);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175061083,0,6,1083);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (175061084,0,6,1084);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186061090,0,6,1090);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186061091,0,6,1091);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (186061092,0,6,1092);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187061093,0,6,1093);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187061094,0,6,1094);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187061095,0,6,1095);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187061096,0,6,1096);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (187061097,0,6,1097);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061121,0,6,1121);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061122,0,6,1122);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061123,0,6,1123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061124,0,6,1124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061125,0,6,1125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061126,0,6,1126);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061127,0,6,1127);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061128,0,6,1128);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061129,0,6,1129);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061130,0,6,1130);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (191061131,0,6,1131);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061132,0,6,1132);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061133,0,6,1133);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061134,0,6,1134);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061135,0,6,1135);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061136,0,6,1136);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061137,0,6,1137);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061138,0,6,1138);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (192061139,0,6,1139);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061140,0,6,1140);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061141,0,6,1141);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061142,0,6,1142);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061143,0,6,1143);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061144,0,6,1144);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061145,0,6,1145);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (193061146,0,6,1146);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212162123,0,6,2123);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212162124,0,6,2124);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (212162125,0,6,2125);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (213262136,0,6,2136);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97001,0,7,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (139002,0,7,504);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (138001,0,7,731);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137002,0,7,732);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137003,0,7,734);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137004,0,7,739);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137005,0,7,740);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120011,0,7,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120012,0,7,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97002,0,7,803);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (139001,0,7,804);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97003,0,7,813);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137001,0,7,904);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (138002,0,7,920);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137006,0,7,956);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (137007,0,7,957);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183071041,0,7,1041);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183071042,0,7,1042);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183071043,0,7,1043);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (183071044,0,7,1044);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071104,0,7,1104);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071105,0,7,1105);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071106,0,7,1106);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071107,0,7,1107);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071108,0,7,1108);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071109,0,7,1109);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (189071110,0,7,1110);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (142001,0,7,1420);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97011,0,8,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120021,0,8,765);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (120022,0,8,766);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97012,0,8,803);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (97013,0,8,814);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (142958,0,8,958);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (142959,0,8,959);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (99001,0,9,1);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (99002,0,9,803);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (99003,0,9,815);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091111,0,9,1111);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091112,0,9,1112);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091113,0,9,1113);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091114,0,9,1114);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091115,0,9,1115);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091116,0,9,1116);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091117,0,9,1117);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091118,0,9,1118);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091119,0,9,1119);
INSERT INTO `sys_role_menu` (`id`, `tenant_id`, `role_id`, `menu_id`) VALUES (190091120,0,9,1120);
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1001,0,'project_status','项目状态','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1002,0,'contract_type','合同类型','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1003,0,'contract_status','合同状态','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1004,0,'approval_status','审批状态','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1005,0,'partner_type','合作方类型','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1006,0,'pay_type','付款类型','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1007,0,'cost_type','成本类型','ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1008,0,'common_status','通用状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1009,0,'wf_instance_status','工作流实例状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1010,0,'wf_task_status','工作流任务状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1011,0,'wf_node_status','工作流节点状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1012,0,'approve_mode','审批模式','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1013,0,'settlement_status','结算生命周期状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1014,0,'settlement_final_status','结算定案状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1015,0,'pay_status','付款状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1016,0,'cost_target_status','成本目标状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1017,0,'purchase_order_status','采购订单状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1018,0,'purchase_request_status','采购申请状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (1019,0,'sub_measure_status','分包计量状态','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_at`, `updated_at`) VALUES (132000,0,'project_type','项目类型','ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100101,0,1001,'草稿','DRAFT',NULL,'info',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100102,0,1001,'在建','ONGOING',NULL,'primary',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100103,0,1001,'已竣工','COMPLETED',NULL,'success',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100104,0,1001,'已暂停','SUSPENDED',NULL,'warning',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100105,0,1001,'已关闭','CLOSED',NULL,'danger',5,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100201,0,1002,'总包合同','MAIN',NULL,'primary',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100202,0,1002,'分包合同','SUB',NULL,'success',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100203,0,1002,'采购合同','PURCHASE',NULL,'info',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100204,0,1002,'租赁合同','LEASE',NULL,'warning',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100205,0,1002,'服务合同','SERVICE',NULL,'default',5,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100301,0,1003,'草稿','DRAFT',NULL,'info',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100302,0,1003,'履约中','PERFORMING',NULL,'primary',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100303,0,1003,'已结算','SETTLED',NULL,'success',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100304,0,1003,'已终止','TERMINATED',NULL,'danger',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100401,0,1004,'草稿','DRAFT',NULL,'info',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100402,0,1004,'审批中','APPROVING',NULL,'warning',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100403,0,1004,'已通过','APPROVED',NULL,'success',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100404,0,1004,'已驳回','REJECTED',NULL,'danger',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100405,0,1004,'已撤回','WITHDRAWN',NULL,'default',5,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100501,0,1005,'供应商','SUPPLIER',NULL,'primary',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100502,0,1005,'分包商','SUBCONTRACTOR',NULL,'success',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100503,0,1005,'租赁商','LESSOR',NULL,'info',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100504,0,1005,'服务商','SERVICE_PROVIDER',NULL,'warning',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100601,0,1006,'预付款','ADVANCE',NULL,'primary',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100602,0,1006,'进度款','PROGRESS',NULL,'success',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100603,0,1006,'结算款','SETTLEMENT',NULL,'info',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100604,0,1006,'质保金','WARRANTY',NULL,'warning',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100701,0,1007,'材料费','MATERIAL',NULL,'primary',1,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100702,0,1007,'分包费','SUBCONTRACT',NULL,'success',2,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100703,0,1007,'机械费','MACHINERY',NULL,'info',3,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100704,0,1007,'人工费','LABOR',NULL,'warning',4,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100705,0,1007,'签证费','VISA',NULL,'default',5,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100706,0,1007,'管理费','MANAGEMENT',NULL,'default',6,'ENABLE','2026-07-18 16:48:32','2026-07-18 16:48:32');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100801,0,1008,'启用','ENABLE',NULL,'success',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100802,0,1008,'禁用','DISABLE',NULL,'danger',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100901,0,1009,'审批中','RUNNING',NULL,'primary',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100902,0,1009,'已通过','APPROVED',NULL,'success',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100903,0,1009,'已驳回','REJECTED',NULL,'danger',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100904,0,1009,'已撤回','WITHDRAWN',NULL,'default',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (100905,0,1009,'已作废','VOIDED',NULL,'default',5,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101001,0,1010,'待处理','PENDING',NULL,'warning',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101002,0,1010,'已通过','APPROVED',NULL,'success',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101003,0,1010,'已驳回','REJECTED',NULL,'danger',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101004,0,1010,'已取消','CANCELLED',NULL,'default',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101005,0,1010,'已转办','TRANSFERRED',NULL,'primary',5,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101101,0,1011,'等待中','WAITING',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101102,0,1011,'激活中','ACTIVE',NULL,'primary',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101103,0,1011,'已完成','COMPLETED',NULL,'success',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101104,0,1011,'已驳回','REJECTED',NULL,'danger',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101105,0,1011,'已跳过','SKIPPED',NULL,'default',5,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101201,0,1012,'顺序审批','SEQUENTIAL',NULL,'primary',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101202,0,1012,'会签审批','COUNTERSIGN',NULL,'success',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101203,0,1012,'或签审批','OR_SIGN',NULL,'warning',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101301,0,1013,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101302,0,1013,'已提交','SUBMITTED',NULL,'primary',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101303,0,1013,'已通过','APPROVED',NULL,'success',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101304,0,1013,'已驳回','REJECTED',NULL,'danger',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101305,0,1013,'已作废','CANCELLED',NULL,'default',5,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101401,0,1014,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101402,0,1014,'已计算','CALCULATED',NULL,'primary',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101403,0,1014,'已定案','FINALIZED',NULL,'success',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101501,0,1015,'待付款','PENDING',NULL,'warning',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101502,0,1015,'未付款','UNPAID',NULL,'default',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101503,0,1015,'已付款','PAID',NULL,'success',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101504,0,1015,'部分付款','PARTIAL',NULL,'primary',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101601,0,1016,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101602,0,1016,'已生效','ACTIVE',NULL,'success',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101603,0,1016,'已作废','CANCELLED',NULL,'danger',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101701,0,1017,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101702,0,1017,'审批中','APPROVING',NULL,'warning',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101703,0,1017,'履行中','PERFORMING',NULL,'primary',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101704,0,1017,'已完成','COMPLETED',NULL,'success',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101705,0,1017,'已取消','CANCELLED',NULL,'danger',5,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101801,0,1018,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101802,0,1018,'已转PO','CONVERTED',NULL,'primary',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101901,0,1019,'草稿','DRAFT',NULL,'default',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101902,0,1019,'审批中','APPROVING',NULL,'warning',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101903,0,1019,'已确认','CONFIRMED',NULL,'primary',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (101904,0,1019,'已完成','COMPLETED',NULL,'success',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (132001,0,132000,'施工总承包','施工总承包',NULL,'blue',1,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (132002,0,132000,'专业分包','专业分包',NULL,'green',2,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (132003,0,132000,'劳务分包','劳务分包',NULL,'orange',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (132004,0,132000,'材料采购','材料采购',NULL,'purple',4,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `sys_dict_data` (`id`, `tenant_id`, `dict_type_id`, `dict_label`, `dict_value`, `css_class`, `list_class`, `order_num`, `status`, `created_at`, `updated_at`) VALUES (132005,0,1018,'已通过','APPROVED',NULL,'success',3,'ENABLE','2026-07-18 16:49:01','2026-07-18 16:49:01');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900000,0,0,'LEGACY_STANDARD_COST_ROOT','成本科目根',NULL,0,0,'DISABLE',NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:49:27',0,'ROOT');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900001,0,0,'5401','合同履约成本','ROOT',1,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900002,0,900000,'LEGACY_CONTRACT_REVENUE','合同收入',NULL,1,20,'DISABLE',NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:49:27',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900007,0,900002,'LEGACY_CONSTRUCTION_REVENUE','合同建造收入',NULL,2,10,'DISABLE',NULL,NULL,NULL,'2026-07-18 16:48:59','2026-07-18 16:49:27',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900010,0,900001,'5401.01','招投标及前期费用','BID',2,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900011,0,900010,'5401.01.01','投标费用','BID',3,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900012,0,900010,'5401.01.02','投标保证金','BID_DEPOSIT',3,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'RECEIVABLE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900013,0,900010,'5401.01.03','标书制作费','BID',3,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900014,0,900010,'5401.01.04','招投标代理费','BID',3,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900015,0,900010,'5401.01.05','前期勘察费','BID',3,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900016,0,900010,'5401.01.06','前期咨询费','BID',3,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900017,0,900010,'5401.01.07','前期差旅费','BID',3,7,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900018,0,900010,'5401.01.08','资格预审费','BID',3,8,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900019,0,900010,'5401.01.09','履约保证金','BID_DEPOSIT',3,9,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'RECEIVABLE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900020,0,900010,'5401.01.10','前期其他费','BID',3,10,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900030,0,900001,'5401.02','采购阶段成本','PURCHASE',2,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900031,0,900030,'5401.02.01','设备采购费','PURCHASE',3,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900032,0,900030,'5401.02.02','材料采购费','MATERIAL',3,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900033,0,900030,'5401.02.03','采购运杂费','PURCHASE',3,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900034,0,900030,'5401.02.04','采购保管费','PURCHASE',3,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900035,0,900030,'5401.02.05','检验试验费','TESTING',3,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900036,0,900030,'5401.02.06','采购管理费','PURCHASE',3,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900040,0,900001,'5401.03','施工阶段成本','CONSTRUCTION',2,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900041,0,900040,'5401.03.01','人工费','LABOR',3,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900042,0,900041,'5401.03.01.01','自有工人工资','LABOR',4,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900043,0,900041,'5401.03.01.02','劳务分包费','LABOR',4,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900044,0,900041,'5401.03.01.03','临时工费用','LABOR',4,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900045,0,900040,'5401.03.02','材料费','MATERIAL',3,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900046,0,900045,'5401.03.02.01','结构材料','MATERIAL',4,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900047,0,900045,'5401.03.02.02','装饰材料','MATERIAL',4,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900048,0,900045,'5401.03.02.03','安装材料','MATERIAL',4,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900049,0,900045,'5401.03.02.04','辅助材料','MATERIAL',4,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900050,0,900040,'5401.03.03','机械使用费','MACHINERY',3,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900051,0,900050,'5401.03.03.01','自有机械费','MACHINERY',4,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900052,0,900050,'5401.03.03.02','租赁机械费','MACHINERY',4,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900053,0,900050,'5401.03.03.03','机械进出场费','MACHINERY',4,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900055,0,900040,'5401.03.04','工程水电费','UTILITY',3,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900056,0,900040,'5401.03.05','专业分包费','SUBCONTRACT',3,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900057,0,900056,'5401.03.05.01','土方分包','SUBCONTRACT',4,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900058,0,900056,'5401.03.05.02','桩基分包','SUBCONTRACT',4,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900059,0,900056,'5401.03.05.03','防水防腐','SUBCONTRACT',4,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900060,0,900056,'5401.03.05.04','钢结构分包','SUBCONTRACT',4,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900061,0,900056,'5401.03.05.05','幕墙分包','SUBCONTRACT',4,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900062,0,900056,'5401.03.05.06','机电分包','SUBCONTRACT',4,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900063,0,900056,'5401.03.05.07','智能化分包','SUBCONTRACT',4,7,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900064,0,900056,'5401.03.05.08','消防分包','SUBCONTRACT',4,8,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900065,0,900056,'5401.03.05.09','精装修分包','SUBCONTRACT',4,9,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900066,0,900056,'5401.03.05.10','景观绿化分包','SUBCONTRACT',4,10,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900067,0,900040,'5401.03.06','措施费','MEASURES',3,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900068,0,900067,'5401.03.06.01','周转材料费','MEASURES',4,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900069,0,900067,'5401.03.06.02','临时设施费','MEASURES',4,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900070,0,900067,'5401.03.06.03','安全文明施工费','MEASURES',4,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900071,0,900067,'5401.03.06.04','夜间施工费','MEASURES',4,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900072,0,900067,'5401.03.06.05','冬雨季施工费','MEASURES',4,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900073,0,900067,'5401.03.06.06','二次搬运费','MEASURES',4,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900074,0,900067,'5401.03.06.07','环境保护费','MEASURES',4,7,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900075,0,900067,'5401.03.06.08','排水降水费','MEASURES',4,8,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900076,0,900067,'5401.03.06.09','已完工程保护费','MEASURES',4,9,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900077,0,900067,'5401.03.06.10','赶工费','MEASURES',4,10,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900078,0,900067,'5401.03.06.11','停工损失','MEASURES',4,11,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900079,0,900040,'5401.03.07','其他直接费','OTHER',3,7,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900080,0,900001,'5401.04','项目间接费用','OVERHEAD',2,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900081,0,900080,'5401.04.01','管理人员薪酬','OVERHEAD',3,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900082,0,900080,'5401.04.02','办公费','OVERHEAD',3,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900083,0,900080,'5401.04.03','差旅交通费','OVERHEAD',3,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900084,0,900080,'5401.04.04','业务招待费','OVERHEAD',3,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900085,0,900080,'5401.04.05','固定资产使用费','OVERHEAD',3,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900086,0,900080,'5401.04.06','低值易耗品摊销','OVERHEAD',3,6,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900087,0,900080,'5401.04.07','保险费','OVERHEAD',3,7,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900088,0,900080,'5401.04.08','检测试验费','OVERHEAD',3,8,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900089,0,900080,'5401.04.09','工程保修费','OVERHEAD',3,9,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900090,0,900080,'5401.04.10','排污费','OVERHEAD',3,10,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900091,0,900080,'5401.04.11','劳动保护费','OVERHEAD',3,11,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900092,0,900080,'5401.04.12','取暖费','OVERHEAD',3,12,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900093,0,900080,'5401.04.13','材料整理及零星运费','OVERHEAD',3,13,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900094,0,900080,'5401.04.14','材料盘亏及毁损','OVERHEAD',3,14,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900095,0,900080,'5401.04.15','外单位管理费','OVERHEAD',3,15,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900096,0,900080,'5401.04.16','职工教育经费','OVERHEAD',3,16,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900097,0,900080,'5401.04.17','工会经费','OVERHEAD',3,17,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900098,0,900080,'5401.04.18','劳动保险费','OVERHEAD',3,18,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900099,0,900080,'5401.04.19','财务费用','OVERHEAD',3,19,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900100,0,900080,'5401.04.20','其他间接费','OVERHEAD',3,20,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'COST');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900200,0,0,'6001','主营业务收入','REVENUE_MAIN',1,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900201,0,900200,'6001.01','合同建造收入','REVENUE_MAIN',2,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900202,0,900200,'6001.02','变更签证收入','REVENUE_MAIN',2,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900203,0,900200,'6001.03','索赔收入','REVENUE_MAIN',2,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900204,0,900200,'6001.04','奖励收入','REVENUE_MAIN',2,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900210,0,0,'6051','其他业务收入','REVENUE_OTHER',1,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900211,0,900210,'6051.01','材料销售收入','REVENUE_OTHER',2,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900212,0,900210,'6051.02','固定资产出租收入','REVENUE_OTHER',2,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900213,0,900210,'6051.03','周转材料出租收入','REVENUE_OTHER',2,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900214,0,900210,'6051.04','技术服务收入','REVENUE_OTHER',2,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900215,0,900210,'6051.05','劳务服务收入','REVENUE_OTHER',2,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900220,0,0,'6301','营业外收入','REVENUE_EXTRA',1,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900221,0,900220,'6301.01','违约金收入','REVENUE_EXTRA',2,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900222,0,900220,'6301.02','罚款收入','REVENUE_EXTRA',2,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900223,0,900220,'6301.03','赔偿收入','REVENUE_EXTRA',2,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900224,0,900220,'6301.04','盘盈利得','REVENUE_EXTRA',2,4,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900225,0,900220,'6301.05','政府补助','REVENUE_EXTRA',2,5,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'REVENUE');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900300,0,0,'SETTLE','合同结算','SETTLEMENT',1,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'SETTLEMENT');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900301,0,900300,'SETTLE.01','合同结算-收入结转','SETTLEMENT',2,1,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'SETTLEMENT');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900302,0,900300,'SETTLE.02','合同结算-价款结算','SETTLEMENT',2,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'SETTLEMENT');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900310,0,0,'CONTRACT_ASSET','合同资产','SETTLEMENT',1,2,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'SETTLEMENT');
INSERT INTO `cost_subject` (`id`, `tenant_id`, `parent_id`, `subject_code`, `subject_name`, `subject_type`, `level`, `sort_order`, `status`, `created_by`, `updated_by`, `remark`, `created_at`, `updated_at`, `deleted_flag`, `account_category`) VALUES (900320,0,0,'CONTRACT_LIABILITY','合同负债','SETTLEMENT',1,3,'ENABLE',NULL,NULL,NULL,'2026-07-18 16:48:52','2026-07-18 16:48:52',0,'SETTLEMENT');
INSERT INTO `md_material_category` (`id`, `tenant_id`, `parent_id`, `category_code`, `category_name`, `level_no`, `order_num`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (202000000000000001,0,NULL,'UNCATEGORIZED','未分类',1,999999,'ENABLE',NULL,'2026-07-18 16:49:41',NULL,'2026-07-18 16:49:41',0,'V209 自动承接既有未分类材料；请由材料主数据负责人逐步归类');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200001,'WORKFLOW_BUSINESS_TYPE','CONTRACT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200002,'WORKFLOW_BUSINESS_TYPE','PROJECT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200003,'WORKFLOW_BUSINESS_TYPE','PURCHASE_ORDER','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200004,'WORKFLOW_BUSINESS_TYPE','MATERIAL_RECEIPT','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200005,'WORKFLOW_BUSINESS_TYPE','SUB_MEASURE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200006,'WORKFLOW_BUSINESS_TYPE','PAY_REQUEST','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200007,'WORKFLOW_BUSINESS_TYPE','VAR_ORDER','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200008,'WORKFLOW_BUSINESS_TYPE','PURCHASE_REQUEST','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200009,'WORKFLOW_BUSINESS_TYPE','CT_CHANGE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200010,'WORKFLOW_BUSINESS_TYPE','SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200011,'WORKFLOW_BUSINESS_TYPE','COST_TARGET','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200012,'WORKFLOW_BUSINESS_TYPE','CONTRACT_REVENUE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200013,'WORKFLOW_BUSINESS_TYPE','MATERIAL_REQUISITION','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200014,'WORKFLOW_BUSINESS_TYPE','TECH_ITEM','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200015,'WORKFLOW_BUSINESS_TYPE','PROJECT_BUDGET','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200016,'WORKFLOW_BUSINESS_TYPE','EXPENSE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200017,'WORKFLOW_BUSINESS_TYPE','OWNER_SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200018,'WORKFLOW_BUSINESS_TYPE','PRODUCTION_MEASUREMENT','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200019,'WORKFLOW_BUSINESS_TYPE','PROJECT_SCHEDULE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200020,'WORKFLOW_BUSINESS_TYPE','PROJECT_PERIOD_PLAN','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200021,'WORKFLOW_BUSINESS_TYPE','PROJECT_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200022,'WORKFLOW_BUSINESS_TYPE','COST_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200023,'WORKFLOW_BUSINESS_TYPE','TECHNICAL_SCHEME','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200024,'WORKFLOW_BUSINESS_TYPE','PROJECT_FINAL_ACCEPTANCE','workflow','1.0','ACTIVE','工作流业务类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200101,'COST_SOURCE_TYPE','CT_CONTRACT','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200102,'COST_SOURCE_TYPE','MAT_RECEIPT','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200103,'COST_SOURCE_TYPE','SUB_MEASURE','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200104,'COST_SOURCE_TYPE','VAR_ORDER','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200105,'COST_SOURCE_TYPE','CT_REVENUE','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200106,'COST_SOURCE_TYPE','MAT_REQUISITION','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200107,'COST_SOURCE_TYPE','MATERIAL_RETURN','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200108,'COST_SOURCE_TYPE','SUPPLIER_RETURN','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200109,'COST_SOURCE_TYPE','CT_CHANGE','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200110,'COST_SOURCE_TYPE','BID_COST','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200111,'COST_SOURCE_TYPE','BID_COST_TRANSFERRED','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `sys_type_registry` (`id`, `type_domain`, `type_code`, `owner_module`, `contract_version`, `status`, `description`, `created_at`, `updated_at`) VALUES (200112,'COST_SOURCE_TYPE','OVERHEAD_ALLOCATION','cost','1.0','ACTIVE','成本来源类型','2026-07-18 16:49:41','2026-07-18 16:49:41');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50001,0,'TPL-CONTRACT-APPROVAL-001','合同审批流程','CONTRACT_APPROVAL',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:34',NULL,'2026-07-18 16:48:34',0,'合同审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50002,0,'TPL-PURCHASE-ORDER-001','采购订单审批流程','PURCHASE_ORDER',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,'采购订单审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50003,0,'TPL-MATERIAL-RECEIPT-001','物料收货审批流程','MATERIAL_RECEIPT',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:40',0,'材料验收审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50004,0,'TPL-SUB-MEASURE-001','分包计量审批流程','SUB_MEASURE',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,'分包计量审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50005,0,'TPL-PAYMENT-APPROVAL-001','付款申请审批流程','PAY_REQUEST',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,'付款申请审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50006,0,'TPL-VARIATION-ORDER-001','签证变更审批流程','VAR_ORDER',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,'签证变更审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50007,0,'TPL-CONTRACT-CHANGE-001','合同变更审批流程','CT_CHANGE',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:36',NULL,'2026-07-18 16:48:36',0,'合同变更审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50008,0,'TPL-SETTLEMENT-001','结算审批流程','SETTLEMENT',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,'结算审批标准流程：项目经理 → 部门经理 → 总经理。审批通过后锁定结算单并回写合同结算金额。');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50009,0,'TPL-COST-TARGET-001','目标成本审批流程','COST_TARGET',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,'目标成本审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50010,0,'TPL-PURCHASE-REQUEST-001','采购申请审批流程','PURCHASE_REQUEST',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:40',0,'第4阶段：采购申请三级顺序审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50014,0,'TPL-MATERIAL-REQUISITION-001','领料申请审批流程','MATERIAL_REQUISITION',1,0.00,999999999.99,NULL,NULL,1,'2026-07-18 16:49:01',NULL,'2026-07-18 16:49:01',0,'领料申请审批标准流程：项目经理审批。');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50020,0,'TPL-PROJECT-BUDGET-001','项目预算审批流程','PROJECT_BUDGET',1,0.01,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,'项目资金闭环：项目经理、成本经理、总经理三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50021,0,'TPL-EXPENSE-001','费用申请审批流程','EXPENSE',1,0.01,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,'项目资金闭环：项目经理、成本经理、财务经理三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50030,0,'TPL-PROJECT-APPROVAL-001','项目立项审批流程','PROJECT_APPROVAL',1,0.00,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:38',NULL,'2026-07-18 16:49:38',0,'项目主数据审批；审批通过后仍需独立执行项目启用动作');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50031,0,'TPL-OWNER-SETTLEMENT-001','业主结算审批流程','OWNER_SETTLEMENT',1,0.01,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:14',NULL,'2026-07-18 16:49:14',0,'项目收入闭环：项目、商务、财务三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50032,0,'TPL-PRODUCTION-MEASUREMENT-001','产值计量审批流程','PRODUCTION_MEASUREMENT',1,0.01,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:15',NULL,'2026-07-18 16:49:15',0,'产值计量闭环：项目、商务、分管领导三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50033,0,'TPL-PROJECT-SCHEDULE-001','项目基线与修订计划审批','PROJECT_SCHEDULE',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,'项目计划闭环：项目经理、工程经理、分管领导三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50034,0,'TPL-PROJECT-PERIOD-PLAN-001','项目月周计划审批','PROJECT_PERIOD_PLAN',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,'项目计划闭环：项目经理、工程经理两级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50035,0,'TPL-PROJECT-CORRECTIVE-001','项目进度纠偏审批','PROJECT_CORRECTIVE_ACTION',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,'项目计划闭环：项目经理、工程经理、分管领导三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50036,0,'TPL-COST-CORRECTIVE-001','成本偏差纠偏审批','COST_CORRECTIVE_ACTION',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:21',NULL,'2026-07-18 16:49:21',0,'目标成本与动态利润闭环：项目经理、成本经理、分管领导三级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50037,0,'TPL-TECHNICAL-SCHEME-001','技术方案审批','TECHNICAL_SCHEME',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:23',NULL,'2026-07-18 16:49:23',0,'技术方案：项目经理与总工程师两级审批');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50038,0,'TPL-PROJECT-FINAL-ACCEPTANCE-001','项目竣工验收审批流程','PROJECT_FINAL_ACCEPTANCE',1,0.00,999999999999.99,NULL,NULL,1,'2026-07-18 16:49:24',NULL,'2026-07-18 16:49:24',0,'竣工收尾闭环：项目、工程、公司三级确认');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50050,0,'TPL-COST-SUBJECT-MAPPING-V2','成本科目映射版本审批','COST_SUBJECT_MAPPING',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,'第51条主线：映射版本启用门禁');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50051,0,'TPL-BID-TARGET-TRANSFER-V2','投标成本转入目标成本审批','BID_COST_TARGET_TRANSFER',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,'第51条主线：投标成本转入门禁');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50052,0,'TPL-BID-TARGET-REVERSAL-V2','投标成本转入冲销审批','BID_COST_TARGET_TRANSFER_REVERSAL',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,'第51条主线：投标成本冲销门禁');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50053,0,'TPL-FINANCE-COST-ALLOCATION-V2','项目财务费用分摊审批','FINANCE_COST_ALLOCATION',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,'第51条主线：项目财务费用分摊门禁');
INSERT INTO `wf_template` (`id`, `tenant_id`, `template_code`, `template_name`, `business_type`, `enabled`, `amount_min`, `amount_max`, `condition_rule`, `form_schema`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50054,0,'TPL-FINANCE-COST-REVERSAL-V2','项目财务费用分摊冲销审批','FINANCE_COST_ALLOCATION_REVERSAL',1,NULL,NULL,NULL,NULL,1,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,'第51条主线：项目财务费用冲销门禁');
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50011,0,50010,'NODE_PR_001','项目经理',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50012,0,50010,'NODE_PR_002','商务经理',2,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COMMERCIAL_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50013,0,50010,'NODE_PR_003','成本/采购',3,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50101,0,50001,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:34',NULL,'2026-07-18 16:48:34',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50102,0,50001,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:34',NULL,'2026-07-18 16:48:34',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50103,0,50001,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:34',NULL,'2026-07-18 16:48:34',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50201,0,50002,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50202,0,50002,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50203,0,50002,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50301,0,50003,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50302,0,50003,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50303,0,50003,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50401,0,50004,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"PROJECT_ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:49:01',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50402,0,50004,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"PROJECT_ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:49:01',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50403,0,50004,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"PROJECT_ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:49:01',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50501,0,50005,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50502,0,50005,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50503,0,50005,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50601,0,50006,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50602,0,50006,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50603,0,50006,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:35',NULL,'2026-07-18 16:48:35',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50701,0,50007,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:36',NULL,'2026-07-18 16:48:36',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50702,0,50007,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:36',NULL,'2026-07-18 16:48:36',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50703,0,50007,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:36',NULL,'2026-07-18 16:48:36',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50801,0,50008,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50802,0,50008,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50803,0,50008,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50901,0,50009,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50902,0,50009,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (50903,0,50009,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:37',NULL,'2026-07-18 16:48:37',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (51001,0,50010,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:40',NULL,'2026-07-18 16:48:40',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (51002,0,50010,'N2','部门经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:48:40',NULL,'2026-07-18 16:48:40',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (51003,0,50010,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:48:40',NULL,'2026-07-18 16:48:40',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (51401,0,50014,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"PROJECT_ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:01',NULL,'2026-07-18 16:49:01',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52001,0,50020,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52002,0,50020,'N2','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52003,0,50020,'N3','总经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52101,0,50021,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52102,0,50021,'N2','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (52103,0,50021,'N3','财务经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:05',NULL,'2026-07-18 16:49:05',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53001,0,50030,'N1','项目立项审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:38',NULL,'2026-07-18 16:49:38',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53101,0,50031,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:14',NULL,'2026-07-18 16:49:14',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53102,0,50031,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:14',NULL,'2026-07-18 16:49:14',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53103,0,50031,'N3','财务经理审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:14',NULL,'2026-07-18 16:49:14',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53201,0,50032,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:15',NULL,'2026-07-18 16:49:15',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53202,0,50032,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:15',NULL,'2026-07-18 16:49:15',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53203,0,50032,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:15',NULL,'2026-07-18 16:49:15',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53301,0,50033,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53302,0,50033,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53303,0,50033,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53401,0,50034,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53402,0,50034,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53501,0,50035,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53502,0,50035,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53503,0,50035,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:20',NULL,'2026-07-18 16:49:20',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53601,0,50036,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:21',NULL,'2026-07-18 16:49:21',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53602,0,50036,'N2','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:21',NULL,'2026-07-18 16:49:21',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53603,0,50036,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:21',NULL,'2026-07-18 16:49:21',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53701,0,50037,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:23',NULL,'2026-07-18 16:49:23',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53702,0,50037,'N2','总工程师审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:23',NULL,'2026-07-18 16:49:23',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53801,0,50038,'N1','项目经理确认',1,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:24',NULL,'2026-07-18 16:49:24',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53802,0,50038,'N2','工程管理确认',2,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:24',NULL,'2026-07-18 16:49:24',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (53803,0,50038,'N3','公司竣工确认',3,'APPROVAL','SEQUENTIAL','{\"type\": \"USER\", \"userId\": 1}',NULL,NULL,NULL,NULL,1,1,72,NULL,'2026-07-18 16:49:24',NULL,'2026-07-18 16:49:24',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55001,0,50050,'COST_MANAGER','成本经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55002,0,50051,'PROJECT_MANAGER','项目经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"PROJECT_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55003,0,50051,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55004,0,50052,'COST_MANAGER','成本经理审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55005,0,50053,'FINANCE','财务审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"FINANCE\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55006,0,50053,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55007,0,50054,'FINANCE','财务审批',1,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"FINANCE\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);
INSERT INTO `wf_template_node` (`id`, `tenant_id`, `template_id`, `node_code`, `node_name`, `node_order`, `node_type`, `approve_mode`, `approver_config`, `pass_rule_json`, `reject_rule_json`, `condition_rule`, `node_config`, `allow_transfer`, `allow_add_sign`, `timeout_hours`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_flag`, `remark`) VALUES (55008,0,50054,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL','{\"type\": \"ROLE\", \"roleCode\": \"COST_MANAGER\"}',NULL,NULL,NULL,NULL,1,1,48,NULL,'2026-07-18 16:49:47',NULL,'2026-07-18 16:49:47',0,NULL);

SET FOREIGN_KEY_CHECKS=1;

CREATE TABLE sys_bootstrap_state (
  bootstrap_key VARCHAR(64) NOT NULL,
  bootstrap_version INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  completed_at DATETIME NULL,
  PRIMARY KEY (bootstrap_key),
  CONSTRAINT chk_bootstrap_status CHECK (status IN ('PENDING', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="平台初始化状态";
INSERT INTO sys_bootstrap_state (bootstrap_key, bootstrap_version, status, completed_at) VALUES ('PLATFORM_ADMIN', 1, 'PENDING', NULL);
