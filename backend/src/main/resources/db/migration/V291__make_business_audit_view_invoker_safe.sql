CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_business_audit_event AS
SELECT 'FINANCE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash
  FROM finance_audit_event
UNION ALL
SELECT 'REVENUE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash
  FROM revenue_audit_event;
