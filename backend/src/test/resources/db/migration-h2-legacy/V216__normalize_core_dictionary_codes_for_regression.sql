-- Test-only overlay for frozen legacy fixtures.
-- Production/local databases use db/migration[-h2]/V216 instead.

UPDATE sys_dict_data SET dict_value='CONSTRUCTION', dict_label='施工总承包', order_num=1
WHERE tenant_id=0 AND id=132001;
UPDATE sys_dict_data SET dict_value='PROFESSIONAL_SUBCONTRACT', dict_label='专业分包', order_num=2
WHERE tenant_id=0 AND id=132002;
UPDATE sys_dict_data SET dict_value='LABOR_SUBCONTRACT', dict_label='劳务分包', order_num=3
WHERE tenant_id=0 AND id=132003;
UPDATE sys_dict_data SET dict_value='MATERIAL_PROCUREMENT', dict_label='材料采购', order_num=4
WHERE tenant_id=0 AND id=132004;

MERGE INTO sys_dict_data
    (id,tenant_id,dict_type_id,dict_label,dict_value,list_class,order_num,status) KEY(dict_type_id,dict_value)
VALUES (2160501,0,1005,'建设单位/客户','CUSTOMER','cyan',5,'ENABLE');
