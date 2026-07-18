-- V61__add_company_id_to_org_position.sql (H2)
-- 为岗位表增加所属公司关联

ALTER TABLE org_position
    ADD COLUMN company_id BIGINT NULL;
CREATE INDEX idx_op_company ON org_position(company_id);
