SET NAMES utf8mb4;

ALTER TABLE finance_reconciliation_run
    ADD CONSTRAINT ck_fin_recon_summary_json CHECK(summary_json IS NULL OR (JSON_VALID(summary_json) AND OCTET_LENGTH(summary_json)<=1048576));
ALTER TABLE invoice_ocr_review
    ADD CONSTRAINT ck_invoice_ocr_raw_json CHECK(JSON_VALID(raw_result_json) AND OCTET_LENGTH(raw_result_json)<=1048576),
    ADD CONSTRAINT ck_invoice_ocr_comparison_json CHECK(comparison_json IS NULL OR (JSON_VALID(comparison_json) AND OCTET_LENGTH(comparison_json)<=1048576));
ALTER TABLE finance_import_batch
    ADD CONSTRAINT ck_fin_import_summary_json CHECK(diff_summary_json IS NULL OR (JSON_VALID(diff_summary_json) AND OCTET_LENGTH(diff_summary_json)<=1048576));
ALTER TABLE finance_import_row
    ADD CONSTRAINT ck_fin_import_input_json CHECK(JSON_VALID(input_json) AND OCTET_LENGTH(input_json)<=1048576),
    ADD CONSTRAINT ck_fin_import_diff_json CHECK(diff_json IS NULL OR (JSON_VALID(diff_json) AND OCTET_LENGTH(diff_json)<=1048576));
ALTER TABLE finance_audit_event
    ADD CONSTRAINT ck_fin_audit_payload_json CHECK(JSON_VALID(payload_json) AND OCTET_LENGTH(payload_json)<=1048576);
ALTER TABLE finance_integration_endpoint
    ADD CONSTRAINT ck_fin_endpoint_config_json CHECK(config_json IS NULL OR (JSON_VALID(config_json) AND OCTET_LENGTH(config_json)<=1048576));
ALTER TABLE finance_integration_message
    ADD CONSTRAINT ck_fin_message_payload_json CHECK(JSON_VALID(payload_json) AND OCTET_LENGTH(payload_json)<=1048576),
    ADD CONSTRAINT ck_fin_message_response_json CHECK(response_json IS NULL OR (JSON_VALID(response_json) AND OCTET_LENGTH(response_json)<=1048576));
ALTER TABLE bank_receipt
    ADD CONSTRAINT ck_bank_receipt_raw_json CHECK(JSON_VALID(raw_payload_json) AND OCTET_LENGTH(raw_payload_json)<=1048576),
    ADD CONSTRAINT ck_bank_receipt_allocation_size CHECK(allocation_json IS NULL OR OCTET_LENGTH(allocation_json)<=1048576);
ALTER TABLE sales_invoice_review
    ADD CONSTRAINT ck_sales_invoice_raw_json CHECK(JSON_VALID(raw_result_json) AND OCTET_LENGTH(raw_result_json)<=1048576),
    ADD CONSTRAINT ck_sales_invoice_comparison_json CHECK(comparison_json IS NULL OR (JSON_VALID(comparison_json) AND OCTET_LENGTH(comparison_json)<=1048576));
ALTER TABLE revenue_import_batch
    ADD CONSTRAINT ck_revenue_import_summary_json CHECK(diff_summary_json IS NULL OR (JSON_VALID(diff_summary_json) AND OCTET_LENGTH(diff_summary_json)<=1048576));
ALTER TABLE revenue_import_row
    ADD CONSTRAINT ck_revenue_import_input_json CHECK(JSON_VALID(input_json) AND OCTET_LENGTH(input_json)<=1048576),
    ADD CONSTRAINT ck_revenue_import_diff_json CHECK(diff_json IS NULL OR (JSON_VALID(diff_json) AND OCTET_LENGTH(diff_json)<=1048576));
ALTER TABLE revenue_audit_event
    ADD CONSTRAINT ck_revenue_audit_payload_json CHECK(JSON_VALID(payload_json) AND OCTET_LENGTH(payload_json)<=1048576);
