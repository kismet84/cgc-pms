INSERT INTO md_material_category
    (id,tenant_id,parent_id,category_code,category_name,level_no,order_num,status,
     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT bounds.base_id + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id),
       tenants.tenant_id,NULL,'UNCATEGORIZED','未分类',1,999999,'ENABLE',
       NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'V209 自动承接既有未分类材料；请由材料主数据负责人逐步归类'
FROM (SELECT DISTINCT tenant_id FROM md_material) tenants
CROSS JOIN (
    SELECT GREATEST(COALESCE(MAX(id),0),202000000000000000) AS base_id
    FROM md_material_category
) bounds
WHERE NOT EXISTS (
    SELECT 1 FROM md_material_category c
    WHERE c.tenant_id=tenants.tenant_id AND c.category_code='UNCATEGORIZED'
);

UPDATE md_material m
SET category_id=(
    SELECT MIN(c.id) FROM md_material_category c
    WHERE c.tenant_id=m.tenant_id AND c.category_code='UNCATEGORIZED' AND c.deleted_flag=0
)
WHERE m.category_id IS NULL;
