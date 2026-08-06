-- Keep legacy Spring test fixtures compatible with canvas-enabled template versions.
ALTER TABLE biz_document_template_version ADD COLUMN design_schema JSON NULL;

ALTER TABLE biz_document_template DROP CONSTRAINT ck_document_template_business;
ALTER TABLE biz_document_template ADD CONSTRAINT ck_document_template_business
    CHECK (REGEXP_LIKE(business_type, '^[A-Z][A-Z0-9_]{1,79}$'));
ALTER TABLE biz_document_default_binding DROP CONSTRAINT ck_document_default_business;
ALTER TABLE biz_document_default_binding ADD CONSTRAINT ck_document_default_business
    CHECK (REGEXP_LIKE(business_type, '^[A-Z][A-Z0-9_]{1,79}$'));
ALTER TABLE biz_document_generation DROP CONSTRAINT ck_document_generation_business;
ALTER TABLE biz_document_generation ADD CONSTRAINT ck_document_generation_business
    CHECK (REGEXP_LIKE(business_type, '^[A-Z][A-Z0-9_]{1,79}$'));
