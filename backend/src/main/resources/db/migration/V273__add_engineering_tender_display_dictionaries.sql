-- Protected display dictionaries. Business services remain authoritative for allowed values and transitions.
INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 273000100000001, 0, id, 'bid_status', '投标状态', 'STATE_MACHINE', 'ENABLE'
FROM sys_dict_group WHERE tenant_id = 0 AND group_code = 'PROJECT';

INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 273000100000002, 0, id, 'bid_document_type', '投标文件分类', 'SYSTEM', 'ENABLE'
FROM sys_dict_group WHERE tenant_id = 0 AND group_code = 'PROJECT';

INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 273000100000003, 0, id, 'cash_direction', '现金收支方向', 'SYSTEM', 'ENABLE'
FROM sys_dict_group WHERE tenant_id = 0 AND group_code = 'FINANCE';

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
VALUES
    (273000200000001, 0, 273000100000001, '注册', 'PREPARING', 'info', 1, 'ENABLE'),
    (273000200000002, 0, 273000100000001, '投标', 'SUBMITTED', 'primary', 2, 'ENABLE'),
    (273000200000003, 0, 273000100000001, '评标', 'EVALUATING', 'warning', 3, 'ENABLE'),
    (273000200000004, 0, 273000100000001, '中标', 'WON', 'success', 4, 'ENABLE'),
    (273000200000005, 0, 273000100000001, '未中标', 'LOST', 'default', 5, 'ENABLE'),
    (273000200000006, 0, 273000100000001, '已关闭', 'CLOSED', 'default', 6, 'ENABLE'),
    (273000200000007, 0, 273000100000001, '已撤回', 'WITHDRAWN', 'danger', 7, 'ENABLE'),
    (273000200000008, 0, 273000100000001, '已终止', 'TERMINATED', 'danger', 8, 'ENABLE'),
    (273000200000101, 0, 273000100000002, '招标文件', 'TENDER_DOCUMENT', 'primary', 1, 'ENABLE'),
    (273000200000102, 0, 273000100000002, '工程量清单', 'BILL_OF_QUANTITIES', 'primary', 2, 'ENABLE'),
    (273000200000103, 0, 273000100000002, '招标图纸', 'TENDER_DRAWING', 'primary', 3, 'ENABLE'),
    (273000200000104, 0, 273000100000002, '投标报价', 'BID_PRICE', 'warning', 4, 'ENABLE'),
    (273000200000105, 0, 273000100000002, '技术文件', 'TECHNICAL_DOCUMENT', 'warning', 5, 'ENABLE'),
    (273000200000106, 0, 273000100000002, '投标图纸', 'BID_DRAWING', 'warning', 6, 'ENABLE'),
    (273000200000107, 0, 273000100000002, '候选人公示', 'CANDIDATE_NOTICE', 'info', 7, 'ENABLE'),
    (273000200000108, 0, 273000100000002, '中标通知书', 'AWARD_NOTICE', 'success', 8, 'ENABLE'),
    (273000200000109, 0, 273000100000002, '未中标通知', 'LOSS_NOTICE', 'default', 9, 'ENABLE'),
    (273000200000110, 0, 273000100000002, '异议及答复', 'OBJECTION_REPLY', 'warning', 10, 'ENABLE'),
    (273000200000111, 0, 273000100000002, '中标澄清', 'AWARD_CLARIFICATION', 'info', 11, 'ENABLE'),
    (273000200000112, 0, 273000100000002, '其他结果文件', 'OTHER_RESULT', 'default', 12, 'ENABLE'),
    (273000200000201, 0, 273000100000003, '收入', 'IN', 'success', 1, 'ENABLE'),
    (273000200000202, 0, 273000100000003, '支出', 'OUT', 'danger', 2, 'ENABLE');
