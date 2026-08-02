-- H2 counterpart of V261__add_project_preparing_status.sql.
UPDATE sys_dict_data
SET dict_label='前期', dict_value='DRAFT', list_class='info', order_num=1
WHERE tenant_id=0 AND id=100101;

MERGE INTO sys_dict_data
    (id,tenant_id,dict_type_id,dict_label,dict_value,list_class,order_num,status)
    KEY(dict_type_id,dict_value)
VALUES (2610101,0,1001,'筹备','PREPARING','warning',2,'ENABLE');

UPDATE sys_dict_data
SET dict_label='在建', dict_value='ACTIVE', list_class='primary', order_num=3
WHERE tenant_id=0 AND id=100102;
UPDATE sys_dict_data
SET dict_label='已暂停', dict_value='SUSPENDED', list_class='warning', order_num=4
WHERE tenant_id=0 AND id=100104;
UPDATE sys_dict_data
SET dict_label='已关闭', dict_value='CLOSED', list_class='danger', order_num=5
WHERE tenant_id=0 AND id=100105;
UPDATE sys_dict_data
SET dict_label='已归档', dict_value='ARCHIVED', list_class='default', order_num=6
WHERE tenant_id=0 AND id=100103;
