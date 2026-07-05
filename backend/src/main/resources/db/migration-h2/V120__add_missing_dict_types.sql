-- V120__add_missing_dict_types.sql (H2)
-- 新增缺失的字典类型，覆盖后端硬编码常量

-- ============================================================
-- 1. 新增字典类型
-- ============================================================

INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status) VALUES
(1008, 0, 'common_status',        '通用状态',          'ENABLE'),
(1009, 0, 'wf_instance_status',   '工作流实例状态',     'ENABLE'),
(1010, 0, 'wf_task_status',       '工作流任务状态',     'ENABLE'),
(1011, 0, 'wf_node_status',       '工作流节点状态',     'ENABLE'),
(1012, 0, 'approve_mode',         '审批模式',          'ENABLE'),
(1013, 0, 'settlement_status',    '结算生命周期状态',   'ENABLE'),
(1014, 0, 'settlement_final_status','结算定案状态',     'ENABLE'),
(1015, 0, 'pay_status',           '付款状态',          'ENABLE');

-- ============================================================
-- 2. 新增字典数据
-- ============================================================

-- 2.1 通用状态 (common_status) — ENABLE / DISABLE
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100801, 0, 1008, '启用', 'ENABLE',  'success', 1, 'ENABLE'),
(100802, 0, 1008, '禁用', 'DISABLE', 'danger',  2, 'ENABLE');

-- 2.2 工作流实例状态 (wf_instance_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100901, 0, 1009, '审批中', 'RUNNING',   'primary', 1, 'ENABLE'),
(100902, 0, 1009, '已通过', 'APPROVED',  'success', 2, 'ENABLE'),
(100903, 0, 1009, '已驳回', 'REJECTED',  'danger',  3, 'ENABLE'),
(100904, 0, 1009, '已撤回', 'WITHDRAWN', 'default', 4, 'ENABLE'),
(100905, 0, 1009, '已作废', 'VOIDED',    'default', 5, 'ENABLE');

-- 2.3 工作流任务状态 (wf_task_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101001, 0, 1010, '待处理', 'PENDING',     'warning', 1, 'ENABLE'),
(101002, 0, 1010, '已通过', 'APPROVED',    'success', 2, 'ENABLE'),
(101003, 0, 1010, '已驳回', 'REJECTED',    'danger',  3, 'ENABLE'),
(101004, 0, 1010, '已取消', 'CANCELLED',   'default', 4, 'ENABLE'),
(101005, 0, 1010, '已转办', 'TRANSFERRED', 'primary', 5, 'ENABLE');

-- 2.4 工作流节点状态 (wf_node_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101101, 0, 1011, '等待中', 'WAITING',   'default', 1, 'ENABLE'),
(101102, 0, 1011, '激活中', 'ACTIVE',    'primary', 2, 'ENABLE'),
(101103, 0, 1011, '已完成', 'COMPLETED', 'success', 3, 'ENABLE'),
(101104, 0, 1011, '已驳回', 'REJECTED',  'danger',  4, 'ENABLE'),
(101105, 0, 1011, '已跳过', 'SKIPPED',   'default', 5, 'ENABLE');

-- 2.5 审批模式 (approve_mode)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101201, 0, 1012, '顺序审批', 'SEQUENTIAL',   'primary', 1, 'ENABLE'),
(101202, 0, 1012, '会签审批', 'COUNTERSIGN', 'success', 2, 'ENABLE'),
(101203, 0, 1012, '或签审批', 'OR_SIGN',      'warning', 3, 'ENABLE');

-- 2.6 结算生命周期状态 (settlement_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101301, 0, 1013, '草稿',   'DRAFT',      'default', 1, 'ENABLE'),
(101302, 0, 1013, '已提交', 'SUBMITTED',  'primary', 2, 'ENABLE'),
(101303, 0, 1013, '已通过', 'APPROVED',   'success', 3, 'ENABLE'),
(101304, 0, 1013, '已驳回', 'REJECTED',   'danger',  4, 'ENABLE'),
(101305, 0, 1013, '已作废', 'CANCELLED',  'default', 5, 'ENABLE');

-- 2.7 结算定案状态 (settlement_final_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101401, 0, 1014, '草稿',   'DRAFT',      'default', 1, 'ENABLE'),
(101402, 0, 1014, '已计算', 'CALCULATED', 'primary', 2, 'ENABLE'),
(101403, 0, 1014, '已定案', 'FINALIZED',  'success', 3, 'ENABLE');

-- 2.8 付款状态 (pay_status)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(101501, 0, 1015, '待付款',   'PENDING',  'warning', 1, 'ENABLE'),
(101502, 0, 1015, '未付款',   'UNPAID',   'default', 2, 'ENABLE'),
(101503, 0, 1015, '已付款',   'PAID',     'success', 3, 'ENABLE'),
(101504, 0, 1015, '部分付款', 'PARTIAL',  'primary', 4, 'ENABLE');
