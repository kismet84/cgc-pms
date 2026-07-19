-- CGC-COMPLETE-PROJECT v1 / PROCUREMENT AND INVENTORY
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO mat_purchase_request
  (id,tenant_id,project_id,contract_id,purpose,request_code,approval_status,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001101,0,520000000000000001,520000000000000702,'主体结构钢筋采购','M52-PR-001','APPROVED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_purchase_request_item
  (id,tenant_id,request_id,material_id,budget_line_id,wbs_task_id,quantity,estimated_unit_price,estimated_amount,unit,planned_date,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001102,0,520000000000001101,520000000000000201,520000000000000802,520000000000001002,
   100,1000,100000,'吨','2025-03-01',@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_purchase_order
  (id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,order_date,delivery_date,total_amount,
   approval_status,order_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001201,0,520000000000000001,520000000000001101,520000000000000702,520000000000000102,
   'M52-PO-001','MATERIAL','2025-02-15','2025-03-01',100000,'APPROVED','COMPLETED',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_purchase_order_item
  (id,tenant_id,order_id,request_item_id,budget_line_id,project_id,material_id,material_name,specification,unit,quantity,
   unit_price,tax_rate,amount,tax_amount,amount_without_tax,received_quantity,version,wbs_task_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001202,0,520000000000001201,520000000000001102,520000000000000802,520000000000000001,
   520000000000000201,'演示钢筋','HRB400E','吨',100,1000,13,100000,11504.42,88495.58,100,0,520000000000001002,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_receipt
  (id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,receipt_date,warehouse_id,receiver_id,receipt_mode,
   quality_status,total_amount,approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001301,0,520000000000000001,520000000000001201,520000000000000702,520000000000000102,
   'M52-RC-001','2025-03-01',520000000000000301,@demo_user,'STANDARD','QUALIFIED',100000,'APPROVED',1,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_receipt_item
  (id,tenant_id,receipt_id,order_item_id,material_id,actual_quantity,qualified_quantity,unqualified_quantity,unit_price,amount,
   use_location,batch_no,disposition_status,wbs_task_id,budget_line_id,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001302,0,520000000000001301,520000000000001202,520000000000000201,100,100,0,1000,100000,
   '主楼','M52-BATCH-001','NOT_REQUIRED',520000000000001002,520000000000000802,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_stock
  (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,
   replenishment_target_qty,replenishment_lead_days,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001401,0,520000000000000301,520000000000000201,80,80000,1000,10,100,7,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_requisition
  (id,tenant_id,project_id,contract_id,partner_id,requisition_code,requisition_date,warehouse_id,requisitioner_id,
   approval_status,total_amount,stock_out_flag,stock_out_by,stock_out_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001501,0,520000000000000001,520000000000000702,520000000000000102,'M52-REQ-001','2025-04-01',
   520000000000000301,@demo_user,'APPROVED',20000,1,@demo_user,NOW(),@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_requisition_item
  (id,tenant_id,requisition_id,material_id,quantity,unit_price,amount,use_location,batch_no,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001502,0,520000000000001501,520000000000000201,20,1000,20000,'主楼','M52-BATCH-001',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_stock_txn
  (id,tenant_id,warehouse_id,material_id,txn_type,quantity,available_after,unit_cost,amount,source_type,source_id,source_line_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001601,0,520000000000000301,520000000000000201,'IN',100,100,1000,100000,'MAT_RECEIPT',520000000000001301,520000000000001302,
   @demo_user,'2025-03-01 10:00:00',@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1'),
  (520000000000001602,0,520000000000000301,520000000000000201,'OUT',20,80,1000,20000,'MAT_REQUISITION',520000000000001501,520000000000001502,
   @demo_user,'2025-04-01 10:00:00',@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO cost_item
  (id,tenant_id,project_id,contract_id,partner_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,
   source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001701,0,520000000000000001,520000000000000702,520000000000000102,@demo_subject,'MATERIAL',20000,0,20000,
   'MAT_REQUISITION',520000000000001501,520000000000001502,'2025-04-01','CONFIRMED',1,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');
