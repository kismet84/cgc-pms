-- CGC-COMPLETE-PROJECT v2 / COST BREAKDOWN DATA
-- Reuses the canonical 5401 cost-subject tree. Parent amount equals the four child amounts.
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO cost_summary
  (id,tenant_id,project_id,summary_date,cost_subject_id,target_cost,contract_locked_cost,actual_cost,paid_amount,estimated_remaining_cost,
   dynamic_cost,contract_income,confirmed_revenue,expected_profit,cost_deviation,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   responsibility_cost,forecast_at_completion_cost,forecast_profit,profit_margin)
VALUES
  (520000000000009471,0,520000000000009002,'2026-07-18',900001,3900000,3100000,2400000,990000,1570000,
   3970000,5000000,0,1030000,70000,@demo_user,NOW(),@demo_user,NOW(),0,'M2成本科目分解：合同履约成本汇总',3900000,3970000,1030000,0.206000),
  (520000000000009472,0,520000000000009002,'2026-07-18',900010,200000,160000,150000,60000,30000,
   180000,0,0,0,-20000,@demo_user,NOW(),@demo_user,NOW(),0,'M2成本科目分解：招投标及前期费用',200000,180000,0,0),
  (520000000000009473,0,520000000000009002,'2026-07-18',900030,900000,760000,720000,340000,260000,
   980000,0,0,0,80000,@demo_user,NOW(),@demo_user,NOW(),0,'M2成本科目分解：采购阶段成本',900000,980000,0,0),
  (520000000000009474,0,520000000000009002,'2026-07-18',900040,2400000,1880000,1250000,480000,1200000,
   2450000,0,0,0,50000,@demo_user,NOW(),@demo_user,NOW(),0,'M2成本科目分解：施工阶段成本',2400000,2450000,0,0),
  (520000000000009475,0,520000000000009002,'2026-07-18',900080,400000,300000,280000,110000,80000,
   360000,0,0,0,-40000,@demo_user,NOW(),@demo_user,NOW(),0,'M2成本科目分解：项目间接费用',400000,360000,0,0)
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),summary_date=VALUES(summary_date),cost_subject_id=VALUES(cost_subject_id),target_cost=VALUES(target_cost),
  contract_locked_cost=VALUES(contract_locked_cost),actual_cost=VALUES(actual_cost),paid_amount=VALUES(paid_amount),
  estimated_remaining_cost=VALUES(estimated_remaining_cost),dynamic_cost=VALUES(dynamic_cost),contract_income=VALUES(contract_income),
  confirmed_revenue=VALUES(confirmed_revenue),expected_profit=VALUES(expected_profit),cost_deviation=VALUES(cost_deviation),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark),
  responsibility_cost=VALUES(responsibility_cost),forecast_at_completion_cost=VALUES(forecast_at_completion_cost),
  forecast_profit=VALUES(forecast_profit),profit_margin=VALUES(profit_margin);
