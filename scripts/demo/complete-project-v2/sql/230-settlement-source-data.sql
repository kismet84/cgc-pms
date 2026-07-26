-- CGC-COMPLETE-PROJECT v2 / SETTLEMENT SERVER-AUTHORITATIVE SOURCE
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,
   paid_amount,signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000014201,0,520000000000009002,'CT-20260726-032','结算闭环验收分包合同','SUB',
   520000000000000101,520000000000000103,500000,500000,0,'2026-01-10','2026-01-10','2026-12-31',
   'PERFORMING','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,'ISSUE-053-032 专属可恢复结算候选',0,0)
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),contract_code=VALUES(contract_code),contract_name=VALUES(contract_name),
  contract_type=VALUES(contract_type),party_a_id=VALUES(party_a_id),party_b_id=VALUES(party_b_id),
  contract_amount=VALUES(contract_amount),current_amount=VALUES(current_amount),contract_status=VALUES(contract_status),
  approval_status=VALUES(approval_status),updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,
  remark=VALUES(remark);

INSERT INTO ct_contract_item
  (id,tenant_id,contract_id,item_code,item_name,item_spec,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,
   sort_order,created_by,updated_by,remark,created_at,updated_at,deleted_flag)
VALUES
  (520000000000002103,0,520000000000000703,'M52-SUB-ITEM-001','主体结构劳务','按已审批计量据实结算','项',10,200000,2000000,
   0,0,2000000,1,@demo_user,@demo_user,'结算来源合同清单项',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE
  contract_id=VALUES(contract_id),item_code=VALUES(item_code),item_name=VALUES(item_name),item_spec=VALUES(item_spec),
  unit=VALUES(unit),quantity=VALUES(quantity),unit_price=VALUES(unit_price),amount=VALUES(amount),
  amount_without_tax=VALUES(amount_without_tax),updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,
  remark=VALUES(remark);

INSERT INTO ct_contract_item
  (id,tenant_id,contract_id,item_code,item_name,item_spec,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,
   sort_order,created_by,updated_by,remark,created_at,updated_at,deleted_flag)
VALUES
  (520000000000014202,0,520000000000014201,'CTI-20260726-032','二次结构劳务','按已审批计量据实结算','项',10,50000,500000,
   0,0,500000,1,@demo_user,@demo_user,'ISSUE-053-032 结算来源合同清单项',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE
  contract_id=VALUES(contract_id),item_code=VALUES(item_code),item_name=VALUES(item_name),item_spec=VALUES(item_spec),
  unit=VALUES(unit),quantity=VALUES(quantity),unit_price=VALUES(unit_price),amount=VALUES(amount),
  amount_without_tax=VALUES(amount_without_tax),updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,
  remark=VALUES(remark);

INSERT INTO sub_measure
  (id,tenant_id,project_id,contract_id,partner_id,measure_code,measure_period,measure_date,reported_amount,approved_amount,
   deduction_amount,net_amount,approval_status,cost_generated_flag,status,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark)
VALUES
  (520000000000014203,0,520000000000009002,520000000000014201,520000000000000103,'SM-20260726-032',
   '2026-06','2026-06-30',100000,100000,0,100000,'APPROVED',0,'CONFIRMED',
   @demo_user,NOW(),@demo_user,NOW(),0,'ISSUE-053-032 专属已审批计量')
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),contract_id=VALUES(contract_id),partner_id=VALUES(partner_id),
  measure_code=VALUES(measure_code),measure_period=VALUES(measure_period),measure_date=VALUES(measure_date),
  reported_amount=VALUES(reported_amount),approved_amount=VALUES(approved_amount),deduction_amount=VALUES(deduction_amount),
  net_amount=VALUES(net_amount),approval_status=VALUES(approval_status),status=VALUES(status),
  updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);

INSERT INTO sub_measure_item
  (id,tenant_id,measure_id,contract_item_id,item_name,unit,contract_quantity,current_quantity,cumulative_quantity,
   unit_price,amount,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000014204,0,520000000000014203,520000000000014202,'二次结构劳务','项',10,2,2,50000,100000,
   @demo_user,NOW(),@demo_user,NOW(),0,'ISSUE-053-032 专属计量来源')
ON DUPLICATE KEY UPDATE
  measure_id=VALUES(measure_id),contract_item_id=VALUES(contract_item_id),item_name=VALUES(item_name),unit=VALUES(unit),
  contract_quantity=VALUES(contract_quantity),current_quantity=VALUES(current_quantity),
  cumulative_quantity=VALUES(cumulative_quantity),unit_price=VALUES(unit_price),amount=VALUES(amount),
  updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);

UPDATE sub_measure_item
SET contract_item_id=520000000000002103,contract_quantity=10,current_quantity=1,cumulative_quantity=1,
    unit_price=200000,amount=200000,updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000002102 AND measure_id=520000000000002101 AND deleted_flag=0;

UPDATE stl_settlement_item
SET item_name='主体结构劳务',unit='项',quantity=1,unit_price=200000,amount=200000,
    source_type='CT_CONTRACT',source_id=520000000000002103,updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000002202 AND settlement_id=520000000000002201 AND deleted_flag=0;

INSERT INTO sys_menu
  (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
   created_by,updated_by,remark,created_at,updated_at,deleted_flag)
VALUES
  (520000000000014001,0,945,'新建结算','BUTTON',NULL,NULL,'settlement:add',NULL,1,'ENABLE',0,
   @demo_user,@demo_user,'M6结算演示权限',NOW(),NOW(),0),
  (520000000000014002,0,945,'编辑结算','BUTTON',NULL,NULL,'settlement:edit',NULL,2,'ENABLE',0,
   @demo_user,@demo_user,'M6结算演示权限',NOW(),NOW(),0),
  (520000000000014003,0,945,'删除结算','BUTTON',NULL,NULL,'settlement:delete',NULL,3,'ENABLE',0,
   @demo_user,@demo_user,'M6结算演示权限',NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),perms=VALUES(perms),
  order_num=VALUES(order_num),status='ENABLE',visible=0,updated_by=VALUES(updated_by),updated_at=NOW(),
  deleted_flag=0,remark=VALUES(remark);

UPDATE sys_menu
SET parent_id=945,order_num=4,status='ENABLE',visible=0,updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=607 AND perms='settlement:submit' AND deleted_flag=0;

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id) VALUES
  (520000000000014101,0,1,945),
  (520000000000014102,0,1,520000000000014001),
  (520000000000014103,0,1,520000000000014002),
  (520000000000014104,0,1,520000000000014003),
  (520000000000014105,0,1,607),
  (520000000000014106,0,4,945),
  (520000000000014107,0,4,520000000000014001),
  (520000000000014108,0,4,520000000000014002),
  (520000000000014109,0,4,520000000000014003),
  (520000000000014110,0,4,607);

UPDATE wf_template_node n
JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
JOIN sys_user u ON u.tenant_id=t.tenant_id AND u.username='admin'
  AND u.status='ENABLE' AND u.deleted_flag=0
SET n.approver_config=JSON_OBJECT('type','USER','userId',u.id),
    n.updated_by=@demo_user,n.updated_at=NOW()
WHERE t.tenant_id=0 AND t.business_type='SETTLEMENT'
  AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0;
