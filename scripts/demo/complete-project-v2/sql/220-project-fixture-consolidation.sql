-- CGC-COMPLETE-PROJECT v2 / consolidate shared project fixtures without losing M3 action states.
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

UPDATE pm_project_member
SET project_id=520000000000009002,remark='M3计划分权验收：复用在建项目',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000013006;

UPDATE account_receivable
SET project_id=520000000000009008,
    original_amount=500,collected_amount=500,credited_amount=0,outstanding_amount=0,
    remark='M3缺陷复验质保金已收：复用缺陷处理项目',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012515;

UPDATE account_receivable
SET original_amount=500,collected_amount=500,credited_amount=0,outstanding_amount=0,
    updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012514;

UPDATE closeout_warranty
SET closeout_id=520000000000012307,project_id=520000000000009008,
    warranty_amount=500,remark='M3缺陷复验正向样本：复用缺陷处理项目',
    updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012512;

UPDATE closeout_warranty
SET warranty_amount=500,updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012511;

UPDATE closeout_defect
SET closeout_id=520000000000012307,project_id=520000000000009008,
    remark='M3缺陷复验正向样本：复用缺陷处理项目',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012521;

DELETE FROM project_closeout WHERE tenant_id=0 AND id=520000000000012308;
UPDATE ct_contract
SET project_id=520000000000009008,remark='M3缺陷复验合同：复用缺陷处理项目',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012508;
UPDATE owner_settlement
SET project_id=520000000000009008,remark='M3缺陷复验最终结算：复用缺陷处理项目',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000012510;
DELETE FROM pm_project_member WHERE tenant_id=0 AND id=520000000000012502;
DELETE FROM pm_project WHERE tenant_id=0 AND id IN (520000000000009009,520000000000009011);
