-- Synthetic canary only, loaded after repository schema migrations in a new 8.0 volume.
INSERT INTO pm_project(id,tenant_id,project_code,project_name,status)
VALUES(100001,100,'M100-ENGINE','主线100升级样本🏗','PREPARING'),
      (100002,101,'M100-OTHER','其他租户样本','PREPARING');
INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type) VALUES
  (100101,100,'M100-A','样本甲方','CUSTOMER'),(100102,100,'M100-B','样本乙方','SUPPLIER'),
  (101101,101,'M100-A','其他甲方','CUSTOMER'),(101102,101,'M100-B','其他乙方','SUPPLIER');
INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,
  contract_amount,current_amount,paid_amount,contract_status,approval_status)
VALUES(100011,100,100001,'M100-CT','升级合同中文🏗','PURCHASE',100101,100102,1234.56,1234.56,12.34,'DRAFT','DRAFT'),
      (100012,101,100002,'M100-CT','隔离合同','PURCHASE',101101,101102,98.76,98.76,0,'DRAFT','DRAFT');
INSERT INTO pay_application(id,tenant_id,project_id,contract_id,apply_code,apply_amount,
  approved_amount,actual_pay_amount,pay_type,pay_status,approval_status)
VALUES(100021,100,100001,100011,'M100-PAY',12.34,12.34,12.34,'DIRECT','PAID','APPROVED');
INSERT INTO pay_record(id,tenant_id,project_id,pay_application_id,contract_id,pay_amount,pay_date,pay_status)
VALUES(100031,100,100001,100021,100011,12.34,'2026-09-06','SUCCESS');
CREATE TABLE m100_engine_bytes(id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,
  payload VARBINARY(32) NOT NULL, label VARCHAR(80) NOT NULL, deleted_flag TINYINT NOT NULL,
  UNIQUE KEY uk_m100_bytes(tenant_id,label));
INSERT INTO m100_engine_bytes VALUES
  (1,100,0x0001FF0D0A7F80,'中文🏗\n引号''与反斜杠\\',0),
  (2,101,0xFEEDBEEF,'跨租户软删除',1);
CREATE PROCEDURE m100_engine_probe() SELECT HEX(payload) FROM m100_engine_bytes ORDER BY id;
