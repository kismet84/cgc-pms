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
SELECT 'project',COUNT(*) FROM pm_project WHERE tenant_id=0 AND id=520000000000000001 AND project_code='CGC-DEMO-M52-001' AND deleted_flag=0
UNION ALL SELECT 'partner',COUNT(*) FROM md_partner WHERE tenant_id=0 AND partner_code LIKE 'M52-%' AND deleted_flag=0
UNION ALL SELECT 'material',COUNT(*) FROM md_material WHERE tenant_id=0 AND material_code='M52-MAT-STEEL' AND deleted_flag=0
UNION ALL SELECT 'contract',COUNT(*) FROM ct_contract WHERE tenant_id=0 AND project_id=520000000000000001 AND contract_code LIKE 'M52-%' AND deleted_flag=0
UNION ALL SELECT 'bid_transfer',COUNT(*) FROM bid_cost_target_transfer WHERE tenant_id=0 AND project_id=520000000000000001 AND status='POSTED'
UNION ALL SELECT 'target',COUNT(*) FROM cost_target WHERE tenant_id=0 AND project_id=520000000000000001 AND is_active=1 AND deleted_flag=0
UNION ALL SELECT 'purchase_request',COUNT(*) FROM mat_purchase_request WHERE tenant_id=0 AND request_code='M52-PR-001' AND deleted_flag=0
UNION ALL SELECT 'purchase_order',COUNT(*) FROM mat_purchase_order WHERE tenant_id=0 AND order_code='M52-PO-001' AND deleted_flag=0
UNION ALL SELECT 'receipt',COUNT(*) FROM mat_receipt WHERE tenant_id=0 AND receipt_code='M52-RC-001' AND deleted_flag=0
UNION ALL SELECT 'requisition',COUNT(*) FROM mat_requisition WHERE tenant_id=0 AND requisition_code='M52-REQ-001' AND deleted_flag=0
UNION ALL SELECT 'stock',COUNT(*) FROM mat_stock WHERE tenant_id=0 AND warehouse_id=520000000000000301 AND material_id=520000000000000201 AND deleted_flag=0
UNION ALL SELECT 'sub_measure',COUNT(*) FROM sub_measure WHERE tenant_id=0 AND measure_code='M52-SM-001' AND deleted_flag=0
UNION ALL SELECT 'settlement',COUNT(*) FROM stl_settlement WHERE tenant_id=0 AND settlement_code='M52-STL-001' AND deleted_flag=0
UNION ALL SELECT 'pay_application',COUNT(*) FROM pay_application WHERE tenant_id=0 AND apply_code='M52-PAY-APP-001' AND deleted_flag=0
UNION ALL SELECT 'pay_record',COUNT(*) FROM pay_record WHERE tenant_id=0 AND external_txn_no='M52-PAY-TXN-001' AND deleted_flag=0
UNION ALL SELECT 'expense',COUNT(*) FROM expense_application WHERE tenant_id=0 AND expense_code='M52-EXP-001' AND deleted_flag=0
UNION ALL SELECT 'revenue',COUNT(*) FROM contract_revenue WHERE tenant_id=0 AND revenue_code='M52-REV-001' AND deleted_flag=0
UNION ALL SELECT 'receivable',COUNT(*) FROM account_receivable WHERE tenant_id=0 AND receivable_code='M52-AR-001' AND deleted_flag=0
UNION ALL SELECT 'collection',COUNT(*) FROM collection_record WHERE tenant_id=0 AND collection_code='M52-COLLECTION-001' AND deleted_flag=0
UNION ALL SELECT 'quality_issue',COUNT(*) FROM qs_issue WHERE tenant_id=0 AND issue_code='M52-QS-ISSUE-001' AND status='CLOSED' AND deleted_flag=0
UNION ALL SELECT 'rectification',COUNT(*) FROM qs_rectification WHERE tenant_id=0 AND issue_id=520000000000003203 AND status='PASSED' AND deleted_flag=0
UNION ALL SELECT 'progress',COUNT(*) FROM project_progress_snapshot WHERE tenant_id=0 AND project_id=520000000000000001 AND status='COMPLETED'
UNION ALL SELECT 'workflow_instance',COUNT(*) FROM wf_instance WHERE tenant_id=0 AND id=520000000000000901 AND instance_status='COMPLETED' AND deleted_flag=0
UNION ALL SELECT 'workflow_task',COUNT(*) FROM wf_task WHERE tenant_id=0 AND instance_id=520000000000000901 AND task_status='COMPLETED' AND deleted_flag=0
UNION ALL SELECT 'workflow_record',COUNT(*) FROM wf_record WHERE tenant_id=0 AND instance_id=520000000000000901 AND record_status='SUCCESS' AND deleted_flag=0
UNION ALL SELECT 'alert',COUNT(*) FROM alert_log WHERE tenant_id=0 AND id=520000000000003401 AND process_status='RESOLVED' AND deleted_flag=0
UNION ALL SELECT 'closeout',COUNT(*) FROM project_closeout WHERE tenant_id=0 AND project_id=520000000000000001 AND status='CLOSED' AND deleted_flag=0
UNION ALL SELECT 'completed_stage',COUNT(*) FROM sys_bootstrap_state WHERE bootstrap_key LIKE 'DEMO_CGC_V1_%' AND status='COMPLETED'
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
UNION ALL SELECT 'actual_cost_total',COALESCE(SUM(amount),0) FROM cost_item WHERE project_id=520000000000000001 AND deleted_flag=0;
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
foreach ($row in $rows) {
    $parts = $row -split "`t", 2
    if ($parts.Count -eq 2) { $metrics[$parts[0]] = [decimal]$parts[1] }
}
$oneKeys = @('project','material','bid_transfer','target','purchase_request','purchase_order','receipt','requisition',
    'stock','sub_measure','settlement','pay_application','pay_record','expense','revenue','receivable','collection',
    'quality_issue','rectification','progress','workflow_instance','workflow_task','workflow_record','alert','closeout')
$passed = $metrics.partner -eq 3 -and $metrics.contract -eq 3 -and $metrics.completed_stage -eq 4
foreach ($key in $oneKeys) { $passed = $passed -and $metrics[$key] -eq 1 }
$passed = $passed -and $metrics.requested_qty -eq 100 -and $metrics.ordered_qty -eq 100 `
    -and $metrics.received_qty -eq 100 -and $metrics.issued_qty -eq 20 -and $metrics.stock_qty -eq 80 `
    -and $metrics.inventory_delta -eq 0 -and $metrics.purchase_amount_delta -eq 0 `
    -and $metrics.material_cost_delta -eq 0 -and $metrics.bid_transfer_delta -eq 0 `
    -and $metrics.subcontract_delta -eq 0 -and $metrics.receivable_delta -eq 0 -and $metrics.collection_delta -eq 0 `
    -and $metrics.quality_safety_cost -eq 5000 -and $metrics.actual_cost_total -eq 85000
$result = [pscustomobject]@{ package='CGC-COMPLETE-PROJECT'; version=1; environment=$Environment; database=$Database; passed=$passed; metrics=$metrics }
$result | ConvertTo-Json -Depth 4
if (-not $passed) { throw 'DEMO_VERIFY_ACCEPTANCE_FAILED' }
