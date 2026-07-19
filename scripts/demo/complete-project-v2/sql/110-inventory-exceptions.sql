-- CGC-COMPLETE-PROJECT v2 / INVENTORY TRANSFER, QUALITY DISPOSITION AND RETURN REVERSAL
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO mat_receipt
  (id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,receipt_date,warehouse_id,receiver_id,receipt_mode,quality_status,total_amount,
   approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008401,0,520000000000000001,520000000000006191,520000000000006190,520000000000006001,'M52-RC-QUALITY-001','2025-04-10',
   520000000000000301,@demo_user,'STANDARD','PARTIALLY_QUALIFIED',10000,'APPROVED',0,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：到货10吨，其中2吨待质量处置');

INSERT INTO mat_receipt_item
  (id,tenant_id,receipt_id,order_item_id,material_id,actual_quantity,qualified_quantity,unqualified_quantity,unit_price,amount,use_location,batch_no,
   disposition_type,disposition_status,disposition_reason,wbs_task_id,budget_line_id,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008402,0,520000000000008401,520000000000006192,520000000000000201,10,8,2,1000,10000,'主楼B区','M52-BATCH-QA-001',
   'RETURN_TO_SUPPLIER','OPEN','复检强度未达到合同约定。',520000000000001002,520000000000000802,@demo_user,NOW(),@demo_user,NOW(),0,'不合格数量已进入质量处置');

INSERT INTO mat_quality_disposition
  (id,tenant_id,project_id,receipt_id,receipt_item_id,rejected_quantity,disposition_action,status,resolved_quantity,resolved_at,version,created_by,created_at,
   updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008403,0,520000000000000001,520000000000008401,520000000000008402,2,'RETURN_TO_SUPPLIER','OPEN',0,NULL,0,@demo_user,NOW(),
   @demo_user,NOW(),0,'业务异常态：等待供应商退换货');

INSERT INTO mat_warehouse
  (id,tenant_id,project_id,warehouse_code,warehouse_name,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008411,0,520000000000000001,'M52-WH-SECONDARY','演示二级周转仓','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'库存调拨目标仓');

INSERT INTO mat_stock
  (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,replenishment_target_qty,replenishment_lead_days,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008412,0,520000000000008411,520000000000000201,0,0,0,0,20,2,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：启用但尚无库存');

INSERT INTO mat_stock_transfer
  (id,tenant_id,project_id,source_stock_id,target_stock_id,source_warehouse_id,target_warehouse_id,material_id,quantity,unit_cost,amount,idempotency_key,status,
   completed_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008413,0,520000000000000001,520000000000001401,520000000000008412,520000000000000301,520000000000008411,
   520000000000000201,5,1000,5000,'M52:STOCK-TRANSFER:PENDING:001','PENDING',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：调拨已申请，尚未执行');

INSERT INTO mat_material_return
  (id,tenant_id,project_id,contract_id,warehouse_id,requisition_id,return_code,return_date,status,reason,idempotency_key,total_amount,confirmed_by,confirmed_at,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark,reversed_by,reversed_at,reversal_reason,version)
VALUES
  (520000000000008421,0,520000000000000001,520000000000000702,520000000000000301,520000000000001501,'M52-MAT-RETURN-REVERSED-001','2025-04-05',
   'REVERSED','现场余料退回主仓','M52:MATERIAL-RETURN:001',5000,@demo_user,'2025-04-05 10:00:00',@demo_user,NOW(),@demo_user,NOW(),0,
   '业务异常态：退料后发现批次录入错误，已冲销',@demo_user,'2025-04-05 11:00:00','退料批次选择错误，恢复原领料事实',2);

INSERT INTO mat_material_return_item
  (id,tenant_id,return_id,requisition_item_id,original_stock_txn_id,original_cost_item_id,material_id,quantity,unit_cost,amount,created_by,created_at,
   updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008422,0,520000000000008421,520000000000001502,520000000000001602,520000000000001701,520000000000000201,5,1000,5000,
   @demo_user,NOW(),@demo_user,NOW(),0,'退料及冲销共用历史明细');

INSERT INTO mat_stock_txn
  (id,tenant_id,warehouse_id,material_id,txn_type,quantity,available_after,unit_cost,amount,source_type,source_id,source_line_id,created_by,created_at,
   updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008423,0,520000000000000301,520000000000000201,'IN',5,85,1000,5000,'MATERIAL_RETURN',520000000000008421,520000000000008422,
   @demo_user,'2025-04-05 10:00:00',@demo_user,NOW(),0,'退料入库流水'),
  (520000000000008424,0,520000000000000301,520000000000000201,'OUT',5,80,1000,5000,'MATERIAL_RETURN_REVERSAL',520000000000008421,520000000000008422,
   @demo_user,'2025-04-05 11:00:00',@demo_user,NOW(),0,'冲销退料出库流水');

INSERT INTO cost_item
  (id,tenant_id,project_id,contract_id,partner_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,
   cost_date,cost_status,generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008425,0,520000000000000001,520000000000000702,520000000000000102,@demo_subject,'MATERIAL',-5000,0,-5000,'MATERIAL_RETURN',
   520000000000008421,520000000000008422,'2025-04-05','CONFIRMED',1,@demo_user,NOW(),@demo_user,NOW(),0,'退料冲减原领料成本'),
  (520000000000008426,0,520000000000000001,520000000000000702,520000000000000102,@demo_subject,'MATERIAL',5000,0,5000,'MATERIAL_RETURN_REVERSAL',
   520000000000008421,520000000000008422,'2025-04-05','CONFIRMED',1,@demo_user,NOW(),@demo_user,NOW(),0,'冲销退料，恢复原领料成本');
