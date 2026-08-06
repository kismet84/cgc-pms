-- Canvas schema is optional so existing source templates keep their current rendering path.
ALTER TABLE biz_document_template_version
    ADD COLUMN design_schema JSON NULL AFTER schema_version;

ALTER TABLE biz_document_template DROP CHECK ck_document_template_business;
ALTER TABLE biz_document_template
    ADD CONSTRAINT ck_document_template_business CHECK (business_type REGEXP '^[A-Z][A-Z0-9_]{1,79}$');
ALTER TABLE biz_document_default_binding DROP CHECK ck_document_default_business;
ALTER TABLE biz_document_default_binding
    ADD CONSTRAINT ck_document_default_business CHECK (business_type REGEXP '^[A-Z][A-Z0-9_]{1,79}$');
ALTER TABLE biz_document_generation DROP CHECK ck_document_generation_business;
ALTER TABLE biz_document_generation
    ADD CONSTRAINT ck_document_generation_business CHECK (business_type REGEXP '^[A-Z][A-Z0-9_]{1,79}$');
