-- H2 equivalent of V305 lightweight construction accounting catalog.
ALTER TABLE cost_subject
    ADD COLUMN ledger_flag TINYINT NOT NULL DEFAULT 0;
ALTER TABLE cost_subject
    ADD CONSTRAINT ck_cost_subject_ledger_flag CHECK (ledger_flag IN (0,1));
CREATE INDEX idx_cost_subject_ledger_tree
    ON cost_subject(tenant_id,ledger_flag,parent_id,status,deleted_flag);

ALTER TABLE accounting_entry ADD COLUMN partner_id BIGINT DEFAULT NULL;
ALTER TABLE accounting_entry ADD COLUMN department_id BIGINT DEFAULT NULL;
ALTER TABLE accounting_entry ADD COLUMN employee_id BIGINT DEFAULT NULL;
CREATE INDEX idx_accounting_entry_partner ON accounting_entry(tenant_id,partner_id,entry_date);
CREATE INDEX idx_accounting_entry_department ON accounting_entry(tenant_id,department_id,entry_date);
CREATE INDEX idx_accounting_entry_employee ON accounting_entry(tenant_id,employee_id,entry_date);

ALTER TABLE accounting_entry_line
    ADD COLUMN accounting_subject_id BIGINT DEFAULT NULL;
CREATE INDEX idx_accounting_entry_line_accounting_subject
    ON accounting_entry_line(tenant_id,accounting_subject_id,entry_id);
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT fk_accounting_entry_line_accounting_subject
        FOREIGN KEY (accounting_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT;

CREATE TABLE accounting_subject_dimension_rule (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  accounting_subject_id BIGINT NOT NULL,
  project_requirement VARCHAR(16) NOT NULL DEFAULT 'NONE',
  contract_requirement VARCHAR(16) NOT NULL DEFAULT 'NONE',
  partner_requirement VARCHAR(16) NOT NULL DEFAULT 'NONE',
  department_requirement VARCHAR(16) NOT NULL DEFAULT 'NONE',
  employee_requirement VARCHAR(16) NOT NULL DEFAULT 'NONE',
  allowed_contract_types VARCHAR(200) DEFAULT NULL,
  allowed_partner_types VARCHAR(200) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_accounting_subject_dimension_rule UNIQUE (tenant_id,accounting_subject_id),
  CONSTRAINT fk_accounting_subject_dimension_rule_subject
    FOREIGN KEY (accounting_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
  CONSTRAINT ck_accounting_subject_project_requirement CHECK (project_requirement IN ('REQUIRED','RECOMMENDED','OPTIONAL','INHERITED','NONE')),
  CONSTRAINT ck_accounting_subject_contract_requirement CHECK (contract_requirement IN ('REQUIRED','RECOMMENDED','OPTIONAL','INHERITED','NONE')),
  CONSTRAINT ck_accounting_subject_partner_requirement CHECK (partner_requirement IN ('REQUIRED','RECOMMENDED','OPTIONAL','INHERITED','NONE')),
  CONSTRAINT ck_accounting_subject_department_requirement CHECK (department_requirement IN ('REQUIRED','RECOMMENDED','OPTIONAL','INHERITED','NONE')),
  CONSTRAINT ck_accounting_subject_employee_requirement CHECK (employee_requirement IN ('REQUIRED','RECOMMENDED','OPTIONAL','INHERITED','NONE'))
);

CREATE TABLE accounting_cost_carryover_mapping (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  category_code VARCHAR(8) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  fulfillment_subject_id BIGINT NOT NULL,
  expense_subject_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_accounting_cost_carryover_category UNIQUE (tenant_id,category_code),
  CONSTRAINT uk_accounting_cost_carryover_source UNIQUE (tenant_id,fulfillment_subject_id),
  CONSTRAINT uk_accounting_cost_carryover_target UNIQUE (tenant_id,expense_subject_id),
  CONSTRAINT fk_accounting_cost_carryover_fulfillment FOREIGN KEY (fulfillment_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
  CONSTRAINT fk_accounting_cost_carryover_expense FOREIGN KEY (expense_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
  CONSTRAINT ck_accounting_cost_carryover_status CHECK (status IN ('ENABLE','DISABLE'))
);

CREATE TABLE accounting_cost_carryover (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  carryover_date DATE NOT NULL,
  balance_hash CHAR(64) NOT NULL,
  entry_id BIGINT DEFAULT NULL,
  total_amount DECIMAL(18,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_accounting_cost_carryover_snapshot UNIQUE (tenant_id,project_id,contract_id,carryover_date,balance_hash),
  CONSTRAINT fk_accounting_cost_carryover_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_accounting_cost_carryover_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_accounting_cost_carryover_entry FOREIGN KEY (entry_id) REFERENCES accounting_entry(id) ON DELETE RESTRICT,
  CONSTRAINT ck_accounting_cost_carryover_amount CHECK (total_amount > 0),
  CONSTRAINT ck_accounting_cost_carryover_state CHECK (status IN ('DRAFT','POSTED','REVERSED'))
);

CREATE TABLE accounting_subject_legacy_review (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_subject_id BIGINT NOT NULL,
  source_subject_code VARCHAR(64) NOT NULL,
  source_subject_name VARCHAR(200) NOT NULL,
  suggested_subject_code VARCHAR(64) DEFAULT NULL,
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(500) DEFAULT NULL,
  reviewed_by BIGINT DEFAULT NULL,
  reviewed_at TIMESTAMP DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_accounting_subject_legacy_review UNIQUE (tenant_id,source_subject_id),
  CONSTRAINT fk_accounting_subject_legacy_review_source FOREIGN KEY (source_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
  CONSTRAINT ck_accounting_subject_legacy_review_status CHECK (review_status IN ('PENDING','CONFIRMED','IGNORED'))
);

-- 固定会计科目一级目录。既有 6001 保留 ID 和历史引用，仅纳入正式总账目录。
INSERT INTO cost_subject
  (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,remark,created_at,updated_at,deleted_flag,ledger_flag)
SELECT 305100000000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id,catalog.sort_order),
       tenants.tenant_id,0,catalog.subject_code,catalog.subject_name,'GENERAL_LEDGER',catalog.account_category,
       1,catalog.sort_order,'ENABLE','建筑工程施工企业固定会计科目',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag=0) tenants
JOIN (
  SELECT '1001' subject_code,'库存现金' subject_name,'ASSET' account_category,101 sort_order
  UNION ALL SELECT '1002','银行存款','ASSET',102
  UNION ALL SELECT '1122','应收账款','ASSET',103
  UNION ALL SELECT '1126','合同资产','ASSET',104
  UNION ALL SELECT '1451','合同履约成本','ASSET',105
  UNION ALL SELECT '1601','固定资产','ASSET',106
  UNION ALL SELECT '2001','短期借款','LIABILITY',201
  UNION ALL SELECT '2202','应付账款','LIABILITY',202
  UNION ALL SELECT '2206','合同负债','LIABILITY',203
  UNION ALL SELECT '2211','应付职工薪酬','LIABILITY',204
  UNION ALL SELECT '2221','应交税费','LIABILITY',205
  UNION ALL SELECT '4401','合同结算','SETTLEMENT',401
  UNION ALL SELECT '6001','主营业务收入','REVENUE',501
  UNION ALL SELECT '6401','主营业务成本','COST',601
  UNION ALL SELECT '6402','其他业务成本','COST',602
  UNION ALL SELECT '6403','税金及附加','COST',603
  UNION ALL SELECT '6602','管理费用','COST',604
  UNION ALL SELECT '6603','财务费用','COST',605
  UNION ALL SELECT '6801','所得税费用','COST',606
) catalog ON 1=1
WHERE NOT EXISTS (
  SELECT 1 FROM cost_subject existing
  WHERE existing.tenant_id=tenants.tenant_id AND existing.subject_code=catalog.subject_code AND existing.deleted_flag=0
);

-- 二级科目。
INSERT INTO cost_subject
  (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,remark,created_at,updated_at,deleted_flag,ledger_flag)
SELECT 305200000000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id,catalog.subject_code),
       tenants.tenant_id,parent.id,catalog.subject_code,catalog.subject_name,'GENERAL_LEDGER',parent.account_category,
       2,catalog.sort_order,'ENABLE','建筑工程施工企业固定会计科目',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag=0) tenants
JOIN (
  SELECT '1002' parent_code,'1002.01' subject_code,'基本账户' subject_name,1 sort_order
  UNION ALL SELECT '1002','1002.02','一般账户',2
  UNION ALL SELECT '1002','1002.03','项目专户',3
  UNION ALL SELECT '1126','1126.01','已完工未结算工程款',1
  UNION ALL SELECT '1126','1126.02','应收工程质量保证金',2
  UNION ALL SELECT '1451','1451.01','材料费',1
  UNION ALL SELECT '1451','1451.02','设备费',2
  UNION ALL SELECT '1451','1451.03','人工费',3
  UNION ALL SELECT '1451','1451.04','机械费',4
  UNION ALL SELECT '1451','1451.05','分包费',5
  UNION ALL SELECT '1451','1451.06','措施费',6
  UNION ALL SELECT '1451','1451.07','项目现场管理费',7
  UNION ALL SELECT '1451','1451.08','其他直接费',8
  UNION ALL SELECT '1601','1601.01','房屋及建筑物',1
  UNION ALL SELECT '1601','1601.02','施工机械',2
  UNION ALL SELECT '1601','1601.03','运输车辆',3
  UNION ALL SELECT '1601','1601.04','办公设备',4
  UNION ALL SELECT '1601','1601.05','电子设备',5
  UNION ALL SELECT '2001','2001.01','银行借款',1
  UNION ALL SELECT '2001','2001.02','其他短期借款',2
  UNION ALL SELECT '2202','2202.01','材料款',1
  UNION ALL SELECT '2202','2202.02','设备款',2
  UNION ALL SELECT '2202','2202.03','劳务分包款',3
  UNION ALL SELECT '2202','2202.04','专业分包款',4
  UNION ALL SELECT '2202','2202.05','机械租赁款',5
  UNION ALL SELECT '2206','2206.01','预收工程款',1
  UNION ALL SELECT '2206','2206.02','已结算未履约款',2
  UNION ALL SELECT '2211','2211.01','工资',1
  UNION ALL SELECT '2221','2221.01','应交增值税',1
  UNION ALL SELECT '2221','2221.02','未交增值税',2
  UNION ALL SELECT '2221','2221.03','预交增值税',3
  UNION ALL SELECT '2221','2221.04','企业所得税',4
  UNION ALL SELECT '4401','4401.01','价款结算',1
  UNION ALL SELECT '4401','4401.02','收入结转',2
  UNION ALL SELECT '6001','6001.01','建筑工程收入',1
  UNION ALL SELECT '6401','6401.01','材料成本',1
  UNION ALL SELECT '6401','6401.02','设备成本',2
  UNION ALL SELECT '6401','6401.03','人工成本',3
  UNION ALL SELECT '6401','6401.04','机械成本',4
  UNION ALL SELECT '6401','6401.05','分包成本',5
  UNION ALL SELECT '6401','6401.06','措施成本',6
  UNION ALL SELECT '6401','6401.07','项目现场管理成本',7
  UNION ALL SELECT '6401','6401.08','其他直接成本',8
  UNION ALL SELECT '6402','6402.01','材料销售成本',1
  UNION ALL SELECT '6402','6402.02','机械出租成本',2
  UNION ALL SELECT '6402','6402.03','其他业务成本',3
  UNION ALL SELECT '6403','6403.01','城市维护建设税',1
  UNION ALL SELECT '6403','6403.02','教育费附加',2
  UNION ALL SELECT '6403','6403.03','地方教育附加',3
  UNION ALL SELECT '6403','6403.04','印花税',4
  UNION ALL SELECT '6403','6403.05','房产税',5
  UNION ALL SELECT '6403','6403.06','土地使用税',6
  UNION ALL SELECT '6403','6403.07','车船税',7
  UNION ALL SELECT '6403','6403.08','其他税费',8
  UNION ALL SELECT '6602','6602.01','公司管理人员工资',1
  UNION ALL SELECT '6602','6602.02','办公费',2
  UNION ALL SELECT '6602','6602.03','差旅费',3
  UNION ALL SELECT '6602','6602.04','业务招待费',4
  UNION ALL SELECT '6602','6602.05','招投标费用',5
  UNION ALL SELECT '6602','6602.06','其他管理费用',6
  UNION ALL SELECT '6603','6603.01','利息支出',1
  UNION ALL SELECT '6603','6603.02','银行手续费',2
) catalog ON 1=1
JOIN cost_subject parent ON parent.tenant_id=tenants.tenant_id AND parent.subject_code=catalog.parent_code AND parent.deleted_flag=0
WHERE NOT EXISTS (
  SELECT 1 FROM cost_subject existing
  WHERE existing.tenant_id=tenants.tenant_id AND existing.subject_code=catalog.subject_code AND existing.deleted_flag=0
);

-- 增值税三级明细。
INSERT INTO cost_subject
  (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,remark,created_at,updated_at,deleted_flag,ledger_flag)
SELECT 305300000000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id,catalog.subject_code),
       tenants.tenant_id,parent.id,catalog.subject_code,catalog.subject_name,'GENERAL_LEDGER','LIABILITY',
       3,catalog.sort_order,'ENABLE','建筑工程施工企业固定会计科目',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag=0) tenants
JOIN (
  SELECT '2221.01.01' subject_code,'进项税额' subject_name,1 sort_order
  UNION ALL SELECT '2221.01.02','销项税额',2
  UNION ALL SELECT '2221.01.03','转出未交增值税',3
) catalog ON 1=1
JOIN cost_subject parent ON parent.tenant_id=tenants.tenant_id AND parent.subject_code='2221.01' AND parent.deleted_flag=0
WHERE NOT EXISTS (
  SELECT 1 FROM cost_subject existing
  WHERE existing.tenant_id=tenants.tenant_id AND existing.subject_code=catalog.subject_code AND existing.deleted_flag=0
);

UPDATE cost_subject
SET ledger_flag=1, subject_type='GENERAL_LEDGER', updated_at=CURRENT_TIMESTAMP
WHERE deleted_flag=0 AND subject_code IN (
 '1001','1002','1002.01','1002.02','1002.03','1122','1126','1126.01','1126.02',
 '1451','1451.01','1451.02','1451.03','1451.04','1451.05','1451.06','1451.07','1451.08',
 '1601','1601.01','1601.02','1601.03','1601.04','1601.05','2001','2001.01','2001.02',
 '2202','2202.01','2202.02','2202.03','2202.04','2202.05','2206','2206.01','2206.02',
 '2211','2211.01','2221','2221.01','2221.01.01','2221.01.02','2221.01.03','2221.02','2221.03','2221.04',
 '4401','4401.01','4401.02','6001','6001.01',
 '6401','6401.01','6401.02','6401.03','6401.04','6401.05','6401.06','6401.07','6401.08',
 '6402','6402.01','6402.02','6402.03','6403','6403.01','6403.02','6403.03','6403.04','6403.05','6403.06','6403.07','6403.08',
 '6602','6602.01','6602.02','6602.03','6602.04','6602.05','6602.06','6603','6603.01','6603.02','6801'
);

UPDATE cost_subject SET subject_name='建筑工程收入',updated_at=CURRENT_TIMESTAMP
WHERE subject_code='6001.01' AND ledger_flag=1 AND deleted_flag=0;

-- 为正式目录逐科目固化辅助核算要求。
INSERT INTO accounting_subject_dimension_rule
  (id,tenant_id,accounting_subject_id,project_requirement,contract_requirement,partner_requirement,
   department_requirement,employee_requirement,allowed_contract_types,allowed_partner_types)
SELECT 305400000000000 + ROW_NUMBER() OVER (ORDER BY subject.tenant_id,subject.subject_code),
       subject.tenant_id,subject.id,
       CASE
         WHEN subject.subject_code LIKE '6602%' THEN 'NONE'
         WHEN subject.subject_code LIKE '6603%' OR subject.subject_code LIKE '1002%' OR subject.subject_code='1001' THEN 'INHERITED'
         WHEN subject.subject_code LIKE '1122%' OR subject.subject_code LIKE '1126%' OR subject.subject_code LIKE '1451%'
           OR subject.subject_code LIKE '2202%' OR subject.subject_code LIKE '2206%' OR subject.subject_code LIKE '4401%'
           OR subject.subject_code LIKE '6001%' OR subject.subject_code LIKE '6401%' THEN 'REQUIRED'
         ELSE 'OPTIONAL' END,
       CASE
         WHEN subject.subject_code LIKE '6602%' THEN 'NONE'
         WHEN subject.subject_code LIKE '6603%' OR subject.subject_code LIKE '1002%' OR subject.subject_code='1001' THEN 'INHERITED'
         WHEN subject.subject_code LIKE '1122%' OR subject.subject_code LIKE '1126%' OR subject.subject_code LIKE '1451%'
           OR subject.subject_code LIKE '2202%' OR subject.subject_code LIKE '2206%' OR subject.subject_code LIKE '4401%'
           OR subject.subject_code LIKE '6001%' OR subject.subject_code LIKE '6401%' THEN 'REQUIRED'
         ELSE 'OPTIONAL' END,
       CASE
         WHEN subject.subject_code LIKE '1122%' OR subject.subject_code LIKE '2202%' OR subject.subject_code LIKE '2206%' THEN 'REQUIRED'
         WHEN subject.subject_code LIKE '1126%' OR subject.subject_code LIKE '4401%' OR subject.subject_code LIKE '6001%' THEN 'RECOMMENDED'
         WHEN subject.subject_code LIKE '1451%' OR subject.subject_code LIKE '6602%' OR subject.subject_code LIKE '6603%' THEN 'OPTIONAL'
         WHEN subject.subject_code LIKE '1002%' OR subject.subject_code='1001' THEN 'INHERITED'
         ELSE 'NONE' END,
       CASE WHEN subject.subject_code LIKE '6602%' THEN 'REQUIRED'
            WHEN subject.subject_code LIKE '1451%' OR subject.subject_code LIKE '6603%' THEN 'RECOMMENDED'
            ELSE 'NONE' END,
       CASE WHEN subject.subject_code LIKE '1451%' OR subject.subject_code LIKE '6602%' THEN 'OPTIONAL' ELSE 'NONE' END,
       CASE
         WHEN subject.subject_code LIKE '1122%' OR subject.subject_code LIKE '1126%' OR subject.subject_code LIKE '2206%'
           OR subject.subject_code LIKE '4401%' OR subject.subject_code LIKE '6001%' THEN 'MAIN'
         WHEN subject.subject_code IN ('1451.01','2202.01') THEN 'MAIN,PURCHASE'
         WHEN subject.subject_code IN ('1451.02','2202.02') THEN 'PURCHASE'
         WHEN subject.subject_code IN ('1451.03','1451.05','2202.03','2202.04') THEN 'SUB'
         WHEN subject.subject_code IN ('1451.04','2202.05') THEN 'LEASE'
         WHEN subject.subject_code LIKE '1451%' OR subject.subject_code LIKE '6401%' THEN 'MAIN,PURCHASE,SUB,LEASE,SERVICE'
         ELSE NULL END,
       CASE
         WHEN subject.subject_code IN ('2202.01','2202.02') THEN 'SUPPLIER'
         WHEN subject.subject_code IN ('2202.03','2202.04') THEN 'SUBCONTRACTOR'
         WHEN subject.subject_code='2202.05' THEN 'LESSOR'
         ELSE NULL END
FROM cost_subject subject
WHERE subject.ledger_flag=1 AND subject.deleted_flag=0
  AND NOT EXISTS (
    SELECT 1 FROM accounting_subject_dimension_rule existing
    WHERE existing.tenant_id=subject.tenant_id AND existing.accounting_subject_id=subject.id
  );
INSERT INTO accounting_cost_carryover_mapping
  (id,tenant_id,category_code,category_name,fulfillment_subject_id,expense_subject_id,status)
SELECT 305500000000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id,catalog.category_code),
       tenants.tenant_id,catalog.category_code,catalog.category_name,source_subject.id,target_subject.id,'ENABLE'
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag=0) tenants
JOIN (
  SELECT '01' category_code,'材料费' category_name,'1451.01' source_code,'6401.01' target_code
  UNION ALL SELECT '02','设备费','1451.02','6401.02'
  UNION ALL SELECT '03','人工费','1451.03','6401.03'
  UNION ALL SELECT '04','机械费','1451.04','6401.04'
  UNION ALL SELECT '05','分包费','1451.05','6401.05'
  UNION ALL SELECT '06','措施费','1451.06','6401.06'
  UNION ALL SELECT '07','项目现场管理费','1451.07','6401.07'
  UNION ALL SELECT '08','其他直接费','1451.08','6401.08'
) catalog ON 1=1
JOIN cost_subject source_subject ON source_subject.tenant_id=tenants.tenant_id AND source_subject.subject_code=catalog.source_code AND source_subject.deleted_flag=0
JOIN cost_subject target_subject ON target_subject.tenant_id=tenants.tenant_id AND target_subject.subject_code=catalog.target_code AND target_subject.deleted_flag=0
WHERE NOT EXISTS (
  SELECT 1 FROM accounting_cost_carryover_mapping existing
  WHERE existing.tenant_id=tenants.tenant_id AND existing.category_code=catalog.category_code
);

-- 旧别名不改写历史凭证；进入待复核清单并提供建议科目。
INSERT INTO accounting_subject_legacy_review
  (id,tenant_id,source_subject_id,source_subject_code,source_subject_name,suggested_subject_code,review_status,review_note)
SELECT 305600000000000 + ROW_NUMBER() OVER (ORDER BY subject.tenant_id,subject.subject_code),
       subject.tenant_id,subject.id,subject.subject_code,subject.subject_name,
       CASE subject.subject_code
         WHEN '1002-BANK' THEN '1002.02'
         WHEN '1122-AR' THEN '1122'
         WHEN '1123-PREPAY' THEN '2202'
         WHEN '2202-AP' THEN '2202'
         WHEN '2203-ADVANCE' THEN '2206.01'
       END,
       'PENDING','历史科目只读保留，复核后由后续业务使用正式科目'
FROM cost_subject subject
WHERE subject.deleted_flag=0
  AND subject.subject_code IN ('1002-BANK','1122-AR','1123-PREPAY','2202-AP','2203-ADVANCE')
  AND NOT EXISTS (
    SELECT 1 FROM accounting_subject_legacy_review existing
    WHERE existing.tenant_id=subject.tenant_id AND existing.source_subject_id=subject.id
  );

-- 对能够无歧义识别的历史分录补充正式总账外键，不改编码、名称、金额和日期快照。
UPDATE accounting_entry_line line
SET accounting_subject_id=(
  SELECT subject.id
  FROM cost_subject subject
  WHERE subject.tenant_id=line.tenant_id
    AND subject.ledger_flag=1
    AND subject.deleted_flag=0
    AND subject.subject_code=CASE
      WHEN line.account_code='1002-BANK' OR line.account_code LIKE '1002-BANK-%' THEN '1002.02'
      WHEN line.account_code='1122-AR' THEN '1122'
      WHEN line.account_code='2202-AP' THEN '2202'
      WHEN line.account_code='2203-ADVANCE' THEN '2206.01'
      WHEN line.account_code='6001.01' THEN '6001.01'
      ELSE line.account_code END
)
WHERE line.accounting_subject_id IS NULL
  AND line.deleted_flag=0
  AND EXISTS (
    SELECT 1
    FROM cost_subject subject
    WHERE subject.tenant_id=line.tenant_id
      AND subject.ledger_flag=1
      AND subject.deleted_flag=0
      AND subject.subject_code=CASE
        WHEN line.account_code='1002-BANK' OR line.account_code LIKE '1002-BANK-%' THEN '1002.02'
        WHEN line.account_code='1122-AR' THEN '1122'
        WHEN line.account_code='2202-AP' THEN '2202'
        WHEN line.account_code='2203-ADVANCE' THEN '2206.01'
        WHEN line.account_code='6001.01' THEN '6001.01'
        ELSE line.account_code END
  );
