CREATE UNIQUE INDEX uk_org_position_tenant_id ON org_position(tenant_id,id);
CREATE TABLE org_user_position(
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,user_id BIGINT NOT NULL,position_id BIGINT NOT NULL,
 primary_flag INT DEFAULT 0 NOT NULL,status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,effective_from DATE,effective_to DATE,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 FOREIGN KEY(tenant_id,user_id) REFERENCES sys_user(tenant_id,id),FOREIGN KEY(tenant_id,position_id) REFERENCES org_position(tenant_id,id),
 CHECK(primary_flag IN (0,1)),CHECK(status IN ('ACTIVE','INACTIVE')),CHECK(effective_from IS NULL OR effective_to IS NULL OR effective_from<=effective_to));
CREATE UNIQUE INDEX uk_org_user_position ON org_user_position(tenant_id,user_id,position_id);
CREATE INDEX idx_org_position_users ON org_user_position(tenant_id,position_id,status);
