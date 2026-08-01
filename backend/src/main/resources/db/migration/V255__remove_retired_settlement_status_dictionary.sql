DELETE FROM sys_dict_data
WHERE dict_type_id IN (
    SELECT id FROM sys_dict_type WHERE dict_code = 'settlement_status'
);

DELETE FROM sys_dict_type
WHERE dict_code = 'settlement_status';
