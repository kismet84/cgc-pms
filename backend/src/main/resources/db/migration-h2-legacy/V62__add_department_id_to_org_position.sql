-- V62__add_department_id_to_org_position.sql (H2)
-- 为岗位表增加所属部门关联

ALTER TABLE org_position
    ADD COLUMN department_id BIGINT NULL;
CREATE INDEX idx_op_department ON org_position(department_id);
