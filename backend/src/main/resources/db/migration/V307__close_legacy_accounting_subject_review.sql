ALTER TABLE fund_account
  ADD COLUMN accounting_subject_code VARCHAR(64) NULL COMMENT '正式总账科目：1001/1002.01/1002.02/1002.03' AFTER account_type,
  ADD CONSTRAINT ck_fund_account_accounting_subject CHECK (
    accounting_subject_code IS NULL
    OR (account_type='CASH' AND accounting_subject_code='1001')
    OR (account_type='BANK' AND accounting_subject_code IN ('1002.01','1002.02','1002.03'))
  );

UPDATE fund_account
SET accounting_subject_code='1001'
WHERE account_type='CASH' AND accounting_subject_code IS NULL;

UPDATE accounting_subject_legacy_review
SET suggested_subject_code=NULL,
    review_note='历史银行账户需逐户确认基本户、一般户或项目专户，不得统一映射'
WHERE source_subject_code='1002-BANK' AND review_status='PENDING';

UPDATE accounting_entry_line line
JOIN cost_subject subject
  ON subject.tenant_id=line.tenant_id AND subject.id=line.accounting_subject_id
SET line.accounting_subject_id=NULL
WHERE line.deleted_flag=0
  AND (line.account_code='1002-BANK' OR line.account_code LIKE '1002-BANK-%')
  AND subject.subject_code='1002.02';

CREATE TEMPORARY TABLE m307_tenant (tenant_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO m307_tenant VALUES (0);
INSERT IGNORE INTO m307_tenant SELECT DISTINCT tenant_id FROM sys_role;

SET @m307_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),307000000000000000) FROM sys_menu);
INSERT INTO sys_menu
 (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
  created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT @m307_menu_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id),t.tenant_id,
       COALESCE((SELECT MIN(m.id) FROM sys_menu m
                 WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0
                   AND m.path='/cost/subject/taxonomy'),0),
       '复核历史会计科目','BUTTON',NULL,NULL,'accounting:subject-review',NULL,96,'ENABLE',0,
       NULL,NULL,'HISTORICAL-ACCOUNTING-SUBJECT-REVIEW',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m307_tenant t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m
                  WHERE m.tenant_id=t.tenant_id AND m.perms='accounting:subject-review'
                    AND m.deleted_flag=0);

SET @m307_role_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),307100000000000000) FROM sys_role_menu);
INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT @m307_role_menu_base+ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,m.id),r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND r.status='ENABLE'
  AND m.deleted_flag=0 AND m.perms='accounting:subject-review'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x
                  WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.menu_id=m.id);

DROP TEMPORARY TABLE m307_tenant;
