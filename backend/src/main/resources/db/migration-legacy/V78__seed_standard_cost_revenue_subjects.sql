-- V78__seed_standard_cost_revenue_subjects.sql
-- 标准化四级会计科目体系种子数据
-- 遵循约定：INSERT IGNORE INTO, tenant_id=0 系统模板, 显式 NOW(), ID 基数 900000+
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ================================================================
-- A. 成本科目 (account_category = 'COST')
-- ================================================================

-- A1. 一级：合同履约成本 (5401)
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900001, 0, 0, '5401', '合同履约成本', 'ROOT', 1, 1, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- A2. 二级 5401.01：招投标及前期费用 + 10个三级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900010, 0, 900001, '5401.01', '招投标及前期费用', 'BID', 2, 1, 'ENABLE', 'COST', NOW(), NOW(), 0);

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900011, 0, 900010, '5401.01.01', '投标费用',            'BID',         3, 1, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900012, 0, 900010, '5401.01.02', '投标保证金',          'BID_DEPOSIT', 3, 2, 'ENABLE', 'RECEIVABLE', NOW(), NOW(), 0),
(900013, 0, 900010, '5401.01.03', '标书制作费',          'BID',         3, 3, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900014, 0, 900010, '5401.01.04', '招投标代理费',        'BID',         3, 4, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900015, 0, 900010, '5401.01.05', '前期勘察费',          'BID',         3, 5, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900016, 0, 900010, '5401.01.06', '前期咨询费',          'BID',         3, 6, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900017, 0, 900010, '5401.01.07', '前期差旅费',          'BID',         3, 7, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900018, 0, 900010, '5401.01.08', '资格预审费',          'BID',         3, 8, 'ENABLE', 'COST',       NOW(), NOW(), 0),
(900019, 0, 900010, '5401.01.09', '履约保证金',          'BID_DEPOSIT', 3, 9, 'ENABLE', 'RECEIVABLE', NOW(), NOW(), 0),
(900020, 0, 900010, '5401.01.10', '前期其他费',          'BID',         3,10, 'ENABLE', 'COST',       NOW(), NOW(), 0);

-- A3. 二级 5401.02：采购阶段成本 + 6个三级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900030, 0, 900001, '5401.02', '采购阶段成本', 'PURCHASE', 2, 2, 'ENABLE', 'COST', NOW(), NOW(), 0);

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900031, 0, 900030, '5401.02.01', '设备采购费', 'PURCHASE', 3, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900032, 0, 900030, '5401.02.02', '材料采购费', 'MATERIAL', 3, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900033, 0, 900030, '5401.02.03', '采购运杂费', 'PURCHASE', 3, 3, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900034, 0, 900030, '5401.02.04', '采购保管费', 'PURCHASE', 3, 4, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900035, 0, 900030, '5401.02.05', '检验试验费', 'TESTING',  3, 5, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900036, 0, 900030, '5401.02.06', '采购管理费', 'PURCHASE', 3, 6, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- A4. 二级 5401.03：施工阶段成本
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900040, 0, 900001, '5401.03', '施工阶段成本', 'CONSTRUCTION', 2, 3, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.01：人工费 + 3个四级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900041, 0, 900040, '5401.03.01', '人工费', 'LABOR', 3, 1, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900042, 0, 900041, '5401.03.01.01', '自有工人工资', 'LABOR', 4, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900043, 0, 900041, '5401.03.01.02', '劳务分包费',   'LABOR', 4, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900044, 0, 900041, '5401.03.01.03', '临时工费用',   'LABOR', 4, 3, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.02：材料费 + 4个四级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900045, 0, 900040, '5401.03.02', '材料费', 'MATERIAL', 3, 2, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900046, 0, 900045, '5401.03.02.01', '结构材料', 'MATERIAL', 4, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900047, 0, 900045, '5401.03.02.02', '装饰材料', 'MATERIAL', 4, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900048, 0, 900045, '5401.03.02.03', '安装材料', 'MATERIAL', 4, 3, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900049, 0, 900045, '5401.03.02.04', '辅助材料', 'MATERIAL', 4, 4, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.03：机械使用费 + 3个四级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900050, 0, 900040, '5401.03.03', '机械使用费', 'MACHINERY', 3, 3, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900051, 0, 900050, '5401.03.03.01', '自有机械费',   'MACHINERY', 4, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900052, 0, 900050, '5401.03.03.02', '租赁机械费',   'MACHINERY', 4, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900053, 0, 900050, '5401.03.03.03', '机械进出场费', 'MACHINERY', 4, 3, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级直达 5401.03.04：工程水电费
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900055, 0, 900040, '5401.03.04', '工程水电费', 'UTILITY', 3, 4, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.05：专业分包费 + 10个四级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900056, 0, 900040, '5401.03.05', '专业分包费', 'SUBCONTRACT', 3, 5, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900057, 0, 900056, '5401.03.05.01', '土方分包',     'SUBCONTRACT', 4, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900058, 0, 900056, '5401.03.05.02', '桩基分包',     'SUBCONTRACT', 4, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900059, 0, 900056, '5401.03.05.03', '防水防腐',     'SUBCONTRACT', 4, 3, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900060, 0, 900056, '5401.03.05.04', '钢结构分包',   'SUBCONTRACT', 4, 4, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900061, 0, 900056, '5401.03.05.05', '幕墙分包',     'SUBCONTRACT', 4, 5, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900062, 0, 900056, '5401.03.05.06', '机电分包',     'SUBCONTRACT', 4, 6, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900063, 0, 900056, '5401.03.05.07', '智能化分包',   'SUBCONTRACT', 4, 7, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900064, 0, 900056, '5401.03.05.08', '消防分包',     'SUBCONTRACT', 4, 8, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900065, 0, 900056, '5401.03.05.09', '精装修分包',   'SUBCONTRACT', 4, 9, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900066, 0, 900056, '5401.03.05.10', '景观绿化分包', 'SUBCONTRACT', 4,10, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.06：措施费 + 11个四级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900067, 0, 900040, '5401.03.06', '措施费', 'MEASURES', 3, 6, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900068, 0, 900067, '5401.03.06.01', '周转材料费',     'MEASURES', 4, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900069, 0, 900067, '5401.03.06.02', '临时设施费',     'MEASURES', 4, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900070, 0, 900067, '5401.03.06.03', '安全文明施工费', 'MEASURES', 4, 3, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900071, 0, 900067, '5401.03.06.04', '夜间施工费',     'MEASURES', 4, 4, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900072, 0, 900067, '5401.03.06.05', '冬雨季施工费',   'MEASURES', 4, 5, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900073, 0, 900067, '5401.03.06.06', '二次搬运费',     'MEASURES', 4, 6, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900074, 0, 900067, '5401.03.06.07', '环境保护费',     'MEASURES', 4, 7, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900075, 0, 900067, '5401.03.06.08', '排水降水费',     'MEASURES', 4, 8, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900076, 0, 900067, '5401.03.06.09', '已完工程保护费', 'MEASURES', 4, 9, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900077, 0, 900067, '5401.03.06.10', '赶工费',         'MEASURES', 4,10, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900078, 0, 900067, '5401.03.06.11', '停工损失',       'MEASURES', 4,11, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- 三级 5401.03.07：其他直接费
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900079, 0, 900040, '5401.03.07', '其他直接费', 'OTHER', 3, 7, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- A5. 二级 5401.04：项目间接费用 + 20个三级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900080, 0, 900001, '5401.04', '项目间接费用', 'OVERHEAD', 2, 4, 'ENABLE', 'COST', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900081, 0, 900080, '5401.04.01', '管理人员薪酬',       'OVERHEAD', 3, 1, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900082, 0, 900080, '5401.04.02', '办公费',             'OVERHEAD', 3, 2, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900083, 0, 900080, '5401.04.03', '差旅交通费',         'OVERHEAD', 3, 3, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900084, 0, 900080, '5401.04.04', '业务招待费',         'OVERHEAD', 3, 4, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900085, 0, 900080, '5401.04.05', '固定资产使用费',     'OVERHEAD', 3, 5, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900086, 0, 900080, '5401.04.06', '低值易耗品摊销',     'OVERHEAD', 3, 6, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900087, 0, 900080, '5401.04.07', '保险费',             'OVERHEAD', 3, 7, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900088, 0, 900080, '5401.04.08', '检测试验费',         'OVERHEAD', 3, 8, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900089, 0, 900080, '5401.04.09', '工程保修费',         'OVERHEAD', 3, 9, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900090, 0, 900080, '5401.04.10', '排污费',             'OVERHEAD', 3,10, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900091, 0, 900080, '5401.04.11', '劳动保护费',         'OVERHEAD', 3,11, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900092, 0, 900080, '5401.04.12', '取暖费',             'OVERHEAD', 3,12, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900093, 0, 900080, '5401.04.13', '材料整理及零星运费', 'OVERHEAD', 3,13, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900094, 0, 900080, '5401.04.14', '材料盘亏及毁损',     'OVERHEAD', 3,14, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900095, 0, 900080, '5401.04.15', '外单位管理费',       'OVERHEAD', 3,15, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900096, 0, 900080, '5401.04.16', '职工教育经费',       'OVERHEAD', 3,16, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900097, 0, 900080, '5401.04.17', '工会经费',           'OVERHEAD', 3,17, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900098, 0, 900080, '5401.04.18', '劳动保险费',         'OVERHEAD', 3,18, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900099, 0, 900080, '5401.04.19', '财务费用',           'OVERHEAD', 3,19, 'ENABLE', 'COST', NOW(), NOW(), 0),
(900100, 0, 900080, '5401.04.20', '其他间接费',         'OVERHEAD', 3,20, 'ENABLE', 'COST', NOW(), NOW(), 0);

-- ================================================================
-- B. 收入科目 (account_category = 'REVENUE')
-- ================================================================

-- B1. 主营业务收入 (6001) + 4个二级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900200, 0, 0, '6001', '主营业务收入', 'REVENUE_MAIN', 1, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900201, 0, 900200, '6001.01', '合同建造收入', 'REVENUE_MAIN', 2, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900202, 0, 900200, '6001.02', '变更签证收入', 'REVENUE_MAIN', 2, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900203, 0, 900200, '6001.03', '索赔收入',     'REVENUE_MAIN', 2, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900204, 0, 900200, '6001.04', '奖励收入',     'REVENUE_MAIN', 2, 4, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);

-- B2. 其他业务收入 (6051) + 5个二级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900210, 0, 0, '6051', '其他业务收入', 'REVENUE_OTHER', 1, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900211, 0, 900210, '6051.01', '材料销售收入',       'REVENUE_OTHER', 2, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900212, 0, 900210, '6051.02', '固定资产出租收入',   'REVENUE_OTHER', 2, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900213, 0, 900210, '6051.03', '周转材料出租收入',   'REVENUE_OTHER', 2, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900214, 0, 900210, '6051.04', '技术服务收入',       'REVENUE_OTHER', 2, 4, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900215, 0, 900210, '6051.05', '劳务服务收入',       'REVENUE_OTHER', 2, 5, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);

-- B3. 营业外收入 (6301) + 5个二级
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES (900220, 0, 0, '6301', '营业外收入', 'REVENUE_EXTRA', 1, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900221, 0, 900220, '6301.01', '违约金收入', 'REVENUE_EXTRA', 2, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900222, 0, 900220, '6301.02', '罚款收入',   'REVENUE_EXTRA', 2, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900223, 0, 900220, '6301.03', '赔偿收入',   'REVENUE_EXTRA', 2, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900224, 0, 900220, '6301.04', '盘盈利得',   'REVENUE_EXTRA', 2, 4, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
(900225, 0, 900220, '6301.05', '政府补助',   'REVENUE_EXTRA', 2, 5, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);

-- ================================================================
-- C. 结算科目 (account_category = 'SETTLEMENT')
-- ================================================================

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900300, 0, 0, 'SETTLE', '合同结算',              'SETTLEMENT', 1, 1, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0),
(900301, 0, 900300, 'SETTLE.01', '合同结算-收入结转', 'SETTLEMENT', 2, 1, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0),
(900302, 0, 900300, 'SETTLE.02', '合同结算-价款结算', 'SETTLEMENT', 2, 2, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0);
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
(900310, 0, 0, 'CONTRACT_ASSET',     '合同资产', 'SETTLEMENT', 1, 2, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0),
(900320, 0, 0, 'CONTRACT_LIABILITY', '合同负债', 'SETTLEMENT', 1, 3, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
