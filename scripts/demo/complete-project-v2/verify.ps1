param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dev', 'test', 'demo')]
    [string]$Environment,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$Database,

    [string]$MySqlContainer = 'cgc-pms-mysql-dev'
)

$ErrorActionPreference = 'Stop'
$portBinding = docker port $MySqlContainer 3306/tcp 2>$null
if ($LASTEXITCODE -ne 0 -or -not (($portBinding -join "`n") -match '127\.0\.0\.1:')) {
    throw 'DEMO_VERIFY_LOCALHOST_BINDING_REQUIRED'
}

$sql = @'
SELECT 'project',COUNT(*) FROM pm_project WHERE tenant_id=0 AND id=520000000000000001 AND deleted_flag=0
UNION ALL SELECT 'partner',COUNT(*) FROM md_partner WHERE tenant_id=0 AND id IN (520000000000000101,520000000000000102,520000000000000103,520000000000006001,520000000000006002,520000000000009101,520000000000009102) AND deleted_flag=0
UNION ALL SELECT 'material',COUNT(*) FROM md_material WHERE tenant_id=0 AND material_code='M52-MAT-STEEL' AND deleted_flag=0
UNION ALL SELECT 'contract',COUNT(*) FROM ct_contract WHERE tenant_id=0 AND project_id=520000000000000001 AND deleted_flag=0
UNION ALL SELECT 'bid_transfer',COUNT(*) FROM bid_cost_target_transfer WHERE tenant_id=0 AND project_id=520000000000000001 AND status='POSTED'
UNION ALL SELECT 'target',COUNT(*) FROM cost_target WHERE tenant_id=0 AND project_id=520000000000000001 AND is_active=1 AND deleted_flag=0
UNION ALL SELECT 'purchase_request',COUNT(*) FROM mat_purchase_request WHERE tenant_id=0 AND id=520000000000001101 AND deleted_flag=0
UNION ALL SELECT 'purchase_order',COUNT(*) FROM mat_purchase_order WHERE tenant_id=0 AND id=520000000000001201 AND deleted_flag=0
UNION ALL SELECT 'receipt',COUNT(*) FROM mat_receipt WHERE tenant_id=0 AND id=520000000000001301 AND deleted_flag=0
UNION ALL SELECT 'requisition',COUNT(*) FROM mat_requisition WHERE tenant_id=0 AND id=520000000000001501 AND deleted_flag=0
UNION ALL SELECT 'stock',COUNT(*) FROM mat_stock WHERE tenant_id=0 AND warehouse_id=520000000000000301 AND material_id=520000000000000201 AND deleted_flag=0
UNION ALL SELECT 'sub_measure',COUNT(*) FROM sub_measure WHERE tenant_id=0 AND id=520000000000002101 AND deleted_flag=0
UNION ALL SELECT 'settlement',COUNT(*) FROM stl_settlement WHERE tenant_id=0 AND id=520000000000002201 AND deleted_flag=0
UNION ALL SELECT 'pay_application',COUNT(*) FROM pay_application WHERE tenant_id=0 AND id=520000000000002401 AND deleted_flag=0
UNION ALL SELECT 'pay_record',COUNT(*) FROM pay_record WHERE tenant_id=0 AND external_txn_no='M52-PAY-TXN-001' AND deleted_flag=0
UNION ALL SELECT 'expense',COUNT(*) FROM expense_application WHERE tenant_id=0 AND id=520000000000002501 AND deleted_flag=0
UNION ALL SELECT 'revenue',COUNT(*) FROM contract_revenue WHERE tenant_id=0 AND id=520000000000002601 AND deleted_flag=0
UNION ALL SELECT 'receivable',COUNT(*) FROM account_receivable WHERE tenant_id=0 AND receivable_code='M52-AR-001' AND deleted_flag=0
UNION ALL SELECT 'collection',COUNT(*) FROM collection_record WHERE tenant_id=0 AND collection_code='M52-COLLECTION-001' AND deleted_flag=0
UNION ALL SELECT 'quality_issue',COUNT(*) FROM qs_issue WHERE tenant_id=0 AND issue_code='M52-QS-ISSUE-001' AND status='CLOSED' AND deleted_flag=0
UNION ALL SELECT 'rectification',COUNT(*) FROM qs_rectification WHERE tenant_id=0 AND issue_id=520000000000003203 AND status='PASSED' AND deleted_flag=0
UNION ALL SELECT 'progress',COUNT(*) FROM project_progress_snapshot WHERE tenant_id=0 AND project_id=520000000000000001 AND status='COMPLETED'
UNION ALL SELECT 'workflow_instance',COUNT(*) FROM wf_instance WHERE tenant_id=0 AND id=520000000000000901 AND instance_status='APPROVED' AND deleted_flag=0
UNION ALL SELECT 'workflow_task',COUNT(*) FROM wf_task WHERE tenant_id=0 AND instance_id=520000000000000901 AND task_status='APPROVED' AND deleted_flag=0
UNION ALL SELECT 'workflow_record',COUNT(*) FROM wf_record WHERE tenant_id=0 AND instance_id=520000000000000901 AND record_status='EFFECTIVE' AND deleted_flag=0
UNION ALL SELECT 'alert',COUNT(*) FROM alert_log WHERE tenant_id=0 AND id=520000000000003401 AND process_status='RESOLVED' AND deleted_flag=0
UNION ALL SELECT 'closeout',COUNT(*) FROM project_closeout WHERE tenant_id=0 AND project_id=520000000000000001 AND status='CLOSED' AND deleted_flag=0
UNION ALL SELECT 'completed_stage',COUNT(*) FROM sys_bootstrap_state WHERE bootstrap_key LIKE 'DEMO_CGC_V2_%' AND status='COMPLETED'
UNION ALL SELECT 'cash_cycle',COUNT(*) FROM cash_forecast_cycle WHERE tenant_id=0 AND cycle_code LIKE 'M52-CASH-%'
UNION ALL SELECT 'pay_invoice',COUNT(*) FROM pay_invoice WHERE tenant_id=0 AND invoice_no LIKE 'M52-PINV-%' AND deleted_flag=0
UNION ALL SELECT 'sales_invoice',COUNT(*) FROM sales_invoice WHERE tenant_id=0 AND invoice_no LIKE 'M52-SINV-%' AND deleted_flag=0
UNION ALL SELECT 'finance_period',COUNT(*) FROM finance_period WHERE tenant_id=0 AND id BETWEEN 520000000000004501 AND 520000000000004503
UNION ALL SELECT 'accounting_entry',COUNT(*) FROM accounting_entry WHERE tenant_id=0 AND entry_code LIKE 'M52-ACC-%' AND deleted_flag=0
UNION ALL SELECT 'accounting_balance_delta',COALESCE(SUM(ABS(total_debit-total_credit)),0) FROM accounting_entry WHERE tenant_id=0 AND entry_code LIKE 'M52-ACC-%' AND deleted_flag=0
UNION ALL SELECT 'invoice_allocation_delta',ABS(
  COALESCE((SELECT invoice_amount FROM pay_invoice WHERE id=520000000000004101),0)
  -COALESCE((SELECT SUM(allocated_amount) FROM invoice_payment_allocation WHERE invoice_id=520000000000004101),0))
UNION ALL SELECT 'measurement_period',COUNT(*) FROM measurement_period WHERE tenant_id=0 AND period_code LIKE 'M52-MEASURE-%' AND deleted_flag=0
UNION ALL SELECT 'production_measurement',COUNT(*) FROM production_measurement WHERE tenant_id=0 AND measure_code LIKE 'M52-PM-%' AND deleted_flag=0
UNION ALL SELECT 'owner_measurement_submission',COUNT(*) FROM owner_measurement_submission WHERE tenant_id=0 AND submission_code LIKE 'M52-OWNER-SUB-%' AND deleted_flag=0
UNION ALL SELECT 'site_daily_log',COUNT(*) FROM site_daily_log WHERE tenant_id=0 AND id BETWEEN 520000000000005501 AND 520000000000005503 AND deleted_flag=0
UNION ALL SELECT 'measurement_amount_delta',ABS(
  COALESCE((SELECT current_reported_amount FROM production_measurement WHERE id=520000000000005301),0)
  -COALESCE((SELECT SUM(current_reported_amount) FROM production_measurement_line WHERE measurement_id=520000000000005301),0))
UNION ALL SELECT 'sourcing_event',COUNT(*) FROM sp_sourcing_event WHERE tenant_id=0 AND sourcing_code LIKE 'M52-SOURCE-%' AND deleted_flag=0
UNION ALL SELECT 'supplier_quote',COUNT(*) FROM sp_supplier_quote WHERE tenant_id=0 AND quote_code LIKE 'M52-QUOTE-%' AND deleted_flag=0
UNION ALL SELECT 'performance_evaluation',COUNT(*) FROM sp_performance_evaluation WHERE tenant_id=0 AND evaluation_code LIKE 'M52-PERF-%' AND deleted_flag=0
UNION ALL SELECT 'technical_scheme',COUNT(*) FROM technical_scheme WHERE tenant_id=0 AND scheme_code LIKE 'M52-TECH-SCHEME-%' AND deleted_flag=0
UNION ALL SELECT 'technical_drawing',COUNT(*) FROM tech_drawing WHERE tenant_id=0 AND drawing_code LIKE 'M52-DRAW-%' AND deleted_flag=0
UNION ALL SELECT 'technical_rfi',COUNT(*) FROM tech_rfi WHERE tenant_id=0 AND rfi_code LIKE 'M52-RFI-%' AND deleted_flag=0
UNION ALL SELECT 'technical_archive',COUNT(*) FROM tech_acceptance_archive WHERE tenant_id=0 AND archive_code LIKE 'M52-TECH-ARCHIVE-%' AND deleted_flag=0
UNION ALL SELECT 'variation_order',COUNT(*) FROM var_order WHERE tenant_id=0 AND id BETWEEN 520000000000007101 AND 520000000000007103 AND deleted_flag=0
UNION ALL SELECT 'variation_submission',COUNT(*) FROM variation_owner_submission WHERE tenant_id=0 AND submission_code LIKE 'M52-VAR-SUB-%' AND deleted_flag=0
UNION ALL SELECT 'contract_change',COUNT(*) FROM ct_contract_change WHERE tenant_id=0 AND id=520000000000007201 AND deleted_flag=0
UNION ALL SELECT 'closeout_detail',(
  (SELECT COUNT(*) FROM closeout_section_acceptance WHERE closeout_id=520000000000003501 AND deleted_flag=0)+
  (SELECT COUNT(*) FROM closeout_final_acceptance WHERE closeout_id=520000000000003501 AND deleted_flag=0)+
  (SELECT COUNT(*) FROM closeout_warranty WHERE closeout_id=520000000000003501 AND deleted_flag=0)+
  (SELECT COUNT(*) FROM closeout_defect WHERE closeout_id=520000000000003501 AND deleted_flag=0)+
  (SELECT COUNT(*) FROM closeout_archive_transfer WHERE closeout_id=520000000000003501 AND deleted_flag=0))
UNION ALL SELECT 'organization_position',COUNT(*) FROM org_position WHERE tenant_id=0 AND position_code LIKE 'M52-POS-%' AND deleted_flag=0
UNION ALL SELECT 'project_member',COUNT(*) FROM pm_project_member WHERE tenant_id=0 AND project_id=520000000000000001 AND deleted_flag=0
UNION ALL SELECT 'user_preference',COUNT(*) FROM sys_user_preference WHERE tenant_id=0 AND user_id=(SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0)
UNION ALL SELECT 'cost_forecast',COUNT(*) FROM cost_forecast WHERE tenant_id=0 AND forecast_code='M52-COST-FC-001' AND deleted_flag=0
UNION ALL SELECT 'cost_corrective_action',COUNT(*) FROM cost_corrective_action WHERE tenant_id=0 AND action_code='M52-CORRECT-001' AND deleted_flag=0
UNION ALL SELECT 'finance_reconciliation_run',COUNT(*) FROM finance_reconciliation_run WHERE tenant_id=0 AND id=520000000000008311
UNION ALL SELECT 'finance_import_batch',COUNT(*) FROM finance_import_batch WHERE tenant_id=0 AND id=520000000000008321 AND valid_rows=1 AND invalid_rows=1
UNION ALL SELECT 'inventory_exception',COUNT(*) FROM mat_quality_disposition WHERE tenant_id=0 AND id=520000000000008403 AND status='OPEN' AND deleted_flag=0
UNION ALL SELECT 'material_return_reversal',COUNT(*) FROM mat_material_return WHERE tenant_id=0 AND id=520000000000008421 AND status='REVERSED' AND deleted_flag=0
UNION ALL SELECT 'document_template',COUNT(*) FROM biz_document_template WHERE tenant_id=0 AND template_code='M52-PAYMENT-PDF' AND enabled=1 AND deleted_flag=0
UNION ALL SELECT 'document_generation',COUNT(*) FROM biz_document_generation WHERE tenant_id=0 AND id BETWEEN 520000000000008511 AND 520000000000008512 AND deleted_flag=0
UNION ALL SELECT 'demo_user',COUNT(*) FROM sys_user WHERE tenant_id=0 AND username='demo.manager' AND status='ENABLE' AND deleted_flag=0
UNION ALL SELECT 'role_test_account',COUNT(*) FROM sys_user WHERE tenant_id=0 AND username IN ('admin','demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance') AND status='ENABLE' AND deleted_flag=0
UNION ALL SELECT 'role_alert_permission',COUNT(DISTINCT u.username) FROM sys_user u
  JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
  JOIN sys_role_menu rm ON rm.tenant_id=ur.tenant_id AND rm.role_id=ur.role_id
  JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
  WHERE u.tenant_id=0 AND u.username IN ('admin','demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance')
    AND u.status='ENABLE' AND u.deleted_flag=0 AND m.perms='alert:view' AND m.status='ENABLE' AND m.deleted_flag=0
UNION ALL SELECT 'role_alert_edit_permission',COUNT(DISTINCT u.username) FROM sys_user u
  JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
  JOIN sys_role_menu rm ON rm.tenant_id=ur.tenant_id AND rm.role_id=ur.role_id
  JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
  WHERE u.tenant_id=0 AND u.username IN ('admin','demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance')
    AND u.status='ENABLE' AND u.deleted_flag=0 AND m.perms='alert:edit' AND m.status='ENABLE' AND m.deleted_flag=0
UNION ALL SELECT 'role_alert_project_members',COUNT(DISTINCT u.username) FROM sys_user u
  JOIN pm_project_member pm ON pm.tenant_id=u.tenant_id AND pm.user_id=u.id
  WHERE u.tenant_id=0 AND u.username IN ('demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance')
    AND pm.project_id=520000000000009002 AND pm.status='ACTIVE' AND pm.deleted_flag=0
UNION ALL SELECT 'role_workflow_status_instances',COUNT(*) FROM wf_instance WHERE tenant_id=0 AND id BETWEEN 520000000000009700 AND 520000000000009739 AND deleted_flag=0 AND remark='M2八角色审批状态矩阵'
UNION ALL SELECT 'role_workflow_status_pairs',COUNT(DISTINCT CONCAT(initiator_id,':',instance_status)) FROM wf_instance WHERE tenant_id=0 AND id BETWEEN 520000000000009700 AND 520000000000009739 AND deleted_flag=0
UNION ALL SELECT 'role_workflow_status_todos',COUNT(*) FROM wf_task t JOIN wf_instance i ON i.id=t.instance_id AND i.tenant_id=t.tenant_id WHERE t.tenant_id=0 AND t.id BETWEEN 520000000000009780 AND 520000000000009819 AND t.task_status='PENDING' AND i.instance_status='RUNNING' AND t.deleted_flag=0 AND i.deleted_flag=0
UNION ALL SELECT 'role_workflow_status_done',COUNT(*) FROM wf_record WHERE tenant_id=0 AND id BETWEEN 520000000000009820 AND 520000000000009859 AND action_type IN ('APPROVE','REJECT','TRANSFER','ADD_SIGN') AND record_status='EFFECTIVE' AND deleted_flag=0
UNION ALL SELECT 'role_workflow_status_cc',COUNT(*) FROM wf_cc WHERE tenant_id=0 AND id BETWEEN 520000000000009860 AND 520000000000009899
UNION ALL SELECT 'role_workflow_business_types',COUNT(DISTINCT business_type) FROM wf_instance WHERE tenant_id=0 AND id BETWEEN 520000000000009700 AND 520000000000009739 AND deleted_flag=0
UNION ALL SELECT 'role_workflow_action_permissions',COUNT(DISTINCT CONCAT(u.username,':',m.perms)) FROM sys_user u
  JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
  JOIN sys_role_menu rm ON rm.tenant_id=ur.tenant_id AND rm.role_id=ur.role_id
  JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
  WHERE u.tenant_id=0 AND u.username IN ('admin','demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance')
    AND u.status='ENABLE' AND u.deleted_flag=0
    AND m.perms IN ('workflow:approve','workflow:reject','workflow:transfer','workflow:add-sign','workflow:withdraw','workflow:resubmit')
    AND m.status='ENABLE' AND m.deleted_flag=0
UNION ALL SELECT 'role_workflow_orphans',(
  (SELECT COUNT(*) FROM wf_node_instance n LEFT JOIN wf_instance i ON i.id=n.instance_id AND i.tenant_id=n.tenant_id WHERE n.tenant_id=0 AND n.id BETWEEN 520000000000009740 AND 520000000000009779 AND (i.id IS NULL OR i.deleted_flag<>0))+
  (SELECT COUNT(*) FROM wf_task t LEFT JOIN wf_instance i ON i.id=t.instance_id AND i.tenant_id=t.tenant_id LEFT JOIN wf_node_instance n ON n.id=t.node_instance_id AND n.tenant_id=t.tenant_id WHERE t.tenant_id=0 AND t.id BETWEEN 520000000000009780 AND 520000000000009819 AND (i.id IS NULL OR n.id IS NULL))+
  (SELECT COUNT(*) FROM wf_record r LEFT JOIN wf_instance i ON i.id=r.instance_id AND i.tenant_id=r.tenant_id WHERE r.tenant_id=0 AND r.id BETWEEN 520000000000009820 AND 520000000000009859 AND i.id IS NULL)+
  (SELECT COUNT(*) FROM wf_cc c LEFT JOIN wf_instance i ON i.id=c.instance_id AND i.tenant_id=c.tenant_id WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000009860 AND 520000000000009899 AND i.id IS NULL))
UNION ALL SELECT 'dashboard_trend_month',COUNT(DISTINCT DATE_FORMAT(summary_date,'%Y-%m')) FROM cost_summary WHERE tenant_id=0 AND project_id=520000000000009002 AND cost_subject_id IS NULL AND deleted_flag=0
UNION ALL SELECT 'finance_demo_budget',COUNT(*) FROM project_budget WHERE tenant_id=0 AND project_id=520000000000009002 AND status='ACTIVE' AND active_flag=1 AND deleted_flag=0
UNION ALL SELECT 'finance_demo_pay_application',COUNT(*) FROM pay_application WHERE tenant_id=0 AND id BETWEEN 520000000000009412 AND 520000000000009415 AND deleted_flag=0
UNION ALL SELECT 'finance_demo_pay_record',COUNT(*) FROM pay_record WHERE tenant_id=0 AND id BETWEEN 520000000000009421 AND 520000000000009424 AND deleted_flag=0
UNION ALL SELECT 'finance_demo_paid',COALESCE(SUM(pay_amount),0) FROM pay_record WHERE tenant_id=0 AND id BETWEEN 520000000000009421 AND 520000000000009424 AND pay_status='SUCCESS' AND deleted_flag=0
UNION ALL SELECT 'finance_demo_processing',COALESCE(SUM(pay_amount),0) FROM pay_record WHERE tenant_id=0 AND id BETWEEN 520000000000009421 AND 520000000000009424 AND pay_status='PROCESSING' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_contract',COUNT(*) FROM ct_contract WHERE tenant_id=0 AND id=520000000000009501 AND project_id=520000000000009002 AND contract_status='PERFORMING' AND end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(),INTERVAL 30 DAY) AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_variation',COUNT(*) FROM var_order WHERE tenant_id=0 AND id=520000000000009502 AND project_id=520000000000009002 AND approval_status='APPROVED' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_measure',COUNT(*) FROM sub_measure WHERE tenant_id=0 AND id=520000000000009503 AND project_id=520000000000009002 AND approval_status='APPROVED' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_settlement',COUNT(*) FROM stl_settlement WHERE tenant_id=0 AND id=520000000000009504 AND project_id=520000000000009002 AND settlement_status='FINALIZED' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_purchase',COUNT(*) FROM mat_purchase_order WHERE tenant_id=0 AND id=520000000000009513 AND project_id=520000000000009002 AND order_status='IN_PROGRESS' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_receipt',COUNT(*) FROM mat_receipt WHERE tenant_id=0 AND id=520000000000009517 AND project_id=520000000000009002 AND approval_status='PENDING' AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_requisition',COUNT(*) FROM mat_requisition WHERE tenant_id=0 AND id=520000000000009519 AND project_id=520000000000009002 AND stock_out_flag=0 AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_low_stock',COUNT(*) FROM mat_stock WHERE tenant_id=0 AND id=520000000000009516 AND available_qty=0 AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_tech',COUNT(*) FROM tech_item WHERE tenant_id=0 AND id BETWEEN 520000000000009531 AND 520000000000009533 AND project_id=520000000000009002 AND deleted_flag=0
UNION ALL SELECT 'role_dashboard_task',COUNT(*) FROM wf_task t JOIN wf_instance i ON i.id=t.instance_id AND i.tenant_id=t.tenant_id WHERE t.tenant_id=0 AND t.id=520000000000009543 AND t.task_status='PENDING' AND i.project_id=520000000000009002 AND t.deleted_flag=0 AND i.deleted_flag=0
UNION ALL SELECT 'role_dashboard_lagging',COUNT(*) FROM pm_project WHERE tenant_id=0 AND id=520000000000009002 AND status='ACTIVE' AND planned_end_date<CURDATE() AND deleted_flag=0
UNION ALL SELECT 'risk_level_contracts',COUNT(*) FROM ct_contract WHERE tenant_id=0 AND id BETWEEN 520000000000009601 AND 520000000000009602 AND project_id=520000000000009002 AND deleted_flag=0
UNION ALL SELECT 'risk_level_project_task',COUNT(*) FROM wf_task WHERE tenant_id=0 AND id=520000000000009603 AND task_status='PENDING' AND deleted_flag=0
UNION ALL SELECT 'risk_level_purchase_order',COUNT(*) FROM mat_purchase_order WHERE tenant_id=0 AND id=520000000000009604 AND order_status='IN_PROGRESS' AND delivery_date<CURDATE() AND deleted_flag=0
UNION ALL SELECT 'risk_level_measures',COUNT(DISTINCT status) FROM sub_measure WHERE tenant_id=0 AND id IN (520000000000009503,520000000000009605,520000000000009606,520000000000009607) AND deleted_flag=0
UNION ALL SELECT 'risk_level_tech_items',COUNT(*) FROM tech_item WHERE tenant_id=0 AND id BETWEEN 520000000000009608 AND 520000000000009609 AND deleted_flag=0
UNION ALL SELECT 'risk_level_finance_records',COUNT(*) FROM pay_record WHERE tenant_id=0 AND id IN (520000000000009611,520000000000009612,520000000000009613,520000000000009618) AND deleted_flag=0
UNION ALL SELECT 'risk_level_alert_severities',COUNT(DISTINCT severity) FROM alert_log WHERE tenant_id=0 AND id BETWEEN 520000000000009621 AND 520000000000009624 AND is_read=0 AND deleted_flag=0
UNION ALL SELECT 'role_quality_safety_alert',COUNT(*) FROM alert_log WHERE tenant_id=0 AND id=520000000000009625 AND alert_domain='QUALITY_SAFETY' AND process_status='OPEN' AND deleted_flag=0
UNION ALL SELECT 'cost_breakdown_rows',COUNT(*) FROM cost_summary WHERE tenant_id=0 AND id BETWEEN 520000000000009471 AND 520000000000009475 AND project_id=520000000000009002 AND deleted_flag=0
UNION ALL SELECT 'cost_breakdown_roots',COUNT(*) FROM cost_summary s JOIN cost_subject c ON c.id=s.cost_subject_id AND c.tenant_id=s.tenant_id WHERE s.tenant_id=0 AND s.id BETWEEN 520000000000009471 AND 520000000000009475 AND c.level=1 AND c.status='ENABLE' AND c.deleted_flag=0 AND s.deleted_flag=0
UNION ALL SELECT 'cost_breakdown_children',COUNT(*) FROM cost_summary s JOIN cost_subject c ON c.id=s.cost_subject_id AND c.tenant_id=s.tenant_id WHERE s.tenant_id=0 AND s.id BETWEEN 520000000000009471 AND 520000000000009475 AND c.level=2 AND c.parent_id=900001 AND c.status='ENABLE' AND c.deleted_flag=0 AND s.deleted_flag=0
UNION ALL SELECT 'cost_breakdown_target_delta',ABS(
  COALESCE((SELECT target_cost FROM cost_summary WHERE id=520000000000009471 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(target_cost) FROM cost_summary WHERE id BETWEEN 520000000000009472 AND 520000000000009475 AND deleted_flag=0),0))
UNION ALL SELECT 'cost_breakdown_actual_delta',ABS(
  COALESCE((SELECT actual_cost FROM cost_summary WHERE id=520000000000009471 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(actual_cost) FROM cost_summary WHERE id BETWEEN 520000000000009472 AND 520000000000009475 AND deleted_flag=0),0))
UNION ALL SELECT 'cost_breakdown_dynamic_delta',ABS(
  COALESCE((SELECT dynamic_cost FROM cost_summary WHERE id=520000000000009471 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(dynamic_cost) FROM cost_summary WHERE id BETWEEN 520000000000009472 AND 520000000000009475 AND deleted_flag=0),0))
UNION ALL SELECT 'cost_breakdown_deviation_delta',ABS(
  COALESCE((SELECT cost_deviation FROM cost_summary WHERE id=520000000000009471 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(cost_deviation) FROM cost_summary WHERE id BETWEEN 520000000000009472 AND 520000000000009475 AND deleted_flag=0),0))
UNION ALL SELECT 'cost_breakdown_permission',COUNT(DISTINCT u.id) FROM sys_user u
  JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
  JOIN sys_role_menu rm ON rm.tenant_id=ur.tenant_id AND rm.role_id=ur.role_id
  JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
  WHERE u.tenant_id=0 AND u.username='demo.cost' AND u.status='ENABLE' AND u.deleted_flag=0
    AND m.perms='dashboard:cost-breakdown:view' AND m.status='ENABLE' AND m.deleted_flag=0
UNION ALL SELECT 'invalid_business_code',SUM(invalid_count) FROM (
  SELECT COUNT(*) invalid_count FROM pm_project WHERE project_code NOT REGEXP '^XM-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM md_partner WHERE partner_code NOT REGEXP '^PTN-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM ct_contract WHERE contract_code NOT REGEXP '^CT-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM ct_contract_change WHERE change_code NOT REGEXP '^CC-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM var_order WHERE var_code NOT REGEXP '^VO-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM mat_purchase_request WHERE request_code NOT REGEXP '^PR-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM mat_purchase_order WHERE order_code NOT REGEXP '^PO-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM mat_receipt WHERE receipt_code NOT REGEXP '^MR-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM mat_requisition WHERE requisition_code NOT REGEXP '^REQ-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM mat_material_return WHERE return_code NOT REGEXP '^MRT-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM sp_supplier_return WHERE return_code NOT REGEXP '^SRT-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM sub_task WHERE task_code NOT REGEXP '^SUB-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM sub_measure WHERE measure_code NOT REGEXP '^SM-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM pay_application WHERE apply_code NOT REGEXP '^PAY-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM pay_record WHERE record_code IS NULL OR record_code NOT REGEXP '^PMT-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM stl_settlement WHERE settlement_code NOT REGEXP '^STL-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM expense_application WHERE expense_code NOT REGEXP '^EXP-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM contract_revenue WHERE revenue_code NOT REGEXP '^RV-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM cash_journal_entry WHERE entry_no NOT REGEXP '^CJ-[0-9]{8}-[0-9]{3}$'
  UNION ALL SELECT COUNT(*) FROM biz_document_generation WHERE generation_no NOT REGEXP '^DOC-[0-9]{8}-[0-9]{3}$'
) standardized_codes
UNION ALL SELECT 'role_test_scope',COUNT(DISTINCT u.username) FROM sys_user u
  JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
  JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
  JOIN sys_role_menu rm ON rm.tenant_id=r.tenant_id AND rm.role_id=r.id
  JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
  WHERE u.tenant_id=0 AND u.deleted_flag=0 AND (
    (u.username='admin' AND r.role_code='SUPER_ADMIN' AND m.perms='dashboard:management:view') OR
    (u.username='demo.manager' AND m.perms='dashboard:project-manager:view') OR
    (u.username='demo.business' AND m.perms='dashboard:business-manager:view') OR
    (u.username='demo.cost' AND m.perms='dashboard:cost-manager:view') OR
    (u.username='demo.purchase' AND m.perms='dashboard:purchase-manager:view') OR
    (u.username='demo.production' AND m.perms='dashboard:production-manager:view') OR
    (u.username='demo.chief' AND m.perms='dashboard:chief-engineer:view') OR
    (u.username='demo.finance' AND m.perms='dashboard:finance:view'))
UNION ALL SELECT 'requested_qty',COALESCE(SUM(quantity),0) FROM mat_purchase_request_item WHERE request_id=520000000000001101 AND deleted_flag=0
UNION ALL SELECT 'ordered_qty',COALESCE(SUM(quantity),0) FROM mat_purchase_order_item WHERE order_id=520000000000001201 AND deleted_flag=0
UNION ALL SELECT 'received_qty',COALESCE(SUM(qualified_quantity),0) FROM mat_receipt_item WHERE receipt_id=520000000000001301 AND deleted_flag=0
UNION ALL SELECT 'issued_qty',COALESCE(SUM(quantity),0) FROM mat_requisition_item WHERE requisition_id=520000000000001501 AND deleted_flag=0
UNION ALL SELECT 'stock_qty',COALESCE(SUM(available_qty),0) FROM mat_stock WHERE warehouse_id=520000000000000301 AND material_id=520000000000000201 AND deleted_flag=0
UNION ALL SELECT 'inventory_delta',ABS(
  COALESCE((SELECT SUM(qualified_quantity) FROM mat_receipt_item WHERE receipt_id=520000000000001301 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(quantity) FROM mat_requisition_item WHERE requisition_id=520000000000001501 AND deleted_flag=0),0)
  -COALESCE((SELECT SUM(available_qty) FROM mat_stock WHERE warehouse_id=520000000000000301 AND material_id=520000000000000201 AND deleted_flag=0),0))
UNION ALL SELECT 'purchase_amount_delta',ABS(
  COALESCE((SELECT total_amount FROM mat_purchase_order WHERE id=520000000000001201),0)
  -COALESCE((SELECT total_amount FROM mat_receipt WHERE id=520000000000001301),0))
UNION ALL SELECT 'material_cost_delta',ABS(
  COALESCE((SELECT total_amount FROM mat_requisition WHERE id=520000000000001501),0)
  -COALESCE((SELECT SUM(amount) FROM cost_item WHERE source_type='MAT_REQUISITION' AND source_id=520000000000001501 AND deleted_flag=0),0))
UNION ALL SELECT 'bid_transfer_delta',ABS(
  COALESCE((SELECT amount FROM cost_item WHERE id=520000000000000402),0)
  -COALESCE((SELECT total_amount FROM bid_cost_target_transfer WHERE id=520000000000000601),0))
UNION ALL SELECT 'subcontract_delta',ABS(
  COALESCE((SELECT final_amount-paid_amount-unpaid_amount FROM stl_settlement WHERE id=520000000000002201),0))
UNION ALL SELECT 'receivable_delta',ABS(
  COALESCE((SELECT original_amount-collected_amount-credited_amount-outstanding_amount FROM account_receivable WHERE id=520000000000002801),0))
UNION ALL SELECT 'collection_delta',ABS(
  COALESCE((SELECT amount-allocated_amount-unallocated_amount FROM collection_record WHERE id=520000000000002901),0))
UNION ALL SELECT 'quality_safety_cost',COALESCE(SUM(amount),0) FROM cost_item WHERE project_id=520000000000000001 AND cost_type='QUALITY_SAFETY' AND deleted_flag=0
UNION ALL SELECT 'actual_cost_total',COALESCE(SUM(amount),0) FROM cost_item WHERE project_id=520000000000000001 AND deleted_flag=0
UNION ALL SELECT 'project_total',COUNT(*) FROM pm_project WHERE tenant_id=0 AND id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND deleted_flag=0
UNION ALL SELECT 'partner_incomplete',COUNT(*) FROM md_partner
  WHERE tenant_id=0 AND id IN (520000000000000101,520000000000000102,520000000000000103,520000000000006001,520000000000006002,520000000000009101,520000000000009102) AND deleted_flag=0
    AND (NULLIF(TRIM(credit_code),'') IS NULL OR NULLIF(TRIM(legal_person),'') IS NULL OR NULLIF(TRIM(contact_name),'') IS NULL
      OR NULLIF(TRIM(contact_phone),'') IS NULL OR NULLIF(TRIM(bank_name),'') IS NULL OR NULLIF(TRIM(bank_account),'') IS NULL
      OR NULLIF(TRIM(qualification_level),'') IS NULL OR NULLIF(TRIM(risk_level),'') IS NULL)
UNION ALL SELECT 'partner_invalid_credit',COUNT(*) FROM md_partner
  WHERE tenant_id=0 AND id IN (520000000000000101,520000000000000102,520000000000000103,520000000000006001,520000000000006002,520000000000009101,520000000000009102) AND deleted_flag=0 AND credit_code NOT REGEXP '^[0-9A-HJ-NPQRTUWXY]{18}$'
UNION ALL SELECT CONCAT('partner_credit:',partner_code),credit_code FROM md_partner
  WHERE tenant_id=0 AND id IN (520000000000000101,520000000000000102,520000000000000103,520000000000006001,520000000000006002,520000000000009101,520000000000009102) AND deleted_flag=0
UNION ALL SELECT 'partner_invalid_type',COUNT(*) FROM md_partner p
  LEFT JOIN sys_dict_type dt ON dt.tenant_id=p.tenant_id AND dt.dict_code='partner_type' AND dt.status='ENABLE'
  LEFT JOIN sys_dict_data dd ON dd.tenant_id=p.tenant_id AND dd.dict_type_id=dt.id AND dd.dict_value=p.partner_type AND dd.status='ENABLE'
  WHERE p.tenant_id=0 AND p.id IN (520000000000000101,520000000000000102,520000000000000103,520000000000006001,520000000000006002,520000000000009101,520000000000009102) AND p.deleted_flag=0 AND dd.id IS NULL
UNION ALL SELECT 'project_type_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='project_type' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM pm_project p WHERE p.tenant_id=0 AND p.id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND p.deleted_flag=0 AND p.project_type=dd.dict_value)
UNION ALL SELECT 'project_status_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='project_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM pm_project p WHERE p.tenant_id=0 AND p.id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND p.deleted_flag=0 AND p.status=dd.dict_value)
UNION ALL SELECT 'project_approval_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='approval_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM pm_project p WHERE p.tenant_id=0 AND p.id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND p.deleted_flag=0 AND p.approval_status=dd.dict_value)
UNION ALL SELECT 'project_invalid_authority',COUNT(*) FROM pm_project p
  LEFT JOIN sys_dict_type ptdt ON ptdt.tenant_id=p.tenant_id AND ptdt.dict_code='project_type' AND ptdt.status='ENABLE'
  LEFT JOIN sys_dict_data ptdd ON ptdd.tenant_id=p.tenant_id AND ptdd.dict_type_id=ptdt.id AND ptdd.dict_value=p.project_type AND ptdd.status='ENABLE'
  LEFT JOIN sys_dict_type psdt ON psdt.tenant_id=p.tenant_id AND psdt.dict_code='project_status' AND psdt.status='ENABLE'
  LEFT JOIN sys_dict_data psdd ON psdd.tenant_id=p.tenant_id AND psdd.dict_type_id=psdt.id AND psdd.dict_value=p.status AND psdd.status='ENABLE'
  LEFT JOIN sys_dict_type padt ON padt.tenant_id=p.tenant_id AND padt.dict_code='approval_status' AND padt.status='ENABLE'
  LEFT JOIN sys_dict_data padd ON padd.tenant_id=p.tenant_id AND padd.dict_type_id=padt.id AND padd.dict_value=p.approval_status AND padd.status='ENABLE'
  WHERE p.tenant_id=0 AND p.id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND p.deleted_flag=0 AND (ptdd.id IS NULL OR psdd.id IS NULL OR padd.id IS NULL)
UNION ALL SELECT 'project_invalid_dates',COUNT(*) FROM pm_project
  WHERE tenant_id=0 AND id IN (520000000000000001,520000000000009001,520000000000009002,520000000000009003,520000000000009004,520000000000009005,520000000000009006,520000000000009007) AND deleted_flag=0
    AND (planned_start_date>planned_end_date OR (actual_start_date IS NOT NULL AND actual_end_date IS NOT NULL AND actual_start_date>actual_end_date))
UNION ALL SELECT 'contract_type_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='contract_type' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM ct_contract c WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND c.contract_type=dd.dict_value)
UNION ALL SELECT 'contract_status_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='contract_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM ct_contract c WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND c.contract_status=dd.dict_value)
UNION ALL SELECT 'contract_invalid_authority',COUNT(*) FROM ct_contract c
  LEFT JOIN sys_dict_type ctdt ON ctdt.tenant_id=c.tenant_id AND ctdt.dict_code='contract_type' AND ctdt.status='ENABLE'
  LEFT JOIN sys_dict_data ctdd ON ctdd.tenant_id=c.tenant_id AND ctdd.dict_type_id=ctdt.id AND ctdd.dict_value=c.contract_type AND ctdd.status='ENABLE'
  LEFT JOIN sys_dict_type csdt ON csdt.tenant_id=c.tenant_id AND csdt.dict_code='contract_status' AND csdt.status='ENABLE'
  LEFT JOIN sys_dict_data csdd ON csdd.tenant_id=c.tenant_id AND csdd.dict_type_id=csdt.id AND csdd.dict_value=c.contract_status AND csdd.status='ENABLE'
  LEFT JOIN sys_dict_type cadt ON cadt.tenant_id=c.tenant_id AND cadt.dict_code='approval_status' AND cadt.status='ENABLE'
  LEFT JOIN sys_dict_data cadd ON cadd.tenant_id=c.tenant_id AND cadd.dict_type_id=cadt.id AND cadd.dict_value=c.approval_status AND cadd.status='ENABLE'
  WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND (ctdd.id IS NULL OR csdd.id IS NULL OR cadd.id IS NULL)
UNION ALL SELECT 'contract_invalid_parties',COUNT(*) FROM ct_contract c
  LEFT JOIN md_partner pa ON pa.id=c.party_a_id AND pa.tenant_id=c.tenant_id AND pa.deleted_flag=0
  LEFT JOIN md_partner pb ON pb.id=c.party_b_id AND pb.tenant_id=c.tenant_id AND pb.deleted_flag=0
  WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND (c.party_a_id=c.party_b_id OR pa.id IS NULL OR pb.id IS NULL)
UNION ALL SELECT 'workflow_instance_status_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='wf_instance_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM wf_instance w WHERE w.tenant_id=0 AND w.id BETWEEN 520000000000000000 AND 520000000000009999 AND w.deleted_flag=0 AND w.instance_status=dd.dict_value)
UNION ALL SELECT 'workflow_task_status_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='wf_task_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM wf_task w WHERE w.tenant_id=0 AND w.id BETWEEN 520000000000000000 AND 520000000000009999 AND w.deleted_flag=0 AND w.task_status=dd.dict_value)
UNION ALL SELECT 'workflow_node_status_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='wf_node_status' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM wf_node_instance n WHERE n.tenant_id=0 AND n.id BETWEEN 520000000000000000 AND 520000000000009999 AND n.deleted_flag=0 AND n.node_status=dd.dict_value)
UNION ALL SELECT 'workflow_mode_missing',COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type dt ON dt.id=dd.dict_type_id
  WHERE dt.tenant_id=0 AND dt.dict_code='approve_mode' AND dt.status='ENABLE' AND dd.status='ENABLE'
    AND NOT EXISTS (SELECT 1 FROM wf_node_instance n WHERE n.tenant_id=0 AND n.id BETWEEN 520000000000000000 AND 520000000000009999 AND n.deleted_flag=0 AND n.approve_mode=dd.dict_value)
UNION ALL SELECT 'workflow_invalid_authority',(
  (SELECT COUNT(*) FROM wf_instance w LEFT JOIN sys_dict_type dt ON dt.tenant_id=w.tenant_id AND dt.dict_code='wf_instance_status' AND dt.status='ENABLE' LEFT JOIN sys_dict_data dd ON dd.tenant_id=w.tenant_id AND dd.dict_type_id=dt.id AND dd.dict_value=w.instance_status AND dd.status='ENABLE' WHERE w.tenant_id=0 AND w.id BETWEEN 520000000000000000 AND 520000000000009999 AND w.deleted_flag=0 AND dd.id IS NULL)
  +(SELECT COUNT(*) FROM wf_task w LEFT JOIN sys_dict_type dt ON dt.tenant_id=w.tenant_id AND dt.dict_code='wf_task_status' AND dt.status='ENABLE' LEFT JOIN sys_dict_data dd ON dd.tenant_id=w.tenant_id AND dd.dict_type_id=dt.id AND dd.dict_value=w.task_status AND dd.status='ENABLE' WHERE w.tenant_id=0 AND w.id BETWEEN 520000000000000000 AND 520000000000009999 AND w.deleted_flag=0 AND dd.id IS NULL)
  +(SELECT COUNT(*) FROM wf_node_instance n LEFT JOIN sys_dict_type sdt ON sdt.tenant_id=n.tenant_id AND sdt.dict_code='wf_node_status' AND sdt.status='ENABLE' LEFT JOIN sys_dict_data sdd ON sdd.tenant_id=n.tenant_id AND sdd.dict_type_id=sdt.id AND sdd.dict_value=n.node_status AND sdd.status='ENABLE' LEFT JOIN sys_dict_type mdt ON mdt.tenant_id=n.tenant_id AND mdt.dict_code='approve_mode' AND mdt.status='ENABLE' LEFT JOIN sys_dict_data mdd ON mdd.tenant_id=n.tenant_id AND mdd.dict_type_id=mdt.id AND mdd.dict_value=n.approve_mode AND mdd.status='ENABLE' WHERE n.tenant_id=0 AND n.id BETWEEN 520000000000000000 AND 520000000000009999 AND n.deleted_flag=0 AND (sdd.id IS NULL OR mdd.id IS NULL))
  +(SELECT COUNT(*) FROM wf_record r WHERE r.tenant_id=0 AND r.id BETWEEN 520000000000000000 AND 520000000000009999 AND r.deleted_flag=0 AND r.record_status NOT IN ('EFFECTIVE','VOIDED')))
UNION ALL SELECT 'workflow_template_semantics_invalid',(
  (SELECT COUNT(*) FROM wf_instance w LEFT JOIN wf_template t ON t.id=w.template_id AND t.tenant_id=w.tenant_id AND t.business_type=w.business_type AND t.enabled=1 AND t.deleted_flag=0
    WHERE w.tenant_id=0 AND w.id BETWEEN 520000000000009301 AND 520000000000009305 AND w.deleted_flag=0 AND t.id IS NULL)
  +(SELECT COUNT(*) FROM wf_node_instance n JOIN wf_instance w ON w.id=n.instance_id AND w.tenant_id=n.tenant_id AND w.deleted_flag=0
    LEFT JOIN wf_template_node tn ON tn.id=n.template_node_id AND tn.tenant_id=n.tenant_id AND tn.template_id=w.template_id AND tn.node_code=n.node_code AND tn.node_name=n.node_name AND tn.node_order=n.node_order AND tn.approve_mode=n.approve_mode AND tn.node_type='APPROVAL' AND tn.deleted_flag=0
    WHERE n.tenant_id=0 AND n.id BETWEEN 520000000000009311 AND 520000000000009315 AND n.deleted_flag=0 AND tn.id IS NULL)
  +(SELECT COUNT(*) FROM wf_task x JOIN wf_instance w ON w.id=x.instance_id AND w.tenant_id=x.tenant_id AND w.deleted_flag=0
    WHERE x.tenant_id=0 AND x.id BETWEEN 520000000000009321 AND 520000000000009324 AND x.deleted_flag=0 AND (x.business_type<>w.business_type OR x.business_id<>w.business_id))
  +(SELECT COUNT(*) FROM wf_record x JOIN wf_instance w ON w.id=x.instance_id AND w.tenant_id=x.tenant_id AND w.deleted_flag=0
    WHERE x.tenant_id=0 AND x.id BETWEEN 520000000000009331 AND 520000000000009333 AND x.deleted_flag=0 AND (x.business_type<>w.business_type OR x.business_id<>w.business_id)))
UNION ALL SELECT 'cost_invalid_authority',COUNT(*) FROM cost_item c
  LEFT JOIN sys_dict_type tdt ON tdt.tenant_id=c.tenant_id AND tdt.dict_code='cost_type' AND tdt.status='ENABLE'
  LEFT JOIN sys_dict_data tdd ON tdd.tenant_id=c.tenant_id AND tdd.dict_type_id=tdt.id AND tdd.dict_value=c.cost_type AND tdd.status='ENABLE'
  LEFT JOIN sys_dict_type sdt ON sdt.tenant_id=c.tenant_id AND sdt.dict_code='cost_source_type' AND sdt.status='ENABLE'
  LEFT JOIN sys_dict_data sdd ON sdd.tenant_id=c.tenant_id AND sdd.dict_type_id=sdt.id AND sdd.dict_value=c.source_type AND sdd.status='ENABLE'
  LEFT JOIN sys_dict_type zdt ON zdt.tenant_id=c.tenant_id AND zdt.dict_code='cost_status' AND zdt.status='ENABLE'
  LEFT JOIN sys_dict_data zdd ON zdd.tenant_id=c.tenant_id AND zdd.dict_type_id=zdt.id AND zdd.dict_value=c.cost_status AND zdd.status='ENABLE'
  WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND (tdd.id IS NULL OR sdd.id IS NULL OR zdd.id IS NULL)
UNION ALL SELECT 'cost_subject_invalid',COUNT(*) FROM cost_item c
  LEFT JOIN cost_subject s ON s.id=c.cost_subject_id AND s.tenant_id=c.tenant_id AND s.status='ENABLE' AND s.deleted_flag=0
  WHERE c.tenant_id=0 AND c.id BETWEEN 520000000000000000 AND 520000000000009999 AND c.deleted_flag=0 AND s.id IS NULL
UNION ALL SELECT 'quality_contract_trace_invalid',COUNT(*) FROM qs_consequence q
  LEFT JOIN ct_contract c ON c.id=q.contract_id AND c.tenant_id=q.tenant_id AND c.project_id=q.project_id AND c.deleted_flag=0
  WHERE q.tenant_id=0 AND q.id=520000000000008201 AND q.deleted_flag=0
    AND (c.id IS NULL OR q.partner_id IS NULL OR q.partner_id NOT IN (c.party_a_id,c.party_b_id))
UNION ALL SELECT 'quality_contract_trace',COUNT(*) FROM qs_consequence q
  JOIN ct_contract c ON c.id=q.contract_id AND c.tenant_id=q.tenant_id AND c.project_id=q.project_id AND c.deleted_flag=0
  WHERE q.tenant_id=0 AND q.id=520000000000008201 AND q.deleted_flag=0
    AND q.partner_id IN (c.party_a_id,c.party_b_id);
'@
$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-demo-verify-{0}.sql" -f [guid]::NewGuid().ToString('N'))
$containerFile = "/tmp/cgc-demo-verify-$([guid]::NewGuid().ToString('N')).sql"
try {
    [System.IO.File]::WriteAllText($tempFile, $sql, [System.Text.UTF8Encoding]::new($false))
    docker cp $tempFile "${MySqlContainer}:$containerFile" 2>$null | Out-Null
    $rows = docker exec $MySqlContainer sh -lc "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql --no-defaults -uroot --default-character-set=utf8mb4 -N $Database < $containerFile" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $summary = (($rows | Select-Object -Last 4) -join ' ') -replace '[\r\n]+', ' '
        throw "DEMO_VERIFY_SQL_FAILED:$summary"
    }
} finally {
    docker exec $MySqlContainer rm -f $containerFile 2>$null | Out-Null
    if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
}

$metrics = @{}
$partnerCreditCodes = @{}
foreach ($row in $rows) {
    $parts = $row -split "`t", 2
    if ($parts.Count -ne 2) { continue }
    if ($parts[0].StartsWith('partner_credit:')) {
        $partnerCreditCodes[$parts[0].Substring('partner_credit:'.Length)] = $parts[1]
    } else {
        $metrics[$parts[0]] = [decimal]$parts[1]
    }
}

function Test-Gb32100CreditCode([string]$CreditCode) {
    if ($CreditCode -notmatch '^[0-9A-HJ-NPQRTUWXY]{18}$') { return $false }
    $alphabet = '0123456789ABCDEFGHJKLMNPQRTUWXY'
    $weights = @(1,3,9,27,19,26,16,17,20,29,25,13,8,24,10,30,28)
    $sum = 0
    for ($index = 0; $index -lt 17; $index++) {
        $value = $alphabet.IndexOf($CreditCode[$index])
        if ($value -lt 0) { return $false }
        $sum += $value * $weights[$index]
    }
    $expected = $alphabet[(31 - ($sum % 31)) % 31]
    return $CreditCode[17] -eq $expected
}

$invalidCreditPartners = @($partnerCreditCodes.GetEnumerator() | Where-Object { -not (Test-Gb32100CreditCode $_.Value) } | ForEach-Object Key)
$oneKeys = @('project','material','bid_transfer','target','purchase_request','purchase_order','receipt','requisition',
    'stock','sub_measure','settlement','pay_application','pay_record','expense','revenue','receivable','collection',
    'quality_issue','rectification','progress','workflow_instance','workflow_task','workflow_record','alert','closeout',
    'cost_forecast','cost_corrective_action','finance_reconciliation_run','finance_import_batch','inventory_exception',
    'material_return_reversal','document_template','demo_user')
$passed = $metrics.partner -eq 7 -and $partnerCreditCodes.Count -eq 7 -and $invalidCreditPartners.Count -eq 0 `
    -and $metrics.contract -eq 4 -and $metrics.completed_stage -eq 20 `
    -and $metrics.role_test_account -eq 8 -and $metrics.role_alert_permission -eq 8 -and $metrics.role_alert_edit_permission -eq 8 `
    -and $metrics.role_alert_project_members -eq 7 `
    -and $metrics.dashboard_trend_month -eq 7 -and $metrics.role_test_scope -eq 8 `
    -and $metrics.document_generation -eq 2 -and $metrics.finance_demo_budget -eq 1 `
    -and $metrics.finance_demo_pay_application -eq 4 -and $metrics.finance_demo_pay_record -eq 4 `
    -and $metrics.finance_demo_paid -eq 340000 -and $metrics.finance_demo_processing -eq 120000 `
    -and $metrics.role_dashboard_contract -eq 1 -and $metrics.role_dashboard_variation -eq 1 `
    -and $metrics.role_dashboard_measure -eq 1 -and $metrics.role_dashboard_settlement -eq 1 `
    -and $metrics.role_dashboard_purchase -eq 1 -and $metrics.role_dashboard_receipt -eq 1 `
    -and $metrics.role_dashboard_requisition -eq 1 -and $metrics.role_dashboard_low_stock -eq 1 `
    -and $metrics.role_dashboard_tech -eq 3 -and $metrics.role_dashboard_task -eq 1 `
    -and $metrics.role_dashboard_lagging -eq 1 `
    -and $metrics.risk_level_contracts -eq 2 -and $metrics.risk_level_project_task -eq 1 `
    -and $metrics.risk_level_purchase_order -eq 1 -and $metrics.risk_level_measures -eq 4 `
    -and $metrics.risk_level_tech_items -eq 2 -and $metrics.risk_level_finance_records -eq 4 `
    -and $metrics.risk_level_alert_severities -eq 4 -and $metrics.role_quality_safety_alert -eq 1 `
    -and $metrics.cost_breakdown_rows -eq 5 -and $metrics.cost_breakdown_roots -eq 1 `
    -and $metrics.cost_breakdown_children -eq 4 -and $metrics.cost_breakdown_permission -eq 1 `
    -and $metrics.cost_breakdown_target_delta -eq 0 -and $metrics.cost_breakdown_actual_delta -eq 0 `
    -and $metrics.cost_breakdown_dynamic_delta -eq 0 -and $metrics.cost_breakdown_deviation_delta -eq 0 `
    -and $metrics.role_workflow_status_instances -eq 40 -and $metrics.role_workflow_status_pairs -eq 40 `
    -and $metrics.role_workflow_status_todos -eq 8 -and $metrics.role_workflow_status_done -eq 40 `
    -and $metrics.role_workflow_status_cc -eq 40 -and $metrics.role_workflow_business_types -eq 25 `
    -and $metrics.role_workflow_action_permissions -eq 48 `
    -and $metrics.role_workflow_orphans -eq 0 `
    -and $metrics.invalid_business_code -eq 0
foreach ($key in $oneKeys) { $passed = $passed -and $metrics[$key] -eq 1 }
$passed = $passed -and $metrics.requested_qty -eq 100 -and $metrics.ordered_qty -eq 100 `
    -and $metrics.received_qty -eq 100 -and $metrics.issued_qty -eq 20 -and $metrics.stock_qty -eq 80 `
    -and $metrics.inventory_delta -eq 0 -and $metrics.purchase_amount_delta -eq 0 `
    -and $metrics.material_cost_delta -eq 0 -and $metrics.bid_transfer_delta -eq 0 `
    -and $metrics.subcontract_delta -eq 0 -and $metrics.receivable_delta -eq 0 -and $metrics.collection_delta -eq 0 `
    -and $metrics.quality_safety_cost -eq 5000 -and $metrics.actual_cost_total -eq 85000
$passed = $passed -and $metrics.project_total -eq 8 `
    -and $metrics.partner_incomplete -eq 0 -and $metrics.partner_invalid_credit -eq 0 -and $metrics.partner_invalid_type -eq 0 `
    -and $metrics.project_type_missing -eq 0 -and $metrics.project_status_missing -eq 0 -and $metrics.project_approval_missing -eq 0 `
    -and $metrics.project_invalid_authority -eq 0 -and $metrics.project_invalid_dates -eq 0 `
    -and $metrics.contract_type_missing -eq 0 -and $metrics.contract_status_missing -eq 0 `
    -and $metrics.contract_invalid_authority -eq 0 -and $metrics.contract_invalid_parties -eq 0 `
    -and $metrics.workflow_instance_status_missing -eq 0 -and $metrics.workflow_task_status_missing -eq 0 `
    -and $metrics.workflow_node_status_missing -eq 0 -and $metrics.workflow_mode_missing -eq 0 `
    -and $metrics.workflow_invalid_authority -eq 0 -and $metrics.workflow_template_semantics_invalid -eq 0 -and $metrics.cost_invalid_authority -eq 0 `
    -and $metrics.cost_subject_invalid -eq 0 -and $metrics.quality_contract_trace -eq 1 `
    -and $metrics.quality_contract_trace_invalid -eq 0
$passed = $passed -and $metrics.cash_cycle -eq 3 -and $metrics.pay_invoice -eq 3 -and $metrics.sales_invoice -eq 3 `
    -and $metrics.finance_period -eq 3 -and $metrics.accounting_entry -eq 2 `
    -and $metrics.accounting_balance_delta -eq 0 -and $metrics.invoice_allocation_delta -eq 0
$passed = $passed -and $metrics.measurement_period -eq 3 -and $metrics.production_measurement -eq 3 `
    -and $metrics.owner_measurement_submission -eq 2 -and $metrics.site_daily_log -eq 3 `
    -and $metrics.measurement_amount_delta -eq 0
$passed = $passed -and $metrics.sourcing_event -eq 3 -and $metrics.supplier_quote -eq 2 `
    -and $metrics.performance_evaluation -eq 2 -and $metrics.technical_scheme -eq 3 `
    -and $metrics.technical_drawing -eq 2 -and $metrics.technical_rfi -eq 2 `
    -and $metrics.technical_archive -eq 2
$passed = $passed -and $metrics.variation_order -eq 3 -and $metrics.variation_submission -eq 2 `
    -and $metrics.contract_change -eq 1 -and $metrics.closeout_detail -eq 5 `
    -and $metrics.organization_position -eq 2 -and $metrics.project_member -eq 1 -and $metrics.user_preference -eq 1
$result = [pscustomobject]@{
    package='CGC-COMPLETE-PROJECT'; version=2; environment=$Environment; database=$Database; passed=$passed
    credit_code_validation=[pscustomobject]@{ standard='GB 32100'; checked=$partnerCreditCodes.Count; invalid_partners=$invalidCreditPartners }
    metrics=$metrics
}
$result | ConvertTo-Json -Depth 4
if (-not $passed) { throw 'DEMO_VERIFY_ACCEPTANCE_FAILED' }
