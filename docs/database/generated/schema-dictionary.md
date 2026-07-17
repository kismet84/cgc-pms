# CGC-PMS 数据库结构字典（自动生成）

> 生成时间：2026-07-17 12:51:59 +08:00；来源：MySQL information_schema / 数据库 `cgc_pms_merge_v210`。请勿手工修改。

- 业务表数量：181
- 字段数量：3193
- 外键列数量：366

## account_receivable

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`settlement_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`customer_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`receivable_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`receivable_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|9|`original_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`collected_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`credited_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`outstanding_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|14|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## accounting_entry

- 表注释：会计凭证主表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||凭证ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`entry_code`|`varchar(64)`|NO|`∅`|||凭证号|
|4|`entry_date`|`date`|NO|`∅`|MUL||凭证日期|
|5|`entry_type`|`varchar(50)`|NO|`∅`|||BID_COST/MATERIAL/LABOR/OVERHEAD/REVENUE/SETTLEMENT|
|6|`source_type`|`varchar(50)`|NO|`∅`|MUL||来源类型（与cost_item.source_type对应）|
|7|`source_id`|`bigint`|NO|`∅`|||来源单据ID|
|8|`entry_status`|`varchar(50)`|NO|`DRAFT`|||DRAFT/POSTED/REVERSED|
|9|`total_debit`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|10|`total_credit`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|13|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|15|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|16|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|17|`project_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|18|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|19|`pay_application_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|20|`pay_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|21|`posted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|22|`reversed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|23|`reversed_entry_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|24|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|25|`external_sync_status`|`varchar(32)`|YES|`∅`|||未定义（需要人工确认）|
|26|`external_sync_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|27|`collection_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|28|`review_status`|`varchar(32)`|NO|`PENDING`|||PENDING/APPROVED/REJECTED|
|29|`reviewed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|30|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|31|`review_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|32|`posted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|33|`period_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|34|`adjustment_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|35|`original_entry_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|

## accounting_entry_line

- 表注释：会计凭证明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||分录行ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`entry_id`|`bigint`|NO|`∅`|MUL||凭证ID|
|4|`line_no`|`int`|NO|`1`|||行号|
|5|`direction`|`varchar(10)`|NO|`∅`|||DEBIT借方 / CREDIT贷方|
|6|`cost_subject_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`amount`|`decimal(18,2)`|NO|`0.00`|||金额|
|8|`summary`|`varchar(500)`|YES|`∅`|||摘要|
|9|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|11|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|13|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|14|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|15|`account_code`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|16|`account_name`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|

## alert_lifecycle_event

- 表注释：预警不可变生命周期事件
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||生命周期事件ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`alert_id`|`bigint`|NO|`∅`|MUL||预警ID|
|4|`event_type`|`varchar(50)`|NO|`∅`|||CREATED/READ/ACKNOWLEDGED/ESCALATED_L1/ESCALATED_L2/STATUS_CHANGED/AUTO_ARCHIVED|
|5|`from_status`|`varchar(20)`|YES|`∅`|||原状态|
|6|`to_status`|`varchar(20)`|YES|`∅`|||目标状态|
|7|`operator_id`|`bigint`|YES|`∅`|||操作人|
|8|`remark`|`varchar(500)`|YES|`∅`|||操作说明|
|9|`occurred_at`|`datetime`|NO|`∅`|||发生时间|
|10|`payload_json`|`text`|NO|`∅`|||事件快照JSON|
|11|`payload_hash`|`char(64)`|NO|`∅`|||事件快照SHA-256|

## alert_log

- 表注释：预警记录表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||预警ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|5|`alert_domain`|`varchar(50)`|YES|`∅`|MUL||预警业务分类|
|6|`alert_category`|`varchar(50)`|YES|`∅`|||细分类标签|
|7|`source_type`|`varchar(50)`|YES|`∅`|MUL||来源业务类型|
|8|`source_id`|`bigint`|YES|`∅`|||来源业务ID|
|9|`dedup_key`|`varchar(200)`|YES|`∅`|||去重键|
|10|`rule_type`|`varchar(100)`|NO|`∅`|||预警规则类型|
|11|`severity`|`varchar(20)`|NO|`MEDIUM`|||严重程度：HIGH高，MEDIUM中，LOW低|
|12|`message`|`text`|YES|`∅`|||预警消息内容|
|13|`triggered_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|触发时间|
|14|`is_read`|`tinyint`|NO|`0`|MUL||是否已读：0未读，1已读|
|15|`read_by`|`bigint`|YES|`∅`|||首次阅读人|
|16|`read_at`|`datetime`|YES|`∅`|||首次阅读时间|
|17|`acknowledged_by`|`bigint`|YES|`∅`|||接单责任人|
|18|`acknowledged_at`|`datetime`|YES|`∅`|||接单时间|
|19|`response_due_at`|`datetime`|YES|`∅`|||响应期限|
|20|`resolution_due_at`|`datetime`|YES|`∅`|||处置期限|
|21|`escalation_level`|`int`|NO|`0`|||升级级别：0未升级/1响应超时/2处置超时|
|22|`last_escalated_at`|`datetime`|YES|`∅`|||最近升级时间|
|23|`process_status`|`varchar(20)`|NO|`OPEN`|MUL||处理状态：OPEN/PROCESSED/ARCHIVED/INVALID|
|24|`processed_at`|`datetime`|YES|`∅`|||处理时间|
|25|`processed_by`|`bigint`|YES|`∅`|||处理人|
|26|`archived_at`|`datetime`|YES|`∅`|||归档时间|
|27|`archived_by`|`bigint`|YES|`∅`|||归档/失效操作人|
|28|`status_remark`|`varchar(500)`|YES|`∅`|||状态备注|
|29|`version`|`int`|NO|`0`|||乐观锁版本|
|30|`created_by`|`bigint`|YES|`∅`|||创建人|
|31|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|32|`updated_by`|`bigint`|YES|`∅`|||更新人|
|33|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|34|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|35|`remark`|`varchar(500)`|YES|`∅`|||备注|

## alert_notification_send_record

- 表注释：预警通知渠道发送记录
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||发送记录ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`alert_id`|`bigint`|NO|`∅`|||预警ID|
|4|`event_type`|`varchar(50)`|NO|`∅`|||事件类型：ALERT_CREATED / STATUS_CHANGED|
|5|`channel`|`varchar(50)`|NO|`∅`|||渠道：IN_APP / EMAIL / WECHAT|
|6|`target_user_id`|`bigint`|YES|`∅`|||目标用户ID|
|7|`biz_notification_id`|`bigint`|YES|`∅`|||站内信ID|
|8|`send_status`|`varchar(50)`|NO|`∅`|||发送状态：SENT / SKIPPED / FAILED|
|9|`fail_reason`|`varchar(500)`|YES|`∅`|||失败或跳过原因|
|10|`requested_at`|`datetime`|NO|`∅`|||请求发送时间|
|11|`completed_at`|`datetime`|YES|`∅`|||完成时间|

## alert_rule_config

- 表注释：预警规则配置表
- information_schema 估算行数：10

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||主键ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`rule_type`|`varchar(100)`|NO|`∅`|||规则类型|
|4|`alert_domain`|`varchar(50)`|NO|`∅`|||业务域|
|5|`alert_category`|`varchar(50)`|NO|`∅`|||细分类标签|
|6|`enabled`|`tinyint`|NO|`1`|||是否启用：1启用，0停用|
|7|`dedup_hours`|`int`|NO|`24`|||去重窗口小时数|
|8|`window_days`|`int`|YES|`∅`|||规则窗口天数|
|9|`threshold_ratio`|`decimal(10,4)`|YES|`∅`|||阈值比例|
|10|`severity_override`|`varchar(20)`|YES|`∅`|||严重度覆盖|
|11|`created_by`|`bigint`|YES|`∅`|||创建人|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|13|`updated_by`|`bigint`|YES|`∅`|||更新人|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|15|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|16|`remark`|`varchar(500)`|YES|`∅`|||备注|

## approval_routing_rule

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`rule_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|4|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`min_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|6|`max_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|7|`contract_type`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|8|`expense_category`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|9|`workflow_template_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|10|`priority`|`int`|NO|`100`|||未定义（需要人工确认）|
|11|`enabled_flag`|`tinyint`|NO|`1`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|17|`rule_signature`|`varchar(512)`|NO|``|||标准化匹配条件签名|
|18|`active_rule_token`|`bigint`|NO|`0`|||启用规则为0，停用规则为自身ID|

## bank_receipt

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`endpoint_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`bank_txn_no`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|5|`account_no_masked`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|6|`transaction_time`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|7|`direction`|`varchar(8)`|NO|`∅`|||未定义（需要人工确认）|
|8|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`counterparty_name`|`varchar(200)`|YES|`∅`|||未定义（需要人工确认）|
|10|`purpose_text`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|11|`match_status`|`varchar(32)`|NO|`UNMATCHED`|||未定义（需要人工确认）|
|12|`pay_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|13|`cash_journal_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`confidence`|`decimal(5,4)`|YES|`∅`|||未定义（需要人工确认）|
|15|`raw_payload_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`matched_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|18|`collection_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|19|`project_id`|`bigint`|YES|`∅`|MUL||入账所属项目ID|
|20|`contract_id`|`bigint`|YES|`∅`|MUL||入账所属合同ID|
|21|`customer_id`|`bigint`|YES|`∅`|MUL||付款客户ID|
|22|`fund_account_id`|`bigint`|YES|`∅`|MUL||收款资金账户ID|
|23|`allocation_json`|`longtext`|YES|`∅`|||银行回单对应应收分配(JSON数组)|

## bid_cost

- 表注释：招投标前期费用头表 - 金额由cost_item聚合
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||投标项目ID|
|2|`tenant_id`|`bigint`|NO|`0`|||未定义（需要人工确认）|
|3|`project_id`|`bigint`|YES|`∅`|MUL||中标后关联的项目ID，未中标时为NULL|
|4|`bid_project_name`|`varchar(200)`|NO|`∅`|||投标项目名称|
|5|`bid_status`|`varchar(50)`|NO|`BIDDING`|MUL||BIDDING投标中/WON已中标/LOST未中标|
|6|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|7|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|8|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|10|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|11|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## bid_deposit

- 表注释：投标保证金表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||保证金ID|
|2|`tenant_id`|`bigint`|NO|`0`|||未定义（需要人工确认）|
|3|`bid_cost_id`|`bigint`|NO|`∅`|MUL||关联投标项目|
|4|`deposit_type`|`varchar(50)`|NO|`∅`|||BID投标保证金/PERFORMANCE履约保证金|
|5|`deposit_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|6|`returned_amount`|`decimal(18,2)`|NO|`0.00`|||已退回金额|
|7|`deposit_status`|`varchar(50)`|NO|`PAID`|MUL||PAID已缴/RETURNED已退回/FORFEITED已没收|
|8|`paid_date`|`date`|YES|`∅`|||未定义（需要人工确认）|
|9|`returned_date`|`date`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|12|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## budget_ledger

- 表注释：不可变预算占用与消耗台账
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`budget_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`budget_line_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`business_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`entry_type`|`varchar(32)`|NO|`∅`|||RESERVE/RELEASE/CONSUME/REVERSE/ADJUST|
|9|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`reserved_balance`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`consumed_balance`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## budget_operation

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`operation_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`from_budget_line_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`to_budget_line_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`contract_allocation_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|8|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|11|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|12|`operator_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## business_matter_registry

- 表注释：合同变更与现场签证跨域事项唯一登记
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|5|`matter_key`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|6|`source_type`|`varchar(30)`|NO|`∅`|||未定义（需要人工确认）|
|7|`source_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(20)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|9|`active_token`|`tinyint`|YES|`1`|||未定义（需要人工确认）|
|10|`resolved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|11|`resolved_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`resolution_note`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|13|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## cash_forecast

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|4|`forecast_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|5|`scenario`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|6|`inflow_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`outflow_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`financing_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`source_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`source_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`confidence`|`decimal(5,4)`|NO|`1.0000`|||未定义（需要人工确认）|
|12|`status`|`varchar(32)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|13|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## cash_forecast_cycle

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`cycle_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`forecast_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`as_of_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`horizon_start`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`horizon_end`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`scenario`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`opening_balance`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`version_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|13|`previous_cycle_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`source_cutoff_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|15|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`approved_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`approved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|19|`approval_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|20|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|21|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|23|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|24|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## cash_forecast_line

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`cycle_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`forecast_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|5|`planned_inflow`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|6|`planned_outflow`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|7|`financing_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|8|`projected_balance`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`gap_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|10|`actual_inflow`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`actual_outflow`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`inflow_variance`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|13|`outflow_variance`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|14|`source_summary_json`|`json`|YES|`∅`|||未定义（需要人工确认）|
|15|`actual_refreshed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|

## cash_funding_action

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`cycle_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`line_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`action_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|7|`planned_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|10|`status`|`varchar(32)`|NO|`PROPOSED`|||未定义（需要人工确认）|
|11|`source_type`|`varchar(32)`|YES|`∅`|||未定义（需要人工确认）|
|12|`source_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`requested_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|14|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`approved_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`approved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`completed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`completed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|19|`actual_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|20|`completion_reference`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|
|21|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|22|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|23|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## cash_journal_change_log

- 表注释：资金日记账不可变变更日志
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||变更日志ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`journal_entry_id`|`bigint`|NO|`∅`|||日记账流水ID|
|4|`action`|`varchar(32)`|NO|`∅`|||REOPEN/UPDATE_AFTER_REOPEN/REARCHIVE/REVERSE|
|5|`reason`|`varchar(500)`|YES|`∅`|||变更原因|
|6|`before_snapshot`|`json`|YES|`∅`|||变更前快照|
|7|`after_snapshot`|`json`|YES|`∅`|||变更后快照|
|8|`operator_id`|`bigint`|NO|`∅`|||操作人|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## cash_journal_entry

- 表注释：资金日记账流水
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||日记账流水ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`entry_no`|`varchar(64)`|NO|`∅`|||流水号|
|4|`account_id`|`bigint`|YES|`∅`|MUL||资金账户ID|
|5|`direction`|`varchar(8)`|NO|`∅`|||IN/OUT|
|6|`amount`|`decimal(18,2)`|NO|`∅`|||金额|
|7|`business_date`|`date`|NO|`∅`|||业务日期|
|8|`counterparty_name`|`varchar(200)`|YES|`∅`|||往来单位|
|9|`summary`|`varchar(500)`|NO|`∅`|||摘要|
|10|`project_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|11|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|12|`source_type`|`varchar(32)`|NO|`∅`|||MANUAL/PAY_RECORD/REVERSAL|
|13|`source_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`status`|`varchar(32)`|NO|`∅`|||DRAFT/PENDING_ARCHIVE/ARCHIVED/REVERSED|
|15|`closure_due_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|16|`archived_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`archived_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|18|`reverse_of_entry_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|19|`reversal_entry_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|20|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|21|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|23|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|24|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|25|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|26|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|27|`pay_application_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|28|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|29|`pay_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|30|`collection_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|

## closeout_archive_transfer

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`closeout_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`transfer_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`transfer_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`recipient_organization`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`recipient_name`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|9|`archive_location`|`varchar(300)`|NO|`∅`|||未定义（需要人工确认）|
|10|`transfer_scope`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`accepted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`accepted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|15|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|19|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|20|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## closeout_defect

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`closeout_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`warranty_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`defect_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`defect_title`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`defect_description`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|9|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|10|`rectification_deadline`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|12|`rectification_content`|`varchar(2000)`|YES|`∅`|||未定义（需要人工确认）|
|13|`rectified_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`rectified_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`verified_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`verified_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`verification_comment`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|18|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|21|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|23|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|24|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## closeout_final_acceptance

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`closeout_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`acceptance_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`acceptance_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`organizer`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`participant_summary`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|9|`conclusion`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`acceptance_summary`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|13|`approved_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`approved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## closeout_section_acceptance

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`closeout_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`wbs_task_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`quality_inspection_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`acceptance_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`acceptance_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|9|`acceptance_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`conclusion`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|15|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|19|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|20|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## closeout_warranty

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`closeout_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`receivable_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`warranty_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`warranty_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`warranty_start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`warranty_end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|12|`status`|`varchar(32)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|13|`released_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`released_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## collection_allocation

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`collection_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`receivable_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`allocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|6|`allocation_type`|`varchar(32)`|NO|`COLLECTION`|||未定义（需要人工确认）|
|7|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## collection_forecast

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|5|`forecast_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|6|`scenario`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|7|`expected_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`confidence`|`decimal(5,4)`|NO|`1.0000`|||未定义（需要人工确认）|
|9|`source_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`source_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## collection_record

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`customer_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`fund_account_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`collection_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`external_txn_no`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|9|`collected_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|10|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`allocated_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`unallocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`payer_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|14|`status`|`varchar(32)`|NO|`SUCCESS`|||未定义（需要人工确认）|
|15|`reversed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`failure_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|17|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|21|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|23|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|24|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## collection_reversal

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`collection_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|5|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|6|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|7|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## collection_schedule

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`receivable_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`planned_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`planned_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`collected_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`reminder_days`|`int`|NO|`7`|||未定义（需要人工确认）|
|10|`status`|`varchar(32)`|NO|`PLANNED`|||未定义（需要人工确认）|
|11|`note`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## contract_budget_allocation

- 表注释：合同预算科目分配
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`budget_line_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`allocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`reserved_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|8|`consumed_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|12|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## contract_revenue

- 表注释：业主收入确认表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||收入确认ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||主合同ID（对甲方的总包合同）|
|5|`revenue_code`|`varchar(64)`|NO|`∅`|||收入确认单号|
|6|`revenue_date`|`date`|NO|`∅`|MUL||收入确认日期|
|7|`progress_percent`|`decimal(5,2)`|NO|`0.00`|||累计履约进度(%)|
|8|`progress_desc`|`varchar(500)`|YES|`∅`|||进度描述|
|9|`revenue_amount`|`decimal(18,2)`|NO|`0.00`|||本期确认收入（不含税）|
|10|`revenue_tax`|`decimal(18,2)`|NO|`0.00`|||销项税额|
|11|`revenue_amount_with_tax`|`decimal(18,2)`|NO|`0.00`|||含税收入|
|12|`billed_amount`|`decimal(18,2)`|NO|`0.00`|||本期向业主结算金额|
|13|`billed_tax`|`decimal(18,2)`|NO|`0.00`|||结算税额|
|14|`approval_status`|`varchar(50)`|NO|`DRAFT`|||DRAFT/PENDING/APPROVED/REJECTED|
|15|`cost_item_id`|`bigint`|YES|`∅`|||审批通过后生成的 cost_item 记录ID|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|22|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|23|`formula_version`|`varchar(64)`|NO|`REVENUE_PROGRESS_V1`|||未定义（需要人工确认）|
|24|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|25|`version`|`int`|NO|`0`|||未定义（需要人工确认）|

## cost_corrective_action

- 表注释：成本偏差纠偏措施
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`forecast_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`action_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`action_title`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|7|`root_cause`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|8|`action_plan`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|9|`expected_saving_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`actual_saving_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|11|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|12|`due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|13|`status`|`varchar(24)`|NO|`DRAFT`|||未定义（需要人工确认）|
|14|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|15|`result_description`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|16|`completed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|20|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|22|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|23|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## cost_forecast

- 表注释：项目完工成本与利润预测版本
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`cost_target_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`forecast_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`forecast_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|7|`version_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|8|`forecast_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`bid_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`target_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`responsibility_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`committed_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`actual_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|14|`estimated_remaining_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|15|`forecast_at_completion_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|16|`contract_income_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|17|`forecast_profit_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|18|`cost_variance_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|19|`profit_margin`|`decimal(9,6)`|NO|`0.000000`|||未定义（需要人工确认）|
|20|`status`|`varchar(24)`|NO|`DRAFT`|||未定义（需要人工确认）|
|21|`formula_version`|`varchar(40)`|NO|`COST_EAC_V1`|||未定义（需要人工确认）|
|22|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|23|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|24|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|25|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|26|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|27|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|28|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|29|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|30|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## cost_forecast_item

- 表注释：完工成本预测科目快照
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`forecast_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`cost_subject_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`bid_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`target_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`responsibility_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`committed_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`actual_cost_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`estimated_remaining_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`forecast_at_completion_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`cost_variance_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|14|`responsible_user_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`responsibility_unit`|`varchar(200)`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## cost_item

- 表注释：成本明细表
- information_schema 估算行数：1

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||成本ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`org_id`|`bigint`|YES|`∅`|||所属组织ID|
|4|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|5|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|6|`partner_id`|`bigint`|YES|`∅`|||合作方ID|
|7|`cost_subject_id`|`bigint`|YES|`∅`|MUL||成本科目ID|
|8|`cost_type`|`varchar(50)`|NO|`∅`|||材料/分包/机械/人工/签证/管理费等|
|9|`amount`|`decimal(18,2)`|NO|`0.00`|||成本金额|
|10|`tax_amount`|`decimal(18,2)`|NO|`0.00`|||税额|
|11|`amount_without_tax`|`decimal(18,2)`|NO|`0.00`|||不含税金额|
|12|`source_type`|`varchar(50)`|NO|`∅`|MUL||来源类型，如 MAT_RECEIPT/SUB_MEASURE/VAR_ORDER|
|13|`source_id`|`bigint`|NO|`∅`|||来源单据主表ID|
|14|`source_item_id`|`bigint`|NO|`0`|||来源单据明细ID，不按明细拆分时为0|
|15|`cost_date`|`date`|NO|`∅`|MUL||成本发生日期|
|16|`cost_status`|`varchar(50)`|NO|`CONFIRMED`|||暂估/已确认/已结算/已冲销|
|17|`generated_flag`|`tinyint`|NO|`1`|||是否系统生成：0否，1是|
|18|`created_by`|`bigint`|YES|`∅`|||创建人|
|19|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|20|`updated_by`|`bigint`|YES|`∅`|||更新人|
|21|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|22|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|23|`remark`|`varchar(500)`|YES|`∅`|||备注|
|24|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动成本事实唯一键辅助列：活动行=0，删除行=id|

## cost_subject

- 表注释：成本科目表
- information_schema 估算行数：114

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||成本科目ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`parent_id`|`bigint`|NO|`0`|MUL||父科目ID，0表示根节点|
|4|`subject_code`|`varchar(64)`|NO|`∅`|||科目编码|
|5|`subject_name`|`varchar(200)`|NO|`∅`|||科目名称|
|6|`subject_type`|`varchar(50)`|YES|`∅`|MUL||科目类型：材料/分包/机械/人工/管理费等|
|7|`level`|`int`|NO|`1`|||科目层级|
|8|`sort_order`|`int`|NO|`0`|||排序|
|9|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|10|`created_by`|`bigint`|YES|`∅`|||创建人|
|11|`updated_by`|`bigint`|YES|`∅`|||更新人|
|12|`remark`|`varchar(500)`|YES|`∅`|||备注|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|15|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|16|`account_category`|`varchar(20)`|NO|`COST`|||科目大类：COST成本，REVENUE收入，SETTLEMENT结算，RECEIVABLE应收|
|17|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## cost_summary

- 表注释：动态成本汇总表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||成本汇总ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`summary_date`|`date`|NO|`∅`|MUL||汇总日期|
|5|`cost_subject_id`|`bigint`|YES|`∅`|||成本科目ID|
|6|`cost_target_id`|`bigint`|YES|`∅`|||关联的目标成本版本ID，关联cost_target.id|
|7|`target_cost`|`decimal(18,2)`|NO|`0.00`|||目标成本|
|8|`contract_locked_cost`|`decimal(18,2)`|NO|`0.00`|||合同锁定成本|
|9|`actual_cost`|`decimal(18,2)`|NO|`0.00`|||实际成本|
|10|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||已付金额|
|11|`estimated_remaining_cost`|`decimal(18,2)`|NO|`0.00`|||预计剩余成本|
|12|`dynamic_cost`|`decimal(18,2)`|NO|`0.00`|||动态成本|
|13|`contract_income`|`decimal(18,2)`|NO|`0.00`|||合同收入|
|14|`confirmed_revenue`|`decimal(18,2)`|NO|`0.00`|||累计已确认收入（按履约进度，来源contract_revenue）|
|15|`expected_profit`|`decimal(18,2)`|NO|`0.00`|||预期利润|
|16|`cost_deviation`|`decimal(18,2)`|NO|`0.00`|||成本偏差|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|
|23|`cost_forecast_id`|`bigint`|YES|`∅`|MUL||采用的完工预测版本|
|24|`responsibility_cost`|`decimal(18,2)`|NO|`0.00`|||责任预算|
|25|`forecast_at_completion_cost`|`decimal(18,2)`|NO|`0.00`|||完工预测成本|
|26|`forecast_profit`|`decimal(18,2)`|NO|`0.00`|||预测利润|
|27|`profit_margin`|`decimal(9,6)`|NO|`0.000000`|||预测利润率|

## cost_target

- 表注释：目标成本表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||目标成本ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`version_no`|`varchar(50)`|NO|`∅`|||版本号|
|5|`version_name`|`varchar(200)`|YES|`∅`|||版本名称|
|6|`total_target_amount`|`decimal(18,2)`|NO|`0.00`|||目标成本总额|
|7|`is_active`|`tinyint`|NO|`0`|||是否生效版本：0否，1是。同一项目仅允许一个生效版本|
|8|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回|
|9|`effective_date`|`date`|YES|`∅`|||生效日期|
|10|`status`|`varchar(50)`|NO|`DRAFT`|||业务状态：DRAFT草稿，APPROVING审批中，ACTIVE已生效，CANCELLED已作废|
|11|`created_by`|`bigint`|YES|`∅`|||创建人|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|13|`updated_by`|`bigint`|YES|`∅`|||更新人|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|15|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|16|`remark`|`text`|YES|`∅`|||备注|
|17|`total_bid_cost_amount`|`decimal(18,2)`|NO|`0.00`|||投标成本基准总额|
|18|`total_responsibility_amount`|`decimal(18,2)`|NO|`0.00`|||责任预算总额|
|19|`approval_instance_id`|`bigint`|YES|`∅`|MUL||审批实例|
|20|`version`|`int`|NO|`0`|||未定义（需要人工确认）|

## cost_target_item

- 表注释：目标成本明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||目标成本明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`target_id`|`bigint`|NO|`∅`|MUL||目标成本ID，关联cost_target.id|
|4|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|5|`cost_subject_id`|`bigint`|NO|`∅`|MUL||成本科目ID，关联cost_subject.id|
|6|`target_amount`|`decimal(18,2)`|NO|`0.00`|||目标金额|
|7|`created_by`|`bigint`|YES|`∅`|||创建人|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|9|`updated_by`|`bigint`|YES|`∅`|||更新人|
|10|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|11|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|12|`remark`|`varchar(500)`|YES|`∅`|||备注|
|13|`bid_cost_amount`|`decimal(18,2)`|NO|`0.00`|||投标成本科目快照|
|14|`responsibility_amount`|`decimal(18,2)`|NO|`0.00`|||责任预算科目金额|
|15|`responsible_user_id`|`bigint`|YES|`∅`|||责任人|
|16|`responsibility_unit`|`varchar(200)`|YES|`∅`|||责任单位/部门|
|17|`sort_order`|`int`|NO|`0`|||未定义（需要人工确认）|

## ct_contract

- 表注释：合同主表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||合同ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`org_id`|`bigint`|YES|`∅`|||所属组织ID|
|4|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|5|`contract_code`|`varchar(64)`|NO|`∅`|||合同编号|
|6|`contract_name`|`varchar(200)`|NO|`∅`|||合同名称|
|7|`contract_type`|`varchar(50)`|NO|`∅`|MUL||总包/分包/采购/租赁/服务等|
|8|`party_a_id`|`bigint`|YES|`∅`|||甲方合作方ID|
|9|`party_b_id`|`bigint`|YES|`∅`|||乙方合作方ID|
|10|`contract_amount`|`decimal(18,2)`|NO|`0.00`|||原合同金额|
|11|`current_amount`|`decimal(18,2)`|NO|`0.00`|||当前合同金额=原合同金额+已生效变更|
|12|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||累计已付金额|
|13|`tax_rate`|`decimal(6,2)`|NO|`0.00`|||税率|
|14|`tax_amount`|`decimal(18,2)`|NO|`0.00`|||税额|
|15|`amount_without_tax`|`decimal(18,2)`|NO|`0.00`|||不含税金额|
|16|`signed_date`|`date`|YES|`∅`|||签订日期|
|17|`start_date`|`date`|YES|`∅`|||合同开始日期|
|18|`end_date`|`date`|YES|`∅`|||合同结束日期|
|19|`payment_method`|`varchar(100)`|YES|`∅`|||付款方式|
|20|`settlement_method`|`varchar(100)`|YES|`∅`|||结算方式|
|21|`contract_status`|`varchar(50)`|NO|`DRAFT`|MUL||合同业务状态|
|22|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|23|`created_by`|`bigint`|YES|`∅`|||创建人|
|24|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|创建时间|
|25|`updated_by`|`bigint`|YES|`∅`|||更新人|
|26|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|27|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|28|`remark`|`varchar(500)`|YES|`∅`|||备注|
|29|`cost_generated_flag`|`tinyint`|NO|`0`|||成本生成标识：0未生成，1已生成|
|30|`settlement_amount`|`decimal(18,2)`|YES|`∅`|||结算金额（结算审批通过后回写）|
|31|`version`|`int`|NO|`0`|||乐观锁版本号|
|32|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## ct_contract_change

- 表注释：合同变更表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||合同变更ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||合同ID|
|5|`change_code`|`varchar(64)`|NO|`∅`|||变更编号，自动生成|
|6|`change_name`|`varchar(200)`|NO|`∅`|||变更名称|
|7|`change_type`|`varchar(50)`|NO|`∅`|||变更类型：AMOUNT金额变更，DURATION工期变更，CLAUSE条款变更|
|8|`before_amount`|`decimal(18,2)`|NO|`0.00`|||变更前合同金额|
|9|`change_amount`|`decimal(18,2)`|NO|`0.00`|||变更金额（正数为增，负数为减）|
|10|`after_amount`|`decimal(18,2)`|NO|`0.00`|||变更后合同金额|
|11|`reason`|`text`|YES|`∅`|||变更原因|
|12|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回|
|13|`effective_flag`|`tinyint`|NO|`0`|||生效标识：0未生效，1已生效|
|14|`cost_generated_flag`|`tinyint`|NO|`0`|||成本生成标识：0未生成，1已生成|
|15|`created_by`|`bigint`|YES|`∅`|||创建人|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|17|`updated_by`|`bigint`|YES|`∅`|||更新人|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|19|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|20|`remark`|`varchar(500)`|YES|`∅`|||备注|
|21|`business_matter_key`|`varchar(100)`|YES|`∅`|||跨域业务事项唯一键|
|22|`source_var_order_id`|`bigint`|YES|`∅`|MUL||来源变更签证|
|23|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## ct_contract_item

- 表注释：合同明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||合同明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`contract_id`|`bigint`|NO|`∅`|MUL||合同ID|
|4|`item_code`|`varchar(64)`|YES|`∅`|MUL||明细编号|
|5|`item_name`|`varchar(200)`|NO|`∅`|||明细名称/清单项名称|
|6|`item_spec`|`varchar(300)`|YES|`∅`|||规格型号|
|7|`unit`|`varchar(50)`|YES|`∅`|||计量单位|
|8|`quantity`|`decimal(18,4)`|NO|`0.0000`|||数量|
|9|`unit_price`|`decimal(18,4)`|NO|`0.0000`|||单价|
|10|`amount`|`decimal(18,2)`|NO|`0.00`|||金额（含税）|
|11|`tax_rate`|`decimal(6,2)`|NO|`0.00`|||税率|
|12|`tax_amount`|`decimal(18,2)`|NO|`0.00`|||税额|
|13|`amount_without_tax`|`decimal(18,2)`|NO|`0.00`|||不含税金额|
|14|`sort_order`|`int`|NO|`0`|||排序|
|15|`created_by`|`bigint`|YES|`∅`|||创建人|
|16|`updated_by`|`bigint`|YES|`∅`|||更新人|
|17|`remark`|`varchar(500)`|YES|`∅`|||备注|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|

## ct_contract_payment_term

- 表注释：合同付款条款表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||付款条款ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`contract_id`|`bigint`|NO|`∅`|MUL||合同ID|
|4|`term_name`|`varchar(200)`|NO|`∅`|||条款名称/付款节点名称|
|5|`payment_ratio`|`decimal(6,2)`|NO|`0.00`|||付款比例(%)|
|6|`payment_amount`|`decimal(18,2)`|NO|`0.00`|||付款金额|
|7|`payment_condition`|`varchar(500)`|YES|`∅`|||付款条件|
|8|`planned_date`|`date`|YES|`∅`|||计划付款日期|
|9|`actual_date`|`date`|YES|`∅`|||实际付款日期|
|10|`term_status`|`varchar(50)`|NO|`PENDING`|MUL||条款状态：PENDING待付，PARTIAL部分付款，PAID已付清|
|11|`sort_order`|`int`|NO|`0`|||排序|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`updated_by`|`bigint`|YES|`∅`|||更新人|
|14|`remark`|`varchar(500)`|YES|`∅`|||备注|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|17|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|

## customer_credit_profile

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`customer_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`credit_limit`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|5|`risk_level`|`varchar(16)`|NO|`NORMAL`|||未定义（需要人工确认）|
|6|`dso_days`|`int`|NO|`0`|||未定义（需要人工确认）|
|7|`overdue_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|8|`score`|`decimal(8,2)`|NO|`100.00`|||未定义（需要人工确认）|
|9|`formula_version`|`varchar(64)`|NO|`CUSTOMER_CREDIT_V1`|||未定义（需要人工确认）|
|10|`refreshed_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|

## dashboard_finance_snapshot

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`snapshot_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|5|`formula_version`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`contract_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`approved_unpaid_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`paid_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`budget_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`budget_reserved`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`budget_consumed`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`cash_inflow`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`cash_outflow`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|14|`actual_cost`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|15|`profit_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|16|`refreshed_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|17|`refresh_mode`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|

## expense_application

- 表注释：费用申请
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`cost_subject_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`budget_line_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`payee_partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|8|`expense_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|9|`expense_category`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|10|`expense_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`converted_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|13|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|14|`description`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|15|`approval_status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|16|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|19|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|21|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|22|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## finance_account_reconciliation

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`period_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`account_type`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|5|`expected_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|6|`ledger_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`difference_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|9|`detail_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|10|`reconciled_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`reconciled_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_alert

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`alert_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|4|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`severity`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|7|`due_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|9|`message`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|10|`alert_key`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|11|`alert_log_id`|`bigint`|YES|`∅`|UNI||驾驶舱权威预警事实ID|
|12|`handled_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`handled_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`handle_note`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_audit_event

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`event_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|4|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`project_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|7|`operator_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`event_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`archive_bucket`|`varchar(32)`|NO|`HOT`|||未定义（需要人工确认）|
|10|`payload_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|11|`payload_hash`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|

## finance_bank_reconciliation

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`period_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`bank_receipt_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`direction`|`varchar(8)`|NO|`∅`|||未定义（需要人工确认）|
|6|`business_type`|`varchar(32)`|YES|`∅`|||未定义（需要人工确认）|
|7|`business_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`cash_journal_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|9|`bank_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`business_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`difference_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`status`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|13|`match_method`|`varchar(16)`|NO|`AUTO`|||未定义（需要人工确认）|
|14|`resolved_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`resolved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`resolution_note`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_import_batch

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`import_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`file_name`|`varchar(255)`|NO|`∅`|||未定义（需要人工确认）|
|6|`file_hash`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`total_rows`|`int`|NO|`0`|||未定义（需要人工确认）|
|9|`valid_rows`|`int`|NO|`0`|||未定义（需要人工确认）|
|10|`invalid_rows`|`int`|NO|`0`|||未定义（需要人工确认）|
|11|`diff_summary_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`applied_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|

## finance_import_row

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`batch_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`row_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_key`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|
|6|`input_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|7|`diff_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|8|`validation_status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|9|`validation_message`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|

## finance_integration_endpoint

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`endpoint_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|4|`endpoint_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`endpoint_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`base_url`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|7|`credential_ref`|`varchar(200)`|YES|`∅`|||未定义（需要人工确认）|
|8|`callback_secret_hash`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|9|`enabled_flag`|`tinyint`|NO|`1`|||未定义（需要人工确认）|
|10|`config_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|11|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## finance_integration_message

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`endpoint_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`direction`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|5|`message_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`business_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|10|`payload_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|11|`response_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|12|`retry_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`next_retry_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`processed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`error_message`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_period

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`period_code`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|4|`fiscal_year`|`int`|NO|`∅`|||未定义（需要人工确认）|
|5|`fiscal_month`|`int`|NO|`∅`|||未定义（需要人工确认）|
|6|`start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|9|`last_check_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`issue_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|11|`closed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`closed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`close_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|14|`reopened_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`reopened_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`reopen_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|17|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|20|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## finance_period_check

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`period_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`check_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`check_status`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|6|`issue_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|7|`detail_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|8|`checked_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`checked_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_reconciliation_issue

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`run_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`dimension_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|6|`issue_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`expected_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|8|`actual_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|10|`detail`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## finance_reconciliation_run

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`business_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|4|`run_type`|`varchar(32)`|NO|`DAILY`|||未定义（需要人工确认）|
|5|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|6|`issue_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|7|`summary_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|8|`started_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`finished_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|

## fund_account

- 表注释：企业资金账户
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||资金账户ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`account_code`|`varchar(64)`|NO|`∅`|||租户内账户编码|
|4|`account_name`|`varchar(128)`|NO|`∅`|||账户名称|
|5|`account_type`|`varchar(16)`|NO|`∅`|||CASH/BANK|
|6|`bank_name`|`varchar(128)`|YES|`∅`|||开户行|
|7|`bank_account_no`|`varchar(128)`|YES|`∅`|||银行账号|
|8|`opening_date`|`date`|NO|`∅`|||期初日期|
|9|`opening_balance`|`decimal(18,2)`|NO|`0.00`|||期初余额|
|10|`enabled_flag`|`tinyint`|NO|`1`|||1启用，0停用|
|11|`version`|`int`|NO|`0`|||乐观锁版本|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|16|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|17|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## fund_pool

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`pool_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|4|`pool_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|5|`currency_code`|`varchar(8)`|NO|`CNY`|||未定义（需要人工确认）|
|6|`status`|`varchar(32)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|7|`control_mode`|`varchar(32)`|NO|`QUOTA`|||未定义（需要人工确认）|
|8|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## fund_pool_member

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`pool_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`company_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|5|`fund_account_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`quota_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`occupied_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|9|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## fund_pool_transaction

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`pool_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`from_member_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|5|`to_member_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`transaction_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|7|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|9|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|10|`external_txn_no`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|
|11|`occurred_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## invoice_ocr_review

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`invoice_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`raw_result_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|5|`confidence`|`decimal(5,4)`|NO|`∅`|||未定义（需要人工确认）|
|6|`comparison_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|7|`review_status`|`varchar(32)`|NO|`PENDING`|||未定义（需要人工确认）|
|8|`reviewer_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`review_note`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## invoice_payment_allocation

- 表注释：发票付款金额分配
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`invoice_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`pay_record_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`pay_application_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`allocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## mat_material_return

- 表注释：材料退料确认单
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|5|`warehouse_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`requisition_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|7|`return_code`|`varchar(50)`|NO|`∅`|||未定义（需要人工确认）|
|8|`return_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(30)`|NO|`∅`|||未定义（需要人工确认）|
|10|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|11|`idempotency_key`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|12|`total_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|13|`confirmed_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|14|`confirmed_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|15|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|19|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|20|`remark`|`text`|YES|`∅`|||未定义（需要人工确认）|
|21|`reversed_by`|`bigint`|YES|`∅`|||冲销人|
|22|`reversed_at`|`datetime`|YES|`∅`|||冲销时间|
|23|`reversal_reason`|`varchar(500)`|YES|`∅`|||冲销原因|
|24|`version`|`int`|NO|`0`|||乐观锁版本|

## mat_material_return_item

- 表注释：材料退料明细
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`return_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|4|`requisition_item_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|5|`original_stock_txn_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`original_cost_item_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|7|`material_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|9|`unit_cost`|`decimal(18,6)`|NO|`∅`|||未定义（需要人工确认）|
|10|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|13|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|15|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|16|`remark`|`text`|YES|`∅`|||未定义（需要人工确认）|

## mat_purchase_order

- 表注释：采购订单表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||采购订单ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`request_id`|`bigint`|YES|`∅`|||采购申请ID|
|5|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|6|`partner_id`|`bigint`|YES|`∅`|MUL||供应商ID|
|7|`order_code`|`varchar(64)`|NO|`∅`|||订单编号|
|8|`order_type`|`varchar(50)`|YES|`∅`|||订单类型|
|9|`order_date`|`date`|YES|`∅`|||订单日期|
|10|`delivery_date`|`date`|YES|`∅`|||预计交付日期|
|11|`delivery_terms`|`varchar(500)`|YES|`∅`|||交付条件|
|12|`exception_purchase_flag`|`tinyint`|NO|`0`|||无申请来源的例外采购标识|
|13|`exception_reason`|`varchar(500)`|YES|`∅`|||例外采购原因|
|14|`total_amount`|`decimal(18,2)`|YES|`∅`|||订单总金额|
|15|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|16|`order_status`|`varchar(50)`|NO|`DRAFT`|||订单状态|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|
|23|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## mat_purchase_order_item

- 表注释：采购订单明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||订单明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`order_id`|`bigint`|NO|`∅`|MUL||采购订单ID|
|4|`request_item_id`|`bigint`|YES|`∅`|MUL||来源采购申请明细ID；手工订单允许为空|
|5|`budget_line_id`|`bigint`|YES|`∅`|||项目预算科目ID|
|6|`project_id`|`bigint`|YES|`∅`|||项目ID|
|7|`material_id`|`bigint`|YES|`∅`|MUL||材料ID|
|8|`material_name`|`varchar(200)`|YES|`∅`|||材料名称|
|9|`specification`|`varchar(200)`|YES|`∅`|||规格型号|
|10|`unit`|`varchar(20)`|YES|`∅`|||计量单位|
|11|`quantity`|`decimal(18,4)`|YES|`∅`|||采购数量|
|12|`unit_price`|`decimal(18,4)`|YES|`∅`|||单价|
|13|`tax_rate`|`decimal(8,4)`|NO|`0.0000`|||税率百分比|
|14|`amount`|`decimal(18,2)`|YES|`∅`|||金额|
|15|`tax_amount`|`decimal(18,2)`|NO|`0.00`|||税额|
|16|`amount_without_tax`|`decimal(18,2)`|NO|`0.00`|||不含税金额|
|17|`received_quantity`|`decimal(18,4)`|NO|`0.0000`|||已收货数量|
|18|`version`|`int`|NO|`0`|||乐观锁版本号|
|19|`created_by`|`bigint`|YES|`∅`|||创建人|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|21|`updated_by`|`bigint`|YES|`∅`|||更新人|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|23|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|24|`remark`|`varchar(500)`|YES|`∅`|||备注|
|25|`wbs_task_id`|`bigint`|YES|`∅`|||来源WBS任务ID|

## mat_purchase_request

- 表注释：采购申请表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||采购申请ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|||关联采购合同|
|5|`purpose`|`varchar(500)`|YES|`∅`|||采购用途/施工部位说明|
|6|`request_code`|`varchar(50)`|NO|`∅`|||申请编号，PR-yyyyMMdd-XXX|
|7|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回，WITHDRAWN已撤回|
|8|`status`|`varchar(50)`|NO|`DRAFT`|||业务状态：DRAFT草稿，APPROVED已通过，CONVERTED已转采购订单|
|9|`created_by`|`bigint`|YES|`∅`|||创建人|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_by`|`bigint`|YES|`∅`|||更新人|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|13|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|14|`remark`|`text`|YES|`∅`|||备注|

## mat_purchase_request_item

- 表注释：采购申请明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`request_id`|`bigint`|NO|`∅`|MUL||采购申请ID|
|4|`material_id`|`bigint`|NO|`∅`|MUL||物料ID|
|5|`budget_line_id`|`bigint`|YES|`∅`|||项目预算科目ID|
|6|`sub_task_id`|`bigint`|YES|`∅`|MUL||分包任务ID|
|7|`quantity`|`decimal(18,4)`|NO|`∅`|||申请数量|
|8|`estimated_unit_price`|`decimal(18,4)`|YES|`∅`|||申请估算单价|
|9|`estimated_amount`|`decimal(18,2)`|YES|`∅`|||申请估算金额|
|10|`unit`|`varchar(20)`|YES|`∅`|||单位|
|11|`planned_date`|`date`|YES|`∅`|||期望到货日期|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`text`|YES|`∅`|||备注|
|18|`wbs_task_id`|`bigint`|YES|`∅`|||WBS任务ID|

## mat_quality_disposition

- 表注释：材料验收不合格处置事实
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||质量处置ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|||项目ID|
|4|`receipt_id`|`bigint`|NO|`∅`|||验收单ID|
|5|`receipt_item_id`|`bigint`|NO|`∅`|||验收明细ID|
|6|`rejected_quantity`|`decimal(18,4)`|NO|`∅`|||不合格数量|
|7|`disposition_action`|`varchar(32)`|NO|`RETURN_TO_SUPPLIER`|||处置动作|
|8|`status`|`varchar(32)`|NO|`OPEN`|||OPEN/RESOLVED/CANCELLED|
|9|`resolved_quantity`|`decimal(18,4)`|NO|`0.0000`|||已处置数量|
|10|`resolved_at`|`datetime`|YES|`∅`|||完成时间|
|11|`version`|`int`|NO|`0`|||乐观锁版本|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`varchar(500)`|YES|`∅`|||处置说明|

## mat_receipt

- 表注释：材料验收表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||验收ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`order_id`|`bigint`|YES|`∅`|MUL||采购订单ID|
|5|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|6|`partner_id`|`bigint`|YES|`∅`|||供应商ID|
|7|`receipt_code`|`varchar(64)`|NO|`∅`|||验收单号|
|8|`receipt_date`|`date`|YES|`∅`|||验收日期|
|9|`warehouse_id`|`bigint`|YES|`∅`|||仓库ID|
|10|`receiver_id`|`bigint`|YES|`∅`|||验收人ID|
|11|`receipt_mode`|`varchar(30)`|NO|`INVENTORY`|||INVENTORY入库；DIRECT_CONSUMPTION直耗|
|12|`quality_status`|`varchar(50)`|YES|`∅`|||质量状态|
|13|`total_amount`|`decimal(18,2)`|YES|`∅`|||验收总金额|
|14|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|15|`cost_generated_flag`|`tinyint`|NO|`0`|||成本生成标识：0未生成，1已生成|
|16|`created_by`|`bigint`|YES|`∅`|||创建人|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|18|`updated_by`|`bigint`|YES|`∅`|||更新人|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|21|`remark`|`varchar(500)`|YES|`∅`|||备注|
|22|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## mat_receipt_item

- 表注释：材料验收明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||验收明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`receipt_id`|`bigint`|NO|`∅`|MUL||验收单ID|
|4|`order_item_id`|`bigint`|YES|`∅`|MUL||订单明细ID|
|5|`material_id`|`bigint`|YES|`∅`|MUL||材料ID|
|6|`actual_quantity`|`decimal(18,4)`|YES|`∅`|||实际到货数量|
|7|`qualified_quantity`|`decimal(18,4)`|YES|`∅`|||合格数量|
|8|`unqualified_quantity`|`decimal(18,4)`|NO|`0.0000`|||不合格数量|
|9|`unit_price`|`decimal(18,4)`|YES|`∅`|||单价|
|10|`amount`|`decimal(18,2)`|YES|`∅`|||金额|
|11|`use_location`|`varchar(200)`|YES|`∅`|||使用部位|
|12|`batch_no`|`varchar(100)`|YES|`∅`|||批号|
|13|`disposition_type`|`varchar(30)`|YES|`∅`|||RETURN/REPLACE/CONCESSION|
|14|`disposition_status`|`varchar(30)`|YES|`∅`|||PENDING/COMPLETED|
|15|`disposition_reason`|`varchar(500)`|YES|`∅`|||不合格处置原因|
|16|`created_by`|`bigint`|YES|`∅`|||创建人|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|18|`updated_by`|`bigint`|YES|`∅`|||更新人|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|21|`remark`|`varchar(500)`|YES|`∅`|||备注|
|22|`wbs_task_id`|`bigint`|YES|`∅`|||来源WBS任务ID|
|23|`budget_line_id`|`bigint`|YES|`∅`|||来源项目预算行ID|

## mat_requisition

- 表注释：领料申请主表
- information_schema 估算行数：1

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI|auto_increment|领料申请ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||关联合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|||合作方/供应商ID|
|6|`requisition_code`|`varchar(50)`|NO|`∅`|||申请编号 REQ-yyyyMMdd-XXX|
|7|`requisition_date`|`date`|NO|`∅`|||申请日期|
|8|`warehouse_id`|`bigint`|NO|`∅`|MUL||仓库ID|
|9|`requisitioner_id`|`bigint`|YES|`∅`|||领料人ID|
|10|`approval_status`|`varchar(50)`|NO|`DRAFT`|MUL||审批状态：DRAFT/APPROVING/APPROVED/REJECTED|
|11|`total_amount`|`decimal(18,4)`|YES|`0.0000`|||总金额（明细金额合计）|
|12|`stock_out_flag`|`tinyint`|NO|`0`|||出库标记：0未出库，1已出库|
|13|`stock_out_by`|`bigint`|YES|`∅`|||实际出库操作人|
|14|`stock_out_at`|`datetime`|YES|`∅`|||实际出库时间|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|17|`created_by`|`bigint`|NO|`0`|||创建人|
|18|`updated_by`|`bigint`|NO|`0`|||更新人|
|19|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|20|`remark`|`varchar(500)`|YES|`∅`|||备注|

## mat_requisition_item

- 表注释：领料申请明细表
- information_schema 估算行数：1

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI|auto_increment|领料申请明细ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`requisition_id`|`bigint`|NO|`∅`|MUL||领料申请ID|
|4|`material_id`|`bigint`|NO|`∅`|MUL||物料ID|
|5|`quantity`|`decimal(18,4)`|NO|`0.0000`|||申请数量|
|6|`unit_price`|`decimal(18,4)`|YES|`0.0000`|||参考单价|
|7|`amount`|`decimal(18,4)`|YES|`0.0000`|||金额（quantity × unit_price）|
|8|`use_location`|`varchar(200)`|YES|`∅`|||使用部位/用途|
|9|`batch_no`|`varchar(100)`|YES|`∅`|||批次号|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|12|`created_by`|`bigint`|NO|`0`|||创建人|
|13|`updated_by`|`bigint`|NO|`0`|||更新人|
|14|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|15|`remark`|`varchar(500)`|YES|`∅`|||备注|

## mat_stock

- 表注释：库存余额表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||库存ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`warehouse_id`|`bigint`|NO|`∅`|MUL||仓库ID|
|4|`material_id`|`bigint`|NO|`∅`|MUL||物料ID，关联md_material.id|
|5|`available_qty`|`decimal(18,4)`|NO|`0.0000`|||可用数量|
|6|`inventory_value`|`decimal(18,2)`|NO|`0.00`|||库存价值|
|7|`average_unit_cost`|`decimal(18,6)`|NO|`0.000000`|||移动加权平均单价|
|8|`safety_stock_qty`|`decimal(18,4)`|NO|`10.0000`|||安全库存阈值|
|9|`replenishment_target_qty`|`decimal(18,4)`|YES|`∅`|||人工补货目标量；NULL 回退安全库存阈值|
|10|`replenishment_lead_days`|`int`|YES|`∅`|||人工补货提前期（自然日）；NULL 不预填计划日期|
|11|`version`|`int`|NO|`0`|||乐观锁版本号|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`text`|YES|`∅`|||备注|
|18|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|库存活动行唯一键辅助列：活动行=0，删除行=id|

## mat_stock_txn

- 表注释：库存流水表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||流水ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`warehouse_id`|`bigint`|NO|`∅`|MUL||仓库ID|
|4|`material_id`|`bigint`|NO|`∅`|||物料ID|
|5|`txn_type`|`varchar(20)`|NO|`∅`|||交易类型：IN入库，OUT出库，ADJUST调整|
|6|`quantity`|`decimal(18,4)`|NO|`∅`|||交易数量（入库为正，出库为负或正数由服务层控制）|
|7|`available_after`|`decimal(18,4)`|NO|`0.0000`|||交易后可用量|
|8|`unit_cost`|`decimal(18,6)`|NO|`0.000000`|||本次移动单位成本|
|9|`amount`|`decimal(18,2)`|NO|`0.00`|||本次移动库存价值|
|10|`source_type`|`varchar(50)`|YES|`∅`|MUL||来源业务类型|
|11|`source_id`|`bigint`|YES|`∅`|||来源业务ID|
|12|`source_line_id`|`bigint`|YES|`∅`|||来源业务明细ID|
|13|`created_by`|`bigint`|YES|`∅`|||创建人|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|15|`updated_by`|`bigint`|YES|`∅`|||更新人|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|17|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|18|`remark`|`text`|YES|`∅`|||备注|

## mat_warehouse

- 表注释：仓库表
- information_schema 估算行数：3

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||仓库ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||所属项目ID|
|4|`warehouse_code`|`varchar(50)`|NO|`∅`|||仓库编码|
|5|`warehouse_name`|`varchar(200)`|NO|`∅`|||仓库名称|
|6|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|7|`created_by`|`bigint`|YES|`∅`|||创建人|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|9|`updated_by`|`bigint`|YES|`∅`|||更新人|
|10|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|11|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|12|`remark`|`text`|YES|`∅`|||备注|

## md_material

- 表注释：材料字典表
- information_schema 估算行数：17

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||材料ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`material_code`|`varchar(64)`|NO|`∅`|||材料编码|
|4|`material_name`|`varchar(200)`|NO|`∅`|||材料名称|
|5|`category_id`|`bigint`|YES|`∅`|MUL||材料类别ID|
|6|`specification`|`varchar(200)`|YES|`∅`|||规格型号|
|7|`unit`|`varchar(20)`|YES|`∅`|||计量单位|
|8|`brand`|`varchar(100)`|YES|`∅`|||品牌|
|9|`default_tax_rate`|`decimal(6,2)`|YES|`∅`|||默认税率|
|10|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|11|`created_by`|`bigint`|YES|`∅`|||创建人|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|13|`updated_by`|`bigint`|YES|`∅`|||更新人|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|15|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|16|`remark`|`varchar(500)`|YES|`∅`|||备注|
|17|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## md_material_category

- 表注释：材料分类主数据
- information_schema 估算行数：1

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||材料分类ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`parent_id`|`bigint`|YES|`∅`|||上级分类ID|
|4|`category_code`|`varchar(64)`|NO|`∅`|||分类编码，租户内永久唯一|
|5|`category_name`|`varchar(128)`|NO|`∅`|||分类名称|
|6|`level_no`|`int`|NO|`1`|||树层级，从1开始|
|7|`order_num`|`int`|NO|`0`|||同级排序号|
|8|`status`|`varchar(32)`|NO|`ENABLE`|||状态：ENABLE/DISABLE|
|9|`created_by`|`bigint`|YES|`∅`|||创建人|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_by`|`bigint`|YES|`∅`|||更新人|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|13|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除标志|
|14|`remark`|`varchar(500)`|YES|`∅`|||备注|

## md_partner

- 表注释：合作方表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||合作方ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`partner_code`|`varchar(64)`|NO|`∅`|||合作方编号|
|4|`partner_name`|`varchar(200)`|NO|`∅`|MUL||合作方名称|
|5|`partner_type`|`varchar(50)`|NO|`∅`|MUL||供应商/分包商/租赁商/服务商等|
|6|`credit_code`|`varchar(100)`|YES|`∅`|||统一社会信用代码|
|7|`legal_person`|`varchar(100)`|YES|`∅`|||法人|
|8|`contact_name`|`varchar(100)`|YES|`∅`|||联系人|
|9|`contact_phone`|`varchar(50)`|YES|`∅`|||联系电话|
|10|`bank_name`|`varchar(200)`|YES|`∅`|||开户行|
|11|`bank_account`|`varchar(100)`|YES|`∅`|||银行账号|
|12|`qualification_level`|`varchar(100)`|YES|`∅`|||资质等级|
|13|`blacklist_flag`|`tinyint`|NO|`0`|MUL||是否黑名单：0否，1是|
|14|`risk_level`|`varchar(50)`|YES|`∅`|||风险等级|
|15|`status`|`varchar(50)`|NO|`ENABLE`|||状态|
|16|`created_by`|`bigint`|YES|`∅`|||创建人|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|创建时间|
|18|`updated_by`|`bigint`|YES|`∅`|||更新人|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|21|`remark`|`varchar(500)`|YES|`∅`|||备注|
|22|`default_lead_days`|`int`|YES|`∅`|||未定义（需要人工确认）|
|23|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## measurement_period

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`period_code`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|6|`period_name`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|7|`start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`cutoff_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`status`|`varchar(20)`|NO|`OPEN`|||未定义（需要人工确认）|
|11|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|16|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|17|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## org_company

- 表注释：公司表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||公司ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`company_code`|`varchar(50)`|NO|`∅`|||公司编码|
|4|`company_name`|`varchar(200)`|NO|`∅`|||公司名称|
|5|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|6|`created_by`|`bigint`|YES|`∅`|||创建人|
|7|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|8|`updated_by`|`bigint`|YES|`∅`|||更新人|
|9|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|10|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|11|`remark`|`text`|YES|`∅`|||备注|
|12|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## org_department

- 表注释：部门表（自引用树形结构）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||部门ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`company_id`|`bigint`|NO|`∅`|MUL||所属公司ID|
|4|`parent_id`|`bigint`|YES|`∅`|MUL||上级部门ID，NULL为根部门|
|5|`dept_code`|`varchar(50)`|NO|`∅`|||部门编码|
|6|`dept_name`|`varchar(200)`|NO|`∅`|||部门名称|
|7|`order_num`|`int`|NO|`0`|||排序号|
|8|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|9|`created_by`|`bigint`|YES|`∅`|||创建人|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_by`|`bigint`|YES|`∅`|||更新人|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|13|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|14|`remark`|`text`|YES|`∅`|||备注|

## org_position

- 表注释：岗位表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||岗位ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`company_id`|`bigint`|YES|`∅`|MUL||所属公司ID|
|4|`department_id`|`bigint`|YES|`∅`|MUL||所属部门ID|
|5|`position_code`|`varchar(50)`|NO|`∅`|||岗位编码|
|6|`position_name`|`varchar(200)`|NO|`∅`|||岗位名称|
|7|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|8|`created_by`|`bigint`|YES|`∅`|||创建人|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|10|`updated_by`|`bigint`|YES|`∅`|||更新人|
|11|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|12|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|13|`remark`|`text`|YES|`∅`|||备注|
|14|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## org_user_position

- 表注释：用户岗位关系
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||用户岗位关系ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`user_id`|`bigint`|NO|`∅`|||用户ID|
|4|`position_id`|`bigint`|NO|`∅`|||岗位ID|
|5|`primary_flag`|`tinyint`|NO|`0`|||是否主岗位|
|6|`status`|`varchar(20)`|NO|`ACTIVE`|||ACTIVE/INACTIVE|
|7|`effective_from`|`date`|YES|`∅`|||生效日期|
|8|`effective_to`|`date`|YES|`∅`|||失效日期|
|9|`created_by`|`bigint`|YES|`∅`|||创建人|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_by`|`bigint`|YES|`∅`|||更新人|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|

## overhead_allocation_record

- 表注释：间接费用分摊记录表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||分摊记录ID|
|2|`tenant_id`|`bigint`|NO|`0`|||未定义（需要人工确认）|
|3|`rule_id`|`bigint`|NO|`∅`|MUL||分摊规则ID|
|4|`source_project_id`|`bigint`|NO|`∅`|||费用发生项目ID|
|5|`target_project_id`|`bigint`|NO|`∅`|MUL||分摊目标项目ID|
|6|`cost_subject_id`|`bigint`|NO|`∅`|||科目ID|
|7|`allocation_date`|`date`|NO|`∅`|MUL||分摊日期|
|8|`source_amount`|`decimal(18,2)`|NO|`0.00`|||原始费用金额|
|9|`allocated_amount`|`decimal(18,2)`|NO|`0.00`|||分摊金额|
|10|`allocation_ratio`|`decimal(5,4)`|NO|`0.0000`|||分摊比例|
|11|`status`|`varchar(50)`|NO|`DRAFT`|||DRAFT/CONFIRMED/POSTED|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## overhead_allocation_rule

- 表注释：间接费用分摊规则表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||分摊规则ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`cost_subject_id`|`bigint`|NO|`∅`|||间接费用科目ID（5401.04.xx）|
|4|`allocation_basis`|`varchar(50)`|NO|`∅`|||DIRECT_LABOR直接人工比例/CONTRACT_AMOUNT合同额比例/USAGE实际使用|
|5|`allocation_cycle`|`varchar(20)`|NO|`MONTHLY`|||MONTHLY按月/PER_OCCURRENCE按次|
|6|`status`|`varchar(50)`|NO|`ENABLE`|MUL||未定义（需要人工确认）|
|7|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|9|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|11|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|12|`remark`|`text`|YES|`∅`|||分摊说明/备注|

## overhead_allocation_run

- 表注释：间接费月度分摊执行事实
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||执行ID（雪花ID）|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|3|`rule_id`|`bigint`|NO|`∅`|||分摊规则ID|
|4|`period`|`date`|NO|`∅`|||目标自然月月末|
|5|`trigger_type`|`varchar(20)`|NO|`∅`|||MANUAL/SCHEDULED|
|6|`executed_by`|`bigint`|YES|`∅`|||手工执行用户；定时任务为空|
|7|`run_status`|`varchar(30)`|NO|`∅`|MUL||PENDING/SUCCESS/SKIPPED_ZERO/SKIPPED_NO_WEIGHT|
|8|`allocated_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`cost_item_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|12|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## owner_measurement_review_line

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`submission_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`measurement_line_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`submitted_quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|6|`submitted_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`confirmed_quantity`|`decimal(18,4)`|YES|`∅`|||未定义（需要人工确认）|
|8|`confirmed_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|9|`deducted_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|10|`deduction_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|13|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## owner_measurement_submission

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`measurement_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`submission_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`revision_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|8|`submitted_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`external_document_no`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|
|10|`submitted_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`confirmed_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`deducted_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|13|`status`|`varchar(32)`|NO|`SUBMITTED`|||未定义（需要人工确认）|
|14|`reviewer_name`|`varchar(100)`|YES|`∅`|||未定义（需要人工确认）|
|15|`review_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|16|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|21|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|23|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|24|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## owner_settlement

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`revenue_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`settlement_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`settlement_period`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`settlement_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`gross_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`tax_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`retention_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|12|`net_receivable_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|14|`customer_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|15|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|16|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|17|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`formula_version`|`varchar(64)`|NO|`OWNER_SETTLEMENT_V1`|||未定义（需要人工确认）|
|19|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|20|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|22|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|23|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|24|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|25|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|26|`production_measurement_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|27|`owner_submission_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|28|`reported_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|29|`deducted_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|30|`settlement_type`|`varchar(32)`|NO|`PROGRESS`|||PROGRESS/FINAL|

## pay_application

- 表注释：付款申请表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||付款申请ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|MUL||合作方ID|
|6|`apply_code`|`varchar(64)`|NO|`∅`|||申请单编号|
|7|`apply_amount`|`decimal(18,2)`|NO|`0.00`|||申请金额|
|8|`approved_amount`|`decimal(18,2)`|NO|`0.00`|||批准金额|
|9|`actual_pay_amount`|`decimal(18,2)`|NO|`0.00`|||实际付款金额|
|10|`pay_type`|`varchar(50)`|NO|`∅`|||付款类型：预付款/进度款/结算款/质保金等|
|11|`pay_status`|`varchar(50)`|NO|`PENDING`|MUL||付款状态：PENDING待付，PARTIAL部分付款，PAID已付清|
|12|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|13|`apply_reason`|`varchar(1000)`|YES|`∅`|||申请事由|
|14|`created_by`|`bigint`|YES|`∅`|||创建人|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|16|`updated_by`|`bigint`|YES|`∅`|||更新人|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|18|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|19|`remark`|`varchar(500)`|YES|`∅`|||备注|
|20|`cost_subject_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|21|`budget_line_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|22|`expense_category`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|23|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|24|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|25|`integrity_version`|`varchar(32)`|NO|`LEGACY_UNVERIFIED`|||未定义（需要人工确认）|
|26|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## pay_application_basis

- 表注释：付款依据关联表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||关联ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`pay_application_id`|`bigint`|NO|`∅`|MUL||付款申请ID|
|4|`basis_type`|`varchar(50)`|NO|`∅`|MUL||依据类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证|
|5|`basis_id`|`bigint`|NO|`∅`|||依据单据ID|
|6|`basis_amount`|`decimal(18,2)`|NO|`∅`|||依据金额|
|7|`created_by`|`bigint`|YES|`∅`|||创建人|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|9|`updated_by`|`bigint`|YES|`∅`|||更新人|
|10|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|11|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|12|`remark`|`varchar(500)`|YES|`∅`|||备注|

## pay_invoice

- 表注释：发票表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||发票ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`pay_application_id`|`bigint`|YES|`∅`|MUL||付款申请ID，关联pay_application.id|
|4|`pay_record_id`|`bigint`|YES|`∅`|MUL||付款记录ID，关联pay_record.id|
|5|`invoice_no`|`varchar(100)`|NO|`∅`|||发票号码|
|6|`invoice_type`|`varchar(50)`|NO|`VAT_SPECIAL`|||发票类型：VAT_SPECIAL增值税专票，VAT_NORMAL增值税普票，OTHER其他|
|7|`invoice_amount`|`decimal(18,2)`|NO|`∅`|||发票金额|
|8|`tax_rate`|`decimal(5,2)`|YES|`∅`|||税率（百分比，如13.00）|
|9|`tax_amount`|`decimal(18,2)`|YES|`∅`|||税额|
|10|`invoice_date`|`date`|YES|`∅`|||开票日期|
|11|`verify_status`|`varchar(50)`|NO|`PENDING`|||核验状态：PENDING待核验，VERIFIED已认证，ABNORMAL异常|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`text`|YES|`∅`|||备注|
|18|`seller_name`|`varchar(200)`|YES|`∅`|||卖方名称|
|19|`buyer_name`|`varchar(200)`|YES|`∅`|||买方名称|
|20|`buyer_tax_no`|`varchar(50)`|YES|`∅`|||买方税号|
|21|`seller_tax_no`|`varchar(50)`|YES|`∅`|||卖方税号|
|22|`project_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|23|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|24|`partner_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|25|`document_type`|`varchar(32)`|NO|`ELECTRONIC_INVOICE`|||未定义（需要人工确认）|
|26|`integrity_version`|`varchar(32)`|NO|`LEGACY_UNVERIFIED`|||未定义（需要人工确认）|
|27|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|28|`exception_status`|`varchar(32)`|NO|`NORMAL`|||未定义（需要人工确认）|
|29|`exception_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## pay_record

- 表注释：付款记录表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||付款记录ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|YES|`∅`|MUL||项目ID|
|4|`pay_application_id`|`bigint`|NO|`∅`|MUL||付款申请ID|
|5|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|6|`partner_id`|`bigint`|YES|`∅`|MUL||合作方ID|
|7|`pay_amount`|`decimal(18,2)`|NO|`0.00`|||付款金额|
|8|`pay_date`|`date`|NO|`∅`|MUL||付款日期|
|9|`pay_method`|`varchar(50)`|YES|`∅`|||付款方式：银行转账/承兑汇票/现金等|
|10|`voucher_no`|`varchar(100)`|YES|`∅`|||付款凭证号|
|11|`pay_status`|`varchar(50)`|NO|`SUCCESS`|||付款状态：SUCCESS成功，FAILED失败，PROCESSING处理中|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`varchar(500)`|YES|`∅`|||备注|
|18|`external_txn_no`|`varchar(128)`|YES|`∅`|||外部交易流水号|
|19|`fund_account_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|20|`paid_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|21|`failure_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|22|`reversed_record_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|23|`reversed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|24|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|25|`reversal_type`|`varchar(32)`|YES|`∅`|||REVERSAL/REFUND|

## payment_application_source

- 表注释：付款申请统一来源
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`pay_application_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`source_type`|`varchar(32)`|NO|`∅`|||EXPENSE/SETTLEMENT/DIRECT|
|5|`source_ref_id`|`bigint`|NO|`∅`|||来源ID；DIRECT时等于付款申请ID|
|6|`expense_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`settlement_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|8|`sub_measure_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|9|`source_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|11|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|13|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|15|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|16|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|17|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|

## payment_record_source_allocation

- 表注释：付款记录来源金额分配
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`pay_record_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`payment_source_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`source_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|6|`source_ref_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|7|`allocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## payment_schedule

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`pay_application_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`schedule_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|7|`planned_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`planned_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|10|`reminder_days`|`int`|NO|`7`|||未定义（需要人工确认）|
|11|`status`|`varchar(32)`|NO|`PLANNED`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## pm_project

- 表注释：项目表
- information_schema 估算行数：1

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||项目ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`org_id`|`bigint`|YES|`∅`|||所属组织/项目部ID|
|4|`project_code`|`varchar(64)`|NO|`∅`|||项目编号|
|5|`project_name`|`varchar(200)`|NO|`∅`|MUL||项目名称|
|6|`project_type`|`varchar(50)`|YES|`∅`|||项目类型|
|7|`project_address`|`varchar(300)`|YES|`∅`|||项目地址|
|8|`owner_unit`|`varchar(200)`|YES|`∅`|||建设单位|
|9|`supervisor_unit`|`varchar(200)`|YES|`∅`|||监理单位|
|10|`design_unit`|`varchar(200)`|YES|`∅`|||设计单位|
|11|`contract_amount`|`decimal(18,2)`|NO|`0.00`|||总包合同金额|
|12|`target_cost`|`decimal(18,2)`|NO|`0.00`|||目标成本|
|13|`planned_start_date`|`date`|YES|`∅`|||计划开工日期|
|14|`planned_end_date`|`date`|YES|`∅`|||计划竣工日期|
|15|`actual_start_date`|`date`|YES|`∅`|||实际开工日期|
|16|`actual_end_date`|`date`|YES|`∅`|||实际竣工日期|
|17|`project_manager_id`|`bigint`|YES|`∅`|MUL||项目经理用户ID|
|18|`status`|`varchar(50)`|NO|`DRAFT`|MUL||项目状态|
|19|`approval_status`|`varchar(50)`|YES|`∅`|||审批状态|
|20|`created_by`|`bigint`|YES|`∅`|||创建人|
|21|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|创建时间|
|22|`updated_by`|`bigint`|YES|`∅`|||更新人|
|23|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|24|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|25|`remark`|`varchar(500)`|YES|`∅`|||备注|
|26|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## pm_project_member

- 表注释：项目成员表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||成员ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`user_id`|`bigint`|NO|`∅`|MUL||用户ID|
|5|`role_code`|`varchar(50)`|NO|`∅`|||项目角色：PM项目经理，CM商务经理，CSTM成本经理，MAT材料员，SUBC分包管理员，FIN财务，OTH其他|
|6|`position_name`|`varchar(200)`|YES|`∅`|||岗位名称（可覆盖用户默认岗位）|
|7|`start_date`|`date`|YES|`∅`|||加入日期|
|8|`end_date`|`date`|YES|`∅`|||退出日期|
|9|`status`|`varchar(50)`|NO|`ACTIVE`|||状态：ACTIVE在职，INACTIVE已退出|
|10|`created_by`|`bigint`|YES|`∅`|||创建人|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|12|`updated_by`|`bigint`|YES|`∅`|||更新人|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|14|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|15|`remark`|`text`|YES|`∅`|||备注|

## production_measurement

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`period_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`measure_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`measure_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|8|`current_reported_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`cumulative_reported_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|10|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|11|`approval_status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|13|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|14|`formula_version`|`varchar(64)`|NO|`PRODUCTION_MEASUREMENT_V1`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## production_measurement_line

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`measurement_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`source_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|5|`contract_item_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`contract_change_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`item_code`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|8|`item_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|9|`item_spec`|`varchar(300)`|YES|`∅`|||未定义（需要人工确认）|
|10|`unit`|`varchar(50)`|YES|`∅`|||未定义（需要人工确认）|
|11|`contract_quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|12|`prior_approved_quantity`|`decimal(18,4)`|NO|`0.0000`|||未定义（需要人工确认）|
|13|`current_reported_quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|14|`cumulative_reported_quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|15|`unit_price`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|16|`current_reported_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|17|`cumulative_reported_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|18|`evidence_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`sort_order`|`int`|NO|`0`|||未定义（需要人工确认）|
|20|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## project_budget

- 表注释：项目预算版本
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`version_no`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|5|`budget_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`total_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`approval_status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`DRAFT`|||未定义（需要人工确认）|
|9|`active_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|10|`active_token`|`bigint`|YES|`∅`|||激活时等于project_id，用于数据库保证单项目唯一生效版本|
|11|`effective_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|17|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|18|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_budget_line

- 表注释：项目预算科目
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`budget_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`cost_subject_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`budget_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`reserved_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|8|`consumed_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|9|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|12|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_closeout

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`closeout_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`planned_completion_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|6|`actual_completion_date`|`date`|YES|`∅`|||未定义（需要人工确认）|
|7|`status`|`varchar(40)`|NO|`INITIATED`|||未定义（需要人工确认）|
|8|`final_owner_settlement_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|9|`tail_collection_verified_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`closed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`closed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|17|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|18|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_corrective_action

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`schedule_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`snapshot_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`alert_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`action_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|9|`action_plan`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|10|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|11|`due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|12|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|13|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`generated_revision_plan_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|15|`completed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|19|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|21|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|22|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_period_plan

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`schedule_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`parent_period_plan_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`period_type`|`varchar(20)`|NO|`∅`|||未定义（需要人工确认）|
|7|`period_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`period_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|9|`start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|13|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|16|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|18|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|19|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_period_plan_item

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`period_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`wbs_task_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`target_progress`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|6|`planned_quantity`|`decimal(18,4)`|YES|`∅`|||未定义（需要人工确认）|
|7|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|9|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## project_progress_snapshot

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`schedule_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`snapshot_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|6|`source_daily_log_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`planned_progress`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|8|`actual_progress`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|9|`deviation_percent`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|10|`lagging_task_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|11|`status`|`varchar(20)`|NO|`∅`|||未定义（需要人工确认）|
|12|`formula_version`|`varchar(40)`|NO|`SCHEDULE_PROGRESS_V1`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## project_schedule_plan

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`plan_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`plan_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`plan_type`|`varchar(20)`|NO|`BASELINE`|||未定义（需要人工确认）|
|7|`version_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|8|`parent_plan_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|9|`corrective_action_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|10|`planned_start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`planned_end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|12|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|13|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`activated_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## project_wbs_task

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`schedule_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`parent_task_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`predecessor_task_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`task_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`task_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|9|`work_area`|`varchar(200)`|YES|`∅`|||未定义（需要人工确认）|
|10|`responsible_user_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`planned_start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|12|`planned_end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|13|`weight_percent`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|14|`planned_quantity`|`decimal(18,4)`|YES|`∅`|||未定义（需要人工确认）|
|15|`unit`|`varchar(30)`|YES|`∅`|||未定义（需要人工确认）|
|16|`actual_start_date`|`date`|YES|`∅`|||未定义（需要人工确认）|
|17|`actual_end_date`|`date`|YES|`∅`|||未定义（需要人工确认）|
|18|`actual_quantity`|`decimal(18,4)`|NO|`0.0000`|||未定义（需要人工确认）|
|19|`actual_progress`|`decimal(7,4)`|NO|`0.0000`|||未定义（需要人工确认）|
|20|`status`|`varchar(20)`|NO|`NOT_STARTED`|||未定义（需要人工确认）|
|21|`sort_order`|`int`|NO|`0`|||未定义（需要人工确认）|
|22|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|23|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|24|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|25|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|26|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|27|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|28|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_consequence

- 表注释：问题处罚与成本后果
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`issue_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`consequence_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`decision_type`|`varchar(20)`|NO|`∅`|||NONE/FINE/REWORK_COST/BOTH|
|9|`fine_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|10|`rework_cost_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|11|`evaluation_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`evaluation_comment`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|13|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|14|`cost_item_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|15|`evaluation_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|16|`posted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`posted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|18|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|21|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|23|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|24|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_inspection_plan

- 表注释：质量安全检查计划
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`plan_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`plan_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`inspection_type`|`varchar(16)`|NO|`∅`|||QUALITY/SAFETY|
|7|`frequency_type`|`varchar(16)`|NO|`∅`|||SINGLE/WEEKLY/MONTHLY|
|8|`start_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`end_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`owner_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`activated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`activated_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`completed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`completed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|19|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|21|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|22|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_inspection_record

- 表注释：质量安全检查记录
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`inspection_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`inspection_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`location`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`inspector_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|9|`conclusion`|`varchar(16)`|NO|`PENDING`|||未定义（需要人工确认）|
|10|`summary`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|15|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|19|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|20|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_issue

- 表注释：质量安全问题单
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`inspection_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`issue_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`issue_type`|`varchar(16)`|NO|`∅`|||QUALITY/SAFETY|
|8|`category`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|9|`severity`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|10|`title`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|11|`description`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|12|`responsible_kind`|`varchar(16)`|NO|`∅`|||INTERNAL/PARTNER|
|13|`responsible_partner_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|15|`due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|16|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|17|`closed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`closed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|19|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|20|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|22|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|23|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|24|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|25|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_partner_evaluation

- 表注释：供应商或分包商质量安全评价事实
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`consequence_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`issue_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`evaluation_type`|`varchar(16)`|NO|`∅`|||未定义（需要人工确认）|
|8|`score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`evaluation_comment`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|10|`evaluated_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|11|`evaluated_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## qs_rectification

- 表注释：问题整改与复验轮次
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`issue_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`round_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|6|`action_description`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|7|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`planned_complete_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`actual_completed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|11|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`reinspection_comment`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|14|`reinspected_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`reinspected_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|19|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|21|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|22|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## receivable_adjustment

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`receivable_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`adjustment_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|5|`amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|6|`reason`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|7|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|9|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## revenue_audit_event

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`event_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|4|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`project_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|7|`operator_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|8|`event_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`archive_bucket`|`varchar(32)`|NO|`HOT`|||未定义（需要人工确认）|
|10|`payload_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|11|`payload_hash`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|

## revenue_dashboard_snapshot

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`snapshot_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|5|`formula_version`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`confirmed_revenue`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`settled_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`receivable_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`outstanding_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`overdue_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`collected_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`invoiced_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`collection_rate`|`decimal(12,6)`|NO|`∅`|||未定义（需要人工确认）|
|14|`refreshed_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|15|`refresh_mode`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|

## revenue_external_sync

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`endpoint_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`business_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|6|`message_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`sync_status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`idempotency_key`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|9|`last_error`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|10|`synced_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## revenue_import_batch

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`import_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`file_name`|`varchar(255)`|NO|`∅`|||未定义（需要人工确认）|
|6|`file_hash`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`total_rows`|`int`|NO|`∅`|||未定义（需要人工确认）|
|9|`valid_rows`|`int`|NO|`∅`|||未定义（需要人工确认）|
|10|`invalid_rows`|`int`|NO|`∅`|||未定义（需要人工确认）|
|11|`diff_summary_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`applied_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|

## revenue_import_row

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|||未定义（需要人工确认）|
|3|`batch_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`row_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|5|`input_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|6|`diff_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|7|`validation_status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|8|`validation_message`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|

## revenue_reconciliation_issue

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`run_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`dimension_type`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`business_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|6|`issue_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`expected_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|8|`actual_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(32)`|NO|`OPEN`|||未定义（需要人工确认）|
|10|`detail`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## revenue_reconciliation_run

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`business_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|4|`status`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|5|`issue_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|6|`started_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|7|`finished_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|8|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|

## sales_invoice

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`customer_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`invoice_code`|`varchar(64)`|YES|`∅`|||未定义（需要人工确认）|
|7|`invoice_no`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|8|`invoice_type`|`varchar(32)`|NO|`∅`|||未定义（需要人工确认）|
|9|`invoice_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`amount_without_tax`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`tax_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`total_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`allocated_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|14|`status`|`varchar(32)`|NO|`ISSUED`|||未定义（需要人工确认）|
|15|`verification_status`|`varchar(32)`|NO|`UNVERIFIED`|||未定义（需要人工确认）|
|16|`attachment_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|20|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|22|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|23|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sales_invoice_allocation

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`invoice_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`receivable_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`allocated_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|6|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|7|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## sales_invoice_review

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`invoice_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`raw_result_json`|`longtext`|NO|`∅`|||未定义（需要人工确认）|
|5|`confidence`|`decimal(5,4)`|NO|`∅`|||未定义（需要人工确认）|
|6|`comparison_json`|`longtext`|YES|`∅`|||未定义（需要人工确认）|
|7|`review_status`|`varchar(32)`|NO|`PENDING`|||未定义（需要人工确认）|
|8|`reviewer_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|10|`review_note`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## settlement_sub_measure

- 表注释：终期结算分包计量快照关系
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`settlement_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`sub_measure_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`reported_amount_snapshot`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|6|`approved_amount_snapshot`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`deduction_amount_snapshot`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`net_amount_snapshot`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## site_daily_log

- 表注释：项目现场日报
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|4|`report_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|5|`construction_content`|`text`|NO|`∅`|||未定义（需要人工确认）|
|6|`issues_delays`|`text`|YES|`∅`|||未定义（需要人工确认）|
|7|`next_day_plan`|`text`|YES|`∅`|||未定义（需要人工确认）|
|8|`weather_summary`|`varchar(200)`|YES|`∅`|||人工天气摘要|
|9|`on_site_headcount`|`int`|YES|`∅`|||在场人数；NULL 未填写，0 明确无人|
|10|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|11|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|17|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|18|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## site_daily_progress

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`daily_log_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`schedule_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`weekly_plan_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`wbs_task_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|8|`previous_progress`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|9|`current_progress`|`decimal(7,4)`|NO|`∅`|||未定义（需要人工确认）|
|10|`completed_quantity`|`decimal(18,4)`|NO|`0.0000`|||未定义（需要人工确认）|
|11|`work_description`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## sp_bid_evaluation

- 表注释：供应商比价评审
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`sourcing_event_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`quote_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`commercial_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|7|`technical_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`delivery_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|9|`quality_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`total_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`evaluation_comment`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|12|`evaluated_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|13|`evaluated_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|16|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|18|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|19|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_blacklist_record

- 表注释：供应商黑名单审批记录
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`performance_evaluation_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`action_type`|`varchar(12)`|NO|`ADD`|||未定义（需要人工确认）|
|7|`reason`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|8|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|9|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|11|`reviewed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`review_comment`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|14|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|15|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|17|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|19|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|20|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_performance_evaluation

- 表注释：供应商履约综合评价
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`purchase_order_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`evaluation_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`period_start`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`period_end`|`date`|NO|`∅`|||未定义（需要人工确认）|
|10|`delivery_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|11|`quality_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|12|`service_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`commercial_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|14|`total_score`|`decimal(5,2)`|NO|`∅`|||未定义（需要人工确认）|
|15|`grade`|`varchar(8)`|NO|`∅`|||未定义（需要人工确认）|
|16|`on_time_flag`|`tinyint`|NO|`∅`|||未定义（需要人工确认）|
|17|`approved_receipt_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|18|`unqualified_receipt_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|19|`return_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|20|`finalized_settlement_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|21|`quality_safety_fact_count`|`int`|NO|`0`|||未定义（需要人工确认）|
|22|`quality_safety_average`|`decimal(5,2)`|YES|`∅`|||未定义（需要人工确认）|
|23|`evaluation_comment`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|24|`recommend_blacklist`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|25|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|26|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|27|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|28|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|29|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|30|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|31|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|32|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|33|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|34|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_sourcing_event

- 表注释：供应商询价招标事件
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`purchase_request_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`sourcing_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`sourcing_title`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|7|`sourcing_type`|`varchar(16)`|NO|`∅`|||INQUIRY/TENDER|
|8|`deadline`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`currency_code`|`varchar(8)`|NO|`CNY`|||未定义（需要人工确认）|
|10|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|11|`awarded_quote_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|12|`awarded_partner_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|13|`contract_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|14|`award_reason`|`varchar(1000)`|YES|`∅`|||未定义（需要人工确认）|
|15|`published_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`published_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`awarded_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`awarded_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|19|`contracted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`contracted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|21|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|22|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|23|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|24|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|25|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|26|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|27|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_sourcing_supplier

- 表注释：招采受邀供应商
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`sourcing_event_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`invitation_status`|`varchar(20)`|NO|`PENDING`|||未定义（需要人工确认）|
|6|`invited_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|7|`responded_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|8|`disqualification_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|9|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|11|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|13|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|14|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_supplier_quote

- 表注释：供应商报价
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`sourcing_event_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`sourcing_supplier_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`quote_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`total_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|8|`tax_rate`|`decimal(8,4)`|NO|`0.0000`|||未定义（需要人工确认）|
|9|`delivery_days`|`int`|NO|`∅`|||未定义（需要人工确认）|
|10|`validity_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`commercial_terms`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|12|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|13|`submitted_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`submitted_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_supplier_return

- 表注释：供应商退货事实
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`partner_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`purchase_order_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`receipt_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|8|`warehouse_id`|`bigint`|YES|`∅`|||退货出库仓库ID|
|9|`return_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|10|`return_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|11|`return_quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|12|`return_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|13|`reason`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|14|`status`|`varchar(16)`|NO|`DRAFT`|||未定义（需要人工确认）|
|15|`idempotency_key`|`varchar(128)`|YES|`∅`|||外部幂等键|
|16|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|18|`reversed_by`|`bigint`|YES|`∅`|||冲销人|
|19|`reversed_at`|`datetime`|YES|`∅`|||冲销时间|
|20|`reversal_reason`|`varchar(500)`|YES|`∅`|||冲销原因|
|21|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|22|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|23|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|24|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|25|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|26|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|27|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## sp_supplier_return_item

- 表注释：供应商退货明细
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||供应商退货明细ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`return_id`|`bigint`|NO|`∅`|||供应商退货ID|
|4|`receipt_item_id`|`bigint`|NO|`∅`|||原验收明细ID|
|5|`order_item_id`|`bigint`|NO|`∅`|||采购订单明细ID|
|6|`quality_disposition_id`|`bigint`|YES|`∅`|MUL||不合格处置ID；为空表示已入库合格品退货|
|7|`original_stock_txn_id`|`bigint`|YES|`∅`|MUL||原验收入库流水ID|
|8|`original_cost_item_id`|`bigint`|YES|`∅`|MUL||原直耗成本ID|
|9|`material_id`|`bigint`|NO|`∅`|MUL||材料ID|
|10|`return_source`|`varchar(20)`|NO|`∅`|||QUALIFIED/REJECTED|
|11|`quantity`|`decimal(18,4)`|NO|`∅`|||退货数量|
|12|`unit_cost`|`decimal(18,4)`|NO|`∅`|||单位成本|
|13|`amount`|`decimal(18,2)`|NO|`∅`|||退货金额|
|14|`created_by`|`bigint`|YES|`∅`|||创建人|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|16|`updated_by`|`bigint`|YES|`∅`|||更新人|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|18|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|19|`remark`|`varchar(500)`|YES|`∅`|||备注|

## stl_settlement

- 表注释：结算主表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||结算ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|MUL||合作方ID|
|6|`settlement_code`|`varchar(64)`|NO|`∅`|||结算单号|
|7|`settlement_type`|`varchar(50)`|YES|`∅`|||结算类型|
|8|`contract_amount`|`decimal(18,2)`|YES|`∅`|||合同金额|
|9|`change_amount`|`decimal(18,2)`|NO|`0.00`|||变更金额|
|10|`measured_amount`|`decimal(18,2)`|NO|`0.00`|||计量金额|
|11|`deduction_amount`|`decimal(18,2)`|NO|`0.00`|||扣款金额|
|12|`paid_amount`|`decimal(18,2)`|NO|`0.00`|||已付金额|
|13|`final_amount`|`decimal(18,2)`|YES|`∅`|||结算金额|
|14|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|15|`created_by`|`bigint`|YES|`∅`|||创建人|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|17|`updated_by`|`bigint`|YES|`∅`|||更新人|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|19|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|20|`remark`|`varchar(500)`|YES|`∅`|||备注|
|21|`unpaid_amount`|`decimal(18,2)`|NO|`0.00`|||未付金额|
|22|`warranty_amount`|`decimal(18,2)`|NO|`0.00`|||质保金金额|
|23|`settlement_status`|`varchar(50)`|NO|`DRAFT`|||结算状态：DRAFT草稿，FINALIZED已定案，CANCELLED已作废|
|24|`finalized_at`|`datetime`|YES|`∅`|||定案时间|
|25|`amount_formula_version`|`varchar(64)`|NO|`LEGACY_UNVERIFIED`|||结算金额口径版本；历史数据核对后方可回填当前版本|

## stl_settlement_item

- 表注释：结算明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||结算明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`settlement_id`|`bigint`|NO|`∅`|MUL||结算单ID|
|4|`item_name`|`varchar(200)`|YES|`∅`|||清单项名称|
|5|`unit`|`varchar(20)`|YES|`∅`|||计量单位|
|6|`quantity`|`decimal(18,4)`|YES|`∅`|||数量|
|7|`unit_price`|`decimal(18,4)`|YES|`∅`|||单价|
|8|`amount`|`decimal(18,2)`|YES|`∅`|||金额|
|9|`cost_subject_id`|`bigint`|YES|`∅`|||成本科目ID|
|10|`created_by`|`bigint`|YES|`∅`|||创建人|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|12|`updated_by`|`bigint`|YES|`∅`|||更新人|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|14|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|15|`remark`|`varchar(500)`|YES|`∅`|||备注|
|16|`source_type`|`varchar(50)`|YES|`∅`|||来源类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证，CT_CONTRACT合同|
|17|`source_id`|`bigint`|YES|`∅`|||来源单据ID|

## sub_measure

- 表注释：分包计量表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||计量ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||分包合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|||分包商ID|
|6|`sub_task_id`|`bigint`|YES|`∅`|MUL||关联分包任务ID|
|7|`measure_code`|`varchar(64)`|NO|`∅`|||计量单号|
|8|`measure_period`|`varchar(50)`|YES|`∅`|||计量周期|
|9|`measure_date`|`date`|YES|`∅`|||计量日期|
|10|`reported_amount`|`decimal(18,2)`|YES|`∅`|||上报金额|
|11|`approved_amount`|`decimal(18,2)`|YES|`∅`|||审定金额|
|12|`deduction_amount`|`decimal(18,2)`|NO|`0.00`|||扣款金额|
|13|`net_amount`|`decimal(18,2)`|YES|`∅`|||净计量金额|
|14|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|15|`cost_generated_flag`|`tinyint`|NO|`0`|||成本生成标识：0未生成，1已生成|
|16|`status`|`varchar(50)`|NO|`DRAFT`|||计量状态|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|

## sub_measure_item

- 表注释：分包计量明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||计量明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`measure_id`|`bigint`|NO|`∅`|MUL||计量单ID|
|4|`contract_item_id`|`bigint`|YES|`∅`|||合同清单项ID|
|5|`item_name`|`varchar(200)`|YES|`∅`|||清单项名称|
|6|`unit`|`varchar(20)`|YES|`∅`|||计量单位|
|7|`contract_quantity`|`decimal(18,4)`|YES|`∅`|||合同数量|
|8|`current_quantity`|`decimal(18,4)`|YES|`∅`|||本期数量|
|9|`cumulative_quantity`|`decimal(18,4)`|YES|`∅`|||累计数量|
|10|`unit_price`|`decimal(18,4)`|YES|`∅`|||单价|
|11|`amount`|`decimal(18,2)`|YES|`∅`|||金额|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`varchar(500)`|YES|`∅`|||备注|

## sub_task

- 表注释：分包任务表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||分包任务ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||分包合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|||分包商ID|
|6|`predecessor_task_id`|`bigint`|YES|`∅`|MUL||单一前置分包任务ID|
|7|`task_code`|`varchar(64)`|NO|`∅`|||任务编号|
|8|`task_name`|`varchar(200)`|NO|`∅`|||任务名称|
|9|`work_area`|`varchar(200)`|YES|`∅`|||施工区域|
|10|`planned_start_date`|`date`|YES|`∅`|||计划开始日期|
|11|`planned_end_date`|`date`|YES|`∅`|||计划结束日期|
|12|`actual_start_date`|`date`|YES|`∅`|||实际开始日期|
|13|`actual_end_date`|`date`|YES|`∅`|||实际结束日期|
|14|`progress_percent`|`decimal(6,2)`|NO|`0.00`|||进度百分比|
|15|`status`|`varchar(50)`|NO|`NOT_STARTED`|||状态：NOT_STARTED未开始，IN_PROGRESS进行中，COMPLETED已完成|
|16|`created_by`|`bigint`|YES|`∅`|||创建人|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|18|`updated_by`|`bigint`|YES|`∅`|||更新人|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|21|`remark`|`varchar(500)`|YES|`∅`|||备注|

## sys_dict_data

- 表注释：字典数据表
- information_schema 估算行数：92

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||字典数据ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`dict_type_id`|`bigint`|NO|`∅`|MUL||字典类型ID|
|4|`dict_label`|`varchar(200)`|NO|`∅`|||字典标签（显示文本）|
|5|`dict_value`|`varchar(200)`|NO|`∅`|||字典键值|
|6|`css_class`|`varchar(100)`|YES|`∅`|||样式类名|
|7|`list_class`|`varchar(100)`|YES|`∅`|||回显样式|
|8|`order_num`|`int`|NO|`0`|||显示顺序|
|9|`status`|`varchar(50)`|NO|`ENABLE`|MUL||状态：ENABLE启用，DISABLE禁用|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|11|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|

## sys_dict_type

- 表注释：字典类型表
- information_schema 估算行数：22

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||字典类型ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`dict_code`|`varchar(100)`|NO|`∅`|||字典编码|
|4|`dict_name`|`varchar(200)`|NO|`∅`|||字典名称|
|5|`status`|`varchar(50)`|NO|`ENABLE`|MUL||状态：ENABLE启用，DISABLE禁用|
|6|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|7|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|

## sys_file

- 表注释：系统文件表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||文件ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`business_type`|`varchar(50)`|NO|`∅`|MUL||业务类型（如CONTRACT、PROJECT等）|
|4|`business_id`|`bigint`|NO|`∅`|||业务ID|
|5|`file_name`|`varchar(255)`|NO|`∅`|||存储文件名（UUID.扩展名）|
|6|`original_name`|`varchar(500)`|NO|`∅`|||原始文件名|
|7|`file_size`|`bigint`|NO|`0`|||文件大小（字节）|
|8|`content_type`|`varchar(200)`|YES|`∅`|||文件MIME类型|
|9|`storage_path`|`varchar(500)`|NO|`∅`|||MinIO对象路径（businessType/businessId/fileName）|
|10|`bucket_name`|`varchar(100)`|NO|`cgc-pms`|||MinIO桶名称|
|11|`virus_scan_status`|`varchar(20)`|NO|`NOT_SCANNED`|||病毒扫描状态|
|12|`virus_scan_detail`|`varchar(255)`|YES|`∅`|||病毒特征或失败摘要|
|13|`virus_scanned_at`|`datetime(3)`|YES|`∅`|||病毒扫描完成时间|
|14|`created_by`|`bigint`|YES|`∅`|||创建人|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|创建时间|
|16|`updated_by`|`bigint`|YES|`∅`|||更新人|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|18|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|19|`remark`|`varchar(500)`|YES|`∅`|||备注|
|20|`document_type`|`varchar(32)`|NO|`OTHER`|||未定义（需要人工确认）|

## sys_menu

- 表注释：系统菜单权限表
- information_schema 估算行数：235

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||菜单ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`parent_id`|`bigint`|NO|`0`|MUL||父菜单ID，0表示根节点|
|4|`menu_name`|`varchar(100)`|NO|`∅`|||菜单名称|
|5|`menu_type`|`varchar(20)`|NO|`∅`|MUL||菜单类型：DIR目录，MENU菜单，BUTTON按钮|
|6|`path`|`varchar(300)`|YES|`∅`|||路由地址|
|7|`component`|`varchar(300)`|YES|`∅`|||组件路径|
|8|`perms`|`varchar(200)`|YES|`∅`|||权限标识|
|9|`icon`|`varchar(100)`|YES|`∅`|||菜单图标|
|10|`order_num`|`int`|NO|`0`|MUL||显示顺序|
|11|`status`|`varchar(50)`|NO|`ENABLE`|||状态：ENABLE启用，DISABLE禁用|
|12|`visible`|`tinyint`|NO|`1`|||是否可见：0隐藏，1显示|
|13|`created_by`|`bigint`|YES|`∅`|||创建人|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`remark`|`varchar(500)`|YES|`∅`|||备注|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|18|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|

## sys_notification

- 表注释：站内消息通知表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||通知ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID（显式冗余，非 UserContext）|
|3|`user_id`|`bigint`|NO|`∅`|||接收人用户ID|
|4|`title`|`varchar(500)`|NO|`∅`|||通知标题|
|5|`content`|`text`|YES|`∅`|||通知内容|
|6|`biz_type`|`varchar(100)`|YES|`∅`|MUL||业务类型：WORKFLOW_APPROVAL审批，WORKFLOW_REJECT驳回，WORKFLOW_CC抄送，ALERT预警，SYSTEM系统|
|7|`biz_id`|`bigint`|YES|`∅`|||业务ID（审批实例/预警等）|
|8|`notify_type`|`varchar(50)`|NO|`INFO`|||通知类型：INFO信息，WARNING警告，ERROR错误|
|9|`is_read`|`tinyint`|NO|`0`|||已读标记：0未读，1已读|
|10|`read_time`|`datetime`|YES|`∅`|||阅读时间|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|

## sys_operation_audit_log

- 表注释：操作审计日志
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI|auto_increment|未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|3|`user_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|4|`operation_type`|`varchar(50)`|NO|`∅`|||LOGIN/LOGOUT/CREATE/UPDATE/DELETE/SUBMIT/APPROVE/UPLOAD/DOWNLOAD|
|5|`business_type`|`varchar(50)`|YES|`∅`|||SETTLEMENT/CONTRACT/RECEIPT/INVOICE etc|
|6|`business_id`|`varchar(100)`|YES|`∅`|||未定义（需要人工确认）|
|7|`http_method`|`varchar(10)`|YES|`∅`|||未定义（需要人工确认）|
|8|`request_path`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|9|`success_flag`|`tinyint(1)`|NO|`∅`|||未定义（需要人工确认）|
|10|`error_code`|`varchar(50)`|YES|`∅`|||未定义（需要人工确认）|
|11|`source_ip`|`varchar(50)`|YES|`∅`|||未定义（需要人工确认）|
|12|`duration_ms`|`int`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|

## sys_role

- 表注释：系统角色表
- information_schema 估算行数：12

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||角色ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`role_code`|`varchar(64)`|NO|`∅`|||角色编码|
|4|`role_name`|`varchar(100)`|NO|`∅`|MUL||角色名称|
|5|`role_type`|`varchar(50)`|NO|`CUSTOM`|||角色类型：SYSTEM系统内置，CUSTOM自定义|
|6|`status`|`varchar(50)`|NO|`ENABLE`|MUL||状态：ENABLE启用，DISABLE禁用|
|7|`data_scope`|`varchar(50)`|NO|`SELF`|||数据范围：ALL全部，DEPT本部门，DEPT_AND_CHILD本部门及以下，SELF仅本人，CUSTOM自定义|
|8|`created_by`|`bigint`|YES|`∅`|||创建人|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|10|`updated_by`|`bigint`|YES|`∅`|||更新人|
|11|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|12|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|13|`remark`|`varchar(500)`|YES|`∅`|||备注|
|14|`role_level`|`int`|NO|`2`|||未定义（需要人工确认）|
|15|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## sys_role_menu

- 表注释：角色菜单关联表
- information_schema 估算行数：472

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||主键ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`role_id`|`bigint`|NO|`∅`|||角色ID|
|4|`menu_id`|`bigint`|NO|`∅`|||菜单ID|

## sys_role_menu_audit_snapshot

- 表注释：角色菜单绑定变更审计快照
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||快照ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`operator_id`|`bigint`|YES|`∅`|||操作者用户ID|
|4|`role_id`|`bigint`|NO|`∅`|||目标角色ID|
|5|`before_menu_ids`|`text`|YES|`∅`|||变更前菜单ID快照|
|6|`after_menu_ids`|`text`|YES|`∅`|||变更后菜单ID快照|
|7|`success_flag`|`tinyint(1)`|NO|`∅`|||是否成功：1成功，0失败|
|8|`error_summary`|`varchar(500)`|YES|`∅`|||失败错误摘要|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|

## sys_type_registry

- 表注释：多态业务类型契约注册表
- information_schema 估算行数：36

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||注册项ID|
|2|`type_domain`|`varchar(64)`|NO|`∅`|MUL||类型域，例如WORKFLOW_BUSINESS_TYPE|
|3|`type_code`|`varchar(64)`|NO|`∅`|||类型编码|
|4|`owner_module`|`varchar(64)`|NO|`∅`|||权威维护模块|
|5|`contract_version`|`varchar(16)`|NO|`∅`|||契约版本|
|6|`status`|`varchar(32)`|NO|`ACTIVE`|||状态：ACTIVE/DEPRECATED|
|7|`description`|`varchar(500)`|NO|`∅`|||语义与引用边界|
|8|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|9|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|

## sys_user

- 表注释：系统用户表
- information_schema 估算行数：8

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||用户ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`username`|`varchar(64)`|NO|`∅`|||登录账号|
|4|`password`|`varchar(200)`|NO|`∅`|||登录密码（加密存储）|
|5|`real_name`|`varchar(100)`|YES|`∅`|MUL||真实姓名|
|6|`phone`|`varchar(50)`|YES|`∅`|MUL||手机号|
|7|`email`|`varchar(128)`|YES|`∅`|||邮箱|
|8|`org_id`|`bigint`|YES|`∅`|||所属组织ID，关联org_company.id|
|9|`avatar`|`varchar(500)`|YES|`∅`|||头像URL|
|10|`status`|`varchar(50)`|NO|`ENABLE`|MUL||状态：ENABLE启用，DISABLE禁用|
|11|`is_admin`|`tinyint`|NO|`0`|||是否超级管理员：0否，1是|
|12|`created_by`|`bigint`|YES|`∅`|||创建人|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`|MUL|DEFAULT_GENERATED|创建时间|
|14|`updated_by`|`bigint`|YES|`∅`|||更新人|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|16|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|17|`remark`|`varchar(500)`|YES|`∅`|||备注|
|18|`active_unique_token`|`bigint`|YES|`∅`||STORED GENERATED|活动行唯一键辅助列：活动行=0，删除行=id|

## sys_user_preference

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI|auto_increment|主键ID|
|2|`tenant_id`|`bigint`|NO|`∅`|MUL||租户ID|
|3|`user_id`|`bigint`|NO|`∅`|||用户ID|
|4|`preferences`|`text`|YES|`∅`|||偏好设置，JSON 格式|
|5|`created_by`|`bigint`|YES|`∅`|||创建人|
|6|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|7|`updated_by`|`bigint`|YES|`∅`|||更新人|
|8|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|9|`deleted_flag`|`smallint`|NO|`0`|||逻辑删除标记|
|10|`remark`|`varchar(500)`|YES|`∅`|||备注|

## sys_user_role

- 表注释：用户角色关联表
- information_schema 估算行数：10

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||主键ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`user_id`|`bigint`|NO|`∅`|||用户ID|
|4|`role_id`|`bigint`|NO|`∅`|||角色ID|

## tech_acceptance_archive

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_version_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`construction_reference_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`quality_inspection_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`archive_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`acceptance_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`acceptance_conclusion`|`varchar(20)`|NO|`∅`|||未定义（需要人工确认）|
|10|`archive_location`|`varchar(300)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|12|`archived_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`archived_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|16|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|18|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|19|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_construction_reference

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_version_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`disclosure_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`daily_log_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|7|`wbs_task_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|8|`reference_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`work_area`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|10|`reference_description`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(20)`|NO|`RECORDED`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|16|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|17|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_disclosure

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_version_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`scheme_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|6|`disclosure_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`disclosure_title`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`disclosure_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`presenter_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|10|`recipient_summary`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|11|`disclosure_content`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|12|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|13|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_drawing

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`drawing_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`specialty`|`varchar(50)`|NO|`∅`|||未定义（需要人工确认）|
|7|`source_organization`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`current_version_id`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(20)`|NO|`ACTIVE`|||未定义（需要人工确认）|
|10|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|12|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|14|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|15|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_drawing_review

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_version_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`review_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|6|`review_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|7|`chair_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`participant_summary`|`varchar(500)`|NO|`∅`|||未定义（需要人工确认）|
|9|`conclusion`|`varchar(20)`|NO|`∅`|||未定义（需要人工确认）|
|10|`review_summary`|`varchar(1000)`|NO|`∅`|||未定义（需要人工确认）|
|11|`requires_rfi`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|12|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|13|`confirmed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`confirmed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|15|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|16|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|18|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|20|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|21|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_drawing_version

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`version_no`|`varchar(30)`|NO|`∅`|||未定义（需要人工确认）|
|6|`previous_version_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|7|`source_rfi_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|8|`received_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|9|`received_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|10|`change_summary`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|11|`status`|`varchar(30)`|NO|`RECEIVED`|||未定义（需要人工确认）|
|12|`approved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|16|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|18|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|19|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_item

- 表注释：总工程师技术事项表
- information_schema 估算行数：2

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI|auto_increment|技术事项ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|||项目ID|
|4|`item_type`|`varchar(50)`|NO|`∅`|||事项类型：TECH_PLAN/DESIGN_COORDINATION/TECH_REVIEW/TECH_ISSUE|
|5|`item_code`|`varchar(50)`|NO|`∅`|||事项编码|
|6|`item_title`|`varchar(200)`|NO|`∅`|||事项标题|
|7|`item_level`|`varchar(20)`|NO|`NORMAL`|||事项等级：NORMAL/HIGH/URGENT|
|8|`item_status`|`varchar(30)`|NO|`OPEN`|||事项状态：OPEN/PENDING/CLOSED/OVERDUE|
|9|`discovered_at`|`datetime`|YES|`∅`|||发现时间|
|10|`due_date`|`datetime`|YES|`∅`|||截止时间|
|11|`closed_at`|`datetime`|YES|`∅`|||关闭时间|
|12|`responsible_user_id`|`bigint`|YES|`∅`|||责任人|
|13|`created_by`|`bigint`|NO|`0`|||创建人|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|15|`updated_by`|`bigint`|NO|`0`|||更新人|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|17|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除|
|18|`remark`|`varchar(500)`|YES|`∅`|||备注|
|19|`source_type`|`varchar(40)`|YES|`∅`|||真实技术业务来源类型|
|20|`source_id`|`bigint`|YES|`∅`|||真实技术业务来源ID|

## tech_rfi

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`drawing_version_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`review_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`rfi_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|7|`subject`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|8|`question`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|9|`priority`|`varchar(20)`|NO|`NORMAL`|||未定义（需要人工确认）|
|10|`raised_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|11|`raised_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|12|`response_due_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|13|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|14|`closed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`closed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|16|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|17|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|19|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|21|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|22|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## tech_rfi_response

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`rfi_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`response_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|5|`response_content`|`varchar(2000)`|NO|`∅`|||未定义（需要人工确认）|
|6|`change_required`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|7|`responder_name`|`varchar(100)`|NO|`∅`|||未定义（需要人工确认）|
|8|`responded_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|9|`responded_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|10|`status`|`varchar(20)`|NO|`SUBMITTED`|||未定义（需要人工确认）|
|11|`reviewed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|12|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|13|`review_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|16|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|17|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## technical_scheme

- 表注释：未定义（需要人工确认）
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`scheme_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|5|`scheme_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`scheme_type`|`varchar(30)`|NO|`∅`|||未定义（需要人工确认）|
|7|`responsible_user_id`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|8|`planned_effective_date`|`date`|NO|`∅`|||未定义（需要人工确认）|
|9|`status`|`varchar(20)`|NO|`DRAFT`|||未定义（需要人工确认）|
|10|`approval_instance_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|11|`approved_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|12|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|13|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|14|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|15|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|16|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|17|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|18|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## var_order

- 表注释：变更签证表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||变更签证ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`project_id`|`bigint`|NO|`∅`|MUL||项目ID|
|4|`contract_id`|`bigint`|YES|`∅`|MUL||合同ID|
|5|`partner_id`|`bigint`|YES|`∅`|||合作方ID|
|6|`var_code`|`varchar(64)`|NO|`∅`|||变更编号|
|7|`var_name`|`varchar(200)`|NO|`∅`|||变更名称|
|8|`var_type`|`varchar(50)`|YES|`∅`|||变更类型|
|9|`direction`|`varchar(20)`|YES|`∅`|||变更方向：ADD增加，REDUCE减少|
|10|`reported_amount`|`decimal(18,2)`|YES|`∅`|||上报金额|
|11|`approved_amount`|`decimal(18,2)`|YES|`∅`|||审定金额|
|12|`confirmed_amount`|`decimal(18,2)`|YES|`∅`|||确认金额|
|13|`owner_confirm_flag`|`tinyint`|NO|`0`|||业主确认标识：0未确认，1已确认|
|14|`impact_days`|`int`|NO|`0`|||影响工期天数|
|15|`approval_status`|`varchar(50)`|NO|`DRAFT`|||审批状态|
|16|`cost_generated_flag`|`tinyint`|NO|`0`|||成本生成标识：0未生成，1已生成|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|
|23|`business_matter_key`|`varchar(100)`|YES|`∅`|||跨域业务事项唯一键|
|24|`event_date`|`date`|YES|`∅`|||变更事件日期|
|25|`claim_deadline`|`date`|YES|`∅`|||合同约定索赔申报截止日|
|26|`event_description`|`varchar(1000)`|YES|`∅`|||现场事件及影响说明|
|27|`cause_category`|`varchar(64)`|YES|`∅`|||原因分类|
|28|`responsible_party`|`varchar(200)`|YES|`∅`|||责任方|
|29|`estimated_cost_amount`|`decimal(18,2)`|NO|`0.00`|||内部成本测算金额|
|30|`owner_status`|`varchar(32)`|NO|`NOT_READY`|||业主申报生命周期|
|31|`internal_approval_instance_id`|`bigint`|YES|`∅`|MUL||内部审批实例|
|32|`generated_contract_change_id`|`bigint`|YES|`∅`|MUL||业主核定后生成的正式合同变更|
|33|`version`|`int`|NO|`0`|||未定义（需要人工确认）|

## var_order_item

- 表注释：变更签证明细表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||变更明细ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`var_order_id`|`bigint`|NO|`∅`|MUL||变更签证ID|
|4|`item_name`|`varchar(200)`|YES|`∅`|||清单项名称|
|5|`unit`|`varchar(20)`|YES|`∅`|||计量单位|
|6|`quantity`|`decimal(18,4)`|YES|`∅`|||数量|
|7|`unit_price`|`decimal(18,4)`|YES|`∅`|||单价|
|8|`amount`|`decimal(18,2)`|YES|`∅`|||金额|
|9|`cost_subject_id`|`bigint`|YES|`∅`|||成本科目ID|
|10|`created_by`|`bigint`|YES|`∅`|||创建人|
|11|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|12|`updated_by`|`bigint`|YES|`∅`|||更新人|
|13|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|14|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|15|`remark`|`varchar(500)`|YES|`∅`|||备注|
|16|`claim_unit_price`|`decimal(18,4)`|YES|`∅`|||对业主申报单价|
|17|`claim_amount`|`decimal(18,2)`|YES|`∅`|||对业主申报金额|

## variation_owner_submission

- 表注释：变更签证对业主申报版本
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`project_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`contract_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`var_order_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|6|`revision_no`|`int`|NO|`∅`|||未定义（需要人工确认）|
|7|`submission_code`|`varchar(64)`|NO|`∅`|||未定义（需要人工确认）|
|8|`external_document_no`|`varchar(128)`|NO|`∅`|||未定义（需要人工确认）|
|9|`submitted_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`status`|`varchar(32)`|NO|`SUBMITTED`|||未定义（需要人工确认）|
|11|`submitted_at`|`datetime`|NO|`∅`|||未定义（需要人工确认）|
|12|`submitted_by`|`bigint`|NO|`∅`|||未定义（需要人工确认）|
|13|`response_document_no`|`varchar(128)`|YES|`∅`|||未定义（需要人工确认）|
|14|`response_comment`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|15|`confirmed_amount`|`decimal(18,2)`|NO|`0.00`|||未定义（需要人工确认）|
|16|`reviewed_at`|`datetime`|YES|`∅`|||未定义（需要人工确认）|
|17|`reviewed_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|18|`generated_contract_change_id`|`bigint`|YES|`∅`|MUL||未定义（需要人工确认）|
|19|`version`|`int`|NO|`0`|||未定义（需要人工确认）|
|20|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|21|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|22|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|23|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|
|24|`deleted_flag`|`tinyint`|NO|`0`|||未定义（需要人工确认）|
|25|`remark`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|

## variation_owner_submission_item

- 表注释：变更签证业主申报及核定明细快照
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||未定义（需要人工确认）|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||未定义（需要人工确认）|
|3|`submission_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|4|`var_order_item_id`|`bigint`|NO|`∅`|MUL||未定义（需要人工确认）|
|5|`item_name`|`varchar(200)`|NO|`∅`|||未定义（需要人工确认）|
|6|`unit`|`varchar(20)`|YES|`∅`|||未定义（需要人工确认）|
|7|`quantity`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|8|`claimed_unit_price`|`decimal(18,4)`|NO|`∅`|||未定义（需要人工确认）|
|9|`claimed_amount`|`decimal(18,2)`|NO|`∅`|||未定义（需要人工确认）|
|10|`confirmed_amount`|`decimal(18,2)`|YES|`∅`|||未定义（需要人工确认）|
|11|`reduction_reason`|`varchar(500)`|YES|`∅`|||未定义（需要人工确认）|
|12|`created_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|13|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|未定义（需要人工确认）|
|14|`updated_by`|`bigint`|YES|`∅`|||未定义（需要人工确认）|
|15|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|未定义（需要人工确认）|

## wf_cc

- 表注释：审批抄送表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||抄送ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`instance_id`|`bigint`|NO|`∅`|MUL||审批实例ID，关联wf_instance.id|
|4|`cc_user_id`|`bigint`|NO|`∅`|||抄送人用户ID|
|5|`cc_user_name`|`varchar(100)`|NO|`∅`|||抄送人姓名（冗余，避免联表）|
|6|`business_type`|`varchar(100)`|YES|`∅`|||业务类型|
|7|`business_id`|`bigint`|YES|`∅`|||业务ID|
|8|`title`|`varchar(500)`|YES|`∅`|||审批标题（冗余）|
|9|`is_read`|`tinyint`|NO|`0`|||已读标记：0未读，1已读|
|10|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|抄送时间|

## wf_idempotency

- 表注释：审批幂等表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||幂等记录ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`user_id`|`bigint`|NO|`∅`|||用户ID|
|4|`idempotency_key`|`varchar(128)`|NO|`∅`|||幂等键|
|5|`business_type`|`varchar(50)`|YES|`∅`|||业务类型|
|6|`business_id`|`bigint`|YES|`∅`|||业务ID|
|7|`request_hash`|`varchar(128)`|YES|`∅`|||请求摘要|
|8|`response_json`|`json`|YES|`∅`|||首次响应结果|
|9|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|10|`expired_at`|`datetime`|YES|`∅`|MUL||过期时间|

## wf_instance

- 表注释：审批实例表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||审批实例ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`template_id`|`bigint`|NO|`∅`|||审批模板ID|
|4|`business_type`|`varchar(50)`|NO|`∅`|MUL||业务类型|
|5|`business_id`|`bigint`|NO|`∅`|||业务单据ID|
|6|`project_id`|`bigint`|YES|`∅`|MUL||项目ID|
|7|`contract_id`|`bigint`|YES|`∅`|||合同ID|
|8|`title`|`varchar(300)`|NO|`∅`|||审批标题|
|9|`amount`|`decimal(18,2)`|YES|`∅`|||审批金额|
|10|`instance_status`|`varchar(50)`|NO|`RUNNING`|MUL||实例状态|
|11|`current_round`|`int`|NO|`1`|||当前审批轮次|
|12|`resubmit_count`|`int`|NO|`0`|||重新提交次数|
|13|`business_revision`|`int`|NO|`1`|||业务版本|
|14|`initiator_id`|`bigint`|NO|`∅`|MUL||发起人ID|
|15|`business_summary`|`text`|YES|`∅`|||业务摘要|
|16|`variables`|`json`|YES|`∅`|||流程变量|
|17|`started_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|发起时间|
|18|`ended_at`|`datetime`|YES|`∅`|||结束时间|
|19|`created_by`|`bigint`|YES|`∅`|||创建人|
|20|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|21|`updated_by`|`bigint`|YES|`∅`|||更新人|
|22|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|23|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|24|`remark`|`varchar(500)`|YES|`∅`|||备注|

## wf_node_instance

- 表注释：审批节点实例表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||节点实例ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`instance_id`|`bigint`|NO|`∅`|MUL||审批实例ID|
|4|`template_node_id`|`bigint`|YES|`∅`|||模板节点ID|
|5|`node_code`|`varchar(64)`|NO|`∅`|||节点编码|
|6|`node_name`|`varchar(200)`|NO|`∅`|||节点名称|
|7|`node_order`|`int`|NO|`∅`|||节点顺序|
|8|`approve_mode`|`varchar(50)`|NO|`∅`|||审批模式|
|9|`node_status`|`varchar(50)`|NO|`WAITING`|MUL||节点状态|
|10|`round_no`|`int`|NO|`1`|||审批轮次|
|11|`pass_rule_json`|`json`|YES|`∅`|||节点通过规则|
|12|`reject_rule_json`|`json`|YES|`∅`|||节点驳回规则|
|13|`started_at`|`datetime`|YES|`∅`|||开始时间|
|14|`ended_at`|`datetime`|YES|`∅`|||结束时间|
|15|`created_by`|`bigint`|YES|`∅`|||创建人|
|16|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|17|`updated_by`|`bigint`|YES|`∅`|||更新人|
|18|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|19|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|20|`remark`|`varchar(500)`|YES|`∅`|||备注|

## wf_record

- 表注释：审批记录表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||审批记录ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`instance_id`|`bigint`|NO|`∅`|MUL||审批实例ID|
|4|`node_instance_id`|`bigint`|YES|`∅`|MUL||节点实例ID，提交/撤回等可为空|
|5|`task_id`|`bigint`|YES|`∅`|MUL||审批任务ID|
|6|`round_no`|`int`|NO|`1`|||审批轮次|
|7|`business_type`|`varchar(50)`|NO|`∅`|MUL||业务类型|
|8|`business_id`|`bigint`|NO|`∅`|||业务单据ID|
|9|`node_code`|`varchar(64)`|YES|`∅`|||节点编码|
|10|`node_name`|`varchar(200)`|YES|`∅`|||节点名称|
|11|`action_type`|`varchar(50)`|NO|`∅`|||动作类型|
|12|`action_name`|`varchar(100)`|NO|`∅`|||动作名称|
|13|`operator_id`|`bigint`|NO|`∅`|||操作人ID|
|14|`operator_name`|`varchar(100)`|YES|`∅`|||操作人姓名|
|15|`comment`|`varchar(1000)`|YES|`∅`|||审批意见|
|16|`record_status`|`varchar(50)`|NO|`EFFECTIVE`|||记录状态|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|

## wf_task

- 表注释：审批任务表
- information_schema 估算行数：0

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||审批任务ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`instance_id`|`bigint`|NO|`∅`|MUL||审批实例ID|
|4|`node_instance_id`|`bigint`|NO|`∅`|MUL||节点实例ID|
|5|`business_type`|`varchar(50)`|NO|`∅`|MUL||业务类型|
|6|`business_id`|`bigint`|NO|`∅`|||业务单据ID|
|7|`approver_id`|`bigint`|NO|`∅`|MUL||审批人ID|
|8|`approver_name`|`varchar(100)`|YES|`∅`|||审批人姓名|
|9|`task_status`|`varchar(50)`|NO|`PENDING`|||任务状态|
|10|`round_no`|`int`|NO|`1`|||审批轮次|
|11|`task_version`|`int`|NO|`1`|||乐观锁版本|
|12|`received_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|接收时间|
|13|`handled_at`|`datetime`|YES|`∅`|||处理时间|
|14|`action_type`|`varchar(50)`|YES|`∅`|||处理动作|
|15|`comment`|`varchar(1000)`|YES|`∅`|||审批意见|
|16|`created_by`|`bigint`|YES|`∅`|||创建人|
|17|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|18|`updated_by`|`bigint`|YES|`∅`|||更新人|
|19|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|20|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|21|`remark`|`varchar(500)`|YES|`∅`|||备注|

## wf_template

- 表注释：审批模板表
- information_schema 估算行数：23

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||审批模板ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|MUL||租户ID|
|3|`template_code`|`varchar(64)`|NO|`∅`|||模板编码|
|4|`template_name`|`varchar(200)`|NO|`∅`|||模板名称|
|5|`business_type`|`varchar(50)`|NO|`∅`|MUL||业务类型|
|6|`enabled`|`tinyint`|NO|`1`|||是否启用：0否，1是|
|7|`amount_min`|`decimal(18,2)`|YES|`∅`|||适用金额下限|
|8|`amount_max`|`decimal(18,2)`|YES|`∅`|||适用金额上限|
|9|`condition_rule`|`json`|YES|`∅`|||流程匹配条件|
|10|`form_schema`|`json`|YES|`∅`|||动态表单Schema|
|11|`created_by`|`bigint`|YES|`∅`|||创建人|
|12|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|13|`updated_by`|`bigint`|YES|`∅`|||更新人|
|14|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|15|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|16|`remark`|`varchar(500)`|YES|`∅`|||备注|

## wf_template_node

- 表注释：审批模板节点表
- information_schema 估算行数：65

|序号|字段|类型|可空|默认值|键|附加属性|注释|
|---:|---|---|---|---|---|---|---|
|1|`id`|`bigint`|NO|`∅`|PRI||模板节点ID，雪花ID|
|2|`tenant_id`|`bigint`|NO|`0`|||租户ID|
|3|`template_id`|`bigint`|NO|`∅`|MUL||模板ID|
|4|`node_code`|`varchar(64)`|NO|`∅`|||节点编码|
|5|`node_name`|`varchar(200)`|NO|`∅`|||节点名称|
|6|`node_order`|`int`|NO|`∅`|||节点顺序|
|7|`node_type`|`varchar(50)`|NO|`APPROVAL`|||节点类型|
|8|`approve_mode`|`varchar(50)`|NO|`SEQUENTIAL`|||审批模式|
|9|`approver_config`|`json`|NO|`∅`|||审批人配置|
|10|`pass_rule_json`|`json`|YES|`∅`|||节点通过规则|
|11|`reject_rule_json`|`json`|YES|`∅`|||节点驳回规则|
|12|`condition_rule`|`json`|YES|`∅`|||节点执行条件|
|13|`node_config`|`json`|YES|`∅`|||节点扩展配置|
|14|`allow_transfer`|`tinyint`|NO|`1`|||是否允许转办：0否，1是|
|15|`allow_add_sign`|`tinyint`|NO|`1`|||是否允许加签：0否，1是|
|16|`timeout_hours`|`int`|YES|`∅`|||超时时间（小时）|
|17|`created_by`|`bigint`|YES|`∅`|||创建人|
|18|`created_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED|创建时间|
|19|`updated_by`|`bigint`|YES|`∅`|||更新人|
|20|`updated_at`|`datetime`|NO|`CURRENT_TIMESTAMP`||DEFAULT_GENERATED on update CURRENT_TIMESTAMP|更新时间|
|21|`deleted_flag`|`tinyint`|NO|`0`|||逻辑删除：0否，1是|
|22|`remark`|`varchar(500)`|YES|`∅`|||备注|

