-- Run before Flyway V277+ on an existing MySQL database. Any returned row blocks deployment.
SELECT 'V277_ACTIVE_FILE_NAME' AS collision_stage,
       tenant_id, business_type, business_id, file_name AS collision_key, COUNT(*) AS row_count
FROM sys_file
WHERE deleted_flag = 0
GROUP BY tenant_id, business_type, business_id, file_name
HAVING COUNT(*) > 1
UNION ALL
SELECT 'V278_V279_FIRST_64' AS collision_stage,
       tenant_id, business_type, business_id, SUBSTRING(file_name, 1, 64) AS collision_key,
       COUNT(*) AS row_count
FROM sys_file
WHERE deleted_flag = 0
GROUP BY tenant_id, business_type, business_id, SUBSTRING(file_name, 1, 64)
HAVING COUNT(*) > 1;
