CREATE TABLE document_template_code_scope (
    tenant_id BIGINT NOT NULL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

MERGE INTO document_template_code_scope(tenant_id)
KEY(tenant_id)
SELECT DISTINCT tenant_id FROM biz_document_template;
